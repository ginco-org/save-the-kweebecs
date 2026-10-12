package gg.ginco.kweebecs

import com.hypixel.hytale.component.Archetype
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import gg.ginco.minigame.component.Phase
import gg.ginco.minigame.component.EndReason
import gg.ginco.minigame.component.TeamData
import gg.ginco.minigame.component.TeamRef
import gg.ginco.minigame.util.Instances
import gg.ginco.minigame.util.Phases
import gg.ginco.minigame.util.Regions
import java.util.concurrent.ConcurrentHashMap
import org.joml.Vector3d
import org.joml.Vector3i

/**
 * The block gate (design v1), where block fates are decided by BLOCK
 * IDENTITY, not tracked coordinates:
 *
 * - In a gated world only the iron-bar **cage** blocks and the trorks'
 *   **barricade** blocks may be damaged/broken (ids in [StkContract]);
 *   everything else cancels, keeping the authored world (fort shell,
 *   terrain) indestructible — no per-block bookkeeping anywhere.
 * - Placement is Trork-only: barricade-fortifying is their verb; kweebec
 *   placements cancel.
 * - Breaking a cage block inside a `CAGE` site region frees that site;
 *   when every cage site is freed the round flips through
 *   [Phases] to Ending(WIN_CONDITION).
 *
 * Systems register per entity-store (plugin setup, every world); gating
 * toggles with [CageBehavior]'s active window, so all other worlds keep
 * their default block behavior.
 */
object StkBlockGate {

    private val gatedWorlds = ConcurrentHashMap.newKeySet<String>()
    private val freedSites = ConcurrentHashMap<String, MutableSet<String>>()

    /** Opens the gate: the block rules start applying in this world. */
    fun activate(worldName: String) {
        gatedWorlds.add(worldName)
        freedSites[worldName] = ConcurrentHashMap.newKeySet()
    }

    fun deactivate(worldName: String) {
        gatedWorlds.remove(worldName)
        freedSites.remove(worldName)
    }

    fun gated(worldName: String): Boolean = gatedWorlds.contains(worldName)

    /** Marks a site freed; true when this call freed the LAST of the sites. */
    fun markFreed(worldName: String, siteId: String, totalSites: Int): Boolean {
        val freed = freedSites[worldName] ?: return false
        freed.add(siteId)
        return freed.size >= totalSites
    }

    /** The one break decision every system consults. */
    private fun breakable(blockTypeId: String): Boolean =
        blockTypeId == StkContract.CAGE_BLOCK_ID || blockTypeId == StkContract.BARRICADE_BLOCK_ID

    private fun worldNameOf(store: Store<EntityStore>): String? = store.externalData.world.name

    /** The actor's team name, or null when not on any round team. */
    private fun teamNameOf(store: Store<EntityStore>, actor: Ref<EntityStore>): String? {
        val teamRef = store.getComponent(actor, TeamRef.componentType)?.teamRef ?: return null
        return store.getComponent(teamRef, TeamData.componentType)?.name
    }

    /** Cancels damage on non-breakable blocks in every stk world. */
    class DamageGate : EntityEventSystem<EntityStore, DamageBlockEvent>(DamageBlockEvent::class.java) {
        override fun handle(
            index: Int,
            chunk: ArchetypeChunk<EntityStore>,
            store: Store<EntityStore>,
            buffer: CommandBuffer<EntityStore>,
            event: DamageBlockEvent,
        ) {
            val worldName = worldNameOf(store) ?: return
            if (!gated(worldName)) return
            if (!breakable(event.blockType.id)) event.setCancelled(true)
        }

        override fun getQuery(): Query<EntityStore>? = Archetype.empty()
    }

    /**
     * Cancels breaks on non-breakable blocks; lets the two breakable kinds
     * through and runs the cage-freed flow for cage blocks (site identity =
     * the `CAGE` region containing the broken block).
     */
    class BreakGate : EntityEventSystem<EntityStore, BreakBlockEvent>(BreakBlockEvent::class.java) {
        override fun handle(
            index: Int,
            chunk: ArchetypeChunk<EntityStore>,
            store: Store<EntityStore>,
            buffer: CommandBuffer<EntityStore>,
            event: BreakBlockEvent,
        ) {
            val worldName = worldNameOf(store) ?: return
            if (!gated(worldName)) return
            val typeId = event.blockType.id
            if (!breakable(typeId)) {
                event.setCancelled(true)
                return
            }
            if (typeId == StkContract.CAGE_BLOCK_ID) {
                val actor = chunk.getReferenceTo(index)
                onCageBroken(store, actor, event.targetBlock)
            }
        }

        private fun onCageBroken(store: Store<EntityStore>, actor: Ref<EntityStore>?, targetBlock: Vector3i) {
            val world: World = store.externalData.world
            val position = Vector3d(
                targetBlock.x().toDouble(),
                targetBlock.y().toDouble(),
                targetBlock.z().toDouble())
            val sites = Regions.regionsOf(world, StkContract.CAGE)
            val site = sites.firstOrNull { it.contains(position) } ?: return
            // Coins for the breaching player — the wallet is team-agnostic
            // (design: the economy rewards participation, not affiliation).
            if (actor != null) {
                store.getComponent(actor, UUIDComponent.getComponentType())?.uuid?.let {
                    Wallets.earn(it, StkContract.COINS_PER_RESCUE)
                }
            }
            if (markFreed(world.name, site.id, sites.size)) {
                // Last cage freed: Kweebecs win — flip the round. The actor's
                // identity is NOT trusted for this (they could be a trork
                // griefing their own cage's bars); the victory belongs to the
                // instance itself.
                Instances.findController(world)?.let { controllerRef ->
                    Phases.set(store, controllerRef, Phase.ending(EndReason.WIN_CONDITION))
                }
            }
        }

        override fun getQuery(): Query<EntityStore>? = Archetype.empty()
    }

    /** Kweebec builds cancel; Trork builds pass (and are plain barricade blocks). */
    class PlaceGate : EntityEventSystem<EntityStore, PlaceBlockEvent>(PlaceBlockEvent::class.java) {
        override fun handle(
            index: Int,
            chunk: ArchetypeChunk<EntityStore>,
            store: Store<EntityStore>,
            buffer: CommandBuffer<EntityStore>,
            event: PlaceBlockEvent,
        ) {
            val worldName = worldNameOf(store) ?: return
            if (!gated(worldName)) return
            val actor = chunk.getReferenceTo(index)
            if (teamNameOf(store, actor) != StkContract.TEAM_TRORK) {
                event.setCancelled(true)
            }
        }

        override fun getQuery(): Query<EntityStore>? = Archetype.empty()
    }
}

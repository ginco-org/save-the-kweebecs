package gg.ginco.savethekweebecs

import com.hypixel.hytale.component.Archetype
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent
import com.hypixel.hytale.server.core.event.events.ecs.UseEntityEvent
import com.hypixel.hytale.server.core.modules.entity.damage.Damage
import com.hypixel.hytale.server.core.modules.entity.damage.event.KillFeedEvent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import gg.ginco.minigame.component.EndReason
import gg.ginco.minigame.component.InMinigame
import gg.ginco.minigame.component.Phase
import gg.ginco.minigame.component.TeamData
import gg.ginco.minigame.component.TeamRef
import gg.ginco.minigame.util.Instances
import gg.ginco.minigame.util.Phases
import gg.ginco.minigame.util.PlayerSet
import gg.ginco.minigame.util.Regions
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.joml.Vector3d
import org.joml.Vector3i

/**
 * Block fates are decided by BLOCK IDENTITY, not tracked coordinates:
 *
 * - In a gated world only the cage and barricade blocks may be damaged or
 *   broken (ids in [WorldContract]); everything else cancels, keeping the
 *   authored world (fort shell, terrain) indestructible — no per-block
 *   bookkeeping anywhere.
 * - Placement is Trork-only.
 * - Breaking a cage block inside a `CAGE` site region OPENS that site
 *   (INTACT → BROKEN); the kweebec inside becomes clickable.
 *
 * The other round-critical gates live here too — all are per-entity-store
 * ECS systems registered once at plugin setup and toggled by the same
 * gated-world set ([CageBehavior]'s active window):
 *
 * - [RescueGate] — the rescue click: `UseEntityEvent.Pre` on a registered
 *   caged-kweebec NPC flips BROKEN → SAVED, pays the rescuer, despawns the
 *   kweebec, and — when the last site saves — ends the round as a Kweebec
 *   victory.
 * - [KillCoinGate] — kill attribution: `KillFeedEvent.Display` (dispatched
 *   for the decedent) names the killer via `Damage.EntitySource`; PvP
 *   inside the round pays [WorldContract.COINS_PER_KILL].
 * - [RescueNpcShield] — caged kweebecs take no damage; the rescue is the
 *   click, not a kill.
 */
object BlockGate {

    private val gatedWorlds = ConcurrentHashMap.newKeySet<String>()

    enum class SiteState { INTACT, BROKEN, SAVED }

    private val sites = ConcurrentHashMap<String, ConcurrentHashMap<String, SiteState>>()

    /** Caged-kweebec NPC entity uuid → cage site id (the rescue click lookup). */
    private val rescueNpcs = ConcurrentHashMap<String, ConcurrentHashMap<UUID, String>>()

    /** Opens the gate: the block rules start applying in this world. */
    fun activate(worldName: String) {
        gatedWorlds.add(worldName)
        sites[worldName] = ConcurrentHashMap()
        rescueNpcs[worldName] = ConcurrentHashMap()
    }

    fun deactivate(worldName: String) {
        gatedWorlds.remove(worldName)
        sites.remove(worldName)
        rescueNpcs.remove(worldName)
    }

    fun gated(worldName: String): Boolean = gatedWorlds.contains(worldName)

    /** Registers the world's cage sites (all [SiteState.INTACT]); called by [CageBehavior] at activation. */
    fun registerSites(worldName: String, siteIds: List<String>) {
        val map = sites[worldName] ?: return
        for (id in siteIds) map.putIfAbsent(id, SiteState.INTACT)
    }

    /** The kweebec NPC with [npcUuid] guards this site, or null. */
    fun rescueSiteOf(worldName: String, npcUuid: UUID): String? =
        rescueNpcs[worldName]?.get(npcUuid)

    fun registerRescueNpc(worldName: String, npcUuid: UUID, siteId: String) {
        rescueNpcs[worldName]?.put(npcUuid, siteId)
    }

    /** Cage opened: INTACT → BROKEN; false when the site was not intact. */
    fun markBroken(worldName: String, siteId: String): Boolean {
        val map = sites[worldName] ?: return false
        return map.replace(siteId, SiteState.INTACT, SiteState.BROKEN)
    }

    /** Rescue click landed: BROKEN → SAVED; true exactly once per site. */
    fun markRescued(worldName: String, siteId: String): Boolean {
        val map = sites[worldName] ?: return false
        return map.replace(siteId, SiteState.BROKEN, SiteState.SAVED)
    }

    /** True when every registered site is SAVED. */
    fun allRescued(worldName: String): Boolean =
        sites[worldName]?.values?.all { it == SiteState.SAVED } ?: false

    private fun breakable(blockTypeId: String): Boolean =
        blockTypeId == WorldContract.CAGE_BLOCK_ID || blockTypeId == WorldContract.BARRICADE_BLOCK_ID

    private fun worldOf(store: Store<EntityStore>): World = store.externalData.world

    private fun teamNameOf(store: Store<EntityStore>, actor: Ref<EntityStore>): String? {
        val teamRef = store.getComponent(actor, TeamRef.componentType)?.teamRef ?: return null
        return store.getComponent(teamRef, TeamData.componentType)?.name
    }

    /** The instance controller of [world]'s store, or null outside a round. */
    private fun controllerOf(store: Store<EntityStore>, world: World): Ref<EntityStore>? =
        Instances.findController(world)

    /** Cancels damage on non-breakable blocks in every gated world. */
    class DamageGate : EntityEventSystem<EntityStore, DamageBlockEvent>(DamageBlockEvent::class.java) {
        override fun handle(
            index: Int,
            chunk: ArchetypeChunk<EntityStore>,
            store: Store<EntityStore>,
            buffer: CommandBuffer<EntityStore>,
            event: DamageBlockEvent,
        ) {
            val worldName = worldOf(store).name
            if (!gated(worldName)) return
            if (!breakable(event.blockType.id)) event.isCancelled = true
        }

        override fun getQuery(): Query<EntityStore>? = Archetype.empty()
    }

    /**
     * Cancels breaks on non-breakable blocks; lets the two breakable kinds
     * through and OPENS the cage site when a cage block breaks (site identity
     * = the `CAGE` region containing the broken block). Coins and the win
     * check wait for the rescue click — the break only frees the kweebec.
     */
    class BreakGate : EntityEventSystem<EntityStore, BreakBlockEvent>(BreakBlockEvent::class.java) {
        override fun handle(
            index: Int,
            chunk: ArchetypeChunk<EntityStore>,
            store: Store<EntityStore>,
            buffer: CommandBuffer<EntityStore>,
            event: BreakBlockEvent,
        ) {
            val world = worldOf(store)
            if (!gated(world.name)) return
            val typeId = event.blockType.id
            if (!breakable(typeId)) {
                event.isCancelled = true
                return
            }
            if (typeId != WorldContract.CAGE_BLOCK_ID) return
            val position = Vector3d(
                event.targetBlock.x().toDouble(),
                event.targetBlock.y().toDouble(),
                event.targetBlock.z().toDouble())
            val site = Regions.regionsOf(world, WorldContract.CAGE)
                .firstOrNull { it.contains(position) } ?: return
            if (!BlockGate.markBroken(world.name, site.id)) return
            // Store writes must not run mid-dispatch — hop to the world queue.
            world.execute {
                val controllerRef = controllerOf(store, world) ?: return@execute
                PlayerSet(store, controllerRef).sendMessage(Message.raw(
                    "A cage has been opened — kweebecs, click the prisoner inside to rescue them!"))
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
            val worldName = worldOf(store).name
            if (!gated(worldName)) return
            val actor = chunk.getReferenceTo(index)
            if (teamNameOf(store, actor) != WorldContract.TEAM_TRORK) {
                event.isCancelled = true
            }
        }

        override fun getQuery(): Query<EntityStore>? = Archetype.empty()
    }

    /**
     * The rescue click: using a registered caged-kweebec NPC while its cage
     * is BROKEN rescues it. Kweebecs only — a trork click does nothing. Pays
     * the rescuer, despawns the kweebec, and ends the round for the kweebecs
     * when the last site saves.
     */
    class RescueGate : EntityEventSystem<EntityStore, UseEntityEvent.Pre>(UseEntityEvent.Pre::class.java) {
        override fun handle(
            index: Int,
            chunk: ArchetypeChunk<EntityStore>,
            store: Store<EntityStore>,
            buffer: CommandBuffer<EntityStore>,
            event: UseEntityEvent.Pre,
        ) {
            val world = worldOf(store)
            if (!gated(world.name)) return
            val targetRef = event.targetEntity
            val npcUuid = store.getComponent(targetRef, UUIDComponent.getComponentType())?.uuid ?: return
            val siteId = BlockGate.rescueSiteOf(world.name, npcUuid) ?: return
            val clickerRef = chunk.getReferenceTo(index)
            if (teamNameOf(store, clickerRef) != WorldContract.TEAM_KWEEBEC) return
            if (!BlockGate.markRescued(world.name, siteId)) return
            world.execute {
                val controllerRef = controllerOf(store, world) ?: return@execute
                val clicker = store.getComponent(clickerRef, PlayerRef.getComponentType())
                    ?: return@execute
                EconomyBehavior.awardRescue(store, controllerRef, clicker)
                store.removeEntity(targetRef, RemoveReason.REMOVE)
                if (BlockGate.allRescued(world.name)) {
                    PlayerSet(store, controllerRef).sendMessage(Message.raw(
                        "Every kweebec is rescued — Kweebecs win!"))
                    Phases.set(store, controllerRef, Phase.ending(EndReason.WIN_CONDITION))
                }
            }
        }

        override fun getQuery(): Query<EntityStore>? = Archetype.empty()
    }

    /**
     * Kill coins: `KillFeedEvent.Display` is dispatched for
     * the decedent and carries the damage source; an entity source that is a
     * round member killing another round member of this instance pays
     * [WorldContract.COINS_PER_KILL]. NPC deaths (incl. caged kweebecs) pay
     * nothing — they carry no membership.
     */
    class KillCoinGate : EntityEventSystem<EntityStore, KillFeedEvent.Display>(KillFeedEvent.Display::class.java) {
        override fun handle(
            index: Int,
            chunk: ArchetypeChunk<EntityStore>,
            store: Store<EntityStore>,
            buffer: CommandBuffer<EntityStore>,
            event: KillFeedEvent.Display,
        ) {
            val world = worldOf(store)
            if (!gated(world.name)) return
            val killerRef = (event.damage.source as? Damage.EntitySource)?.ref ?: return
            val victimRef = chunk.getReferenceTo(index)
            if (killerRef == victimRef) return
            world.execute {
                val controllerRef = controllerOf(store, world) ?: return@execute
                val killer = store.getComponent(killerRef, PlayerRef.getComponentType())
                    ?: return@execute
                val victim = store.getComponent(victimRef, PlayerRef.getComponentType())
                    ?: return@execute
                if (store.getComponent(victimRef, InMinigame.componentType)?.controllerRef != controllerRef) {
                    return@execute
                }
                if (store.getComponent(killerRef, InMinigame.componentType)?.controllerRef != controllerRef) {
                    return@execute
                }
                EconomyBehavior.awardKill(store, controllerRef, killer)
            }
        }

        override fun getQuery(): Query<EntityStore>? = Archetype.empty()
    }

    /** Caged kweebecs are unkillable — the rescue is the click, not a kill. */
    class RescueNpcShield : EntityEventSystem<EntityStore, Damage>(Damage::class.java) {
        override fun handle(
            index: Int,
            chunk: ArchetypeChunk<EntityStore>,
            store: Store<EntityStore>,
            buffer: CommandBuffer<EntityStore>,
            event: Damage,
        ) {
            val worldName = worldOf(store).name
            if (!gated(worldName)) return
            val victimRef = chunk.getReferenceTo(index)
            val uuid = store.getComponent(victimRef, UUIDComponent.getComponentType())?.uuid ?: return
            if (BlockGate.rescueSiteOf(worldName, uuid) != null) event.isCancelled = true
        }

        override fun getQuery(): Query<EntityStore>? = Archetype.empty()
    }
}

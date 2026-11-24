package gg.ginco.savethekweebecs

import com.hypixel.hytale.component.Archetype
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.component.system.tick.TickingSystem
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent
import com.hypixel.hytale.server.core.event.events.ecs.RespawnEvent
import com.hypixel.hytale.server.core.event.events.ecs.UseEntityEvent
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent
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

    /** The site id whose rescue npc carries [npcUuid], or null. */
    fun siteOfNpc(worldName: String, npcUuid: UUID): String? = rescueNpcs[worldName]?.get(npcUuid)

    /**
     * Runs [action] for every ALIVE prisoner npc entity of [worldName], with
     * its site id. Entity refs are resolved fresh from the store's current
     * chunks each call — prisoner entities despawn mid-round (rescues, teardown),
     * and refs captured earlier would throw "Invalid entity reference" when
     * the store reuses their slots.
     */
    fun forEachAlivePrisoner(
        store: Store<EntityStore>,
        worldName: String,
        action: (npcRef: Ref<EntityStore>, siteId: String) -> Unit,
    ) {
        val wanted = rescueNpcs[worldName] ?: return
        if (wanted.isEmpty()) return
        val consumer = java.util.function.BiConsumer { chunk: ArchetypeChunk<EntityStore>, _: CommandBuffer<EntityStore> ->
            for (i in 0 until chunk.size()) {
                val uuid = chunk.getComponent(i, UUIDComponent.getComponentType())?.uuid ?: continue
                val siteId = wanted[uuid] ?: continue
                action(chunk.getReferenceTo(i), siteId)
            }
        }
        store.forEachChunk(Archetype.of(UUIDComponent.getComponentType()), consumer)
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

    /** The site's current state, or null (diagnostics). */
    fun stateOf(worldName: String, siteId: String): SiteState? = sites[worldName]?.get(siteId)

    /**
     * Executes a rescue for [siteId] in [world] (already flipped BROKEN → SAVED
     * by the caller): pays [rescuer] (null = unattributed), despawns the
     * prisoner, and ends the round when the last site saves. World thread.
     */
    fun executeRescue(
        store: Store<EntityStore>,
        world: World,
        npcRef: Ref<EntityStore>,
        siteId: String,
        rescuerRef: Ref<EntityStore>?,
    ) {
        val controllerRef = Instances.findController(world) ?: return
        val rescuer = rescuerRef?.let { store.getComponent(it, PlayerRef.getComponentType()) }
        if (rescuer != null) EconomyBehavior.awardRescue(store, controllerRef, rescuer)
        store.removeEntity(npcRef, RemoveReason.REMOVE)
        if (allRescued(world.name)) {
            PlayerSet(store, controllerRef).sendMessage(Message.raw(
                "Every kweebec is rescued — Kweebecs win!"))
            Phases.set(store, controllerRef, Phase.ending(EndReason.WIN_CONDITION))
        }
    }

    /** True when every registered site is SAVED. */
    fun allRescued(worldName: String): Boolean =
        sites[worldName]?.values?.all { it == SiteState.SAVED } ?: false

    private fun breakable(blockTypeId: String): Boolean =
        blockTypeId in WorldContract.CAGE_BLOCK_IDS ||
            blockTypeId == WorldContract.BARRICADE_BLOCK_ID

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
            if (typeId !in WorldContract.CAGE_BLOCK_IDS) return
            val position = Vector3d(
                event.targetBlock.x().toDouble(),
                event.targetBlock.y().toDouble(),
                event.targetBlock.z().toDouble())
            val site = Regions.regionsOf(world, WorldContract.CAGE)
                .firstOrNull { it.contains(position) }
            com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass()
                .at(java.util.logging.Level.INFO).log(
                    "MDBG break: cage block at %s -> site=%s", position, site?.id)
            if (site == null) return
            if (!BlockGate.markBroken(world.name, site.id)) return
            // Store writes must not run mid-dispatch — hop to the world queue.
            world.execute {
                val controllerRef = controllerOf(store, world) ?: return@execute
                PlayerSet(store, controllerRef).sendMessage(Message.raw(
                    "A cage has been opened — kweebecs, reach the prisoner inside to rescue them!"))
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
            val siteId = BlockGate.rescueSiteOf(world.name, npcUuid)
            val clickerRef = chunk.getReferenceTo(index)
            val team = teamNameOf(store, clickerRef)
            val siteState = siteId?.let { BlockGate.stateOf(world.name, it) }
            com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass()
                .at(java.util.logging.Level.INFO).log(
                    "MDBG use: target=%s registered=%b team=%s site=%s state=%s",
                    npcUuid, siteId != null, team, siteId, siteState)
            if (siteId == null) return
            if (team != WorldContract.TEAM_KWEEBEC) return
            if (!BlockGate.markRescued(world.name, siteId)) return
            world.execute {
                BlockGate.executeRescue(store, world, targetRef, siteId, clickerRef)
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

    /**
     * Proximity rescue fallback: a kweebec member standing within [RADIUS]
     * of a BROKEN site's prisoner rescues them — no click required. The
     * entity-use path depends on client-side interaction offers that a bare
     * spawned NPC may never surface; approach cannot be blocked. Runs every
     * [INTERVAL_TICKS] ticks in gated worlds.
     */
    class RescueProximitySystem : TickingSystem<EntityStore>() {

        /** Per WORLD THREAD tick counter (systems tick on every world). */
        private val tickCounter = ThreadLocal.withInitial { 0L }

        override fun tick(dt: Float, systemIndex: Int, store: Store<EntityStore>) {
            val n = tickCounter.get() + 1
            tickCounter.set(n)
            if (n % INTERVAL_TICKS != 0L) return
            val world = store.externalData.world
            if (!gated(world.name)) return
            val controllerRef = Instances.findController(world) ?: return

            val memberPositions = PlayerSet(store, controllerRef).all().mapNotNull { member ->
                if (teamNameOf(store, member) != WorldContract.TEAM_KWEEBEC) return@mapNotNull null
                val transform = store.getComponent(member, TransformComponent.getComponentType())
                    ?: return@mapNotNull null
                member to transform.position
            }
            if (memberPositions.isEmpty()) return

            val rescues = mutableListOf<Triple<Ref<EntityStore>, String, Ref<EntityStore>>>()
            forEachAlivePrisoner(store, world.name) { npcRef, siteId ->
                if (stateOf(world.name, siteId) != SiteState.BROKEN) return@forEachAlivePrisoner
                val npcTransform = store.getComponent(npcRef, TransformComponent.getComponentType())
                    ?: return@forEachAlivePrisoner
                val npcPos = npcTransform.position
                val rescuer = memberPositions.firstOrNull { (_, pos) ->
                    pos.distanceSquared(npcPos) <= RADIUS * RADIUS
                }?.first ?: return@forEachAlivePrisoner
                rescues.add(Triple(npcRef, siteId, rescuer))
            }
            // Writes AFTER the scan — forEachChunk holds the store's processing
            // lock, and executing rescues inside it (removeEntity, phases) threw
            // "Store is currently processing" and killed the world thread,
            // leaving zombie queue worlds that accepted nobody.
            for ((npcRef, siteId, rescuer) in rescues) {
                if (!markRescued(world.name, siteId)) continue
                PlayerSet(store, controllerRef).sendMessage(Message.raw(
                    "A kweebec has been rescued!"))
                executeRescue(store, world, npcRef, siteId, rescuer)
            }
        }

        companion object {
            /** Rescue distance (blocks) from the prisoner. */
            const val RADIUS = 3.0

            /** Check cadence in ticks (10 = 0.5s at 20 TPS). */
            const val INTERVAL_TICKS = 10L
        }
    }

    /**
     * No friendly fire: round members of the SAME team cannot damage each
     * other (cross-team combat is the game). Attacker comes from the damage's
     * entity source; non-members are untouched (fall damage, environment and
     * NPC hits behave as vanilla). Read-only handler — cancellation is the
     * event's own flag.
     */
    class TeamDamageGate : EntityEventSystem<EntityStore, Damage>(Damage::class.java) {
        override fun handle(
            index: Int,
            chunk: ArchetypeChunk<EntityStore>,
            store: Store<EntityStore>,
            buffer: CommandBuffer<EntityStore>,
            event: Damage,
        ) {
            if (!gated(worldOf(store).name)) return
            val victimRef = chunk.getReferenceTo(index)
            val attackerRef = (event.source as? Damage.EntitySource)?.ref ?: return
            if (attackerRef == victimRef) return
            val victimTeam = teamNameOf(store, victimRef) ?: return
            val attackerTeam = teamNameOf(store, attackerRef) ?: return
            if (victimTeam == attackerTeam) event.isCancelled = true
        }

        override fun getQuery(): Query<EntityStore>? = Archetype.empty()
    }

    /**
     * Deaths respawn at the world spawn — the map's default marker, which
     * sits on the kweebec side — so respawning members went back to the
     * WRONG side. Send them to their team's spawn pool with the server's own
     * mid-play teleport pattern, deferred 200ms so it lands AFTER the
     * respawn pipeline has applied its own spawn transform.
     */
    class RespawnSpawnGate : EntityEventSystem<EntityStore, RespawnEvent>(RespawnEvent::class.java) {

        /** Cycles team spawn points across respawns. */
        private val respawnCounter = java.util.concurrent.atomic.AtomicLong()

        override fun handle(
            index: Int,
            chunk: ArchetypeChunk<EntityStore>,
            store: Store<EntityStore>,
            buffer: CommandBuffer<EntityStore>,
            event: RespawnEvent,
        ) {
            val world = worldOf(store)
            if (!gated(world.name)) return
            val playerRef = chunk.getReferenceTo(index)
            val team = teamNameOf(store, playerRef) ?: return
            val spawns = Regions.spawnPointsOf(world, Regions.spawnTypeFor(team))
            if (spawns.isEmpty()) return
            val target = spawns[((respawnCounter.incrementAndGet() and Int.MAX_VALUE.toLong()) % spawns.size).toInt()]
            com.hypixel.hytale.server.core.HytaleServer.SCHEDULED_EXECUTOR.schedule({
                world.execute {
                    val transform = store.getComponent(playerRef, TransformComponent.getComponentType())
                        ?: return@execute
                    transform.teleportPosition(target.position)
                    val rotation = target.rotation ?: transform.rotation
                    target.rotation?.let { transform.teleportRotation(it) }
                    store.addComponent(
                        playerRef,
                        com.hypixel.hytale.server.core.modules.entity.teleport.PendingTeleport.getComponentType(),
                        com.hypixel.hytale.server.core.modules.entity.teleport.PendingTeleport(),
                    )
                    store.getComponent(playerRef, PlayerRef.getComponentType())?.let { pr ->
                        com.hypixel.hytale.server.core.modules.entity.teleport.TeleportSystems
                            .queueAndSendClientTeleport(pr, target.position, rotation, rotation, true)
                    }
                }
            }, 200, java.util.concurrent.TimeUnit.MILLISECONDS)
        }

        override fun getQuery(): Query<EntityStore>? = Archetype.empty()
    }
}

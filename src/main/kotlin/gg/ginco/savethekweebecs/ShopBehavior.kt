package gg.ginco.savethekweebecs

import com.hypixel.hytale.builtin.points.PointsPlugin
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.component.RemoveReason
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.server.core.entity.Frozen
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.npc.NPCPlugin
import gg.ginco.minigame.behavior.GameBehavior
import gg.ginco.minigame.behavior.MinigameContext
import org.joml.Vector3d

/**
 * Spawns one frozen shopkeeper NPC at every `KWEEBEC_SHOP` / `TRORK_SHOP`
 * point on the map and registers it with [ShopRegistry]. The click itself is
 * handled by [ShopGate], which enforces the team gate and opens [ShopPage].
 *
 * Missing shop points are not an error: maps can ship without shops, and the
 * authored dev map gets its points from the in-game point tool.
 */
class ShopBehavior(ctx: MinigameContext) : GameBehavior("save-the-kweebecs-shops") {

    override val displayName = "Save the Kweebecs shops"

    private val spawned = mutableListOf<Ref<EntityStore>>()
    private var deactivated = false

    override fun onActivate(ctx: MinigameContext) {
        deactivated = false
        val kweebec = markers(ctx, WorldContract.SHOP_KWEEBEC)
        val trork = markers(ctx, WorldContract.SHOP_TRORK)
        if (kweebec.isEmpty()) LOGGER.atWarning().log(
            "world '%s' has no %s points — no Kweebec shop", ctx.world.name, WorldContract.SHOP_KWEEBEC)
        if (trork.isEmpty()) LOGGER.atWarning().log(
            "world '%s' has no %s points — no Trork shop", ctx.world.name, WorldContract.SHOP_TRORK)
        ctx.world.execute {
            if (deactivated) return@execute
            for (marker in kweebec) {
                runCatching { spawn(ctx, marker, ShopKind.KWEEBEC) }
                    .onFailure { LOGGER.atSevere().withCause(it).log("Kweebec shopkeeper spawn failed") }
            }
            for (marker in trork) {
                runCatching { spawn(ctx, marker, ShopKind.TRORK) }
                    .onFailure { LOGGER.atSevere().withCause(it).log("Trork shopkeeper spawn failed") }
            }
        }
    }

    override fun onDeactivate(ctx: MinigameContext) {
        deactivated = true
        for (ref in spawned) {
            if (ref.isValid()) ctx.store.removeEntity(ref, RemoveReason.REMOVE)
        }
        spawned.clear()
        ShopRegistry.clearController(ctx.controllerRef)
    }

    private data class Marker(val position: Vector3d, val rotation: Rotation3f)

    private fun markers(ctx: MinigameContext, type: String): List<Marker> {
        val manager = runCatching {
            ctx.store.getResource(PointsPlugin.get().managerResourceType)
        }.getOrNull() ?: return emptyList()
        return manager.getPointsWithTags(type)
            .asSequence()
            .filter { it.isEnabled }
            .sortedBy { it.id }
            .map {
                val transform = it.transform
                Marker(Vector3d(transform.position), transform.rotation)
            }
            .toList()
    }

    private fun spawn(ctx: MinigameContext, marker: Marker, kind: ShopKind) {
        val npc = NPCPlugin.get()
            ?: throw IllegalStateException("NPCPlugin not reachable — cannot spawn shopkeepers")
        val pair = npc.spawnNPC(
            ctx.store, kind.npcRole, null, marker.position, marker.rotation)
            ?: throw IllegalStateException(
                "shopkeeper spawn failed (role '${kind.npcRole}' missing?)")
        val ref = pair.first()
        // Frozen stops a Trork shopkeeper's combat AI and keeps either role at
        // its point; Invulnerable keeps the shop from being killed shut.
        ctx.store.ensureComponent(ref, Frozen.getComponentType())
        ctx.store.ensureComponent(ref, Invulnerable.getComponentType())
        NpcInteraction.makeUsable(ctx.store, ref)
        val uuid = ctx.store.getComponent(ref, UUIDComponent.getComponentType())?.uuid
            ?: throw IllegalStateException("spawned shopkeeper has no UUIDComponent")
        ShopRegistry.register(uuid, kind, ctx.controllerRef)
        spawned.add(ref)
    }

    companion object {
        private val LOGGER = HytaleLogger.forEnclosingClass()
    }
}

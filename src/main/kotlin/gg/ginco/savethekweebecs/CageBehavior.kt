package gg.ginco.savethekweebecs

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.util.FillerBlockUtil
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple
import com.hypixel.hytale.server.core.entity.Frozen
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.npc.NPCPlugin
import gg.ginco.minigame.behavior.GameBehavior
import gg.ginco.minigame.behavior.MinigameContext
import gg.ginco.minigame.event.PlayerJoinedInstanceEvent
import gg.ginco.minigame.util.Regions
import org.joml.Vector3d
import kotlin.math.floor

/**
 * Cage-site lifecycle: activates the block gate for this instance's world
 * and registers the site ids with it, stamps a placeholder cage block at
 * each site (dev maps made of void need a breakable cage piece; on the
 * authored real world the stamp is a harmless extra piece at the site's
 * center cell), and spawns the caged kweebec NPC per site, registered as
 * that site's rescue target (uuid lookup in [BlockGate]).
 *
 * The rescue itself is two steps: kweebecs break the cage, then click the
 * kweebec NPC — [BlockGate.RescueGate] completes it. The spawned prisoners
 * are frozen (no wandering AI) and carry the interaction marker; the rescue
 * gate enforces which team may click them.
 */
class CageBehavior(ctx: MinigameContext) : GameBehavior("save-the-kweebecs-cages") {

    override val displayName = "Save the Kweebecs cages"

    override fun onActivate(ctx: MinigameContext) {
        val sites = ctx.regions(WorldContract.CAGE)
        if (sites.isEmpty()) throw IllegalStateException(
            "world missing the CAGE contract regions — not a Save the Kweebecs map (found none)")
        BlockGate.activate(ctx.world.name)
        BlockGate.registerSites(ctx.world.name, sites.map { it.id })
        ctx.onEvent<PlayerJoinedInstanceEvent> { e ->
            if (e.instance != ctx.controllerRef) return@onEvent
            makePrisonersRescuableFor(ctx)
        }
        // Chunk reads/writes are world-thread work.
        ctx.world.execute {
            for (site in sites) {
                stampPlaceholderCage(ctx, site)
                spawnCagedKweebec(ctx, site)
            }
        }
    }

    override fun onDeactivate(ctx: MinigameContext) {
        // The copied instance world is removed wholesale at teardown; the
        // gate must not survive into a later round on the same world name.
        BlockGate.deactivate(ctx.world.name)
    }

    /** Stamps one cage block at the site's center-top cell, best effort. */
    private fun stampPlaceholderCage(ctx: MinigameContext, site: Regions.RegionInfo) {
        val centerX = floor((site.minX + site.maxX) / 2.0).toInt()
        val centerZ = floor((site.minZ + site.maxZ) / 2.0).toInt()
        val y = floor(site.maxY).toInt() - 1 // top-most cell of the region column
        // Best-effort: the authored world carries its own cages, so a stamp
        // that can't land (cell not resident) is safe to skip.
        val chunkStore = ctx.world.chunkStore
        val sectionRef = chunkStore.getChunkSectionReferenceAtBlock(centerX, y, centerZ)
            ?: return
        val blockType: BlockType = BlockType.getAssetMap().getAsset(WorldContract.CAGE_STAMP_BLOCK_ID)
            ?: return
        BlockOperations.setBlock(
            chunkStore, sectionRef,
            centerX, y, centerZ,
            BlockType.getAssetMap().getIndex(blockType.id),
            blockType,
            RotationTuple.NONE_INDEX,
            FillerBlockUtil.NO_FILLER,
            SetBlockSettings.NONE,
        )
    }

    /**
     * Spawns the caged kweebec NPC ([WorldContract.CAGED_KWEEBEC_ROLE]) at the
     * site's center and registers it as the site's rescue target. Fails
     * loudly: a site without its kweebec can never be rescued, so the round
     * would silently be unwinnable.
     */
    private fun spawnCagedKweebec(ctx: MinigameContext, site: Regions.RegionInfo) {
        val npc = NPCPlugin.get()
            ?: throw IllegalStateException("NPCPlugin not reachable — cannot spawn caged kweebecs")
        val centerX = floor((site.minX + site.maxX) / 2.0).toInt()
        val centerZ = floor((site.minZ + site.maxZ) / 2.0).toInt()
        val position = Vector3d(centerX + 0.5, site.minY + 1.0, centerZ + 0.5)
        val pair = npc.spawnNPC(
            ctx.store, WorldContract.CAGED_KWEEBEC_ROLE, null, position, Rotation3f())
            ?: throw IllegalStateException(
                "caged-kweebec spawn failed (role '${WorldContract.CAGED_KWEEBEC_ROLE}' missing?)")
        val uuid = ctx.store.getComponent(pair.first(), UUIDComponent.getComponentType())?.uuid
            ?: throw IllegalStateException("spawned caged kweebec has no UUIDComponent")
        BlockGate.registerRescueNpc(ctx.world.name, uuid, site.id)
        // Canonical NPC freeze: keeps the prisoner in place and out of the AI
        // movement/behaviour loop while leaving interaction handling alive.
        ctx.store.ensureComponent(pair.first(), Frozen.getComponentType())
        makeRescuable(ctx, pair.first())
    }

    /** Makes every live prisoner clickable (late joiners see them too). World thread. */
    private fun makePrisonersRescuableFor(ctx: MinigameContext) {
        // Collect first: forEachAlivePrisoner holds the store processing lock.
        val prisoners = mutableListOf<Ref<EntityStore>>()
        BlockGate.forEachAlivePrisoner(ctx.store, ctx.world.name) { npcRef, _ ->
            prisoners.add(npcRef)
        }
        for (npcRef in prisoners) makeRescuable(ctx, npcRef)
    }

    private fun makeRescuable(ctx: MinigameContext, npcRef: Ref<EntityStore>) {
        NpcInteraction.makeUsable(ctx.store, npcRef)
    }
}

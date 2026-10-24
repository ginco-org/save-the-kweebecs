package gg.ginco.kweebecs

import com.hypixel.hytale.math.vector.Rotation3f
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.core.util.FillerBlockUtil
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.npc.NPCPlugin
import gg.ginco.minigame.behavior.GameBehavior
import gg.ginco.minigame.behavior.MinigameContext
import gg.ginco.minigame.util.Regions
import org.joml.Vector3d

/**
 * Cage management (design v1): each `CAGE`-contracted site holds an
 * imprisoned kweebec NPC inside a cage whose bars are
 * [StkContract.CAGE_BLOCK_ID]. The rescue is two steps (design): kweebecs
 * break the cage (the block gate lets only cage/barricade blocks through),
 * then CLICK the kweebec NPC — [StkBlockGate.RescueGate] completes it.
 *
 * This behavior owns cage-site lifecycle:
 * - activates the block gate for this instance's world and registers the
 *   site ids with it;
 * - **stamps a placeholder cage block** at each site (dev maps made of void
 *   need a breakable cage piece per site; the authored real world already
 *   carries its own cage geometry, where the stamp is a harmless extra
 *   piece at the site's center cell);
 * - **spawns the caged kweebec NPC** at each site's center and registers it
 *   as that site's rescue target (uuid lookup in [StkBlockGate]).
 */
class CageBehavior(ctx: MinigameContext) : GameBehavior("stk-cages") {

    override val displayName = "SaveTheKweebecs cages"

    override fun onActivate(ctx: MinigameContext) {
        val sites = ctx.regions(StkContract.CAGE)
        if (sites.isEmpty()) throw IllegalStateException(
            "world missing the CAGE contract regions — not a stk map (found none)")
        StkBlockGate.activate(ctx.world.name)
        StkBlockGate.registerSites(ctx.world.name, sites.map { it.id })
        // On the world thread: chunk reads/writes and the region lookups obey
        // the store's thread rules.
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
        StkBlockGate.deactivate(ctx.world.name)
    }

    /** Stamps one cage block at the site's center-top cell, best effort. */
    private fun stampPlaceholderCage(ctx: MinigameContext, site: Regions.RegionInfo) {
        val centerX = Math.floor((site.minX + site.maxX) / 2.0).toInt()
        val centerZ = Math.floor((site.minZ + site.maxZ) / 2.0).toInt()
        val y = Math.floor(site.maxY).toInt() - 1 // top-most cell of the region column
        // Instance worlds are small; the site cell must be resident right
        // after open. If not, best-effort skip (the authored world carries
        // its own cages and stamps are a dev-map nicety).
        //
        // Hytale 0.7.0 chunk API: blocks live in per-section ChunkStore
        // entities; writes go through BlockOperations (the builtin flow),
        // resolving the section ref by block coordinate.
        val chunkStore = ctx.world.chunkStore
        val sectionRef = chunkStore.getChunkSectionReferenceAtBlock(centerX, y, centerZ)
            ?: return
        val blockType: BlockType = BlockType.getAssetMap().getAsset(StkContract.CAGE_BLOCK_ID)
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
     * Spawns the caged kweebec NPC ([StkContract.CAGED_KWEEBEC_ROLE]) at the
     * site's center and registers it as the site's rescue target. Fails
     * loudly: a site without its kweebec can never be rescued, so the round
     * would silently be unwinnable.
     */
    private fun spawnCagedKweebec(ctx: MinigameContext, site: Regions.RegionInfo) {
        val npc = NPCPlugin.get()
            ?: throw IllegalStateException("NPCPlugin not reachable — cannot spawn caged kweebecs")
        val centerX = Math.floor((site.minX + site.maxX) / 2.0).toInt()
        val centerZ = Math.floor((site.minZ + site.maxZ) / 2.0).toInt()
        val position = Vector3d(centerX + 0.5, site.minY + 1.0, centerZ + 0.5)
        val pair = npc.spawnNPC(
            ctx.store, StkContract.CAGED_KWEEBEC_ROLE, null, position, Rotation3f())
            ?: throw IllegalStateException(
                "caged-kweebec spawn failed (role '${StkContract.CAGED_KWEEBEC_ROLE}' missing?)")
        val uuid = ctx.store.getComponent(pair.first(), UUIDComponent.getComponentType())?.uuid
            ?: throw IllegalStateException("spawned caged kweebec has no UUIDComponent")
        StkBlockGate.registerRescueNpc(ctx.world.name, uuid, site.id)
    }
}

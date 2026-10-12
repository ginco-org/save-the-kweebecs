package gg.ginco.kweebecs

import com.hypixel.hytale.math.util.ChunkUtil
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import gg.ginco.minigame.behavior.GameBehavior
import gg.ginco.minigame.behavior.MinigameContext
import gg.ginco.minigame.util.Regions

/**
 * Cage management (design v1): each `CAGE`-contracted site holds an
 * imprisoned kweebec inside a cage whose bars are
 * [StkContract.CAGE_BLOCK_ID]. Kweebecs break the cage (the block gate lets
 * only cage/barricade blocks through), then click the kweebec to save them.
 *
 * This behavior owns cage-site lifecycle:
 * - activates the block gate for this instance's world;
 * - **stamps a placeholder cage block** at each site (dev maps made of void
 *   need a breakable cage piece per site; the authored real world already
 *   carries its own cage geometry, where the stamp is a harmless extra
 *   piece at the site's center cell);
 * - freed bookkeeping lives in [StkBlockGate]: block events are the only
 *   place cage breaks are observed from.
 */
class CageBehavior(ctx: MinigameContext) : GameBehavior("stk-cages") {

    override val displayName = "SaveTheKweebecs cages"

    override fun onActivate(ctx: MinigameContext) {
        val sites = ctx.regions(StkContract.CAGE)
        if (sites.isEmpty()) throw IllegalStateException(
            "world missing the CAGE contract regions — not a stk map (found none)")
        StkBlockGate.activate(ctx.world.name)
        // On the world thread: chunk reads/writes and the region lookups obey
        // the store's thread rules.
        ctx.world.execute {
            for (site in sites) stampPlaceholderCage(ctx, site)
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
        val chunk = ctx.world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(centerX, centerZ))
        if (chunk == null) return
        chunk.setBlock(centerX, y, centerZ, StkContract.CAGE_BLOCK_ID)
    }
}

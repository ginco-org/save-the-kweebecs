package gg.ginco.kweebecs

import gg.ginco.minigame.behavior.GameBehavior
import gg.ginco.minigame.behavior.MinigameContext

/**
 * Cage management (design v1): each `CAGE`-contracted site holds an
 * imprisoned NPC kweebec. Kweebecs break the cage (server-gated breakable
 * block at the anchor), then click the kweebec to save them. Freed cages
 * stay freed; kweebecs win when every cage is freed before the timer.
 *
 * v1 slice, wiring-first: the behavior discovers the cage sites and wins
 * the round when all are freed. The physical cage (block + entity +
 * interactable) lands in the next slice — the block-damage gate comes with
 * DamageBlockEvent/BreakBlockEvent systems reserved by the plugin.
 */
class CageBehavior(ctx: MinigameContext) : GameBehavior("stk-cages") {

    override val displayName = "SaveTheKweebecs cages"

    /** Anchors are fixed at activate-time; kinds per design v1. */
    private lateinit var sites: List<CageSite>

    override fun onActivate(ctx: MinigameContext) {
        sites = ctx.regions(StkContract.CAGE).map { CageSite(it, state = State.INTACT) }
        if (sites.isEmpty()) throw IllegalStateException(
            "world missing the CAGE contract regions — not a stk map (found none)")
    }

    override fun onDeactivate(ctx: MinigameContext) {
        sites = emptyList()
    }

    /** Disappearance proxy for the physical cage — coming with the block work. */
    enum class State { INTACT, BROKEN, FREED }

    data class CageSite(val anchorName: Any, val state: State)

    val freedCount: Int get() = sites.count { it.state == State.FREED }
    val count: Int get() = sites.size

    companion object {
        /** Marks a cage freed and may end the round. Returns freed/total. */
        fun onFreed(ctx: MinigameContext, rescuer: UUID, behavior: CageBehavior): Pair<Int, Int> {
            EconomyBehavior.onRescue(ctx, rescuer)
            // TODO: win check + Ending phase transition once state lives
            //  per-cage (needs the freed-count mutation the physical cage
            //  work provides).
            return behavior.freedCount to behavior.count
        }
    }
}

private typealias UUID = java.util.UUID

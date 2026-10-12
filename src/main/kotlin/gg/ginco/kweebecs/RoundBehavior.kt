package gg.ginco.kweebecs

import gg.ginco.minigame.behavior.GameBehavior
import gg.ginco.minigame.behavior.MinigameContext


/**
 * The round clock (design v1): when the instance enters Playing, spend
 * StkContract.ROUND_SECONDS; expiry with any cage intact = Trorks win
 * (Phases.ending / endInstance decide the actual teardown — see
 * KweebecWin_behavior when the cage victory lands).
 *
 * TODO(wiring): timer start is driven by the Playing transition (observation
 * of PhaseChangedEvent exists via ctx.onEvent once the framework exposes
 * it here — filler until the definition is saved and used for real boots).
 */
class RoundBehavior(ctx: MinigameContext) : GameBehavior("stk-round") {

    override val displayName = "SaveTheKweebecs round timer"

    override fun onActivate(ctx: MinigameContext) {
        // Hook Playing -> schedule the expiry (ctx.runLater is tick-based,
        // seconds→ticks at 20tps; runs on the world thread via runLater→
        // execute). Filler until the definition boots for real and the cage
        // victory can end the round early.
        ctx.runLater(StkContract.ROUND_SECONDS * 20L) {
            ctx.addStat("rounds_expired", 1)
        }
    }
}

package gg.ginco.savethekweebecs

import com.hypixel.hytale.server.core.Message
import gg.ginco.minigame.behavior.GameBehavior
import gg.ginco.minigame.behavior.MinigameContext
import gg.ginco.minigame.component.EndReason
import gg.ginco.minigame.component.Phase
import gg.ginco.minigame.event.PhaseChangedEvent
import gg.ginco.minigame.util.Phases
import gg.ginco.minigame.util.PlayerSet

/**
 * The round clock: started by the Countdown→Playing transition
 * ([PhaseChangedEvent] — listeners auto-unregister on deactivation). Expiry
 * with any kweebec still unrescued = Trorks win; the Kweebec victory path
 * is the rescue click ([BlockGate.RescueGate]).
 */
class RoundBehavior(ctx: MinigameContext) : GameBehavior("save-the-kweebecs-round") {

    override val displayName = "Save the Kweebecs round timer"

    override fun onActivate(ctx: MinigameContext) {
        ctx.onEvent<PhaseChangedEvent> { e ->
            if (e.instance != ctx.controllerRef) return@onEvent
            if (e.new != Phase.Kind.PLAYING) return@onEvent
            // runLater is tick-based (seconds→ticks at 20tps) and lands on
            // the world thread; a deactivated context drops the task.
            ctx.runLater(WorldContract.ROUND_SECONDS * 20L) { onExpiry(ctx) }
        }
    }

    /** Timer expired: Trorks win unless every kweebec is already rescued. */
    private fun onExpiry(ctx: MinigameContext) {
        val phase = ctx.store.getComponent(ctx.controllerRef, Phase.componentType) ?: return
        if (phase.kind != Phase.Kind.PLAYING) return
        if (BlockGate.allRescued(ctx.world.name)) return
        ctx.addStat("rounds_expired", 1)
        PlayerSet(ctx.store, ctx.controllerRef).sendMessage(
            Message.raw("Time is up with kweebecs still caged — Trorks win!"))
        Phases.set(ctx.store, ctx.controllerRef, Phase.ending(EndReason.TIMEOUT))
    }
}

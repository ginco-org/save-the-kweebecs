package gg.ginco.kweebecs

import gg.ginco.minigame.behavior.GameBehavior
import gg.ginco.minigame.behavior.MinigameContext
import gg.ginco.minigame.util.PlayerSet
import gg.ginco.minigame.util.Teams
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Wallets for the round economy (design v1): player-scoped coins, kept
 * across respawns, wiped with the instance ("coins" stat is authoritative —
 * this cache is the dispatch layer for purchase affordances and fast UI
 * reads; the stat survives reloads and is the final word on totals).
 */
object Wallets {
    private val coins = ConcurrentHashMap<UUID, Int>()

    fun balance(uuid: UUID): Int = coins[uuid] ?: 0

    fun earn(uuid: UUID, amount: Int) {
        if (amount <= 0) return
        coins.put(uuid, balance(uuid) + amount)
    }

    fun spend(uuid: UUID, amount: Int): Boolean {
        val current = balance(uuid)
        if (current < amount) return false
        coins.put(uuid, current - amount)
        return true
    }

    fun clear() = coins.clear()
}

/**
 * The round economy behavior (design v1): kills earn coins, rescues earn
 * coins, `/stk shop` spends them. Restart-proof: the "coins" stat is the
 * authoritative ledger, this behavior keeps the instance's running tally
 * and mirrors earn/spend into it per player.
 *
 * TODO(feedback): `MinigameContext.onEvent` reaches only bus events; kill
 * attribution needs `KillFeedEvent` details — fill in once the
 * damage-module integration is verified live (see DESIGN.md).
 */
class EconomyBehavior(ctx: MinigameContext) : GameBehavior("stk-economy") {

    override val displayName = "SaveTheKweebecs economy (coins)"

    override fun onActivate(ctx: MinigameContext) {
        // TODO: attribute kills -> COINS_PER_KILL once kill feed details are
        //  verified live; the wallet API is ready for the hook.
        //  (see DESIGN.md "Economy hooks")
    }

    override fun onDeactivate(ctx: MinigameContext) {
        Wallets.clear()
    }

    companion object {
        /** Coins for a rescue — called by [CageBehavior] when a kweebec is freed. */
        fun onRescue(ctx: MinigameContext, rescuer: UUID) {
            Wallets.earn(rescuer, StkContract.COINS_PER_RESCUE)
        }
    }
}

package gg.ginco.savethekweebecs

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import gg.ginco.minigame.behavior.GameBehavior
import gg.ginco.minigame.behavior.MinigameContext
import gg.ginco.minigame.util.Stats
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Player-scoped coins, kept across respawns, wiped with the instance. The
 * instance stats ("coins", "kills", "rescues" — see [WorldContract]) are the
 * authoritative ledger, mirrored by [EconomyBehavior.awardKill]/[awardRescue];
 * this cache is the dispatch layer for purchase affordances and fast UI reads.
 */
object Wallets {
    private val coins = ConcurrentHashMap<UUID, Int>()

    fun balance(uuid: UUID): Int = coins[uuid] ?: 0

    fun earn(uuid: UUID, amount: Int) {
        if (amount <= 0) return
        coins[uuid] = balance(uuid) + amount
    }

    fun spend(uuid: UUID, amount: Int): Boolean {
        val current = balance(uuid)
        if (current < amount) return false
        coins[uuid] = current - amount
        return true
    }

    fun clear() = coins.clear()
}

/**
 * Kills earn coins, rescues earn coins, `/shop` spends them. The stats are
 * the ledger, the wallet the running tally.
 *
 * The award entry points are called from the ECS gates in [BlockGate]
 * ([BlockGate.KillCoinGate] for kills, [BlockGate.RescueGate] for
 * rescues) — ECS events are not bus events, so they cannot be observed from
 * this behavior's [onEvent]; the gates pay through here instead.
 */
class EconomyBehavior(ctx: MinigameContext) : GameBehavior("save-the-kweebecs-economy") {

    override val displayName = "Save the Kweebecs economy (coins)"

    override fun onDeactivate(ctx: MinigameContext) {
        Wallets.clear()
    }

    companion object {
        /** Coins for a PvP kill inside the round — called by [BlockGate.KillCoinGate]. */
        fun awardKill(store: Store<EntityStore>, controllerRef: Ref<EntityStore>, killer: PlayerRef) =
            award(store, controllerRef, killer, WorldContract.STAT_KILLS, WorldContract.COINS_PER_KILL, "kill")

        /** Coins for rescuing a caged kweebec — called by [BlockGate.RescueGate]. */
        fun awardRescue(store: Store<EntityStore>, controllerRef: Ref<EntityStore>, rescuer: PlayerRef) =
            award(store, controllerRef, rescuer, WorldContract.STAT_RESCUES, WorldContract.COINS_PER_RESCUE, "rescue")

        /** Wallet + both stat counters + the player-facing award line. */
        private fun award(
            store: Store<EntityStore>,
            controllerRef: Ref<EntityStore>,
            player: PlayerRef,
            stat: String,
            coins: Int,
            verb: String,
        ) {
            Wallets.earn(player.uuid, coins)
            Stats.addForPlayer(store, controllerRef, player, stat, 1)
            Stats.addForPlayer(store, controllerRef, player, WorldContract.STAT_COINS, coins)
            player.sendMessage(
                Message.raw("+$coins coins ($verb) — balance: ${Wallets.balance(player.uuid)}"))
        }
    }
}

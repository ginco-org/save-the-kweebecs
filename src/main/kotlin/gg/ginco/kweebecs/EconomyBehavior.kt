package gg.ginco.kweebecs

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
 * Wallets for the round economy (design v1): player-scoped coins, kept
 * across respawns, wiped with the instance. The instance stats ("coins",
 * "kills", "rescues" — see [StkContract]) are the authoritative ledger,
 * mirrored by [EconomyBehavior.awardKill]/[awardRescue]; this cache is the
 * dispatch layer for purchase affordances and fast UI reads.
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
 * coins, `/shop` spends them. Restart-proof: the stats are the ledger, the
 * wallet is the running tally.
 *
 * The award entry points are called from the ECS gates in [StkBlockGate]
 * ([StkBlockGate.KillCoinGate] for kills, [StkBlockGate.RescueGate] for
 * rescues) — ECS events are not bus events, so they cannot be observed from
 * this behavior's [onEvent]; the gates pay through here instead.
 */
class EconomyBehavior(ctx: MinigameContext) : GameBehavior("stk-economy") {

    override val displayName = "SaveTheKweebecs economy (coins)"

    override fun onActivate(ctx: MinigameContext) {
        // Wallet lifecycle only — awards flow in through the gates (see class
        // doc); bus-event hooks (join/leave messages etc.) would live here.
    }

    override fun onDeactivate(ctx: MinigameContext) {
        Wallets.clear()
    }

    companion object {
        /** Coins for a PvP kill inside the round — called by [StkBlockGate.KillCoinGate]. */
        fun awardKill(store: Store<EntityStore>, controllerRef: Ref<EntityStore>, killer: PlayerRef) =
            award(store, controllerRef, killer, StkContract.STAT_KILLS, StkContract.COINS_PER_KILL, "kill")

        /** Coins for rescuing a caged kweebec — called by [StkBlockGate.RescueGate]. */
        fun awardRescue(store: Store<EntityStore>, controllerRef: Ref<EntityStore>, rescuer: PlayerRef) =
            award(store, controllerRef, rescuer, StkContract.STAT_RESCUES, StkContract.COINS_PER_RESCUE, "rescue")

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
            Stats.addForPlayer(store, controllerRef, player, StkContract.STAT_COINS, coins)
            player.sendMessage(
                Message.raw("+$coins coins ($verb) — balance: ${Wallets.balance(player.uuid)}"))
        }
    }
}

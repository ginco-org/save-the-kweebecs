package gg.ginco.savethekweebecs

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.GameMode
import com.hypixel.hytale.server.core.asset.type.gameplay.DeathConfig
import com.hypixel.hytale.server.core.asset.type.gameplay.MutableDeathConfig
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.inventory.InventoryUtils
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap
import com.hypixel.hytale.server.core.modules.physics.component.Velocity
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import gg.ginco.minigame.behavior.GameBehavior
import gg.ginco.minigame.behavior.MinigameContext
import gg.ginco.minigame.component.TeamData
import gg.ginco.minigame.component.TeamRef
import gg.ginco.minigame.event.PlayerJoinedInstanceEvent
import gg.ginco.minigame.event.PlayerLeftInstanceEvent

/**
 * Round gear: every member receives their team's basic kit when their
 * membership is established ([PlayerJoinedInstanceEvent] — first join and
 * reconnect alike), replacing whatever they carried in. The instance world's
 * death config is pinned to [DeathConfig.ItemsLossMode.NONE] so kits are
 * never dropped on death — items are score, not stakes.
 */
class KitBehavior(ctx: MinigameContext) : GameBehavior("save-the-kweebecs-kits") {

    override val displayName = "Save the Kweebecs team kits"

    override fun onActivate(ctx: MinigameContext) {
        val worldConfig = ctx.world.worldConfig
        val death = worldConfig.deathConfigOverride ?: MutableDeathConfig()
        death.itemsLossMode = DeathConfig.ItemsLossMode.NONE
        worldConfig.deathConfigOverride = death
        ctx.world.resendGameplayConfig()

        ctx.onEvent<PlayerJoinedInstanceEvent> { e ->
            if (e.instance != ctx.controllerRef) return@onEvent
            resetPlayer(ctx, e.playerEntity)
            // Pin the round's gamemode: players keep whatever mode they had in
            // the lobby (survival building, creative testing), and the round
            // expects Adventure movement + no block breaking outside the gates.
            Player.setGameMode(e.playerEntity, GameMode.Adventure, ctx.store)
            giveKit(ctx, e.playerEntity)
        }
        // Voluntary leave: go home with an empty inventory, not the kit.
        ctx.onEvent<PlayerLeftInstanceEvent> { e ->
            if (e.instance == ctx.controllerRef) {
                e.playerEntity?.let { InventoryUtils.clear(it, ctx.store) }
            }
        }
    }

    override fun onDeactivate(ctx: MinigameContext) {
        // Instance teardown deactivates behaviors before sending players home.
        for (player in ctx.players().all()) InventoryUtils.clear(player, ctx.store)
    }

    /** Fresh round state: full stats, no effects, no leftover momentum. */
    private fun resetPlayer(ctx: MinigameContext, player: Ref<EntityStore>) {
        val stats = ctx.store.getComponent(player, EntityStatMap.getComponentType())
        if (stats != null) {
            for (index in 0 until stats.size()) stats.resetStatValue(index)
        }
        ctx.store.getComponent(player, EffectControllerComponent.getComponentType())
            ?.clearEffects(player, ctx.store)
        ctx.store.getComponent(player, Velocity.getComponentType())?.setZero()
    }

    private fun giveKit(ctx: MinigameContext, playerRef: Ref<EntityStore>) {
        InventoryUtils.clear(playerRef, ctx.store)
        for (stack in kitFor(teamNameOf(ctx.store, playerRef))) {
            Player.giveItem(stack, playerRef, ctx.store)
        }
    }

    private fun kitFor(team: String?): List<ItemStack> = when (team) {
        WorldContract.TEAM_KWEEBEC -> listOf(
            ItemStack(WorldContract.KIT_KWEEBEC_ITEM, 1),
            ItemStack(WorldContract.KIT_KWEEBEC_TOOL, 1),
        )
        WorldContract.TEAM_TRORK -> listOf(ItemStack(WorldContract.KIT_TRORK_ITEM, 1))
        else -> emptyList()
    }

    private fun teamNameOf(store: Store<EntityStore>, playerRef: Ref<EntityStore>): String? {
        val teamRef = store.getComponent(playerRef, TeamRef.componentType)?.teamRef ?: return null
        return store.getComponent(teamRef, TeamData.componentType)?.name
    }
}

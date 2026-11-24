package gg.ginco.savethekweebecs

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.GameMode
import com.hypixel.hytale.server.core.asset.type.gameplay.DeathConfig
import com.hypixel.hytale.server.core.asset.type.gameplay.MutableDeathConfig
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.inventory.InventoryUtils
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.npc.role.support.StateSupport
import gg.ginco.minigame.behavior.GameBehavior
import gg.ginco.minigame.behavior.MinigameContext
import gg.ginco.minigame.component.TeamData
import gg.ginco.minigame.component.TeamRef
import gg.ginco.minigame.event.PlayerJoinedInstanceEvent

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
            // Pin the round's gamemode: players keep whatever mode they had in
            // the lobby (survival building, creative testing), and the round
            // expects Adventure movement + no block breaking outside the gates.
            Player.setGameMode(e.playerEntity, GameMode.Adventure, ctx.store)
            giveKit(ctx, e.playerEntity)
            // Prisoners spawned before this member arrived must be clickable
            // by them too (interactability is per-player on the NPC). Fresh
            // scan: stored refs would go stale as prisoners despawn.
            runCatching {
                BlockGate.forEachAlivePrisoner(ctx.store, ctx.world.name) { npcRef, _ ->
                    StateSupport.get(npcRef, ctx.store).setInteractable(e.playerEntity, true)
                }
            }
        }
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

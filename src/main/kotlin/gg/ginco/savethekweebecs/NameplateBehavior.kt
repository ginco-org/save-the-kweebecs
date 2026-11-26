package gg.ginco.savethekweebecs

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import gg.ginco.minigame.behavior.GameBehavior
import gg.ginco.minigame.behavior.MinigameContext
import gg.ginco.minigame.component.Phase
import gg.ginco.minigame.component.TeamData
import gg.ginco.minigame.component.TeamRef
import gg.ginco.minigame.event.PhaseChangedEvent
import gg.ginco.minigame.event.PlayerJoinedInstanceEvent
import gg.ginco.minigame.event.PlayerLeftInstanceEvent

/**
 * Shows a player's team in their nameplate: `[Kweebecs] Name` in the team's
 * colour. Teams are assigned at Waiting→Countdown, so the phase listener is
 * the main paint pass; late arrivals are handled by the join event.
 */
class NameplateBehavior(ctx: MinigameContext) : GameBehavior("save-the-kweebecs-nameplates") {

    override val displayName = "Save the Kweebecs team nameplates"

    override fun onActivate(ctx: MinigameContext) {
        ctx.onEvent<PlayerJoinedInstanceEvent> { e ->
            if (e.instance == ctx.controllerRef) paint(ctx.store, e.playerEntity)
        }
        ctx.onEvent<PlayerLeftInstanceEvent> { e ->
            if (e.instance == ctx.controllerRef) {
                e.playerEntity?.let { clear(ctx.store, it) }
            }
        }
        ctx.onEvent<PhaseChangedEvent> { e ->
            if (e.instance != ctx.controllerRef) return@onEvent
            if (e.new != Phase.Kind.COUNTDOWN && e.new != Phase.Kind.PLAYING) return@onEvent
            for (player in ctx.players().all()) paint(ctx.store, player)
        }
    }

    override fun onDeactivate(ctx: MinigameContext) {
        // The instance teardown strips membership after this behavior is
        // deactivated, so clear here rather than waiting for the leave event.
        for (player in ctx.players().all()) clear(ctx.store, player)
    }

    private fun paint(store: Store<EntityStore>, player: Ref<EntityStore>) {
        val playerRef = store.getComponent(player, PlayerRef.getComponentType()) ?: return
        val teamRef = store.getComponent(player, TeamRef.componentType)?.teamRef
        val team = teamRef?.takeIf { it.isValid() }
            ?.let { store.getComponent(it, TeamData.componentType) }
        if (team == null) {
            setNameplate(store, player, playerRef.username)
            return
        }
        val color = "#%06X".format(team.color and 0xFFFFFF)
        val text = Message.raw("[${team.name}] ${playerRef.username}").color(color).ansiMessage
        setNameplate(store, player, text)
    }

    private fun clear(store: Store<EntityStore>, player: Ref<EntityStore>) {
        val playerRef = store.getComponent(player, PlayerRef.getComponentType()) ?: return
        setNameplate(store, player, playerRef.username)
    }

    private fun setNameplate(store: Store<EntityStore>, player: Ref<EntityStore>, text: String) {
        val nameplate = store.getComponent(player, Nameplate.getComponentType())
        if (nameplate == null) {
            store.addComponent(player, Nameplate.getComponentType(), Nameplate(text))
        } else {
            nameplate.setText(text)
        }
    }
}

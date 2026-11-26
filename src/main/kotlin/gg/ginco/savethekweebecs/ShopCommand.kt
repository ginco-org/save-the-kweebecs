package gg.ginco.savethekweebecs

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore

/**
 * Command-driven shop; coins are player-scoped and respawn-proof. Kweebecs
 * buy infiltration gear, Trorks buy weapons and fortification blocks.
 * Offers land with item/block placement integration; the deny paths live
 * here from the start.
 */
class ShopCommand :
    AbstractPlayerCommand("shop", "savethekweebecs.commands.shop.desc") {

    init {
        // The server's default group; without it only admins reach the command.
        setPermissionGroups(com.hypixel.hytale.server.core.permissions.provider.HytalePermissionsProvider.GROUP_ADVENTURER)
    }

    override fun execute(
        context: CommandContext,
        store: Store<EntityStore>,
        player: Ref<EntityStore>,
        playerRef: PlayerRef,
        world: World,
    ) {
        val coins = Wallets.balance(playerRef.uuid)
        context.sendMessage(Message.raw(
            "Coins: $coins — visit your team's shopkeeper to spend them."))
    }
}

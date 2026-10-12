package gg.ginco.kweebecs

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore

/**
 * v1 shop (design v1): command-driven; coins are player-scoped and
 * respawn-proof. Kweebecs buy infiltration gear, Trorks buy weapons and
 * fortification blocks. Offers fill in as item/block placement APIs are
 * integrated; the deny paths live here from the start.
 */
class ShopCommand :
    AbstractPlayerCommand("shop", "kweebecs.commands.shop.desc") {

    override fun execute(
        context: CommandContext,
        store: Store<EntityStore>,
        player: Ref<EntityStore>,
        playerRef: PlayerRef,
        world: World,
    ) {
        // TODO: team gate via Membership once rounds are joined through it.
        val coins = Wallets.balance(playerRef.uuid)
        context.sendMessage(Message.raw("Shop — coins: $coins"))
        context.sendMessage(
            Message.raw("offers coming with item/block integration (DESIGN.md)"))
    }
}

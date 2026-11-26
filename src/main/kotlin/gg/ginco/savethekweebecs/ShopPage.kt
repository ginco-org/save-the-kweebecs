package gg.ginco.savethekweebecs

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.packets.interface_.CustomPage
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.ItemUtils
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage
import com.hypixel.hytale.server.core.inventory.InventoryComponent
import com.hypixel.hytale.server.core.inventory.ItemStack
import com.hypixel.hytale.server.core.ui.builder.EventData
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import gg.ginco.minigame.component.TeamData
import gg.ginco.minigame.component.TeamRef

/**
 * Team shop UI, adapted from Roshan's thankmas-vault shop page: the same
 * `ShopPage.ui` / `ShopItemCard.ui` card grid, but the currency is the
 * round's [Wallets] coins and the item list is [ShopKind.offers].
 *
 * The team gate is re-checked on every purchase: the player can change teams
 * (or lose one) while the page is open.
 */
class ShopPage(
    playerRef: PlayerRef,
    private val shop: ShopKind,
) : InteractiveCustomUIPage<ShopPage.ShopEventData>(
    playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ShopEventData.CODEC
) {
    private var isDismissed = false

    override fun build(
        ref: Ref<EntityStore>,
        commandBuilder: UICommandBuilder,
        eventBuilder: UIEventBuilder,
        store: Store<EntityStore>,
    ) {
        commandBuilder.append("SaveTheKweebecs/ShopPage.ui")
        commandBuilder.set("#ShopTitle.Text", shop.title)
        val balance = Wallets.balance(playerRef.uuid)
        commandBuilder.set("#PlayerBalance.Text", "$balance Coins")
        commandBuilder.clear("#ItemsGrid")

        for ((i, offer) in shop.offers.withIndex()) {
            val selector = "#ItemsGrid[$i]"
            commandBuilder.append("#ItemsGrid", "SaveTheKweebecs/ShopItemCard.ui")
            commandBuilder.set("$selector #ItemIcon.ItemId", offer.itemId)
            commandBuilder.set("$selector #ItemQuantity.Text",
                if (offer.quantity > 1) "${offer.quantity}x" else "")
            commandBuilder.set("$selector #ItemName.Text", offer.name)
            commandBuilder.set("$selector #ItemDescription.Text", offer.description)
            commandBuilder.set("$selector #ItemCost.Text", "${offer.cost} Coins")
            commandBuilder.set("$selector #ItemCost.Style.TextColor",
                if (balance >= offer.cost) "#a0c8e0" else "#e57373")
            commandBuilder.set("$selector #BuyButton.Disabled", balance < offer.cost)
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "$selector #BuyButton",
                EventData(mapOf(ShopEventData.ITEM_INDEX to i.toString())),
                false,
            )
        }
    }

    override fun handleDataEvent(
        ref: Ref<EntityStore>,
        store: Store<EntityStore>,
        data: ShopEventData,
    ) {
        if (isDismissed) return
        val offer = shop.offers.getOrNull(data.itemIndex) ?: return
        if (teamNameOf(store, ref) != shop.team) {
            playerRef.sendMessage(Message.raw("You are no longer on the ${shop.team} team."))
            return
        }
        val balance = Wallets.balance(playerRef.uuid)
        if (balance < offer.cost) {
            playerRef.sendMessage(Message.raw("You cannot afford ${offer.name}."))
            return
        }

        val inventory = InventoryComponent.getCombined(store, ref, *InventoryComponent.HOTBAR_FIRST)
        val stack = runCatching { ItemStack(offer.itemId, offer.quantity) }.getOrNull()
        if (stack == null) {
            playerRef.sendMessage(Message.raw("That item is unavailable right now."))
            return
        }
        if (!inventory.canAddItemStack(stack)) {
            playerRef.sendMessage(Message.raw("Your inventory is full."))
            return
        }
        if (!Wallets.spend(playerRef.uuid, offer.cost)) {
            playerRef.sendMessage(Message.raw("Transaction failed."))
            return
        }

        val transaction = runCatching { inventory.addItemStack(stack) }.getOrNull()
        if (transaction == null) {
            Wallets.earn(playerRef.uuid, offer.cost)
            playerRef.sendMessage(Message.raw("Transaction failed — coins refunded."))
            return
        }
        val remainder = transaction.remainder
        if (remainder != null && !remainder.isEmpty) {
            val added = stack.quantity - remainder.quantity
            if (added > 0) {
                val notifyStack = stack.withQuantity(added) ?: stack
                Player.notifyPickupItem(ref, notifyStack, null, store)
            }
            ItemUtils.dropItem(ref, remainder, store)
        } else {
            Player.notifyPickupItem(ref, stack, null, store)
        }

        playerRef.sendMessage(Message.raw(
            "Bought ${offer.name} for ${offer.cost} coins — balance: ${Wallets.balance(playerRef.uuid)}"))
        refreshUI(ref, store)
    }

    private fun refreshUI(ref: Ref<EntityStore>, store: Store<EntityStore>) {
        val commandBuilder = UICommandBuilder()
        val eventBuilder = UIEventBuilder()
        build(ref, commandBuilder, eventBuilder, store)
        sendUpdate(commandBuilder, eventBuilder, true)
    }

    override fun sendUpdate(commandBuilder: UICommandBuilder?, clear: Boolean) {
        sendUpdate(commandBuilder, null, clear)
    }

    override fun sendUpdate(
        commandBuilder: UICommandBuilder?,
        eventBuilder: UIEventBuilder?,
        clear: Boolean,
    ) {
        if (isDismissed) return
        val ref = playerRef.reference ?: return
        val store = ref.store
        val world = store.externalData.world
        world.execute {
            if (!ref.isValid || isDismissed) return@execute
            val player = store.getComponent(ref, Player.getComponentType()) ?: return@execute
            if (player.pageManager.customPage !== this) return@execute
            player.pageManager.updateLegacyCustomPage(
                CustomPage(
                    javaClass.name, false, clear, lifetime,
                    commandBuilder?.commands ?: UICommandBuilder.EMPTY_COMMAND_ARRAY,
                    eventBuilder?.events ?: UIEventBuilder.EMPTY_EVENT_BINDING_ARRAY,
                )
            )
        }
    }

    override fun onDismiss(ref: Ref<EntityStore>, store: Store<EntityStore>) {
        super.onDismiss(ref, store)
        isDismissed = true
    }

    private fun teamNameOf(store: Store<EntityStore>, actor: Ref<EntityStore>): String? {
        val teamRef = store.getComponent(actor, TeamRef.componentType)?.teamRef ?: return null
        return store.getComponent(teamRef, TeamData.componentType)?.name
    }

    class ShopEventData {
        var itemIndex: Int = -1

        companion object {
            const val ITEM_INDEX = "ItemIndex"

            @JvmField
            val CODEC: BuilderCodec<ShopEventData> =
                BuilderCodec.builder(ShopEventData::class.java, ::ShopEventData)
                    .append(
                        KeyedCodec(ITEM_INDEX, Codec.STRING),
                        { data, s -> data.itemIndex = s?.toIntOrNull() ?: -1 },
                        { data -> data.itemIndex.toString() },
                    ).add()
                    .build()
        }
    }
}

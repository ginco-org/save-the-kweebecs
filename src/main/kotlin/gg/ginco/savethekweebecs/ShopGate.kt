package gg.ginco.savethekweebecs

import com.hypixel.hytale.component.Archetype
import com.hypixel.hytale.component.ArchetypeChunk
import com.hypixel.hytale.component.CommandBuffer
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.query.Query
import com.hypixel.hytale.component.system.EntityEventSystem
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.UUIDComponent
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.event.events.ecs.UseEntityEvent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import gg.ginco.minigame.component.InMinigame
import gg.ginco.minigame.component.TeamData
import gg.ginco.minigame.component.TeamRef

/**
 * The shop click: using a registered shopkeeper opens [ShopPage] for the
 * clicker. Team-gated here as well as in the page, so a Kweebec cannot browse
 * the Trork shop and vice versa.
 *
 * The event is cancelled once handled — no vanilla interaction (a merchant's
 * barter shop, a hostile role's attack) may run on top of our page.
 */
class ShopGate : EntityEventSystem<EntityStore, UseEntityEvent.Pre>(UseEntityEvent.Pre::class.java) {

    override fun handle(
        index: Int,
        chunk: ArchetypeChunk<EntityStore>,
        store: Store<EntityStore>,
        buffer: CommandBuffer<EntityStore>,
        event: UseEntityEvent.Pre,
    ) {
        val npcUuid = store.getComponent(event.targetEntity, UUIDComponent.getComponentType())?.uuid
            ?: return
        val shop = ShopRegistry.get(npcUuid) ?: return
        val clickerRef = chunk.getReferenceTo(index)
        val playerRef = store.getComponent(clickerRef, PlayerRef.getComponentType()) ?: return
        if (store.getComponent(clickerRef, InMinigame.componentType)?.controllerRef != shop.controllerRef) {
            return
        }

        val team = teamNameOf(store, clickerRef)
        if (team != shop.kind.team) {
            playerRef.sendMessage(Message.raw(
                "The ${shop.kind.title} is for ${shop.kind.team} players only."))
            event.isCancelled = true
            return
        }

        event.isCancelled = true
        val world = store.externalData.world
        world.execute {
            if (!clickerRef.isValid()) return@execute
            val player = store.getComponent(clickerRef, Player.getComponentType()) ?: return@execute
            player.pageManager.openCustomPage(clickerRef, store, ShopPage(playerRef, shop.kind))
        }
    }

    override fun getQuery(): Query<EntityStore>? = Archetype.empty()

    private fun teamNameOf(store: Store<EntityStore>, actor: Ref<EntityStore>): String? {
        val teamRef = store.getComponent(actor, TeamRef.componentType)?.teamRef ?: return null
        return store.getComponent(teamRef, TeamData.componentType)?.name
    }
}

package gg.ginco.savethekweebecs

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.InteractionType
import com.hypixel.hytale.server.core.modules.entity.component.Interactable
import com.hypixel.hytale.server.core.modules.interaction.Interactions
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.hypixel.hytale.server.npc.interactions.UseNPCInteraction

/**
 * Makes a spawned NPC usable with the vanilla Use interaction.
 *
 * The client only offers Use on an entity carrying [Interactable], and
 * `UseEntityEvent.Pre` only fires when the target's [Interactions] map has a
 * `Use` entry. Roles without an `InteractionInstruction` (Kweebec_Sapling,
 * Trork_Guard) do not get either from the role builder, so the plugin supplies
 * both itself.
 */
object NpcInteraction {

    fun makeUsable(store: Store<EntityStore>, npcRef: Ref<EntityStore>) {
        store.ensureComponent(npcRef, Interactable.getComponentType())
        val interactions = store.getComponent(npcRef, Interactions.getComponentType())
            ?: store.addComponent(npcRef, Interactions.getComponentType())
        interactions.setInteractionId(InteractionType.Use, UseNPCInteraction.DEFAULT_ID)
    }
}

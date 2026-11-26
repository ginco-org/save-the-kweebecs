package gg.ginco.savethekweebecs

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Maps spawned shopkeeper NPCs to the team shop they open. */
object ShopRegistry {

    data class ShopNpc(
        val kind: ShopKind,
        val controllerRef: Ref<EntityStore>,
    )

    private val byNpcUuid = ConcurrentHashMap<UUID, ShopNpc>()

    fun register(npcUuid: UUID, kind: ShopKind, controllerRef: Ref<EntityStore>) {
        byNpcUuid[npcUuid] = ShopNpc(kind, controllerRef)
    }

    fun get(npcUuid: UUID): ShopNpc? = byNpcUuid[npcUuid]

    fun remove(npcUuid: UUID) {
        byNpcUuid.remove(npcUuid)
    }

    fun clearController(controllerRef: Ref<EntityStore>) {
        byNpcUuid.entries.removeIf { it.value.controllerRef == controllerRef }
    }
}

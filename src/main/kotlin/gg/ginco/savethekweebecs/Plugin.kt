package gg.ginco.savethekweebecs

import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import gg.ginco.minigame.behavior.GameBehaviors
import gg.ginco.minigame.util.Instances
import java.util.logging.Level

class Plugin(init: JavaPluginInit) : JavaPlugin(init) {

    override fun setup() {
        logger.at(Level.INFO).log("Save the Kweebecs set up")
        // Behaviors activate by id; the save-the-kweebecs- prefix namespaces them against other plugins.
        GameBehaviors.register("save-the-kweebecs-cages") { ctx -> CageBehavior(ctx) }
        GameBehaviors.register("save-the-kweebecs-economy") { ctx -> EconomyBehavior(ctx) }
        GameBehaviors.register("save-the-kweebecs-round") { ctx -> RoundBehavior(ctx) }
        GameBehaviors.register("save-the-kweebecs-kits") { ctx -> KitBehavior(ctx) }
        GameBehaviors.register("save-the-kweebecs-shops") { ctx -> ShopBehavior(ctx) }
        GameBehaviors.register("save-the-kweebecs-nameplates") { ctx -> NameplateBehavior(ctx) }
        // Round-critical ECS gates — see BlockGate.
        entityStoreRegistry.registerSystem(BlockGate.DamageGate())
        entityStoreRegistry.registerSystem(BlockGate.BreakGate())
        entityStoreRegistry.registerSystem(BlockGate.PlaceGate())
        entityStoreRegistry.registerSystem(BlockGate.RescueGate())
        entityStoreRegistry.registerSystem(ShopGate())
        entityStoreRegistry.registerSystem(BlockGate.KillCoinGate())
        entityStoreRegistry.registerSystem(BlockGate.RescueNpcShield())
        entityStoreRegistry.registerSystem(BlockGate.TeamDamageGate())
        entityStoreRegistry.registerSystem(BlockGate.RespawnSpawnGate())
        logger.at(Level.INFO).log("behaviors registered: %s", GameBehaviors.ids())
    }

    override fun start() {
        logger.at(Level.INFO).log("Save the Kweebecs started")
        commandRegistry.registerCommand(ShopCommand())
        logger.at(Level.INFO).log("Minigame framework reachable: no instance world(s) expected at boot")
    }

    override fun shutdown() {
        logger.at(Level.INFO).log("Save the Kweebecs shut down")
    }
}

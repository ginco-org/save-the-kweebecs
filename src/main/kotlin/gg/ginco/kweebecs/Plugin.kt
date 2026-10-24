package gg.ginco.kweebecs

import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import gg.ginco.minigame.behavior.GameBehaviors
import gg.ginco.minigame.util.Instances
import java.util.logging.Level

class Plugin(init: JavaPluginInit) : JavaPlugin(init) {

    override fun setup() {
        logger.at(Level.INFO).log("SaveTheKweebecs set up")
        // SaveTheKweebecs behaviors (see docs/DESIGN.md): games activate by
        // id; one shared classloader, so registration is idempotent and safe
        // to repeat. ids are the framework's open vocabulary, ours are
        // prefixed stk- to stay replaceable.
        GameBehaviors.register("stk-cages") { ctx -> CageBehavior(ctx) }
        GameBehaviors.register("stk-economy") { ctx -> EconomyBehavior(ctx) }
        GameBehaviors.register("stk-round") { ctx -> RoundBehavior(ctx) }
        // Round-critical ECS gates (design v1 — see StkBlockGate): block
        // identity, the rescue click, kill coins, and the kweebec shield.
        entityStoreRegistry.registerSystem(StkBlockGate.DamageGate())
        entityStoreRegistry.registerSystem(StkBlockGate.BreakGate())
        entityStoreRegistry.registerSystem(StkBlockGate.PlaceGate())
        entityStoreRegistry.registerSystem(StkBlockGate.RescueGate())
        entityStoreRegistry.registerSystem(StkBlockGate.KillCoinGate())
        entityStoreRegistry.registerSystem(StkBlockGate.RescueNpcShield())
        logger.at(Level.INFO).log("behaviors registered: %s", GameBehaviors.ids())
    }

    override fun start() {
        logger.at(Level.INFO).log("SaveTheKweebecs started")
        commandRegistry.registerCommand(ShopCommand())
        logger.at(Level.INFO).log("Minigame framework reachable: no instance world(s) expected at boot")
    }

    override fun shutdown() {
        logger.at(Level.INFO).log("SaveTheKweebecs shut down")
    }
}

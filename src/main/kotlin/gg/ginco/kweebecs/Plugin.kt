package gg.ginco.kweebecs

import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import gg.ginco.minigame.util.Instances
import java.util.logging.Level

class Plugin(init: JavaPluginInit) : JavaPlugin(init) {

    override fun setup() {
        logger.at(Level.INFO).log("SaveTheKweebecs set up")
    }

    override fun start() {
        logger.at(Level.INFO).log("SaveTheKweebecs started")
        // The framework (bundled in this jar, loaded as a sub-plugin) runs its
        // start() right after ours — see README "Framework integration". Its
        // API is safe to call from here on; this also proves the dependency
        // compiles and links against the bundled classes.
        logger.at(Level.INFO).log(
            "Minigame framework reachable: ${Instances.instanceWorlds().size} instance world(s)")
    }

    override fun shutdown() {
        logger.at(Level.INFO).log("SaveTheKweebecs shut down")
    }
}

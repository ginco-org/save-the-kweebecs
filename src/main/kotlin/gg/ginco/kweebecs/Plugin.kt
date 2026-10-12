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
        // The framework plugin is installed separately in mods/ (hard manifest
        // dependency) and shares nothing but the bridge: our classloader
        // resolves its classes through PluginBridgeClassLoader (hard deps are
        // searched first). Its start() precedes ours (dependency load order).
        // This call proves the bridge link actually resolves at runtime.
        logger.at(Level.INFO).log(
            "Minigame framework reachable: ${Instances.instanceWorlds().size} instance world(s)")
    }

    override fun shutdown() {
        logger.at(Level.INFO).log("SaveTheKweebecs shut down")
    }
}

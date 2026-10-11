package gg.ginco.kweebecs

import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import java.util.logging.Level

class Plugin(init: JavaPluginInit) : JavaPlugin(init) {

    override fun setup() {
        logger.at(Level.INFO).log("SaveTheKweebecs set up")
    }

    override fun start() {
        logger.at(Level.INFO).log("SaveTheKweebecs started")
    }

    override fun shutdown() {
        logger.at(Level.INFO).log("SaveTheKweebecs shut down")
    }
}

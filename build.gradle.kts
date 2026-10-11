plugins {
    // hygradle comes onto the classpath via gg.ginco.hygradle.settings (0.5.0) in
    // settings.gradle.kts, so no version is declared here.
    id("gg.ginco.hygradle")
    kotlin("jvm") version "2.3.21"
}

group = "gg.ginco"
version = "0.1.0"

hytale {
    group = "gg.ginco"
    name = "SaveTheKweebecs"
    description = "Save the Kweebecs minigame for Hytale"
    serverVersion = "0.6.8"
    mainClass = "gg.ginco.kweebecs.Plugin"
    // Kotlin stdlib is bundled into the plugin jar — the server doesn't provide it.
    bundleDependencies = true
}

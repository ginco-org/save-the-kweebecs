plugins {
    // hygradle comes onto the classpath via gg.ginco.hygradle.settings (0.5.1) in
    // settings.gradle.kts, so no version is declared here.
    id("gg.ginco.hygradle")
    kotlin("jvm") version "2.3.21"
}

group = "gg.ginco"
version = "0.1.0"

dependencies {
    // Minigame framework (thin artifact from Maven Central; pulls
    // hytale-codec 1.2.1 and kotlin-stdlib transitively). Bundled into the
    // Compile-time only — at RUNTIME the classes resolve through the plugin
    // bridge from the framework plugin installed in mods/ (hard dependency
    // declared below). Never bundle it: one server, one framework.
    implementation("gg.ginco:hytale-minigame-framework:0.1.1")
}

hytale {
    group = "gg.ginco"
    name = "SaveTheKweebecs"
    description = "Save the Kweebecs minigame for Hytale"
    serverVersion = "0.6.8"
    mainClass = "gg.ginco.kweebecs.Plugin"
    // No bundling: the framework must exist exactly ONCE per server (its own
    // jar in mods/), because each plugin jar gets its own PluginClassLoader —
    // two bundled copies would mean two frameworks with split static state
    // (registries, instances, commands). Framework + codec + kotlin-stdlib
    // classes resolve at runtime through the plugin bridge, which searches
    // declared hard dependencies FIRST (PluginBridgeClassLoader.loadClass0,
    // dependency order before the last-resort all-plugins pass).
    bundleDependencies = false

    // Hard dependency: the server refuses to load this plugin when the
    // framework plugin (installed from its GitHub Release fat jar) is absent.
    depends("gg.ginco:MinigameFramework", ">=0.1")
}

// bundleDependencies unpacks several jars into one (kotlin-stdlib is
// multi-release): duplicate module-info/class entries are expected and must
// be excluded rather than failing the jar task.
tasks.named<org.gradle.api.tasks.bundling.Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
}

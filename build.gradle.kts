plugins {
    // hygradle comes onto the classpath via gg.ginco.hygradle.settings (0.7.0) in
    // settings.gradle.kts, so no version is declared here.
    id("gg.ginco.hygradle")
    kotlin("jvm") version "2.3.21"
}

group = "gg.ginco"
version = "0.1.0"

dependencies {
    // Minigame framework (thin artifact from Maven Central; pulls
    // hytale-codec and kotlin-stdlib transitively). Compile-time only —
    // at RUNTIME the classes resolve through the plugin bridge from the
    // framework plugin installed in mods/ (hard dependency declared below).
    // Never bundle it: one server, one framework. 0.4.0 carries the
    // framework-owned Minigame/MinigameMap asset types, the queue flows and
    // the member events.
    implementation("gg.ginco:hytale-minigame-framework:0.4.0")
}

hytale {
    group = "gg.ginco"
    name = "SaveTheKweebecs"
    description = "Save the Kweebecs minigame for Hytale"
    serverVersion = "0.7.2"
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
    // >=0.4 — tracks the framework release we compile against; the
    // framework-owned Minigame/MinigameMap asset types that our bundled
    // pack feeds live here (docs/ASSETS.md).
    depends("gg.ginco:MinigameFramework", ">=0.4")
    // The Kotlin runtime contract: our classes bind stdlib through the
    // carrier's loader; without this hard dep the resolution would be
    // best-effort across whatever else happens to carry stdlib.
    depends("gg.ginco:KotlinForHytale", ">=0.1.2")

    // Bundle an asset pack INSIDE this plugin jar: the jar itself is registered
    // as an AssetPack (zip filesystem, PackSource.RUNTIME — see server source
    // PluginManager.registerAssetPackIfNeeded) right after setup() and before
    // start(), so assets under src/main/resources' pack layout are live by the
    // time our start() reads them; see docs/ASSETS.md.
    includesAssetPack = true
}

// bundleDependencies unpacks several jars into one (kotlin-stdlib is
// multi-release): duplicate module-info/class entries are expected and must
// be excluded rather than failing the jar task.
tasks.named<org.gradle.api.tasks.bundling.Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
}

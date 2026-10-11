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
    // plugin jar below and loaded as a sub-plugin via the manifest entry in
    // the hytale { } block — see README "Framework integration".
    implementation("gg.ginco:hytale-minigame-framework:0.1.1")
}

hytale {
    group = "gg.ginco"
    name = "SaveTheKweebecs"
    description = "Save the Kweebecs minigame for Hytale"
    serverVersion = "0.6.8"
    mainClass = "gg.ginco.kweebecs.Plugin"
    // Kotlin stdlib is bundled into the plugin jar — the server doesn't provide it.
    // Also pulls in the minigame framework + hytale-codec (see dependencies {}).
    bundleDependencies = true

    // The framework is itself a Hytale plugin (gg.ginco.minigame.Plugin); its
    // setup() must run for /minigame commands and its systems to exist. The
    // server reads one manifest.json per jar, so the framework rides along as
    // a SubPlugins entry: the server instantiates gg.ginco.minigame.Plugin
    // from this same jar/classloader (PendingLoadJavaPlugin
    // .createSubPendingLoadPlugin) and inherits missing manifest fields
    // (Group/Version/ServerVersion) from this plugin's manifest.
    subPlugin {
        name = "MinigameFramework"
        mainClass = "gg.ginco.minigame.Plugin"
    }
}

// bundleDependencies unpacks several jars into one (kotlin-stdlib is
// multi-release): duplicate module-info/class entries are expected and must
// be excluded rather than failing the jar task.
tasks.named<org.gradle.api.tasks.bundling.Jar>("jar") {
    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
}

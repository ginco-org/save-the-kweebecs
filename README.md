# Save the Kweebecs

A Hytale plugin (minigame) built with [hygradle](https://plugins.gradle.org/plugin/gg.ginco.hygradle).

## Development

Enter the dev shell (JDK 25, kotlin-language-server, ktfmt):

```bash
direnv allow   # once; or: nix develop
```

Build the plugin jar (manifest included):

```bash
./gradlew jar
```

Run a dev server with the plugin loaded — first run downloads the Hytale
server files and authenticates via device-code login:

```bash
./gradlew runServer
```

## Tasks

| Task | Description |
|------|-------------|
| `jar` | Builds the plugin jar with the generated manifest |
| `generatePluginManifest` | Regenerates `manifest.json` from the `hytale { }` block |
| `provisionServer` | Downloads the Hytale server files into `run/` without starting |
| `runServer` | Deploys the plugin and starts a dev server in `run/` |

Plugin metadata lives in the `hytale { }` block in `build.gradle.kts`.

## Asset pack

The jar bundles an asset pack (a game type asset, map assets, and a map
instance slot): see `docs/ASSETS.md`.

## Framework integration

The [hytale-minigame-framework](https://github.com/ginco-org/hytale-minigame-framework)
(`gg.ginco:hytale-minigame-framework`, Maven Central, Apache-2.0) is a
**compile + runtime dependency**: this plugin bundles it into its own jar and
the server loads it as a *sub-plugin* of SaveTheKweebecs.

### How it works

The server reads exactly one `manifest.json` per plugin jar and instantiates
its `Main` — a jar on its own cannot be a second plugin. So the framework
rides along inside our jar:

1. `implementation("gg.ginco:hytale-minigame-framework:0.1.1")` + hygradle's
   `bundleDependencies = true` unpack the framework (thin artifact; pulls
   `hytale-codec` and `kotlin-stdlib` transitively) into the plugin jar.
   The Hytale Server API stays compile-only — the running server provides it.
2. The `hytale { subPlugin { name = "MinigameFramework"; mainClass =
   "gg.ginco.minigame.Plugin" } }` entry stamps a `SubPlugins` list into our
   `manifest.json`. The server then instantiates the framework's plugin class
   from the *same* jar and classloader
   (`PendingLoadJavaPlugin.createSubPendingLoadPlugin`) and inherits missing
   manifest fields (`Group`, `Version`, …) from ours (`PluginManifest
   .inherit`); it also adds this plugin as a hard dependency of the framework,
   so unloading one unloads both.
3. The alternative — shipping two separate jars and requiring operators to
   install both — was rejected: sub-plugin bundling is the pattern the
   framework's own test harness uses (its test jar carries hytest the same
   way), and it keeps deployment to a single file.

### Lifecycle & caveats

- Sub-plugins run **after** their parent at each stage: our `setup()`/`start()`
  runs first, then the framework's. Call framework APIs from `start()` onward
  (see `Plugin.start()`); systems/commands are live once the framework has
  started (`/minigame` responds).
- The server logs a benign warning that the sub-plugin "does not specify a
  target server version": hygradle's manifest task cannot yet emit
  `ServerVersion` per sub-plugin entry. Harmless today; the server says this
  becomes a hard error in a future release — bump hygradle when it does.

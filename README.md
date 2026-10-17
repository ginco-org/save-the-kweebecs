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
**compile-time dependency and a runtime hard dependency** — it is NOT
bundled into this jar.

### How it works

Each plugin jar gets its own `PluginClassLoader`, so whatever a jar bundles
is private to it. Bundling the framework here would fork it: two frameworks
with split static state (registries, instance ids, stats) and duplicate
`/minigame` commands. Instead:

1. `implementation("gg.ginco:hytale-minigame-framework:0.4.0")` (thin
   artifact; pulls `hytale-codec` and `kotlin-stdlib` transitively) is
   compile-time only. The Hytale Server API stays compile-only — the
   running server provides it.
2. `bundleDependencies = false` keeps the framework (and everything else)
   out of our jar.
3. `depends("gg.ginco:MinigameFramework", ">=0.4")` and
   `depends("gg.ginco:KotlinForHytale", ">=0.1.2")` stamp hard dependencies
   into our `manifest.json`: the server refuses to load this plugin when
   the framework plugin or the Kotlin carrier is absent, and guarantees
   both load before ours.
4. At runtime the framework's classes resolve through the server's plugin
   bridge (`PluginBridgeClassLoader`): own jar → server classpath →
   declared hard dependencies FIRST, then everything else. Our classes
   bind the one framework instance in `mods/`, not a private copy.

### Deployment

The framework must exist exactly ONCE per server, installed in `mods/`
from its GitHub Release fat jar (`hytale-minigame-framework-x.y.z.jar`);
the Kotlin carrier (`kotlin-for-hytale-x.y.z.jar`) sits next to it. For
`./gradlew runServer` the build resolves both from the sibling checkouts
(`../hytale-minigame-framework`, `../kotlin-for-hytale`) and declares them
as `server { extraMods }` — hygradle rebuilds `run/mods/` from scratch on
every run, so jars dropped in by hand are wiped.

### Lifecycle & caveats

- Hard dependencies load before their dependents: the carrier and the
  framework are live before our `setup()`/`start()`. Call framework APIs
  from `start()` onward; systems/commands are live once the framework has
  started (`/minigame` responds).
- The framework's own README ("Packaging: one framework per server")
  documents the same loadout from the framework's side.

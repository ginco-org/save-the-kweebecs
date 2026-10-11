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

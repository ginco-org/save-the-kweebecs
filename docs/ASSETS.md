# Save the Kweebecs — bundled asset pack

The plugin jar **is** its own asset pack. `build.gradle.kts` sets
`includesAssetPack = true`, hygradle stamps `IncludesAssetPack: true` into
`manifest.json`, and the server registers the jar itself as an `AssetPack`
(zip filesystem, `PackSource.RUNTIME`) right after `setup()` returns,
dispatching `AssetPackRegisterEvent`. Anything under `src/main/resources/`
following the pack layout
ships with the jar and is live by the time plugins' `start()` rounds read it.

Save the Kweebecs ships **data only**: the asset *types* (`Minigame`, `MinigameMap`) and the
definition compilation are owned by the minigame framework (framework ≥ 0.4,
`gg/ginco/minigame/asset/Assets.kt`), which registers their asset stores in
its own `setup()`. Our pack feeds both stores via `AssetPackRegisterEvent`,
and the framework compiles our assets into `MinigameDefinition`s —
`/minigame create <gameTypeId>` plays them.

## Pack layout (as shipped)

```
Server/
├── Minigames/SaveTheKweebecs/
│   └── SaveTheKweebecs.json      # Minigame asset (id "SaveTheKweebecs"; Maps: Canyon)
├── MinigameMaps/SaveTheKweebecs/
│   └── Canyon.json               # MinigameMap asset (id "Canyon")
└── Instances/
    └── Canyon/
        ├── instance.bson         # imported authored desert world (play-adapted)
        ├── chunks/               # 12 authored region files
        └── resources/            # authored world resources; PointData tagged
```

### Canyon (imported)

Authored world (`stk_canyon`: desert canyon fort, void-gen, paused 05:30
morning, haze weather) imported from a universe save. `config.json` became
`instance.bson` with minigame adaptations (PvP on, `DeleteOnRemove`, ambient
NPC/block spawning off, static `WorldMap: Disabled`, `SpawnProvider` fixed to
authored point `spawn_kwee_1`, `WorldEmpty` removal). The author's 28 points
became the contract, with per-team spawn pools: the 10 `spawn_kwee_*` points
carry a bare `SPAWN_KWEEBECS` tag key, the 10 `spawn_tror_*` points
`SPAWN_TRORKS` — the framework's `SPAWN_<TEAM-NAME-uppercase>`
convention (`Regions.spawnTypeFor("Kweebecs")`; `PhaseSystem` sends each
player through their team's pool, `i % n` per pool, facing included, falling
back to generic `spawn`-keyed markers when a team authors none). The 8
`jail_kwee_*` points became eight `CAGE`-keyed box volumes under the same
anchors — cage sites must be volumes (`CageBehavior` reads regions, not
points).

## Asset schemas (framework-owned)

**Minigame** — `Server/Minigames/…/<gameTypeId>.json` (id = file name):

| Field | Type | Meaning |
|---|---|---|
| `DisplayName` | string | shown to players |
| `Maps` | string[] | map asset ids this game type is playable on; empty = legacy in-world mode |
| `DefaultMap` | string | which listed map the bare `SaveTheKweebecs` definition points at; required once more than one map is listed |
| `Behaviors` | string[] | `GameBehaviors` ids activated for every instance (`save-the-kweebecs-cages`, `save-the-kweebecs-economy`, `save-the-kweebecs-round`) |
| `Teams` | TeamDefinition[] | `{ Name, Color (int), MaxPlayers (-1 = unlimited) }` — the same codec as saved definitions |
| `MinPlayers` | int | auto-start floor: the queue moves Waiting → Countdown once this many players are in (`<= 0` = manual start only). Shipped value: 2 |
| `MaxPlayers` | int | join cap; `<= 0` = uncapped. Shipped value: 20 (10 authored spawn points per side) |

A **map** asset may override both per map (`MinPlayers`/`MaxPlayers` on the
`MinigameMap` asset; absent = inherit the minigame's values).

**MinigameMap** — `Server/MinigameMaps/…/<mapId>.json` (id = file name):

| Field | Type | Meaning |
|---|---|---|
| `DisplayName` | string | map name |
| `Instance` | string (required) | instance asset name under `Server/Instances/` carrying the map's chunks and region contract |

Both types support asset `Parent` inheritance (every field) since the
framework's codecs are `AssetBuilderCodec`s.

## Resolved plays and maps

For minigame `SaveTheKweebecs` the framework compiles **one definition** (`SaveTheKweebecs` — teams,
behaviors, default map's instance). Asset ids are the file name minus extension, and Hytale
asset keys must be PascalCase (capitalized first letter — the vanilla asset store rejects
lowercase file names with an `incorrect format` warning), so pack ids carry no dashes and
read as one word: `SaveTheKweebecs`, `Canyon`. Where it is played comes from the
resolution (`DefinitionStore.resolve`) behind the player-facing
`/minigame queue <minigame> [<map>]` (queued/balanced/auto-started) and the
admin `/minigame create` (raw/testing):

- `/minigame queue SaveTheKweebecs` — joins (or creates) the open queue; a **random
  playable** map when none is running and no map was named
- `/minigame queue SaveTheKweebecs Canyon` — queueing that map's instance
- `/minigame create SaveTheKweebecs [map]` stays the admin/testing surface (bare creates,
  forced starts via `/minigame start`)
- a minigame with no maps (or a saved data-dir definition) keeps the legacy
  in-world mode

Data-dir definitions (`run/mods/gg.ginco_MinigameFramework/definitions/`)
own their game type id end to end (teams, behaviors, instance) — an editor
`/minigame defs save` shadows the shipped asset. A `<map>` argument works
through the shadow too (it overrides the instance target, teams stay the
shadow's). Authoring errors (unknown map ids, missing instance assets, no
teams) log loudly at boot from the framework and refuse cleanly at create
time.

## Authoring a map (instance)

1. **Build the world.** Author geometry plus the contract regions of
   `WorldContract` (`SPAWN_KWEEBECS` / `SPAWN_TRORKS` spawn types, one `CAGE`
   volume/point per cage, optional `SHOP_*`): build in a creative/edit world,
   author regions with the vanilla point/volume tools (a region is any point
   or trigger volume carrying a bare `<type>` tag key with no value — spawn
   points keyed `SPAWN_<TEAM>`, cage volumes keyed `CAGE` — the tool sets
   keys without values; see the Canyon import for a worked example), or edit
   the `PointData.json`/`TriggerVolumeData.json`
   resources directly. If the edit world's worldgen is
   non-void, make sure every generated chunk you depend on is pregenerated —
   the instance ships its own `chunks/` and generates nothing else (template
   worldgen is `Void`).
2. **Extract the folder** that defines the world (`instance.bson`, `chunks/`,
   `resources/`) from the edit world's universe directory, or export it from a
   development pack loaded via the server's `--assets` flag (hot-reloadable;
   the packaged jar is read-only in spirit — zip-backed).
3. **Drop it into the pack**: `src/main/resources/Server/Instances/<Name>/` —
   and check the `SpawnProvider`/`Seed`/time values copied from the edit world
   make sense as instanced defaults.
4. **Register it**: add `Server/MinigameMaps/SaveTheKweebecs/<MapId>.json`
   (PascalCase id — Hytale asset keys) with `Instance: "<Name>"` and list the map id in
   `SaveTheKweebecs.json`'s `Maps`. After
   a restart (or plugin reload), `/minigame defs list` shows the compiled
   definitions and `/minigame create SaveTheKweebecs` teleports out of the default world.

## Runtime notes

- The framework requires **≥ 0.4** (`depends("gg.ginco:MinigameFramework",
  ">=0.4")`): the asset types and the definition compiler live there.
- Each minigame plugin's pack registers after that plugin's `setup0()`, so the
  framework's stores (registered during the framework's earlier `setup()`)
  receive the pack through `AssetPackRegisterEvent` — pack order cannot strand
  assets.
- `initPendingStores` logs a benign `SEVERE "Path … isn't a directory"` per
  pack lacking `Server/Minigames` during framework setup — expected noise.
- Asset edits need a plugin/server reload to recompile; `/minigame defs
  reload` only re-reads the data-dir layer.

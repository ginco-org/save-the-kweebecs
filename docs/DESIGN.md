# Save the Kweebecs — game design

Two-team asymmetric PvP. Authoritative rules captured here; behavior code
must match this document, and this document changes only through commit
messages that explain the playtest reason.

## Teams

| Team | Role |
|---|---|
| **Kweebecs** | Attackers: infiltrate the Trork fort and rescue their caged friends |
| **Trorks** | Defenders: keep the cages intact until the round ends |

## Objectives

- The Trork fort contains **cages**, each holding an imprisoned NPC kweebec
  (spawned by the plugin at the site's center, role `Intelligent/Neutral/Kweebec`;
  positions differ per map, same *count* is a design constant per map).
- Kweebec rescue sequence per cage: **break the cage → click the kweebec**
  (two steps, both server-verified: the break only OPENS the site; the click
  on the kweebec NPC rescues them). A rescued kweebec is saved permanently
  for the round and the NPC despawns.
- **Kweebecs win** when every caged kweebec is rescued before the round
  timer runs out (`Phase.ending(WIN_CONDITION)`).
- **Trorks win** when the timer expires with any kweebec still unrescued
  (`Phase.ending(TIMEOUT)`).
- The winner is announced to the instance in chat; Ending is a short
  teardown window (no results board yet).
- Caged kweebecs are **unkillable** (damage shielded) — the rescue is the
  click, not a kill.
- Players **respawn** (eliminations feed the economy but never end the
  round early — revisit only if playtest disagrees).

## Kits

- Members receive their team's **basic kit** the moment they join the
  instance (and again on reconnect — a disconnect destroys the entity and
  its inventory): Kweebecs a tribal spear, Trorks a stone trork club
  (`WorldContract` kit ids). Joining replaces whatever the player carried in.
- **Items are never dropped on death**: the instance world's death config
  is pinned to `ItemsLossMode.NONE` at activation, so kits persist across
  respawns — the economy, not item loss, is the death consequence.

## Economy

- Both teams earn **coins**: kills for anyone (PvP inside the round),
  rescues for kweebecs.
- Coins are player-scoped and persist across respawns within the round
  (dying keeps your coins; the economy rewards participation, not
  survival).
- Coins live in the instance stats (`kills`, `rescues`, `coins` per
  player — the ledger) mirrored into an in-memory wallet for fast reads;
  `/shop` shows the balance, the shopkeeper NPCs spend from the wallet.
- Each team has an in-world **shopkeeper NPC** at every `KWEEBEC_SHOP` /
  `TRORK_SHOP` point. Clicking one opens the coin shop page; the other
  team's shopkeeper refuses the click.

| Team | Shop |
|---|---|
| Kweebecs | pickaxes, spears, health potions |
| Trorks | trork weapons, bombs, potions |

- Trork-placed **fortification blocks** are the **only breakable blocks on
  the map**, alongside the cage bars. All world blocks (the fort shell,
  terrain) are indestructible.
  (Enforced server-side by BLOCK IDENTITY, not tracked coordinates: the
  block gate cancels damage/break on everything whose type is not one of
  the two breakable ids — no per-block bookkeeping anywhere.)

## World contract (both the dev map and the real world obey this)

The world asset *itself* carries the geometry; the plugin reads only these
**region/type contracts** (framework regions are world content, so the real
world ships with the same types once region-authoring lands):

| Contract | Meaning |
|---|---|
| `SPAWN_KWEEBECS` / `SPAWN_TRORKS` | spawn points per team — the framework derives the type id as `SPAWN_` + the uppercase team name (`Regions.spawnTypeFor`) |
| `CAGE` region per cage site | cage anchor; the plugin opens the cage, spawns the kweebec NPC, and tracks the rescue state at that site |
| `SHOP_KWEEBEC` / `SHOP_TRORK` (optional) | shopkeeper spawn points; the NPC is spawned at the point and opens that team's shop on click |

Behavior code never hardcodes coordinates — it looks up the contract and
fails loudly at instance start when the world is missing it (better a red
console than silent wrong-plugin-on-wrong-map behavior).

## Framework mapping

- 2 teams, round phases via `Phases` (Waiting → Countdown → Playing →
  Ending → Cleanup); the round clock starts at the Countdown→Playing
  transition (`PhaseChangedEvent`), and both win paths end the round via
  `Phases.set` + `Phase.ending(reason)`.
- Behaviors (`save-the-kweebecs-cages`, `save-the-kweebecs-economy`,
  `save-the-kweebecs-round`, `save-the-kweebecs-kits`,
  `save-the-kweebecs-shops`) + ECS gate systems (block
  damage/break/place, rescue click, shop click, kill coins, kweebec
  shield — see `BlockGate.kt` and `ShopGate.kt`) registered once at plugin
  setup, active only in gated instance worlds.
- Coins: `InstanceStats` counters (per-player `kills`/`rescues`/`coins` +
  instance totals) with the wallet as the fast read layer.

## Deliberately out of scope

- NPC trork mobs (both teams are players; caged kweebecs and the two
  shopkeepers are the only NPCs)
- Results board at Ending (a chat announcement covers it)
- Ranked/balancing systems, multiple rounds, map rotation

## Server mechanism notes

- **Block breakability gate**: `DamageBlockEvent` / `BreakBlockEvent`
  (cancellable ECS events carrying the target block) are consumed by
  `EntityEventSystem`s registered on the entity-store registry (the
  `TriggerVolumeBlockEvents.BlockBroken` builtin pattern; queries are
  `Archetype.empty()` — the dispatch hands the event's own entity chunk).
  The plugin gates them: cancel everything whose target block is not one
  of the two breakable ids. Cage breaks inside a `CAGE` region flip that
  site INTACT → BROKEN (site state lives in `BlockGate`, keyed by
  world + site id); the plugin removes the cage block itself so it never
  drops an item.
- **Block placement**: `PlaceBlockEvent` cancels for non-Trork actors.
- **Rescue click**: `UseEntityEvent.Pre` (ECS event dispatched for the
  interacting player, `getTargetEntity()` = the kweebec NPC) completes the
  rescue when the site is BROKEN and the clicker is a Kweebec. Kweebec
  NPCs spawn via `NPCPlugin.spawnNPC(store, role, null, position,
  rotation)`, are frozen (`Frozen`) so they have no wander AI, and are
  marked interactable per player — including players who join after the
  prisoners spawn — so the client offers the click interaction. They are
  registered by uuid → site for the click lookup and the damage shield.
- **Shop click**: `UseEntityEvent.Pre` on a registered shopkeeper NPC
  checks the clicker's `InMinigame` link and team, cancels the vanilla
  interaction and opens `ShopPage`. Purchases re-check the team and spend
  from `Wallets` before adding the item.
- **Kill coins**: `KillFeedEvent.Display` (damage module, dispatched for
  the decedent) names the killer through `Damage.EntitySource.ref`; both
  parties must carry the instance membership (`InMinigame`) for the award
  to pay.
- **Threading**: ECS event handlers may run mid-scan; store writes
  (stats, messages, `Phases.set`, `removeEntity`) are deferred to the
  world thread via `world.execute { … }` from every gate.
- **Timer**: `MinigameContext.runLater` (tick-scheduled, world thread)
  started by the Playing transition; expiry re-checks the live phase
  before declaring the Trork win.

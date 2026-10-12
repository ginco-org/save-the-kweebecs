# Save the Kweebecs — game design (v1)

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
  (present in the world asset; positions differ per map, same *count* is a
  design constant per map).
- Kweebec rescue sequence per cage: **break the cage → click the kweebec
  inside** (two steps; a rescued kweebec is saved permanently for the
  round).
- **Kweebecs win** when every cage is freed before the round timer runs out.
- **Trorks win** when the timer expires with any cage intact.
- Players **respawn** (eliminations feed the economy but never end the
  round early in v1 — revist only if playtest disagrees).

## Economy

- Both teams earn **coins** from kills (and rescues for kweebecs).
- Coins are player-scoped and persist across respawns within the round
  (dying keeps your coins; the economy rewards participation, not
  survival).
- `/stk shop` (v1: command shop; an in-world shopkeeper NPC can come later)

| Team | Shop |
|---|---|
| Kweebecs | weapons and infiltration gear |
| Trorks | weapons **and fortification blocks** |

- Trork-placed **fortification blocks** are the **only breakable blocks on
  the map**. All world blocks (the fort shell, terrain) are indestructible.
  (Enforced server-side: the plugin owns the coordinate set of
  trork-placed blocks and gates block-damage accordingly.)

## World contract (both the dev map and the real world obey this)

The world asset *itself* carries the geometry; the plugin reads only these
**region/type contracts** (framework regions are world content, so the real
world ships with the same types once region-authoring lands):

| Contract | Meaning |
|---|---|
| `SPAWN_TEAM_KWEEBEC` / `SPAWN_TEAM_TRORK` | spawn points per team |
| `CAGE` region/point per cage site | cage anchor; the plugin manages the cage state at that anchor |
| `SHOP_KWEEBEC` / `SHOP_TRORK` (optional v1.1) | shop interaction zones |

Behavior code never hardcodes coordinates — it looks up the contract and
fails loudly at instance start when the world is missing it (better a red
console than silent wrong-plugin-on-wrong-map behavior).

## Framework mapping

- 2 teams, round phases via `Phases` (Waiting → Countdown → Playing →
  Ending → Cleanup); Ending = results board.
- Coins: `InstanceStats` counters (per-player + total) re-exported to the
  player as "coins" (kill events → award; rescue → award).
- Behaviors: a `SaveTheKweebecsGame` behavior family — cage management,
  rescue interaction, round timer, economy hooks.

## Deliberately out of v1

- NPC trork mobs (both teams are players; caged kweebecs are the only NPCs)
- Shopkeeper UI (command shop first)
- Ranked/balancing systems, multiple rounds, map rotation

## Server mechanism notes (verified against the 0.6.8 shared source)

- **Block breakability gate**: `DamageBlockEvent` / `BreakBlockEvent`
  (cancellable ECS events carrying the target block + damage amount) are
  consumed by registering entity-event systems — see
  `TriggerVolumeBlockEventSystems.BlockBroken` for the builtin pattern.
  The plugin gates them: cancel everything whose target block is not in
  the plugin-owned breakable set (trork-placed blocks + cage blocks).
- **Block placement**: `WorldChunk.setBlock(x, y, z, blockType)` (+ id
  overloads) for both the dev-map build-out and trork fortification.
- **Rescue click**: `UseEntityEvent.Pre` (interactable NPC entity at the
  freed cage) for "click the kweebec"; block-gated interactions via
  `LivingEntityUseBlockEvent` if the cage stays a block in v1.
- **Economy hooks**: `KillFeedEvent` (damage module) carries
  killer/decedent for coin awards; player deaths respawn via the vanilla
  flow (design: respawn, keep coins).

package gg.ginco.savethekweebecs

/**
 * The world-content contract: region types and counts the game looks up in
 * the instance world (framework regions are world content, so both the dev
 * map and the real world speak these). Behavior code reads these names and
 * fails loudly when a world is missing one — never silently run the game on
 * the wrong map.
 */
object WorldContract {
    /** Framework SPAWN-* type suffixed per team — PhaseSystem teleports by spawn type.
     *  The framework derives the type id as `SPAWN_` + the uppercase team name
     *  (Regions.spawnTypeFor): "Kweebecs" → SPAWN_KWEEBECS, "Trorks" → SPAWN_TRORKS.
     *  Author per-team spawn markers carrying a bare `<that id>` tag key (no
     *  value — the point tool sets keys without values); teams without their
     *  own markers fall back to the generic `spawn` pool. */
    const val SPAWN_KWEEBECS = "SPAWN_KWEEBECS"
    const val SPAWN_TRORKS = "SPAWN_TRORKS"

    /** One per cage site: the plugin manages cage state at this anchor. */
    const val CAGE = "CAGE"

    const val TEAM_KWEEBEC = "Kweebecs"
    const val TEAM_TRORK = "Trorks"

    // The only breakable block ids (see BlockGate): the cage bars, and
    // the fortification wall trorks place.
    const val CAGE_BLOCK_ID = "Deco_Trork_Cage"
    const val BARRICADE_BLOCK_ID = "Wood_Village_Wall_Full"

    const val ROUND_SECONDS = 7 * 60

    /** The NPC role the caged kweebecs spawn with (game asset `NPC/Attitude/Roles/…`). */
    const val CAGED_KWEEBEC_ROLE = "Intelligent/Neutral/Kweebec"

    const val COINS_PER_KILL = 10
    const val COINS_PER_RESCUE = 25

    /** Framework stat ids double as the coin ledger (player-scoped + total). */
    const val STAT_KILLS = "kills"
    const val STAT_RESCUES = "rescues"
    const val STAT_COINS = "coins"
}

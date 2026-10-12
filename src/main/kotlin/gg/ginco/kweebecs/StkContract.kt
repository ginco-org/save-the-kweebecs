package gg.ginco.kweebecs

/**
 * The world-content contract: region types and counts the game looks up in
 * the instance world (framework regions are world content, so both the dev
 * map and the real world speak these). Behavior code reads these names and
 * fails loudly when a world is missing one — never silently run the game on
 * the wrong map.
 */
object StkContract {
    /** Framework SPAWN-* type suffixed per team — PhaseSystem teleports by spawn type. */
    const val SPAWN_KWEEBEC = "SPAWN_KWEEBEC"
    const val SPAWN_TRORK = "SPAWN_TRORK"

    /** One per cage site: the plugin manages cage state at this anchor. */
    const val CAGE = "CAGE"

    /** Round contract (design v1): cages freed before timer expiry wins. */
    const val ROUND_SECONDS = 7 * 60

    /** Economy: coin awards. */
    const val COINS_PER_KILL = 10
    const val COINS_PER_RESCUE = 25

    /** Framework stat ids double as the coin ledger (player-scoped + total). */
    const val STAT_KILLS = "kills"
    const val STAT_RESCUES = "rescues"
    const val STAT_COINS = "coins"
}

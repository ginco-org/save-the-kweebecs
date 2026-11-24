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
    /**
     * Blocks that count as cage bars — all breakable, and breaking any of
     * them opens the enclosing CAGE site. The authored Canyon cages are
     * built from the Deco_Iron_Bars family (connected variants have their
     * own ids); Deco_Trork_Cage is the dev-map placeholder stamp.
     */
    val CAGE_BLOCK_IDS = setOf(
        "Deco_Iron_Bars",
        "Deco_Iron_Bars_Corner",
        "Deco_Iron_Bars_Platforms",
        "Deco_Trork_Cage",
    )

    /** The dev-map placeholder cage stamp ([CageBehavior]). */
    const val CAGE_STAMP_BLOCK_ID = "Deco_Trork_Cage"

    /** The fortification wall trorks place (the other breakable kind). */
    const val BARRICADE_BLOCK_ID = "Wood_Village_Wall_Full"

    const val ROUND_SECONDS = 7 * 60

    /** The NPC role the caged kweebecs spawn with. Role builder ids are BARE
     *  FILENAMES of spawnable role assets under Server/NPC/Roles/…
     *  (BuilderManager.builderNameFromPath strips the directory + extension).
     *  Kweebec_Prisoner is "Type": "Abstract" — abstract roles are excluded
     *  from the spawnable builder registry; Kweebec_Sapling (a Variant) is
     *  spawnable (verified at runtime: getRoleTemplateNames hasSapling=true). */
    const val CAGED_KWEEBEC_ROLE = "Kweebec_Sapling"

    const val COINS_PER_KILL = 10
    const val COINS_PER_RESCUE = 25

    /** Basic kit items each member receives on entering the instance (vanilla item ids).
     *  Kweebecs carry a pickaxe: the cage bars are the rescue path. */
    const val KIT_KWEEBEC_ITEM = "Weapon_Spear_Tribal"
    const val KIT_KWEEBEC_TOOL = "Tool_Pickaxe_Iron"
    const val KIT_TRORK_ITEM = "Weapon_Club_Stone_Trork"

    /** Framework stat ids double as the coin ledger (player-scoped + total). */
    const val STAT_KILLS = "kills"
    const val STAT_RESCUES = "rescues"
    const val STAT_COINS = "coins"
}

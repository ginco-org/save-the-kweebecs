package gg.ginco.savethekweebecs

/**
 * The two team shops. Offers are deliberately data-simple (no asset store) so
 * balancing lives next to the game rules; item ids are vanilla assets.
 */
data class ShopOffer(
    val itemId: String,
    val name: String,
    val description: String,
    val cost: Int,
    val quantity: Int = 1,
)

enum class ShopKind(
    val team: String,
    val title: String,
    val npcRole: String,
    val offers: List<ShopOffer>,
) {
    KWEEBEC(
        WorldContract.TEAM_KWEEBEC,
        "Kweebec Shop",
        "Kweebec_Sapling",
        listOf(
            ShopOffer("Tool_Pickaxe_Iron", "Iron Pickaxe",
                "A sturdier pickaxe for opening cages faster.", 20),
            ShopOffer("Potion_Health_Small", "Small Health Potion",
                "Restores a little health.", 12, 2),
            ShopOffer("Potion_Regen_Health_Small", "Health Regeneration Potion",
                "Regenerates health over time.", 18),
            ShopOffer("Weapon_Spear_Tribal", "Tribal Spear",
                "A balanced spear for holding the line.", 25),
            ShopOffer("Weapon_Spear_Iron", "Iron Spear",
                "More reach, more damage.", 45),
        ),
    ),
    TRORK(
        WorldContract.TEAM_TRORK,
        "Trork Shop",
        "Trork_Guard",
        listOf(
            ShopOffer("Weapon_Club_Stone_Trork", "Stone Trork Club",
                "A brutal close-range club.", 20),
            ShopOffer("Weapon_Sword_Stone_Trork", "Stone Trork Sword",
                "A heavier trork blade.", 35),
            ShopOffer("Potion_Health_Small", "Small Health Potion",
                "Restores a little health.", 12, 2),
            ShopOffer("Potion_Stamina_Small", "Small Stamina Potion",
                "Keeps you swinging.", 12, 2),
            ShopOffer("Weapon_Bomb", "Bomb",
                "Lob it at the kweebecs.", 25),
            ShopOffer("Weapon_Bangstick", "Bangstick",
                "A noisy surprise for cage-breakers.", 30),
        ),
    );

    companion object {
        fun forTeam(teamName: String?): ShopKind? =
            entries.firstOrNull { it.team == teamName }
    }
}

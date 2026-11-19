package com.reeware.android.breed.engine

/**
 * Dauerhaft ausrüstbare Bio-Waffen für die gesamte Alien-Brut (In-App-Käufe).
 * Alle Effekte sind zu 100 % deterministisch in TurnSimulator berechenbar.
 */
enum class BioWeapon(
    val productId: String,
    private val titleDe: String,
    private val titleEn: String,
    private val bonusDe: String,
    private val bonusEn: String
) {
    PLASMA_VENOM(
        productId = "breed_weapon_plasma_venom",
        titleDe = "Plasma-Giftdrüsen",
        titleEn = "Plasma Venom Glands",
        bonusDe = "+1 Angriff & +1 Rüstungsdurchbruch für alle Aliens.",
        bonusEn = "+1 Attack & +1 Armor Piercing for all aliens."
    ),
    CHITIN_BLADES(
        productId = "breed_weapon_chitin_blades",
        titleDe = "Sensen-Chitin-Panzer",
        titleEn = "Scythe Chitin Armor",
        bonusDe = "+2 HP & +1 Rüstung für alle Aliens gegen Waffenfeuer.",
        bonusEn = "+2 HP & +1 Armor for all aliens against gunfire."
    );

    val title: String get() = GameLocale.tr(titleDe, titleEn)
    val bonusHint: String get() = GameLocale.tr(bonusDe, bonusEn)
}

/** Kategorien für den In-App-Shop (Google Play Billing). */
enum class ShopCategory {
    ALIEN,
    WEAPON,
    BUNDLE
}

/**
 * Katalog der optionalen Google-Play-In-App-Produkte (Einmalkäufe / Non-Consumables).
 * Die Produkt-IDs entsprechen exakt den Einträgen in der Google Play Console.
 */
enum class ShopProduct(
    val productId: String,
    val category: ShopCategory,
    private val titleDe: String,
    private val titleEn: String,
    private val descDe: String,
    private val descEn: String,
    val fallbackPriceDe: String,
    val fallbackPriceEn: String,
    val unlockedSpecies: Set<AlienSpecies> = emptySet(),
    val unlockedWeapons: Set<BioWeapon> = emptySet()
) {
    ALIEN_REAPER(
        productId = "breed_alien_reaper",
        category = ShopCategory.ALIEN,
        titleDe = "Elite-Alien: Klingen-Bestie",
        titleEn = "Elite Alien: Blade Reaper",
        descDe = "Neuer Vent-Assassine mit 4 Angriff, 6 HP und 2 Rüstungsdurchbruch + eigenem Mutations-Baum.",
        descEn = "New Vent assassin with 4 Attack, 6 HP, and 2 Armor Piercing + dedicated mutation tree.",
        fallbackPriceDe = "1,99 €",
        fallbackPriceEn = "$1.99",
        unlockedSpecies = setOf(AlienSpecies.REAPER)
    ),
    ALIEN_BOMBER(
        productId = "breed_alien_bomber",
        category = ShopCategory.ALIEN,
        titleDe = "Elite-Alien: Sporen-Bomber",
        titleEn = "Elite Alien: Spore Bomber",
        descDe = "Lebende Bio-Artillerie (RW 2), explodiert beim Ableben in 3×3-Säuredetonation + eigenem Mutations-Baum.",
        descEn = "Living bio-artillery (range 2) that erupts in a 3×3 acid blast on death + dedicated mutation tree.",
        fallbackPriceDe = "1,99 €",
        fallbackPriceEn = "$1.99",
        unlockedSpecies = setOf(AlienSpecies.BOMBER)
    ),
    WEAPON_PLASMA_VENOM(
        productId = "breed_weapon_plasma_venom",
        category = ShopCategory.WEAPON,
        titleDe = "Bio-Waffe: Plasma-Giftdrüsen",
        titleEn = "Bio-Weapon: Plasma Venom Glands",
        descDe = "Rüste deine gesamte Brut dauerhaft mit +1 Angriff & +1 Rüstungsdurchbruch aus.",
        descEn = "Permanently equip your whole brood with +1 Attack & +1 Armor Piercing.",
        fallbackPriceDe = "1,49 €",
        fallbackPriceEn = "$1.49",
        unlockedWeapons = setOf(BioWeapon.PLASMA_VENOM)
    ),
    WEAPON_CHITIN_BLADES(
        productId = "breed_weapon_chitin_blades",
        category = ShopCategory.WEAPON,
        titleDe = "Bio-Waffe: Sensen-Chitin-Panzer",
        titleEn = "Bio-Weapon: Scythe Chitin Armor",
        descDe = "Verstärkt jede Einheit dauerhaft mit +2 HP & +1 Rüstung gegen Laser, Panzer & Flammen.",
        descEn = "Permanently reinforces every unit with +2 HP & +1 Armor against lasers, tanks & flames.",
        fallbackPriceDe = "1,49 €",
        fallbackPriceEn = "$1.49",
        unlockedWeapons = setOf(BioWeapon.CHITIN_BLADES)
    ),
    APEX_BUNDLE(
        productId = "breed_pack_apex_arsenal",
        category = ShopCategory.BUNDLE,
        titleDe = "Apex-Schwarm & Arsenal (Komplett)",
        titleEn = "Apex Brood & Arsenal (Complete)",
        descDe = "Schaltet beide Elite-Aliens (Klingen-Bestie & Sporen-Bomber) sowie beide Bio-Waffen dauerhaft frei!",
        descEn = "Permanently unlocks both Elite Aliens (Blade Reaper & Spore Bomber) plus both Bio-Weapons!",
        fallbackPriceDe = "4,99 €",
        fallbackPriceEn = "$4.99",
        unlockedSpecies = setOf(AlienSpecies.REAPER, AlienSpecies.BOMBER),
        unlockedWeapons = setOf(BioWeapon.PLASMA_VENOM, BioWeapon.CHITIN_BLADES)
    );

    val title: String get() = GameLocale.tr(titleDe, titleEn)
    val description: String get() = GameLocale.tr(descDe, descEn)
    val fallbackPrice: String get() = GameLocale.tr(fallbackPriceDe, fallbackPriceEn)

    companion object {
        val allProductIds: List<String> = entries.map { it.productId }

        fun byProductId(id: String): ShopProduct? = entries.firstOrNull { it.productId == id }

        fun isSpeciesUnlocked(species: AlienSpecies, ownedProductIds: Set<String>): Boolean {
            if (!species.isPremium) return true
            return entries.any { it.productId in ownedProductIds && species in it.unlockedSpecies }
        }

        fun isWeaponUnlocked(weapon: BioWeapon, ownedProductIds: Set<String>): Boolean =
            entries.any { it.productId in ownedProductIds && weapon in it.unlockedWeapons }

        fun isProductOwned(product: ShopProduct, ownedProductIds: Set<String>): Boolean {
            if (product.productId in ownedProductIds) return true
            if (product != APEX_BUNDLE && APEX_BUNDLE.productId in ownedProductIds) return true
            return product.unlockedSpecies.all { isSpeciesUnlocked(it, ownedProductIds) } &&
                product.unlockedWeapons.all { isWeaponUnlocked(it, ownedProductIds) }
        }
    }
}

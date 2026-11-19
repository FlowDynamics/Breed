package com.reeware.android.breed

import com.reeware.android.breed.data.BuiltInCampaign
import com.reeware.android.breed.engine.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class LocalizationAndShopTest {

    @After
    fun restoreDefaultLocale() {
        GameLocale.current = AppLanguage.DE
    }

    @Test
    fun switchingBetweenGermanAndEnglishTranslatesCoreEntitiesAndCampaign() {
        GameLocale.current = AppLanguage.DE
        assertEquals("Lauerer", AlienSpecies.STALKER.displayName)
        assertEquals("Klingen-Bestie", AlienSpecies.REAPER.displayName)
        assertEquals("1. Frachtschleuse", BuiltInCampaign.levels.first().title)
        assertEquals("Brutkammer", RoomUpgrade.BROOD_CHAMBER.displayName)
        assertEquals("Plasma-Giftdrüsen", BioWeapon.PLASMA_VENOM.title)

        GameLocale.current = AppLanguage.EN
        assertEquals("Stalker", AlienSpecies.STALKER.displayName)
        assertEquals("Blade Reaper", AlienSpecies.REAPER.displayName)
        assertEquals("1. Cargo Airlock", BuiltInCampaign.levels.first().title)
        assertEquals("Brood Chamber", RoomUpgrade.BROOD_CHAMBER.displayName)
        assertEquals("Plasma Venom Glands", BioWeapon.PLASMA_VENOM.title)
        assertEquals("E", Direction.EAST.label)
    }

    @Test
    fun shopEntitlementsUnlockBaseAndPurchasedItemsProperly() {
        // 5 core species are always free; 2 elite species require IAP
        assertTrue(ShopProduct.isSpeciesUnlocked(AlienSpecies.STALKER, emptySet()))
        assertTrue(ShopProduct.isSpeciesUnlocked(AlienSpecies.PSION, emptySet()))
        assertFalse(ShopProduct.isSpeciesUnlocked(AlienSpecies.REAPER, emptySet()))
        assertFalse(ShopProduct.isSpeciesUnlocked(AlienSpecies.BOMBER, emptySet()))
        assertFalse(ShopProduct.isWeaponUnlocked(BioWeapon.PLASMA_VENOM, emptySet()))

        // Individual alien purchase unlocks only that alien
        val withReaper = setOf(ShopProduct.ALIEN_REAPER.productId)
        assertTrue(ShopProduct.isSpeciesUnlocked(AlienSpecies.REAPER, withReaper))
        assertFalse(ShopProduct.isSpeciesUnlocked(AlienSpecies.BOMBER, withReaper))

        // Apex bundle unlocks all elite aliens and all bio-weapons
        val withBundle = setOf(ShopProduct.APEX_BUNDLE.productId)
        assertTrue(ShopProduct.isSpeciesUnlocked(AlienSpecies.REAPER, withBundle))
        assertTrue(ShopProduct.isSpeciesUnlocked(AlienSpecies.BOMBER, withBundle))
        assertTrue(ShopProduct.isWeaponUnlocked(BioWeapon.PLASMA_VENOM, withBundle))
        assertTrue(ShopProduct.isWeaponUnlocked(BioWeapon.CHITIN_BLADES, withBundle))
        assertTrue(ShopProduct.isProductOwned(ShopProduct.ALIEN_REAPER, withBundle))
    }

    @Test
    fun reaperArmorPiercingAndBioWeaponsWorkDeterministically() {
        val cells = buildList {
            for (y in 0 until 5) {
                for (x in 0 until 5) {
                    val pos = GridPos(x, y)
                    val type = when {
                        x == 0 || y == 0 || x == 4 || y == 4 -> TileType.WALL
                        pos == GridPos(2, 3) -> TileType.CREEP
                        else -> TileType.FLOOR
                    }
                    add(TileCell(pos, type))
                }
            }
        }
        val level = LevelDefinition(
            id = "test_reaper_tank",
            title = "Reaper vs Tank",
            sectorName = "Test",
            hint = "",
            planetTheme = PlanetTheme.ACHERON_ASH,
            width = 5,
            height = 5,
            maxBiomass = 10,
            maxCommands = 5,
            maxRounds = 6,
            dayNight = DayNightConfig(startsAtNight = false, switchEveryRounds = 0),
            cells = cells,
            humanSpawns = listOf(
                HumanSpawnSpec("tank1", HumanType.TANK, GridPos(2, 2), Direction.SOUTH, listOf(GridPos(2, 2)))
            )
        )

        val reaperPlacement = listOf(
            AlienPlacement("r1", AlienSpecies.REAPER, EvolutionStage.ADULT, GridPos(2, 3))
        )

        // Without weapons vs with both Plasma Venom + Chitin Blades
        val baseRes = TurnSimulator.simulate(level, reaperPlacement)
        val armedRes = TurnSimulator.simulate(
            level,
            reaperPlacement,
            equippedWeapons = setOf(BioWeapon.PLASMA_VENOM, BioWeapon.CHITIN_BLADES)
        )

        // Armed Reaper deals more damage & survives longer, defeating the Siege Tank
        assertTrue("Ausgerüstete Klingen-Bestie sollte den Panzer besiegen", armedRes.isVictory)
        assertTrue(
            "Mit Bio-Waffen sollte das Gefecht mindestens so gut/schneller ausgehen",
            !baseRes.isVictory || armedRes.totalRounds <= baseRes.totalRounds
        )
    }

    @Test
    fun sporeBomberDetonatesIn3x3OnDeath() {
        val cells = buildList {
            for (y in 0 until 5) {
                for (x in 0 until 5) {
                    val pos = GridPos(x, y)
                    val type = when {
                        x == 0 || y == 0 || x == 4 || y == 4 -> TileType.WALL
                        pos == GridPos(2, 2) -> TileType.CREEP
                        else -> TileType.FLOOR
                    }
                    add(TileCell(pos, type))
                }
            }
        }
        val level = LevelDefinition(
            id = "test_bomber_nova",
            title = "Bomber Nova",
            sectorName = "Test",
            hint = "",
            planetTheme = PlanetTheme.KRYON_ICE,
            width = 5,
            height = 5,
            maxBiomass = 10,
            maxCommands = 5,
            maxRounds = 4,
            dayNight = DayNightConfig(startsAtNight = false, switchEveryRounds = 0),
            cells = cells,
            humanSpawns = listOf(
                HumanSpawnSpec("t1", HumanType.TANK, GridPos(2, 1), Direction.SOUTH, listOf(GridPos(2, 1))),
                HumanSpawnSpec("m1", HumanType.MARINE, GridPos(3, 2), Direction.WEST, listOf(GridPos(3, 2)))
            )
        )
        // Larva Bomber (4 HP) gets killed by Tank (4 dmg) right in Round 1 and triggers Bio-Nova 3x3 blast!
        val res = TurnSimulator.simulate(
            level,
            listOf(AlienPlacement("b1", AlienSpecies.BOMBER, EvolutionStage.LARVA, GridPos(2, 2)))
        )
        val round1 = res.snapshots[1]
        assertTrue(
            "Sporen-Bomber muss beim Ableben eine EXPLOSION auslösen",
            round1.effects.any { it.kind == EffectKind.EXPLOSION }
        )
    }
}

package com.reeware.android.breed.data

import com.reeware.android.breed.engine.*

/**
 * 8 handgefertigte, aufsteigend anspruchsvolle Hive-Flow-Zimmer über beide Raumschiffe
 * (Frachter Nostromo-IX / Acheron IV & Kreuzer Kryon-Bastion / Kryon VII).
 *
 * Alle 8 Räume sind durch FlowGameTest automatisch auf exakte Mengen-Gleichheit (Kapseln == Wachen)
 * und 100 % Deadlock-freie Lösbarkeit geprüft!
 */
object FlowCampaign {

    val levels: List<FlowLevelSpec>
        get() = listOf(
            buildLevel01(),
            buildLevel02(),
            buildLevel03(),
            buildLevel04(),
            buildLevel05(),
            buildLevel06(),
            buildLevel07(),
            buildLevel08()
        )

    fun levelById(id: String): FlowLevelSpec =
        levels.firstOrNull { it.id == id } ?: levels.first()

    /**
     * ASCII-Legende für Zimmer-Pläne (unten ist die offene Angriffs-Front!):
     * '#' = WALL, 'O' = WINDOW (Planeten-Bullauge), '.' = FLOOR (offener Gang), ' ' = VOID
     * 'M' = MARINE (🟣 Lauerer), 'T' = TANK (🟢 Säure-Brut), 'P' = PYRO (🟠 Koloss),
     * 'U' = TURRET (🔵 Netz-Brut), 'K' = COMMANDER (🩷 Schwarm-Psyche)
     * 'B' = BARREL (Säurefass – sprengt benachbarte 'D'-Barrieren!), 'D' = BARRIER (Schott)
     */
    private fun parseRoom(rows: List<String>): List<FlowCellSpec> {
        val result = mutableListOf<FlowCellSpec>()
        for (y in rows.indices) {
            val row = rows[y]
            for (x in row.indices) {
                val pos = GridPos(x, y)
                val cell = when (row[x]) {
                    '#' -> FlowCellSpec(pos, FlowTileKind.WALL)
                    'O' -> FlowCellSpec(pos, FlowTileKind.WINDOW)
                    '.' -> FlowCellSpec(pos, FlowTileKind.FLOOR)
                    'B' -> FlowCellSpec(pos, FlowTileKind.BARREL)
                    'D' -> FlowCellSpec(pos, FlowTileKind.BARRIER)
                    'M' -> FlowCellSpec(pos, FlowTileKind.GUARD, FlowGuardType.MARINE)
                    'T' -> FlowCellSpec(pos, FlowTileKind.GUARD, FlowGuardType.TANK)
                    'P' -> FlowCellSpec(pos, FlowTileKind.GUARD, FlowGuardType.PYRO)
                    'U' -> FlowCellSpec(pos, FlowTileKind.GUARD, FlowGuardType.TURRET)
                    'K' -> FlowCellSpec(pos, FlowTileKind.GUARD, FlowGuardType.COMMANDER)
                    else -> FlowCellSpec(pos, FlowTileKind.VOID)
                }
                result += cell
            }
        }
        return result
    }

    private fun pod(
        id: String,
        species: AlienSpecies,
        count: Int,
        linked: Int = 0,
        hidden: Boolean = false
    ): AlienPod = AlienPod(id, species, count, count, linked, hidden)

    // Level 1: Sofort-Einstieg (2 Klassen: 🟣 Lauerer vs Marines, 🟢 Säure-Brut vs Panzer)
    // M = 8, T = 6
    private fun buildLevel01(): FlowLevelSpec {
        val rows = listOf(
            "##O##O##",
            "#TTTTTT#",
            "#MMMMMM#",
            "O..MM..O",
            "#......#"
        )
        return FlowLevelSpec(
            id = "level_01_cargo",
            title = GameLocale.tr("1. Frachtschleuse", "1. Cargo Airlock"),
            sectorName = "Nostromo-IX · Acheron IV",
            hint = GameLocale.tr(
                "Tippe unten violette Lauerer für die vorderen Marines – dann grüne Säure-Brut für die Panzer dahinter!",
                "Tap purple Stalkers below for the front Marines – then green Acid Spitters for the Tanks behind!"
            ),
            planetTheme = PlanetTheme.ACHERON_ASH,
            isNight = false,
            gridWidth = 8,
            gridHeight = 5,
            cells = parseRoom(rows),
            columns = listOf(
                listOf(pod("1a", AlienSpecies.STALKER, 3), pod("1b", AlienSpecies.SPITTER, 2)),
                listOf(pod("1c", AlienSpecies.SPITTER, 2), pod("1d", AlienSpecies.STALKER, 2)),
                listOf(pod("1e", AlienSpecies.STALKER, 3)),
                listOf(pod("1f", AlienSpecies.SPITTER, 2))
            )
        )
    }

    // Level 2: 3 Klassen + Säurefass sprengt das Schott zur Panzer-Kammer!
    // M = 6, P = 6, T = 6
    private fun buildLevel02(): FlowLevelSpec {
        val rows = listOf(
            "#O####O#",
            "#TT##TT#",
            "#TD##DT#",
            "#PBMMBP#",
            "O.PMMP.O",
            "#P.MM.P#"
        )
        return FlowLevelSpec(
            id = "level_02_vents",
            title = GameLocale.tr("2. Lüftungstrakt", "2. Ventilation Shaft"),
            sectorName = "Nostromo-IX · Acheron IV",
            hint = GameLocale.tr(
                "🟠 Kolosse schlagen Pyro-Wachen aus dem Weg und sprengen die grünen Säurefässer vor den Panzern!",
                "🟠 Brutes smash Pyro guards and detonate the acid barrels guarding the Tanks!"
            ),
            planetTheme = PlanetTheme.ACHERON_ASH,
            isNight = true,
            gridWidth = 8,
            gridHeight = 6,
            cells = parseRoom(rows),
            columns = listOf(
                listOf(pod("2a", AlienSpecies.BRUTE, 3), pod("2b", AlienSpecies.SPITTER, 3)),
                listOf(pod("2c", AlienSpecies.STALKER, 3), pod("2d", AlienSpecies.BRUTE, 3)),
                listOf(pod("2e", AlienSpecies.SPITTER, 3)),
                listOf(pod("2f", AlienSpecies.STALKER, 3))
            )
        )
    }

    // Level 3: 4 Klassen – 🔵 Netz-Brut schaltet Auto-Geschütze im Zentrum aus!
    // M = 6, P = 4, U = 6, T = 4
    private fun buildLevel03(): FlowLevelSpec {
        val rows = listOf(
            "##O##O##",
            "#TUUUUT#",
            "#TU##UT#",
            "O.P..P.O",
            "#PMMMMP#",
            "#..MM..#"
        )
        return FlowLevelSpec(
            id = "level_03_turret",
            title = GameLocale.tr("3. Geschützdeck", "3. Turret Deck"),
            sectorName = "Nostromo-IX · Acheron IV",
            hint = GameLocale.tr(
                "Räume erst Marines (🟣) & Pyros (🟠) vorn weg, damit 🔵 Netz-Brut an die Auto-Geschütze herankommt!",
                "Clear front Marines (🟣) & Pyros (🟠) first so 🔵 Web Weavers can reach the Auto-Turrets!"
            ),
            planetTheme = PlanetTheme.ACHERON_ASH,
            isNight = false,
            gridWidth = 8,
            gridHeight = 6,
            cells = parseRoom(rows),
            columns = listOf(
                listOf(pod("3a", AlienSpecies.STALKER, 3), pod("3b", AlienSpecies.WEAVER, 3)),
                listOf(pod("3c", AlienSpecies.BRUTE, 2), pod("3d", AlienSpecies.SPITTER, 2)),
                listOf(pod("3e", AlienSpecies.STALKER, 3), pod("3f", AlienSpecies.SPITTER, 2)),
                listOf(pod("3g", AlienSpecies.BRUTE, 2), pod("3h", AlienSpecies.WEAVER, 3))
            )
        )
    }

    // Level 4: Nachtschicht-Reaktor – Tiefe Formation & knifflige Reihenfolge!
    // M = 6, P = 6, T = 4, U = 4
    private fun buildLevel04(): FlowLevelSpec {
        val rows = listOf(
            "#O####O#",
            "#TUUUUT#",
            "#TBDDBT#",
            "O.PPPP.O",
            "#P#MM#P#",
            "#MMMM..#"
        )
        return FlowLevelSpec(
            id = "level_04_nightshift",
            title = GameLocale.tr("4. Nachtschicht-Reaktor", "4. Nightshift Reactor"),
            sectorName = "Nostromo-IX · Acheron IV",
            hint = GameLocale.tr(
                "Blockiere nicht alle 5 Slots mit grüner Säure-Brut, bevor die Pyros die Säurefässer gesprengt haben!",
                "Don't clog all 5 slots with Acid Spitters before Brutes trigger the acid barrels!"
            ),
            planetTheme = PlanetTheme.ACHERON_ASH,
            isNight = true,
            gridWidth = 8,
            gridHeight = 6,
            cells = parseRoom(rows),
            columns = listOf(
                listOf(pod("4a", AlienSpecies.STALKER, 4), pod("4b", AlienSpecies.SPITTER, 2)),
                listOf(pod("4c", AlienSpecies.BRUTE, 3), pod("4d", AlienSpecies.WEAVER, 2)),
                listOf(pod("4e", AlienSpecies.STALKER, 2), pod("4f", AlienSpecies.BRUTE, 3)),
                listOf(pod("4g", AlienSpecies.WEAVER, 2), pod("4h", AlienSpecies.SPITTER, 2))
            )
        )
    }

    // Level 5: Brücke (Panzer) – Neu: Gekoppelte Doppel-Kapseln (belegen 2 Slots gleichzeitig!)
    // M = 6, T = 8, P = 4, U = 4
    private fun buildLevel05(): FlowLevelSpec {
        val rows = listOf(
            "##O##O##",
            "#TTTTTT#",
            "#TU..UT#",
            "O.PUUP.O",
            "#P.MM.P#",
            "#MMMM..#"
        )
        return FlowLevelSpec(
            id = "level_05_bridge",
            title = GameLocale.tr("5. Brücke (Panzer)", "5. Bridge (Siege Tank)"),
            sectorName = "Nostromo-IX · Acheron IV",
            hint = GameLocale.tr(
                "Achtung: Verbundene Kapseln wandern zusammen in 2 Slots! Halte rechtzeitig 2 Plätze frei.",
                "Watch out: Linked pods deploy together into 2 slots! Keep 2 free slots ready."
            ),
            planetTheme = PlanetTheme.ACHERON_ASH,
            isNight = false,
            gridWidth = 8,
            gridHeight = 6,
            cells = parseRoom(rows),
            columns = listOf(
                listOf(pod("5a", AlienSpecies.STALKER, 3, linked = 1), pod("5b", AlienSpecies.SPITTER, 4)),
                listOf(pod("5c", AlienSpecies.STALKER, 3, linked = 1), pod("5d", AlienSpecies.WEAVER, 2)),
                listOf(pod("5e", AlienSpecies.BRUTE, 4), pod("5f", AlienSpecies.SPITTER, 4)),
                listOf(pod("5g", AlienSpecies.WEAVER, 2))
            )
        )
    }

    // Level 6: Kryo-Schleuse (Kryon VII) – Neu: 5. Klasse 🩷 Schwarm-Psyche & verdeckte ?-Kokons!
    // M = 6, U = 6, K = 4, T = 4
    private fun buildLevel06(): FlowLevelSpec {
        val rows = listOf(
            "#O####O#",
            "#TKKKKT#",
            "#TU##UT#",
            "O.UUUU.O",
            "#MMMMMM#",
            "#......#"
        )
        return FlowLevelSpec(
            id = "level_06_cryo",
            title = GameLocale.tr("6. Kryo-Schleuse", "6. Cryo Airlock"),
            sectorName = "Kryon-Bastion · Kryon VII",
            hint = GameLocale.tr(
                "Eiskreuzer! Verdeckte ?-Kokons enthüllen sich erst vorn. 🩷 Schwarm-Psyche bricht die Kommandanten!",
                "Ice Cruiser! Hidden ? cocoons reveal once at the front. 🩷 Hive Psions break the Commanders!"
            ),
            planetTheme = PlanetTheme.KRYON_ICE,
            isNight = true,
            gridWidth = 8,
            gridHeight = 6,
            cells = parseRoom(rows),
            columns = listOf(
                listOf(pod("6a", AlienSpecies.STALKER, 3), pod("6b", AlienSpecies.WEAVER, 3, hidden = true), pod("6c", AlienSpecies.PSION, 2)),
                listOf(pod("6d", AlienSpecies.STALKER, 3), pod("6e", AlienSpecies.SPITTER, 2, hidden = true)),
                listOf(pod("6f", AlienSpecies.WEAVER, 3), pod("6g", AlienSpecies.PSION, 2, hidden = true)),
                listOf(pod("6h", AlienSpecies.SPITTER, 2))
            )
        )
    }

    // Level 7: Kreuzfeuer-Hangar – Symmetrischer Großraum mit 5 Klassen, Fässern & Doppel-Kapseln
    // M = 6, P = 6, U = 4, T = 4, K = 4
    private fun buildLevel07(): FlowLevelSpec {
        val rows = listOf(
            "##O##O##",
            "#TKKKKT#",
            "#TBDDBT#",
            "O.UUUU.O",
            "#PPPPPP#",
            "#MMMMMM#"
        )
        return FlowLevelSpec(
            id = "level_07_crossfire",
            title = GameLocale.tr("7. Kreuzfeuer-Hangar", "7. Crossfire Hangar"),
            sectorName = "Kryon-Bastion · Kryon VII",
            hint = GameLocale.tr(
                "Durchbrich die vorderen Reihen sauber Schicht für Schicht – lass immer 1–2 Slots als Puffer frei!",
                "Peel each defense layer cleanly – always keep 1–2 slots free as a buffer!"
            ),
            planetTheme = PlanetTheme.KRYON_ICE,
            isNight = false,
            gridWidth = 8,
            gridHeight = 6,
            cells = parseRoom(rows),
            columns = listOf(
                listOf(pod("7a", AlienSpecies.STALKER, 3, linked = 1), pod("7b", AlienSpecies.BRUTE, 3), pod("7c", AlienSpecies.SPITTER, 2)),
                listOf(pod("7d", AlienSpecies.STALKER, 3, linked = 1), pod("7e", AlienSpecies.WEAVER, 2, hidden = true), pod("7f", AlienSpecies.PSION, 2)),
                listOf(pod("7g", AlienSpecies.BRUTE, 3), pod("7h", AlienSpecies.SPITTER, 2, hidden = true)),
                listOf(pod("7i", AlienSpecies.WEAVER, 2), pod("7j", AlienSpecies.PSION, 2, hidden = true))
            )
        )
    }

    // Level 8: Eiskern-Zitadelle (Finale!) – Alle 5 Klassen, Doppel-Kapseln, ?-Kokons & Dual-Fässer
    // M = 6, P = 6, U = 6, T = 6, K = 6 (30 Wachen!)
    private fun buildLevel08(): FlowLevelSpec {
        val rows = listOf(
            "#O####O#",
            "#KKKKKK#",
            "#TDBBDT#",
            "O.TTTT.O",
            "#UUUUUU#",
            "#PPPPPP#",
            "#MMMMMM#"
        )
        return FlowLevelSpec(
            id = "level_08_citadel",
            title = GameLocale.tr("8. Eiskern-Zitadelle", "8. Ice-Core Citadel"),
            sectorName = "Kryon-Bastion · Kryon VII",
            hint = GameLocale.tr(
                "Finale der Zitadelle! 5 Wellen – schalte Marines, Pyros, Geschütze, Panzer und schließlich alle 6 Kommandanten aus!",
                "Citadel Finale! 5 defense layers – clear Marines, Pyros, Turrets, Tanks, and all 6 Commanders!"
            ),
            planetTheme = PlanetTheme.KRYON_ICE,
            isNight = true,
            gridWidth = 8,
            gridHeight = 7,
            cells = parseRoom(rows),
            columns = listOf(
                listOf(pod("8a", AlienSpecies.STALKER, 3, linked = 1), pod("8b", AlienSpecies.BRUTE, 3), pod("8c", AlienSpecies.SPITTER, 3, hidden = true)),
                listOf(pod("8d", AlienSpecies.STALKER, 3, linked = 1), pod("8e", AlienSpecies.WEAVER, 3), pod("8f", AlienSpecies.PSION, 3)),
                listOf(pod("8g", AlienSpecies.BRUTE, 3), pod("8h", AlienSpecies.SPITTER, 3, hidden = true)),
                listOf(pod("8i", AlienSpecies.WEAVER, 3), pod("8j", AlienSpecies.PSION, 3, hidden = true))
            )
        )
    }
}

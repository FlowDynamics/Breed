package com.reeware.android.breed.data

import com.reeware.android.breed.engine.*

/**
 * Handcrafted campaign across two planetary spaceship sectors.
 * All routes, switches, turrets, and day/night puzzles are verified by CampaignLevelSolvabilityTest.
 */
object BuiltInCampaign {

    val shipNodes: List<ShipRoomNode>
        get() = listOf(
            // Ship 1: USCSS Nostromo-IX (Orbiting Acheron IV)
            ShipRoomNode(
                levelId = "level_01_cargo",
                title = GameLocale.tr("1. Frachtschleuse", "1. Cargo Airlock"),
                shipName = GameLocale.tr("Frachter Nostromo-IX", "Freighter Nostromo-IX"),
                planetTheme = PlanetTheme.ACHERON_ASH,
                mapX = 0.50f,
                mapY = 0.85f,
                connectedIds = listOf("level_02_vents", "level_03_turret"),
                mutagensReward = 20,
                initiallyAvailable = true
            ),
            ShipRoomNode(
                levelId = "level_02_vents",
                title = GameLocale.tr("2. Lüftungstrakt", "2. Ventilation Shaft"),
                shipName = GameLocale.tr("Frachter Nostromo-IX", "Freighter Nostromo-IX"),
                planetTheme = PlanetTheme.ACHERON_ASH,
                mapX = 0.25f,
                mapY = 0.68f,
                connectedIds = listOf("level_04_nightshift"),
                mutagensReward = 25
            ),
            ShipRoomNode(
                levelId = "level_03_turret",
                title = GameLocale.tr("3. Geschützdeck", "3. Turret Deck"),
                shipName = GameLocale.tr("Frachter Nostromo-IX", "Freighter Nostromo-IX"),
                planetTheme = PlanetTheme.ACHERON_ASH,
                mapX = 0.75f,
                mapY = 0.68f,
                connectedIds = listOf("level_04_nightshift"),
                mutagensReward = 30
            ),
            ShipRoomNode(
                levelId = "level_04_nightshift",
                title = GameLocale.tr("4. Nachtschicht-Reaktor", "4. Nightshift Reactor"),
                shipName = GameLocale.tr("Frachter Nostromo-IX", "Freighter Nostromo-IX"),
                planetTheme = PlanetTheme.ACHERON_ASH,
                mapX = 0.50f,
                mapY = 0.50f,
                connectedIds = listOf("level_05_bridge"),
                mutagensReward = 35
            ),
            ShipRoomNode(
                levelId = "level_05_bridge",
                title = GameLocale.tr("5. Brücke (Panzer)", "5. Bridge (Siege Tank)"),
                shipName = GameLocale.tr("Frachter Nostromo-IX", "Freighter Nostromo-IX"),
                planetTheme = PlanetTheme.ACHERON_ASH,
                mapX = 0.50f,
                mapY = 0.34f,
                connectedIds = listOf("level_06_cryo"),
                mutagensReward = 45
            ),
            // Ship 2: UNSC Kryon-Bastion (Orbiting Kryon VII)
            ShipRoomNode(
                levelId = "level_06_cryo",
                title = GameLocale.tr("6. Kryo-Schleuse", "6. Cryo Airlock"),
                shipName = GameLocale.tr("Kreuzer Kryon-Bastion", "Cruiser Kryon-Bastion"),
                planetTheme = PlanetTheme.KRYON_ICE,
                mapX = 0.28f,
                mapY = 0.20f,
                connectedIds = listOf("level_07_crossfire"),
                mutagensReward = 45
            ),
            ShipRoomNode(
                levelId = "level_07_crossfire",
                title = GameLocale.tr("7. Kreuzfeuer-Hangar", "7. Crossfire Hangar"),
                shipName = GameLocale.tr("Kreuzer Kryon-Bastion", "Cruiser Kryon-Bastion"),
                planetTheme = PlanetTheme.KRYON_ICE,
                mapX = 0.72f,
                mapY = 0.20f,
                connectedIds = listOf("level_08_citadel"),
                mutagensReward = 50
            ),
            ShipRoomNode(
                levelId = "level_08_citadel",
                title = GameLocale.tr("8. Eiskern-Zitadelle", "8. Ice-Core Citadel"),
                shipName = GameLocale.tr("Kreuzer Kryon-Bastion", "Cruiser Kryon-Bastion"),
                planetTheme = PlanetTheme.KRYON_ICE,
                mapX = 0.50f,
                mapY = 0.08f,
                connectedIds = emptyList(),
                mutagensReward = 75
            )
        )

    val levels: List<LevelDefinition>
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

    fun levelById(id: String): LevelDefinition =
        levels.firstOrNull { it.id == id } ?: levels.first()

    /**
     * ASCII map legend:
     * '#' = WALL, 'O' = WINDOW (outer hull viewports), '.' = FLOOR, 'c' = CREEP,
     * 'I' = ENTRY, 'X' = EXIT, 'V' = VENT (link 1, deployable), 'v' = VENT (link 2, deployable),
     * 'D' = CLOSED DOOR (link 1), 'G' = OPEN DOOR (link 1), 'C' = CONSOLE (link 1),
     * 'k' = CONSOLE (link 2), 'B' = ACID_BARREL
     */
    private fun parseAsciiCells(rows: List<String>): List<TileCell> {
        val cells = mutableListOf<TileCell>()
        for (y in rows.indices) {
            val row = rows[y]
            for (x in row.indices) {
                val pos = GridPos(x, y)
                val cell = when (row[x]) {
                    '#' -> TileCell(pos, TileType.WALL)
                    'O' -> TileCell(pos, TileType.WINDOW)
                    'c' -> TileCell(pos, TileType.CREEP)
                    'I' -> TileCell(pos, TileType.ENTRY)
                    'X' -> TileCell(pos, TileType.EXIT)
                    'V' -> TileCell(pos, TileType.VENT, linkId = 1, deployable = true)
                    'v' -> TileCell(pos, TileType.VENT, linkId = 2, deployable = true)
                    'D' -> TileCell(pos, TileType.DOOR, linkId = 1, doorOpen = false)
                    'd' -> TileCell(pos, TileType.DOOR, linkId = 2, doorOpen = false)
                    'G' -> TileCell(pos, TileType.DOOR, linkId = 1, doorOpen = true)
                    'C' -> TileCell(pos, TileType.CONSOLE, linkId = 1)
                    'k' -> TileCell(pos, TileType.CONSOLE, linkId = 2)
                    'B' -> TileCell(pos, TileType.ACID_BARREL)
                    else -> TileCell(pos, TileType.FLOOR)
                }
                cells += cell
            }
        }
        return cells
    }

    private fun lineRoute(vararg points: Pair<Int, Int>): List<GridPos> {
        val result = mutableListOf<GridPos>()
        for (i in points.indices) {
            val target = GridPos(points[i].first, points[i].second)
            if (result.isEmpty()) {
                result += target
            } else {
                var cur = result.last()
                while (cur != target) {
                    val dx = (target.x - cur.x).coerceIn(-1, 1)
                    val dy = if (dx == 0) (target.y - cur.y).coerceIn(-1, 1) else 0
                    cur = GridPos(cur.x + dx, cur.y + dy)
                    result += cur
                }
            }
        }
        return result
    }

    // Level 1: Überzahl-Tutorial (Einzeln zu schwach, 2v1 von beiden Flanken gewinnt)
    private fun buildLevel01(): LevelDefinition {
        val rows = listOf(
            "###I###",
            "#.....#",
            "O.c.c.O",
            "#c.#.c#",
            "#c...c#",
            "O..#..O",
            "#.....#",
            "#.....#",
            "###X###"
        )
        return LevelDefinition(
            id = "level_01_cargo",
            title = GameLocale.tr("1. Frachtschleuse", "1. Cargo Airlock"),
            sectorName = "Nostromo-IX · Acheron IV",
            hint = GameLocale.tr(
                "Eine Larve allein fällt im Kreuzfeuer. Greife im Schwarm (Überzahl 2v1) von beiden Creep-Flanken gleichzeitig an!",
                "A lone Larva falls in crossfire. Strike as a swarm (2v1 outnumbering) from both Creep flanks at once!"
            ),
            planetTheme = PlanetTheme.ACHERON_ASH,
            width = 7,
            height = 9,
            maxBiomass = 6,
            maxCommands = 6,
            maxRounds = 12,
            dayNight = DayNightConfig(startsAtNight = false, switchEveryRounds = 0),
            cells = parseAsciiCells(rows),
            humanSpawns = listOf(
                HumanSpawnSpec(
                    id = "m1",
                    type = HumanType.MARINE,
                    spawn = GridPos(3, 0),
                    facing = Direction.SOUTH,
                    route = lineRoute(3 to 0, 3 to 2, 4 to 2, 4 to 6, 3 to 6, 3 to 8)
                )
            )
        )
    }

    // Level 2: Pyro-Marine & Vent-Hinterhalt in der Nachtschicht
    private fun buildLevel02(): LevelDefinition {
        val rows = listOf(
            "###I###",
            "#.....#",
            "O.c#..O",
            "#V.#.c#",
            "#..#..#",
            "O..#.VO",
            "#..#..#",
            "#.....#",
            "###X###"
        )
        return LevelDefinition(
            id = "level_02_vents",
            title = GameLocale.tr("2. Lüftungstrakt", "2. Ventilation Shaft"),
            sectorName = "Nostromo-IX · Acheron IV",
            hint = GameLocale.tr(
                "Der Pyro-Marine verbrennt frontale Wellen! Springe mit einem Lauerer durch Vent V hinter die Trennwand und falle ihm im Dunkeln in den Rücken.",
                "The Pyro Trooper scorches frontal waves! Leap a Stalker through Vent V behind the bulkhead and ambush from behind in the dark."
            ),
            planetTheme = PlanetTheme.ACHERON_ASH,
            width = 7,
            height = 9,
            maxBiomass = 6,
            maxCommands = 8,
            maxRounds = 12,
            dayNight = DayNightConfig(startsAtNight = true, switchEveryRounds = 0),
            cells = parseAsciiCells(rows),
            humanSpawns = listOf(
                HumanSpawnSpec(
                    id = "pyro1",
                    type = HumanType.PYRO,
                    spawn = GridPos(3, 0),
                    facing = Direction.SOUTH,
                    route = lineRoute(3 to 0, 3 to 1, 4 to 1, 4 to 7, 3 to 7, 3 to 8)
                )
            )
        )
    }

    // Level 3: Auto-Geschütz & Konsole + Schott-Schalter-Rätsel
    private fun buildLevel03(): LevelDefinition {
        val rows = listOf(
            "###I###",
            "#.....#",
            "O.c...O",
            "#V..#.#",
            "#.c.#.#",
            "O.#G#CO",
            "#..c.V#",
            "#.....#",
            "###X###"
        )
        return LevelDefinition(
            id = "level_03_turret",
            title = GameLocale.tr("3. Geschützdeck", "3. Turret Deck"),
            sectorName = "Nostromo-IX · Acheron IV",
            hint = GameLocale.tr(
                "Das Auto-Geschütz feuert quer über den Gang! Springe durch Vent V zur Konsole C: Ein Schalter-Befehl schaltet das Geschütz ab und schließt Schott G vor dem Marine.",
                "The Auto-Turret covers the corridor! Leap through Vent V to Console C: toggling the switch powers down the turret and seals Door G."
            ),
            planetTheme = PlanetTheme.ACHERON_ASH,
            width = 7,
            height = 9,
            maxBiomass = 7,
            maxCommands = 8,
            maxRounds = 12,
            dayNight = DayNightConfig(startsAtNight = false, switchEveryRounds = 0),
            cells = parseAsciiCells(rows),
            humanSpawns = listOf(
                HumanSpawnSpec(
                    id = "turret1",
                    type = HumanType.TURRET,
                    spawn = GridPos(5, 2),
                    facing = Direction.WEST,
                    route = emptyList(),
                    linkedConsoleId = 1
                ),
                HumanSpawnSpec(
                    id = "m1",
                    type = HumanType.MARINE,
                    spawn = GridPos(3, 0),
                    facing = Direction.SOUTH,
                    route = lineRoute(3 to 0, 3 to 8)
                )
            )
        )
    }

    // Level 4: Tag-/Nacht-Zyklus-Rätsel
    private fun buildLevel04(): LevelDefinition {
        val rows = listOf(
            "###I###",
            "#.....#",
            "O.c.c.O",
            "##.#.##",
            "#.....#",
            "O.c.c.O",
            "#.....#",
            "#.....#",
            "###X###"
        )
        return LevelDefinition(
            id = "level_04_nightshift",
            title = GameLocale.tr("4. Nachtschicht-Reaktor", "4. Nightshift Reactor"),
            sectorName = "Nostromo-IX · Acheron IV",
            hint = GameLocale.tr(
                "Tag-/Nacht-Wechsel alle 2 Runden! In Runde 3 bricht die Nacht an: Marines sehen nur noch 1 Feld weit und Lauerer schlagen im Schatten doppelt hart zu.",
                "Day/Night switches every 2 rounds! Night falls on Round 3: Marines drop to range 1 while Stalkers strike twice as hard in the shadows."
            ),
            planetTheme = PlanetTheme.ACHERON_ASH,
            width = 7,
            height = 9,
            maxBiomass = 7,
            maxCommands = 8,
            maxRounds = 12,
            dayNight = DayNightConfig(startsAtNight = false, switchEveryRounds = 2),
            cells = parseAsciiCells(rows),
            humanSpawns = listOf(
                HumanSpawnSpec(
                    id = "m1",
                    type = HumanType.MARINE,
                    spawn = GridPos(3, 0),
                    facing = Direction.SOUTH,
                    route = lineRoute(3 to 0, 3 to 1, 2 to 1, 2 to 6, 3 to 6, 3 to 8)
                ),
                HumanSpawnSpec(
                    id = "m2",
                    type = HumanType.MARINE,
                    spawn = GridPos(3, 0),
                    facing = Direction.SOUTH,
                    route = lineRoute(3 to 0, 3 to 1, 4 to 1, 4 to 6, 3 to 6, 3 to 8),
                    startDelayRounds = 1
                )
            )
        )
    }

    // Level 5: Belagerungs-Panzer + Säurefass + Säure-Brut / Koloss
    private fun buildLevel05(): LevelDefinition {
        val rows = listOf(
            "###I###",
            "#.....#",
            "O.B...O",
            "#c...c#",
            "##.#.##",
            "Oc...cO",
            "#..c..#",
            "#.....#",
            "###X###"
        )
        return LevelDefinition(
            id = "level_05_bridge",
            title = GameLocale.tr("5. Brücke (Panzer)", "5. Bridge (Siege Tank)"),
            sectorName = "Nostromo-IX · Acheron IV",
            hint = GameLocale.tr(
                "Der Belagerungs-Panzer (Rüstung 3) lässt einfache Klauen abprallen! Säure-Brut schmilzt seine Panzerung, während ein Koloss den Engpass blockt.",
                "The Siege Tank (Armor 3) shrugs off basic claws! Acid Spitter melts its armor while a Brute blocks the chokepoint."
            ),
            planetTheme = PlanetTheme.ACHERON_ASH,
            width = 7,
            height = 9,
            maxBiomass = 10,
            maxCommands = 8,
            maxRounds = 12,
            dayNight = DayNightConfig(startsAtNight = false, switchEveryRounds = 3),
            cells = parseAsciiCells(rows),
            humanSpawns = listOf(
                HumanSpawnSpec(
                    id = "tank1",
                    type = HumanType.TANK,
                    spawn = GridPos(3, 0),
                    facing = Direction.SOUTH,
                    route = lineRoute(3 to 0, 3 to 2, 4 to 2, 4 to 6, 3 to 6, 3 to 8)
                ),
                HumanSpawnSpec(
                    id = "m1",
                    type = HumanType.MARINE,
                    spawn = GridPos(3, 0),
                    facing = Direction.SOUTH,
                    route = lineRoute(3 to 0, 3 to 1, 4 to 1, 4 to 6, 3 to 6, 3 to 8),
                    startDelayRounds = 1
                )
            )
        )
    }

    // Level 6: Kryo-Schleuse (Netz-Brut + Schott-Konsole)
    private fun buildLevel06(): LevelDefinition {
        val rows = listOf(
            "###I###",
            "#.....#",
            "O.cGc.O",
            "#C.#..#",
            "#..#.V#",
            "O..#.cO",
            "#V.#..#",
            "#.....#",
            "###X###"
        )
        return LevelDefinition(
            id = "level_06_cryo",
            title = GameLocale.tr("6. Kryo-Schleuse", "6. Cryo Airlock"),
            sectorName = "Kryon-Bastion · Kryon VII",
            hint = GameLocale.tr(
                "Verriegele das Schott G über Konsole C oder fessle die Marines mit der Netz-Brut, bis der Schwarm die Überzahl herstellt.",
                "Lock Door G via Console C or root the Marines with a Web Weaver until your swarm outnumbers them."
            ),
            planetTheme = PlanetTheme.KRYON_ICE,
            width = 7,
            height = 9,
            maxBiomass = 10,
            maxCommands = 9,
            maxRounds = 12,
            dayNight = DayNightConfig(startsAtNight = true, switchEveryRounds = 2),
            cells = parseAsciiCells(rows),
            humanSpawns = listOf(
                HumanSpawnSpec(
                    id = "m1",
                    type = HumanType.MARINE,
                    spawn = GridPos(3, 0),
                    facing = Direction.SOUTH,
                    route = lineRoute(3 to 0, 3 to 2, 4 to 2, 4 to 7, 3 to 7, 3 to 8)
                ),
                HumanSpawnSpec(
                    id = "pyro1",
                    type = HumanType.PYRO,
                    spawn = GridPos(3, 0),
                    facing = Direction.SOUTH,
                    route = lineRoute(3 to 0, 3 to 2, 2 to 2, 2 to 7, 3 to 7, 3 to 8),
                    startDelayRounds = 1
                )
            )
        )
    }

    // Level 7: Kreuzfeuer-Hangar (2 Geschütze + Konsole + Säurefass)
    private fun buildLevel07(): LevelDefinition {
        val rows = listOf(
            "###I###",
            "#.....#",
            "O.c#..O",
            "#V.Bc.#",
            "#...#C#",
            "Oc.#.VO",
            "#..G..#",
            "#.....#",
            "###X###"
        )
        return LevelDefinition(
            id = "level_07_crossfire",
            title = GameLocale.tr("7. Kreuzfeuer-Hangar", "7. Crossfire Hangar"),
            sectorName = "Kryon-Bastion · Kryon VII",
            hint = GameLocale.tr(
                "Schalte das linke Geschütz & Schott G über Konsole C ab, zerätze das zweite Geschütz mit Säure-Brut und fang den Marine am Schott ab.",
                "Power down the left turret & Door G via Console C, melt the second turret with Acid Spitter, and ambush the Marine at the door."
            ),
            planetTheme = PlanetTheme.KRYON_ICE,
            width = 7,
            height = 9,
            maxBiomass = 12,
            maxCommands = 10,
            maxRounds = 13,
            dayNight = DayNightConfig(startsAtNight = false, switchEveryRounds = 2),
            cells = parseAsciiCells(rows),
            humanSpawns = listOf(
                HumanSpawnSpec(
                    id = "turret_a",
                    type = HumanType.TURRET,
                    spawn = GridPos(1, 4),
                    facing = Direction.NORTH,
                    route = emptyList(),
                    linkedConsoleId = 1
                ),
                HumanSpawnSpec(
                    id = "turret_b",
                    type = HumanType.TURRET,
                    spawn = GridPos(5, 2),
                    facing = Direction.WEST,
                    route = emptyList(),
                    linkedConsoleId = 2
                ),
                HumanSpawnSpec(
                    id = "m1",
                    type = HumanType.MARINE,
                    spawn = GridPos(3, 0),
                    facing = Direction.SOUTH,
                    route = lineRoute(3 to 0, 3 to 1, 2 to 1, 2 to 6, 3 to 6, 3 to 8)
                )
            )
        )
    }

    // Level 8: Eiskern-Zitadelle (Finale über Kryon VII)
    private fun buildLevel08(): LevelDefinition {
        val rows = listOf(
            "###I###",
            "#.....#",
            "O.cBc.O",
            "#V.#..#",
            "#..G.C#",
            "O#.#.VO",
            "#c...c#",
            "#.....#",
            "###X###"
        )
        return LevelDefinition(
            id = "level_08_citadel",
            title = GameLocale.tr("8. Eiskern-Zitadelle", "8. Ice-Core Citadel"),
            sectorName = "Kryon-Bastion · Kryon VII",
            hint = GameLocale.tr(
                "Finale! Panzer, Pyro-Marine und Geschütz sichern den Eiskern. Schalte das Geschütz über V->C aus, schmilz den Panzer mit Säure-Brut und blocke den Fluchtweg.",
                "Finale! Siege Tank, Pyro Trooper, and Turret guard the ice core. Disable the turret via V->C, melt the tank with Acid Spitter, and seal the escape."
            ),
            planetTheme = PlanetTheme.KRYON_ICE,
            width = 7,
            height = 9,
            maxBiomass = 15,
            maxCommands = 12,
            maxRounds = 14,
            dayNight = DayNightConfig(startsAtNight = false, switchEveryRounds = 2),
            cells = parseAsciiCells(rows),
            humanSpawns = listOf(
                HumanSpawnSpec(
                    id = "turret1",
                    type = HumanType.TURRET,
                    spawn = GridPos(5, 3),
                    facing = Direction.WEST,
                    route = emptyList(),
                    linkedConsoleId = 1
                ),
                HumanSpawnSpec(
                    id = "tank1",
                    type = HumanType.TANK,
                    spawn = GridPos(3, 0),
                    facing = Direction.SOUTH,
                    route = lineRoute(3 to 0, 3 to 1, 2 to 1, 2 to 7, 3 to 7, 3 to 8)
                ),
                HumanSpawnSpec(
                    id = "pyro1",
                    type = HumanType.PYRO,
                    spawn = GridPos(3, 0),
                    facing = Direction.SOUTH,
                    route = lineRoute(3 to 0, 3 to 1, 4 to 1, 4 to 7, 3 to 7, 3 to 8),
                    startDelayRounds = 1
                )
            )
        )
    }
}

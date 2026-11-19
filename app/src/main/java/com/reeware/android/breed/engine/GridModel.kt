package com.reeware.android.breed.engine

import kotlin.math.abs

/** Cardinal directions on the tactical room grid. */
enum class Direction(val dx: Int, val dy: Int, private val labelDe: String, private val labelEn: String) {
    NORTH(0, -1, "N", "N"),
    EAST(1, 0, "O", "E"),
    SOUTH(0, 1, "S", "S"),
    WEST(-1, 0, "W", "W");

    val label: String get() = GameLocale.tr(labelDe, labelEn)

    fun opposite(): Direction = when (this) {
        NORTH -> SOUTH
        EAST -> WEST
        SOUTH -> NORTH
        WEST -> EAST
    }

    companion object {
        fun fromDelta(dx: Int, dy: Int): Direction? = entries.firstOrNull { it.dx == dx && it.dy == dy }
    }
}

/** 2D integer grid coordinate. */
data class GridPos(val x: Int, val y: Int) {
    fun move(dir: Direction): GridPos = GridPos(x + dir.dx, y + dir.dy)

    fun manhattanTo(other: GridPos): Int = abs(x - other.x) + abs(y - other.y)

    fun isAdjacentTo(other: GridPos): Boolean = manhattanTo(other) == 1
}

/** Terrain and puzzle tile types on a spaceship room grid. */
enum class TileType {
    FLOOR,
    WALL,
    CREEP,
    VENT,
    DOOR,
    CONSOLE,
    ACID_BARREL,
    WINDOW,
    ENTRY,
    EXIT;

    fun blocksLineOfSight(doorOpen: Boolean): Boolean = when (this) {
        WALL -> true
        DOOR -> !doorOpen
        else -> false
    }

    fun isWalkableForHumans(doorOpen: Boolean): Boolean = when (this) {
        FLOOR, CREEP, CONSOLE, ENTRY, EXIT -> true
        DOOR -> doorOpen
        WALL, VENT, ACID_BARREL, WINDOW -> false
    }

    fun isWalkableForAliens(doorOpen: Boolean): Boolean = when (this) {
        FLOOR, CREEP, VENT, CONSOLE, ENTRY -> true
        DOOR -> doorOpen
        WALL, ACID_BARREL, WINDOW, EXIT -> false
    }
}

/** Single tile state on the room grid. */
data class TileCell(
    val pos: GridPos,
    val type: TileType,
    val linkId: Int = 0,
    val doorOpen: Boolean = false,
    val lightOff: Boolean = false,
    val barrelIntact: Boolean = true,
    val acidPoolRounds: Int = 0,
    val deployable: Boolean = false
) {
    fun allowsAlienPlacement(): Boolean =
        type == TileType.CREEP || (deployable && (type == TileType.FLOOR || type == TileType.VENT || type == TileType.CONSOLE))
}

/** Orbiting planet visual theme visible through ship windows and briefings. */
enum class PlanetTheme(
    private val displayNameDe: String,
    private val displayNameEn: String,
    private val subtitleDe: String,
    private val subtitleEn: String
) {
    ACHERON_ASH("Acheron IV", "Acheron IV", "Vulkanische Aschewelt", "Volcanic Ash World"),
    KRYON_ICE("Kryon VII", "Kryon VII", "Gefrorener Eisriese", "Frozen Ice Giant"),
    XENO_NEBULA("Nyx-Sektor", "Nyx Sector", "Violetter Sporennebel", "Violet Spore Nebula"),
    ORBIT_WRECK("Sektor Theta", "Sector Theta", "Trümmer-Orbit", "Debris Orbit");

    val displayName: String get() = GameLocale.tr(displayNameDe, displayNameEn)
    val subtitle: String get() = GameLocale.tr(subtitleDe, subtitleEn)
}

/** Deterministic Day/Night orbit cycle rules for a room. */
data class DayNightConfig(
    val startsAtNight: Boolean = false,
    val switchEveryRounds: Int = 0
) {
    fun isNightAtRound(round: Int): Boolean {
        if (switchEveryRounds <= 0 || round <= 0) return startsAtNight
        val phase = (round - 1) / switchEveryRounds
        return if (phase % 2 == 0) startsAtNight else !startsAtNight
    }
}

/** Pre-configured human intruder spawn and scripted route. */
data class HumanSpawnSpec(
    val id: String,
    val type: HumanType,
    val spawn: GridPos,
    val facing: Direction,
    val route: List<GridPos>,
    val linkedConsoleId: Int = 0,
    val startDelayRounds: Int = 0
)

/** Complete immutable definition of a room puzzle level (campaign or editor). */
data class LevelDefinition(
    val id: String,
    val title: String,
    val sectorName: String,
    val hint: String,
    val planetTheme: PlanetTheme,
    val width: Int,
    val height: Int,
    val maxBiomass: Int,
    val maxCommands: Int,
    val maxRounds: Int,
    val dayNight: DayNightConfig,
    val cells: List<TileCell>,
    val humanSpawns: List<HumanSpawnSpec>
) {
    fun cellAt(pos: GridPos): TileCell? =
        if (pos.x in 0 until width && pos.y in 0 until height) {
            cells.firstOrNull { it.pos == pos }
        } else {
            null
        }
}

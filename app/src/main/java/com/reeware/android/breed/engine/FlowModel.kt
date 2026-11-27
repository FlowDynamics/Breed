package com.reeware.android.breed.engine

/**
 * Wachen-Klassen im Echtzeit-Hive-Flow-Modus:
 * Jede Wachen-Klasse hat genau eine passende Konter-Alien-Klasse mit identischer Farbsignatur
 * (sofort auf einen Blick lesbar – wie bei Colony Flow, nur als Alien-gegen-Wachen-Gefecht).
 */
enum class FlowGuardType(
    val counterSpecies: AlienSpecies,
    val humanVisual: HumanType,
    private val nameDe: String,
    private val nameEn: String
) {
    MARINE(AlienSpecies.STALKER, HumanType.MARINE, "Space Marine", "Space Marine"),
    TANK(AlienSpecies.SPITTER, HumanType.TANK, "Belagerungs-Panzer", "Siege Tank"),
    PYRO(AlienSpecies.BRUTE, HumanType.PYRO, "Pyro-Wache", "Pyro Guard"),
    TURRET(AlienSpecies.WEAVER, HumanType.TURRET, "Auto-Geschütz", "Auto-Turret"),
    COMMANDER(AlienSpecies.PSION, HumanType.MARINE, "Sektor-Kommandant", "Sector Commander");

    val displayName: String get() = GameLocale.tr(nameDe, nameEn)
    val accentColorHex: Long get() = counterSpecies.accentColorHex
}

/** Kacheltypen im Raumschiff-Zimmer oben. */
enum class FlowTileKind {
    VOID,
    WALL,
    WINDOW,
    FLOOR,
    CREEP,
    GUARD,
    BARREL,
    BARRIER
}

/** Unveränderliche Kachel-Vorlage für eine Flow-Level-Definition. */
data class FlowCellSpec(
    val pos: GridPos,
    val kind: FlowTileKind,
    val guardType: FlowGuardType? = null
)

/** Ein antippbarer Alien-Block im unteren Brut-Stapel bzw. in einem der 5 aktiven Slots. */
data class AlienPod(
    val id: String,
    val species: AlienSpecies,
    val count: Int,
    val maxCount: Int = count,
    val linkedGroupId: Int = 0,
    val hiddenUntilFront: Boolean = false
)

/** Vollständige Level-Definition für ein Echtzeit-Hive-Flow-Zimmer. */
data class FlowLevelSpec(
    val id: String,
    val title: String,
    val sectorName: String,
    val hint: String,
    val planetTheme: PlanetTheme,
    val isNight: Boolean,
    val gridWidth: Int,
    val gridHeight: Int,
    val slotCount: Int = 5,
    val cells: List<FlowCellSpec>,
    val columns: List<List<AlienPod>>
) {
    fun guardCountsBySpecies(): Map<AlienSpecies, Int> {
        val map = mutableMapOf<AlienSpecies, Int>()
        for (c in cells) {
            if (c.kind == FlowTileKind.GUARD && c.guardType != null) {
                val sp = c.guardType.counterSpecies
                map[sp] = (map[sp] ?: 0) + 1
            }
        }
        return map
    }

    fun podCountsBySpecies(): Map<AlienSpecies, Int> {
        val map = mutableMapOf<AlienSpecies, Int>()
        for (col in columns) {
            for (pod in col) {
                map[pod.species] = (map[pod.species] ?: 0) + pod.count
            }
        }
        return map
    }
}

enum class FlowOutcome {
    PLAYING,
    VICTORY,
    DEADLOCK
}

enum class FlowTapResult {
    DEPLOYED,
    EMPTY_COLUMN,
    NO_FREE_SLOTS,
    LINKED_PARTNER_BLOCKED,
    GAME_OVER
}

/** Audio-/Haptik-Ereignis, das die UI beim Echtzeit-Update abspielen kann. */
enum class FlowEventKind {
    POD_DEPLOYED,
    ALIEN_SPAWNED,
    COMBAT_HIT,
    GUARD_KILLED,
    BARREL_BLAST,
    SLOT_CLEARED,
    ELITE_STRIKE,
    VICTORY,
    DEADLOCK
}

/** Aktiver Zustand einer einzelnen Raum-Kachel zur Laufzeit. */
class FlowCellState(
    val pos: GridPos,
    var kind: FlowTileKind,
    var guardType: FlowGuardType? = null,
    var reserved: Boolean = false,
    var exposed: Boolean = false,
    var hitFlashSec: Float = 0f
)

/** Belegter Slot in der 5er-Brut-Leiste zwischen Stapel und Zimmer. */
class FlowActiveSlot(
    val slotIndex: Int,
    val id: String,
    val species: AlienSpecies,
    var remaining: Int,
    val maxCount: Int,
    var spawnCooldownSec: Float = 0.06f,
    var slideAnim: Float = 0f,
    var stalled: Boolean = false
)

enum class RunningAlienPhase {
    RUNNING,
    FIGHTING
}

/** Ein einzelnes Alien, das in Echtzeit vom Brut-Slot ins Zimmer stürmt und eine Wache bekämpft. */
class RunningAlien(
    val id: String,
    val species: AlienSpecies,
    val slotIndex: Int,
    val targetPos: GridPos,
    val path: List<Pair<Float, Float>>,
    var pathIdx: Int = 0,
    var x: Float,
    var y: Float,
    var phase: RunningAlienPhase = RunningAlienPhase.RUNNING,
    var fightTimerSec: Float = 0f,
    val fightDurationSec: Float = 0.30f,
    var facingX: Float = 0f,
    var facingY: Float = -1f
)

/** Kurzlebiger visueller Kampf-Effekt (Laser, Krallenschnitt, Säure-Spritzer, Fass-Explosion). */
class FlowVisualFx(
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val kind: EffectKind,
    val colorHex: Long,
    var ageSec: Float = 0f,
    val durationSec: Float = 0.38f
)

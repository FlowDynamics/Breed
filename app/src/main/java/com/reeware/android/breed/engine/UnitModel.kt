package com.reeware.android.breed.engine

/**
 * 5 Basis-Alien-Arten (kostenlos) + 2 optionale Elite-Arten (In-App-Kauf),
 * jede Art besitzt ihren eigenen festen Mutations-Baum (keine Arten-Kreuzung).
 */
enum class AlienSpecies(
    private val displayNameDe: String,
    private val displayNameEn: String,
    private val shortNameDe: String,
    private val shortNameEn: String,
    private val roleTagDe: String,
    private val roleTagEn: String,
    val baseCost: Int,
    val baseHp: Int,
    val baseAttack: Int,
    val baseArmor: Int,
    val attackRange: Int,
    val canUseVent: Boolean,
    val accentColorHex: Long,
    val isPremium: Boolean = false
) {
    STALKER(
        displayNameDe = "Lauerer",
        displayNameEn = "Stalker",
        shortNameDe = "Lauer",
        shortNameEn = "Stalk",
        roleTagDe = "Vent & Schatten",
        roleTagEn = "Vent & Shadow",
        baseCost = 2,
        baseHp = 4,
        baseAttack = 3,
        baseArmor = 0,
        attackRange = 1,
        canUseVent = true,
        accentColorHex = 0xFF9C5CFF
    ),
    SPITTER(
        displayNameDe = "Säure-Brut",
        displayNameEn = "Acid Spitter",
        shortNameDe = "Säure",
        shortNameEn = "Acid",
        roleTagDe = "Anti-Panzer",
        roleTagEn = "Anti-Armor",
        baseCost = 3,
        baseHp = 4,
        baseAttack = 2,
        baseArmor = 0,
        attackRange = 2,
        canUseVent = false,
        accentColorHex = 0xFF66FF66
    ),
    BRUTE(
        displayNameDe = "Koloss",
        displayNameEn = "Brute",
        shortNameDe = "Koloss",
        shortNameEn = "Brute",
        roleTagDe = "Tank & Blocker",
        roleTagEn = "Tank & Blocker",
        baseCost = 4,
        baseHp = 10,
        baseAttack = 3,
        baseArmor = 2,
        attackRange = 1,
        canUseVent = false,
        accentColorHex = 0xFFFF7A3D
    ),
    WEAVER(
        displayNameDe = "Netz-Brut",
        displayNameEn = "Web Weaver",
        shortNameDe = "Netz",
        shortNameEn = "Web",
        roleTagDe = "Fessel & Schwarm",
        roleTagEn = "Root & Swarm",
        baseCost = 3,
        baseHp = 5,
        baseAttack = 2,
        baseArmor = 0,
        attackRange = 1,
        canUseVent = true,
        accentColorHex = 0xFF33E6D1
    ),
    PSION(
        displayNameDe = "Schwarm-Psyche",
        displayNameEn = "Hive Psion",
        shortNameDe = "Psyche",
        shortNameEn = "Psion",
        roleTagDe = "Aura & Panik",
        roleTagEn = "Aura & Panic",
        baseCost = 4,
        baseHp = 5,
        baseAttack = 2,
        baseArmor = 0,
        attackRange = 2,
        canUseVent = false,
        accentColorHex = 0xFFFF4D9E
    ),
    REAPER(
        displayNameDe = "Klingen-Bestie",
        displayNameEn = "Blade Reaper",
        shortNameDe = "Klinge",
        shortNameEn = "Reap",
        roleTagDe = "Panzerbrecher ★",
        roleTagEn = "Armor Pierce ★",
        baseCost = 3,
        baseHp = 6,
        baseAttack = 4,
        baseArmor = 1,
        attackRange = 1,
        canUseVent = true,
        accentColorHex = 0xFFFF335E,
        isPremium = true
    ),
    BOMBER(
        displayNameDe = "Sporen-Bomber",
        displayNameEn = "Spore Bomber",
        shortNameDe = "Bomber",
        shortNameEn = "Bomb",
        roleTagDe = "3×3 Detonation ★",
        roleTagEn = "3×3 Acid Blast ★",
        baseCost = 4,
        baseHp = 5,
        baseAttack = 3,
        baseArmor = 0,
        attackRange = 2,
        canUseVent = false,
        accentColorHex = 0xFFDDFF33,
        isPremium = true
    );

    val displayName: String get() = GameLocale.tr(displayNameDe, displayNameEn)
    val shortName: String get() = GameLocale.tr(shortNameDe, shortNameEn)
    val roleTag: String get() = GameLocale.tr(roleTagDe, roleTagEn)
}

/** Developmental stages of each alien unit. */
enum class EvolutionStage(
    private val displayNameDe: String,
    private val displayNameEn: String,
    val costDelta: Int,
    val hpBonus: Int,
    val attackBonus: Int,
    val hatchDelayRounds: Int
) {
    EGG("Ei", "Egg", costDelta = -1, hpBonus = -2, attackBonus = 0, hatchDelayRounds = 2),
    LARVA("Larve", "Larva", costDelta = -1, hpBonus = -1, attackBonus = -1, hatchDelayRounds = 0),
    ADULT("Kampfform", "Adult", costDelta = 0, hpBonus = 0, attackBonus = 0, hatchDelayRounds = 0),
    ALPHA("Alpha", "Alpha", costDelta = 1, hpBonus = 3, attackBonus = 2, hatchDelayRounds = 0);

    val displayName: String get() = GameLocale.tr(displayNameDe, displayNameEn)

    fun effectiveCost(species: AlienSpecies): Int =
        (species.baseCost + costDelta).coerceAtLeast(1)
}

/** Fixed per-species mutation tree nodes (2 distinct branches per species, no cross-breeding). */
enum class MutationNode(
    val species: AlienSpecies,
    private val titleDe: String,
    private val titleEn: String,
    private val descriptionDe: String,
    private val descriptionEn: String,
    val costMutagens: Int
) {
    STALKER_SHADOW(
        AlienSpecies.STALKER,
        "Schattengift",
        "Shadow Venom",
        "+2 Schaden bei Nacht oder Licht-Aus.",
        "+2 damage during Night or Light-Off.",
        20
    ),
    STALKER_LEAP(
        AlienSpecies.STALKER,
        "Vent-Reflex",
        "Vent Reflex",
        "+1 Zusatz-Schaden direkt nach einem Vent-Sprung.",
        "+1 bonus damage right after a Vent jump.",
        25
    ),
    SPITTER_CORROSION(
        AlienSpecies.SPITTER,
        "Hyper-Säure",
        "Hyper Acid",
        "Schmilzt komplette Rüstung von Panzern/Geschützen auf 0.",
        "Melts all armor of Tanks and Turrets down to 0.",
        25
    ),
    SPITTER_POOL(
        AlienSpecies.SPITTER,
        "Ätzpfütze",
        "Corrosive Pool",
        "Hinterlässt eine 2-Runden-Säurepfütze auf getroffenen Feldern.",
        "Leaves a 2-round acid pool on hit tiles.",
        30
    ),
    BRUTE_CARAPAX(
        AlienSpecies.BRUTE,
        "Knochen-Carapax",
        "Bone Carapace",
        "+3 HP und +1 Rüstung gegen Geschütz- & Pyro-Feuer.",
        "+3 HP and +1 armor against Turret & Pyro fire.",
        25
    ),
    BRUTE_SLAM(
        AlienSpecies.BRUTE,
        "Schock-Ramme",
        "Shock Slam",
        "Stößt getroffene Marines 1 Feld zurück.",
        "Knocks hit Marines back by 1 tile.",
        30
    ),
    WEAVER_HOST(
        AlienSpecies.WEAVER,
        "Brut-Wirt",
        "Brood Host",
        "Jeder besiegte Mensch spawnt sofort eine Larve für Überzahl.",
        "Every defeated human immediately spawns a Larva for outnumbering.",
        30
    ),
    WEAVER_WEB(
        AlienSpecies.WEAVER,
        "Klebe-Netz",
        "Sticky Web",
        "Fesselt getroffene Gegner für 2 statt 1 Runde.",
        "Roots hit enemies for 2 rounds instead of 1.",
        25
    ),
    PSION_TERROR(
        AlienSpecies.PSION,
        "Schwarm-Terror",
        "Hive Terror",
        "Zwingt getroffene Marines, 1 Runde vor Panik auszusetzen.",
        "Forces hit Marines to skip 1 round in panic.",
        30
    ),
    PSION_LINK(
        AlienSpecies.PSION,
        "Schwarm-Link",
        "Swarm Link",
        "Verdoppelt den Überzahl-Bonus verbündeter Aliens in 2 Feldern.",
        "Doubles the outnumbering bonus of allied aliens within 2 tiles.",
        35
    ),
    REAPER_REND(
        AlienSpecies.REAPER,
        "Monoklingen",
        "Mono-Blades",
        "Ignoriert komplette Rüstung und +1 Schaden gegen Panzer/Geschütze.",
        "Ignores all armor and deals +1 damage to Tanks & Turrets.",
        25
    ),
    REAPER_FRENZY(
        AlienSpecies.REAPER,
        "Blutrausch",
        "Blood Frenzy",
        "Heilt 2 HP bei jedem besiegten Gegner.",
        "Restores 2 HP whenever an enemy is eliminated.",
        30
    ),
    BOMBER_NOVA(
        AlienSpecies.BOMBER,
        "Bio-Nova",
        "Bio-Nova",
        "Todes-Explosion verursacht +2 Extraschaden im 3×3-Radius.",
        "Death blast deals +2 extra damage in a 3×3 radius.",
        25
    ),
    BOMBER_SPORES(
        AlienSpecies.BOMBER,
        "Giftsporen",
        "Toxic Spores",
        "Hinterlässt bei jedem Fernkampftreffer eine 2-Runden-Säurepfütze.",
        "Leaves a 2-round acid pool on every ranged hit.",
        30
    );

    val title: String get() = GameLocale.tr(titleDe, titleEn)
    val description: String get() = GameLocale.tr(descriptionDe, descriptionEn)
}

/** Human defender / intruder types on the spaceship. */
enum class HumanType(
    private val displayNameDe: String,
    private val displayNameEn: String,
    val maxHp: Int,
    val baseArmor: Int,
    val attack: Int,
    val dayRange: Int,
    val nightRange: Int,
    val movesPerRound: Int,
    val isStationary: Boolean = false
) {
    MARINE("Space Marine", "Space Marine", maxHp = 5, baseArmor = 0, attack = 2, dayRange = 3, nightRange = 1, movesPerRound = 1),
    PYRO("Pyro-Marine", "Pyro Trooper", maxHp = 6, baseArmor = 1, attack = 3, dayRange = 2, nightRange = 1, movesPerRound = 1),
    TANK("Belagerungs-Panzer", "Siege Tank", maxHp = 10, baseArmor = 3, attack = 4, dayRange = 3, nightRange = 2, movesPerRound = 1),
    TURRET("Auto-Geschütz", "Auto-Turret", maxHp = 7, baseArmor = 2, attack = 3, dayRange = 4, nightRange = 2, movesPerRound = 0, isStationary = true);

    val displayName: String get() = GameLocale.tr(displayNameDe, displayNameEn)
}

/** Pre-programmed phase-1 action commands (Variant A). */
sealed class AlienCommand {
    data class Move(val dir: Direction) : AlienCommand()
    data class VentJump(val target: GridPos) : AlienCommand()
    data object ToggleConsole : AlienCommand()
    data object Wait : AlienCommand()
    data object HatchNow : AlienCommand()

    fun shortLabel(): String = when (this) {
        is Move -> dir.label
        is VentJump -> "V"
        ToggleConsole -> GameLocale.tr("S", "C")
        Wait -> "W"
        HatchNow -> GameLocale.tr("E", "H")
    }
}

/** Player placement created in Phase 1 via bottom-dock selection + room grid tap. */
data class AlienPlacement(
    val id: String,
    val species: AlienSpecies,
    val stage: EvolutionStage,
    val startPos: GridPos,
    val commands: List<AlienCommand> = emptyList()
)

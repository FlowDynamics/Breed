package com.reeware.android.breed.engine

/** Outcome state of a Phase 2 tactical simulation. */
enum class BattleOutcome {
    IN_PROGRESS,
    VICTORY,
    DEFEAT_ESCAPED,
    DEFEAT_WIPED_OUT,
    DEFEAT_TIMEOUT
}

/** Immutable runtime snapshot of an alien unit during a simulation round. */
data class AlienRuntimeState(
    val id: String,
    val species: AlienSpecies,
    val stage: EvolutionStage,
    val pos: GridPos,
    val hp: Int,
    val maxHp: Int,
    val armor: Int,
    val hatchCountdown: Int,
    val justExitedVent: Boolean = false,
    val facing: Direction = Direction.SOUTH,
    val remainingCommands: Int = 0
) {
    val isEgg: Boolean get() = stage == EvolutionStage.EGG
    val isAlive: Boolean get() = hp > 0
}

/** Immutable runtime snapshot of a human unit during a simulation round. */
data class HumanRuntimeState(
    val id: String,
    val type: HumanType,
    val pos: GridPos,
    val hp: Int,
    val maxHp: Int,
    val armor: Int,
    val facing: Direction,
    val powered: Boolean = true,
    val rootedRounds: Int = 0,
    val panickedRounds: Int = 0,
    val routeIndex: Int = 0
) {
    val isAlive: Boolean get() = hp > 0
    val isThreat: Boolean get() = isAlive && (!type.isStationary || powered)
}

/** Visual combat cue emitted during a deterministic round tick. */
data class CombatEffect(
    val from: GridPos,
    val to: GridPos,
    val kind: EffectKind,
    val label: String
)

enum class EffectKind {
    CLAW,
    ACID,
    LASER,
    FLAME,
    EXPLOSION,
    PSIONIC,
    CONSOLE,
    VENT,
    HATCH
}

/** Complete board state at the end of a single chess-like simulation round. */
data class TurnSnapshot(
    val round: Int,
    val isNight: Boolean,
    val cells: List<TileCell>,
    val aliens: List<AlienRuntimeState>,
    val humans: List<HumanRuntimeState>,
    val effects: List<CombatEffect>,
    val logs: List<String>,
    val outcome: BattleOutcome
)

/** Full deterministic Phase 2 simulation trace from Round 0 to final outcome. */
data class SimulationResult(
    val snapshots: List<TurnSnapshot>,
    val finalOutcome: BattleOutcome,
    val totalRounds: Int
) {
    val isVictory: Boolean get() = finalOutcome == BattleOutcome.VICTORY
}

/**
 * 100% deterministic chess-like tactical simulator for Phase 2.
 * Same inputs always produce identical round-by-round snapshots and outcome.
 */
object TurnSimulator {

    private class MutableAlien(
        val id: String,
        val species: AlienSpecies,
        var stage: EvolutionStage,
        var pos: GridPos,
        var hp: Int,
        var maxHp: Int,
        var armor: Int,
        var hatchCountdown: Int,
        var justExitedVent: Boolean = false,
        var facing: Direction = Direction.SOUTH,
        val commands: ArrayDeque<AlienCommand>
    ) {
        val isAlive: Boolean get() = hp > 0
        val isEgg: Boolean get() = stage == EvolutionStage.EGG

        fun toSnapshot() = AlienRuntimeState(
            id = id,
            species = species,
            stage = stage,
            pos = pos,
            hp = hp,
            maxHp = maxHp,
            armor = armor,
            hatchCountdown = hatchCountdown,
            justExitedVent = justExitedVent,
            facing = facing,
            remainingCommands = commands.size
        )
    }

    private class MutableHuman(
        val id: String,
        val type: HumanType,
        var pos: GridPos,
        var hp: Int,
        val maxHp: Int,
        var armor: Int,
        var facing: Direction,
        var powered: Boolean = true,
        var rootedRounds: Int = 0,
        var panickedRounds: Int = 0,
        val route: List<GridPos>,
        var routeIndex: Int = 0,
        val linkedConsoleId: Int = 0,
        var startDelayRounds: Int = 0
    ) {
        val isAlive: Boolean get() = hp > 0
        val isActiveThreat: Boolean get() = isAlive && (!type.isStationary || powered)

        fun toSnapshot() = HumanRuntimeState(
            id = id,
            type = type,
            pos = pos,
            hp = hp,
            maxHp = maxHp,
            armor = armor,
            facing = facing,
            powered = powered,
            rootedRounds = rootedRounds,
            panickedRounds = panickedRounds,
            routeIndex = routeIndex
        )
    }

    fun simulate(
        level: LevelDefinition,
        placements: List<AlienPlacement>,
        unlockedMutations: Set<MutationNode> = emptySet(),
        equippedWeapons: Set<BioWeapon> = emptySet()
    ): SimulationResult {
        val grid = Array(level.width) { x ->
            Array(level.height) { y ->
                level.cellAt(GridPos(x, y)) ?: TileCell(GridPos(x, y), TileType.WALL)
            }
        }
        val aliens = placements.map { createAlien(it, unlockedMutations, equippedWeapons) }.toMutableList()
        val humans = level.humanSpawns.map { spec ->
            MutableHuman(
                id = spec.id,
                type = spec.type,
                pos = spec.spawn,
                hp = spec.type.maxHp,
                maxHp = spec.type.maxHp,
                armor = spec.type.baseArmor,
                facing = spec.facing,
                route = spec.route,
                linkedConsoleId = spec.linkedConsoleId,
                startDelayRounds = spec.startDelayRounds
            )
        }.toMutableList()

        val snapshots = mutableListOf<TurnSnapshot>()
        snapshots += TurnSnapshot(
            round = 0,
            isNight = level.dayNight.isNightAtRound(0),
            cells = flattenGrid(grid, level.width, level.height),
            aliens = aliens.map { it.toSnapshot() },
            humans = humans.map { it.toSnapshot() },
            effects = emptyList(),
            logs = listOf(GameLocale.tr("Phase 1 abgeschlossen – Schwarm bereit.", "Phase 1 complete – Brood ready.")),
            outcome = BattleOutcome.IN_PROGRESS
        )

        var spawnCounter = 0
        for (round in 1..level.maxRounds) {
            val isNight = level.dayNight.isNightAtRound(round)
            val effects = mutableListOf<CombatEffect>()
            val logs = mutableListOf<String>()

            if (round == 1 || level.dayNight.isNightAtRound(round - 1) != isNight) {
                logs += if (isNight) {
                    GameLocale.tr("Nacht-Zyklus: Sichtweite reduziert, Lauerer gestärkt.", "Night cycle: visibility reduced, Stalkers empowered.")
                } else {
                    GameLocale.tr("Tag-Zyklus: Volle Sensor- und Geschützreichweite.", "Day cycle: full sensor and turret range.")
                }
            }

            // 1. Egg hatching & acid pool ticks
            processEggsAndPools(grid, aliens, humans, isNight, effects, logs)

            // 2. Alien pre-programmed commands (Variant A)
            executeAlienCommands(grid, level.width, level.height, aliens, humans, effects, logs)

            // 3. Alien attacks with Outnumbering, Specialization counters & Bio-Weapons
            val spawnedLarvae = executeAlienCombat(
                grid, level.width, level.height, aliens, humans,
                unlockedMutations, equippedWeapons, isNight, effects, logs
            ) {
                spawnCounter++
                "larva_spawn_$spawnCounter"
            }
            aliens += spawnedLarvae

            // 4. Turrets & Space Marines / Tanks phase
            executeHumanPhase(
                grid, level.width, level.height, aliens, humans,
                unlockedMutations, isNight, effects, logs
            )

            // 5. Evaluate outcome
            val outcome = evaluateOutcome(grid, aliens, humans, round, level.maxRounds)
            snapshots += TurnSnapshot(
                round = round,
                isNight = isNight,
                cells = flattenGrid(grid, level.width, level.height),
                aliens = aliens.map { it.toSnapshot() },
                humans = humans.map { it.toSnapshot() },
                effects = effects,
                logs = logs,
                outcome = outcome
            )
            if (outcome != BattleOutcome.IN_PROGRESS) {
                return SimulationResult(snapshots, outcome, round)
            }
        }

        val last = snapshots.last()
        return SimulationResult(snapshots, last.outcome, last.round)
    }

    private fun createAlien(
        p: AlienPlacement,
        mutations: Set<MutationNode>,
        equippedWeapons: Set<BioWeapon>
    ): MutableAlien {
        var hp = (p.species.baseHp + p.stage.hpBonus).coerceAtLeast(2)
        var armor = p.species.baseArmor
        if (p.species == AlienSpecies.BRUTE && MutationNode.BRUTE_CARAPAX in mutations) {
            hp += 3
            armor += 1
        }
        if (BioWeapon.CHITIN_BLADES in equippedWeapons) {
            hp += 2
            armor += 1
        }
        return MutableAlien(
            id = p.id,
            species = p.species,
            stage = p.stage,
            pos = p.startPos,
            hp = hp,
            maxHp = hp,
            armor = armor,
            hatchCountdown = p.stage.hatchDelayRounds,
            commands = ArrayDeque(p.commands)
        )
    }

    private fun processEggsAndPools(
        grid: Array<Array<TileCell>>,
        aliens: List<MutableAlien>,
        humans: List<MutableHuman>,
        isNight: Boolean,
        effects: MutableList<CombatEffect>,
        logs: MutableList<String>
    ) {
        for (x in grid.indices) {
            for (y in grid[x].indices) {
                val cell = grid[x][y]
                if (cell.acidPoolRounds > 0) {
                    val humanHere = humans.firstOrNull { it.isAlive && it.pos == cell.pos }
                    if (humanHere != null) {
                        humanHere.armor = (humanHere.armor - 1).coerceAtLeast(0)
                        humanHere.hp -= 1
                        effects += CombatEffect(cell.pos, cell.pos, EffectKind.ACID, GameLocale.tr("-1 Säure", "-1 Acid"))
                        logs += GameLocale.tr(
                            "${humanHere.type.displayName} erleidet 1 Säureschaden.",
                            "${humanHere.type.displayName} takes 1 acid damage."
                        )
                    }
                    grid[x][y] = cell.copy(acidPoolRounds = cell.acidPoolRounds - 1)
                }
            }
        }

        for (alien in aliens) {
            if (!alien.isAlive || !alien.isEgg) continue
            val nextCmd = alien.commands.firstOrNull()
            val forceHatch = nextCmd == AlienCommand.HatchNow
            if (forceHatch) alien.commands.removeFirst()

            val hatchRate = if (isNight) 2 else 1
            alien.hatchCountdown -= hatchRate
            if (forceHatch || alien.hatchCountdown <= 0) {
                alien.stage = EvolutionStage.ADULT
                alien.maxHp = alien.species.baseHp
                alien.hp = alien.species.baseHp
                alien.hatchCountdown = 0
                effects += CombatEffect(alien.pos, alien.pos, EffectKind.HATCH, GameLocale.tr("Schlupf!", "Hatch!"))
                logs += GameLocale.tr(
                    "${alien.species.displayName}-Ei schlüpft zur Kampfform!",
                    "${alien.species.displayName} egg hatches into adult!"
                )
            }
        }
    }

    private fun executeAlienCommands(
        grid: Array<Array<TileCell>>,
        width: Int,
        height: Int,
        aliens: List<MutableAlien>,
        humans: List<MutableHuman>,
        effects: MutableList<CombatEffect>,
        logs: MutableList<String>
    ) {
        for (alien in aliens) {
            alien.justExitedVent = false
            if (!alien.isAlive || alien.isEgg) continue
            val cmd = alien.commands.removeFirstOrNull() ?: continue

            when (cmd) {
                AlienCommand.Wait, AlienCommand.HatchNow -> Unit
                AlienCommand.ToggleConsole -> {
                    val console = findConsoleAround(grid, width, height, alien.pos)
                    if (console != null) {
                        toggleLinkedSystems(grid, width, height, humans, console.linkId)
                        effects += CombatEffect(alien.pos, console.pos, EffectKind.CONSOLE, GameLocale.tr("Schalter!", "Switch!"))
                        logs += GameLocale.tr(
                            "${alien.species.displayName} schaltet Konsole #${console.linkId} um.",
                            "${alien.species.displayName} toggles console #${console.linkId}."
                        )
                    }
                }
                is AlienCommand.VentJump -> {
                    val canVent = alien.species.canUseVent || alien.stage == EvolutionStage.LARVA
                    val fromCell = grid[alien.pos.x][alien.pos.y]
                    val targetCell = if (inBounds(cmd.target, width, height)) grid[cmd.target.x][cmd.target.y] else null
                    val occupied = isTileOccupied(cmd.target, aliens, humans)
                    if (canVent && fromCell.type == TileType.VENT && targetCell?.type == TileType.VENT &&
                        targetCell.linkId == fromCell.linkId && !occupied
                    ) {
                        val origin = alien.pos
                        alien.pos = cmd.target
                        alien.justExitedVent = true
                        effects += CombatEffect(origin, cmd.target, EffectKind.VENT, "Vent")
                        logs += GameLocale.tr(
                            "${alien.species.displayName} kriecht durch Vent nach (${cmd.target.x},${cmd.target.y}).",
                            "${alien.species.displayName} leaps through vent to (${cmd.target.x},${cmd.target.y})."
                        )
                    }
                }
                is AlienCommand.Move -> {
                    val target = alien.pos.move(cmd.dir)
                    if (!inBounds(target, width, height)) continue
                    alien.facing = cmd.dir
                    val targetCell = grid[target.x][target.y]

                    // Brute can smash through a closed door!
                    if (targetCell.type == TileType.DOOR && !targetCell.doorOpen && alien.species == AlienSpecies.BRUTE) {
                        grid[target.x][target.y] = targetCell.copy(doorOpen = true)
                        effects += CombatEffect(alien.pos, target, EffectKind.CLAW, GameLocale.tr("Schott auf!", "Breached!"))
                        logs += GameLocale.tr("Koloss bricht gepanzertes Schott auf!", "Brute smashes through bulkhead door!")
                    }

                    val updatedCell = grid[target.x][target.y]
                    if (updatedCell.type.isWalkableForAliens(updatedCell.doorOpen) &&
                        !isTileOccupied(target, aliens, humans)
                    ) {
                        alien.pos = target
                    }
                }
            }
        }
    }

    private fun executeAlienCombat(
        grid: Array<Array<TileCell>>,
        width: Int,
        height: Int,
        aliens: List<MutableAlien>,
        humans: List<MutableHuman>,
        mutations: Set<MutationNode>,
        equippedWeapons: Set<BioWeapon>,
        isNight: Boolean,
        effects: MutableList<CombatEffect>,
        logs: MutableList<String>,
        nextSpawnId: () -> String
    ): List<MutableAlien> {
        val spawned = mutableListOf<MutableAlien>()

        // Pre-plan target per active alien so simultaneous outnumbering count is exact!
        val chosenTargets = mutableMapOf<MutableAlien, MutableHuman>()
        for (alien in aliens) {
            if (!alien.isAlive || alien.isEgg) continue
            val target = findAlienTarget(grid, width, height, alien, humans)
            if (target != null) {
                chosenTargets[alien] = target
            } else if (alien.species == AlienSpecies.SPITTER || alien.species == AlienSpecies.BOMBER) {
                // Spitter / Bomber can shoot an adjacent/nearby acid barrel if no direct human target
                val barrel = findTargetBarrel(grid, width, height, alien.pos, alien.species.attackRange)
                if (barrel != null) {
                    detonateBarrel(grid, width, height, barrel.pos, aliens, humans, effects, logs)
                }
            }
        }

        val attackersPerHuman = chosenTargets.entries.groupBy({ it.value }, { it.key })

        for ((human, attackers) in attackersPerHuman) {
            if (!human.isAlive) continue
            val outnumberCount = attackers.size

            for (alien in attackers) {
                if (!human.isAlive) break
                val psionSupport = aliens.any {
                    it.isAlive && !it.isEgg && it.species == AlienSpecies.PSION &&
                        it.pos.manhattanTo(alien.pos) <= 2 && it != alien
                }
                val hasLink = MutationNode.PSION_LINK in mutations && psionSupport
                val baseSwarmBonus = when {
                    outnumberCount >= 3 -> 2
                    outnumberCount == 2 -> 1
                    else -> 0
                }
                val swarmBonus = (if (hasLink) baseSwarmBonus * 2 else baseSwarmBonus) + if (psionSupport) 1 else 0

                var rawAttack = (alien.species.baseAttack + alien.stage.attackBonus).coerceAtLeast(1) + swarmBonus
                if (BioWeapon.PLASMA_VENOM in equippedWeapons) {
                    rawAttack += 1
                }

                // Specialization bonuses
                val inDark = isNight || grid[alien.pos.x][alien.pos.y].lightOff
                if (alien.species == AlienSpecies.STALKER) {
                    if (inDark) {
                        rawAttack += 2
                        if (MutationNode.STALKER_SHADOW in mutations) rawAttack += 2
                    }
                    if (alien.justExitedVent) {
                        rawAttack += 1
                        if (MutationNode.STALKER_LEAP in mutations) rawAttack += 1
                    }
                    val fromBehind = alien.pos == human.pos.move(human.facing.opposite())
                    if (fromBehind) rawAttack += 1
                }

                if (alien.species == AlienSpecies.REAPER && MutationNode.REAPER_REND in mutations &&
                    (human.type == HumanType.TANK || human.type == HumanType.TURRET)
                ) {
                    rawAttack += 1
                }

                if (alien.species == AlienSpecies.SPITTER) {
                    val melt = if (MutationNode.SPITTER_CORROSION in mutations) human.armor else 2
                    human.armor = (human.armor - melt).coerceAtLeast(0)
                    if (MutationNode.SPITTER_POOL in mutations) {
                        val c = grid[human.pos.x][human.pos.y]
                        grid[human.pos.x][human.pos.y] = c.copy(acidPoolRounds = 2)
                    }
                }
                if (alien.species == AlienSpecies.BOMBER && MutationNode.BOMBER_SPORES in mutations) {
                    val c = grid[human.pos.x][human.pos.y]
                    grid[human.pos.x][human.pos.y] = c.copy(acidPoolRounds = 2)
                }

                val effectiveDamage = computeArmorDamage(
                    rawAttack, human.armor, alien.species, alien.stage, mutations, equippedWeapons
                )
                human.hp -= effectiveDamage

                val kind = when (alien.species) {
                    AlienSpecies.SPITTER, AlienSpecies.BOMBER -> EffectKind.ACID
                    AlienSpecies.PSION -> EffectKind.PSIONIC
                    else -> EffectKind.CLAW
                }
                val tag = if (outnumberCount >= 2) "-$effectiveDamage (${outnumberCount}v1)" else "-$effectiveDamage"
                effects += CombatEffect(alien.pos, human.pos, kind, tag)
                logs += GameLocale.tr(
                    "${alien.species.displayName} trifft ${human.type.displayName} für $effectiveDamage Schaden" +
                        if (outnumberCount >= 2) " (Überzahl ${outnumberCount}v1!)." else ".",
                    "${alien.species.displayName} hits ${human.type.displayName} for $effectiveDamage dmg" +
                        if (outnumberCount >= 2) " (Outnumbered ${outnumberCount}v1!)." else "."
                )

                // Reaper Blood Frenzy self-heal on kill
                if (!human.isAlive && alien.species == AlienSpecies.REAPER && MutationNode.REAPER_FRENZY in mutations) {
                    alien.hp = (alien.hp + 2).coerceAtMost(alien.maxHp)
                }

                // Status effects
                if (human.isAlive && alien.species == AlienSpecies.WEAVER) {
                    val rootDur = if (MutationNode.WEAVER_WEB in mutations) 2 else 1
                    human.rootedRounds = maxOf(human.rootedRounds, rootDur)
                }
                if (human.isAlive && alien.species == AlienSpecies.PSION) {
                    human.panickedRounds = maxOf(human.panickedRounds, 1)
                }
                if (human.isAlive && alien.species == AlienSpecies.BRUTE && MutationNode.BRUTE_SLAM in mutations &&
                    !human.type.isStationary && human.type != HumanType.TANK
                ) {
                    val pushDir = Direction.fromDelta(
                        (human.pos.x - alien.pos.x).coerceIn(-1, 1),
                        (human.pos.y - alien.pos.y).coerceIn(-1, 1)
                    )
                    if (pushDir != null) {
                        val pushTarget = human.pos.move(pushDir)
                        if (inBounds(pushTarget, width, height)) {
                            val cell = grid[pushTarget.x][pushTarget.y]
                            if (cell.type.isWalkableForHumans(cell.doorOpen) && !isTileOccupied(pushTarget, aliens, humans)) {
                                human.pos = pushTarget
                                val routePosIdx = human.route.indexOf(pushTarget)
                                if (routePosIdx >= 0) {
                                    human.routeIndex = routePosIdx
                                }
                                logs += "${human.type.displayName} wird zurückgestoßen!"
                            }
                        }
                    }
                }

                // Weaver parasite kill-spawn
                if (!human.isAlive && alien.species == AlienSpecies.WEAVER &&
                    MutationNode.WEAVER_HOST in mutations && !human.type.isStationary
                ) {
                    val spawnPos = findAdjacentFreeTile(grid, width, height, human.pos, aliens + spawned, humans)
                    if (spawnPos != null) {
                        spawned += MutableAlien(
                            id = nextSpawnId(),
                            species = AlienSpecies.STALKER,
                            stage = EvolutionStage.LARVA,
                            pos = spawnPos,
                            hp = 3,
                            maxHp = 3,
                            armor = 0,
                            hatchCountdown = 0,
                            commands = ArrayDeque()
                        )
                        logs += "Brut-Wirt erzeugt eine frische Larve!"
                    }
                }
            }
        }
        return spawned
    }

    private fun executeHumanPhase(
        grid: Array<Array<TileCell>>,
        width: Int,
        height: Int,
        aliens: List<MutableAlien>,
        humans: List<MutableHuman>,
        mutations: Set<MutationNode>,
        isNight: Boolean,
        effects: MutableList<CombatEffect>,
        logs: MutableList<String>
    ) {
        for (human in humans) {
            if (!human.isAlive) continue
            if (human.startDelayRounds > 0) {
                human.startDelayRounds--
                continue
            }
            if (human.type.isStationary && !human.powered) continue

            if (human.panickedRounds > 0) {
                human.panickedRounds--
                logs += "${human.type.displayName} ist in Panik und setzt aus!"
                if (MutationNode.PSION_TERROR in mutations && !human.type.isStationary && human.routeIndex > 0) {
                    val backPos = human.route[human.routeIndex - 1]
                    val cell = grid[backPos.x][backPos.y]
                    if (cell.type.isWalkableForHumans(cell.doorOpen) && !isTileOccupied(backPos, aliens, humans)) {
                        human.routeIndex--
                        human.pos = backPos
                    }
                }
                continue
            }

            // 1. Fire at visible alien in range
            val maxRange = if (isNight) human.type.nightRange else human.type.dayRange
            val targetAlien = findHumanTarget(grid, width, height, human, maxRange, isNight, aliens)
            if (targetAlien != null) {
                val baseDmg = human.type.attack
                applyHumanDamage(grid, width, height, targetAlien, baseDmg, human, humans, mutations, effects, logs)
                if (human.type == HumanType.PYRO) {
                    // Pyro splash hits 1 adjacent alien in flame cone
                    val splash = aliens.firstOrNull {
                        it.isAlive && it != targetAlien && it.pos.isAdjacentTo(targetAlien.pos)
                    }
                    if (splash != null) {
                        applyHumanDamage(grid, width, height, splash, (baseDmg - 1).coerceAtLeast(1), human, humans, mutations, effects, logs)
                    }
                }
            }

            // 2. Move along scripted route (unless stationary or rooted)
            if (!human.type.isStationary) {
                if (human.rootedRounds > 0) {
                    human.rootedRounds--
                    logs += "${human.type.displayName} steckt im Netz fest!"
                } else {
                    repeat(human.type.movesPerRound) {
                        val targetPos = when {
                            human.routeIndex in human.route.indices && human.pos != human.route[human.routeIndex] ->
                                stepToward(human.pos, human.route[human.routeIndex])
                            human.routeIndex + 1 < human.route.size ->
                                human.route[human.routeIndex + 1]
                            else -> null
                        }
                        if (targetPos != null && inBounds(targetPos, width, height)) {
                            val dir = Direction.fromDelta(
                                (targetPos.x - human.pos.x).coerceIn(-1, 1),
                                (targetPos.y - human.pos.y).coerceIn(-1, 1)
                            )
                            if (dir != null) human.facing = dir
                            val cell = grid[targetPos.x][targetPos.y]
                            if (cell.type.isWalkableForHumans(cell.doorOpen) &&
                                !isTileOccupied(targetPos, aliens, humans)
                            ) {
                                human.pos = targetPos
                                val newIdx = human.route.indexOf(targetPos)
                                if (newIdx >= 0) human.routeIndex = newIdx
                            }
                        }
                    }
                }
            }
        }
    }

    private fun applyHumanDamage(
        grid: Array<Array<TileCell>>,
        width: Int,
        height: Int,
        alien: MutableAlien,
        rawDamage: Int,
        attacker: MutableHuman,
        humans: List<MutableHuman>,
        mutations: Set<MutationNode>,
        effects: MutableList<CombatEffect>,
        logs: MutableList<String>
    ) {
        val wasAlive = alien.isAlive
        val dmg = (rawDamage - alien.armor).coerceAtLeast(1)
        alien.hp -= dmg
        val kind = if (attacker.type == HumanType.PYRO) EffectKind.FLAME else EffectKind.LASER
        effects += CombatEffect(attacker.pos, alien.pos, kind, "-$dmg")
        logs += GameLocale.tr(
            "${attacker.type.displayName} trifft ${alien.species.displayName} (-$dmg HP).",
            "${attacker.type.displayName} hits ${alien.species.displayName} (-$dmg HP)."
        )

        // Spore Bomber 3x3 death explosion!
        if (wasAlive && !alien.isAlive && alien.species == AlienSpecies.BOMBER) {
            val blastDmg = if (MutationNode.BOMBER_NOVA in mutations) 6 else 4
            effects += CombatEffect(alien.pos, alien.pos, EffectKind.EXPLOSION, GameLocale.tr("BIO-NOVA!", "BIO-NOVA!"))
            logs += GameLocale.tr(
                "${alien.species.displayName} detoniert im 3×3-Radius (-$blastDmg)!",
                "${alien.species.displayName} detonates in a 3×3 blast (-$blastDmg)!"
            )
            for (h in humans) {
                if (h.isAlive && kotlin.math.abs(h.pos.x - alien.pos.x) <= 1 && kotlin.math.abs(h.pos.y - alien.pos.y) <= 1) {
                    h.armor = (h.armor - 2).coerceAtLeast(0)
                    h.hp -= blastDmg
                }
            }
            for (dx in -1..1) {
                for (dy in -1..1) {
                    val p = GridPos(alien.pos.x + dx, alien.pos.y + dy)
                    if (inBounds(p, width, height) && grid[p.x][p.y].type == TileType.FLOOR) {
                        grid[p.x][p.y] = grid[p.x][p.y].copy(acidPoolRounds = 2)
                    }
                }
            }
        }
    }

    private fun computeArmorDamage(
        rawAttack: Int,
        targetArmor: Int,
        species: AlienSpecies,
        stage: EvolutionStage,
        mutations: Set<MutationNode>,
        equippedWeapons: Set<BioWeapon>
    ): Int {
        var pierce = when (species) {
            AlienSpecies.BRUTE -> 1
            AlienSpecies.REAPER -> if (MutationNode.REAPER_REND in mutations) targetArmor else 2
            else -> 0
        }
        if (BioWeapon.PLASMA_VENOM in equippedWeapons) {
            pierce += 1
        }
        val effectiveArmor = (targetArmor - pierce).coerceAtLeast(0)
        val net = rawAttack - effectiveArmor
        if (net <= 0) {
            return if (effectiveArmor >= 3 && species != AlienSpecies.SPITTER && species != AlienSpecies.BRUTE && species != AlienSpecies.REAPER && stage != EvolutionStage.ALPHA) 0 else 1
        }
        return net
    }

    private fun evaluateOutcome(
        grid: Array<Array<TileCell>>,
        aliens: List<MutableAlien>,
        humans: List<MutableHuman>,
        round: Int,
        maxRounds: Int
    ): BattleOutcome {
        val escaped = humans.any { it.isAlive && !it.type.isStationary && grid[it.pos.x][it.pos.y].type == TileType.EXIT }
        if (escaped) return BattleOutcome.DEFEAT_ESCAPED

        val anyHumanThreat = humans.any { it.isActiveThreat }
        if (!anyHumanThreat) return BattleOutcome.VICTORY

        val anyAlienAlive = aliens.any { it.isAlive }
        if (!anyAlienAlive) return BattleOutcome.DEFEAT_WIPED_OUT

        if (round >= maxRounds) return BattleOutcome.DEFEAT_TIMEOUT
        return BattleOutcome.IN_PROGRESS
    }

    private fun findAlienTarget(
        grid: Array<Array<TileCell>>,
        width: Int,
        height: Int,
        alien: MutableAlien,
        humans: List<MutableHuman>
    ): MutableHuman? {
        val range = alien.species.attackRange
        return humans
            .filter { h ->
                h.isAlive &&
                    h.pos.manhattanTo(alien.pos) <= range &&
                    hasLineOfSight(grid, width, height, alien.pos, h.pos)
            }
            .minByOrNull { it.pos.manhattanTo(alien.pos) }
    }

    private fun findHumanTarget(
        grid: Array<Array<TileCell>>,
        width: Int,
        height: Int,
        human: MutableHuman,
        maxRange: Int,
        isNight: Boolean,
        aliens: List<MutableAlien>
    ): MutableAlien? {
        return aliens
            .filter { a ->
                if (!a.isAlive) return@filter false
                val dist = human.pos.manhattanTo(a.pos)
                if (dist > maxRange) return@filter false
                // Turrets only fire in their facing line corridor
                if (human.type == HumanType.TURRET && !isInFrontCorridor(human.pos, human.facing, a.pos)) {
                    return@filter false
                }
                val tileDark = isNight || grid[a.pos.x][a.pos.y].lightOff
                if (a.species == AlienSpecies.STALKER && tileDark && dist > 1) {
                    return@filter false
                }
                hasLineOfSight(grid, width, height, human.pos, a.pos)
            }
            .minByOrNull { it.pos.manhattanTo(human.pos) }
    }

    private fun isInFrontCorridor(origin: GridPos, facing: Direction, target: GridPos): Boolean = when (facing) {
        Direction.NORTH -> target.x == origin.x && target.y < origin.y
        Direction.SOUTH -> target.x == origin.x && target.y > origin.y
        Direction.EAST -> target.y == origin.y && target.x > origin.x
        Direction.WEST -> target.y == origin.y && target.x < origin.x
    }

    private fun hasLineOfSight(
        grid: Array<Array<TileCell>>,
        width: Int,
        height: Int,
        from: GridPos,
        to: GridPos
    ): Boolean {
        if (from.manhattanTo(to) <= 1) return true
        // Bresenham grid step check for straight/diagonal shots
        var x = from.x
        var y = from.y
        val dx = kotlin.math.abs(to.x - from.x)
        val dy = kotlin.math.abs(to.y - from.y)
        val sx = if (from.x < to.x) 1 else -1
        val sy = if (from.y < to.y) 1 else -1
        var err = dx - dy

        while (x != to.x || y != to.y) {
            if ((x != from.x || y != from.y) && (x != to.x || y != to.y)) {
                if (!inBounds(GridPos(x, y), width, height)) return false
                val cell = grid[x][y]
                if (cell.type.blocksLineOfSight(cell.doorOpen)) return false
            }
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                x += sx
            }
            if (e2 < dx) {
                err += dx
                y += sy
            }
        }
        return true
    }

    private fun findConsoleAround(
        grid: Array<Array<TileCell>>,
        width: Int,
        height: Int,
        pos: GridPos
    ): TileCell? {
        if (grid[pos.x][pos.y].type == TileType.CONSOLE) return grid[pos.x][pos.y]
        for (dir in Direction.entries) {
            val n = pos.move(dir)
            if (inBounds(n, width, height) && grid[n.x][n.y].type == TileType.CONSOLE) {
                return grid[n.x][n.y]
            }
        }
        return null
    }

    private fun toggleLinkedSystems(
        grid: Array<Array<TileCell>>,
        width: Int,
        height: Int,
        humans: List<MutableHuman>,
        linkId: Int
    ) {
        if (linkId <= 0) return
        for (x in 0 until width) {
            for (y in 0 until height) {
                val cell = grid[x][y]
                if (cell.linkId == linkId) {
                    when (cell.type) {
                        TileType.DOOR -> grid[x][y] = cell.copy(doorOpen = !cell.doorOpen)
                        TileType.FLOOR, TileType.CREEP -> grid[x][y] = cell.copy(lightOff = !cell.lightOff)
                        else -> Unit
                    }
                }
            }
        }
        for (h in humans) {
            if (h.type.isStationary && h.linkedConsoleId == linkId) {
                h.powered = !h.powered
            }
        }
    }

    private fun findTargetBarrel(
        grid: Array<Array<TileCell>>,
        width: Int,
        height: Int,
        pos: GridPos,
        range: Int
    ): TileCell? {
        for (x in 0 until width) {
            for (y in 0 until height) {
                val c = grid[x][y]
                if (c.type == TileType.ACID_BARREL && c.barrelIntact && c.pos.manhattanTo(pos) <= range) {
                    return c
                }
            }
        }
        return null
    }

    private fun detonateBarrel(
        grid: Array<Array<TileCell>>,
        width: Int,
        height: Int,
        center: GridPos,
        aliens: List<MutableAlien>,
        humans: List<MutableHuman>,
        effects: MutableList<CombatEffect>,
        logs: MutableList<String>
    ) {
        grid[center.x][center.y] = grid[center.x][center.y].copy(barrelIntact = false, type = TileType.FLOOR, acidPoolRounds = 2)
        effects += CombatEffect(center, center, EffectKind.EXPLOSION, GameLocale.tr("Säure-Explosion!", "Acid Blast!"))
        logs += GameLocale.tr("Chemiefass explodiert bei (${center.x},${center.y})!", "Acid barrel explodes at (${center.x},${center.y})!")
        for (h in humans) {
            if (h.isAlive && kotlin.math.abs(h.pos.x - center.x) <= 1 && kotlin.math.abs(h.pos.y - center.y) <= 1) {
                h.armor = (h.armor - 2).coerceAtLeast(0)
                h.hp -= 5
            }
        }
        for (a in aliens) {
            if (a.isAlive && kotlin.math.abs(a.pos.x - center.x) <= 1 && kotlin.math.abs(a.pos.y - center.y) <= 1) {
                a.hp -= 2
            }
        }
        // Splash acid pools around 3x3
        for (dx in -1..1) {
            for (dy in -1..1) {
                val p = GridPos(center.x + dx, center.y + dy)
                if (inBounds(p, width, height) && grid[p.x][p.y].type == TileType.FLOOR) {
                    grid[p.x][p.y] = grid[p.x][p.y].copy(acidPoolRounds = 2)
                }
            }
        }
    }

    private fun findAdjacentFreeTile(
        grid: Array<Array<TileCell>>,
        width: Int,
        height: Int,
        center: GridPos,
        aliens: List<MutableAlien>,
        humans: List<MutableHuman>
    ): GridPos? = Direction.entries
        .map { center.move(it) }
        .firstOrNull { pos ->
            inBounds(pos, width, height) &&
                grid[pos.x][pos.y].type.isWalkableForAliens(grid[pos.x][pos.y].doorOpen) &&
                !isTileOccupied(pos, aliens, humans)
        }

    private fun stepToward(from: GridPos, to: GridPos): GridPos {
        val dx = (to.x - from.x).coerceIn(-1, 1)
        val dy = if (dx == 0) (to.y - from.y).coerceIn(-1, 1) else 0
        return GridPos(from.x + dx, from.y + dy)
    }

    private fun isTileOccupied(
        pos: GridPos,
        aliens: List<MutableAlien>,
        humans: List<MutableHuman>
    ): Boolean = aliens.any { it.isAlive && it.pos == pos } || humans.any { it.isAlive && it.pos == pos }

    private fun inBounds(pos: GridPos, width: Int, height: Int): Boolean =
        pos.x in 0 until width && pos.y in 0 until height

    private fun flattenGrid(grid: Array<Array<TileCell>>, width: Int, height: Int): List<TileCell> {
        val list = ArrayList<TileCell>(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                list += grid[x][y]
            }
        }
        return list
    }
}

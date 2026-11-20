package com.reeware.android.breed.engine

/** Validation result for Phase 1 placement and command programming. */
data class PlanValidation(
    val usedBiomass: Int,
    val remainingBiomass: Int,
    val usedCommands: Int,
    val remainingCommands: Int,
    val isValid: Boolean,
    val errorMessage: String? = null
)

/** Result of a single smart board tap in Phase 1 (auto-path, vent jump, console toggle, or hatch). */
data class SmartTapPlan(
    val commands: List<AlienCommand>,
    val requiredCommands: Int = commands.size,
    val exceededBudget: Boolean = false
)

/**
 * Manages Phase 1 (Variant A): placing units from the bottom dock onto valid room cells
 * and assigning deterministic step-by-step or 1-tap auto-routed commands before Phase 2 starts.
 */
object CommandPlanner {

    fun placementCost(species: AlienSpecies, stage: EvolutionStage): Int =
        stage.effectiveCost(species)

    fun canUseVents(placement: AlienPlacement): Boolean =
        placement.species.canUseVent || placement.stage == EvolutionStage.LARVA

    fun isPlannableTile(cell: TileCell?): Boolean =
        cell != null &&
            cell.type != TileType.WALL &&
            cell.type != TileType.WINDOW &&
            cell.type != TileType.ACID_BARREL &&
            cell.type != TileType.EXIT

    fun evaluatePlan(level: LevelDefinition, placements: List<AlienPlacement>): PlanValidation {
        val usedBiomass = placements.sumOf { placementCost(it.species, it.stage) }
        val usedCommands = placements.sumOf { it.commands.size }

        val occupied = mutableSetOf<GridPos>()
        for (p in placements) {
            val cell = level.cellAt(p.startPos)
            if (cell == null || !cell.allowsAlienPlacement()) {
                return PlanValidation(
                    usedBiomass = usedBiomass,
                    remainingBiomass = level.maxBiomass - usedBiomass,
                    usedCommands = usedCommands,
                    remainingCommands = level.maxCommands - usedCommands,
                    isValid = false,
                    errorMessage = GameLocale.tr(
                        "Ungültiges Startfeld bei (${p.startPos.x}, ${p.startPos.y}).",
                        "Invalid start tile at (${p.startPos.x}, ${p.startPos.y})."
                    )
                )
            }
            if (!occupied.add(p.startPos)) {
                return PlanValidation(
                    usedBiomass = usedBiomass,
                    remainingBiomass = level.maxBiomass - usedBiomass,
                    usedCommands = usedCommands,
                    remainingCommands = level.maxCommands - usedCommands,
                    isValid = false,
                    errorMessage = GameLocale.tr(
                        "Doppelbelegung auf Feld (${p.startPos.x}, ${p.startPos.y}).",
                        "Tile already occupied at (${p.startPos.x}, ${p.startPos.y})."
                    )
                )
            }
        }

        val overBiomass = usedBiomass > level.maxBiomass
        val overCommands = usedCommands > level.maxCommands
        val error = when {
            placements.isEmpty() -> GameLocale.tr("Tippe ein lila Feld, um dein erstes Alien zu setzen.", "Tap a purple tile to place your first alien.")
            overBiomass -> GameLocale.tr(
                "Biomasse-Limit überschritten ($usedBiomass/${level.maxBiomass}).",
                "Biomass limit exceeded ($usedBiomass/${level.maxBiomass})."
            )
            overCommands -> GameLocale.tr(
                "Befehls-Limit überschritten ($usedCommands/${level.maxCommands}).",
                "Command limit exceeded ($usedCommands/${level.maxCommands})."
            )
            else -> null
        }
        return PlanValidation(
            usedBiomass = usedBiomass,
            remainingBiomass = level.maxBiomass - usedBiomass,
            usedCommands = usedCommands,
            remainingCommands = level.maxCommands - usedCommands,
            isValid = error == null,
            errorMessage = error
        )
    }

    /** Computes the projected grid position of a placement after executing its current command queue. */
    fun projectedEndPosition(level: LevelDefinition, placement: AlienPlacement): GridPos {
        var pos = placement.startPos
        for (cmd in placement.commands) {
            when (cmd) {
                is AlienCommand.Move -> {
                    val candidate = pos.move(cmd.dir)
                    if (isPlannableTile(level.cellAt(candidate))) {
                        pos = candidate
                    }
                }
                is AlienCommand.VentJump -> {
                    if (level.cellAt(cmd.target)?.type == TileType.VENT) {
                        pos = cmd.target
                    }
                }
                AlienCommand.ToggleConsole, AlienCommand.Wait, AlienCommand.HatchNow -> Unit
            }
        }
        return pos
    }

    /** Finds a paired VENT destination on the grid when standing on a VENT tile. */
    fun findLinkedVent(level: LevelDefinition, from: GridPos): GridPos? {
        val current = level.cellAt(from) ?: return null
        if (current.type != TileType.VENT) return null
        return level.cells.firstOrNull {
            it.type == TileType.VENT && it.pos != from && it.linkId == current.linkId
        }?.pos
    }

    /** Checks if a unit standing at [pos] can reach a CONSOLE tile (either on it or adjacent). */
    fun hasNearbyConsole(level: LevelDefinition, pos: GridPos): Boolean {
        val here = level.cellAt(pos)
        if (here?.type == TileType.CONSOLE) return true
        return Direction.entries.any { dir ->
            level.cellAt(pos.move(dir))?.type == TileType.CONSOLE
        }
    }

    /**
     * Plans the shortest deterministic command sequence for a single tap on [tappedPos]:
     * - Tapping a distant floor/vent tile queues the shortest path (including VentJumps if Vent-capable).
     * - Tapping a CONSOLE walks into reach (if needed) and automatically appends [AlienCommand.ToggleConsole].
     * - Tapping the unit's current end position triggers VentJump / ToggleConsole / HatchNow when applicable.
     */
    fun planSmartTap(
        level: LevelDefinition,
        placement: AlienPlacement,
        tappedPos: GridPos,
        remainingCommands: Int
    ): SmartTapPlan {
        val endPos = projectedEndPosition(level, placement)
        val canVent = canUseVents(placement)
        val tappedCell = level.cellAt(tappedPos) ?: return SmartTapPlan(emptyList())

        // 1. Tipp auf aktuelle Endposition -> kontextuelle Sofort-Aktion
        if (tappedPos == endPos) {
            val instantCmd: AlienCommand? = when {
                canVent && findLinkedVent(level, endPos) != null ->
                    AlienCommand.VentJump(findLinkedVent(level, endPos)!!)
                tappedCell.type == TileType.CONSOLE ->
                    AlienCommand.ToggleConsole
                placement.stage == EvolutionStage.EGG && AlienCommand.HatchNow !in placement.commands ->
                    AlienCommand.HatchNow
                else -> null
            }
            if (instantCmd == null) return SmartTapPlan(emptyList())
            return if (remainingCommands >= 1) {
                SmartTapPlan(listOf(instantCmd), requiredCommands = 1, exceededBudget = false)
            } else {
                SmartTapPlan(emptyList(), requiredCommands = 1, exceededBudget = true)
            }
        }

        // 2. Direkter Vent-Sprung, wenn bereits auf Partner-Vent
        if (canVent && findLinkedVent(level, endPos) == tappedPos) {
            return if (remainingCommands >= 1) {
                SmartTapPlan(listOf(AlienCommand.VentJump(tappedPos)), requiredCommands = 1, exceededBudget = false)
            } else {
                SmartTapPlan(emptyList(), requiredCommands = 1, exceededBudget = true)
            }
        }

        // 3. Tipp auf Konsole -> ggf. hinlaufen + automatisch umschalten
        if (tappedCell.type == TileType.CONSOLE) {
            if (tappedPos.isAdjacentTo(endPos)) {
                return if (remainingCommands >= 1) {
                    SmartTapPlan(listOf(AlienCommand.ToggleConsole), requiredCommands = 1, exceededBudget = false)
                } else {
                    SmartTapPlan(emptyList(), requiredCommands = 1, exceededBudget = true)
                }
            }
            val pathToReach = findShortestCommands(level, endPos, canVent) { pos ->
                pos == tappedPos || pos.isAdjacentTo(tappedPos)
            } ?: return SmartTapPlan(emptyList())
            val fullCmds = pathToReach + AlienCommand.ToggleConsole
            return if (fullCmds.size <= remainingCommands) {
                SmartTapPlan(fullCmds, requiredCommands = fullCmds.size, exceededBudget = false)
            } else {
                SmartTapPlan(emptyList(), requiredCommands = fullCmds.size, exceededBudget = true)
            }
        }

        // 4. Beliebiges begehbares Zielfeld -> kürzester deterministischer Pfad (inkl. Vent-Abkürzungen)
        if (!isPlannableTile(tappedCell)) return SmartTapPlan(emptyList())
        val path = findShortestCommands(level, endPos, canVent) { it == tappedPos }
            ?: return SmartTapPlan(emptyList())
        return if (path.size <= remainingCommands) {
            SmartTapPlan(path, requiredCommands = path.size, exceededBudget = false)
        } else {
            SmartTapPlan(emptyList(), requiredCommands = path.size, exceededBudget = true)
        }
    }

    /**
     * Returns all tiles reachable within [remainingCommands] from the placement's current projected position
     * (including linked Vents and Consoles toggleable within the remaining command budget).
     */
    fun reachableTiles(
        level: LevelDefinition,
        placement: AlienPlacement,
        remainingCommands: Int
    ): Set<GridPos> {
        if (remainingCommands <= 0) return emptySet()
        val start = projectedEndPosition(level, placement)
        val canVent = canUseVents(placement)

        val dist = HashMap<GridPos, Int>(64)
        val queue = ArrayDeque<GridPos>()
        dist[start] = 0
        queue.addLast(start)

        val result = LinkedHashSet<GridPos>()
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val d = dist[cur] ?: continue
            if (d > 0) result.add(cur)

            // Konsolen in Nachbarschaft sind mit +1 Befehl (ToggleConsole) erreichbar
            if (d + 1 <= remainingCommands) {
                for (dir in Direction.entries) {
                    val adj = cur.move(dir)
                    if (level.cellAt(adj)?.type == TileType.CONSOLE) {
                        result.add(adj)
                    }
                }
            }

            if (d >= remainingCommands) continue

            for (dir in Direction.entries) {
                val next = cur.move(dir)
                if (next !in dist && isPlannableTile(level.cellAt(next))) {
                    dist[next] = d + 1
                    queue.addLast(next)
                }
            }
            if (canVent) {
                val linked = findLinkedVent(level, cur)
                if (linked != null && linked !in dist) {
                    dist[linked] = d + 1
                    queue.addLast(linked)
                }
            }
        }
        return result
    }

    private fun findShortestCommands(
        level: LevelDefinition,
        start: GridPos,
        canVent: Boolean,
        isGoal: (GridPos) -> Boolean
    ): List<AlienCommand>? {
        if (isGoal(start)) return emptyList()
        val parent = HashMap<GridPos, Pair<GridPos, AlienCommand>>(64)
        val visited = HashSet<GridPos>(64)
        val queue = ArrayDeque<GridPos>()
        visited.add(start)
        queue.addLast(start)

        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()

            // Deterministische Reihenfolge: erst orthogonale Schritte, dann Vent-Sprung
            for (dir in Direction.entries) {
                val next = cur.move(dir)
                if (next !in visited && isPlannableTile(level.cellAt(next))) {
                    visited.add(next)
                    parent[next] = cur to AlienCommand.Move(dir)
                    if (isGoal(next)) return reconstructPath(start, next, parent)
                    queue.addLast(next)
                }
            }
            if (canVent) {
                val linked = findLinkedVent(level, cur)
                if (linked != null && linked !in visited) {
                    visited.add(linked)
                    parent[linked] = cur to AlienCommand.VentJump(linked)
                    if (isGoal(linked)) return reconstructPath(start, linked, parent)
                    queue.addLast(linked)
                }
            }
        }
        return null
    }

    private fun reconstructPath(
        start: GridPos,
        goal: GridPos,
        parent: Map<GridPos, Pair<GridPos, AlienCommand>>
    ): List<AlienCommand> {
        val reversed = mutableListOf<AlienCommand>()
        var cur = goal
        while (cur != start) {
            val step = parent[cur] ?: break
            reversed += step.second
            cur = step.first
        }
        return reversed.asReversed()
    }
}

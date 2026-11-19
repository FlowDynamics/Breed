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

/**
 * Manages Phase 1 (Variant A): placing units from the bottom dock onto valid room cells
 * and assigning deterministic step-by-step commands before Phase 2 starts.
 */
object CommandPlanner {

    fun placementCost(species: AlienSpecies, stage: EvolutionStage): Int =
        stage.effectiveCost(species)

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
            placements.isEmpty() -> GameLocale.tr("Platziere mindestens eine Einheit.", "Place at least one unit.")
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
                    val cell = level.cellAt(candidate)
                    if (cell != null && cell.type != TileType.WALL && cell.type != TileType.WINDOW && cell.type != TileType.ACID_BARREL && cell.type != TileType.EXIT) {
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
}

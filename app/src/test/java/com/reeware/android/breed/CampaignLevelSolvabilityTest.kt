package com.reeware.android.breed

import com.reeware.android.breed.data.BuiltInCampaign
import com.reeware.android.breed.engine.*
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies map integrity (walkable human routes, valid turret spawns, valid vent pairs)
 * and confirms every built-in campaign level has at least one valid Phase-1 victory plan.
 */
class CampaignLevelSolvabilityTest {

    @Test
    fun allBuiltInLevelsHaveValidMapsAndRoutes() {
        for (level in BuiltInCampaign.levels) {
            for (spawn in level.humanSpawns) {
                val cell = level.cellAt(spawn.spawn)
                assertTrue("${level.id}: invalid spawn cell at ${spawn.spawn}", cell != null && cell.type != TileType.WALL)
                if (!spawn.type.isStationary) {
                    assertTrue("${level.id}: mobile unit ${spawn.id} must end at EXIT", level.cellAt(spawn.route.last())?.type == TileType.EXIT)
                    for (step in spawn.route) {
                        val stepCell = level.cellAt(step)
                        assertTrue(
                            "${level.id}: blocked route step $step (${stepCell?.type}) for ${spawn.id}",
                            stepCell != null && stepCell.type.isWalkableForHumans(doorOpen = true)
                        )
                    }
                }
            }
        }
    }

    @Test
    fun allEightCampaignLevelsAreSolvable() {
        val solutions: Map<String, List<AlienPlacement>> = mapOf(
            "level_01_cargo" to listOf(
                AlienPlacement("a1", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(2, 2), listOf(AlienCommand.Move(Direction.EAST))),
                AlienPlacement("a2", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(4, 2), listOf(AlienCommand.Move(Direction.NORTH)))
            ),
            "level_02_vents" to listOf(
                AlienPlacement(
                    "a1", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(1, 3),
                    listOf(AlienCommand.VentJump(GridPos(5, 5)), AlienCommand.Wait, AlienCommand.Wait, AlienCommand.Move(Direction.NORTH))
                ),
                AlienPlacement("a2", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(5, 3), emptyList())
            ),
            "level_03_turret" to listOf(
                AlienPlacement(
                    "a1", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(1, 3),
                    listOf(AlienCommand.VentJump(GridPos(5, 6)), AlienCommand.ToggleConsole)
                ),
                AlienPlacement(
                    "a2", AlienSpecies.BRUTE, EvolutionStage.ADULT, GridPos(2, 4),
                    listOf(AlienCommand.Wait, AlienCommand.Wait, AlienCommand.Move(Direction.EAST))
                )
            ),
            "level_04_nightshift" to listOf(
                AlienPlacement(
                    "a1", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(2, 5),
                    listOf(AlienCommand.Wait, AlienCommand.Wait, AlienCommand.Move(Direction.NORTH))
                ),
                AlienPlacement(
                    "a2", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(4, 5),
                    listOf(AlienCommand.Wait, AlienCommand.Wait, AlienCommand.Move(Direction.NORTH), AlienCommand.Move(Direction.NORTH))
                )
            ),
            "level_05_bridge" to listOf(
                AlienPlacement(
                    "a1", AlienSpecies.SPITTER, EvolutionStage.ADULT, GridPos(1, 3),
                    listOf(AlienCommand.Move(Direction.NORTH))
                ),
                AlienPlacement(
                    "a2", AlienSpecies.BRUTE, EvolutionStage.ADULT, GridPos(5, 3),
                    listOf(AlienCommand.Move(Direction.WEST))
                ),
                AlienPlacement(
                    "a3", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(5, 5),
                    listOf(AlienCommand.Move(Direction.WEST), AlienCommand.Move(Direction.NORTH))
                )
            ),
            "level_06_cryo" to listOf(
                AlienPlacement(
                    "a1", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(2, 2),
                    listOf(AlienCommand.Move(Direction.NORTH))
                ),
                AlienPlacement(
                    "a2", AlienSpecies.BRUTE, EvolutionStage.ADULT, GridPos(4, 2),
                    listOf(AlienCommand.Move(Direction.NORTH))
                ),
                AlienPlacement(
                    "a3", AlienSpecies.WEAVER, EvolutionStage.ADULT, GridPos(5, 5),
                    listOf(AlienCommand.Move(Direction.NORTH), AlienCommand.Move(Direction.NORTH), AlienCommand.Move(Direction.WEST))
                )
            ),
            "level_07_crossfire" to listOf(
                AlienPlacement(
                    "a1", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(1, 3),
                    listOf(AlienCommand.VentJump(GridPos(5, 5)), AlienCommand.ToggleConsole)
                ),
                AlienPlacement(
                    "a2", AlienSpecies.SPITTER, EvolutionStage.ADULT, GridPos(4, 3),
                    listOf(AlienCommand.Wait)
                ),
                AlienPlacement(
                    "a3", AlienSpecies.BRUTE, EvolutionStage.ADULT, GridPos(1, 5),
                    listOf(AlienCommand.Move(Direction.EAST))
                ),
                AlienPlacement(
                    "a4", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(2, 2),
                    listOf(AlienCommand.Wait)
                )
            ),
            "level_08_citadel" to listOf(
                AlienPlacement(
                    "a1", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(1, 3),
                    listOf(AlienCommand.VentJump(GridPos(5, 5)), AlienCommand.ToggleConsole)
                ),
                AlienPlacement(
                    "a2", AlienSpecies.SPITTER, EvolutionStage.ADULT, GridPos(2, 2),
                    listOf(AlienCommand.Wait)
                ),
                AlienPlacement(
                    "a3", AlienSpecies.BRUTE, EvolutionStage.ADULT, GridPos(1, 6),
                    listOf(AlienCommand.Move(Direction.EAST), AlienCommand.Move(Direction.NORTH))
                ),
                AlienPlacement(
                    "a4", AlienSpecies.PSION, EvolutionStage.ADULT, GridPos(4, 2),
                    listOf(AlienCommand.Wait)
                )
            )
        )

        val failures = mutableListOf<String>()
        for (level in BuiltInCampaign.levels) {
            val plan = solutions[level.id] ?: error("Missing test solution for ${level.id}")
            val validation = CommandPlanner.evaluatePlan(level, plan)
            if (!validation.isValid) {
                failures += "${level.id} plan invalid: ${validation.errorMessage}"
                continue
            }

            val result = TurnSimulator.simulate(level, plan, MutationNode.entries.toSet())
            if (!result.isVictory) {
                failures += "${level.id} did not win (outcome=${result.finalOutcome}, round=${result.totalRounds}, logs=${result.snapshots.last().logs})"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}

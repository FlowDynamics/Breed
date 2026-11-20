package com.reeware.android.breed

import com.reeware.android.breed.data.BuiltInCampaign
import com.reeware.android.breed.engine.*
import org.junit.Assert.*
import org.junit.Test

/** Unit-Tests für 1-Tipp-Auto-Routing, Vent-Abkürzungen, Konsolen-Auto-Toggle und Reichweiten-Berechnung. */
class CommandPlannerTest {

    @Test
    fun smartTapAutoRoutesMultipleStepsInOneTap() {
        val level1 = BuiltInCampaign.levelById("level_01_cargo")
        val stalker = AlienPlacement("a1", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(2, 2))

        val plan = CommandPlanner.planSmartTap(level1, stalker, GridPos(3, 1), remainingCommands = 6)

        assertFalse(plan.exceededBudget)
        assertEquals(2, plan.commands.size)
        val after = stalker.copy(commands = plan.commands)
        assertEquals(GridPos(3, 1), CommandPlanner.projectedEndPosition(level1, after))
    }

    @Test
    fun smartTapRespectsRemainingCommandBudget() {
        val level1 = BuiltInCampaign.levelById("level_01_cargo")
        val stalker = AlienPlacement("a1", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(2, 2))

        val tooFar = CommandPlanner.planSmartTap(level1, stalker, GridPos(3, 1), remainingCommands = 1)

        assertTrue(tooFar.exceededBudget)
        assertTrue(tooFar.commands.isEmpty())
        assertEquals(2, tooFar.requiredCommands)
    }

    @Test
    fun smartTapRoutesThroughLinkedVentsForVentCapableAliensOnly() {
        val level2 = BuiltInCampaign.levelById("level_02_vents")
        val vents = level2.cells.filter { it.type == TileType.VENT }
        assertTrue("Level 2 muss verknüpfte Vents enthalten", vents.size >= 2)
        val entryVent = vents.first { it.pos.x < 3 }.pos
        val exitVent = CommandPlanner.findLinkedVent(level2, entryVent)!!

        // Lauerer (Vent-fähig) kann per 1 Tipp direkt das Ziel-Vent auf der anderen Seite der Wand ansteuern!
        val stalker = AlienPlacement("s1", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(2, 2))
        val stalkerPlan = CommandPlanner.planSmartTap(level2, stalker, exitVent, remainingCommands = 8)
        assertFalse(stalkerPlan.exceededBudget)
        assertTrue("Auto-Route muss VentJump enthalten", stalkerPlan.commands.any { it is AlienCommand.VentJump })
        assertEquals(exitVent, CommandPlanner.projectedEndPosition(level2, stalker.copy(commands = stalkerPlan.commands)))

        // Auch Larven sind Vent-fähig (wie in TurnSimulator)
        val larva = AlienPlacement("l1", AlienSpecies.BRUTE, EvolutionStage.LARVA, entryVent)
        val larvaPlan = CommandPlanner.planSmartTap(level2, larva, exitVent, remainingCommands = 4)
        assertEquals(listOf(AlienCommand.VentJump(exitVent)), larvaPlan.commands)

        // Ausgewachsener Koloss darf KEINEN Vent-Sprung planen
        val brute = AlienPlacement("b1", AlienSpecies.BRUTE, EvolutionStage.ADULT, entryVent)
        val brutePlan = CommandPlanner.planSmartTap(level2, brute, exitVent, remainingCommands = 2)
        assertTrue("Koloss darf nicht durch Vent springen", brutePlan.commands.none { it is AlienCommand.VentJump })
    }

    @Test
    fun smartTapWalksToConsoleAndAutoAppendsToggleConsole() {
        val level3 = BuiltInCampaign.levelById("level_03_turret")
        val consolePos = level3.cells.first { it.type == TileType.CONSOLE }.pos
        val startTile = level3.cells.first { it.allowsAlienPlacement() && it.pos != consolePos }.pos
        val stalker = AlienPlacement("s1", AlienSpecies.STALKER, EvolutionStage.ADULT, startTile)

        val plan = CommandPlanner.planSmartTap(level3, stalker, consolePos, remainingCommands = 12)

        assertFalse(plan.exceededBudget)
        assertTrue(plan.commands.isNotEmpty())
        assertEquals(AlienCommand.ToggleConsole, plan.commands.last())
        val finalPos = CommandPlanner.projectedEndPosition(level3, stalker.copy(commands = plan.commands))
        assertTrue(CommandPlanner.hasNearbyConsole(level3, finalPos))
    }

    @Test
    fun smartTapOnCurrentTileTriggersEggHatch() {
        val level1 = BuiltInCampaign.levelById("level_01_cargo")
        val egg = AlienPlacement("e1", AlienSpecies.STALKER, EvolutionStage.EGG, GridPos(2, 2))

        val plan = CommandPlanner.planSmartTap(level1, egg, GridPos(2, 2), remainingCommands = 4)

        assertEquals(listOf(AlienCommand.HatchNow), plan.commands)
    }

    @Test
    fun reachableTilesMatchesSmartTapReachabilityAndIsDeterministic() {
        val level1 = BuiltInCampaign.levelById("level_01_cargo")
        val stalker = AlienPlacement("a1", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(2, 2))

        val r1 = CommandPlanner.reachableTiles(level1, stalker, remainingCommands = 2)
        val r2 = CommandPlanner.reachableTiles(level1, stalker, remainingCommands = 2)

        assertEquals(r1, r2)
        assertTrue(GridPos(3, 1) in r1)
        assertFalse(GridPos(2, 2) in r1)
        for (tile in r1) {
            val plan = CommandPlanner.planSmartTap(level1, stalker, tile, remainingCommands = 2)
            assertFalse("Erreichbares Feld $tile darf Budget nicht überschreiten", plan.exceededBudget)
            assertTrue("Erreichbares Feld $tile muss Befehle liefern", plan.commands.isNotEmpty())
        }
    }
}

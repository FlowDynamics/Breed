package com.reeware.android.breed

import com.reeware.android.breed.data.BuiltInCampaign
import com.reeware.android.breed.data.LevelJsonCodec
import com.reeware.android.breed.engine.*
import org.junit.Assert.*
import org.junit.Test

class TurnSimulatorTest {

    @Test
    fun deterministicSimulationReturnsIdenticalTrace() {
        val level = BuiltInCampaign.levelById("level_01_cargo")
        val plan = listOf(
            AlienPlacement("a1", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(2, 2), listOf(AlienCommand.Move(Direction.EAST))),
            AlienPlacement("a2", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(4, 2), listOf(AlienCommand.Move(Direction.NORTH)))
        )
        val first = TurnSimulator.simulate(level, plan)
        val second = TurnSimulator.simulate(level, plan)
        assertEquals(first, second)
        assertEquals(BattleOutcome.VICTORY, first.finalOutcome)
    }

    @Test
    fun outnumberingDecidesCloseMarineMatchup() {
        val level = BuiltInCampaign.levelById("level_01_cargo")
        val soloLarva = listOf(
            AlienPlacement("l1", AlienSpecies.STALKER, EvolutionStage.LARVA, GridPos(4, 2), emptyList())
        )
        val soloResult = TurnSimulator.simulate(level, soloLarva)
        assertFalse("Single larva must not solo a Space Marine", soloResult.isVictory)

        val swarmFlank = listOf(
            AlienPlacement("s1", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(2, 2), listOf(AlienCommand.Move(Direction.EAST))),
            AlienPlacement("s2", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(4, 2), listOf(AlienCommand.Move(Direction.NORTH)))
        )
        val swarmResult = TurnSimulator.simulate(level, swarmFlank)
        assertTrue("Two flanking aliens should overwhelm the Marine via 2v1", swarmResult.isVictory)
    }

    @Test
    fun consoleSwitchDisablesTurretAndClosesDoor() {
        val level = BuiltInCampaign.levelById("level_03_turret")
        // Without hitting the console, a lone Brute in the open gets shredded by turret + marine or fails to stop escape
        val noSwitchPlan = listOf(
            AlienPlacement("s1", AlienSpecies.STALKER, EvolutionStage.LARVA, GridPos(3, 6), emptyList())
        )
        assertFalse(TurnSimulator.simulate(level, noSwitchPlan).isVictory)

        // With Stalker VentJump -> ToggleConsole + Brute holding the choke point
        val switchPlan = listOf(
            AlienPlacement(
                "s1", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(1, 3),
                listOf(AlienCommand.VentJump(GridPos(5, 6)), AlienCommand.ToggleConsole)
            ),
            AlienPlacement(
                "b1", AlienSpecies.BRUTE, EvolutionStage.ADULT, GridPos(2, 4),
                listOf(AlienCommand.Wait, AlienCommand.Wait, AlienCommand.Move(Direction.EAST))
            )
        )
        val result = TurnSimulator.simulate(level, switchPlan)
        assertTrue("Expected victory after disabling turret and closing door: ${result.finalOutcome}", result.isVictory)
    }

    @Test
    fun spitterMeltsHeavyTankArmorWhereLarvaeBounce() {
        val level = BuiltInCampaign.levelById("level_05_bridge")
        // Three larvae alone bounce off tank armor 3
        val larvaeOnly = listOf(
            AlienPlacement("l1", AlienSpecies.STALKER, EvolutionStage.LARVA, GridPos(1, 3), listOf(AlienCommand.Move(Direction.EAST), AlienCommand.Move(Direction.EAST))),
            AlienPlacement("l2", AlienSpecies.STALKER, EvolutionStage.LARVA, GridPos(5, 3), listOf(AlienCommand.Move(Direction.WEST))),
            AlienPlacement("l3", AlienSpecies.STALKER, EvolutionStage.LARVA, GridPos(5, 5), listOf(AlienCommand.Move(Direction.WEST), AlienCommand.Move(Direction.NORTH)))
        )
        assertFalse("Pure larvae should lose against heavy Tank armor", TurnSimulator.simulate(level, larvaeOnly).isVictory)

        // Spitter + Brute + Stalker counter the Tank + Marine escort
        val counterPlan = listOf(
            AlienPlacement(
                "sp1", AlienSpecies.SPITTER, EvolutionStage.ADULT, GridPos(1, 3),
                listOf(AlienCommand.Move(Direction.EAST), AlienCommand.Move(Direction.EAST))
            ),
            AlienPlacement(
                "br1", AlienSpecies.BRUTE, EvolutionStage.ADULT, GridPos(5, 3),
                listOf(AlienCommand.Move(Direction.WEST))
            ),
            AlienPlacement(
                "st1", AlienSpecies.STALKER, EvolutionStage.ADULT, GridPos(5, 5),
                listOf(AlienCommand.Move(Direction.WEST), AlienCommand.Move(Direction.NORTH))
            )
        )
        val res = TurnSimulator.simulate(level, counterPlan, setOf(MutationNode.SPITTER_CORROSION))
        assertTrue("Spitter + Brute + Stalker should defeat Tank level: ${res.finalOutcome}", res.isVictory)
    }

    @Test
    fun nightCycleReducesMarineSightAndAcceleratesEggHatch() {
        val dn = DayNightConfig(startsAtNight = false, switchEveryRounds = 2)
        assertFalse(dn.isNightAtRound(1))
        assertFalse(dn.isNightAtRound(2))
        assertTrue(dn.isNightAtRound(3))
        assertTrue(dn.isNightAtRound(4))
        assertFalse(dn.isNightAtRound(5))

        // Egg hatched in night cycle hatches in 1 round (hatchDelay = 2, rate = 2 at night)
        val nightLevel = BuiltInCampaign.levelById("level_02_vents")
        val eggPlan = listOf(
            AlienPlacement("e1", AlienSpecies.STALKER, EvolutionStage.EGG, GridPos(2, 2), emptyList())
        )
        val sim = TurnSimulator.simulate(nightLevel, eggPlan)
        assertEquals(EvolutionStage.ADULT, sim.snapshots[1].aliens.first().stage)
    }

    @Test
    fun defeatCausesLossOfPreviouslyConqueredRoom() {
        val nodes = BuiltInCampaign.shipNodes
        var progress = CampaignProgress.initial(nodes)
        val (afterWin1, _) = progress.applyVictory(nodes, "level_01_cargo")
        progress = afterWin1
        assertEquals(RoomStatus.CONQUERED, progress.roomStatuses["level_01_cargo"])
        assertEquals(RoomStatus.AVAILABLE, progress.roomStatuses["level_02_vents"])

        val upgraded = progress.upgradeRoom("level_01_cargo", RoomUpgrade.BROOD_CHAMBER)
        assertNotNull(upgraded)
        progress = upgraded!!
        assertEquals(1, progress.extraBiomassBonus())

        val (afterLoss, penalty) = progress.applyDefeat(nodes)
        assertEquals("level_01_cargo", penalty.lostRoomId)
        assertEquals(RoomStatus.LOST, afterLoss.roomStatuses["level_01_cargo"])
        assertEquals(0, afterLoss.extraBiomassBonus())
    }

    @Test
    fun levelJsonCodecRoundTripPreservesAllFields() {
        for (level in BuiltInCampaign.levels) {
            val encoded = LevelJsonCodec.encodeLevel(level)
            val decoded = LevelJsonCodec.decodeLevel(encoded)
            assertEquals(level.id, decoded.id)
            assertEquals(level.title, decoded.title)
            assertEquals(level.planetTheme, decoded.planetTheme)
            assertEquals(level.dayNight, decoded.dayNight)
            assertEquals(level.humanSpawns, decoded.humanSpawns)
            assertEquals(level.cells.toSet(), decoded.cells.toSet())
        }
    }
}

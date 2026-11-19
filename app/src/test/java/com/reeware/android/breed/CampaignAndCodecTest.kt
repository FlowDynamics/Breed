package com.reeware.android.breed

import com.reeware.android.breed.data.BuiltInCampaign
import com.reeware.android.breed.data.LevelJsonCodec
import com.reeware.android.breed.engine.*
import org.junit.Assert.*
import org.junit.Test

class CampaignAndCodecTest {

    @Test
    fun eachSpeciesHasDedicatedMutationTreeWithoutCrossBreeding() {
        for (species in AlienSpecies.entries) {
            val nodes = MutationNode.entries.filter { it.species == species }
            assertEquals("Jede Art braucht genau 2 eigene Mutationsknoten", 2, nodes.size)
        }
    }

    @Test
    fun campaignVictoryUnlocksNeighborsAndDefeatCausesRoomLoss() {
        var progress = CampaignProgress.initial(BuiltInCampaign.shipNodes)
        assertEquals(RoomStatus.AVAILABLE, progress.roomStatuses["level_01_cargo"])
        assertEquals(RoomStatus.LOCKED, progress.roomStatuses["level_02_vents"])

        val (afterWin, reward) = progress.applyVictory(BuiltInCampaign.shipNodes, "level_01_cargo")
        progress = afterWin
        assertTrue(reward >= 20)
        assertEquals(RoomStatus.CONQUERED, progress.roomStatuses["level_01_cargo"])
        assertEquals(RoomStatus.AVAILABLE, progress.roomStatuses["level_02_vents"])
        assertEquals(RoomStatus.AVAILABLE, progress.roomStatuses["level_03_turret"])

        // Build an upgrade in the conquered room (give enough mutagens via a second win)
        val (afterWin2, _) = progress.applyVictory(BuiltInCampaign.shipNodes, "level_02_vents")
        progress = afterWin2.upgradeRoom("level_01_cargo", RoomUpgrade.BROOD_CHAMBER)
            ?: error("Ausbau sollte mit ausreichend Mutagenen gelingen")
        assertEquals(1, progress.extraBiomassBonus())

        // Losing a room reverts the most recently conquered room to LOST and strips its upgrade
        val (afterLoss, penalty) = progress.applyDefeat(BuiltInCampaign.shipNodes)
        assertEquals("level_02_vents", penalty.lostRoomId)
        assertEquals(RoomStatus.LOST, afterLoss.roomStatuses["level_02_vents"])
        assertTrue(afterLoss.isRoomPlayable("level_02_vents"))
    }

    @Test
    fun levelAndProgressSurviveJsonRoundTripIdentically() {
        val originalLevel = BuiltInCampaign.levelById("level_05_bridge")
        val decodedLevel = LevelJsonCodec.decodeLevel(LevelJsonCodec.encodeLevel(originalLevel))
        assertEquals(originalLevel, decodedLevel)

        var progress = CampaignProgress.initial(BuiltInCampaign.shipNodes)
        val (won, _) = progress.applyVictory(BuiltInCampaign.shipNodes, "level_01_cargo")
        progress = won.unlockMutation(MutationNode.STALKER_SHADOW) ?: error("Mutation freischaltbar")
        progress = progress.copy(mutagens = 100).unlockAlpha(AlienSpecies.SPITTER) ?: error("Alpha freischaltbar")

        val roundTripped = LevelJsonCodec.decodeProgress(
            LevelJsonCodec.encodeProgress(progress),
            BuiltInCampaign.shipNodes
        )
        assertEquals(progress, roundTripped)
    }
}

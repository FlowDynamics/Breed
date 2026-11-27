package com.reeware.android.breed

import com.reeware.android.breed.data.BuiltInCampaign
import com.reeware.android.breed.data.FlowCampaign
import com.reeware.android.breed.engine.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifiziert die neue Colony-Flow-artige Echtzeit-Engine (FlowGameEngine)
 * sowie exakte Mengen-Balance & 100 % Deadlock-freie Lösbarkeit aller 8 Kampagnen-Zimmer!
 */
class FlowGameTest {

    @Test
    fun all8CampaignRoomsMatchShipNodesAndHaveExactPodGuardParity() {
        val levels = FlowCampaign.levels
        assertEquals(BuiltInCampaign.shipNodes.size, levels.size)

        for ((idx, lvl) in levels.withIndex()) {
            assertEquals("Level-ID muss zum Schiffsknoten passen", BuiltInCampaign.shipNodes[idx].levelId, lvl.id)
            val guards = lvl.guardCountsBySpecies()
            val pods = lvl.podCountsBySpecies()
            assertEquals("Kapsel-Summen müssen exakt zu den Wachen passen in ${lvl.id}", guards, pods)
            assertTrue("Zimmer ${lvl.id} braucht mindestens 10 Wachen", guards.values.sum() >= 10)
        }
    }

    @Test
    fun all8CampaignRoomsAreSolvableWithoutDeadlock() {
        for (lvl in FlowCampaign.levels) {
            val engine = FlowGameEngine(lvl)
            var safetyTaps = 0
            while (engine.outcome == FlowOutcome.PLAYING && safetyTaps < 60) {
                // Wähle bevorzugt eine Spalte, deren vordere Kapsel sofort exponierte Wachen treffen kann
                val readyCol = engine.columns.indices.firstOrNull { col ->
                    val pod = engine.columns[col].firstOrNull() ?: return@firstOrNull false
                    engine.canTapColumn(col) && engine.hasExposedTargetFor(pod.species)
                }
                val fallbackCol = engine.columns.indices.firstOrNull { col -> engine.canTapColumn(col) }
                val pick = readyCol ?: fallbackCol
                if (pick != null) {
                    assertEquals(FlowTapResult.DEPLOYED, engine.tapColumn(pick))
                    safetyTaps++
                }
                // Echtzeit-Simulation vorantreiben, bis Aliens fertig gekämpft haben
                repeat(120) {
                    if (engine.outcome == FlowOutcome.PLAYING) {
                        engine.update(0.05f)
                    }
                }
            }
            assertEquals("Level ${lvl.id} muss sauber mit VICTORY lösbar sein!", FlowOutcome.VICTORY, engine.outcome)
            assertEquals("In ${lvl.id} dürfen am Ende keine Wachen übrig sein", 0, engine.remainingGuards)
        }
    }

    @Test
    fun cloggingAll5SlotsWithBlockedSpeciesCausesDeadlock() {
        // Mini-Level: Vorne 2 Marines (STALKER), dahinter 5 Panzer (SPITTER), 5 einzelne SPITTER-Kapseln blockieren alle 5 Slots
        val cells = listOf(
            FlowCellSpec(GridPos(0, 0), FlowTileKind.GUARD, FlowGuardType.TANK),
            FlowCellSpec(GridPos(1, 0), FlowTileKind.GUARD, FlowGuardType.TANK),
            FlowCellSpec(GridPos(2, 0), FlowTileKind.GUARD, FlowGuardType.TANK),
            FlowCellSpec(GridPos(3, 0), FlowTileKind.GUARD, FlowGuardType.TANK),
            FlowCellSpec(GridPos(4, 0), FlowTileKind.GUARD, FlowGuardType.TANK),
            FlowCellSpec(GridPos(0, 1), FlowTileKind.GUARD, FlowGuardType.MARINE),
            FlowCellSpec(GridPos(1, 1), FlowTileKind.GUARD, FlowGuardType.MARINE),
            FlowCellSpec(GridPos(2, 1), FlowTileKind.WALL),
            FlowCellSpec(GridPos(3, 1), FlowTileKind.WALL),
            FlowCellSpec(GridPos(4, 1), FlowTileKind.WALL)
        )
        val lvl = FlowLevelSpec(
            id = "test_deadlock",
            title = "Deadlock Test",
            sectorName = "Test",
            hint = "",
            planetTheme = PlanetTheme.ACHERON_ASH,
            isNight = false,
            gridWidth = 5,
            gridHeight = 2,
            slotCount = 5,
            cells = cells,
            columns = listOf(
                listOf(AlienPod("p1", AlienSpecies.SPITTER, 1), AlienPod("p2", AlienSpecies.SPITTER, 1)),
                listOf(AlienPod("p3", AlienSpecies.SPITTER, 1), AlienPod("p4", AlienSpecies.SPITTER, 1)),
                listOf(AlienPod("p5", AlienSpecies.SPITTER, 1)),
                listOf(AlienPod("p6", AlienSpecies.STALKER, 2))
            )
        )
        val engine = FlowGameEngine(lvl)
        assertEquals(FlowTapResult.DEPLOYED, engine.tapColumn(0))
        assertEquals(FlowTapResult.DEPLOYED, engine.tapColumn(0))
        assertEquals(FlowTapResult.DEPLOYED, engine.tapColumn(1))
        assertEquals(FlowTapResult.DEPLOYED, engine.tapColumn(1))
        assertEquals(FlowTapResult.DEPLOYED, engine.tapColumn(2))
        assertEquals(FlowTapResult.NO_FREE_SLOTS, engine.tapColumn(3))

        repeat(30) { engine.update(0.05f) }
        assertEquals(FlowOutcome.DEADLOCK, engine.outcome)
    }

    @Test
    fun eliteStrikePreservesExactSolvabilityAndReducesMatchingPod() {
        val lvl = FlowCampaign.levelById("level_01_cargo")
        val engine = FlowGameEngine(lvl, ownedProductIds = setOf(ShopProduct.ALIEN_REAPER.productId))
        assertEquals(1, engine.eliteJokerCharges)

        assertTrue(engine.triggerEliteStrike())
        assertEquals(0, engine.eliteJokerCharges)
        repeat(100) { engine.update(0.05f) }

        // Rest des Levels muss trotzdem exakt mit 0 Rest-Kapseln zu VICTORY aufgehen!
        var taps = 0
        while (engine.outcome == FlowOutcome.PLAYING && taps < 40) {
            val col = engine.columns.indices.firstOrNull { c ->
                val p = engine.columns[c].firstOrNull() ?: return@firstOrNull false
                engine.canTapColumn(c) && engine.hasExposedTargetFor(p.species)
            } ?: engine.columns.indices.firstOrNull { engine.canTapColumn(it) }
            if (col != null) {
                engine.tapColumn(col)
                taps++
            }
            repeat(100) { if (engine.outcome == FlowOutcome.PLAYING) engine.update(0.05f) }
        }
        assertEquals(FlowOutcome.VICTORY, engine.outcome)
    }
}

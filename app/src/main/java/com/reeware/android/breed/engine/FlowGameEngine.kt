package com.reeware.android.breed.engine

import kotlin.math.hypot

/**
 * Android-freie 60-FPS-Echtzeit-Engine für BREED (Colony-Flow-inspiriertes Alien-Schwarm-Gameplay):
 * - Unten liegen 4 Spalten aus farbigen Alien-Kapseln (nur vorderste Reihe direkt antippbar).
 * - Tippen schickt eine Kapsel in einen der 5 aktiven Brut-Slots.
 * - Aliens stürmen in Echtzeit ins Raumschiff-Zimmer nach oben und kämpfen gegen passende Wachen an der offenen Front.
 * - Gefallene Wachen verwandeln den Boden in Alien-Creep, sprengen angrenzende Säurefässer/Schotts und legen innere Wachen frei.
 * - Sind alle 5 Slots voll und kein Slot hat erreichbare Ziele mehr -> Blockade (Deadlock).
 * - Sind alle Wachen ausgeschaltet -> Sieg!
 */
class FlowGameEngine(
    val level: FlowLevelSpec,
    unlockedMutations: Set<MutationNode> = emptySet(),
    equippedWeapons: Set<BioWeapon> = emptySet(),
    ownedProductIds: Set<String> = emptySet()
) {
    val gridWidth: Int = level.gridWidth
    val gridHeight: Int = level.gridHeight
    val slotCount: Int = level.slotCount

    val cells: Array<Array<FlowCellState>> = Array(gridWidth) { x ->
        Array(gridHeight) { y ->
            FlowCellState(GridPos(x, y), FlowTileKind.VOID)
        }
    }

    val columns: List<MutableList<AlienPod>> = level.columns.map { col ->
        col.map { it.copy() }.toMutableList()
    }

    val slots: Array<FlowActiveSlot?> = arrayOfNulls(slotCount)
    val runningAliens: MutableList<RunningAlien> = mutableListOf()
    val visualFx: MutableList<FlowVisualFx> = mutableListOf()
    val recentEvents: MutableList<FlowEventKind> = mutableListOf()

    var outcome: FlowOutcome = FlowOutcome.PLAYING
        private set

    var totalGuards: Int = 0
        private set
    var remainingGuards: Int = 0
        private set

    var screenShakeSec: Float = 0f
        private set

    // Boni durch Mutationen & optionale Bio-Waffen aus dem Shop
    private val speedMultiplier: Float
    private val fightDurationSec: Float
    private val spawnIntervalSec: Float

    // Optionale Elite-Joker-Ladungen (1 pro Raum für jedes freigeschaltete Elite-Alien)
    var eliteJokerCharges: Int = 0
        private set
    val eliteSpeciesAvailable: List<AlienSpecies>

    private var alienIdSeq = 0
    private var deadlockGraceSec = 0f

    init {
        for (spec in level.cells) {
            if (spec.pos.x in 0 until gridWidth && spec.pos.y in 0 until gridHeight) {
                cells[spec.pos.x][spec.pos.y] = FlowCellState(
                    pos = spec.pos,
                    kind = spec.kind,
                    guardType = spec.guardType
                )
                if (spec.kind == FlowTileKind.GUARD) {
                    totalGuards++
                }
            }
        }
        remainingGuards = totalGuards

        val mutBonus = (unlockedMutations.size * 0.04f).coerceAtMost(0.32f)
        val weaponBonus = if (equippedWeapons.isNotEmpty()) 0.14f * equippedWeapons.size else 0f
        speedMultiplier = 1f + mutBonus + weaponBonus
        fightDurationSec = (0.32f / (1f + weaponBonus * 0.7f)).coerceAtLeast(0.18f)
        spawnIntervalSec = (0.25f / (1f + mutBonus * 0.5f)).coerceAtLeast(0.14f)

        val elites = mutableListOf<AlienSpecies>()
        if (ShopProduct.isSpeciesUnlocked(AlienSpecies.REAPER, ownedProductIds)) elites += AlienSpecies.REAPER
        if (ShopProduct.isSpeciesUnlocked(AlienSpecies.BOMBER, ownedProductIds)) elites += AlienSpecies.BOMBER
        eliteSpeciesAvailable = elites
        eliteJokerCharges = elites.size

        recomputeExposedGuards()
    }

    /** Aktuelle Ereignisse abholen & leeren (für Sound-Effekte & Haptik in der UI). */
    fun drainEvents(): List<FlowEventKind> {
        if (recentEvents.isEmpty()) return emptyList()
        val copy = recentEvents.toList()
        recentEvents.clear()
        return copy
    }

    fun freeSlotCount(): Int = slots.count { it == null }

    /** Prüft, ob die vorderste Kapsel einer Spalte aktuell direkt einsetzbar ist. */
    fun canTapColumn(colIndex: Int): Boolean {
        if (outcome != FlowOutcome.PLAYING) return false
        if (colIndex !in columns.indices) return false
        val pod = columns[colIndex].firstOrNull() ?: return false
        if (pod.linkedGroupId == 0) {
            return freeSlotCount() >= 1
        }
        val partnerCol = findLinkedFrontColumn(colIndex, pod.linkedGroupId) ?: return false
        return partnerCol != colIndex && freeSlotCount() >= 2
    }

    /** Prüft, ob eine Alien-Art aktuell mindestens eine freie erreichbare Ziel-Wache an der Front hat. */
    fun hasExposedTargetFor(species: AlienSpecies): Boolean {
        for (x in 0 until gridWidth) {
            for (y in 0 until gridHeight) {
                val c = cells[x][y]
                if (c.kind == FlowTileKind.GUARD && c.exposed && !c.reserved &&
                    c.guardType?.counterSpecies == species
                ) {
                    return true
                }
            }
        }
        return false
    }

    /** Tippt die vorderste Alien-Kapsel in [colIndex] an und bewegt sie in einen freien Brut-Slot. */
    fun tapColumn(colIndex: Int): FlowTapResult {
        if (outcome != FlowOutcome.PLAYING) return FlowTapResult.GAME_OVER
        if (colIndex !in columns.indices) return FlowTapResult.EMPTY_COLUMN
        val pod = columns[colIndex].firstOrNull() ?: return FlowTapResult.EMPTY_COLUMN

        if (pod.linkedGroupId == 0) {
            val slotIdx = slots.indexOfFirst { it == null }
            if (slotIdx < 0) return FlowTapResult.NO_FREE_SLOTS
            columns[colIndex].removeAt(0)
            slots[slotIdx] = FlowActiveSlot(
                slotIndex = slotIdx,
                id = pod.id,
                species = pod.species,
                remaining = pod.count,
                maxCount = pod.maxCount
            )
            deadlockGraceSec = 0f
            recentEvents += FlowEventKind.POD_DEPLOYED
            updateSlotStallFlags()
            return FlowTapResult.DEPLOYED
        }

        // Gekoppeltes Paar (2 verbundene Kapseln belegen gleichzeitig 2 freie Slots)
        val partnerCol = findLinkedFrontColumn(colIndex, pod.linkedGroupId)
            ?: return FlowTapResult.LINKED_PARTNER_BLOCKED
        val freeIndices = slots.indices.filter { slots[it] == null }
        if (freeIndices.size < 2) return FlowTapResult.NO_FREE_SLOTS

        val firstCol = minOf(colIndex, partnerCol)
        val secondCol = maxOf(colIndex, partnerCol)
        val podA = columns[firstCol].removeAt(0)
        val podB = columns[secondCol].removeAt(0)

        slots[freeIndices[0]] = FlowActiveSlot(
            slotIndex = freeIndices[0],
            id = podA.id,
            species = podA.species,
            remaining = podA.count,
            maxCount = podA.maxCount
        )
        slots[freeIndices[1]] = FlowActiveSlot(
            slotIndex = freeIndices[1],
            id = podB.id,
            species = podB.species,
            remaining = podB.count,
            maxCount = podB.maxCount
        )
        deadlockGraceSec = 0f
        recentEvents += FlowEventKind.POD_DEPLOYED
        updateSlotStallFlags()
        return FlowTapResult.DEPLOYED
    }

    /**
     * Optionale Elite-Verstärkung (Klingen-Bestie / Sporen-Bomber aus dem Shop):
     * Schickt 2 Elite-Aliens auf beliebige exponierte Front-Wachen (unabhängig von deren Klasse)
     * und reduziert passende Kapsel-Zähler fair um 1, damit die Gesamtzahl immer exakt lösbar bleibt!
     */
    fun triggerEliteStrike(): Boolean {
        if (outcome != FlowOutcome.PLAYING || eliteJokerCharges <= 0) return false
        val exposed = mutableListOf<FlowCellState>()
        for (y in gridHeight - 1 downTo 0) {
            for (x in 0 until gridWidth) {
                val c = cells[x][y]
                if (c.kind == FlowTileKind.GUARD && c.exposed && !c.reserved) {
                    exposed += c
                }
            }
        }
        if (exposed.isEmpty()) return false

        val eliteSpecies = eliteSpeciesAvailable[(eliteJokerCharges - 1) % eliteSpeciesAvailable.size]
        val targets = exposed.take(2)
        eliteJokerCharges--
        deadlockGraceSec = 0f

        for ((idx, target) in targets.withIndex()) {
            val counterSpecies = target.guardType?.counterSpecies
            target.reserved = true
            if (counterSpecies != null) {
                decrementOneMatchingPod(counterSpecies)
            }
            val slotIdx = (slotCount / 2 + idx).coerceIn(0, slotCount - 1)
            val startX = slotCenterGridX(slotIdx)
            val startY = gridHeight + 0.55f
            val path = buildPathToTarget(target.pos)
            runningAliens += RunningAlien(
                id = "elite_${++alienIdSeq}",
                species = eliteSpecies,
                slotIndex = slotIdx,
                targetPos = target.pos,
                path = path,
                x = startX,
                y = startY,
                fightDurationSec = 0.22f
            )
        }
        recentEvents += FlowEventKind.ELITE_STRIKE
        updateSlotStallFlags()
        return true
    }

    private fun decrementOneMatchingPod(species: AlienSpecies) {
        // Zuerst aktive Slots abbauen (befreit blockierte Slots!), sonst vorderste Stapel-Kapsel
        for (i in slots.indices) {
            val s = slots[i] ?: continue
            if (s.species == species && s.remaining > 0) {
                s.remaining--
                if (s.remaining <= 0) {
                    slots[i] = null
                    recentEvents += FlowEventKind.SLOT_CLEARED
                }
                return
            }
        }
        for (col in columns) {
            for (idx in col.indices) {
                val p = col[idx]
                if (p.species == species && p.count > 0) {
                    if (p.count == 1) {
                        col.removeAt(idx)
                    } else {
                        col[idx] = p.copy(count = p.count - 1)
                    }
                    return
                }
            }
        }
    }

    /** 60-FPS-Echtzeit-Update (dtSec typischerweise ~0.016s). */
    fun update(dtSec: Float) {
        val dt = dtSec.coerceIn(0f, 0.05f)

        if (screenShakeSec > 0f) {
            screenShakeSec = (screenShakeSec - dt).coerceAtLeast(0f)
        }

        // Visuelle Kampf-Effekte & Treffer-Aufblitzen altern lassen
        val fxIt = visualFx.iterator()
        while (fxIt.hasNext()) {
            val fx = fxIt.next()
            fx.ageSec += dt
            if (fx.ageSec >= fx.durationSec) fxIt.remove()
        }
        for (x in 0 until gridWidth) {
            for (y in 0 until gridHeight) {
                val c = cells[x][y]
                if (c.hitFlashSec > 0f) c.hitFlashSec = (c.hitFlashSec - dt).coerceAtLeast(0f)
            }
        }

        if (outcome != FlowOutcome.PLAYING) return

        // 1. Aktive Slots updaten & Aliens losstürmen lassen
        for (i in slots.indices) {
            val slot = slots[i] ?: continue
            if (slot.slideAnim < 1f) {
                slot.slideAnim = (slot.slideAnim + dt * 6.5f).coerceAtMost(1f)
            }
            if (slot.remaining <= 0) {
                slots[i] = null
                recentEvents += FlowEventKind.SLOT_CLEARED
                continue
            }
            slot.spawnCooldownSec -= dt
            if (slot.spawnCooldownSec <= 0f) {
                val target = findBestExposedTarget(slot.species, slotCenterGridX(i))
                if (target != null) {
                    slot.stalled = false
                    slot.remaining--
                    slot.spawnCooldownSec = spawnIntervalSec
                    target.reserved = true

                    val startX = slotCenterGridX(i)
                    val startY = gridHeight + 0.55f
                    val path = buildPathToTarget(target.pos)
                    runningAliens += RunningAlien(
                        id = "a_${++alienIdSeq}",
                        species = slot.species,
                        slotIndex = i,
                        targetPos = target.pos,
                        path = path,
                        x = startX,
                        y = startY,
                        fightDurationSec = fightDurationSec
                    )
                    recentEvents += FlowEventKind.ALIEN_SPAWNED

                    if (slot.remaining <= 0) {
                        slots[i] = null
                        recentEvents += FlowEventKind.SLOT_CLEARED
                    }
                } else {
                    slot.stalled = true
                    slot.spawnCooldownSec = 0.08f
                }
            }
        }

        // 2. Laufende & kämpfende Aliens in Echtzeit bewegen
        val moveSpeed = 7.4f * speedMultiplier // Zellen pro Sekunde
        val alienIt = runningAliens.iterator()
        var boardChanged = false

        while (alienIt.hasNext()) {
            val alien = alienIt.next()
            when (alien.phase) {
                RunningAlienPhase.RUNNING -> {
                    var step = moveSpeed * dt
                    while (step > 0f && alien.pathIdx < alien.path.size) {
                        val (wx, wy) = alien.path[alien.pathIdx]
                        val dx = wx - alien.x
                        val dy = wy - alien.y
                        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        if (dist <= 0.001f) {
                            alien.pathIdx++
                        } else if (dist <= step) {
                            alien.facingX = dx / dist
                            alien.facingY = dy / dist
                            alien.x = wx
                            alien.y = wy
                            step -= dist
                            alien.pathIdx++
                        } else {
                            alien.facingX = dx / dist
                            alien.facingY = dy / dist
                            alien.x += (dx / dist) * step
                            alien.y += (dy / dist) * step
                            step = 0f
                        }
                    }

                    if (alien.pathIdx >= alien.path.size) {
                        alien.phase = RunningAlienPhase.FIGHTING
                        alien.fightTimerSec = 0f
                        emitSkirmishStartFx(alien)
                    }
                }

                RunningAlienPhase.FIGHTING -> {
                    alien.fightTimerSec += dt
                    val targetCell = cells[alien.targetPos.x][alien.targetPos.y]
                    targetCell.hitFlashSec = 0.12f
                    if (alien.fightTimerSec >= alien.fightDurationSec) {
                        resolveGuardElimination(alien, targetCell)
                        alienIt.remove()
                        boardChanged = true
                    }
                }
            }
        }

        if (boardChanged) {
            recomputeExposedGuards()
            updateSlotStallFlags()
        }

        // 3. Sieg- & Blockade-Prüfung
        if (remainingGuards <= 0 && runningAliens.isEmpty()) {
            outcome = FlowOutcome.VICTORY
            recentEvents += FlowEventKind.VICTORY
            return
        }

        val allSlotsFull = slots.all { it != null }
        val anyCanAct = slots.any { it != null && hasExposedTargetFor(it.species) }
        if (allSlotsFull && runningAliens.isEmpty() && !anyCanAct) {
            deadlockGraceSec += dt
            if (deadlockGraceSec >= 0.65f) {
                outcome = FlowOutcome.DEADLOCK
                recentEvents += FlowEventKind.DEADLOCK
            }
        } else {
            deadlockGraceSec = 0f
        }
    }

    private fun emitSkirmishStartFx(alien: RunningAlien) {
        val tx = alien.targetPos.x + 0.5f
        val ty = alien.targetPos.y + 0.5f
        val targetCell = cells[alien.targetPos.x][alien.targetPos.y]
        val guardLaserKind = when (targetCell.guardType) {
            FlowGuardType.PYRO -> EffectKind.FLAME
            else -> EffectKind.LASER
        }
        // Kurzes Gegenfeuer der Wache -> lebendiges Echtzeit-Gefecht!
        visualFx += FlowVisualFx(
            fromX = tx,
            fromY = ty,
            toX = alien.x,
            toY = alien.y,
            kind = guardLaserKind,
            colorHex = if (guardLaserKind == EffectKind.FLAME) 0xFFFF8822 else 0xFFFF3B52,
            durationSec = 0.22f
        )
        val alienFxKind = when (alien.species) {
            AlienSpecies.SPITTER, AlienSpecies.BOMBER -> EffectKind.ACID
            AlienSpecies.PSION -> EffectKind.PSIONIC
            else -> EffectKind.CLAW
        }
        visualFx += FlowVisualFx(
            fromX = alien.x,
            fromY = alien.y,
            toX = tx,
            toY = ty,
            kind = alienFxKind,
            colorHex = alien.species.accentColorHex,
            durationSec = 0.30f
        )
        recentEvents += FlowEventKind.COMBAT_HIT
    }

    private fun resolveGuardElimination(alien: RunningAlien, targetCell: FlowCellState) {
        if (targetCell.kind == FlowTileKind.GUARD) {
            targetCell.kind = FlowTileKind.CREEP
            targetCell.guardType = null
            targetCell.reserved = false
            targetCell.exposed = false
            remainingGuards = (remainingGuards - 1).coerceAtLeast(0)
            recentEvents += FlowEventKind.GUARD_KILLED

            val tx = targetCell.pos.x + 0.5f
            val ty = targetCell.pos.y + 0.5f
            visualFx += FlowVisualFx(
                fromX = tx,
                fromY = ty,
                toX = tx,
                toY = ty,
                kind = if (alien.species == AlienSpecies.BOMBER) EffectKind.EXPLOSION else EffectKind.CLAW,
                colorHex = alien.species.accentColorHex,
                durationSec = 0.34f
            )

            // Angrenzende Säurefässer sprengen direkt danebenliegende Schotts/Barrieren frei!
            detonateAdjacentBarrels(targetCell.pos)
        }
    }

    private fun detonateAdjacentBarrels(pos: GridPos) {
        val toBlast = ArrayDeque<GridPos>()
        for (dir in Direction.entries) {
            val adj = pos.move(dir)
            if (inBounds(adj) && cells[adj.x][adj.y].kind == FlowTileKind.BARREL) {
                toBlast.addLast(adj)
            }
        }
        while (toBlast.isNotEmpty()) {
            val bPos = toBlast.removeFirst()
            val bCell = cells[bPos.x][bPos.y]
            if (bCell.kind != FlowTileKind.BARREL) continue
            bCell.kind = FlowTileKind.CREEP
            screenShakeSec = 0.25f
            recentEvents += FlowEventKind.BARREL_BLAST
            visualFx += FlowVisualFx(
                fromX = bPos.x + 0.5f,
                fromY = bPos.y + 0.5f,
                toX = bPos.x + 0.5f,
                toY = bPos.y + 0.5f,
                kind = EffectKind.EXPLOSION,
                colorHex = 0xFFCCFF33,
                durationSec = 0.48f
            )
            // Sprengt alle angrenzenden Schott-Barrieren (und löst Ketten-Fässer aus!)
            for (dx in -1..1) {
                for (dy in -1..1) {
                    val np = GridPos(bPos.x + dx, bPos.y + dy)
                    if (!inBounds(np)) continue
                    val nc = cells[np.x][np.y]
                    when (nc.kind) {
                        FlowTileKind.BARRIER -> nc.kind = FlowTileKind.CREEP
                        FlowTileKind.BARREL -> toBlast.addLast(np)
                        else -> Unit
                    }
                }
            }
        }
    }

    /**
     * Berechnet per BFS von allen offenen Zugängen der Unterkante (und freien Korridoren) aus,
     * welche lebenden Wachen aktuell an der offenen Front stehen und angegriffen werden können.
     */
    fun recomputeExposedGuards() {
        for (x in 0 until gridWidth) {
            for (y in 0 until gridHeight) {
                cells[x][y].exposed = false
            }
        }

        val visited = Array(gridWidth) { BooleanArray(gridHeight) }
        val queue = ArrayDeque<GridPos>()

        // Alle offenen Boden-/Creep-Felder und direkt an der Unterkante stehenden Wachen sind Eintrittspunkte
        for (x in 0 until gridWidth) {
            val bottom = cells[x][gridHeight - 1]
            when (bottom.kind) {
                FlowTileKind.FLOOR, FlowTileKind.CREEP -> {
                    visited[x][gridHeight - 1] = true
                    queue.addLast(bottom.pos)
                }
                FlowTileKind.GUARD -> {
                    bottom.exposed = true
                }
                else -> Unit
            }
        }

        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (dir in Direction.entries) {
                val next = cur.move(dir)
                if (!inBounds(next) || visited[next.x][next.y]) continue
                val c = cells[next.x][next.y]
                when (c.kind) {
                    FlowTileKind.FLOOR, FlowTileKind.CREEP -> {
                        visited[next.x][next.y] = true
                        queue.addLast(next)
                    }
                    FlowTileKind.GUARD -> {
                        c.exposed = true
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun updateSlotStallFlags() {
        for (slot in slots) {
            if (slot != null) {
                slot.stalled = slot.remaining > 0 && !hasExposedTargetFor(slot.species)
            }
        }
    }

    private fun findBestExposedTarget(species: AlienSpecies, preferredX: Float): FlowCellState? {
        var best: FlowCellState? = null
        var bestScore = Float.MAX_VALUE
        for (y in gridHeight - 1 downTo 0) {
            for (x in 0 until gridWidth) {
                val c = cells[x][y]
                if (c.kind == FlowTileKind.GUARD && c.exposed && !c.reserved &&
                    c.guardType?.counterSpecies == species
                ) {
                    // Bevorzuge vordere (untere) Wachen nah an der eigenen Slot-Spalte -> sehr natürlicher Schwarmfluss!
                    val distY = (gridHeight - 1 - y) * 1.4f
                    val distX = kotlin.math.abs((x + 0.5f) - preferredX)
                    val score = distY + distX
                    if (score < bestScore) {
                        bestScore = score
                        best = c
                    }
                }
            }
        }
        return best
    }

    /** Berechnet einen flüssigen 2D-Wegpunkt-Pfad vom Slot durch freie Korridore bis vor die Ziel-Wache. */
    private fun buildPathToTarget(target: GridPos): List<Pair<Float, Float>> {
        // Wenn die Wache direkt an der Unterkante steht, sprintet das Alien direkt hoch!
        if (target.y == gridHeight - 1) {
            return listOf(
                (target.x + 0.5f) to (gridHeight + 0.12f),
                (target.x + 0.5f) to (target.y + 0.85f)
            )
        }

        // BFS rückwärts von offenen Feldern neben [target] bis zu offenen Feldern an der Unterkante
        val parent = HashMap<GridPos, GridPos>(64)
        val queue = ArrayDeque<GridPos>()
        for (x in 0 until gridWidth) {
            val c = cells[x][gridHeight - 1]
            if (c.kind == FlowTileKind.FLOOR || c.kind == FlowTileKind.CREEP) {
                parent[c.pos] = c.pos
                queue.addLast(c.pos)
            }
        }

        var standTile: GridPos? = null
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (cur.isAdjacentTo(target)) {
                standTile = cur
                break
            }
            for (dir in Direction.entries) {
                val next = cur.move(dir)
                if (!inBounds(next) || next in parent) continue
                val nc = cells[next.x][next.y]
                if (nc.kind == FlowTileKind.FLOOR || nc.kind == FlowTileKind.CREEP) {
                    parent[next] = cur
                    queue.addLast(next)
                }
            }
        }

        var cur: GridPos = standTile ?: return listOf((target.x + 0.5f) to (target.y + 0.72f))
        val gridPath = mutableListOf<GridPos>()
        while (true) {
            gridPath += cur
            val prev = parent[cur] ?: break
            if (prev == cur) break
            cur = prev
        }
        gridPath.reverse()

        val waypoints = mutableListOf<Pair<Float, Float>>()
        val entry = gridPath.first()
        waypoints += (entry.x + 0.5f) to (gridHeight + 0.10f)
        for (p in gridPath) {
            waypoints += (p.x + 0.5f) to (p.y + 0.5f)
        }
        // Letztes Stück leicht an die Ziel-Wache heranrücken (für packende Nahkampf-Optik!)
        val last = gridPath.last()
        val approachX = last.x + 0.5f + (target.x - last.x) * 0.42f
        val approachY = last.y + 0.5f + (target.y - last.y) * 0.42f
        waypoints += approachX to approachY
        return waypoints
    }

    fun slotCenterGridX(slotIndex: Int): Float =
        ((slotIndex + 0.5f) / slotCount.toFloat()) * gridWidth.toFloat()

    fun findLinkedFrontColumn(colIndex: Int, groupId: Int): Int? {
        if (groupId <= 0) return null
        for (c in columns.indices) {
            if (c == colIndex) continue
            val front = columns[c].firstOrNull()
            if (front != null && front.linkedGroupId == groupId) return c
        }
        return null
    }

    private fun inBounds(pos: GridPos): Boolean =
        pos.x in 0 until gridWidth && pos.y in 0 until gridHeight
}

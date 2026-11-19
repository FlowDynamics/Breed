package com.reeware.android.breed.render

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.reeware.android.breed.engine.*
import kotlin.math.*

/**
 * Hardware-beschleunigtes Hochformat-Spielfeld für BREED:
 * - 60-FPS-Atmosphäre (treibende Staub-/Sporen-Partikel, Taschenlampen-/Sensor-Kegel,
 *   flackernde Notbeleuchtung, dunkle Raumschiff-Vignette, Motion-Tracker-Ping)
 * - Phase 1: Interaktive Ziel-Markierungen (Nachbarfelder, Vent-Ziel, Konsole) für direkte Touch-Züge
 * - Phase 2: Flüssige Interpolation von Einheiten-Bewegungen, animierte Projektile/Explosionen,
 *   Todes-Effekte und dezenter Screen-Shake bei schweren Treffern.
 */
class BattleBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val theme = BiomechTheme()
    private val sprites = SpritePainter()

    private var level: LevelDefinition? = null
    private var placements: List<AlienPlacement> = emptyList()
    private var selectedPlacementId: String? = null
    private var highlightDeployable: Boolean = false
    private var commandModeActive: Boolean = false
    private var remainingCommands: Int = 0

    // Phase 2 Animations- & Snapshot-Zustand
    private var phase2Snapshot: TurnSnapshot? = null
    private var prevPhase2Snapshot: TurnSnapshot? = null
    private var animStartMs: Long = 0L
    private var animDurationMs: Long = 650L
    private var hasHeavyImpact: Boolean = false
    private var motionPingStartMs: Long = 0L

    // Vorallokierte Caches (vermeidet Allokationen in onDraw)
    private var phase1HumansCache: List<HumanRuntimeState> = emptyList()
    private val cellLookup = HashMap<GridPos, TileCell>(128)
    private val prevAlienLookup = HashMap<String, AlienRuntimeState>(24)
    private val prevHumanLookup = HashMap<String, HumanRuntimeState>(24)
    private val currAlienIds = HashSet<String>(24)
    private val currHumanIds = HashSet<String>(24)

    private var cellSize = 64f
    private var boardLeft = 0f
    private var boardTop = 0f
    private val tileRect = RectF()
    private val interpRect = RectF()
    private val hintRect = RectF()
    private val conePath = Path()
    private var vignetteShader: Shader? = null

    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val effectPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val conePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val scanlinePaint = Paint().apply {
        color = Color.argb(18, 0, 0, 0)
        strokeWidth = 1f
    }

    // Deterministische Staub-/Sporen-Partikel über dem Deck
    private val dustCount = 22
    private val dustX = FloatArray(dustCount)
    private val dustY = FloatArray(dustCount)
    private val dustSpeedX = FloatArray(dustCount)
    private val dustSpeedY = FloatArray(dustCount)
    private val dustRadius = FloatArray(dustCount)

    private var isAttached = false

    var onCellTap: ((GridPos) -> Unit)? = null

    init {
        for (i in 0 until dustCount) {
            dustX[i] = ((i * 73 + 17) % 100) / 100f
            dustY[i] = ((i * 41 + 31) % 100) / 100f
            dustSpeedX[i] = 0.004f + (i % 5) * 0.0018f
            dustSpeedY[i] = 0.002f + (i % 4) * 0.0015f
            dustRadius[i] = 1.2f + (i % 3) * 0.9f
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAttached = true
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        isAttached = false
        super.onDetachedFromWindow()
    }

    fun triggerMotionPing() {
        motionPingStartMs = SystemClock.uptimeMillis()
        invalidate()
    }

    fun setPhase1State(
        level: LevelDefinition,
        placements: List<AlienPlacement>,
        selectedPlacementId: String?,
        highlightDeployable: Boolean,
        commandModeActive: Boolean = false,
        remainingCommands: Int = 0
    ) {
        if (this.level != level) {
            rebuildPhase1HumansCache(level)
        }
        this.level = level
        this.placements = placements
        this.selectedPlacementId = selectedPlacementId
        this.highlightDeployable = highlightDeployable
        this.commandModeActive = commandModeActive
        this.remainingCommands = remainingCommands
        this.phase2Snapshot = null
        this.prevPhase2Snapshot = null
        rebuildCellLookup(level.cells)
        recomputeMetrics()
        invalidate()
    }

    fun setPhase2Snapshot(level: LevelDefinition, snapshot: TurnSnapshot) {
        setPhase2Transition(level, null, snapshot, 1L)
    }

    fun setPhase2Transition(
        level: LevelDefinition,
        prevSnapshot: TurnSnapshot?,
        snapshot: TurnSnapshot,
        durationMs: Long
    ) {
        if (this.level != level) {
            rebuildPhase1HumansCache(level)
        }
        this.level = level
        this.prevPhase2Snapshot = prevSnapshot
        this.phase2Snapshot = snapshot
        this.highlightDeployable = false
        this.commandModeActive = false
        this.animStartMs = SystemClock.uptimeMillis()
        this.animDurationMs = durationMs.coerceAtLeast(1L)

        rebuildCellLookup(snapshot.cells)

        prevAlienLookup.clear()
        prevSnapshot?.aliens?.forEach { prevAlienLookup[it.id] = it }
        prevHumanLookup.clear()
        prevSnapshot?.humans?.forEach { prevHumanLookup[it.id] = it }

        currAlienIds.clear()
        snapshot.aliens.forEach { if (it.isAlive) currAlienIds.add(it.id) }
        currHumanIds.clear()
        snapshot.humans.forEach { if (it.isAlive) currHumanIds.add(it.id) }

        val anyKill = (prevSnapshot?.aliens?.any { it.isAlive && it.id !in currAlienIds } == true) ||
            (prevSnapshot?.humans?.any { it.isAlive && it.id !in currHumanIds } == true)
        hasHeavyImpact = anyKill || snapshot.effects.any {
            it.kind == EffectKind.EXPLOSION || it.kind == EffectKind.FLAME
        }

        recomputeMetrics()
        invalidate()
    }

    private fun rebuildPhase1HumansCache(level: LevelDefinition) {
        phase1HumansCache = level.humanSpawns.map { h ->
            HumanRuntimeState(
                id = h.id,
                type = h.type,
                pos = h.spawn,
                hp = h.type.maxHp,
                maxHp = h.type.maxHp,
                armor = h.type.baseArmor,
                facing = h.facing
            )
        }
    }

    private fun rebuildCellLookup(cells: List<TileCell>) {
        cellLookup.clear()
        for (c in cells) {
            cellLookup[c.pos] = c
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeMetrics()
        if (w > 0 && h > 0) {
            vignetteShader = RadialGradient(
                w * 0.5f, h * 0.5f, maxOf(w, h) * 0.72f,
                intArrayOf(Color.TRANSPARENT, Color.argb(45, 0, 0, 0), Color.argb(175, 0, 0, 0)),
                floatArrayOf(0.52f, 0.80f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
    }

    private fun recomputeMetrics() {
        val lvl = level ?: return
        if (width <= 0 || height <= 0) return
        val pad = 14f
        val availW = (width - pad * 2f).coerceAtLeast(100f)
        val availH = (height - pad * 2f).coerceAtLeast(100f)
        cellSize = minOf(availW / lvl.width, availH / lvl.height)
        val totalW = cellSize * lvl.width
        val totalH = cellSize * lvl.height
        boardLeft = (width - totalW) * 0.5f
        boardTop = (height - totalH) * 0.5f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(theme.voidBg)

        val lvl = level ?: return
        val nowMs = SystemClock.uptimeMillis()
        val timeSec = nowMs / 1000f
        val pulsePhase = timeSec * 1.4f

        val snap = phase2Snapshot
        val cells = snap?.cells ?: lvl.cells
        val isNight = snap?.isNight ?: lvl.dayNight.isNightAtRound(0)

        val animT = if (snap != null && prevPhase2Snapshot != null) {
            ((nowMs - animStartMs).toFloat() / animDurationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            1f
        }

        // Dezenter Screen-Shake bei Explosionen & Eliminierungen
        var shakeX = 0f
        var shakeY = 0f
        if (snap != null && hasHeavyImpact && animT in 0.40f..0.85f) {
            val strength = (1f - (animT - 0.40f) / 0.45f) * cellSize * 0.11f
            shakeX = sin(timeSec * 85f) * strength
            shakeY = cos(timeSec * 73f) * strength
        }

        canvas.save()
        canvas.translate(shakeX, shakeY)

        // 1. Kacheln & Planeten-Bullaugen zeichnen
        for (i in cells.indices) {
            val cell = cells[i]
            cellToRect(cell.pos, tileRect)
            val isFreeStart = highlightDeployable && !hasPlacementAt(cell.pos)
            theme.drawTile(
                canvas = canvas,
                cell = cell,
                rect = tileRect,
                planetTheme = lvl.planetTheme,
                isNight = isNight,
                pulsePhase = pulsePhase,
                highlightDeployable = isFreeStart
            )
        }

        // 2. Marschrouten (mit laufendem Richtungs-Impuls), Geschütz-Korridore & Taschenlampen-Kegel
        drawHumanRoutesAndSightCones(canvas, lvl, snap, isNight, timeSec)

        if (snap == null) {
            // Phase 1: Interaktive Touch-Zielfelder + vorgeplante Befehls-Pfade + Einheiten
            if (commandModeActive && selectedPlacementId != null && remainingCommands > 0) {
                drawInteractiveCommandHints(canvas, lvl, pulsePhase)
            }
            drawPlannedCommands(canvas, lvl)

            for (i in placements.indices) {
                val p = placements[i]
                cellToRect(p.startPos, tileRect)
                val hp = (p.species.baseHp + p.stage.hpBonus).coerceAtLeast(2)
                sprites.drawAlien(
                    canvas = canvas,
                    cellRect = tileRect,
                    species = p.species,
                    stage = p.stage,
                    hp = hp,
                    maxHp = hp,
                    hatchCountdown = p.stage.hatchDelayRounds,
                    isSelected = p.id == selectedPlacementId,
                    pulsePhase = pulsePhase
                )
            }
            for (i in phase1HumansCache.indices) {
                val human = phase1HumansCache[i]
                cellToRect(human.pos, tileRect)
                sprites.drawHuman(canvas, tileRect, human)
            }
        } else {
            // Phase 2: Flüssige Bewegung (0..0.46) + Kampf-Effekte & Todes-Ringe (0.40..1.0)
            val moveT = smoothstep((animT / 0.46f).coerceIn(0f, 1f))
            val effectT = ((animT - 0.36f) / 0.64f).coerceIn(0f, 1f)

            // Verblassende Todes-Effekte für gerade eliminierte Einheiten
            if (prevPhase2Snapshot != null && effectT > 0f) {
                drawFallenUnitEffects(canvas, effectT)
            }

            // Aliens interpoliert zeichnen
            for (i in snap.aliens.indices) {
                val alien = snap.aliens[i]
                if (!alien.isAlive) continue
                val prev = prevAlienLookup[alien.id]
                val drawX: Float
                val drawY: Float
                if (prev != null && prev.pos != alien.pos && prev.pos.manhattanTo(alien.pos) <= 2) {
                    drawX = lerp(prev.pos.x.toFloat(), alien.pos.x.toFloat(), moveT)
                    drawY = lerp(prev.pos.y.toFloat(), alien.pos.y.toFloat(), moveT)
                } else {
                    drawX = alien.pos.x.toFloat()
                    drawY = alien.pos.y.toFloat()
                }
                gridFloatToRect(drawX, drawY, interpRect)
                sprites.drawAlien(
                    canvas = canvas,
                    cellRect = interpRect,
                    species = alien.species,
                    stage = alien.stage,
                    hp = alien.hp,
                    maxHp = alien.maxHp,
                    hatchCountdown = alien.hatchCountdown,
                    pulsePhase = pulsePhase
                )
            }

            // Menschen interpoliert zeichnen
            for (i in snap.humans.indices) {
                val human = snap.humans[i]
                if (!human.isAlive) continue
                val prev = prevHumanLookup[human.id]
                val drawX: Float
                val drawY: Float
                if (prev != null && prev.pos != human.pos) {
                    drawX = lerp(prev.pos.x.toFloat(), human.pos.x.toFloat(), moveT)
                    drawY = lerp(prev.pos.y.toFloat(), human.pos.y.toFloat(), moveT)
                } else {
                    drawX = human.pos.x.toFloat()
                    drawY = human.pos.y.toFloat()
                }
                gridFloatToRect(drawX, drawY, interpRect)
                sprites.drawHuman(canvas, interpRect, human)
            }

            // Animierte Kampf-Effekte
            if (effectT > 0f) {
                drawAnimatedCombatEffects(canvas, snap.effects, effectT)
            }
        }

        // 3. Düstere Raumschiff-Atmosphäre: Staub-/Sporen-Partikel, flackerndes Notlicht, Motion-Tracker-Ping
        drawAtmosphericParticles(canvas, lvl, isNight, timeSec)
        drawEmergencyFlicker(canvas, lvl, isNight, timeSec)
        drawMotionTrackerPing(canvas, nowMs)

        // Äußerer metallischer StarCraft-/Nostromo-Rahmen
        theme.strokePaint.color = when {
            snap != null && snap.outcome == BattleOutcome.VICTORY -> Color.parseColor("#39FF88")
            snap != null && snap.outcome != BattleOutcome.IN_PROGRESS -> Color.parseColor("#FF4252")
            isNight -> Color.parseColor("#2A4478")
            else -> theme.hudBorder
        }
        theme.strokePaint.strokeWidth = 3f
        canvas.drawRect(
            boardLeft - 2f,
            boardTop - 2f,
            boardLeft + lvl.width * cellSize + 2f,
            boardTop + lvl.height * cellSize + 2f,
            theme.strokePaint
        )

        canvas.restore()

        // 4. Dezente Scanlines & dunkle Vignette über das gesamte Spielfeld
        var sy = 0f
        while (sy < height) {
            canvas.drawLine(0f, sy, width.toFloat(), sy, scanlinePaint)
            sy += 6f
        }
        vignetteShader?.let { shader ->
            effectPaint.style = Paint.Style.FILL
            effectPaint.shader = shader
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), effectPaint)
            effectPaint.shader = null
        }

        if (isAttached && visibility == VISIBLE) {
            postInvalidateOnAnimation()
        }
    }

    private fun hasPlacementAt(pos: GridPos): Boolean {
        for (i in placements.indices) {
            if (placements[i].startPos == pos) return true
        }
        return false
    }

    private fun drawHumanRoutesAndSightCones(
        canvas: Canvas,
        lvl: LevelDefinition,
        snap: TurnSnapshot?,
        isNight: Boolean,
        timeSec: Float
    ) {
        // Laufende Marschrouten der mobilen Menschen
        routePaint.color = Color.argb(115, 255, 165, 50)
        routePaint.strokeWidth = cellSize * 0.075f
        for (i in lvl.humanSpawns.indices) {
            val h = lvl.humanSpawns[i]
            if (h.route.size < 2) continue
            for (k in 0 until h.route.size - 1) {
                val p1 = h.route[k]
                val p2 = h.route[k + 1]
                canvas.drawLine(centerX(p1), centerY(p1), centerX(p2), centerY(p2), routePaint)
            }
            // Wandernder Leuchtpunkt entlang der Route zeigt sofort die Marschrichtung!
            val segCount = h.route.size - 1
            val prog = (timeSec * 0.85f) % segCount
            val segIdx = prog.toInt().coerceIn(0, segCount - 1)
            val localT = prog - segIdx
            val a = h.route[segIdx]
            val b = h.route[segIdx + 1]
            val px = lerp(centerX(a), centerX(b), localT)
            val py = lerp(centerY(a), centerY(b), localT)
            effectPaint.style = Paint.Style.FILL
            effectPaint.color = Color.argb(210, 255, 196, 84)
            canvas.drawCircle(px, py, cellSize * 0.08f, effectPaint)
        }

        // Dynamische Taschenlampen- & Geschütz-Feuerkorridore
        val humans = snap?.humans ?: phase1HumansCache
        for (i in humans.indices) {
            val h = humans[i]
            if (!h.isAlive) continue
            if (h.type == HumanType.TURRET && !h.powered) continue

            val maxDist = if (isNight) h.type.nightRange else h.type.dayRange
            var cur = h.pos
            var visibleSteps = 0
            while (visibleSteps < maxDist) {
                val next = cur.move(h.facing)
                val c = cellLookup[next] ?: break
                if (c.type.blocksLineOfSight(c.doorOpen)) break
                cur = next
                visibleSteps++
                if (h.type == HumanType.TURRET) {
                    cellToRect(cur, tileRect)
                    effectPaint.style = Paint.Style.FILL
                    val pulse = 36 + (sin(timeSec * 6f) * 14f).toInt()
                    effectPaint.color = Color.argb(pulse.coerceIn(20, 62), 255, 35, 60)
                    canvas.drawRect(tileRect, effectPaint)
                }
            }

            // Atmosphärischer Taschenlampen-/Sensor-Kegel in Blickrichtung
            if (visibleSteps > 0) {
                val sx = centerX(h.pos)
                val sy = centerY(h.pos)
                val reach = cellSize * (visibleSteps + 0.35f)
                val halfSpread = cellSize * (0.28f + visibleSteps * 0.14f)
                val dx = h.facing.dx.toFloat()
                val dy = h.facing.dy.toFloat()
                val px = -dy
                val py = dx

                conePath.reset()
                conePath.moveTo(sx, sy)
                conePath.lineTo(sx + dx * reach + px * halfSpread, sy + dy * reach + py * halfSpread)
                conePath.lineTo(sx + dx * reach - px * halfSpread, sy + dy * reach - py * halfSpread)
                conePath.close()

                conePaint.color = when (h.type) {
                    HumanType.TURRET -> Color.argb(38, 255, 50, 75)
                    HumanType.PYRO -> Color.argb(if (isNight) 44 else 28, 255, 130, 45)
                    HumanType.TANK -> Color.argb(if (isNight) 42 else 26, 255, 95, 95)
                    HumanType.MARINE -> Color.argb(if (isNight) 52 else 24, 175, 220, 255)
                }
                canvas.drawPath(conePath, conePaint)
            }
        }
    }

    /** Zeigt beim ausgewählten Alien direkt auf dem Raster antippbare Zielfelder (Schach-Steuerung). */
    private fun drawInteractiveCommandHints(canvas: Canvas, lvl: LevelDefinition, pulsePhase: Float) {
        var selected: AlienPlacement? = null
        for (i in placements.indices) {
            if (placements[i].id == selectedPlacementId) {
                selected = placements[i]
                break
            }
        }
        val unit = selected ?: return
        val endPos = CommandPlanner.projectedEndPosition(lvl, unit)
        val alpha = (115 + (sin(pulsePhase * 5.2f) * 65f).toInt()).coerceIn(60, 210)

        // 1. Begehbare Nachbarfelder für 1-Tipp-Bewegung
        for (dir in Direction.entries) {
            val next = endPos.move(dir)
            val c = cellLookup[next] ?: continue
            if (c.type == TileType.WALL || c.type == TileType.WINDOW || c.type == TileType.EXIT) continue

            cellToRect(next, hintRect)
            hintRect.inset(cellSize * 0.11f, cellSize * 0.11f)
            effectPaint.style = Paint.Style.STROKE
            effectPaint.strokeWidth = cellSize * 0.045f
            effectPaint.color = Color.argb(alpha, 59, 232, 255)
            canvas.drawRoundRect(hintRect, cellSize * 0.12f, cellSize * 0.12f, effectPaint)

            badgeText.color = Color.argb(alpha, 135, 244, 255)
            badgeText.textSize = cellSize * 0.24f
            val glyph = when (dir) {
                Direction.NORTH -> "▲"
                Direction.SOUTH -> "▼"
                Direction.WEST -> "◄"
                Direction.EAST -> "►"
            }
            canvas.drawText(glyph, hintRect.centerX(), hintRect.centerY() + badgeText.textSize * 0.35f, badgeText)
        }

        // 2. Verknüpftes Vent-Ziel hervorheben
        if (unit.species.canUseVent) {
            val linkedVent = CommandPlanner.findLinkedVent(lvl, endPos)
            if (linkedVent != null) {
                cellToRect(linkedVent, hintRect)
                hintRect.inset(cellSize * 0.07f, cellSize * 0.07f)
                effectPaint.style = Paint.Style.STROKE
                effectPaint.strokeWidth = cellSize * 0.07f
                effectPaint.color = Color.argb(235, 51, 230, 209)
                canvas.drawRoundRect(hintRect, cellSize * 0.16f, cellSize * 0.16f, effectPaint)
            }
        }

        // 3. Erreichbare Konsole hervorheben
        for (dir in Direction.entries) {
            val adj = endPos.move(dir)
            if (cellLookup[adj]?.type == TileType.CONSOLE) {
                cellToRect(adj, hintRect)
                hintRect.inset(cellSize * 0.06f, cellSize * 0.06f)
                effectPaint.style = Paint.Style.STROKE
                effectPaint.strokeWidth = cellSize * 0.07f
                effectPaint.color = Color.argb(235, 57, 255, 136)
                canvas.drawRoundRect(hintRect, cellSize * 0.16f, cellSize * 0.16f, effectPaint)
            }
        }
    }

    private fun drawPlannedCommands(canvas: Canvas, lvl: LevelDefinition) {
        for (i in placements.indices) {
            val p = placements[i]
            val isSel = p.id == selectedPlacementId
            val color = if (isSel) Color.parseColor("#3BE8FF") else Color.argb(170, 175, 130, 255)
            arrowPaint.color = color
            arrowPaint.strokeWidth = if (isSel) cellSize * 0.085f else cellSize * 0.055f

            var cur = p.startPos
            for (idx in p.commands.indices) {
                val cmd = p.commands[idx]
                when (cmd) {
                    is AlienCommand.Move -> {
                        val next = cur.move(cmd.dir)
                        val c = lvl.cellAt(next)
                        if (c != null && c.type != TileType.WALL) {
                            canvas.drawLine(centerX(cur), centerY(cur), centerX(next), centerY(next), arrowPaint)
                            cur = next
                        }
                    }
                    is AlienCommand.VentJump -> {
                        routePaint.color = Color.parseColor("#3BE8FF")
                        routePaint.strokeWidth = cellSize * 0.07f
                        canvas.drawLine(centerX(cur), centerY(cur), centerX(cmd.target), centerY(cmd.target), routePaint)
                        cur = cmd.target
                    }
                    AlienCommand.ToggleConsole, AlienCommand.Wait, AlienCommand.HatchNow -> Unit
                }
                badgeText.textSize = cellSize * 0.22f
                badgeText.color = Color.WHITE
                effectPaint.style = Paint.Style.FILL
                effectPaint.color = color
                val bx = centerX(cur) + cellSize * 0.22f
                val by = centerY(cur) - cellSize * 0.20f
                canvas.drawCircle(bx, by, cellSize * 0.14f, effectPaint)
                canvas.drawText("${idx + 1}", bx, by + badgeText.textSize * 0.34f, badgeText)
            }
        }
    }

    private fun drawFallenUnitEffects(canvas: Canvas, effectT: Float) {
        val prev = prevPhase2Snapshot ?: return
        val fade = (1f - effectT).coerceIn(0f, 1f)
        if (fade <= 0.02f) return

        for (i in prev.humans.indices) {
            val h = prev.humans[i]
            if (h.isAlive && h.id !in currHumanIds) {
                val cx = centerX(h.pos)
                val cy = centerY(h.pos)
                effectPaint.style = Paint.Style.STROKE
                effectPaint.strokeWidth = cellSize * 0.06f
                effectPaint.color = Color.argb((fade * 220f).toInt(), 255, 70, 75)
                canvas.drawCircle(cx, cy, cellSize * (0.22f + effectT * 0.42f), effectPaint)
            }
        }
        for (i in prev.aliens.indices) {
            val a = prev.aliens[i]
            if (a.isAlive && a.id !in currAlienIds) {
                val cx = centerX(a.pos)
                val cy = centerY(a.pos)
                effectPaint.style = Paint.Style.FILL
                effectPaint.color = Color.argb((fade * 145f).toInt(), 90, 255, 95)
                canvas.drawCircle(cx, cy, cellSize * (0.18f + effectT * 0.28f), effectPaint)
            }
        }
    }

    private fun drawAnimatedCombatEffects(canvas: Canvas, effects: List<CombatEffect>, t: Float) {
        val fade = (1f - t * 0.85f).coerceIn(0.15f, 1f)
        val shotT = (t / 0.55f).coerceIn(0f, 1f)

        for (i in effects.indices) {
            val ef = effects[i]
            val x1 = centerX(ef.from)
            val y1 = centerY(ef.from)
            val x2 = centerX(ef.to)
            val y2 = centerY(ef.to)

            val color = when (ef.kind) {
                EffectKind.LASER -> Color.parseColor("#FF3B52")
                EffectKind.FLAME -> Color.parseColor("#FF8822")
                EffectKind.ACID -> Color.parseColor("#66FF66")
                EffectKind.PSIONIC -> Color.parseColor("#FF4D9E")
                EffectKind.CONSOLE -> Color.parseColor("#3BE8FF")
                EffectKind.EXPLOSION -> Color.parseColor("#CCFF33")
                EffectKind.VENT -> Color.parseColor("#33E6D1")
                EffectKind.HATCH -> Color.parseColor("#C77DFF")
                EffectKind.CLAW -> Color.WHITE
            }

            when (ef.kind) {
                EffectKind.LASER -> {
                    val bx = lerp(x1, x2, shotT)
                    val by = lerp(y1, y2, shotT)
                    effectPaint.style = Paint.Style.STROKE
                    effectPaint.color = Color.argb((fade * 200f).toInt(), 255, 60, 82)
                    effectPaint.strokeWidth = cellSize * 0.06f
                    canvas.drawLine(x1, y1, bx, by, effectPaint)
                    // Leuchtender Projektilkopf
                    effectPaint.style = Paint.Style.FILL
                    effectPaint.color = Color.argb((fade * 255f).toInt(), 255, 220, 225)
                    canvas.drawCircle(bx, by, cellSize * 0.08f, effectPaint)
                }
                EffectKind.FLAME -> {
                    val fx = lerp(x1, x2, shotT)
                    val fy = lerp(y1, y2, shotT)
                    effectPaint.style = Paint.Style.STROKE
                    effectPaint.color = Color.argb((fade * 210f).toInt(), 255, 110, 25)
                    effectPaint.strokeWidth = cellSize * (0.14f + t * 0.08f)
                    canvas.drawLine(x1, y1, fx, fy, effectPaint)
                    effectPaint.style = Paint.Style.FILL
                    effectPaint.color = Color.argb((fade * 195f).toInt(), 255, 210, 75)
                    canvas.drawCircle(fx, fy, cellSize * (0.14f + t * 0.16f), effectPaint)
                }
                EffectKind.ACID -> {
                    val ax = lerp(x1, x2, shotT)
                    val ay = lerp(y1, y2, shotT)
                    effectPaint.style = Paint.Style.FILL
                    effectPaint.color = Color.argb((fade * 235f).toInt(), 110, 255, 90)
                    canvas.drawCircle(ax, ay, cellSize * 0.11f, effectPaint)
                    if (shotT > 0.75f) {
                        effectPaint.style = Paint.Style.STROKE
                        effectPaint.strokeWidth = cellSize * 0.06f
                        canvas.drawCircle(x2, y2, cellSize * (0.18f + (t - 0.4f).coerceAtLeast(0f) * 0.45f), effectPaint)
                    }
                }
                EffectKind.CLAW -> {
                    // Kreuzende Alien-Krallen-Schnitte auf dem Ziel
                    val r = cellSize * 0.28f * shotT
                    effectPaint.style = Paint.Style.STROKE
                    effectPaint.color = Color.argb((fade * 245f).toInt(), 240, 255, 245)
                    effectPaint.strokeWidth = cellSize * 0.065f
                    canvas.drawLine(x2 - r, y2 - r, x2 + r, y2 + r, effectPaint)
                    canvas.drawLine(x2 + r, y2 - r, x2 - r, y2 + r, effectPaint)
                }
                EffectKind.EXPLOSION -> {
                    effectPaint.style = Paint.Style.FILL
                    effectPaint.color = Color.argb((fade * 135f).toInt(), 160, 255, 65)
                    canvas.drawCircle(x2, y2, cellSize * (0.35f + t * 1.05f), effectPaint)
                    effectPaint.style = Paint.Style.STROKE
                    effectPaint.strokeWidth = cellSize * 0.09f
                    effectPaint.color = Color.argb((fade * 245f).toInt(), 235, 255, 165)
                    canvas.drawCircle(x2, y2, cellSize * (0.25f + t * 1.15f), effectPaint)
                }
                EffectKind.PSIONIC -> {
                    effectPaint.style = Paint.Style.STROKE
                    effectPaint.strokeWidth = cellSize * 0.06f
                    effectPaint.color = Color.argb((fade * 225f).toInt(), 255, 77, 158)
                    canvas.drawCircle(x2, y2, cellSize * (0.18f + t * 0.65f), effectPaint)
                    canvas.drawCircle(x2, y2, cellSize * (0.10f + t * 0.40f), effectPaint)
                }
                EffectKind.CONSOLE, EffectKind.VENT, EffectKind.HATCH -> {
                    effectPaint.style = Paint.Style.STROKE
                    effectPaint.strokeWidth = cellSize * 0.07f
                    effectPaint.color = Color.argb((fade * 230f).toInt(), Color.red(color), Color.green(color), Color.blue(color))
                    if (ef.from != ef.to) {
                        canvas.drawLine(x1, y1, x2, y2, effectPaint)
                    }
                    canvas.drawCircle(x2, y2, cellSize * (0.18f + t * 0.44f), effectPaint)
                }
            }

            // Hochgleitendes Schadens-/Status-Label
            val lift = t * cellSize * 0.36f
            badgeText.color = Color.argb((fade * 255f).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
            badgeText.textSize = cellSize * 0.25f
            canvas.drawText(ef.label, x2, y2 - cellSize * 0.24f - lift, badgeText)
        }
    }

    private fun drawAtmosphericParticles(canvas: Canvas, lvl: LevelDefinition, isNight: Boolean, timeSec: Float) {
        val boardW = lvl.width * cellSize
        val boardH = lvl.height * cellSize
        for (i in 0 until dustCount) {
            val px = boardLeft + ((dustX[i] + timeSec * dustSpeedX[i]) % 1f) * boardW
            val py = boardTop + ((dustY[i] + timeSec * dustSpeedY[i]) % 1f) * boardH
            val alpha = if (isNight) 58 else 36
            dustPaint.color = if (i % 4 == 0) Color.argb(alpha, 95, 255, 155) else Color.argb(alpha, 185, 210, 235)
            canvas.drawCircle(px, py, dustRadius[i], dustPaint)
        }
    }

    private fun drawEmergencyFlicker(canvas: Canvas, lvl: LevelDefinition, isNight: Boolean, timeSec: Float) {
        // Seltenes, unheimliches Flackern einer defekten Korridor-Leuchte
        val trigger = sin(timeSec * 1.7f) * sin(timeSec * 4.3f) * sin(timeSec * 11.1f)
        if (trigger > 0.78f) {
            effectPaint.style = Paint.Style.FILL
            effectPaint.color = if (isNight) Color.argb(38, 180, 25, 35) else Color.argb(26, 0, 0, 0)
            canvas.drawRect(boardLeft, boardTop, boardLeft + lvl.width * cellSize, boardTop + lvl.height * cellSize, effectPaint)
        }
    }

    private fun drawMotionTrackerPing(canvas: Canvas, nowMs: Long) {
        if (motionPingStartMs == 0L) return
        val dt = (nowMs - motionPingStartMs) / 950f
        if (dt !in 0f..1f) return
        val cx = width * 0.5f
        val cy = height * 0.5f
        val maxR = maxOf(width, height) * 0.55f
        effectPaint.style = Paint.Style.STROKE
        effectPaint.strokeWidth = 3.5f
        effectPaint.color = Color.argb(((1f - dt) * 155f).toInt().coerceIn(0, 200), 57, 255, 136)
        canvas.drawCircle(cx, cy, dt * maxR, effectPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val lvl = level ?: return true
            val gx = ((event.x - boardLeft) / cellSize).toInt()
            val gy = ((event.y - boardTop) / cellSize).toInt()
            if (gx in 0 until lvl.width && gy in 0 until lvl.height) {
                performClick()
                onCellTap?.invoke(GridPos(gx, gy))
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun cellToRect(pos: GridPos, out: RectF) {
        val left = boardLeft + pos.x * cellSize
        val top = boardTop + pos.y * cellSize
        out.set(left, top, left + cellSize, top + cellSize)
    }

    private fun gridFloatToRect(gx: Float, gy: Float, out: RectF) {
        val left = boardLeft + gx * cellSize
        val top = boardTop + gy * cellSize
        out.set(left, top, left + cellSize, top + cellSize)
    }

    private fun centerX(pos: GridPos): Float = boardLeft + (pos.x + 0.5f) * cellSize
    private fun centerY(pos: GridPos): Float = boardTop + (pos.y + 0.5f) * cellSize
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    private fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)
}

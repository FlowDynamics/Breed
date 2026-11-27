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
 * Interaktive 60-FPS-Vollbild-Spielfläche für das neue BREED Hive-Flow-Gameplay:
 * - Oben (~54 %): Biomechanisches Raumschiff-Zimmer voller farbcodierter Wachen + live stürmende Alien-Schwärme
 * - Mitte (~13 %): 5 aktive Brut-Slots (Holding-Slots) mit Alien-Icon & Rest-Zähler
 * - Unten (~33 %): 4 Spalten antippbarer Alien-Kapsel-Blöcke (1 Tipp aktiviert sofort den Schwarm!)
 */
class HiveFlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val theme = BiomechTheme()
    private val sprites = SpritePainter()

    private var engine: FlowGameEngine? = null
    private var lastFrameMs: Long = 0L
    private var isAttached = false

    // Layout-Geometrie
    private var roomLeft = 0f
    private var roomTop = 0f
    private var cellSize = 64f
    private var roomBottom = 0f

    private var slotAreaTop = 0f
    private var slotAreaBottom = 0f
    private val slotRects = Array(5) { RectF() }

    private var stackAreaTop = 0f
    private var stackAreaBottom = 0f
    private val frontPodRects = Array(4) { RectF() }

    // Vorallokierte Zeichenobjekte
    private val workRect = RectF()
    private val iconRect = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val subText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9FB4C9")
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val scanlinePaint = Paint().apply {
        color = Color.argb(14, 0, 0, 0)
        strokeWidth = 1f
    }
    private var vignetteShader: Shader? = null

    // Dezente Staub-/Sporen-Partikel im Raumschiff
    private val dustCount = 18
    private val dustX = FloatArray(dustCount) { ((it * 67 + 13) % 100) / 100f }
    private val dustY = FloatArray(dustCount) { ((it * 43 + 29) % 100) / 100f }

    // Callbacks zur Activity
    var onColumnTapped: ((Int, FlowTapResult) -> Unit)? = null
    var onGuardInspected: ((FlowGuardType, Boolean) -> Unit)? = null
    var onEngineEvents: ((List<FlowEventKind>) -> Unit)? = null
    var onStateTick: (() -> Unit)? = null

    private var lastReportedGuards = -1
    private var lastReportedOutcome: FlowOutcome? = null

    fun bindEngine(newEngine: FlowGameEngine) {
        this.engine = newEngine
        this.lastFrameMs = SystemClock.uptimeMillis()
        this.lastReportedGuards = newEngine.remainingGuards
        this.lastReportedOutcome = newEngine.outcome
        recomputeLayout()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAttached = true
        lastFrameMs = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        isAttached = false
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeLayout()
        if (w > 0 && h > 0) {
            vignetteShader = RadialGradient(
                w * 0.5f, h * 0.34f, maxOf(w, h) * 0.72f,
                intArrayOf(Color.TRANSPARENT, Color.argb(35, 0, 0, 0), Color.argb(145, 0, 0, 0)),
                floatArrayOf(0.50f, 0.80f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
    }

    private fun recomputeLayout() {
        val eng = engine ?: return
        if (width <= 0 || height <= 0) return

        val padX = width * 0.028f
        val availW = width - padX * 2f
        val maxRoomH = height * 0.50f

        cellSize = minOf(availW / eng.gridWidth, maxRoomH / eng.gridHeight)
        val boardW = cellSize * eng.gridWidth
        val boardH = cellSize * eng.gridHeight

        val slotH = (height * 0.112f).coerceIn(88f, 165f)
        val roomToSlotGap = (height * 0.036f).coerceIn(28f, 68f)
        val slotToStackGap = (height * 0.020f).coerceIn(16f, 38f)
        val stackH = (height * 0.30f).coerceIn(220f, 520f)

        val totalContentH = boardH + roomToSlotGap + slotH + slotToStackGap + stackH
        val topOffset = ((height - totalContentH) * 0.42f).coerceAtLeast(14f)

        roomLeft = (width - boardW) * 0.5f
        roomTop = topOffset
        roomBottom = roomTop + boardH

        // Mittlere Zone: 5 Brut-Slots direkt unter dem Einmarsch-Korridor
        slotAreaTop = roomBottom + roomToSlotGap
        slotAreaBottom = slotAreaTop + slotH

        val slotGap = width * 0.016f
        val slotW = (availW - slotGap * (eng.slotCount - 1)) / eng.slotCount
        for (i in 0 until minOf(eng.slotCount, slotRects.size)) {
            val sx = padX + i * (slotW + slotGap)
            slotRects[i].set(sx, slotAreaTop + 6f, sx + slotW, slotAreaBottom - 6f)
        }

        // Untere Zone: 4 Spalten mit Alien-Kapsel-Blöcken
        stackAreaTop = slotAreaBottom + slotToStackGap
        stackAreaBottom = minOf(stackAreaTop + stackH, height - height * 0.016f)
        val colCount = eng.columns.size.coerceAtLeast(1)
        val colGap = width * 0.022f
        val colW = (availW - colGap * (colCount - 1)) / colCount
        val frontH = ((stackAreaBottom - stackAreaTop) * 0.38f).coerceIn(96f, 180f)
        for (c in 0 until minOf(colCount, frontPodRects.size)) {
            val cx = padX + c * (colW + colGap)
            frontPodRects[c].set(cx, stackAreaTop, cx + colW, stackAreaTop + frontH)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(theme.voidBg)

        val eng = engine ?: return
        val nowMs = SystemClock.uptimeMillis()
        val dtSec = ((nowMs - lastFrameMs).coerceIn(0L, 50L)) / 1000f
        lastFrameMs = nowMs

        // Echtzeit-Engine 1 Frame vorantreiben
        eng.update(dtSec)
        val events = eng.drainEvents()
        if (events.isNotEmpty()) {
            onEngineEvents?.invoke(events)
        }
        if (eng.remainingGuards != lastReportedGuards || eng.outcome != lastReportedOutcome) {
            lastReportedGuards = eng.remainingGuards
            lastReportedOutcome = eng.outcome
            onStateTick?.invoke()
        }

        val timeSec = nowMs / 1000f
        val pulse = timeSec * 1.6f

        // Screen-Shake bei Säurefass-Explosionen
        var shakeX = 0f
        var shakeY = 0f
        if (eng.screenShakeSec > 0f) {
            val mag = (eng.screenShakeSec / 0.25f) * cellSize * 0.14f
            shakeX = sin(timeSec * 90f) * mag
            shakeY = cos(timeSec * 77f) * mag
        }

        canvas.save()
        canvas.translate(shakeX, shakeY)

        // 1. Raumschiff-Zimmer oben
        drawRoomBoard(canvas, eng, timeSec, pulse)

        // 2. Laufende & kämpfende Aliens + Kampf-Effekte
        drawRunningAliens(canvas, eng, timeSec, pulse)
        drawVisualEffects(canvas, eng)

        // 3. 5 Aktive Brut-Slots in der Mitte
        drawHiveSlots(canvas, eng, pulse)

        // 4. 4 Spalten mit Alien-Kapsel-Blöcken unten
        drawPodColumns(canvas, eng, pulse)

        canvas.restore()

        // Dezente Sci-Fi-Scanlines & Vignette
        var sy = 0f
        while (sy < roomBottom + 10f) {
            canvas.drawLine(0f, sy, width.toFloat(), sy, scanlinePaint)
            sy += 7f
        }
        vignetteShader?.let {
            fill.shader = it
            canvas.drawRect(0f, 0f, width.toFloat(), roomBottom + 20f, fill)
            fill.shader = null
        }

        if (isAttached && visibility == VISIBLE) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawRoomBoard(canvas: Canvas, eng: FlowGameEngine, timeSec: Float, pulse: Float) {
        val lvl = eng.level
        // Äußerer metallischer Raum-Rahmen
        val boardW = eng.gridWidth * cellSize
        val boardH = eng.gridHeight * cellSize
        workRect.set(roomLeft - 3f, roomTop - 3f, roomLeft + boardW + 3f, roomTop + boardH + 3f)
        fill.color = Color.parseColor("#070C12")
        canvas.drawRoundRect(workRect, 12f, 12f, fill)

        for (y in 0 until eng.gridHeight) {
            for (x in 0 until eng.gridWidth) {
                val cell = eng.cells[x][y]
                if (cell.kind == FlowTileKind.VOID) continue
                cellToRect(x, y, workRect)

                val mappedType = when (cell.kind) {
                    FlowTileKind.WALL -> TileType.WALL
                    FlowTileKind.WINDOW -> TileType.WINDOW
                    FlowTileKind.CREEP -> TileType.CREEP
                    FlowTileKind.BARREL -> TileType.ACID_BARREL
                    FlowTileKind.BARRIER -> TileType.DOOR
                    else -> TileType.FLOOR
                }
                val tileCell = TileCell(
                    pos = cell.pos,
                    type = mappedType,
                    doorOpen = false,
                    barrelIntact = cell.kind == FlowTileKind.BARREL
                )
                theme.drawTile(
                    canvas = canvas,
                    cell = tileCell,
                    rect = workRect,
                    planetTheme = lvl.planetTheme,
                    isNight = lvl.isNight,
                    pulsePhase = pulse,
                    highlightDeployable = false
                )

                // Wachen zeichnen mit klarer Klassen-Farb-Signatur!
                if (cell.kind == FlowTileKind.GUARD && cell.guardType != null) {
                    drawGuardOnTile(canvas, workRect, cell, pulse)
                }
            }
        }

        // Schwebende Sporen-Partikel über dem Zimmer
        for (i in 0 until dustCount) {
            val px = roomLeft + ((dustX[i] + timeSec * 0.012f) % 1f) * boardW
            val py = roomTop + ((dustY[i] + timeSec * 0.008f) % 1f) * boardH
            fill.color = if (i % 3 == 0) Color.argb(46, 80, 255, 145) else Color.argb(34, 175, 210, 240)
            canvas.drawCircle(px, py, 2.2f, fill)
        }

        // Offene Einmarsch-Pfeile an der Unterkante des Zimmers (Aliens strömen von unten nach oben!)
        val arrowAlpha = (120 + (sin(timeSec * 5f) * 65f).toInt()).coerceIn(60, 210)
        text.color = Color.argb(arrowAlpha, 57, 255, 136)
        text.textSize = cellSize * 0.24f
        for (x in 0 until eng.gridWidth) {
            val bottom = eng.cells[x][eng.gridHeight - 1]
            if (bottom.kind == FlowTileKind.FLOOR || bottom.kind == FlowTileKind.CREEP || bottom.kind == FlowTileKind.GUARD) {
                canvas.drawText("▲", roomLeft + (x + 0.5f) * cellSize, roomBottom + cellSize * 0.20f, text)
            }
        }

        // Rahmenfarbe (leuchtet grün bei Sieg, rot bei Blockade)
        stroke.color = when (eng.outcome) {
            FlowOutcome.VICTORY -> Color.parseColor("#39FF88")
            FlowOutcome.DEADLOCK -> Color.parseColor("#FF4252")
            FlowOutcome.PLAYING -> if (lvl.isNight) Color.parseColor("#2A4A7C") else Color.parseColor("#29475E")
        }
        stroke.strokeWidth = if (eng.outcome != FlowOutcome.PLAYING) 4.5f else 2.5f
        workRect.set(roomLeft - 2f, roomTop - 2f, roomLeft + boardW + 2f, roomTop + boardH + 2f)
        canvas.drawRoundRect(workRect, 10f, 10f, stroke)
    }

    private fun drawGuardOnTile(canvas: Canvas, rect: RectF, cell: FlowCellState, pulse: Float) {
        val gType = cell.guardType ?: return
        val accent = gType.accentColorHex.toInt()
        val padInset = cellSize * 0.06f

        // Farbiges Klassen-Pad unter jeder Wache -> sofort auf einen Blick mit den Alien-Blöcken unten abgleichbar!
        iconRect.set(rect.left + padInset, rect.top + padInset, rect.right - padInset, rect.bottom - padInset)
        val padAlpha = if (cell.exposed) 62 else 28
        fill.color = Color.argb(padAlpha, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(iconRect, cellSize * 0.14f, cellSize * 0.14f, fill)

        // Exponierte Front-Wachen leuchten klar auf; verdeckte Wachen sind dezent gedimmt
        stroke.color = if (cell.exposed) {
            val glow = (185 + (sin(pulse * 4.5f) * 55f).toInt()).coerceIn(130, 255)
            Color.argb(glow, Color.red(accent), Color.green(accent), Color.blue(accent))
        } else {
            Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent))
        }
        stroke.strokeWidth = if (cell.exposed) cellSize * 0.055f else cellSize * 0.025f
        canvas.drawRoundRect(iconRect, cellSize * 0.14f, cellSize * 0.14f, stroke)

        // Wachen-Silhouette zeichnen (ohne kleine HP-Leiste, da 1 Alien = 1 Wache -> noch sauberer!)
        val dummyHuman = HumanRuntimeState(
            id = "g",
            type = gType.humanVisual,
            pos = cell.pos,
            hp = 1,
            maxHp = 0,
            armor = 0,
            facing = Direction.SOUTH,
            powered = true
        )
        sprites.drawHuman(canvas, rect, dummyHuman)

        // Kommandanten-Krone/Stern zur klaren Unterscheidung vom normalen Space Marine
        if (gType == FlowGuardType.COMMANDER) {
            text.color = accent
            text.textSize = cellSize * 0.26f
            canvas.drawText("★", rect.centerX(), rect.top + cellSize * 0.28f, text)
        }

        // Verdeckte (noch nicht erreichbare) Wachen leicht abdunkeln -> Front-Linie sofort erkennbar!
        if (!cell.exposed) {
            fill.color = Color.argb(92, 4, 8, 16)
            canvas.drawRoundRect(iconRect, cellSize * 0.14f, cellSize * 0.14f, fill)
        }

        // Weiß-rotes Treffer-Aufblitzen während des Nahkampfs
        if (cell.hitFlashSec > 0f) {
            fill.color = Color.argb(120, 255, 255, 255)
            canvas.drawRoundRect(iconRect, cellSize * 0.14f, cellSize * 0.14f, fill)
        }
    }

    private fun drawRunningAliens(canvas: Canvas, eng: FlowGameEngine, timeSec: Float, pulse: Float) {
        for (i in eng.runningAliens.indices) {
            val a = eng.runningAliens[i]
            val px = roomLeft + a.x * cellSize
            val py = roomTop + a.y * cellSize
            // Lebendiges Scurrying-Wippen beim Stürmen & Zustoßen im Nahkampf
            val bob = if (a.phase == RunningAlienPhase.RUNNING) {
                sin(timeSec * 28f + i * 1.7f) * cellSize * 0.05f
            } else {
                sin(a.fightTimerSec * 38f) * cellSize * 0.08f
            }
            val half = cellSize * 0.44f
            iconRect.set(px - half + bob, py - half, px + half + bob, py + half)
            sprites.drawAlien(
                canvas = canvas,
                cellRect = iconRect,
                species = a.species,
                stage = EvolutionStage.ADULT,
                hp = 1,
                maxHp = 0,
                pulsePhase = pulse
            )
        }
    }

    private fun drawVisualEffects(canvas: Canvas, eng: FlowGameEngine) {
        for (i in eng.visualFx.indices) {
            val fx = eng.visualFx[i]
            val t = (fx.ageSec / fx.durationSec).coerceIn(0f, 1f)
            val fade = (1f - t).coerceIn(0f, 1f)
            val x1 = roomLeft + fx.fromX * cellSize
            val y1 = roomTop + fx.fromY * cellSize
            val x2 = roomLeft + fx.toX * cellSize
            val y2 = roomTop + fx.toY * cellSize
            val c = fx.colorHex.toInt()
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)

            when (fx.kind) {
                EffectKind.LASER, EffectKind.FLAME -> {
                    stroke.color = Color.argb((fade * 225).toInt(), r, g, b)
                    stroke.strokeWidth = if (fx.kind == EffectKind.FLAME) cellSize * 0.13f else cellSize * 0.06f
                    canvas.drawLine(x1, y1, x2, y2, stroke)
                }
                EffectKind.ACID -> {
                    val ax = x1 + (x2 - x1) * minOf(1f, t * 1.8f)
                    val ay = y1 + (y2 - y1) * minOf(1f, t * 1.8f)
                    fill.color = Color.argb((fade * 240).toInt(), r, g, b)
                    canvas.drawCircle(ax, ay, cellSize * 0.12f, fill)
                    stroke.color = Color.argb((fade * 210).toInt(), r, g, b)
                    stroke.strokeWidth = cellSize * 0.05f
                    canvas.drawCircle(x2, y2, cellSize * (0.16f + t * 0.42f), stroke)
                }
                EffectKind.CLAW -> {
                    val cr = cellSize * 0.30f * minOf(1f, t * 2f)
                    stroke.color = Color.argb((fade * 250).toInt(), 245, 255, 245)
                    stroke.strokeWidth = cellSize * 0.07f
                    canvas.drawLine(x2 - cr, y2 - cr, x2 + cr, y2 + cr, stroke)
                    canvas.drawLine(x2 + cr, y2 - cr, x2 - cr, y2 + cr, stroke)
                }
                EffectKind.PSIONIC -> {
                    stroke.color = Color.argb((fade * 235).toInt(), r, g, b)
                    stroke.strokeWidth = cellSize * 0.06f
                    canvas.drawCircle(x2, y2, cellSize * (0.14f + t * 0.55f), stroke)
                }
                EffectKind.EXPLOSION -> {
                    fill.color = Color.argb((fade * 140).toInt(), 160, 255, 65)
                    canvas.drawCircle(x2, y2, cellSize * (0.35f + t * 1.15f), fill)
                    stroke.color = Color.argb((fade * 245).toInt(), 235, 255, 160)
                    stroke.strokeWidth = cellSize * 0.09f
                    canvas.drawCircle(x2, y2, cellSize * (0.25f + t * 1.25f), stroke)
                }
                else -> Unit
            }
        }
    }

    private fun drawHiveSlots(canvas: Canvas, eng: FlowGameEngine, pulse: Float) {
        // Hintergrund-Leiste für die 5 Brut-Slots
        val trayPad = width * 0.02f
        workRect.set(trayPad, slotAreaTop, width - trayPad, slotAreaBottom)
        fill.color = Color.parseColor("#0B131D")
        canvas.drawRoundRect(workRect, 14f, 14f, fill)
        stroke.color = if (eng.freeSlotCount() == 0) Color.parseColor("#FF4D5A") else Color.parseColor("#254358")
        stroke.strokeWidth = 2.5f
        canvas.drawRoundRect(workRect, 14f, 14f, stroke)

        for (i in 0 until eng.slotCount) {
            val rect = slotRects[i]
            val slot = eng.slots[i]
            if (slot == null) {
                fill.color = Color.parseColor("#070B11")
                canvas.drawRoundRect(rect, 12f, 12f, fill)
                stroke.color = Color.parseColor("#1D3142")
                stroke.strokeWidth = 2f
                canvas.drawRoundRect(rect, 12f, 12f, stroke)
                subText.color = Color.parseColor("#354C60")
                subText.textSize = rect.height() * 0.22f
                canvas.drawText("${i + 1}", rect.centerX(), rect.centerY() + subText.textSize * 0.35f, subText)
            } else {
                val accent = slot.species.accentColorHex.toInt()
                val scaleOffset = (1f - slot.slideAnim) * rect.height() * 0.35f
                workRect.set(rect.left, rect.top + scaleOffset, rect.right, rect.bottom + scaleOffset)

                fill.color = Color.argb(58, Color.red(accent), Color.green(accent), Color.blue(accent))
                canvas.drawRoundRect(workRect, 12f, 12f, fill)

                // Blockierter Slot pulsiert warnend bernstein/rot
                stroke.color = if (slot.stalled) {
                    val blink = (145 + (sin(pulse * 7f) * 95f).toInt()).coerceIn(90, 255)
                    Color.argb(blink, 255, 175, 50)
                } else {
                    accent
                }
                stroke.strokeWidth = if (slot.stalled) 4f else 3.2f
                canvas.drawRoundRect(workRect, 12f, 12f, stroke)

                // Alien-Icon oben im Slot
                val iconSz = minOf(workRect.width() * 0.64f, workRect.height() * 0.56f)
                iconRect.set(
                    workRect.centerX() - iconSz * 0.5f,
                    workRect.top + workRect.height() * 0.06f,
                    workRect.centerX() + iconSz * 0.5f,
                    workRect.top + workRect.height() * 0.06f + iconSz
                )
                sprites.drawAlien(canvas, iconRect, slot.species, EvolutionStage.ADULT, 1, 0, pulsePhase = pulse)

                // Große Rest-Anzahl unten im Slot
                text.color = Color.WHITE
                text.textSize = workRect.height() * 0.30f
                val label = if (slot.stalled) "${slot.remaining}⏳" else "${slot.remaining}"
                canvas.drawText(label, workRect.centerX(), workRect.bottom - workRect.height() * 0.11f, text)
            }
        }
    }

    private fun drawPodColumns(canvas: Canvas, eng: FlowGameEngine, pulse: Float) {
        val stackH = (stackAreaBottom - stackAreaTop).coerceAtLeast(100f)
        val frontH = frontPodRects[0].height()
        val buriedH = ((stackH - frontH - 12f) / 2.35f).coerceIn(50f, frontH * 0.72f)
        val rowGap = 9f

        // Zuerst gekoppelte Bio-Verbindungsröhren zwischen nebeneinanderliegenden Vorder-Blöcken zeichnen
        for (c in 0 until eng.columns.size - 1) {
            val a = eng.columns[c].firstOrNull() ?: continue
            val b = eng.columns[c + 1].firstOrNull() ?: continue
            if (a.linkedGroupId > 0 && a.linkedGroupId == b.linkedGroupId) {
                val rA = frontPodRects[c]
                val rB = frontPodRects[c + 1]
                val cy = rA.centerY()
                stroke.color = Color.parseColor("#39FF88")
                stroke.strokeWidth = 10f
                canvas.drawLine(rA.right - 2f, cy, rB.left + 2f, cy, stroke)
                stroke.color = Color.WHITE
                stroke.strokeWidth = 3f
                canvas.drawLine(rA.right - 2f, cy, rB.left + 2f, cy, stroke)
            }
        }

        for (c in eng.columns.indices) {
            val col = eng.columns[c]
            val baseRect = frontPodRects[c]
            if (col.isEmpty()) {
                // Leere Spalte dezent andeuten
                fill.color = Color.argb(45, 16, 26, 38)
                canvas.drawRoundRect(baseRect, 14f, 14f, fill)
                stroke.color = Color.argb(60, 42, 65, 86)
                stroke.strokeWidth = 1.5f
                canvas.drawRoundRect(baseRect, 14f, 14f, stroke)
                continue
            }

            // Bis zu 3 Blöcke pro Spalte darstellen (Reihe 0 = aktiv & antippbar, Reihe 1-2 = darunter liegend)
            val maxVisible = minOf(col.size, 3)
            for (row in maxVisible - 1 downTo 0) {
                val pod = col[row]
                val isFront = row == 0
                val topY = if (isFront) {
                    baseRect.top
                } else {
                    baseRect.bottom + rowGap + (row - 1) * (buriedH + rowGap * 0.7f)
                }
                val h = if (isFront) frontH else buriedH
                if (topY > stackAreaBottom) continue
                val insetX = if (isFront) 0f else (row * 7f)
                workRect.set(baseRect.left + insetX, topY, baseRect.right - insetX, minOf(topY + h, stackAreaBottom))

                val isHidden = !isFront && pod.hiddenUntilFront
                drawPodBlock(canvas, eng, pod, workRect, isFront, isHidden, pulse)
            }

            // Falls noch mehr Blöcke tief in der Spalte liegen -> kleiner Zähler unten
            if (col.size > 3) {
                subText.color = Color.parseColor("#6D849A")
                subText.textSize = 24f
                canvas.drawText("+${col.size - 3}", baseRect.centerX(), stackAreaBottom - 4f, subText)
            }
        }
    }

    private fun drawPodBlock(
        canvas: Canvas,
        eng: FlowGameEngine,
        pod: AlienPod,
        rect: RectF,
        isFront: Boolean,
        isHidden: Boolean,
        pulse: Float
    ) {
        val accent = if (isHidden) Color.parseColor("#6A7D92") else pod.species.accentColorHex.toInt()
        val hasTargetNow = isFront && eng.hasExposedTargetFor(pod.species)

        // Körper des Alien-Blocks (leicht plastisch)
        val bodyBg = when {
            isHidden -> Color.parseColor("#141B24")
            isFront -> Color.argb(72, Color.red(accent), Color.green(accent), Color.blue(accent))
            else -> Color.argb(34, Color.red(accent), Color.green(accent), Color.blue(accent))
        }
        fill.color = Color.parseColor("#0E1620")
        canvas.drawRoundRect(rect, 16f, 16f, fill)
        fill.color = bodyBg
        canvas.drawRoundRect(rect, 16f, 16f, fill)

        // Leuchtender Rand – extra kräftig, wenn passende Wachen gerade vorn erreichbar sind!
        stroke.color = when {
            isHidden -> Color.parseColor("#3E5063")
            hasTargetNow -> {
                val glow = (205 + (sin(pulse * 5.2f) * 50f).toInt()).coerceIn(160, 255)
                Color.argb(glow, Color.red(accent), Color.green(accent), Color.blue(accent))
            }
            isFront -> Color.argb(180, Color.red(accent), Color.green(accent), Color.blue(accent))
            else -> Color.argb(95, Color.red(accent), Color.green(accent), Color.blue(accent))
        }
        stroke.strokeWidth = when {
            hasTargetNow -> 5f
            isFront -> 3.2f
            else -> 2f
        }
        canvas.drawRoundRect(rect, 16f, 16f, stroke)

        if (isHidden) {
            text.color = Color.parseColor("#8CA2B8")
            text.textSize = rect.height() * 0.44f
            canvas.drawText("?", rect.centerX(), rect.centerY() + text.textSize * 0.35f, text)
            return
        }

        if (isFront) {
            // Alien-Silhouette links/oben + große klare Zahl rechts/unten + Klassen-Kurzname
            val iconSz = minOf(rect.width() * 0.48f, rect.height() * 0.56f)
            iconRect.set(
                rect.left + rect.width() * 0.08f,
                rect.top + rect.height() * 0.10f,
                rect.left + rect.width() * 0.08f + iconSz,
                rect.top + rect.height() * 0.10f + iconSz
            )
            sprites.drawAlien(canvas, iconRect, pod.species, EvolutionStage.ADULT, 1, 0, pulsePhase = pulse)

            // Große klare Anzahl der Aliens in diesem Block
            text.color = Color.WHITE
            text.textSize = rect.height() * 0.38f
            canvas.drawText("${pod.count}", rect.right - rect.width() * 0.27f, rect.top + rect.height() * 0.52f, text)

            // Klassenname unten auf dem Block
            subText.color = accent
            subText.textSize = (rect.height() * 0.17f).coerceIn(20f, 30f)
            canvas.drawText(pod.species.shortName, rect.centerX(), rect.bottom - rect.height() * 0.12f, subText)
        } else {
            // Kompakter tieferer Block (Vorschau auf nächste Welle)
            val iconSz = minOf(rect.width() * 0.34f, rect.height() * 0.68f)
            iconRect.set(
                rect.left + rect.width() * 0.14f,
                rect.centerY() - iconSz * 0.5f,
                rect.left + rect.width() * 0.14f + iconSz,
                rect.centerY() + iconSz * 0.5f
            )
            sprites.drawAlien(canvas, iconRect, pod.species, EvolutionStage.ADULT, 1, 0, pulsePhase = pulse)

            text.color = Color.argb(200, 235, 245, 255)
            text.textSize = rect.height() * 0.40f
            canvas.drawText("${pod.count}", rect.right - rect.width() * 0.28f, rect.centerY() + text.textSize * 0.35f, text)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val eng = engine ?: return true
            val tx = event.x
            val ty = event.y

            // 1. Tipp auf eine der 4 unteren Alien-Spalten (großzügige Trefferfläche über die ganze Spalte!)
            if (ty >= stackAreaTop - 16f && ty <= stackAreaBottom + 16f) {
                for (c in eng.columns.indices) {
                    val r = frontPodRects[c]
                    if (tx >= r.left - 8f && tx <= r.right + 8f) {
                        performClick()
                        val res = eng.tapColumn(c)
                        onColumnTapped?.invoke(c, res)
                        invalidate()
                        return true
                    }
                }
            }

            // 2. Tipp auf eine Wache im Zimmer oben -> zeigt Konter-Hinweis!
            if (ty >= roomTop && ty <= roomBottom) {
                val gx = ((tx - roomLeft) / cellSize).toInt()
                val gy = ((ty - roomTop) / cellSize).toInt()
                if (gx in 0 until eng.gridWidth && gy in 0 until eng.gridHeight) {
                    val cell = eng.cells[gx][gy]
                    if (cell.kind == FlowTileKind.GUARD && cell.guardType != null) {
                        performClick()
                        onGuardInspected?.invoke(cell.guardType!!, cell.exposed)
                        return true
                    }
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun cellToRect(x: Int, y: Int, out: RectF) {
        val left = roomLeft + x * cellSize
        val top = roomTop + y * cellSize
        out.set(left, top, left + cellSize, top + cellSize)
    }
}

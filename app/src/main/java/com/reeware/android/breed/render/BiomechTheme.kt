package com.reeware.android.breed.render

import android.graphics.*
import com.reeware.android.breed.engine.GameLocale
import com.reeware.android.breed.engine.PlanetTheme
import com.reeware.android.breed.engine.TileCell
import com.reeware.android.breed.engine.TileType
import kotlin.math.cos
import kotlin.math.sin

/**
 * Düstere biomechanische Farbpalette und Kachel-Renderer inspiriert von
 * Alien / Alien Breed (kalte Stahlkorridore, organische Rippen, Schatten)
 * kombiniert mit StarCraft-Taktik-Lesbarkeit.
 */
class BiomechTheme {

    val voidBg = Color.parseColor("#030508")
    val hullSteel = Color.parseColor("#11161E")
    val ribHighlight = Color.parseColor("#2E3B4B")
    val ribShadow = Color.parseColor("#05080C")
    val floorBase = Color.parseColor("#0A0F16")
    val floorGrate = Color.parseColor("#16202C")
    val creepBase = Color.parseColor("#210A2B")
    val creepVein = Color.parseColor("#7D249E")
    val creepGlow = Color.parseColor("#39FF88")
    val hudBorder = Color.parseColor("#264154")
    val nightTint = Color.argb(132, 2, 8, 22)
    val darkTileTint = Color.argb(176, 2, 4, 10)

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val workRect = RectF()
    private val workPath = Path()

    fun drawTile(
        canvas: Canvas,
        cell: TileCell,
        rect: RectF,
        planetTheme: PlanetTheme,
        isNight: Boolean,
        pulsePhase: Float,
        highlightDeployable: Boolean
    ) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val w = rect.width()
        val h = rect.height()

        when (cell.type) {
            TileType.WALL -> {
                fillPaint.color = hullSteel
                canvas.drawRect(rect, fillPaint)

                // Tiefe organisch-biomechanische Stahlrippen (H.R.-Giger-inspiriert)
                strokePaint.color = ribShadow
                strokePaint.strokeWidth = w * 0.09f
                canvas.drawLine(rect.left + w * 0.14f, rect.top, rect.left + w * 0.14f, rect.bottom, strokePaint)
                canvas.drawLine(rect.right - w * 0.14f, rect.top, rect.right - w * 0.14f, rect.bottom, strokePaint)

                strokePaint.color = ribHighlight
                strokePaint.strokeWidth = w * 0.05f
                for (i in 1..3) {
                    val y = rect.top + h * (i / 4f)
                    workPath.reset()
                    workPath.moveTo(rect.left + w * 0.08f, y)
                    workPath.quadTo(cx, y - h * 0.14f, rect.right - w * 0.08f, y)
                    canvas.drawPath(workPath, strokePaint)
                }

                // Zentraler dunkler Bio-Kabelkanal + dezenter metallischer Glanzpunkt
                fillPaint.color = ribShadow
                canvas.drawRect(cx - w * 0.09f, rect.top, cx + w * 0.09f, rect.bottom, fillPaint)
                strokePaint.color = Color.argb(95, 110, 140, 170)
                strokePaint.strokeWidth = 1.4f
                canvas.drawLine(cx - w * 0.04f, rect.top + h * 0.1f, cx - w * 0.04f, rect.bottom - h * 0.1f, strokePaint)

                strokePaint.color = Color.parseColor("#2A3544")
                strokePaint.strokeWidth = 1.5f
                canvas.drawRect(rect, strokePaint)
            }

            TileType.WINDOW -> {
                fillPaint.color = hullSteel
                canvas.drawRect(rect, fillPaint)
                workRect.set(rect.left + w * 0.09f, rect.top + h * 0.09f, rect.right - w * 0.09f, rect.bottom - h * 0.09f)
                canvas.save()
                canvas.clipRect(workRect)
                PlanetBackdrop.drawMiniWindowPlanet(canvas, workRect, planetTheme, isNight, cell.pos)
                canvas.restore()

                // Verstärkter Beobachtungs-Bullaugenrahmen + kalter Glasreflex
                strokePaint.color = Color.parseColor("#3E4F63")
                strokePaint.strokeWidth = w * 0.075f
                canvas.drawRect(workRect, strokePaint)
                strokePaint.color = Color.argb(55, 180, 230, 255)
                strokePaint.strokeWidth = w * 0.035f
                canvas.drawLine(workRect.left + w * 0.12f, workRect.top + h * 0.28f, workRect.left + w * 0.38f, workRect.top + h * 0.08f, strokePaint)
            }

            TileType.FLOOR, TileType.CREEP, TileType.ENTRY, TileType.EXIT, TileType.VENT, TileType.CONSOLE, TileType.ACID_BARREL, TileType.DOOR -> {
                drawBaseFloorGrate(canvas, rect, cell.pos.x, cell.pos.y)
                if (cell.type == TileType.CREEP) {
                    drawCreepOverlay(canvas, rect, pulsePhase, cell.pos.x + cell.pos.y * 3)
                }
                if (cell.acidPoolRounds > 0) {
                    drawAcidPool(canvas, cx, cy, w, pulsePhase)
                }

                when (cell.type) {
                    TileType.VENT -> drawVent(canvas, rect, cell.linkId, pulsePhase)
                    TileType.CONSOLE -> drawConsole(canvas, rect, cell.linkId, pulsePhase)
                    TileType.ACID_BARREL -> if (cell.barrelIntact) drawBarrel(canvas, rect, pulsePhase)
                    TileType.DOOR -> drawDoor(canvas, rect, cell.doorOpen, cell.linkId)
                    TileType.ENTRY -> drawAirlockMarker(canvas, rect, GameLocale.tr("EIN", "IN"), Color.parseColor("#FF8C24"))
                    TileType.EXIT -> drawAirlockMarker(canvas, rect, GameLocale.tr("ZIEL", "EXIT"), Color.parseColor("#FF2E4C"))
                    else -> Unit
                }
            }
        }

        // Schatten- / Nacht-Abdunklung (stärkere Spannung in der Dunkelheit)
        if (cell.type != TileType.WALL && cell.type != TileType.WINDOW) {
            if (cell.lightOff) {
                fillPaint.color = darkTileTint
                canvas.drawRect(rect, fillPaint)
            } else if (isNight) {
                fillPaint.color = nightTint
                canvas.drawRect(rect, fillPaint)
            }
        }

        // Organisches grünes Pulsieren auf gültigen Startfeldern im Setz-Modus
        if (highlightDeployable && cell.allowsAlienPlacement()) {
            val alpha = (95 + (sin(pulsePhase * 4.2f) * 75f).toInt()).coerceIn(45, 215)
            strokePaint.color = Color.argb(alpha, 57, 255, 136)
            strokePaint.strokeWidth = w * 0.06f
            workRect.set(rect.left + w * 0.06f, rect.top + h * 0.06f, rect.right - w * 0.06f, rect.bottom - h * 0.06f)
            canvas.drawRoundRect(workRect, w * 0.1f, w * 0.1f, strokePaint)
        }
    }

    private fun drawBaseFloorGrate(canvas: Canvas, rect: RectF, gx: Int, gy: Int) {
        // Leicht variierende dunkle Metallplatten für räumliche Tiefe
        val alt = (gx + gy) % 2 == 0
        fillPaint.color = if (alt) floorBase else Color.parseColor("#0D131C")
        canvas.drawRect(rect, fillPaint)

        val w = rect.width()
        val h = rect.height()
        strokePaint.color = floorGrate
        strokePaint.strokeWidth = 1.4f
        canvas.drawRect(rect, strokePaint)

        // Industrielle Deck-Gitterlinien & Nietenpunkte (Alien-Breed-Flair)
        canvas.drawLine(rect.left + w * 0.22f, rect.top + h * 0.20f, rect.right - w * 0.22f, rect.top + h * 0.20f, strokePaint)
        canvas.drawLine(rect.left + w * 0.22f, rect.bottom - h * 0.20f, rect.right - w * 0.22f, rect.bottom - h * 0.20f, strokePaint)

        fillPaint.color = Color.parseColor("#1D2835")
        val rivet = (w * 0.028f).coerceAtLeast(1.5f)
        canvas.drawCircle(rect.left + w * 0.11f, rect.top + h * 0.11f, rivet, fillPaint)
        canvas.drawCircle(rect.right - w * 0.11f, rect.bottom - h * 0.11f, rivet, fillPaint)
    }

    private fun drawCreepOverlay(canvas: Canvas, rect: RectF, pulsePhase: Float, seed: Int) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val w = rect.width()
        fillPaint.color = creepBase
        workRect.set(rect.left + w * 0.04f, rect.top + w * 0.04f, rect.right - w * 0.04f, rect.bottom - w * 0.04f)
        canvas.drawRoundRect(workRect, w * 0.24f, w * 0.24f, fillPaint)

        val pulse = 0.84f + 0.16f * sin(pulsePhase * 2.4f + seed * 0.7f)
        strokePaint.color = creepVein
        strokePaint.strokeWidth = w * 0.048f
        canvas.drawCircle(cx, cy, w * 0.24f * pulse, strokePaint)
        // Organische Adern zu den Kanten
        strokePaint.color = Color.argb(125, 145, 50, 185)
        strokePaint.strokeWidth = w * 0.028f
        canvas.drawLine(cx - w * 0.34f, cy - w * 0.12f, cx + w * 0.28f, cy + w * 0.18f, strokePaint)
        canvas.drawLine(cx + w * 0.25f, cy - w * 0.30f, cx - w * 0.18f, cy + w * 0.32f, strokePaint)

        fillPaint.color = Color.argb(110, 175, 80, 220)
        canvas.drawCircle(cx - w * 0.12f, cy + w * 0.08f, w * 0.065f * pulse, fillPaint)
        fillPaint.color = Color.argb(85, 57, 255, 136)
        canvas.drawCircle(cx + w * 0.14f, cy - w * 0.11f, w * 0.045f, fillPaint)
    }

    private fun drawAcidPool(canvas: Canvas, cx: Float, cy: Float, w: Float, pulsePhase: Float) {
        val r = w * (0.31f + 0.04f * sin(pulsePhase * 5f))
        fillPaint.color = Color.argb(115, 76, 255, 65)
        canvas.drawCircle(cx, cy, r, fillPaint)
        // Blubbernde Säureblasen
        fillPaint.color = Color.argb(180, 180, 255, 120)
        val b1x = cx + cos(pulsePhase * 3.1f) * w * 0.13f
        val b1y = cy + sin(pulsePhase * 2.7f) * w * 0.11f
        canvas.drawCircle(b1x, b1y, w * 0.045f, fillPaint)
        canvas.drawCircle(cx - w * 0.10f, cy + w * 0.09f, w * 0.032f, fillPaint)
    }

    private fun drawVent(canvas: Canvas, rect: RectF, linkId: Int, pulsePhase: Float) {
        val w = rect.width()
        val h = rect.height()
        workRect.set(rect.left + w * 0.14f, rect.top + h * 0.14f, rect.right - w * 0.14f, rect.bottom - h * 0.14f)
        fillPaint.color = Color.parseColor("#04060A")
        canvas.drawRoundRect(workRect, w * 0.08f, w * 0.08f, fillPaint)
        val rimAlpha = (160 + (sin(pulsePhase * 3f) * 55f).toInt()).coerceIn(110, 240)
        strokePaint.color = Color.argb(rimAlpha, 59, 232, 255)
        strokePaint.strokeWidth = w * 0.04f
        canvas.drawRoundRect(workRect, w * 0.08f, w * 0.08f, strokePaint)

        strokePaint.color = Color.parseColor("#2A384A")
        strokePaint.strokeWidth = w * 0.04f
        for (i in 1..3) {
            val ly = workRect.top + workRect.height() * (i / 4f)
            canvas.drawLine(workRect.left + w * 0.06f, ly, workRect.right - w * 0.06f, ly, strokePaint)
        }
        textPaint.color = Color.parseColor("#3BE8FF")
        textPaint.textSize = w * 0.24f
        canvas.drawText("V$linkId", rect.centerX(), rect.bottom - h * 0.04f, textPaint)
    }

    private fun drawConsole(canvas: Canvas, rect: RectF, linkId: Int, pulsePhase: Float) {
        val w = rect.width()
        val h = rect.height()
        workRect.set(rect.left + w * 0.18f, rect.top + h * 0.18f, rect.right - w * 0.18f, rect.bottom - h * 0.18f)
        fillPaint.color = Color.parseColor("#111D29")
        canvas.drawRoundRect(workRect, w * 0.1f, w * 0.1f, fillPaint)
        val glow = (175 + (sin(pulsePhase * 4.5f) * 65f).toInt()).coerceIn(120, 255)
        strokePaint.color = Color.argb(glow, 57, 255, 136)
        strokePaint.strokeWidth = w * 0.048f
        canvas.drawRoundRect(workRect, w * 0.1f, w * 0.1f, strokePaint)

        // Bewegter Monitor-Scanbalken
        val scanY = workRect.top + ((pulsePhase * 0.35f) % 1f) * workRect.height()
        strokePaint.color = Color.argb(90, 57, 255, 136)
        strokePaint.strokeWidth = 2f
        canvas.drawLine(workRect.left + 3f, scanY, workRect.right - 3f, scanY, strokePaint)

        textPaint.color = Color.parseColor("#39FF88")
        textPaint.textSize = w * 0.27f
        canvas.drawText("C$linkId", rect.centerX(), rect.centerY() + textPaint.textSize * 0.36f, textPaint)
    }

    private fun drawBarrel(canvas: Canvas, rect: RectF, pulsePhase: Float) {
        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = rect.width() * 0.29f
        // Subtiles giftgrünes Warn-Leuchten
        val aura = (35 + (sin(pulsePhase * 3.8f) * 25f).toInt()).coerceIn(15, 75)
        fillPaint.color = Color.argb(aura, 102, 255, 90)
        canvas.drawCircle(cx, cy, r * 1.38f, fillPaint)

        fillPaint.color = Color.parseColor("#17381B")
        canvas.drawCircle(cx, cy, r, fillPaint)
        strokePaint.color = Color.parseColor("#66FF66")
        strokePaint.strokeWidth = rect.width() * 0.055f
        canvas.drawCircle(cx, cy, r, strokePaint)
        textPaint.color = Color.parseColor("#D8FF9C")
        textPaint.textSize = rect.width() * 0.28f
        canvas.drawText("☢", cx, cy + textPaint.textSize * 0.35f, textPaint)
    }

    private fun drawDoor(canvas: Canvas, rect: RectF, open: Boolean, linkId: Int) {
        val w = rect.width()
        val h = rect.height()
        if (open) {
            fillPaint.color = Color.parseColor("#232F3E")
            canvas.drawRect(rect.left, rect.top, rect.left + w * 0.16f, rect.bottom, fillPaint)
            canvas.drawRect(rect.right - w * 0.16f, rect.top, rect.right, rect.bottom, fillPaint)
            strokePaint.color = Color.parseColor("#39FF88")
            strokePaint.strokeWidth = w * 0.04f
            canvas.drawLine(rect.left + w * 0.18f, rect.centerY(), rect.right - w * 0.18f, rect.centerY(), strokePaint)
        } else {
            workRect.set(rect.left + w * 0.05f, rect.top + h * 0.14f, rect.right - w * 0.05f, rect.bottom - h * 0.14f)
            fillPaint.color = Color.parseColor("#341E22")
            canvas.drawRect(workRect, fillPaint)
            strokePaint.color = Color.parseColor("#FF4252")
            strokePaint.strokeWidth = w * 0.05f
            canvas.drawRect(workRect, strokePaint)
            canvas.drawLine(rect.centerX(), workRect.top, rect.centerX(), workRect.bottom, strokePaint)
            textPaint.color = Color.parseColor("#FF8A80")
            textPaint.textSize = w * 0.22f
            canvas.drawText("S$linkId", rect.centerX(), rect.bottom - h * 0.04f, textPaint)
        }
    }

    private fun drawAirlockMarker(canvas: Canvas, rect: RectF, label: String, color: Int) {
        val w = rect.width()
        fillPaint.color = Color.argb(36, Color.red(color), Color.green(color), Color.blue(color))
        workRect.set(rect.left + w * 0.07f, rect.top + w * 0.07f, rect.right - w * 0.07f, rect.bottom - w * 0.07f)
        canvas.drawRect(workRect, fillPaint)
        strokePaint.color = color
        strokePaint.strokeWidth = w * 0.05f
        canvas.drawRect(workRect, strokePaint)
        textPaint.color = color
        textPaint.textSize = w * 0.25f
        canvas.drawText(label, rect.centerX(), rect.centerY() + textPaint.textSize * 0.35f, textPaint)
    }
}

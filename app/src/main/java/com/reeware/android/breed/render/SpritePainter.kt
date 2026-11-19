package com.reeware.android.breed.render

import android.graphics.*
import com.reeware.android.breed.engine.*

/**
 * Draws iconic high-contrast top-down silhouettes (StarCraft clarity x Alien bio-horror)
 * for all 5 alien species across their developmental stages and all human forces.
 */
class SpritePainter {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val path = Path()
    private val rect = RectF()

    fun drawAlien(
        canvas: Canvas,
        cellRect: RectF,
        species: AlienSpecies,
        stage: EvolutionStage,
        hp: Int,
        maxHp: Int,
        hatchCountdown: Int = 0,
        isSelected: Boolean = false,
        pulsePhase: Float = 0f
    ) {
        val cx = cellRect.centerX()
        val cy = cellRect.centerY()
        val size = cellRect.width()
        val accent = species.accentColorHex.toInt()

        if (isSelected) {
            stroke.color = Color.parseColor("#3BE8FF")
            stroke.strokeWidth = size * 0.06f
            canvas.drawCircle(cx, cy, size * 0.43f, stroke)
        }

        when (stage) {
            EvolutionStage.EGG -> drawEgg(canvas, cx, cy, size, accent, hatchCountdown, pulsePhase)
            EvolutionStage.LARVA -> drawLarva(canvas, cx, cy, size, accent)
            EvolutionStage.ADULT, EvolutionStage.ALPHA -> drawAdultAlien(canvas, cx, cy, size, species, stage == EvolutionStage.ALPHA, accent)
        }

        drawHealthBar(canvas, cellRect, hp, maxHp, accent)
    }

    private fun drawEgg(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        accent: Int,
        hatchCountdown: Int,
        pulsePhase: Float
    ) {
        val scale = 1f + 0.06f * kotlin.math.sin(pulsePhase * 4f)
        val rx = size * 0.24f * scale
        val ry = size * 0.30f * scale
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry)

        fill.color = Color.parseColor("#1B1826")
        canvas.drawOval(rect, fill)
        stroke.color = accent
        stroke.strokeWidth = size * 0.045f
        canvas.drawOval(rect, stroke)

        // Four-petal ovomorph slit at top
        stroke.color = Color.argb(220, Color.red(accent), Color.green(accent), Color.blue(accent))
        stroke.strokeWidth = size * 0.035f
        canvas.drawLine(cx - rx * 0.45f, cy, cx + rx * 0.45f, cy, stroke)
        canvas.drawLine(cx, cy - ry * 0.45f, cx, cy + ry * 0.45f, stroke)

        if (hatchCountdown > 0) {
            text.color = Color.WHITE
            text.textSize = size * 0.23f
            canvas.drawText("E$hatchCountdown", cx, cy - size * 0.25f, text)
        }
    }

    private fun drawLarva(canvas: Canvas, cx: Float, cy: Float, size: Float, accent: Int) {
        // Segmented scurrying facehugger/crawler silhouette
        fill.color = Color.parseColor("#211D2D")
        canvas.drawOval(cx - size * 0.18f, cy - size * 0.14f, cx + size * 0.18f, cy + size * 0.14f, fill)
        stroke.color = accent
        stroke.strokeWidth = size * 0.045f
        for (side in listOf(-1f, 1f)) {
            for (angle in listOf(-0.22f, 0f, 0.22f)) {
                canvas.drawLine(
                    cx + side * size * 0.12f,
                    cy + angle * size * 0.5f,
                    cx + side * size * 0.34f,
                    cy + angle * size * 1.15f,
                    stroke
                )
            }
        }
        // Coiled tail
        path.reset()
        path.moveTo(cx, cy + size * 0.12f)
        path.quadTo(cx + size * 0.18f, cy + size * 0.34f, cx - size * 0.08f, cy + size * 0.38f)
        canvas.drawPath(path, stroke)
        fill.color = accent
        canvas.drawCircle(cx, cy - size * 0.04f, size * 0.06f, fill)
    }

    private fun drawAdultAlien(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        species: AlienSpecies,
        isAlpha: Boolean,
        accent: Int
    ) {
        if (isAlpha) {
            // Alpha aura spikes
            stroke.color = Color.argb(170, 255, 220, 90)
            stroke.strokeWidth = size * 0.04f
            canvas.drawCircle(cx, cy, size * 0.37f, stroke)
        }

        val darkBody = Color.parseColor("#13111C")
        when (species) {
            AlienSpecies.STALKER -> {
                // Sleek xenomorph-inspired elongated crest + twin scythe arms + tail
                path.reset()
                path.moveTo(cx, cy - size * 0.36f)
                path.lineTo(cx + size * 0.24f, cy + size * 0.05f)
                path.lineTo(cx, cy + size * 0.34f)
                path.lineTo(cx - size * 0.24f, cy + size * 0.05f)
                path.close()
                fill.color = darkBody
                canvas.drawPath(path, fill)
                stroke.color = accent
                stroke.strokeWidth = size * 0.05f
                canvas.drawPath(path, stroke)
                // Twin front scythe claws
                canvas.drawLine(cx - size * 0.14f, cy - size * 0.1f, cx - size * 0.34f, cy - size * 0.32f, stroke)
                canvas.drawLine(cx + size * 0.14f, cy - size * 0.1f, cx + size * 0.34f, cy - size * 0.32f, stroke)
            }
            AlienSpecies.SPITTER -> {
                // Bulbous bioluminescent acid sac + mandibles
                fill.color = Color.parseColor("#15291A")
                canvas.drawCircle(cx, cy + size * 0.06f, size * 0.26f, fill)
                stroke.color = accent
                stroke.strokeWidth = size * 0.05f
                canvas.drawCircle(cx, cy + size * 0.06f, size * 0.26f, stroke)
                fill.color = accent
                canvas.drawCircle(cx, cy + size * 0.08f, size * 0.13f, fill)
                canvas.drawCircle(cx - size * 0.11f, cy - size * 0.22f, size * 0.07f, fill)
                canvas.drawCircle(cx + size * 0.11f, cy - size * 0.22f, size * 0.07f, fill)
            }
            AlienSpecies.BRUTE -> {
                // Massive armored hexagon shoulders + crushing arms
                rect.set(cx - size * 0.34f, cy - size * 0.24f, cx + size * 0.34f, cy + size * 0.24f)
                fill.color = Color.parseColor("#2A1812")
                canvas.drawRoundRect(rect, size * 0.14f, size * 0.14f, fill)
                stroke.color = accent
                stroke.strokeWidth = size * 0.065f
                canvas.drawRoundRect(rect, size * 0.14f, size * 0.14f, stroke)
                fill.color = accent
                canvas.drawRect(cx - size * 0.16f, cy - size * 0.32f, cx + size * 0.16f, cy - size * 0.14f, fill)
            }
            AlienSpecies.WEAVER -> {
                // Hex-legged chitin spider silhouette
                stroke.color = accent
                stroke.strokeWidth = size * 0.045f
                for (side in listOf(-1f, 1f)) {
                    for (dy in listOf(-0.22f, 0f, 0.22f)) {
                        canvas.drawLine(cx, cy, cx + side * size * 0.36f, cy + dy * size, stroke)
                    }
                }
                fill.color = Color.parseColor("#11262A")
                canvas.drawCircle(cx, cy, size * 0.21f, fill)
                canvas.drawCircle(cx, cy, size * 0.21f, stroke)
            }
            AlienSpecies.PSION -> {
                // Neural cerebrate crest + concentric psionic rings
                stroke.color = Color.argb(140, Color.red(accent), Color.green(accent), Color.blue(accent))
                stroke.strokeWidth = size * 0.035f
                canvas.drawCircle(cx, cy, size * 0.34f, stroke)
                fill.color = Color.parseColor("#2B1124")
                canvas.drawCircle(cx, cy, size * 0.22f, fill)
                stroke.color = accent
                stroke.strokeWidth = size * 0.055f
                canvas.drawCircle(cx, cy, size * 0.22f, stroke)
                fill.color = accent
                canvas.drawCircle(cx, cy, size * 0.09f, fill)
            }
            AlienSpecies.REAPER -> {
                // Klingen-Bestie: scharfe doppelte Sichelschwingen + gepanzerter Kamm
                path.reset()
                path.moveTo(cx, cy - size * 0.38f)
                path.lineTo(cx + size * 0.36f, cy - size * 0.14f)
                path.lineTo(cx + size * 0.18f, cy + size * 0.12f)
                path.lineTo(cx, cy + size * 0.36f)
                path.lineTo(cx - size * 0.18f, cy + size * 0.12f)
                path.lineTo(cx - size * 0.36f, cy - size * 0.14f)
                path.close()
                fill.color = Color.parseColor("#2B0D18")
                canvas.drawPath(path, fill)
                stroke.color = accent
                stroke.strokeWidth = size * 0.055f
                canvas.drawPath(path, stroke)
                fill.color = Color.parseColor("#FFD2DC")
                canvas.drawCircle(cx, cy - size * 0.08f, size * 0.07f, fill)
            }
            AlienSpecies.BOMBER -> {
                // Sporen-Bomber: strahlender Sporensack mit 3 äußeren Detonations-Blasen
                fill.color = Color.parseColor("#292E0D")
                canvas.drawCircle(cx, cy, size * 0.29f, fill)
                stroke.color = accent
                stroke.strokeWidth = size * 0.055f
                canvas.drawCircle(cx, cy, size * 0.29f, stroke)
                fill.color = accent
                canvas.drawCircle(cx, cy - size * 0.12f, size * 0.10f, fill)
                canvas.drawCircle(cx - size * 0.13f, cy + size * 0.10f, size * 0.09f, fill)
                canvas.drawCircle(cx + size * 0.13f, cy + size * 0.10f, size * 0.09f, fill)
            }
        }
    }

    fun drawHuman(canvas: Canvas, cellRect: RectF, human: HumanRuntimeState) {
        val cx = cellRect.centerX()
        val cy = cellRect.centerY()
        val size = cellRect.width()

        canvas.save()
        val angle = when (human.facing) {
            Direction.NORTH -> 0f
            Direction.EAST -> 90f
            Direction.SOUTH -> 180f
            Direction.WEST -> 270f
        }
        canvas.rotate(angle, cx, cy)

        when (human.type) {
            HumanType.MARINE -> {
                // StarCraft Terran-style broad shoulder armor + rifle
                fill.color = Color.parseColor("#2C4B78")
                rect.set(cx - size * 0.28f, cy - size * 0.16f, cx + size * 0.28f, cy + size * 0.2f)
                canvas.drawRoundRect(rect, size * 0.1f, size * 0.1f, fill)
                stroke.color = Color.parseColor("#7AB8FF")
                stroke.strokeWidth = size * 0.045f
                canvas.drawRoundRect(rect, size * 0.1f, size * 0.1f, stroke)
                // Helmet visor
                fill.color = Color.parseColor("#DCEEFF")
                canvas.drawCircle(cx, cy - size * 0.04f, size * 0.11f, fill)
                // Rifle barrel pointing North (rotated by facing)
                stroke.color = Color.parseColor("#B0C4DE")
                stroke.strokeWidth = size * 0.07f
                canvas.drawLine(cx + size * 0.12f, cy - size * 0.06f, cx + size * 0.12f, cy - size * 0.38f, stroke)
            }
            HumanType.PYRO -> {
                // Orange-red firebat armor + twin fuel tanks + twin nozzles
                fill.color = Color.parseColor("#8A2E1A")
                rect.set(cx - size * 0.30f, cy - size * 0.16f, cx + size * 0.30f, cy + size * 0.22f)
                canvas.drawRoundRect(rect, size * 0.1f, size * 0.1f, fill)
                stroke.color = Color.parseColor("#FF7833")
                stroke.strokeWidth = size * 0.05f
                canvas.drawRoundRect(rect, size * 0.1f, size * 0.1f, stroke)
                fill.color = Color.parseColor("#FFAA33")
                canvas.drawCircle(cx, cy - size * 0.05f, size * 0.10f, fill)
                stroke.color = Color.parseColor("#FF5722")
                stroke.strokeWidth = size * 0.07f
                canvas.drawLine(cx - size * 0.14f, cy - size * 0.1f, cx - size * 0.14f, cy - size * 0.38f, stroke)
                canvas.drawLine(cx + size * 0.14f, cy - size * 0.1f, cx + size * 0.14f, cy - size * 0.38f, stroke)
            }
            HumanType.TANK -> {
                // Heavy armored siege tank tracks + turret + cannon
                fill.color = Color.parseColor("#2A313A")
                canvas.drawRect(cx - size * 0.38f, cy - size * 0.30f, cx - size * 0.22f, cy + size * 0.30f, fill)
                canvas.drawRect(cx + size * 0.22f, cy - size * 0.30f, cx + size * 0.38f, cy + size * 0.30f, fill)
                fill.color = Color.parseColor("#4C5B6E")
                rect.set(cx - size * 0.28f, cy - size * 0.24f, cx + size * 0.28f, cy + size * 0.26f)
                canvas.drawRoundRect(rect, size * 0.08f, size * 0.08f, fill)
                stroke.color = Color.parseColor("#B0C6DE")
                stroke.strokeWidth = size * 0.045f
                canvas.drawRoundRect(rect, size * 0.08f, size * 0.08f, stroke)
                // Heavy main cannon
                stroke.color = Color.parseColor("#CFD8DC")
                stroke.strokeWidth = size * 0.10f
                canvas.drawLine(cx, cy, cx, cy - size * 0.44f, stroke)
            }
            HumanType.TURRET -> {
                val activeColor = if (human.powered) Color.parseColor("#FF3355") else Color.parseColor("#546E7A")
                fill.color = Color.parseColor("#262B33")
                canvas.drawCircle(cx, cy, size * 0.28f, fill)
                stroke.color = activeColor
                stroke.strokeWidth = size * 0.06f
                canvas.drawCircle(cx, cy, size * 0.28f, stroke)
                stroke.strokeWidth = size * 0.075f
                canvas.drawLine(cx - size * 0.08f, cy, cx - size * 0.08f, cy - size * 0.40f, stroke)
                canvas.drawLine(cx + size * 0.08f, cy, cx + size * 0.08f, cy - size * 0.40f, stroke)
            }
        }
        canvas.restore()

        val barColor = if (human.type.isStationary && !human.powered) Color.GRAY else Color.parseColor("#FF4455")
        drawHealthBar(canvas, cellRect, human.hp, human.maxHp, barColor)

        if (human.armor > 0) {
            text.color = Color.parseColor("#80D8FF")
            text.textSize = size * 0.21f
            canvas.drawText("A${human.armor}", cellRect.right - size * 0.2f, cellRect.top + size * 0.24f, text)
        }
        if (human.rootedRounds > 0) {
            text.color = Color.parseColor("#33E6D1")
            text.textSize = size * 0.22f
            canvas.drawText("NET", cx, cellRect.top + size * 0.24f, text)
        } else if (human.panickedRounds > 0) {
            text.color = Color.parseColor("#FF4D9E")
            text.textSize = size * 0.22f
            canvas.drawText("PAN", cx, cellRect.top + size * 0.24f, text)
        }
    }

    private fun drawHealthBar(canvas: Canvas, cellRect: RectF, hp: Int, maxHp: Int, color: Int) {
        if (maxHp <= 0) return
        val w = cellRect.width() * 0.72f
        val left = cellRect.centerX() - w * 0.5f
        val top = cellRect.bottom - cellRect.height() * 0.13f
        val barH = cellRect.height() * 0.08f

        fill.color = Color.argb(200, 10, 12, 16)
        canvas.drawRect(left, top, left + w, top + barH, fill)
        val frac = (hp.toFloat() / maxHp.toFloat()).coerceIn(0f, 1f)
        fill.color = color
        canvas.drawRect(left, top, left + w * frac, top + barH, fill)
    }
}

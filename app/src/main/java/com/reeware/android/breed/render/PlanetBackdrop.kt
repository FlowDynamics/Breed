package com.reeware.android.breed.render

import android.graphics.*
import com.reeware.android.breed.engine.GridPos
import com.reeware.android.breed.engine.PlanetTheme

/**
 * Renders deep-space starfields and planetary atmospheres visible through ship viewports
 * and behind the tactical ship sector map.
 */
object PlanetBackdrop {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    fun drawMiniWindowPlanet(
        canvas: Canvas,
        bounds: RectF,
        theme: PlanetTheme,
        isNight: Boolean,
        pos: GridPos
    ) {
        bgPaint.color = Color.parseColor("#04060B")
        canvas.drawRect(bounds, bgPaint)

        // Subtle deterministic stars inside the viewport
        for (i in 0..4) {
            val sx = bounds.left + ((pos.x * 37 + i * 29) % 100) / 100f * bounds.width()
            val sy = bounds.top + ((pos.y * 53 + i * 19) % 100) / 100f * bounds.height()
            starPaint.alpha = if (isNight) 210 else 130
            canvas.drawCircle(sx, sy, 1.4f, starPaint)
        }

        // Offset planet curve so windows look like views onto a huge orbiting world
        val radius = bounds.width() * 0.68f
        val px = bounds.right - bounds.width() * 0.25f
        val py = bounds.bottom - bounds.height() * 0.2f
        drawPlanetDisc(canvas, px, py, radius, theme, isNight)
    }

    fun drawPlanetDisc(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        theme: PlanetTheme,
        isNight: Boolean
    ) {
        val (litColor, midColor, darkColor, rimColor) = when (theme) {
            PlanetTheme.ACHERON_ASH -> Quad(
                Color.parseColor("#D96236"),
                Color.parseColor("#6E281B"),
                Color.parseColor("#180C10"),
                Color.argb(120, 255, 110, 50)
            )
            PlanetTheme.KRYON_ICE -> Quad(
                Color.parseColor("#88E5FF"),
                Color.parseColor("#2A6F99"),
                Color.parseColor("#0A1A2E"),
                Color.argb(130, 140, 235, 255)
            )
            PlanetTheme.XENO_NEBULA -> Quad(
                Color.parseColor("#D17CFF"),
                Color.parseColor("#5D248A"),
                Color.parseColor("#130824"),
                Color.argb(120, 210, 120, 255)
            )
            PlanetTheme.ORBIT_WRECK -> Quad(
                Color.parseColor("#DFB16B"),
                Color.parseColor("#6E502A"),
                Color.parseColor("#1C150E"),
                Color.argb(100, 235, 190, 100)
            )
        }

        // Outer atmospheric glow
        bgPaint.shader = null
        bgPaint.color = rimColor
        canvas.drawCircle(cx, cy, radius * 1.08f, bgPaint)

        // Shaded planet body (darker crescent during Night cycle)
        val lightOffset = if (isNight) -radius * 0.62f else -radius * 0.28f
        val effectiveLit = if (isNight) midColor else litColor
        bgPaint.shader = RadialGradient(
            cx + lightOffset,
            cy - radius * 0.3f,
            radius * 1.45f,
            intArrayOf(effectiveLit, midColor, darkColor),
            floatArrayOf(0.05f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, bgPaint)
        bgPaint.shader = null

        // Planetary rings for ice/nebula planets
        if (theme == PlanetTheme.KRYON_ICE || theme == PlanetTheme.XENO_NEBULA) {
            ringPaint.color = Color.argb(if (isNight) 70 else 120, 180, 240, 255)
            ringPaint.strokeWidth = (radius * 0.08f).coerceAtLeast(2f)
            canvas.save()
            canvas.rotate(-22f, cx, cy)
            canvas.drawOval(cx - radius * 1.45f, cy - radius * 0.28f, cx + radius * 1.45f, cy + radius * 0.28f, ringPaint)
            canvas.restore()
        }
    }

    private data class Quad(val a: Int, val b: Int, val c: Int, val d: Int)
}

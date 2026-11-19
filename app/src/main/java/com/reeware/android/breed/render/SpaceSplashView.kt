package com.reeware.android.breed.render

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.reeware.android.breed.engine.GameLocale
import kotlin.math.*

/**
 * Düsterer 60-FPS-Weltraum-Splashscreen für BREED:
 * Zeigt einen gewaltigen Gasriesen im Stil von Jupiter & Saturn (Sturmbänder, Wirbelauge,
 * vereistes Ringsystem im Planeten-Schatten), einen finsteren Begleitmond, eine ferne
 * Raumschiff-Silhouette und den mehrfach dramatisch aufblitzenden Titel "B R E E D".
 */
class SpaceSplashView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onTitleFlash: ((Int) -> Unit)? = null
    var onSplashFinished: (() -> Unit)? = null

    private var startTimeMs: Long = 0L
    private var running: Boolean = false
    private var finishDispatched: Boolean = false
    private var flash1Triggered = false
    private var flash2Triggered = false
    private var flash3Triggered = false

    // Vorallokierte Zeichenobjekte (keine Allokationen in onDraw!)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.38f
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.24f
    }
    private val promptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.16f
    }

    private val planetClipPath = Path()
    private val ringRect = RectF()
    private val stormRect = RectF()
    private val bandRect = RectF()
    private val boltPath = Path()

    // Vorgecachte Shader (in onSizeChanged erzeugt)
    private var spaceBgShader: Shader? = null
    private var nebulaShaderA: Shader? = null
    private var nebulaShaderB: Shader? = null
    private var planetShadowShader: Shader? = null
    private var planetRimShader: Shader? = null
    private var moonShader: Shader? = null
    private var vignetteShader: Shader? = null
    private var titleFlareShader: Shader? = null

    private var planetCx = 0f
    private var planetCy = 0f
    private var planetR = 0f
    private var moonCx = 0f
    private var moonCy = 0f
    private var moonR = 0f
    private var titleY = 0f

    // Deterministisches Sternenfeld (xNorm, yNorm, size, phase, speed)
    private val starCount = 135
    private val starX = FloatArray(starCount)
    private val starY = FloatArray(starCount)
    private val starR = FloatArray(starCount)
    private val starPhase = FloatArray(starCount)
    private val starTint = IntArray(starCount)

    private val bandColors = intArrayOf(
        Color.parseColor("#2A1D19"),
        Color.parseColor("#5C3A28"),
        Color.parseColor("#3B2823"),
        Color.parseColor("#7E5A3E"),
        Color.parseColor("#2F2427"),
        Color.parseColor("#6B4732"),
        Color.parseColor("#473530"),
        Color.parseColor("#8A6E56"),
        Color.parseColor("#2A2226"),
        Color.parseColor("#54382A"),
        Color.parseColor("#1D1921")
    )

    init {
        var s = 0x13579BDF
        for (i in 0 until starCount) {
            s = s * 1103515245 + 12345
            starX[i] = ((s ushr 16) and 0x7FFF) / 32767f
            s = s * 1103515245 + 12345
            starY[i] = ((s ushr 16) and 0x7FFF) / 32767f
            s = s * 1103515245 + 12345
            val rNorm = ((s ushr 16) and 0xFF) / 255f
            starR[i] = 0.8f + rNorm * rNorm * 2.4f
            starPhase[i] = (i * 0.73f) % (2f * PI.toFloat())
            starTint[i] = when (i % 7) {
                0 -> Color.rgb(185, 220, 255)
                1 -> Color.rgb(255, 220, 185)
                2 -> Color.rgb(190, 255, 225)
                else -> Color.rgb(235, 240, 250)
            }
        }
    }

    fun startSequence() {
        startTimeMs = SystemClock.uptimeMillis()
        running = true
        finishDispatched = false
        flash1Triggered = false
        flash2Triggered = false
        flash3Triggered = false
        invalidate()
    }

    fun stopSequence() {
        running = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val wf = w.toFloat()
        val hf = h.toFloat()

        planetCx = wf * 0.56f
        planetCy = hf * 0.35f
        planetR = minOf(wf * 0.34f, hf * 0.20f)

        moonCx = wf * 0.16f
        moonCy = hf * 0.78f
        moonR = planetR * 0.30f

        titleY = hf * 0.67f

        planetClipPath.reset()
        planetClipPath.addCircle(planetCx, planetCy, planetR, Path.Direction.CW)

        spaceBgShader = LinearGradient(
            0f, 0f, 0f, hf,
            intArrayOf(
                Color.parseColor("#020307"),
                Color.parseColor("#060A13"),
                Color.parseColor("#080E14"),
                Color.parseColor("#020305")
            ),
            floatArrayOf(0f, 0.38f, 0.74f, 1f),
            Shader.TileMode.CLAMP
        )

        nebulaShaderA = RadialGradient(
            wf * 0.18f, hf * 0.22f, wf * 0.75f,
            intArrayOf(Color.argb(55, 34, 88, 128), Color.argb(20, 28, 74, 72), Color.TRANSPARENT),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        nebulaShaderB = RadialGradient(
            wf * 0.85f, hf * 0.72f, wf * 0.70f,
            intArrayOf(Color.argb(48, 38, 118, 72), Color.argb(18, 65, 22, 78), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        // Harter Raum-Schatten über die rechte/untere Seite des Gasriesen
        planetShadowShader = RadialGradient(
            planetCx - planetR * 0.58f,
            planetCy - planetR * 0.48f,
            planetR * 1.68f,
            intArrayOf(
                Color.argb(0, 0, 0, 0),
                Color.argb(45, 8, 10, 18),
                Color.argb(215, 4, 6, 12),
                Color.argb(252, 2, 3, 7)
            ),
            floatArrayOf(0.0f, 0.44f, 0.76f, 1.0f),
            Shader.TileMode.CLAMP
        )

        // Kühler atmosphärischer Streulicht-Rand links oben
        planetRimShader = RadialGradient(
            planetCx - planetR * 0.35f,
            planetCy - planetR * 0.35f,
            planetR * 1.15f,
            intArrayOf(Color.TRANSPARENT, Color.argb(28, 150, 210, 255), Color.argb(115, 195, 225, 255), Color.TRANSPARENT),
            floatArrayOf(0.70f, 0.88f, 0.96f, 1.0f),
            Shader.TileMode.CLAMP
        )

        // Finsterer Begleitmond links unten
        moonShader = RadialGradient(
            moonCx - moonR * 0.45f,
            moonCy - moonR * 0.45f,
            moonR * 1.55f,
            intArrayOf(
                Color.parseColor("#7D8A97"),
                Color.parseColor("#2A323D"),
                Color.parseColor("#080B10"),
                Color.parseColor("#020306")
            ),
            floatArrayOf(0.05f, 0.42f, 0.78f, 1.0f),
            Shader.TileMode.CLAMP
        )

        vignetteShader = RadialGradient(
            wf * 0.5f, hf * 0.48f, maxOf(wf, hf) * 0.72f,
            intArrayOf(Color.TRANSPARENT, Color.argb(65, 0, 0, 0), Color.argb(210, 0, 0, 0)),
            floatArrayOf(0.45f, 0.78f, 1.0f),
            Shader.TileMode.CLAMP
        )

        titleFlareShader = LinearGradient(
            wf * 0.04f, titleY, wf * 0.96f, titleY,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(140, 80, 255, 165),
                Color.argb(255, 235, 255, 245),
                Color.argb(140, 80, 255, 165),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.28f, 0.5f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val elapsedSec = if (startTimeMs == 0L) 0f else (SystemClock.uptimeMillis() - startTimeMs) / 1000f

        // Synchronisierte Blitz-Events auslösen
        if (!flash1Triggered && elapsedSec >= 0.75f) {
            flash1Triggered = true
            onTitleFlash?.invoke(1)
        }
        if (!flash2Triggered && elapsedSec >= 1.75f) {
            flash2Triggered = true
            onTitleFlash?.invoke(2)
        }
        if (!flash3Triggered && elapsedSec >= 2.75f) {
            flash3Triggered = true
            onTitleFlash?.invoke(3)
        }

        // Berechne Blitz-Intensität (3 scharfe, abklingende Aufblitzen + leichtes Rest-Flackern)
        val flash1 = flashPulse(elapsedSec, 0.75f, 0.52f)
        val flash2 = flashPulse(elapsedSec, 1.75f, 0.36f) * 0.72f
        val flash3 = flashPulse(elapsedSec, 2.75f, 0.45f) * 0.92f
        val flashPeak = maxOf(flash1, maxOf(flash2, flash3))

        // 1. Weltraum-Hintergrund & Nebelschwaden
        fillPaint.shader = spaceBgShader
        canvas.drawRect(0f, 0f, w, h, fillPaint)
        fillPaint.shader = nebulaShaderA
        canvas.drawRect(0f, 0f, w, h, fillPaint)
        fillPaint.shader = nebulaShaderB
        canvas.drawRect(0f, 0f, w, h, fillPaint)
        fillPaint.shader = null

        // 2. Mehrschichtiges Sternenfeld mit langsamer Drift
        for (i in 0 until starCount) {
            val sx = ((starX[i] + elapsedSec * 0.0025f * starR[i]) % 1f) * w
            val sy = starY[i] * h
            val twinkle = 0.55f + 0.45f * sin(elapsedSec * (1.2f + (i % 5) * 0.4f) + starPhase[i])
            val alpha = ((90f + 165f * twinkle) * (0.75f + flashPeak * 0.25f)).toInt().coerceIn(40, 255)
            fillPaint.color = starTint[i]
            fillPaint.alpha = alpha
            canvas.drawCircle(sx, sy, starR[i], fillPaint)
        }
        fillPaint.alpha = 255

        // 3. Monumentaler Gasriese (Jupiter-Sturmbänder + Saturn-Ringe) mit leichtem Zoom-In
        val camScale = 1f + (elapsedSec * 0.014f).coerceAtMost(0.08f)
        val shakeX = if (flashPeak > 0.35f) sin(elapsedSec * 95f) * flashPeak * 7f else 0f
        val shakeY = if (flashPeak > 0.35f) cos(elapsedSec * 82f) * flashPeak * 4f else 0f

        canvas.save()
        canvas.translate(shakeX, shakeY)
        canvas.scale(camScale, camScale, planetCx, planetCy)

        // Hinterer Ringbogen (hinter dem Planeten)
        drawSaturnRings(canvas, frontHalf = false, flashBoost = flashPeak)

        // Gasriesen-Körper mit gebogenen Jupiter-Sturmbändern & Sturmauge
        drawGasGiantBody(canvas, elapsedSec, flashPeak)

        // Vorderer Ringbogen (vor dem Planeten inkl. Planeten-Schattenwurf)
        drawSaturnRings(canvas, frontHalf = true, flashBoost = flashPeak)

        // Ferne Raumschiff-Silhouette (Nostromo-Vibe) mit rotem Blinklicht
        drawDistantDerelictShip(canvas, elapsedSec)

        canvas.restore()

        // 4. Düsterer Begleitmond links unten
        fillPaint.shader = moonShader
        canvas.drawCircle(moonCx, moonCy, moonR, fillPaint)
        fillPaint.shader = null
        // Dezente Mondkrater im beleuchteten Bereich
        fillPaint.color = Color.argb(50, 12, 16, 24)
        canvas.drawCircle(moonCx - moonR * 0.32f, moonCy - moonR * 0.18f, moonR * 0.16f, fillPaint)
        canvas.drawCircle(moonCx - moonR * 0.08f, moonCy - moonR * 0.42f, moonR * 0.11f, fillPaint)

        // 5. Aufblitzender Titel "B R E E D" + elektrische Korona + Lens-Flare
        drawFlashingTitle(canvas, w, h, elapsedSec, flashPeak)

        // 6. Globale Blitz-Aufhellung + Kino-Vignette + Ein-/Ausblenden
        if (flashPeak > 0.05f) {
            fillPaint.color = Color.argb((flashPeak * 85f).toInt().coerceIn(0, 110), 170, 255, 210)
            canvas.drawRect(0f, 0f, w, h, fillPaint)
        }

        fillPaint.shader = vignetteShader
        canvas.drawRect(0f, 0f, w, h, fillPaint)
        fillPaint.shader = null

        val fadeIn = (elapsedSec / 0.65f).coerceIn(0f, 1f)
        val fadeOut = if (elapsedSec > 4.2f) ((elapsedSec - 4.2f) / 0.55f).coerceIn(0f, 1f) else 0f
        val blackAlpha = maxOf(1f - fadeIn, fadeOut)
        if (blackAlpha > 0.01f) {
            fillPaint.color = Color.argb((blackAlpha * 255f).toInt().coerceIn(0, 255), 0, 0, 0)
            canvas.drawRect(0f, 0f, w, h, fillPaint)
        }

        if (elapsedSec >= 4.75f && !finishDispatched) {
            finishDispatched = true
            running = false
            onSplashFinished?.invoke()
            return
        }

        if (running) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawGasGiantBody(canvas: Canvas, elapsedSec: Float, flashBoost: Float) {
        // Äußerer schwacher Atmosphären-Halo
        fillPaint.color = Color.argb((42 + flashBoost * 45).toInt().coerceIn(0, 140), 115, 165, 195)
        canvas.drawCircle(planetCx, planetCy, planetR * 1.06f, fillPaint)

        canvas.save()
        canvas.clipPath(planetClipPath)

        // Grundfarbe der Gasriesen-Atmosphäre
        fillPaint.color = Color.parseColor("#2A1F1D")
        canvas.drawCircle(planetCx, planetCy, planetR, fillPaint)

        // Schräge Jupiter-Sturmbänder
        canvas.save()
        canvas.rotate(-18f, planetCx, planetCy)
        val bandCount = bandColors.size
        val bandH = (planetR * 2.15f) / bandCount
        val topY = planetCy - planetR * 1.07f
        for (i in 0 until bandCount) {
            fillPaint.color = bandColors[i]
            val y = topY + i * bandH
            bandRect.set(planetCx - planetR * 1.25f, y, planetCx + planetR * 1.25f, y + bandH + 2f)
            canvas.drawRect(bandRect, fillPaint)
        }

        // Wirbelnder "Großer Sturm" (Jupiter-Auge), langsam wandernd
        val stormX = planetCx - planetR * 0.26f + elapsedSec * planetR * 0.012f
        val stormY = planetCy + planetR * 0.16f
        stormRect.set(stormX - planetR * 0.22f, stormY - planetR * 0.10f, stormX + planetR * 0.22f, stormY + planetR * 0.10f)
        fillPaint.color = Color.parseColor("#8D422B")
        canvas.drawOval(stormRect, fillPaint)
        stormRect.inset(planetR * 0.055f, planetR * 0.025f)
        fillPaint.color = Color.parseColor("#B86744")
        canvas.drawOval(stormRect, fillPaint)
        canvas.restore()

        // Tiefer Planeten-Schatten (Halb- bis Sichelbeleuchtung für maximale Düsterkeit)
        fillPaint.shader = planetShadowShader
        canvas.drawCircle(planetCx, planetCy, planetR * 1.02f, fillPaint)

        // Atmosphären-Saumlicht
        fillPaint.shader = planetRimShader
        canvas.drawCircle(planetCx, planetCy, planetR, fillPaint)
        fillPaint.shader = null

        canvas.restore()
    }

    private fun drawSaturnRings(canvas: Canvas, frontHalf: Boolean, flashBoost: Float) {
        canvas.save()
        canvas.rotate(-21f, planetCx, planetCy)

        // Halbebene clippen: oben = hinter Planet, unten = vor Planet
        if (frontHalf) {
            canvas.clipRect(
                planetCx - planetR * 2.5f,
                planetCy,
                planetCx + planetR * 2.5f,
                planetCy + planetR * 1.2f
            )
        } else {
            canvas.clipRect(
                planetCx - planetR * 2.5f,
                planetCy - planetR * 1.2f,
                planetCx + planetR * 2.5f,
                planetCy
            )
        }

        // Äußerer schwacher Eisring
        drawSingleRing(canvas, 2.08f, 0.44f, planetR * 0.10f, Color.argb((72 + flashBoost * 40).toInt(), 158, 178, 198))
        // Hauptring (A- & B-Ring mit dunkler Cassini-Lücke dazwischen)
        drawSingleRing(canvas, 1.82f, 0.38f, planetR * 0.14f, Color.argb((120 + flashBoost * 55).toInt(), 188, 174, 152))
        // Innerer dunkler Staubring
        drawSingleRing(canvas, 1.48f, 0.30f, planetR * 0.08f, Color.argb(62, 128, 118, 108))

        // Schatten des Planeten auf der rechten Seite des Rings
        if (frontHalf) {
            fillPaint.color = Color.argb(175, 2, 3, 7)
            canvas.drawRect(
                planetCx + planetR * 0.18f,
                planetCy,
                planetCx + planetR * 2.4f,
                planetCy + planetR * 0.8f,
                fillPaint
            )
        }
        canvas.restore()
    }

    private fun drawSingleRing(canvas: Canvas, rxMul: Float, ryMul: Float, widthPx: Float, color: Int) {
        ringRect.set(
            planetCx - planetR * rxMul,
            planetCy - planetR * ryMul,
            planetCx + planetR * rxMul,
            planetCy + planetR * ryMul
        )
        strokePaint.color = color
        strokePaint.strokeWidth = widthPx
        canvas.drawOval(ringRect, strokePaint)
    }

    private fun drawDistantDerelictShip(canvas: Canvas, elapsedSec: Float) {
        val sx = planetCx - planetR * 1.05f + elapsedSec * 4.5f
        val sy = planetCy + planetR * 0.84f
        fillPaint.color = Color.parseColor("#1A232E")
        canvas.drawRect(sx - 18f, sy - 2.5f, sx + 18f, sy + 2.5f, fillPaint)
        canvas.drawRect(sx - 6f, sy - 6f, sx + 8f, sy + 5f, fillPaint)
        // Blinkendes rotes Positionslicht
        val blink = ((elapsedSec * 1.8f).toInt() % 2 == 0)
        if (blink) {
            fillPaint.color = Color.argb(230, 255, 55, 65)
            canvas.drawCircle(sx + 18f, sy, 2.6f, fillPaint)
        }
    }

    private fun drawFlashingTitle(canvas: Canvas, w: Float, h: Float, elapsedSec: Float, flashPeak: Float) {
        if (elapsedSec < 0.72f) return

        val baseReveal = ((elapsedSec - 0.72f) / 0.25f).coerceIn(0f, 1f)
        // Subtiles defektes Raumschiff-Leuchtröhren-Flackern nach dem Aufblitzen
        val microFlicker = if (sin(elapsedSec * 37f) * sin(elapsedSec * 19f) > 0.82f) 0.55f else 1f
        val glowAlpha = ((baseReveal * 0.65f + flashPeak * 0.75f) * microFlicker).coerceIn(0f, 1f)

        val textSizePx = (w * 0.138f).coerceIn(48f, 180f)
        titlePaint.textSize = textSizePx

        // Horizontale anamorphe Lichtblitz-Linie beim Aufblitzen
        if (flashPeak > 0.04f) {
            fillPaint.shader = titleFlareShader
            val flareHalfH = 2.5f + flashPeak * 18f
            canvas.drawRect(0f, titleY - textSizePx * 0.34f - flareHalfH, w, titleY - textSizePx * 0.34f + flareHalfH, fillPaint)
            fillPaint.shader = null

            // Zitternder elektrischer Lichtbogen über dem Titel
            boltPath.reset()
            val bxStart = w * 0.14f
            val bxEnd = w * 0.86f
            val steps = 10
            val baseY = titleY - textSizePx * 0.36f
            boltPath.moveTo(bxStart, baseY)
            for (s in 1..steps) {
                val frac = s / steps.toFloat()
                val bx = bxStart + (bxEnd - bxStart) * frac
                val jitter = sin(elapsedSec * 70f + s * 2.7f) * flashPeak * 22f * sin(frac * PI.toFloat())
                boltPath.lineTo(bx, baseY + jitter)
            }
            strokePaint.color = Color.argb((flashPeak * 235f).toInt().coerceIn(0, 255), 140, 255, 200)
            strokePaint.strokeWidth = 3.2f
            canvas.drawPath(boltPath, strokePaint)
        }

        // Äußerer düsterer Bio-Glow hinter "BREED"
        titlePaint.style = Paint.Style.STROKE
        titlePaint.strokeWidth = 10f + flashPeak * 14f
        titlePaint.color = Color.argb((glowAlpha * 120f).toInt().coerceIn(0, 200), 42, 255, 136)
        canvas.drawText("BREED", w * 0.5f, titleY, titlePaint)

        // Haupt-Titelkörper: kaltes Metallweiß, blitzt bei Flashes grell auf
        titlePaint.style = Paint.Style.FILL
        val r = (195 + flashPeak * 60).toInt().coerceIn(0, 255)
        val g = (225 + flashPeak * 30).toInt().coerceIn(0, 255)
        val b = (212 + flashPeak * 43).toInt().coerceIn(0, 255)
        titlePaint.color = Color.argb((baseReveal * 255f * microFlicker).toInt().coerceIn(0, 255), r, g, b)
        canvas.drawText("BREED", w * 0.5f, titleY, titlePaint)

        // Scharfe metallische Kante
        titlePaint.style = Paint.Style.STROKE
        titlePaint.strokeWidth = 2f
        titlePaint.color = Color.argb((glowAlpha * 210f).toInt().coerceIn(0, 255), 110, 255, 175)
        canvas.drawText("BREED", w * 0.5f, titleY, titlePaint)

        // Untertitel & Tipp-Hinweis ab 1.35s
        if (elapsedSec >= 1.35f) {
            val subAlpha = (((elapsedSec - 1.35f) / 0.7f).coerceIn(0f, 1f) * 220f).toInt()
            subPaint.textSize = (w * 0.032f).coerceIn(13f, 34f)
            subPaint.color = Color.argb(subAlpha, 125, 185, 170)
            canvas.drawText("ORBITAL HIVE TACTICS", w * 0.5f, titleY + textSizePx * 0.42f, subPaint)

            val promptPulse = (0.45f + 0.55f * (0.5f + 0.5f * sin(elapsedSec * 3.8f))) * (subAlpha / 220f)
            promptPaint.textSize = (w * 0.029f).coerceIn(12f, 28f)
            promptPaint.color = Color.argb((promptPulse * 190f).toInt().coerceIn(0, 220), 175, 215, 200)
            canvas.drawText(GameLocale.tr("TIPPEN ZUM STARTEN", "TAP TO START"), w * 0.5f, h * 0.90f, promptPaint)
        }
    }

    private fun flashPulse(elapsed: Float, triggerAt: Float, decaySec: Float): Float {
        if (elapsed < triggerAt) return 0f
        val dt = elapsed - triggerAt
        if (dt > decaySec * 2.5f) return 0f
        return exp(-dt / (decaySec * 0.35f))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val elapsedSec = if (startTimeMs == 0L) 0f else (SystemClock.uptimeMillis() - startTimeMs) / 1000f
            if (elapsedSec >= 0.35f && !finishDispatched) {
                performClick()
                finishDispatched = true
                running = false
                onSplashFinished?.invoke()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

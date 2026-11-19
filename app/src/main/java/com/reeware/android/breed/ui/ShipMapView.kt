package com.reeware.android.breed.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.reeware.android.breed.engine.*
import com.reeware.android.breed.render.PlanetBackdrop
import kotlin.math.hypot

/**
 * Portrait tactical spaceship map showing conquered, available, locked, and lost rooms
 * orbiting Acheron IV and Kryon VII, inspired by StarCraft sector briefings and Alien hull maps.
 */
class ShipMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var nodes: List<ShipRoomNode> = emptyList()
    private var progress: CampaignProgress? = null
    private var selectedLevelId: String? = null

    var onNodeSelected: ((ShipRoomNode) -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val hullPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(175, 16, 22, 30)
    }
    private val hullBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#354B63")
    }
    private val linkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val nodeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val nodeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private val shipHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#88D4FF")
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }
    private val hullRect = RectF()
    private val nodeDiamond = Path()

    fun setMapData(
        nodes: List<ShipRoomNode>,
        progress: CampaignProgress,
        selectedLevelId: String?
    ) {
        this.nodes = nodes
        this.progress = progress
        this.selectedLevelId = selectedLevelId
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // 1. Deep space backdrop
        bgPaint.color = Color.parseColor("#05080D")
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        for (i in 0 until 45) {
            val sx = ((i * 197 + 41) % 1000) / 1000f * w
            val sy = ((i * 313 + 73) % 1000) / 1000f * h
            starPaint.alpha = 90 + (i * 29) % 150
            canvas.drawCircle(sx, sy, if (i % 5 == 0) 2.2f else 1.3f, starPaint)
        }

        // 2. Orbiting planets behind both ships
        PlanetBackdrop.drawPlanetDisc(
            canvas = canvas,
            cx = w * 0.86f,
            cy = h * 0.16f,
            radius = w * 0.28f,
            theme = PlanetTheme.KRYON_ICE,
            isNight = false
        )
        PlanetBackdrop.drawPlanetDisc(
            canvas = canvas,
            cx = w * 0.14f,
            cy = h * 0.72f,
            radius = w * 0.34f,
            theme = PlanetTheme.ACHERON_ASH,
            isNight = true
        )

        // 3. Biomechanical ship hull silhouettes around the two sectors
        drawShipHull(
            canvas = canvas,
            left = w * 0.08f,
            top = h * 0.03f,
            right = w * 0.92f,
            bottom = h * 0.275f,
            shipLabel = GameLocale.tr("KREUZER KRYON-BASTION · KRYON VII", "CRUISER KRYON-BASTION · KRYON VII"),
            accent = Color.parseColor("#58C4FF")
        )
        drawShipHull(
            canvas = canvas,
            left = w * 0.08f,
            top = h * 0.295f,
            right = w * 0.92f,
            bottom = h * 0.94f,
            shipLabel = GameLocale.tr("FRACHTER NOSTROMO-IX · ACHERON IV", "FREIGHTER NOSTROMO-IX · ACHERON IV"),
            accent = Color.parseColor("#FF884D")
        )

        val prog = progress ?: return
        val byId = nodes.associateBy { it.levelId }

        // 4. Connection corridors
        for (node in nodes) {
            val x1 = nodeX(node, w)
            val y1 = nodeY(node, h)
            val st1 = prog.roomStatuses[node.levelId] ?: RoomStatus.LOCKED
            for (targetId in node.connectedIds) {
                val target = byId[targetId] ?: continue
                val x2 = nodeX(target, w)
                val y2 = nodeY(target, h)
                val st2 = prog.roomStatuses[targetId] ?: RoomStatus.LOCKED

                linkPaint.strokeWidth = 6f
                linkPaint.color = when {
                    st1 == RoomStatus.LOST || st2 == RoomStatus.LOST -> Color.parseColor("#B83B3B")
                    st1 == RoomStatus.CONQUERED && st2 != RoomStatus.LOCKED -> Color.parseColor("#2EE676")
                    st1 == RoomStatus.CONQUERED || st2 == RoomStatus.AVAILABLE -> Color.parseColor("#6C8CA8")
                    else -> Color.parseColor("#24303F")
                }
                canvas.drawLine(x1, y1, x2, y2, linkPaint)
            }
        }

        // 5. Room nodes
        val padRadius = (w * 0.062f).coerceIn(22f, 34f)
        titlePaint.textSize = (w * 0.030f).coerceIn(11f, 24f)
        subPaint.textSize = (w * 0.025f).coerceIn(10f, 20f)

        for (node in nodes) {
            val cx = nodeX(node, w)
            val cy = nodeY(node, h)
            val status = prog.roomStatuses[node.levelId] ?: RoomStatus.LOCKED
            val upgrade = prog.roomUpgrades[node.levelId] ?: RoomUpgrade.NONE
            val isSelected = node.levelId == selectedLevelId

            val fillCol = when (status) {
                RoomStatus.CONQUERED -> Color.parseColor("#133824")
                RoomStatus.AVAILABLE -> Color.parseColor("#382C11")
                RoomStatus.LOST -> Color.parseColor("#471318")
                RoomStatus.LOCKED -> Color.parseColor("#141922")
            }
            val ringCol = when (status) {
                RoomStatus.CONQUERED -> Color.parseColor("#39FF88")
                RoomStatus.AVAILABLE -> Color.parseColor("#FFD24C")
                RoomStatus.LOST -> Color.parseColor("#FF4D4D")
                RoomStatus.LOCKED -> Color.parseColor("#3B4758")
            }
            val statusShort = when (status) {
                RoomStatus.CONQUERED -> if (upgrade != RoomUpgrade.NONE) upgrade.displayName else GameLocale.tr("Erobert", "Owned")
                RoomStatus.AVAILABLE -> "+${node.mutagensReward} Mut"
                RoomStatus.LOST -> GameLocale.tr("VERLOREN!", "LOST!")
                RoomStatus.LOCKED -> GameLocale.tr("Gesperrt", "Locked")
            }

            if (isSelected) {
                nodeStrokePaint.color = Color.WHITE
                nodeStrokePaint.strokeWidth = 4f
                canvas.drawCircle(cx, cy, padRadius + 7f, nodeStrokePaint)
            }

            // Octagonal/diamond biomechanical chamber node
            nodeDiamond.reset()
            nodeDiamond.moveTo(cx, cy - padRadius)
            nodeDiamond.lineTo(cx + padRadius, cy)
            nodeDiamond.lineTo(cx, cy + padRadius)
            nodeDiamond.lineTo(cx - padRadius, cy)
            nodeDiamond.close()

            nodeFillPaint.color = fillCol
            canvas.drawPath(nodeDiamond, nodeFillPaint)

            nodeStrokePaint.color = ringCol
            nodeStrokePaint.strokeWidth = if (status == RoomStatus.LOCKED) 2.5f else 4f
            canvas.drawPath(nodeDiamond, nodeStrokePaint)

            // Center glyph (checkmark / alien eye / lock / exclamation)
            titlePaint.color = ringCol
            val glyph = when (status) {
                RoomStatus.CONQUERED -> "✓"
                RoomStatus.AVAILABLE -> "⚔"
                RoomStatus.LOST -> "!"
                RoomStatus.LOCKED -> "×"
            }
            canvas.drawText(glyph, cx, cy + titlePaint.textSize * 0.35f, titlePaint)

            // Labels underneath
            titlePaint.color = if (status == RoomStatus.LOCKED) Color.parseColor("#7D8A99") else Color.WHITE
            canvas.drawText(node.title, cx, cy + padRadius + titlePaint.textSize * 1.05f, titlePaint)

            subPaint.color = ringCol
            canvas.drawText(statusShort, cx, cy + padRadius + titlePaint.textSize * 1.95f, subPaint)
        }
    }

    private fun drawShipHull(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        shipLabel: String,
        accent: Int
    ) {
        hullRect.set(left, top, right, bottom)
        canvas.drawRoundRect(hullRect, 22f, 22f, hullPaint)
        hullBorderPaint.color = accent
        hullBorderPaint.alpha = 115
        canvas.drawRoundRect(hullRect, 22f, 22f, hullBorderPaint)

        shipHeaderPaint.textSize = ((right - left) * 0.032f).coerceIn(11f, 22f)
        shipHeaderPaint.color = accent
        canvas.drawText(shipLabel, left + 16f, top + shipHeaderPaint.textSize + 8f, shipHeaderPaint)
    }

    private fun nodeX(node: ShipRoomNode, w: Float): Float =
        w * (0.14f + node.mapX * 0.72f)

    private fun nodeY(node: ShipRoomNode, h: Float): Float =
        h * (0.06f + node.mapY * 0.84f)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && width > 0 && height > 0) {
            val w = width.toFloat()
            val h = height.toFloat()
            val hitRadius = (w * 0.11f).coerceAtLeast(48f)
            val tapped = nodes.minByOrNull { node ->
                hypot((event.x - nodeX(node, w)).toDouble(), (event.y - nodeY(node, h)).toDouble())
            }
            if (tapped != null) {
                val dist = hypot((event.x - nodeX(tapped, w)).toDouble(), (event.y - nodeY(tapped, h)).toDouble())
                if (dist <= hitRadius) {
                    performClick()
                    onNodeSelected?.invoke(tapped)
                    return true
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

package com.reeware.android.breed.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Shared StarCraft-inspired tactical HUD styling and safe haptic helper for BREED. */
object UiTheme {

    val BG_VOID = Color.parseColor("#030508")
    val BG_PANEL = Color.parseColor("#0A1017")
    val BG_CARD = Color.parseColor("#111924")
    val BORDER_STEEL = Color.parseColor("#264154")
    val ACCENT_ALIEN = Color.parseColor("#39FF88")
    val ACCENT_AMBER = Color.parseColor("#FFCC44")
    val ACCENT_ALERT = Color.parseColor("#FF4D5A")
    val ACCENT_CYAN = Color.parseColor("#44D9FF")
    val TEXT_PRIMARY = Color.parseColor("#EAF2FA")
    val TEXT_MUTED = Color.parseColor("#93A8BE")

    fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()

    fun panelDrawable(
        fillColor: Int = BG_PANEL,
        strokeColor: Int = BORDER_STEEL,
        strokePx: Int = 2,
        cornerPx: Float = 10f
    ): GradientDrawable = GradientDrawable().apply {
        setColor(fillColor)
        cornerRadius = cornerPx
        setStroke(strokePx, strokeColor)
    }

    fun makeHudButton(
        context: Context,
        label: String,
        fillColor: Int = BG_CARD,
        strokeColor: Int = BORDER_STEEL,
        textColor: Int = TEXT_PRIMARY,
        textSizeSp: Float = 11f,
        onClick: () -> Unit
    ): Button = Button(context).apply {
        text = label
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        isAllCaps = false
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        val padH = dp(context, 7)
        val padV = dp(context, 5)
        setPadding(padH, padV, padH, padV)
        background = panelDrawable(fillColor, strokeColor, dp(context, 1), dp(context, 6).toFloat())
        setOnClickListener { onClick() }
    }

    fun makeBadgeView(
        context: Context,
        textColor: Int = TEXT_PRIMARY,
        textSizeSp: Float = 11f
    ): TextView = TextView(context).apply {
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        gravity = Gravity.CENTER
        val pH = dp(context, 6)
        val pV = dp(context, 4)
        setPadding(pH, pV, pH, pV)
        background = panelDrawable(BG_CARD, BORDER_STEEL, 2, dp(context, 6).toFloat())
    }

    fun styleToggleButton(
        button: Button,
        selected: Boolean,
        accentColor: Int,
        enabled: Boolean = true
    ) {
        button.isEnabled = enabled
        val bg = when {
            !enabled -> Color.parseColor("#11151B")
            selected -> Color.parseColor("#1C2C36")
            else -> BG_CARD
        }
        val border = when {
            !enabled -> Color.parseColor("#242D38")
            selected -> accentColor
            else -> BORDER_STEEL
        }
        button.setTextColor(
            when {
                !enabled -> Color.parseColor("#5C6A79")
                selected -> accentColor
                else -> TEXT_PRIMARY
            }
        )
        button.background = panelDrawable(bg, border, if (selected) 4 else 2, 14f)
    }

    fun rowLayoutParams(weight: Float = 0f, widthPx: Int = ViewGroup.LayoutParams.WRAP_CONTENT, marginDp: Int = 2, context: Context): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(
            if (weight > 0f) 0 else widthPx,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            weight
        )
        val m = dp(context, marginDp)
        lp.setMargins(m, m, m, m)
        return lp
    }

    fun buzz(context: Context, durationMs: Long = 24L) {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        }
    }

    fun setVisible(view: View, visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }
}

package com.reeware.android.breed.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.reeware.android.breed.BuildConfig
import com.reeware.android.breed.audio.AlienSoundEngine
import com.reeware.android.breed.billing.BillingManager
import com.reeware.android.breed.engine.*

/**
 * Zweisprachiger In-App-Shop für BREED:
 * Ermöglicht optionale Einmalkäufe über Google Play Billing für neue Elite-Aliens
 * (Klingen-Bestie, Sporen-Bomber) und dauerhafte Bio-Waffen (Plasma-Giftdrüsen, Sensen-Chitin-Panzer).
 */
object ShopDialog {

    fun show(
        activity: Activity,
        billingManager: BillingManager,
        getOwnedProducts: () -> Set<String>,
        getEquippedWeapons: () -> Set<BioWeapon>,
        onToggleWeapon: (BioWeapon) -> Unit,
        onRefresh: () -> Unit
    ) {
        val scroll = ScrollView(activity).apply {
            setBackgroundColor(UiTheme.BG_VOID)
            setPadding(22, 16, 22, 16)
        }
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        scroll.addView(container)

        lateinit var dialog: AlertDialog

        fun rebuild() {
            container.removeAllViews()
            val owned = getOwnedProducts()
            val equipped = getEquippedWeapons()

            container.addView(TextView(activity).apply {
                text = GameLocale.tr(
                    "BREED ist komplett kostenlos spielbar. Hier kannst du optional neue Elite-Aliens und Bio-Waffen über Google Play freischalten:",
                    "BREED is completely free to play. Optionally unlock new Elite Aliens and Bio-Weapons via Google Play:"
                )
                setTextColor(UiTheme.TEXT_MUTED)
                textSize = 12f
                setPadding(4, 0, 4, 12)
            })

            if (BuildConfig.DEBUG && !billingManager.isPlayStoreReady()) {
                container.addView(TextView(activity).apply {
                    text = GameLocale.tr(
                        "[Debug-Vorschau] Play Billing offline – Klick simuliert einen Testkauf.",
                        "[Debug Preview] Play Billing offline – tap simulates a test purchase."
                    )
                    setTextColor(UiTheme.ACCENT_AMBER)
                    textSize = 11f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    setPadding(4, 0, 4, 12)
                })
            }

            // Ausgerüstete Bio-Waffen Sektion (falls mindestens 1 Waffe freigeschaltet)
            val unlockedWeapons = BioWeapon.entries.filter { ShopProduct.isWeaponUnlocked(it, owned) }
            if (unlockedWeapons.isNotEmpty()) {
                container.addView(TextView(activity).apply {
                    text = GameLocale.tr("DEIN BIO-WAFFEN-ARSENAL", "YOUR BIO-WEAPON ARSENAL")
                    setTextColor(UiTheme.ACCENT_ALIEN)
                    textSize = 12.5f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    setPadding(4, 4, 4, 8)
                })
                for (w in unlockedWeapons) {
                    val isOn = w in equipped
                    val row = LinearLayout(activity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(14, 10, 14, 10)
                        background = GradientDrawable().apply {
                            setColor(UiTheme.BG_CARD)
                            cornerRadius = 12f
                            setStroke(2, if (isOn) UiTheme.ACCENT_ALIEN else UiTheme.BORDER_STEEL)
                        }
                        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        lp.bottomMargin = 10
                        layoutParams = lp
                    }
                    val info = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    info.addView(TextView(activity).apply {
                        text = w.title
                        setTextColor(Color.WHITE)
                        textSize = 13f
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    })
                    info.addView(TextView(activity).apply {
                        text = w.bonusHint
                        setTextColor(UiTheme.TEXT_MUTED)
                        textSize = 11f
                    })
                    row.addView(info)

                    val toggleBtn = Button(activity).apply {
                        text = if (isOn) GameLocale.tr("AKTIV ✓", "ACTIVE ✓") else GameLocale.tr("AUSRÜSTEN", "EQUIP")
                        textSize = 11f
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                        setTextColor(if (isOn) Color.parseColor("#06140B") else Color.WHITE)
                        setPadding(16, 8, 16, 8)
                        background = GradientDrawable().apply {
                            cornerRadius = 10f
                            if (isOn) setColor(UiTheme.ACCENT_ALIEN)
                            else {
                                setColor(UiTheme.BG_PANEL)
                                setStroke(2, UiTheme.ACCENT_CYAN)
                            }
                        }
                        setOnClickListener {
                            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.CONSOLE)
                            onToggleWeapon(w)
                            rebuild()
                            onRefresh()
                        }
                    }
                    row.addView(toggleBtn)
                    container.addView(row)
                }
            }

            // Alle Shop-Produkte (Bundle zuerst, dann Aliens, dann Waffen)
            val displayOrder = listOf(
                ShopProduct.APEX_BUNDLE,
                ShopProduct.ALIEN_REAPER,
                ShopProduct.ALIEN_BOMBER,
                ShopProduct.WEAPON_PLASMA_VENOM,
                ShopProduct.WEAPON_CHITIN_BLADES
            )

            for (product in displayOrder) {
                val isOwned = ShopProduct.isProductOwned(product, owned)
                val accent = when (product.category) {
                    ShopCategory.BUNDLE -> UiTheme.ACCENT_AMBER
                    ShopCategory.ALIEN -> Color.parseColor("#FF335E")
                    ShopCategory.WEAPON -> UiTheme.ACCENT_CYAN
                }

                val card = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(16, 12, 16, 12)
                    background = GradientDrawable().apply {
                        setColor(UiTheme.BG_CARD)
                        cornerRadius = 14f
                        setStroke(if (product.category == ShopCategory.BUNDLE) 3 else 2, if (isOwned) UiTheme.ACCENT_ALIEN else accent)
                    }
                    val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp.bottomMargin = 12
                    layoutParams = lp
                }

                val info = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                info.addView(TextView(activity).apply {
                    text = product.title
                    setTextColor(if (isOwned) UiTheme.ACCENT_ALIEN else accent)
                    textSize = 13.5f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                })
                info.addView(TextView(activity).apply {
                    text = product.description
                    setTextColor(UiTheme.TEXT_PRIMARY)
                    textSize = 11.5f
                    setPadding(0, 4, 8, 0)
                })
                card.addView(info)

                val buyBtn = Button(activity).apply {
                    text = if (isOwned) {
                        GameLocale.tr("BESITZT ✓", "OWNED ✓")
                    } else {
                        billingManager.formattedPrice(product)
                    }
                    isEnabled = !isOwned
                    textSize = 11.5f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    setTextColor(if (isOwned) Color.parseColor("#06140B") else Color.WHITE)
                    setPadding(18, 10, 18, 10)
                    background = GradientDrawable().apply {
                        cornerRadius = 10f
                        if (isOwned) {
                            setColor(UiTheme.ACCENT_ALIEN)
                        } else {
                            setColor(Color.parseColor("#1A2634"))
                            setStroke(2, accent)
                        }
                    }
                    setOnClickListener {
                        AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
                        billingManager.launchPurchase(activity, product) { errMsg ->
                            Toast.makeText(activity, errMsg, Toast.LENGTH_LONG).show()
                        }
                        if (BuildConfig.DEBUG && !billingManager.isPlayStoreReady()) {
                            rebuild()
                            onRefresh()
                        }
                    }
                }
                card.addView(buyBtn)
                container.addView(card)
            }
        }

        rebuild()

        dialog = AlertDialog.Builder(activity)
            .setTitle(GameLocale.tr("Brut-Shop · Elite-Aliens & Bio-Waffen", "Brood Shop · Elite Aliens & Bio-Weapons"))
            .setView(scroll)
            .setNeutralButton(GameLocale.tr("Käufe wiederherstellen", "Restore Purchases")) { _, _ ->
                billingManager.restorePurchases { feedback ->
                    Toast.makeText(activity, feedback, Toast.LENGTH_LONG).show()
                    onRefresh()
                }
            }
            .setPositiveButton(GameLocale.tr("Schließen", "Close"), null)
            .create()
        dialog.show()
    }
}

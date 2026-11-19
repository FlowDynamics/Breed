package com.reeware.android.breed.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.reeware.android.breed.engine.*

/**
 * Zeigt alle festen Art-Mutations-Bäume (5 Basis-Arten + 2 optionale Shop-Elite-Arten;
 * strikt ohne Arten-Kreuzung) zweisprachig (DE & EN) an.
 */
object MutationsDialog {

    private const val ALPHA_COST = 40

    fun show(
        context: Context,
        getProgress: () -> CampaignProgress,
        ownedProductIds: Set<String> = emptySet(),
        onOpenShop: (() -> Unit)? = null,
        onUpdateProgress: (CampaignProgress) -> Unit
    ) {
        val scroll = ScrollView(context).apply {
            setBackgroundColor(UiTheme.BG_VOID)
            setPadding(24, 18, 24, 18)
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        scroll.addView(container)

        lateinit var dialog: AlertDialog

        fun rebuildContent() {
            container.removeAllViews()
            val progress = getProgress()

            container.addView(TextView(context).apply {
                text = GameLocale.tr(
                    "MUTAGENE VERFÜGBAR: ${progress.mutagens}",
                    "MUTAGENS AVAILABLE: ${progress.mutagens}"
                )
                setTextColor(UiTheme.ACCENT_ALIEN)
                textSize = 16f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                setPadding(4, 0, 4, 10)
            })

            container.addView(TextView(context).apply {
                text = GameLocale.tr(
                    "Jede Alien-Art besitzt ihren eigenen, festen Mutations-Baum (keine Arten-Kreuzung).",
                    "Each alien species has its own fixed mutation tree (no cross-breeding)."
                )
                setTextColor(UiTheme.TEXT_MUTED)
                textSize = 12f
                setPadding(4, 0, 4, 16)
            })

            for (species in AlienSpecies.entries) {
                val speciesUnlocked = ShopProduct.isSpeciesUnlocked(species, ownedProductIds)
                val accent = species.accentColorHex.toInt()
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(18, 14, 18, 14)
                    background = GradientDrawable().apply {
                        setColor(UiTheme.BG_CARD)
                        cornerRadius = 16f
                        setStroke(3, if (speciesUnlocked) accent else UiTheme.BORDER_STEEL)
                    }
                    val lp = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    lp.bottomMargin = 16
                    layoutParams = lp
                }

                card.addView(TextView(context).apply {
                    val lockSuffix = if (speciesUnlocked) "" else GameLocale.tr(" [SHOP 🔒]", " [SHOP 🔒]")
                    text = "${species.displayName.uppercase()} · ${species.roleTag}$lockSuffix"
                    setTextColor(accent)
                    textSize = 14f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                })
                card.addView(TextView(context).apply {
                    val ventTxt = if (species.canUseVent) GameLocale.tr(" · Vent-fähig", " · Vent-capable") else ""
                    text = GameLocale.tr(
                        "Basis: HP ${species.baseHp} · ANG ${species.baseAttack} · RÜS ${species.baseArmor} · RW ${species.attackRange}$ventTxt",
                        "Base: HP ${species.baseHp} · ATK ${species.baseAttack} · ARM ${species.baseArmor} · RNG ${species.attackRange}$ventTxt"
                    )
                    setTextColor(UiTheme.TEXT_PRIMARY)
                    textSize = 11f
                    setPadding(0, 4, 0, 10)
                })

                if (!speciesUnlocked) {
                    card.addView(Button(context).apply {
                        text = GameLocale.tr("Im Shop freischalten →", "Unlock in Shop →")
                        textSize = 11.5f
                        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                        setTextColor(UiTheme.ACCENT_AMBER)
                        background = UiTheme.panelDrawable(UiTheme.BG_PANEL, UiTheme.ACCENT_AMBER, 2, 10f)
                        setOnClickListener {
                            dialog.dismiss()
                            onOpenShop?.invoke()
                        }
                    })
                    container.addView(card)
                    continue
                }

                // Alpha-Form
                val alphaUnlocked = species in progress.alphaUnlockedSpecies
                card.addView(
                    buildUnlockRow(
                        context = context,
                        title = GameLocale.tr("Alpha-Form (${species.displayName})", "Alpha Stage (${species.displayName})"),
                        desc = GameLocale.tr(
                            "Schaltet die Elite-Alpha-Stufe frei (+3 HP, +2 Angriff, +1 Kosten).",
                            "Unlocks the elite Alpha stage (+3 HP, +2 Attack, +1 cost)."
                        ),
                        unlocked = alphaUnlocked,
                        cost = ALPHA_COST,
                        canAfford = progress.mutagens >= ALPHA_COST,
                        accent = accent
                    ) {
                        getProgress().unlockAlpha(species, ALPHA_COST)?.let { updated ->
                            onUpdateProgress(updated)
                            rebuildContent()
                        }
                    }
                )

                // Art-spezifische Mutationsknoten
                val speciesNodes = MutationNode.entries.filter { it.species == species }
                for (node in speciesNodes) {
                    val unlocked = node in progress.unlockedMutations
                    card.addView(
                        buildUnlockRow(
                            context = context,
                            title = node.title,
                            desc = node.description,
                            unlocked = unlocked,
                            cost = node.costMutagens,
                            canAfford = progress.mutagens >= node.costMutagens,
                            accent = accent
                        ) {
                            getProgress().unlockMutation(node)?.let { updated ->
                                onUpdateProgress(updated)
                                rebuildContent()
                            }
                        }
                    )
                }

                container.addView(card)
            }
        }

        rebuildContent()

        dialog = AlertDialog.Builder(context)
            .setTitle(GameLocale.tr("Brut-Genetik & Feste Mutations-Bäume", "Brood Genetics & Fixed Mutation Trees"))
            .setView(scroll)
            .setPositiveButton(GameLocale.tr("Schließen", "Close"), null)
            .create()
        dialog.show()
    }

    private fun buildUnlockRow(
        context: Context,
        title: String,
        desc: String,
        unlocked: Boolean,
        cost: Int,
        canAfford: Boolean,
        accent: Int,
        onUnlock: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 10, 12, 10)
            background = GradientDrawable().apply {
                setColor(UiTheme.BG_PANEL)
                cornerRadius = 10f
                setStroke(1, UiTheme.BORDER_STEEL)
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 8
            layoutParams = lp
        }

        val info = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        info.addView(TextView(context).apply {
            text = title
            setTextColor(if (unlocked) UiTheme.ACCENT_ALIEN else Color.WHITE)
            textSize = 13f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        })
        info.addView(TextView(context).apply {
            text = desc
            setTextColor(UiTheme.TEXT_MUTED)
            textSize = 11f
        })
        row.addView(info)

        val btn = Button(context).apply {
            text = if (unlocked) GameLocale.tr("AKTIV ✓", "ACTIVE ✓") else "$cost Mut"
            isEnabled = !unlocked && canAfford
            textSize = 11f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(if (unlocked) Color.parseColor("#07150C") else Color.WHITE)
            setPadding(18, 10, 18, 10)
            background = GradientDrawable().apply {
                cornerRadius = 10f
                when {
                    unlocked -> setColor(UiTheme.ACCENT_ALIEN)
                    canAfford -> {
                        setColor(Color.parseColor("#1D2A38"))
                        setStroke(2, accent)
                    }
                    else -> {
                        setColor(Color.parseColor("#171C23"))
                        setStroke(1, Color.parseColor("#2E3845"))
                    }
                }
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.leftMargin = 10
            layoutParams = lp
            setOnClickListener { onUnlock() }
        }
        row.addView(btn)
        return row
    }
}

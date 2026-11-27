package com.reeware.android.breed.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.reeware.android.breed.audio.AlienSoundEngine
import com.reeware.android.breed.billing.BillingManager
import com.reeware.android.breed.data.BuiltInCampaign
import com.reeware.android.breed.data.FlowCampaign
import com.reeware.android.breed.data.LocalStorage
import com.reeware.android.breed.engine.*
import com.reeware.android.breed.render.HiveFlowView

/**
 * Haupt-Activity von BREED (Echtzeit Hive-Flow im Stil von Colony Flow × Alien/StarCraft):
 * - Schlankes 2-Zeilen-HUD oben (Titel, Wachen-Zähler, Mutagene, Sprache, Sound, Hilfe, Karte, Mut., Shop, Neu)
 * - Vollbild-Echtzeit-Spielfeld (HiveFlowView): Oben das Raumschiff-Zimmer voller Wachen,
 *   in der Mitte 5 Brut-Slots, unten 4 Spalten mit farbigen Alien-Kapseln zum 1-Tipp-Aktivieren!
 * - Umschaltbar auf die interaktive 2-Planeten-Raumschiff-Sektorkarte (ShipMapView).
 */
class MainActivity : Activity() {

    private lateinit var storage: LocalStorage
    private lateinit var billingManager: BillingManager
    private var progress: CampaignProgress = CampaignProgress.initial(BuiltInCampaign.shipNodes)
    private var ownedProductIds: Set<String> = emptySet()
    private var equippedWeapons: Set<BioWeapon> = emptySet()

    private var currentLevelId: String = FlowCampaign.levels.first().id
    private lateinit var flowEngine: FlowGameEngine
    private var isShowingMap: Boolean = false
    private var outcomeHandled: Boolean = false
    private var bannerMessage: String? = null

    private val handler = Handler(Looper.getMainLooper())

    // Top-HUD-Views
    private lateinit var titleText: TextView
    private lateinit var guardsBadge: TextView
    private lateinit var mutagensBadge: TextView
    private lateinit var langBtn: Button
    private lateinit var soundBtn: Button
    private lateinit var mapToggleBtn: Button
    private lateinit var mutBtn: Button
    private lateinit var shopBtn: Button
    private lateinit var eliteStrikeBtn: Button
    private lateinit var restartBtn: Button
    private lateinit var hintBanner: TextView

    // Haupt-Viewports
    private lateinit var gameContainer: LinearLayout
    private lateinit var flowView: HiveFlowView

    // Schiffskarten-Viewport
    private lateinit var mapContainer: LinearLayout
    private lateinit var shipMapView: ShipMapView
    private var selectedMapNodeId: String = BuiltInCampaign.shipNodes.first().levelId
    private lateinit var mapRoomTitle: TextView
    private lateinit var mapRoomMeta: TextView
    private lateinit var mapAttackBtn: Button
    private lateinit var mapUpgradeBtn: Button
    private lateinit var mapEditorBtn: Button
    private lateinit var mapResetBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = LocalStorage(this)
        GameLocale.applyPreference(storage.loadLanguageCode())
        progress = storage.loadCampaignProgress(BuiltInCampaign.shipNodes)
        ownedProductIds = storage.loadOwnedProductIds()
        equippedWeapons = storage.loadEquippedWeapons()
        AlienSoundEngine.setMuted(storage.isSoundMuted())

        billingManager = BillingManager(this, storage) { updatedOwned, feedbackMsg ->
            ownedProductIds = updatedOwned
            equippedWeapons = storage.loadEquippedWeapons()
            if (!feedbackMsg.isNullOrBlank()) {
                AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.VICTORY)
                Toast.makeText(this, feedbackMsg, Toast.LENGTH_LONG).show()
            }
            // Starte aktuellen Raum mit neuen Elite-Ladungen / Bio-Waffen neu, falls noch nicht gespielt
            if (flowEngine.remainingGuards == flowEngine.totalGuards) {
                startFlowLevel(currentLevelId)
            } else {
                refreshHud()
            }
        }
        billingManager.startConnection()

        buildUi()
        val initialRoomId = intent?.getStringExtra(EXTRA_LEVEL_ID)
            ?: BuiltInCampaign.shipNodes.firstOrNull {
                val st = progress.roomStatuses[it.levelId]
                st == RoomStatus.LOST || st == RoomStatus.AVAILABLE
            }?.levelId
            ?: FlowCampaign.levels.first().id
        startFlowLevel(initialRoomId)
    }

    override fun onResume() {
        super.onResume()
        GameLocale.applyPreference(storage.loadLanguageCode())
        progress = storage.loadCampaignProgress(BuiltInCampaign.shipNodes)
        ownedProductIds = storage.loadOwnedProductIds()
        equippedWeapons = storage.loadEquippedWeapons()
        AlienSoundEngine.setMuted(storage.isSoundMuted())
        AlienSoundEngine.acquire()
        syncAudioMode()
        refreshHud()
    }

    override fun onPause() {
        super.onPause()
        AlienSoundEngine.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        billingManager.endConnection()
    }

    private fun syncAudioMode() {
        val lvl = FlowCampaign.levelById(currentLevelId)
        if (isShowingMap) {
            AlienSoundEngine.setMode(AlienSoundEngine.SoundMode.MAP, isNight = true)
        } else {
            AlienSoundEngine.setMode(AlienSoundEngine.SoundMode.COMBAT, isNight = lvl.isNight)
        }
    }

    private fun startFlowLevel(levelId: String) {
        currentLevelId = levelId
        selectedMapNodeId = levelId
        isShowingMap = false
        outcomeHandled = false
        bannerMessage = null

        val spec = FlowCampaign.levelById(levelId)
        val activeWeapons = equippedWeapons.filter { ShopProduct.isWeaponUnlocked(it, ownedProductIds) }.toSet()
        flowEngine = FlowGameEngine(
            level = spec,
            unlockedMutations = progress.unlockedMutations,
            equippedWeapons = activeWeapons,
            ownedProductIds = ownedProductIds
        )
        flowView.bindEngine(flowEngine)
        syncAudioMode()
        refreshHud()
    }

    private fun openShopDialog() {
        ShopDialog.show(
            activity = this,
            billingManager = billingManager,
            getOwnedProducts = { ownedProductIds },
            getEquippedWeapons = { equippedWeapons },
            onToggleWeapon = { weapon ->
                val updated = equippedWeapons.toMutableSet()
                if (weapon in updated) updated.remove(weapon) else updated.add(weapon)
                equippedWeapons = updated
                storage.saveEquippedWeapons(updated)
            },
            onRefresh = {
                ownedProductIds = storage.loadOwnedProductIds()
                equippedWeapons = storage.loadEquippedWeapons()
                if (flowEngine.remainingGuards == flowEngine.totalGuards) {
                    startFlowLevel(currentLevelId)
                } else {
                    refreshHud()
                }
            }
        )
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiTheme.BG_VOID)
            fitsSystemWindows = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        root.addView(buildTopHud())

        val viewport = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        gameContainer = buildGameContainer()
        mapContainer = buildMapContainer()
        viewport.addView(gameContainer)
        viewport.addView(mapContainer)
        root.addView(viewport)

        setContentView(root)
    }

    private fun buildTopHud(): LinearLayout {
        val hud = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiTheme.BG_PANEL)
            val p = UiTheme.dp(this@MainActivity, 5)
            setPadding(p, p, p, p)
        }

        // Zeile 1: Raumtitel + Wachen-Rest + Mutagene + Sprache + Sound + Hilfe
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        titleText = TextView(this).apply {
            setTextColor(UiTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topRow.addView(titleText)

        guardsBadge = UiTheme.makeBadgeView(this, UiTheme.ACCENT_ALERT, 10.5f).apply {
            layoutParams = UiTheme.rowLayoutParams(marginDp = 2, context = this@MainActivity)
        }
        topRow.addView(guardsBadge)

        mutagensBadge = UiTheme.makeBadgeView(this, UiTheme.ACCENT_ALIEN, 10.5f).apply {
            layoutParams = UiTheme.rowLayoutParams(marginDp = 2, context = this@MainActivity)
        }
        topRow.addView(mutagensBadge)

        langBtn = UiTheme.makeHudButton(this, "DE", textColor = UiTheme.ACCENT_CYAN, textSizeSp = 10.5f) {
            val next = GameLocale.toggle()
            storage.saveLanguageCode(next.code)
            bannerMessage = null
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            refreshHud()
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(marginDp = 2, context = this@MainActivity)
        }
        topRow.addView(langBtn)

        soundBtn = UiTheme.makeHudButton(this, "🔊", textSizeSp = 11f) {
            val nextMute = !AlienSoundEngine.isMuted()
            AlienSoundEngine.setMuted(nextMute)
            storage.setSoundMuted(nextMute)
            if (!nextMute) AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            refreshHud()
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(marginDp = 2, context = this@MainActivity)
        }
        topRow.addView(soundBtn)

        topRow.addView(UiTheme.makeHudButton(this, "?") {
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            showHelpDialog()
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(marginDp = 2, context = this@MainActivity)
        })
        hud.addView(topRow)

        // Zeile 2: Karte, Mutationen, Shop, optional Elite-Schlag, Neustart
        val navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = UiTheme.dp(this@MainActivity, 3)
            layoutParams = lp
        }

        mapToggleBtn = UiTheme.makeHudButton(this, "Karte", textSizeSp = 10.5f) {
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            isShowingMap = !isShowingMap
            syncAudioMode()
            refreshHud()
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 1f, marginDp = 2, context = this@MainActivity)
        }
        navRow.addView(mapToggleBtn)

        mutBtn = UiTheme.makeHudButton(this, "Mut.", textSizeSp = 10.5f) {
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            MutationsDialog.show(
                context = this,
                getProgress = { progress },
                ownedProductIds = ownedProductIds,
                onOpenShop = { openShopDialog() },
                onUpdateProgress = { updated ->
                    progress = updated
                    storage.saveCampaignProgress(progress)
                    AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.HATCH)
                    refreshHud()
                }
            )
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 0.85f, marginDp = 2, context = this@MainActivity)
        }
        navRow.addView(mutBtn)

        shopBtn = UiTheme.makeHudButton(
            this,
            "★ Shop",
            fillColor = Color.parseColor("#241C0F"),
            strokeColor = UiTheme.ACCENT_AMBER,
            textColor = UiTheme.ACCENT_AMBER,
            textSizeSp = 10.5f
        ) {
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            openShopDialog()
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 1f, marginDp = 2, context = this@MainActivity)
        }
        navRow.addView(shopBtn)

        eliteStrikeBtn = UiTheme.makeHudButton(
            this,
            "⚡ Elite",
            fillColor = Color.parseColor("#2B111C"),
            strokeColor = Color.parseColor("#FF335E"),
            textColor = Color.parseColor("#FF6B8A"),
            textSizeSp = 10.5f
        ) {
            if (flowEngine.triggerEliteStrike()) {
                bannerMessage = GameLocale.tr(
                    "⚡ Elite-Schwarm stürmt direkt die Front-Wachen!",
                    "⚡ Elite Swarm charges straight into front guards!"
                )
                UiTheme.buzz(this, 32L)
                refreshHud()
            }
        }.apply {
            visibility = View.GONE
            layoutParams = UiTheme.rowLayoutParams(weight = 1.05f, marginDp = 2, context = this@MainActivity)
        }
        navRow.addView(eliteStrikeBtn)

        restartBtn = UiTheme.makeHudButton(this, "↺ Neu", textColor = UiTheme.ACCENT_CYAN, textSizeSp = 10.5f) {
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            UiTheme.buzz(this, 20L)
            startFlowLevel(currentLevelId)
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 0.85f, marginDp = 2, context = this@MainActivity)
        }
        navRow.addView(restartBtn)

        hud.addView(navRow)
        return hud
    }

    private fun buildGameContainer(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        hintBanner = TextView(this).apply {
            setTextColor(UiTheme.TEXT_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            maxLines = 2
            gravity = Gravity.CENTER_HORIZONTAL
            val pH = UiTheme.dp(this@MainActivity, 10)
            val pV = UiTheme.dp(this@MainActivity, 4)
            setPadding(pH, pV, pH, pV)
            background = UiTheme.panelDrawable(Color.parseColor("#070D14"), UiTheme.BORDER_STEEL, 1, 0f)
        }
        container.addView(hintBanner)

        flowView = HiveFlowView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            onColumnTapped = { _, result -> handleColumnTapFeedback(result) }
            onGuardInspected = { guardType, exposed ->
                val stateTag = if (exposed) {
                    GameLocale.tr("AN DER FRONT!", "EXPOSED AT FRONT!")
                } else {
                    GameLocale.tr("noch gedeckt", "still shielded")
                }
                bannerMessage = GameLocale.tr(
                    "${guardType.displayName} ($stateTag) → Konter: ${guardType.counterSpecies.displayName}",
                    "${guardType.displayName} ($stateTag) → Counter: ${guardType.counterSpecies.displayName}"
                )
                AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
                UiTheme.buzz(this@MainActivity, 14L)
                refreshHud()
            }
            onEngineEvents = { events -> handleFlowEvents(events) }
            onStateTick = {
                refreshHud()
                checkOutcomeDialog()
            }
        }
        container.addView(flowView)
        return container
    }

    private fun buildMapContainer(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        shipMapView = ShipMapView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            onNodeSelected = { node ->
                selectedMapNodeId = node.levelId
                AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
                UiTheme.buzz(this@MainActivity, 18L)
                refreshHud()
            }
        }
        container.addView(shipMapView)

        val detailCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiTheme.panelDrawable(UiTheme.BG_PANEL, UiTheme.BORDER_STEEL, 3, 0f)
            val p = UiTheme.dp(this@MainActivity, 10)
            setPadding(p, p, p, p)
        }

        mapRoomTitle = TextView(this).apply {
            setTextColor(UiTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        mapRoomMeta = TextView(this).apply {
            setTextColor(UiTheme.TEXT_MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = UiTheme.dp(this@MainActivity, 3)
            lp.bottomMargin = UiTheme.dp(this@MainActivity, 6)
            layoutParams = lp
        }
        detailCard.addView(mapRoomTitle)
        detailCard.addView(mapRoomMeta)

        val actionsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        mapAttackBtn = UiTheme.makeHudButton(
            this,
            "ATTACK",
            Color.parseColor("#143221"),
            UiTheme.ACCENT_ALIEN,
            UiTheme.ACCENT_ALIEN,
            12f
        ) {
            if (progress.isRoomPlayable(selectedMapNodeId)) {
                AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.CONSOLE)
                startFlowLevel(selectedMapNodeId)
            }
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 1.25f, marginDp = 2, context = this@MainActivity)
        }
        actionsRow.addView(mapAttackBtn)

        mapUpgradeBtn = UiTheme.makeHudButton(this, "Upgrade", textSizeSp = 11f) {
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            val node = BuiltInCampaign.shipNodes.firstOrNull { it.levelId == selectedMapNodeId } ?: return@makeHudButton
            showRoomUpgradeDialog(node)
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 0.85f, marginDp = 2, context = this@MainActivity)
        }
        actionsRow.addView(mapUpgradeBtn)

        mapEditorBtn = UiTheme.makeHudButton(this, "Editor", textSizeSp = 11f) {
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            startActivity(Intent(this, LevelEditorActivity::class.java))
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 0.75f, marginDp = 2, context = this@MainActivity)
        }
        actionsRow.addView(mapEditorBtn)

        mapResetBtn = UiTheme.makeHudButton(this, "Reset", textColor = UiTheme.ACCENT_ALERT) {
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            confirmResetCampaign()
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 0.65f, marginDp = 2, context = this@MainActivity)
        }
        actionsRow.addView(mapResetBtn)

        detailCard.addView(actionsRow)
        container.addView(detailCard)
        return container
    }

    private fun handleColumnTapFeedback(result: FlowTapResult) {
        when (result) {
            FlowTapResult.DEPLOYED -> {
                bannerMessage = null
                UiTheme.buzz(this, 20L)
                refreshHud()
            }
            FlowTapResult.NO_FREE_SLOTS -> {
                bannerMessage = GameLocale.tr(
                    "Alle 5 Brut-Slots sind voll! Warte bis ein Slot frei wird – oder starte mit '↺ Neu'.",
                    "All 5 hive slots are full! Wait for a slot to clear – or tap '↺ Retry'."
                )
                UiTheme.buzz(this, 36L)
                refreshHud()
            }
            FlowTapResult.LINKED_PARTNER_BLOCKED -> {
                bannerMessage = GameLocale.tr(
                    "Verbundene Doppel-Kapsel: räume zuerst den Block über der Partner-Kapsel frei!",
                    "Linked pair pod: clear the pod above its partner first!"
                )
                UiTheme.buzz(this, 30L)
                refreshHud()
            }
            else -> Unit
        }
    }

    private fun handleFlowEvents(events: List<FlowEventKind>) {
        val played = HashSet<FlowEventKind>()
        for (ev in events) {
            if (!played.add(ev)) continue
            when (ev) {
                FlowEventKind.POD_DEPLOYED -> AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.PLACE_ALIEN)
                FlowEventKind.COMBAT_HIT -> AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.CLAW)
                FlowEventKind.GUARD_KILLED -> AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.ACID)
                FlowEventKind.BARREL_BLAST -> {
                    AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.EXPLOSION)
                    UiTheme.buzz(this, 45L)
                }
                FlowEventKind.SLOT_CLEARED -> AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.CONSOLE)
                FlowEventKind.ELITE_STRIKE -> AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.HATCH)
                FlowEventKind.VICTORY -> AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.VICTORY)
                FlowEventKind.DEADLOCK -> AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.DEFEAT)
                FlowEventKind.ALIEN_SPAWNED -> Unit
            }
        }
    }

    private fun checkOutcomeDialog() {
        if (outcomeHandled || flowEngine.outcome == FlowOutcome.PLAYING) return
        outcomeHandled = true
        val finalOutcome = flowEngine.outcome
        handler.postDelayed({
            if (isFinishing || isShowingMap) return@postDelayed
            if (finalOutcome == FlowOutcome.VICTORY) {
                showVictoryDialog()
            } else if (finalOutcome == FlowOutcome.DEADLOCK) {
                showDeadlockDialog()
            }
        }, 420L)
    }

    private fun showVictoryDialog() {
        UiTheme.buzz(this, 55L)
        val lvl = FlowCampaign.levelById(currentLevelId)
        val nodes = BuiltInCampaign.shipNodes
        val (updated, reward) = progress.applyVictory(nodes, lvl.id)
        progress = updated
        storage.saveCampaignProgress(progress)
        refreshHud()

        val nextPlayable = nodes.firstOrNull { node ->
            node.levelId != lvl.id &&
                nodes.firstOrNull { it.levelId == lvl.id }?.connectedIds?.contains(node.levelId) == true &&
                progress.isRoomPlayable(node.levelId)
        } ?: nodes.firstOrNull { it.levelId != lvl.id && progress.roomStatuses[it.levelId] == RoomStatus.AVAILABLE }

        val builder = AlertDialog.Builder(this)
            .setTitle(GameLocale.tr("SIEG! Zimmer freigeräumt!", "VICTORY! Room Cleared!"))
            .setMessage(
                GameLocale.tr(
                    "${lvl.title} komplett vom Schwarm übernommen!\n\nBelohnung: +$reward Mutagene (Gesamt: ${progress.mutagens}).",
                    "${lvl.title} completely overrun by your brood!\n\nReward: +$reward Mutagens (Total: ${progress.mutagens})."
                )
            )
            .setNegativeButton(GameLocale.tr("Schiffskarte", "Ship Map")) { _, _ ->
                isShowingMap = true
                syncAudioMode()
                refreshHud()
            }
            .setNeutralButton(GameLocale.tr("Nochmal", "Replay")) { _, _ ->
                startFlowLevel(currentLevelId)
            }

        if (nextPlayable != null) {
            builder.setPositiveButton(GameLocale.tr("Weiter: ${nextPlayable.title}", "Next: ${nextPlayable.title}")) { _, _ ->
                startFlowLevel(nextPlayable.levelId)
            }
        } else {
            builder.setPositiveButton("OK", null)
        }
        builder.show()
    }

    private fun showDeadlockDialog() {
        UiTheme.buzz(this, 90L)
        AlertDialog.Builder(this)
            .setTitle(GameLocale.tr("Schwarm blockiert!", "Brood Stalled!"))
            .setMessage(
                GameLocale.tr(
                    "Alle 5 Brut-Slots sind mit Aliens belegt, deren Ziel-Wachen noch hinter anderen Reihen stehen.\n\nTipp: Aktiviere zuerst Alien-Blöcke in den Farben der vordersten Wachen!",
                    "All 5 hive slots are filled with aliens whose matching guards are still shielded behind other rows.\n\nTip: Deploy alien pods matching the front-line guards first!"
                )
            )
            .setPositiveButton(GameLocale.tr("↺ Sofort nochmal", "↺ Try Again")) { _, _ ->
                startFlowLevel(currentLevelId)
            }
            .setNegativeButton(GameLocale.tr("Schiffskarte", "Ship Map")) { _, _ ->
                isShowingMap = true
                syncAudioMode()
                refreshHud()
            }
            .show()
    }

    private fun refreshHud() {
        val lvl = FlowCampaign.levelById(currentLevelId)
        mutagensBadge.text = "Mut:${progress.mutagens}"
        langBtn.text = GameLocale.current.code.uppercase()
        soundBtn.text = if (AlienSoundEngine.isMuted()) "🔇" else "🔊"
        mapToggleBtn.text = if (isShowingMap) GameLocale.tr("Spielen", "Play") else GameLocale.tr("Karte", "Map")
        mutBtn.text = "Mut."
        shopBtn.text = if (equippedWeapons.isNotEmpty()) "★ Shop(${equippedWeapons.size})" else "★ Shop"
        restartBtn.text = GameLocale.tr("↺ Neu", "↺ Retry")

        UiTheme.setVisible(gameContainer, !isShowingMap)
        UiTheme.setVisible(mapContainer, isShowingMap)
        UiTheme.setVisible(guardsBadge, !isShowingMap)
        UiTheme.setVisible(restartBtn, !isShowingMap)

        val showElite = !isShowingMap && flowEngine.eliteSpeciesAvailable.isNotEmpty()
        UiTheme.setVisible(eliteStrikeBtn, showElite)
        if (showElite) {
            eliteStrikeBtn.text = "⚡ Elite(${flowEngine.eliteJokerCharges})"
            eliteStrikeBtn.isEnabled = flowEngine.eliteJokerCharges > 0 && flowEngine.outcome == FlowOutcome.PLAYING
        }

        if (isShowingMap) {
            titleText.text = GameLocale.tr("SCHIFFSKARTE · SEKTOR-ÜBERSICHT", "SHIP MAP · SECTOR OVERVIEW")
            renderMapPanel()
            return
        }

        titleText.text = lvl.title
        guardsBadge.text = GameLocale.tr(
            "Wachen:${flowEngine.remainingGuards}/${flowEngine.totalGuards}",
            "Guards:${flowEngine.remainingGuards}/${flowEngine.totalGuards}"
        )
        hintBanner.text = bannerMessage ?: lvl.hint
        hintBanner.setTextColor(if (bannerMessage != null) UiTheme.ACCENT_AMBER else UiTheme.TEXT_MUTED)
    }

    private fun renderMapPanel() {
        val nodes = BuiltInCampaign.shipNodes
        val selectedNode = nodes.firstOrNull { it.levelId == selectedMapNodeId } ?: nodes.first()
        shipMapView.setMapData(nodes, progress, selectedNode.levelId)
        val status = progress.roomStatuses[selectedNode.levelId] ?: RoomStatus.LOCKED
        val upgrade = progress.roomUpgrades[selectedNode.levelId] ?: RoomUpgrade.NONE

        mapRoomTitle.text = "${selectedNode.title} (${selectedNode.shipName})"
        val bonusText = if (upgrade != RoomUpgrade.NONE) {
            GameLocale.tr(" · Ausbau: ${upgrade.displayName}", " · Upgrade: ${upgrade.displayName}")
        } else ""
        mapRoomMeta.text = GameLocale.tr(
            "Status: ${status.label} · Belohnung: +${selectedNode.mutagensReward} Mutagene$bonusText",
            "Status: ${status.label} · Reward: +${selectedNode.mutagensReward} Mutagens$bonusText"
        )

        mapAttackBtn.isEnabled = status != RoomStatus.LOCKED
        mapAttackBtn.text = when (status) {
            RoomStatus.LOST -> GameLocale.tr("ZURÜCKEROBERN!", "RECLAIM!")
            RoomStatus.CONQUERED -> GameLocale.tr("NOCHMAL SPIELEN", "REPLAY")
            RoomStatus.AVAILABLE -> GameLocale.tr("RAUM STÜRMEN", "RAID ROOM")
            RoomStatus.LOCKED -> GameLocale.tr("GESPERRT", "LOCKED")
        }
        UiTheme.styleToggleButton(
            mapAttackBtn,
            selected = status != RoomStatus.LOCKED,
            accentColor = if (status == RoomStatus.LOST) UiTheme.ACCENT_ALERT else UiTheme.ACCENT_ALIEN,
            enabled = status != RoomStatus.LOCKED
        )

        val canUpgrade = status == RoomStatus.CONQUERED
        mapUpgradeBtn.text = GameLocale.tr("Ausbauen", "Upgrade")
        mapUpgradeBtn.isEnabled = canUpgrade
        UiTheme.styleToggleButton(mapUpgradeBtn, canUpgrade, UiTheme.ACCENT_AMBER, canUpgrade)

        mapEditorBtn.text = "Editor"
        mapResetBtn.text = "Reset"
    }

    private fun showRoomUpgradeDialog(node: ShipRoomNode) {
        if (progress.roomStatuses[node.levelId] != RoomStatus.CONQUERED) return
        val options = RoomUpgrade.entries.filter { it != RoomUpgrade.NONE }
        val labels = options.map { up ->
            val active = progress.roomUpgrades[node.levelId] == up
            if (active) "${up.displayName} [${GameLocale.tr("AKTIV", "ACTIVE")}]"
            else "${up.displayName} (${up.costMutagens} Mut)"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(GameLocale.tr("Ausbau: ${node.title} (Mut: ${progress.mutagens})", "Upgrade: ${node.title} (Mut: ${progress.mutagens})"))
            .setItems(labels) { _, idx ->
                val updated = progress.upgradeRoom(node.levelId, options[idx])
                if (updated != null) {
                    progress = updated
                    storage.saveCampaignProgress(progress)
                    AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.CONSOLE)
                    UiTheme.buzz(this, 26L)
                    refreshHud()
                } else {
                    Toast.makeText(
                        this,
                        GameLocale.tr("Nicht genug Mutagene oder Ausbau aktiv.", "Not enough Mutagens or already active."),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(GameLocale.tr("Abbrechen", "Cancel"), null)
            .show()
    }

    private fun showHelpDialog() {
        val scroll = ScrollView(this).apply { setPadding(28, 16, 28, 16) }
        val body = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            text = GameLocale.tr(
                buildString {
                    append("SO SPIELST DU BREED:\n")
                    append("1. Schau ins Zimmer oben: Welche Wachen stehen hell leuchtend an der vorderen Front?\n")
                    append("2. Tippe unten auf den passenden farbigen Alien-Block – er rutscht in einen der 5 Brut-Slots und schickt deine Aliens sofort in Echtzeit nach oben ins Gefecht!\n")
                    append("3. Räume Schicht für Schicht alle Wachen aus dem Raum.\n\n")
                    append("FARBEN & KONTER:\n")
                    append("• Violett (Lauerer) → Space Marines\n")
                    append("• Grün (Säure-Brut) → Belagerungs-Panzer\n")
                    append("• Orange (Koloss) → Pyro-Wachen\n")
                    append("• Cyan (Netz-Brut) → Auto-Geschütze\n")
                    append("• Magenta (Psyche) → Kommandanten ★\n\n")
                    append("WICHTIG:\n")
                    append("• Du hast genau 5 Brut-Slots! Aktivierst du Aliens, deren Wachen noch hinter anderen Reihen versteckt sind, warten sie im Slot.\n")
                    append("• Säurefässer neben Wachen explodieren automatisch und sprengen verschlossene Schotts auf!")
                },
                buildString {
                    append("HOW TO PLAY BREED:\n")
                    append("1. Look at the ship room above: which guards are glowing on the open front line?\n")
                    append("2. Tap a matching colored alien pod below – it slides into one of your 5 hive slots and unleashes your aliens in real time!\n")
                    append("3. Clear every guard layer by layer to overrun the room.\n\n")
                    append("COLORS & COUNTERS:\n")
                    append("• Purple (Stalker) → Space Marines\n")
                    append("• Green (Acid Spitter) → Siege Tanks\n")
                    append("• Orange (Brute) → Pyro Guards\n")
                    append("• Cyan (Web Weaver) → Auto-Turrets\n")
                    append("• Magenta (Psion) → Commanders ★\n\n")
                    append("KEY RULES:\n")
                    append("• You only have 5 hive slots! Activating aliens whose guards are still blocked behind other rows stalls that slot until the front clears.\n")
                    append("• Acid barrels next to guards explode automatically and blast open armored barriers!")
                }
            )
        }
        scroll.setBackgroundColor(UiTheme.BG_PANEL)
        scroll.addView(body)
        AlertDialog.Builder(this)
            .setTitle(GameLocale.tr("Spielprinzip", "How to Play"))
            .setView(scroll)
            .setPositiveButton(GameLocale.tr("Los geht's!", "Let's go!"), null)
            .show()
    }

    private fun confirmResetCampaign() {
        AlertDialog.Builder(this)
            .setTitle(GameLocale.tr("Kampagne zurücksetzen?", "Reset Campaign?"))
            .setMessage(
                GameLocale.tr(
                    "Alle eroberten Räume, Ausbauten und Mutationen werden zurückgesetzt. Gekaufte Shop-Artikel bleiben erhalten.",
                    "All conquered rooms, upgrades, and mutations will reset. Purchased Shop items remain unlocked."
                )
            )
            .setPositiveButton(GameLocale.tr("Zurücksetzen", "Reset")) { _, _ ->
                progress = storage.resetCampaignProgress(BuiltInCampaign.shipNodes)
                selectedMapNodeId = BuiltInCampaign.shipNodes.first().levelId
                startFlowLevel(FlowCampaign.levels.first().id)
            }
            .setNegativeButton(GameLocale.tr("Abbrechen", "Cancel"), null)
            .show()
    }

    companion object {
        const val EXTRA_LEVEL_ID = "extra_level_id"
        const val EXTRA_TEST_LEVEL_JSON = "extra_test_level_json"
    }
}

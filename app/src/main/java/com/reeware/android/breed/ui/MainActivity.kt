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
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.reeware.android.breed.audio.AlienSoundEngine
import com.reeware.android.breed.billing.BillingManager
import com.reeware.android.breed.data.BuiltInCampaign
import com.reeware.android.breed.data.LevelJsonCodec
import com.reeware.android.breed.data.LocalStorage
import com.reeware.android.breed.engine.*
import com.reeware.android.breed.render.BattleBoardView

/**
 * Haupt-Activity von BREED (Deutsch & Englisch, kostenlos mit optionalen Google-Play-In-App-Käufen):
 * - Top-HUD: Titel, Mutagene, Sprache (DE/EN), Ton, Shop, Karte, Mutationen, Editor, Hilfe
 * - Mitte: Animiertes 60-FPS-Spielfeld (BattleBoardView) ODER Schiffskarte (ShipMapView)
 * - Unten: Phase-1-Dock mit direkter Touch-Steuerung, 2-Reihen-Befehlen, Stufen & Alien-Buttons
 *          sowie Phase-2-Automatik-Wiedergabe.
 */
class MainActivity : Activity() {

    private lateinit var storage: LocalStorage
    private lateinit var billingManager: BillingManager
    private var progress: CampaignProgress = CampaignProgress.initial(BuiltInCampaign.shipNodes)
    private var ownedProductIds: Set<String> = emptySet()
    private var equippedWeapons: Set<BioWeapon> = emptySet()

    private var baseLevel: LevelDefinition = BuiltInCampaign.levels.first()
    private var isEditorTestMode: Boolean = false
    private var isCustomLevelMode: Boolean = false
    private var isShowingMap: Boolean = false

    // Phase-1-Zustand
    private val placements = mutableListOf<AlienPlacement>()
    private var nextUnitNumber = 1
    private var activeSpecies: AlienSpecies = AlienSpecies.STALKER
    private var activeStage: EvolutionStage = EvolutionStage.ADULT
    private var placementArmed: Boolean = true
    private var selectedPlacementId: String? = null
    private var statusMessage: String? = null

    // Phase-2-Wiedergabe-Zustand
    private var simulationResult: SimulationResult? = null
    private var playbackIndex: Int = 0
    private var lastRenderedStep: Int = -1
    private var isAutoPlaying: Boolean = false
    private var speedFast: Boolean = false
    private var outcomeHandled: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private val playbackTick = object : Runnable {
        override fun run() {
            val sim = simulationResult ?: return
            if (!isAutoPlaying) return
            if (playbackIndex < sim.snapshots.lastIndex) {
                playbackIndex++
                renderCurrentState()
                handler.postDelayed(this, currentStepDurationMs())
            } else {
                isAutoPlaying = false
                renderCurrentState()
            }
        }
    }

    // Top-HUD-Views
    private lateinit var titleText: TextView
    private lateinit var mutagensBadge: TextView
    private lateinit var langBtn: Button
    private lateinit var soundBtn: Button
    private lateinit var shopBtn: Button
    private lateinit var mapToggleBtn: Button
    private lateinit var mutBtn: Button
    private lateinit var editorBtn: Button
    private lateinit var dayNightBadge: TextView
    private lateinit var biomassBadge: TextView
    private lateinit var commandBadge: TextView
    private lateinit var roundBadge: TextView
    private lateinit var statusStrip: LinearLayout

    // Haupt-Viewport
    private lateinit var battleContainer: LinearLayout
    private lateinit var boardView: BattleBoardView
    private lateinit var infoBanner: TextView

    // Schiffskarten-Viewport
    private lateinit var mapContainer: LinearLayout
    private lateinit var shipMapView: ShipMapView
    private var selectedMapNodeId: String = BuiltInCampaign.shipNodes.first().levelId
    private lateinit var mapRoomTitle: TextView
    private lateinit var mapRoomMeta: TextView
    private lateinit var mapAttackBtn: Button
    private lateinit var mapUpgradeBtn: Button
    private lateinit var mapCustomBtn: Button
    private lateinit var mapResetBtn: Button

    // Bottom-Docks
    private lateinit var phase1Dock: LinearLayout
    private lateinit var selectedUnitBar: TextView
    private lateinit var undoCmdBtn: Button
    private lateinit var removeCmdBtn: Button
    private lateinit var ventCmdBtn: Button
    private lateinit var consoleCmdBtn: Button
    private lateinit var waitCmdBtn: Button
    private lateinit var hatchCmdBtn: Button
    private lateinit var clearAllBtn: Button
    private val stageButtons = mutableMapOf<EvolutionStage, Button>()
    private val speciesButtons = mutableMapOf<AlienSpecies, Button>()
    private lateinit var startAttackBtn: Button

    private lateinit var phase2Dock: LinearLayout
    private lateinit var phase2Summary: TextView
    private lateinit var playPauseBtn: Button
    private lateinit var speedBtn: Button
    private lateinit var backPhase1Btn: Button

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
            renderCurrentState()
        }
        billingManager.startConnection()

        buildUi()
        handleIncomingIntent(intent)
        renderCurrentState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
        renderCurrentState()
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
        renderCurrentState()
    }

    override fun onPause() {
        super.onPause()
        stopPlaybackLoop()
        AlienSoundEngine.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlaybackLoop()
        billingManager.endConnection()
    }

    private fun currentStepDurationMs(): Long = if (speedFast) 380L else 760L

    private fun syncAudioMode() {
        val lvl = effectiveLevel()
        val sim = simulationResult
        when {
            isShowingMap -> AlienSoundEngine.setMode(AlienSoundEngine.SoundMode.MAP, isNight = true)
            sim != null -> {
                val snap = sim.snapshots[playbackIndex.coerceIn(0, sim.snapshots.lastIndex)]
                AlienSoundEngine.setMode(AlienSoundEngine.SoundMode.COMBAT, isNight = snap.isNight)
            }
            else -> AlienSoundEngine.setMode(AlienSoundEngine.SoundMode.PLANNING, isNight = lvl.dayNight.isNightAtRound(1))
        }
    }

    private fun handleIncomingIntent(incoming: Intent?) {
        val testJson = incoming?.getStringExtra(EXTRA_TEST_LEVEL_JSON)
        if (!testJson.isNullOrBlank()) {
            val decoded = runCatching { LevelJsonCodec.decodeLevel(testJson) }.getOrNull()
            if (decoded != null) {
                loadLevel(decoded, editorTest = true, customLevel = true)
                return
            }
        }
        val requestedRoomId = incoming?.getStringExtra(EXTRA_LEVEL_ID)
        if (!requestedRoomId.isNullOrBlank()) {
            val found = BuiltInCampaign.levels.firstOrNull { it.id == requestedRoomId }
            if (found != null && progress.isRoomPlayable(found.id)) {
                loadLevel(found, editorTest = false, customLevel = false)
                return
            }
        }
        val priorityId = BuiltInCampaign.shipNodes.firstOrNull {
            val st = progress.roomStatuses[it.levelId]
            st == RoomStatus.LOST || st == RoomStatus.AVAILABLE
        }?.levelId ?: BuiltInCampaign.levels.first().id
        loadLevel(BuiltInCampaign.levelById(priorityId), editorTest = false, customLevel = false)
    }

    private fun effectiveLevel(): LevelDefinition {
        if (isEditorTestMode || isCustomLevelMode) return baseLevel
        // Bei Kampagnen-Räumen frisch aus BuiltInCampaign laden, damit Sprachwechsel Titel & Hinweise sofort aktualisiert
        val localized = BuiltInCampaign.levels.firstOrNull { it.id == baseLevel.id } ?: baseLevel
        return localized.copy(
            maxBiomass = localized.maxBiomass + progress.extraBiomassBonus(),
            maxCommands = localized.maxCommands + progress.extraCommandBonus()
        )
    }

    private fun loadLevel(level: LevelDefinition, editorTest: Boolean, customLevel: Boolean) {
        stopPlaybackLoop()
        baseLevel = level
        isEditorTestMode = editorTest
        isCustomLevelMode = customLevel
        isShowingMap = false
        placements.clear()
        nextUnitNumber = 1
        selectedPlacementId = null
        simulationResult = null
        playbackIndex = 0
        lastRenderedStep = -1
        outcomeHandled = false
        placementArmed = true
        statusMessage = null
        BuiltInCampaign.shipNodes.firstOrNull { it.levelId == level.id }?.let { selectedMapNodeId = it.levelId }
        syncAudioMode()
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
                renderCurrentState()
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
        battleContainer = buildBattleContainer()
        mapContainer = buildMapContainer()
        viewport.addView(battleContainer)
        viewport.addView(mapContainer)
        root.addView(viewport)

        phase1Dock = buildPhase1Dock()
        phase2Dock = buildPhase2Dock()
        root.addView(phase1Dock)
        root.addView(phase2Dock)

        setContentView(root)
    }

    private fun buildTopHud(): LinearLayout {
        val hud = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiTheme.BG_PANEL)
            val p = UiTheme.dp(this@MainActivity, 5)
            setPadding(p, p, p, p)
        }

        // Zeile 1: Raumtitel + Mutagene + Sprache + Ton + Hilfe
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

        mutagensBadge = UiTheme.makeBadgeView(this, UiTheme.ACCENT_ALIEN, 10.5f).apply {
            layoutParams = UiTheme.rowLayoutParams(marginDp = 2, context = this@MainActivity)
        }
        topRow.addView(mutagensBadge)

        langBtn = UiTheme.makeHudButton(this, "DE", textColor = UiTheme.ACCENT_CYAN, textSizeSp = 10.5f) {
            val next = GameLocale.toggle()
            storage.saveLanguageCode(next.code)
            statusMessage = null
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            renderCurrentState()
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(marginDp = 2, context = this@MainActivity)
        }
        topRow.addView(langBtn)

        soundBtn = UiTheme.makeHudButton(this, "🔊", textSizeSp = 11f) {
            val nextMute = !AlienSoundEngine.isMuted()
            AlienSoundEngine.setMuted(nextMute)
            storage.setSoundMuted(nextMute)
            if (!nextMute) AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            renderCurrentState()
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(marginDp = 2, context = this@MainActivity)
        }
        topRow.addView(soundBtn)

        topRow.addView(UiTheme.makeHudButton(this, "?") {
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            showLevelHelpDialog()
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(marginDp = 2, context = this@MainActivity)
        })
        hud.addView(topRow)

        // Zeile 2: Navigations-Leiste (Karte, Mutationen, Shop, Editor)
        val navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = UiTheme.dp(this@MainActivity, 3)
            layoutParams = lp
        }

        mapToggleBtn = UiTheme.makeHudButton(this, "Karte", textSizeSp = 10.5f) {
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            isShowingMap = !isShowingMap
            if (isShowingMap) stopPlaybackLoop()
            syncAudioMode()
            renderCurrentState()
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
                    renderCurrentState()
                }
            )
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 1f, marginDp = 2, context = this@MainActivity)
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
            layoutParams = UiTheme.rowLayoutParams(weight = 1.05f, marginDp = 2, context = this@MainActivity)
        }
        navRow.addView(shopBtn)

        editorBtn = UiTheme.makeHudButton(this, "Editor", textSizeSp = 10.5f) {
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            startActivity(Intent(this, LevelEditorActivity::class.java))
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 0.95f, marginDp = 2, context = this@MainActivity)
        }
        navRow.addView(editorBtn)
        hud.addView(navRow)

        // Zeile 3: Gefechts-Status (Tag/Nacht, Biomasse, Befehle, Runden)
        statusStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = UiTheme.dp(this@MainActivity, 3)
            layoutParams = lp
        }
        dayNightBadge = UiTheme.makeBadgeView(this, UiTheme.ACCENT_AMBER, 10.5f).apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 1.25f, marginDp = 2, context = this@MainActivity)
        }
        biomassBadge = UiTheme.makeBadgeView(this, UiTheme.ACCENT_ALIEN, 10.5f).apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 1f, marginDp = 2, context = this@MainActivity)
        }
        commandBadge = UiTheme.makeBadgeView(this, UiTheme.ACCENT_CYAN, 10.5f).apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 1f, marginDp = 2, context = this@MainActivity)
        }
        roundBadge = UiTheme.makeBadgeView(this, UiTheme.TEXT_PRIMARY, 10.5f).apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 0.9f, marginDp = 2, context = this@MainActivity)
        }
        statusStrip.addView(dayNightBadge)
        statusStrip.addView(biomassBadge)
        statusStrip.addView(commandBadge)
        statusStrip.addView(roundBadge)
        hud.addView(statusStrip)

        return hud
    }

    private fun buildBattleContainer(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        boardView = BattleBoardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            onCellTap = { pos -> handleBoardCellTap(pos) }
        }
        container.addView(boardView)

        infoBanner = TextView(this).apply {
            setTextColor(UiTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            maxLines = 2
            val pH = UiTheme.dp(this@MainActivity, 10)
            val pV = UiTheme.dp(this@MainActivity, 5)
            setPadding(pH, pV, pH, pV)
            background = UiTheme.panelDrawable(UiTheme.BG_PANEL, UiTheme.BORDER_STEEL, 2, 0f)
        }
        container.addView(infoBanner)

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
                renderCurrentState()
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
                loadLevel(BuiltInCampaign.levelById(selectedMapNodeId), editorTest = false, customLevel = false)
                renderCurrentState()
            }
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 1.2f, marginDp = 2, context = this@MainActivity)
        }
        actionsRow.addView(mapAttackBtn)

        mapUpgradeBtn = UiTheme.makeHudButton(this, "Upgrade", textSizeSp = 11f) {
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            val node = BuiltInCampaign.shipNodes.firstOrNull { it.levelId == selectedMapNodeId } ?: return@makeHudButton
            showRoomUpgradeDialog(node)
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 0.9f, marginDp = 2, context = this@MainActivity)
        }
        actionsRow.addView(mapUpgradeBtn)

        mapCustomBtn = UiTheme.makeHudButton(this, "Custom") {
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            showCustomLevelsDialog()
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 0.8f, marginDp = 2, context = this@MainActivity)
        }
        actionsRow.addView(mapCustomBtn)

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

    private fun buildPhase1Dock(): LinearLayout {
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiTheme.panelDrawable(UiTheme.BG_PANEL, UiTheme.BORDER_STEEL, 3, 0f)
            val p = UiTheme.dp(this@MainActivity, 5)
            setPadding(p, UiTheme.dp(this@MainActivity, 4), p, p)
        }

        selectedUnitBar = TextView(this).apply {
            setTextColor(UiTheme.ACCENT_CYAN)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            maxLines = 1
            setPadding(UiTheme.dp(this@MainActivity, 4), 0, UiTheme.dp(this@MainActivity, 4), UiTheme.dp(this@MainActivity, 2))
        }
        dock.addView(selectedUnitBar)

        // Befehls-Reihe 1: Richtungstasten + Undo + Entfernen
        val moveRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val dirs = listOf(
            "←" to Direction.WEST,
            "↑" to Direction.NORTH,
            "↓" to Direction.SOUTH,
            "→" to Direction.EAST
        )
        for ((lbl, dir) in dirs) {
            moveRow.addView(UiTheme.makeHudButton(this, lbl, textSizeSp = 12f) {
                appendMoveCommand(dir)
            }.apply {
                layoutParams = UiTheme.rowLayoutParams(weight = 1f, marginDp = 2, context = this@MainActivity)
            })
        }
        undoCmdBtn = UiTheme.makeHudButton(this, "↶", textSizeSp = 11f) { undoLastCommand() }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 1f, marginDp = 2, context = this@MainActivity)
        }
        removeCmdBtn = UiTheme.makeHudButton(this, "✕", textColor = UiTheme.ACCENT_ALERT, textSizeSp = 11f) {
            removeSelectedPlacement()
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 1f, marginDp = 2, context = this@MainActivity)
        }
        moveRow.addView(undoCmdBtn)
        moveRow.addView(removeCmdBtn)
        dock.addView(moveRow)

        // Befehls-Reihe 2: Vent, Schalter, Warten, Schlüpfen, Leeren
        val specialRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        ventCmdBtn = UiTheme.makeHudButton(this, "Vent", textSizeSp = 11f) { appendVentCommand() }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 1f, marginDp = 2, context = this@MainActivity)
        }
        consoleCmdBtn = UiTheme.makeHudButton(this, "Switch", textSizeSp = 11f) { appendConsoleCommand() }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 1.15f, marginDp = 2, context = this@MainActivity)
        }
        waitCmdBtn = UiTheme.makeHudButton(this, "Wait", textSizeSp = 11f) { appendSimpleCommand(AlienCommand.Wait) }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 1f, marginDp = 2, context = this@MainActivity)
        }
        hatchCmdBtn = UiTheme.makeHudButton(this, "Hatch", textSizeSp = 11f) { appendHatchCommand() }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 1.1f, marginDp = 2, context = this@MainActivity)
        }
        clearAllBtn = UiTheme.makeHudButton(this, "Clear", textColor = UiTheme.ACCENT_AMBER, textSizeSp = 10.5f) {
            clearAllPlacements()
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 0.95f, marginDp = 2, context = this@MainActivity)
        }
        specialRow.addView(ventCmdBtn)
        specialRow.addView(consoleCmdBtn)
        specialRow.addView(waitCmdBtn)
        specialRow.addView(hatchCmdBtn)
        specialRow.addView(clearAllBtn)
        dock.addView(specialRow)

        // Reihe 3: Entwicklungsstufen + ANGRIFF ▶
        val stageRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = UiTheme.dp(this@MainActivity, 2)
            layoutParams = lp
        }
        for (stage in EvolutionStage.entries) {
            val btn = UiTheme.makeHudButton(this, stage.displayName, textSizeSp = 10.5f) {
                selectStage(stage)
            }.apply {
                layoutParams = UiTheme.rowLayoutParams(weight = 1f, marginDp = 2, context = this@MainActivity)
            }
            stageButtons[stage] = btn
            stageRow.addView(btn)
        }

        startAttackBtn = UiTheme.makeHudButton(
            this,
            "ATTACK ▶",
            Color.parseColor("#143922"),
            UiTheme.ACCENT_ALIEN,
            UiTheme.ACCENT_ALIEN,
            12f
        ) {
            startPhase2Simulation()
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 1.75f, marginDp = 2, context = this@MainActivity)
        }
        stageRow.addView(startAttackBtn)
        dock.addView(stageRow)

        // Unterste Reihe: 5 kostenlose Alien-Arten + 2 Elite-Shop-Aliens (kompakt horizontal scrollbar)
        val speciesScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = UiTheme.dp(this@MainActivity, 3)
            layoutParams = lp
        }
        val speciesBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnMinW = UiTheme.dp(this, 66)
        for (sp in AlienSpecies.entries) {
            val btn = UiTheme.makeHudButton(this, sp.shortName, textSizeSp = 10.5f) {
                if (!ShopProduct.isSpeciesUnlocked(sp, ownedProductIds)) {
                    AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
                    statusMessage = GameLocale.tr(
                        "${sp.displayName} ist ein optionales Elite-Alien – im Shop freischaltbar!",
                        "${sp.displayName} is an optional Elite Alien – unlock it in the Shop!"
                    )
                    renderCurrentState()
                    openShopDialog()
                    return@makeHudButton
                }
                activeSpecies = sp
                placementArmed = true
                if (activeStage == EvolutionStage.ALPHA && !isAlphaAvailable(sp)) {
                    activeStage = EvolutionStage.ADULT
                }
                statusMessage = GameLocale.tr(
                    "SETZEN: ${sp.displayName} (${sp.roleTag}, HP ${sp.baseHp}/ANG ${sp.baseAttack}) → Startfeld tippen!",
                    "PLACE: ${sp.displayName} (${sp.roleTag}, HP ${sp.baseHp}/ATK ${sp.baseAttack}) → Tap start tile!"
                )
                AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
                UiTheme.buzz(this@MainActivity, 20L)
                renderCurrentState()
            }.apply {
                minWidth = btnMinW
                minimumWidth = btnMinW
                layoutParams = UiTheme.rowLayoutParams(weight = 1f, marginDp = 2, context = this@MainActivity)
            }
            speciesButtons[sp] = btn
            speciesBar.addView(btn)
        }
        speciesScroll.addView(speciesBar)
        dock.addView(speciesScroll)

        return dock
    }

    private fun buildPhase2Dock(): LinearLayout {
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = UiTheme.panelDrawable(UiTheme.BG_PANEL, UiTheme.ACCENT_AMBER, 3, 0f)
            val p = UiTheme.dp(this@MainActivity, 8)
            setPadding(p, p, p, p)
        }

        phase2Summary = TextView(this).apply {
            setTextColor(UiTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(0, 0, 0, UiTheme.dp(this@MainActivity, 6))
        }
        dock.addView(phase2Summary)

        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        controls.addView(UiTheme.makeHudButton(this, "◀ -1") {
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            isAutoPlaying = false
            stopPlaybackLoop()
            if (playbackIndex > 0) {
                playbackIndex--
                renderCurrentState()
            }
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 0.85f, marginDp = 2, context = this@MainActivity)
        })

        playPauseBtn = UiTheme.makeHudButton(this, "⏸ Pause", textColor = UiTheme.ACCENT_ALIEN) {
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            toggleAutoPlayback()
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 1.15f, marginDp = 2, context = this@MainActivity)
        }
        controls.addView(playPauseBtn)

        speedBtn = UiTheme.makeHudButton(this, "1×") {
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            speedFast = !speedFast
            renderCurrentState()
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 0.65f, marginDp = 2, context = this@MainActivity)
        }
        controls.addView(speedBtn)

        controls.addView(UiTheme.makeHudButton(this, "+1 ▶") {
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            isAutoPlaying = false
            stopPlaybackLoop()
            val sim = simulationResult ?: return@makeHudButton
            if (playbackIndex < sim.snapshots.lastIndex) {
                playbackIndex++
                renderCurrentState()
            }
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 0.9f, marginDp = 2, context = this@MainActivity)
        })

        backPhase1Btn = UiTheme.makeHudButton(this, "↺ Phase 1", textColor = UiTheme.ACCENT_AMBER) {
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            returnToPhase1()
        }.apply {
            layoutParams = UiTheme.rowLayoutParams(weight = 1.15f, marginDp = 2, context = this@MainActivity)
        }
        controls.addView(backPhase1Btn)

        dock.addView(controls)
        return dock
    }

    private fun isAlphaAvailable(species: AlienSpecies): Boolean =
        isEditorTestMode || species in progress.alphaUnlockedSpecies

    private fun selectStage(stage: EvolutionStage) {
        if (stage == EvolutionStage.ALPHA && !isAlphaAvailable(activeSpecies)) {
            statusMessage = GameLocale.tr(
                "Alpha für ${activeSpecies.displayName} zuerst unter 'Mut.' freischalten (40 Mut).",
                "Unlock Alpha for ${activeSpecies.displayName} under 'Mut.' first (40 Mut)."
            )
            UiTheme.buzz(this, 35L)
            renderCurrentState()
            return
        }
        activeStage = stage
        placementArmed = true
        statusMessage = GameLocale.tr(
            "SETZEN: ${activeSpecies.displayName} (${stage.displayName}) → Tippe Startfeld.",
            "PLACE: ${activeSpecies.displayName} (${stage.displayName}) → Tap start tile."
        )
        AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
        UiTheme.buzz(this, 18L)
        renderCurrentState()
    }

    private fun handleBoardCellTap(pos: GridPos) {
        if (simulationResult != null) return
        val lvl = effectiveLevel()

        val existing = placements.firstOrNull { it.startPos == pos }
        if (existing != null) {
            selectedPlacementId = existing.id
            placementArmed = false
            statusMessage = GameLocale.tr(
                "ZUG-MODUS (${existing.species.displayName}): Tippe leuchtende Zielfelder, Vent oder Konsole!",
                "MOVE MODE (${existing.species.displayName}): Tap glowing adjacent tiles, vent, or console!"
            )
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            UiTheme.buzz(this, 18L)
            renderCurrentState()
            return
        }

        val enemy = lvl.humanSpawns.firstOrNull { it.spawn == pos }
        if (enemy != null) {
            val counterTip = when (enemy.type) {
                HumanType.MARINE -> GameLocale.tr(
                    "Konter: Überzahl (2v1) oder Lauerer im Schatten/Nacht.",
                    "Counter: Outnumber (2v1) or Stalker in Shadow/Night."
                )
                HumanType.PYRO -> GameLocale.tr(
                    "Konter: Flankieren per Vent oder Säure-Brut aus Distanz.",
                    "Counter: Flank via Vent or use Acid Spitter from range."
                )
                HumanType.TANK -> GameLocale.tr(
                    "Konter: Säure-Brut schmilzt 3 Rüstung weg + Koloss blockt!",
                    "Counter: Acid Spitter melts 3 armor + Brute blocks!"
                )
                HumanType.TURRET -> GameLocale.tr(
                    "Konter: Konsole schaltet Geschütz ab oder Säure-Brut!",
                    "Counter: Console powers down turret or melt with Acid!"
                )
            }
            statusMessage = GameLocale.tr(
                "${enemy.type.displayName} (HP ${enemy.type.maxHp}, Rüs ${enemy.type.baseArmor}, RW ${enemy.type.dayRange}/${enemy.type.nightRange}) · $counterTip",
                "${enemy.type.displayName} (HP ${enemy.type.maxHp}, ARM ${enemy.type.baseArmor}, RNG ${enemy.type.dayRange}/${enemy.type.nightRange}) · $counterTip"
            )
            AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
            UiTheme.buzz(this, 14L)
            renderCurrentState()
            return
        }

        val selected = selectedPlacement()
        val cell = lvl.cellAt(pos)
        if (selected != null && (!placementArmed || cell == null || !cell.allowsAlienPlacement())) {
            if (tryDirectTapCommand(lvl, selected, pos)) {
                return
            }
        }

        if (cell == null || !cell.allowsAlienPlacement()) {
            statusMessage = if (selected != null) {
                GameLocale.tr(
                    "Tippe ein benachbartes Cyan-Feld für ${selected.species.displayName} oder wähle unten eine Art.",
                    "Tap an adjacent cyan tile for ${selected.species.displayName}, or select a species below."
                )
            } else {
                GameLocale.tr(
                    "Nur lila Creep- oder Startfelder sind zum Platzieren erlaubt!",
                    "Only purple Creep or deploy tiles allow placement!"
                )
            }
            UiTheme.buzz(this, 26L)
            renderCurrentState()
            return
        }

        if (!ShopProduct.isSpeciesUnlocked(activeSpecies, ownedProductIds)) {
            openShopDialog()
            return
        }

        val stageToUse = if (activeStage == EvolutionStage.ALPHA && !isAlphaAvailable(activeSpecies)) {
            EvolutionStage.ADULT
        } else {
            activeStage
        }
        val cost = CommandPlanner.placementCost(activeSpecies, stageToUse)
        val validation = CommandPlanner.evaluatePlan(lvl, placements)
        if (cost > validation.remainingBiomass) {
            statusMessage = GameLocale.tr(
                "Zu wenig Biomasse (${validation.usedBiomass + cost}/${lvl.maxBiomass})!",
                "Not enough Biomass (${validation.usedBiomass + cost}/${lvl.maxBiomass})!"
            )
            UiTheme.buzz(this, 40L)
            renderCurrentState()
            return
        }

        val id = "alien_${nextUnitNumber++}"
        val created = AlienPlacement(
            id = id,
            species = activeSpecies,
            stage = stageToUse,
            startPos = pos
        )
        placements += created
        selectedPlacementId = id
        statusMessage = GameLocale.tr(
            "${activeSpecies.displayName} bei (${pos.x},${pos.y}) gesetzt · Weiteres Startfeld tippen ODER Einheit antippen für Züge.",
            "${activeSpecies.displayName} placed at (${pos.x},${pos.y}) · Tap another start tile OR tap unit to program moves."
        )
        AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.PLACE_ALIEN)
        UiTheme.buzz(this, 24L)
        renderCurrentState()
    }

    private fun tryDirectTapCommand(lvl: LevelDefinition, selected: AlienPlacement, tappedPos: GridPos): Boolean {
        val endPos = CommandPlanner.projectedEndPosition(lvl, selected)

        if (selected.species.canUseVent) {
            val linked = CommandPlanner.findLinkedVent(lvl, endPos)
            if (linked == tappedPos) {
                placementArmed = false
                appendSimpleCommand(AlienCommand.VentJump(linked), sfx = AlienSoundEngine.Sfx.VENT_JUMP)
                return true
            }
        }

        val tappedCell = lvl.cellAt(tappedPos)
        if (tappedCell?.type == TileType.CONSOLE && (tappedPos == endPos || tappedPos.isAdjacentTo(endPos))) {
            placementArmed = false
            appendSimpleCommand(AlienCommand.ToggleConsole, sfx = AlienSoundEngine.Sfx.CONSOLE)
            return true
        }

        val dir = Direction.fromDelta(tappedPos.x - endPos.x, tappedPos.y - endPos.y)
        if (dir != null && tappedCell != null &&
            tappedCell.type != TileType.WALL &&
            tappedCell.type != TileType.WINDOW &&
            tappedCell.type != TileType.EXIT
        ) {
            placementArmed = false
            appendSimpleCommand(AlienCommand.Move(dir))
            return true
        }
        return false
    }

    private fun selectedPlacement(): AlienPlacement? =
        placements.firstOrNull { it.id == selectedPlacementId }

    private fun appendMoveCommand(dir: Direction) {
        val selected = selectedPlacement() ?: run {
            statusMessage = GameLocale.tr("Platziere oder tippe zuerst eine Einheit an.", "Place or tap a unit first.")
            renderCurrentState()
            return
        }
        val lvl = effectiveLevel()
        val curPos = CommandPlanner.projectedEndPosition(lvl, selected)
        val nextPos = curPos.move(dir)
        val targetCell = lvl.cellAt(nextPos)
        if (targetCell == null || targetCell.type == TileType.WALL || targetCell.type == TileType.WINDOW || targetCell.type == TileType.EXIT) {
            statusMessage = GameLocale.tr(
                "Weg nach ${dir.label} ist durch Hülle/ZIEL blockiert.",
                "Path ${dir.label} is blocked by hull/EXIT."
            )
            UiTheme.buzz(this, 30L)
            renderCurrentState()
            return
        }
        placementArmed = false
        appendSimpleCommand(AlienCommand.Move(dir))
    }

    private fun appendVentCommand() {
        val selected = selectedPlacement() ?: run {
            statusMessage = GameLocale.tr("Tippe zuerst eine platzierte Einheit an.", "Tap a placed unit first.")
            renderCurrentState()
            return
        }
        if (!selected.species.canUseVent) {
            statusMessage = GameLocale.tr(
                "${selected.species.displayName} kann keine Lüftungsschächte nutzen.",
                "${selected.species.displayName} cannot use air vents."
            )
            UiTheme.buzz(this, 35L)
            renderCurrentState()
            return
        }
        val lvl = effectiveLevel()
        val endPos = CommandPlanner.projectedEndPosition(lvl, selected)
        val linked = CommandPlanner.findLinkedVent(lvl, endPos)
        if (linked == null) {
            statusMessage = GameLocale.tr(
                "Endposition (${endPos.x},${endPos.y}) liegt auf keinem verknüpften Vent-Feld.",
                "End tile (${endPos.x},${endPos.y}) is not a linked Vent."
            )
            UiTheme.buzz(this, 35L)
            renderCurrentState()
            return
        }
        placementArmed = false
        appendSimpleCommand(AlienCommand.VentJump(linked), sfx = AlienSoundEngine.Sfx.VENT_JUMP)
    }

    private fun appendConsoleCommand() {
        val selected = selectedPlacement() ?: run {
            statusMessage = GameLocale.tr("Tippe zuerst eine platzierte Einheit an.", "Tap a placed unit first.")
            renderCurrentState()
            return
        }
        val lvl = effectiveLevel()
        val endPos = CommandPlanner.projectedEndPosition(lvl, selected)
        if (!CommandPlanner.hasNearbyConsole(lvl, endPos)) {
            statusMessage = GameLocale.tr(
                "Bei (${endPos.x},${endPos.y}) befindet sich keine Konsole in Reichweite!",
                "No console within reach of (${endPos.x},${endPos.y})!"
            )
            UiTheme.buzz(this, 35L)
            renderCurrentState()
            return
        }
        placementArmed = false
        appendSimpleCommand(AlienCommand.ToggleConsole, sfx = AlienSoundEngine.Sfx.CONSOLE)
    }

    private fun appendHatchCommand() {
        val selected = selectedPlacement() ?: run {
            statusMessage = GameLocale.tr("Tippe zuerst eine platzierte Ei-Einheit an.", "Tap a placed Egg unit first.")
            renderCurrentState()
            return
        }
        if (selected.stage != EvolutionStage.EGG) {
            statusMessage = GameLocale.tr(
                "Nur Einheiten der Stufe 'Ei' nutzen Schlüpfen.",
                "Only Egg-stage units can use Hatch."
            )
            renderCurrentState()
            return
        }
        placementArmed = false
        appendSimpleCommand(AlienCommand.HatchNow, sfx = AlienSoundEngine.Sfx.HATCH)
    }

    private fun appendSimpleCommand(cmd: AlienCommand, sfx: AlienSoundEngine.Sfx = AlienSoundEngine.Sfx.CMD_STEP) {
        val selected = selectedPlacement() ?: run {
            statusMessage = GameLocale.tr("Tippe zuerst eine platzierte Einheit an.", "Tap a placed unit first.")
            renderCurrentState()
            return
        }
        val lvl = effectiveLevel()
        val validation = CommandPlanner.evaluatePlan(lvl, placements)
        if (validation.remainingCommands <= 0) {
            statusMessage = GameLocale.tr(
                "Befehls-Limit (${lvl.maxCommands}) erreicht!",
                "Command limit (${lvl.maxCommands}) reached!"
            )
            UiTheme.buzz(this, 40L)
            renderCurrentState()
            return
        }
        replacePlacement(selected.copy(commands = selected.commands + cmd))
        statusMessage = GameLocale.tr(
            "Befehl '${cmd.shortLabel()}' für ${selected.species.displayName} programmiert.",
            "Queued '${cmd.shortLabel()}' for ${selected.species.displayName}."
        )
        AlienSoundEngine.playSfx(sfx)
        UiTheme.buzz(this, 16L)
        renderCurrentState()
    }

    private fun undoLastCommand() {
        val selected = selectedPlacement() ?: return
        if (selected.commands.isEmpty()) return
        replacePlacement(selected.copy(commands = selected.commands.dropLast(1)))
        statusMessage = GameLocale.tr("Letzten Befehl rückgängig gemacht.", "Undid last command.")
        AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
        UiTheme.buzz(this, 16L)
        renderCurrentState()
    }

    private fun removeSelectedPlacement() {
        val id = selectedPlacementId ?: return
        placements.removeAll { it.id == id }
        selectedPlacementId = placements.lastOrNull()?.id
        if (placements.isEmpty()) placementArmed = true
        statusMessage = GameLocale.tr("Einheit entfernt.", "Unit removed.")
        AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
        UiTheme.buzz(this, 20L)
        renderCurrentState()
    }

    private fun clearAllPlacements() {
        if (placements.isEmpty()) return
        placements.clear()
        selectedPlacementId = null
        placementArmed = true
        statusMessage = GameLocale.tr("Plan geleert – setze deine Brut neu.", "Plan cleared – redeploy your brood.")
        AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
        UiTheme.buzz(this, 22L)
        renderCurrentState()
    }

    private fun replacePlacement(updated: AlienPlacement) {
        val idx = placements.indexOfFirst { it.id == updated.id }
        if (idx >= 0) placements[idx] = updated
    }

    private fun startPhase2Simulation() {
        val lvl = effectiveLevel()
        val validation = CommandPlanner.evaluatePlan(lvl, placements)
        if (!validation.isValid) {
            statusMessage = validation.errorMessage ?: GameLocale.tr("Plan ungültig.", "Invalid plan.")
            UiTheme.buzz(this, 45L)
            renderCurrentState()
            return
        }
        stopPlaybackLoop()
        val activeWeapons = equippedWeapons.filter { ShopProduct.isWeaponUnlocked(it, ownedProductIds) }.toSet()
        simulationResult = TurnSimulator.simulate(lvl, placements, progress.unlockedMutations, activeWeapons)
        playbackIndex = 0
        lastRenderedStep = -1
        outcomeHandled = false
        isAutoPlaying = true
        syncAudioMode()
        boardView.triggerMotionPing()
        AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.CONSOLE)
        UiTheme.buzz(this, 32L)
        renderCurrentState()
        handler.postDelayed(playbackTick, 420L)
    }

    private fun toggleAutoPlayback() {
        val sim = simulationResult ?: return
        if (isAutoPlaying) {
            isAutoPlaying = false
            stopPlaybackLoop()
        } else {
            if (playbackIndex >= sim.snapshots.lastIndex) {
                playbackIndex = 0
                lastRenderedStep = -1
            }
            isAutoPlaying = true
            handler.postDelayed(playbackTick, 240L)
        }
        renderCurrentState()
    }

    private fun returnToPhase1() {
        stopPlaybackLoop()
        simulationResult = null
        playbackIndex = 0
        lastRenderedStep = -1
        outcomeHandled = false
        syncAudioMode()
        statusMessage = GameLocale.tr(
            "Zurück in Phase 1 – passe Züge direkt am Brett oder per Buttons an.",
            "Back in Phase 1 – adjust moves directly on the grid or via buttons."
        )
        renderCurrentState()
    }

    private fun stopPlaybackLoop() {
        handler.removeCallbacks(playbackTick)
    }

    private fun playRoundEffectsSfx(snap: TurnSnapshot) {
        if (snap.effects.isEmpty()) return
        val played = HashSet<EffectKind>()
        var count = 0
        for (ef in snap.effects) {
            if (played.add(ef.kind)) {
                AlienSoundEngine.playForCombatEffect(ef.kind)
                if (ef.kind == EffectKind.EXPLOSION) {
                    UiTheme.buzz(this, 45L)
                }
                count++
                if (count >= 3) break
            }
        }
    }

    private fun renderCurrentState() {
        val lvl = effectiveLevel()
        val sim = simulationResult

        mutagensBadge.text = "Mut:${progress.mutagens}"
        langBtn.text = GameLocale.current.code.uppercase()
        soundBtn.text = if (AlienSoundEngine.isMuted()) "🔇" else "🔊"
        mapToggleBtn.text = if (isShowingMap) GameLocale.tr("Gefecht", "Battle") else GameLocale.tr("Karte", "Map")
        mutBtn.text = GameLocale.tr("Mut.", "Mut.")
        shopBtn.text = if (equippedWeapons.isNotEmpty()) {
            GameLocale.tr("★ Shop(${equippedWeapons.size})", "★ Shop(${equippedWeapons.size})")
        } else {
            "★ Shop"
        }
        editorBtn.text = "Editor"

        UiTheme.setVisible(battleContainer, !isShowingMap)
        UiTheme.setVisible(mapContainer, isShowingMap)
        UiTheme.setVisible(statusStrip, !isShowingMap)
        UiTheme.setVisible(phase1Dock, !isShowingMap && sim == null)
        UiTheme.setVisible(phase2Dock, !isShowingMap && sim != null)

        if (isShowingMap) {
            titleText.text = GameLocale.tr("SCHIFFSKARTE · SEKTOR-ÜBERSICHT", "SHIP MAP · SECTOR OVERVIEW")
            renderMapPanel()
            return
        }

        val modeSuffix = if (isEditorTestMode) " [TEST]" else if (isCustomLevelMode) GameLocale.tr(" [EIGEN]", " [CUSTOM]") else ""
        titleText.text = "${lvl.title} · ${lvl.planetTheme.displayName}$modeSuffix"

        if (sim == null) {
            renderPhase1(lvl)
        } else {
            renderPhase2(lvl, sim)
        }
    }

    private fun renderMapPanel() {
        val nodes = BuiltInCampaign.shipNodes
        val selectedNode = nodes.firstOrNull { it.levelId == selectedMapNodeId } ?: nodes.first()
        shipMapView.setMapData(nodes, progress, selectedNode.levelId)
        val status = progress.roomStatuses[selectedNode.levelId] ?: RoomStatus.LOCKED
        val upgrade = progress.roomUpgrades[selectedNode.levelId] ?: RoomUpgrade.NONE

        mapRoomTitle.text = "${selectedNode.title} (${selectedNode.shipName})"
        val bonusText = if (upgrade != RoomUpgrade.NONE) {
            GameLocale.tr(" · Ausbau: ${upgrade.displayName} (${upgrade.bonusHint})", " · Upgrade: ${upgrade.displayName} (${upgrade.bonusHint})")
        } else ""
        mapRoomMeta.text = GameLocale.tr(
            "Status: ${status.label} · Belohnung: +${selectedNode.mutagensReward} Mutagene$bonusText",
            "Status: ${status.label} · Reward: +${selectedNode.mutagensReward} Mutagens$bonusText"
        )

        mapAttackBtn.isEnabled = status != RoomStatus.LOCKED
        mapAttackBtn.text = when (status) {
            RoomStatus.LOST -> GameLocale.tr("ZURÜCKEROBERN!", "RECLAIM ROOM!")
            RoomStatus.CONQUERED -> GameLocale.tr("NOCHMAL SPIELEN", "REPLAY ROOM")
            RoomStatus.AVAILABLE -> GameLocale.tr("RAUM ANGREIFEN", "ATTACK ROOM")
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

        mapCustomBtn.text = GameLocale.tr("Eigene", "Custom")
        mapResetBtn.text = "Reset"
    }

    private fun renderPhase1(lvl: LevelDefinition) {
        val validation = CommandPlanner.evaluatePlan(lvl, placements)
        val initialNight = lvl.dayNight.isNightAtRound(1)
        val cycleInfo = if (lvl.dayNight.switchEveryRounds > 0) {
            GameLocale.tr(" alle ${lvl.dayNight.switchEveryRounds}R", " /${lvl.dayNight.switchEveryRounds}R")
        } else ""

        dayNightBadge.text = (if (initialNight) GameLocale.tr("☾ NACHT", "☾ NIGHT") else GameLocale.tr("☀ TAG", "☀ DAY")) + cycleInfo
        dayNightBadge.setTextColor(if (initialNight) UiTheme.ACCENT_CYAN else UiTheme.ACCENT_AMBER)

        biomassBadge.text = "Bio:${validation.usedBiomass}/${lvl.maxBiomass}"
        biomassBadge.setTextColor(
            if (validation.remainingBiomass < 0) UiTheme.ACCENT_ALERT else UiTheme.ACCENT_ALIEN
        )

        commandBadge.text = GameLocale.tr("Bef:${validation.usedCommands}/${lvl.maxCommands}", "Cmd:${validation.usedCommands}/${lvl.maxCommands}")
        commandBadge.setTextColor(
            if (validation.remainingCommands < 0) UiTheme.ACCENT_ALERT else UiTheme.ACCENT_CYAN
        )

        roundBadge.text = "Max:${lvl.maxRounds}R"

        undoCmdBtn.text = GameLocale.tr("↶ Zur.", "↶ Undo")
        removeCmdBtn.text = GameLocale.tr("✕ Einh.", "✕ Del")
        ventCmdBtn.text = "Vent"
        consoleCmdBtn.text = GameLocale.tr("Schalter", "Switch")
        waitCmdBtn.text = GameLocale.tr("Warten", "Wait")
        hatchCmdBtn.text = GameLocale.tr("Schlüpf.", "Hatch")
        clearAllBtn.text = GameLocale.tr("Leeren", "Clear")
        startAttackBtn.text = GameLocale.tr("ANGRIFF ▶", "ATTACK ▶")

        val selected = selectedPlacement()
        val endPos = selected?.let { CommandPlanner.projectedEndPosition(lvl, it) }
        val ventReady = selected != null && endPos != null && selected.species.canUseVent &&
            CommandPlanner.findLinkedVent(lvl, endPos) != null
        val consoleReady = endPos != null && CommandPlanner.hasNearbyConsole(lvl, endPos)
        val hatchReady = selected?.stage == EvolutionStage.EGG

        UiTheme.styleToggleButton(ventCmdBtn, selected = ventReady, accentColor = UiTheme.ACCENT_CYAN, enabled = true)
        UiTheme.styleToggleButton(consoleCmdBtn, selected = consoleReady, accentColor = UiTheme.ACCENT_ALIEN, enabled = true)
        UiTheme.styleToggleButton(hatchCmdBtn, selected = hatchReady, accentColor = Color.parseColor("#C77DFF"), enabled = true)

        selectedUnitBar.text = if (selected != null && endPos != null) {
            val modeTag = if (placementArmed) GameLocale.tr("SETZEN", "PLACE") else GameLocale.tr("ZUG", "MOVE")
            val q = if (selected.commands.isEmpty()) "—" else selected.commands.joinToString(" ") { it.shortLabel() }
            "[$modeTag] ${selected.species.displayName} (${selected.stage.displayName}) → (${endPos.x},${endPos.y}) · $q"
        } else {
            GameLocale.tr(
                "[SETZEN] ${activeSpecies.displayName} (${CommandPlanner.placementCost(activeSpecies, activeStage)} Bio) → Startfeld tippen",
                "[PLACE] ${activeSpecies.displayName} (${CommandPlanner.placementCost(activeSpecies, activeStage)} Bio) → Tap start tile"
            )
        }

        infoBanner.text = statusMessage ?: validation.errorMessage ?: lvl.hint
        infoBanner.setTextColor(
            if (validation.errorMessage != null && placements.isNotEmpty()) UiTheme.ACCENT_AMBER else UiTheme.TEXT_PRIMARY
        )

        for ((stage, btn) in stageButtons) {
            val unlocked = stage != EvolutionStage.ALPHA || isAlphaAvailable(activeSpecies)
            val short = when (stage) {
                EvolutionStage.EGG -> GameLocale.tr("Ei", "Egg")
                EvolutionStage.LARVA -> GameLocale.tr("Larve", "Larva")
                EvolutionStage.ADULT -> GameLocale.tr("Kampf", "Adult")
                EvolutionStage.ALPHA -> if (unlocked) "Alpha" else "Alpha🔒"
            }
            btn.text = short
            UiTheme.styleToggleButton(btn, activeStage == stage, UiTheme.ACCENT_CYAN, enabled = true)
        }

        for ((sp, btn) in speciesButtons) {
            val isUnlocked = ShopProduct.isSpeciesUnlocked(sp, ownedProductIds)
            val effectiveStage = if (activeStage == EvolutionStage.ALPHA && !isAlphaAvailable(sp)) {
                EvolutionStage.ADULT
            } else {
                activeStage
            }
            val cost = CommandPlanner.placementCost(sp, effectiveStage)
            btn.text = if (isUnlocked) {
                "${sp.shortName}\n${cost}B"
            } else {
                "${sp.shortName}🔒\nShop"
            }
            UiTheme.styleToggleButton(
                btn,
                selected = isUnlocked && placementArmed && activeSpecies == sp,
                accentColor = if (isUnlocked) sp.accentColorHex.toInt() else UiTheme.ACCENT_AMBER,
                enabled = true
            )
        }

        UiTheme.styleToggleButton(
            startAttackBtn,
            selected = validation.isValid,
            accentColor = if (validation.isValid) UiTheme.ACCENT_ALIEN else UiTheme.ACCENT_AMBER,
            enabled = true
        )

        boardView.setPhase1State(
            level = lvl,
            placements = placements,
            selectedPlacementId = selectedPlacementId,
            highlightDeployable = placementArmed,
            commandModeActive = !placementArmed && selectedPlacementId != null,
            remainingCommands = validation.remainingCommands
        )
    }

    private fun renderPhase2(lvl: LevelDefinition, sim: SimulationResult) {
        val idx = playbackIndex.coerceIn(0, sim.snapshots.lastIndex)
        val snap = sim.snapshots[idx]
        AlienSoundEngine.setNightAtmosphere(snap.isNight)

        val nextSwitch = if (lvl.dayNight.switchEveryRounds > 0) {
            val rem = lvl.dayNight.switchEveryRounds - (snap.round % lvl.dayNight.switchEveryRounds)
            " (${rem}R)"
        } else ""
        dayNightBadge.text = (if (snap.isNight) GameLocale.tr("☾ NACHT", "☾ NIGHT") else GameLocale.tr("☀ TAG", "☀ DAY")) + nextSwitch
        dayNightBadge.setTextColor(if (snap.isNight) UiTheme.ACCENT_CYAN else UiTheme.ACCENT_AMBER)

        biomassBadge.text = "Aliens:${snap.aliens.count { it.isAlive }}"
        biomassBadge.setTextColor(UiTheme.ACCENT_ALIEN)
        commandBadge.text = GameLocale.tr("Gegner:${snap.humans.count { it.isThreat }}", "Hostiles:${snap.humans.count { it.isThreat }}")
        commandBadge.setTextColor(UiTheme.ACCENT_ALERT)
        roundBadge.text = "R:${snap.round}/${lvl.maxRounds}"

        val lastLogs = snap.logs.takeLast(2).joinToString(" | ")
        infoBanner.text = if (lastLogs.isBlank()) {
            GameLocale.tr("Runde ${snap.round}: Gefecht läuft…", "Round ${snap.round}: Combat in progress…")
        } else lastLogs
        infoBanner.setTextColor(
            when (snap.outcome) {
                BattleOutcome.VICTORY -> UiTheme.ACCENT_ALIEN
                BattleOutcome.IN_PROGRESS -> UiTheme.TEXT_PRIMARY
                else -> UiTheme.ACCENT_ALERT
            }
        )

        val eggs = snap.aliens.count { it.isAlive && it.isEgg }
        val turretsOff = snap.humans.count { it.isAlive && it.type == HumanType.TURRET && !it.powered }
        phase2Summary.text = GameLocale.tr(
            "PHASE 2 (Automatik) · Runde ${snap.round}/${sim.totalRounds}",
            "PHASE 2 (Auto-Combat) · Round ${snap.round}/${sim.totalRounds}"
        ) + (if (eggs > 0) GameLocale.tr(" · Eier: $eggs", " · Eggs: $eggs") else "") +
            (if (turretsOff > 0) GameLocale.tr(" · Geschütze aus: $turretsOff", " · Turrets off: $turretsOff") else "")

        playPauseBtn.text = if (isAutoPlaying) "⏸ Pause" else "▶ Auto"
        speedBtn.text = if (speedFast) "2×" else "1×"
        backPhase1Btn.text = "↺ Phase 1"

        if (lastRenderedStep != idx) {
            val prevSnap = if (idx > 0 && lastRenderedStep == idx - 1) sim.snapshots[idx - 1] else null
            boardView.setPhase2Transition(lvl, prevSnap, snap, currentStepDurationMs() - 40L)
            if (idx > 0) {
                playRoundEffectsSfx(snap)
            }
            lastRenderedStep = idx
        }

        if (idx == sim.snapshots.lastIndex &&
            snap.outcome != BattleOutcome.IN_PROGRESS &&
            !outcomeHandled
        ) {
            outcomeHandled = true
            handler.postDelayed({
                if (simulationResult === sim && !isFinishing) {
                    handleSimulationFinished(sim)
                }
            }, if (speedFast) 320L else 560L)
        }
    }

    private fun handleSimulationFinished(sim: SimulationResult) {
        val lvl = effectiveLevel()
        UiTheme.buzz(this, if (sim.isVictory) 50L else 95L)
        AlienSoundEngine.playSfx(if (sim.isVictory) AlienSoundEngine.Sfx.VICTORY else AlienSoundEngine.Sfx.DEFEAT)

        if (sim.isVictory) {
            if (!isEditorTestMode && !isCustomLevelMode) {
                val nodes = BuiltInCampaign.shipNodes
                val (updated, reward) = progress.applyVictory(nodes, lvl.id)
                progress = updated
                storage.saveCampaignProgress(progress)

                val nextPlayable = nodes.firstOrNull { node ->
                    node.levelId != lvl.id &&
                        nodes.firstOrNull { it.levelId == lvl.id }?.connectedIds?.contains(node.levelId) == true &&
                        progress.isRoomPlayable(node.levelId)
                }

                val builder = AlertDialog.Builder(this)
                    .setTitle(GameLocale.tr("SIEG! Raum erobert", "VICTORY! Room Conquered"))
                    .setMessage(
                        GameLocale.tr(
                            "${lvl.title} in ${sim.totalRounds} Runden gesichert.\n\nBelohnung: +$reward Mutagene (Gesamt: ${progress.mutagens}).\nNutze 'Ausbauen' auf der Schiffskarte oder 'Mut.' für deine Brut!",
                            "${lvl.title} secured in ${sim.totalRounds} rounds.\n\nReward: +$reward Mutagens (Total: ${progress.mutagens}).\nUpgrade rooms on the Ship Map or evolve your brood under 'Mut.'!"
                        )
                    )
                    .setNegativeButton(GameLocale.tr("Schiffskarte", "Ship Map")) { _, _ ->
                        isShowingMap = true
                        simulationResult = null
                        syncAudioMode()
                        renderCurrentState()
                    }
                    .setNeutralButton(GameLocale.tr("Nochmal", "Replay")) { _, _ -> returnToPhase1() }

                if (nextPlayable != null) {
                    builder.setPositiveButton(GameLocale.tr("Weiter: ${nextPlayable.title}", "Next: ${nextPlayable.title}")) { _, _ ->
                        loadLevel(BuiltInCampaign.levelById(nextPlayable.levelId), editorTest = false, customLevel = false)
                        renderCurrentState()
                    }
                } else {
                    builder.setPositiveButton("OK", null)
                }
                builder.show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle(GameLocale.tr("SIEG! (${lvl.title})", "VICTORY! (${lvl.title})"))
                    .setMessage(
                        GameLocale.tr(
                            "Level in ${sim.totalRounds} deterministischen Runden gelöst.",
                            "Level solved in ${sim.totalRounds} deterministic rounds."
                        )
                    )
                    .setPositiveButton(GameLocale.tr("Zurück zu Phase 1", "Back to Phase 1")) { _, _ -> returnToPhase1() }
                    .setNegativeButton(
                        if (isEditorTestMode) GameLocale.tr("Zum Editor", "Back to Editor")
                        else GameLocale.tr("Schiffskarte", "Ship Map")
                    ) { _, _ ->
                        if (isEditorTestMode) finish() else {
                            isShowingMap = true
                            simulationResult = null
                            syncAudioMode()
                            renderCurrentState()
                        }
                    }
                    .show()
            }
        } else {
            val reason = when (sim.finalOutcome) {
                BattleOutcome.DEFEAT_ESCAPED -> GameLocale.tr(
                    "Ein Gegner hat das ZIEL-Feld erreicht!",
                    "A hostile reached the EXIT tile!"
                )
                BattleOutcome.DEFEAT_WIPED_OUT -> GameLocale.tr(
                    "Alle eigenen Aliens wurden ausgeschaltet!",
                    "All of your aliens were eliminated!"
                )
                BattleOutcome.DEFEAT_TIMEOUT -> GameLocale.tr(
                    "Zeitlimit (${lvl.maxRounds} Runden) abgelaufen – Menschen haben überlebt!",
                    "Round limit (${lvl.maxRounds} rounds) reached – hostiles survived!"
                )
                else -> GameLocale.tr("Niederlage!", "Defeat!")
            }

            val penaltyMsg = if (!isEditorTestMode && !isCustomLevelMode) {
                val (updated, penalty) = progress.applyDefeat(BuiltInCampaign.shipNodes)
                progress = updated
                storage.saveCampaignProgress(progress)
                if (penalty.lostRoomTitle != null) {
                    GameLocale.tr(
                        "\n\n⚠ RAUMVERLUST: '${penalty.lostRoomTitle}' wurde zurückerobert und muss neu gesichert werden!",
                        "\n\n⚠ ROOM LOST: '${penalty.lostRoomTitle}' was retaken and must be reclaimed!"
                    )
                } else {
                    GameLocale.tr(
                        "\n\nNoch kein vorher eroberter Raum verloren.",
                        "\n\nNo previously conquered room lost yet."
                    )
                }
            } else {
                ""
            }

            AlertDialog.Builder(this)
                .setTitle(GameLocale.tr("NIEDERLAGE!", "DEFEAT!"))
                .setMessage("$reason$penaltyMsg")
                .setPositiveButton(GameLocale.tr("Züge anpassen (Phase 1)", "Adjust Plan (Phase 1)")) { _, _ -> returnToPhase1() }
                .setNegativeButton(
                    if (isEditorTestMode) GameLocale.tr("Zum Editor", "Back to Editor")
                    else GameLocale.tr("Schiffskarte", "Ship Map")
                ) { _, _ ->
                    if (isEditorTestMode) finish() else {
                        isShowingMap = true
                        simulationResult = null
                        syncAudioMode()
                        renderCurrentState()
                    }
                }
                .show()
        }
    }

    private fun showRoomUpgradeDialog(node: ShipRoomNode) {
        if (progress.roomStatuses[node.levelId] != RoomStatus.CONQUERED) return
        val options = RoomUpgrade.entries.filter { it != RoomUpgrade.NONE }
        val labels = options.map { up ->
            val active = progress.roomUpgrades[node.levelId] == up
            if (active) "${up.displayName} [${GameLocale.tr("AKTIV", "ACTIVE")}] – ${up.bonusHint}"
            else "${up.displayName} (${up.costMutagens} Mut) – ${up.bonusHint}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(GameLocale.tr("Ausbau: ${node.title} (Mut: ${progress.mutagens})", "Upgrade: ${node.title} (Mut: ${progress.mutagens})"))
            .setItems(labels) { _, idx ->
                val chosen = options[idx]
                val updated = progress.upgradeRoom(node.levelId, chosen)
                if (updated != null) {
                    progress = updated
                    storage.saveCampaignProgress(progress)
                    AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.CONSOLE)
                    UiTheme.buzz(this, 26L)
                    renderCurrentState()
                } else {
                    AlertDialog.Builder(this)
                        .setMessage(GameLocale.tr("Nicht genug Mutagene oder Ausbau bereits aktiv.", "Not enough Mutagens or upgrade already active."))
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .setNegativeButton(GameLocale.tr("Abbrechen", "Cancel"), null)
            .show()
    }

    private fun showCustomLevelsDialog() {
        val customs = storage.loadCustomLevels()
        if (customs.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(GameLocale.tr("Eigene Level", "Custom Levels"))
                .setMessage(
                    GameLocale.tr(
                        "Noch keine eigenen Level gespeichert.\nÖffne den 'Editor', erstelle ein Raum-Rätsel und speichere es!",
                        "No custom levels saved yet.\nOpen the 'Editor', design a room puzzle, and save it!"
                    )
                )
                .setPositiveButton(GameLocale.tr("Editor öffnen", "Open Editor")) { _, _ ->
                    startActivity(Intent(this, LevelEditorActivity::class.java))
                }
                .setNegativeButton(GameLocale.tr("Schließen", "Close"), null)
                .show()
            return
        }

        val names = customs.map {
            GameLocale.tr(
                "${it.title} (${it.width}×${it.height}, ${it.humanSpawns.size} Gegner)",
                "${it.title} (${it.width}×${it.height}, ${it.humanSpawns.size} hostiles)"
            )
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(GameLocale.tr("Eigene Level spielen", "Play Custom Levels"))
            .setItems(names) { _, which ->
                loadLevel(customs[which], editorTest = false, customLevel = true)
                renderCurrentState()
            }
            .setNegativeButton(GameLocale.tr("Schließen", "Close"), null)
            .show()
    }

    private fun showLevelHelpDialog() {
        val lvl = effectiveLevel()
        val scroll = ScrollView(this).apply { setPadding(28, 16, 28, 16) }
        val body = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            text = GameLocale.tr(
                buildString {
                    append("TAKTIK-HINWEIS (${lvl.title}):\n")
                    append(lvl.hint).append("\n\n")
                    append("STEUERUNG (PHASE 1):\n")
                    append("• Unten Alien-Art wählen → auf lila Creep-/Startfeld tippen.\n")
                    append("• Platziertes Alien antippen → leuchtende Cyan-Nachbarfelder, Ziel-Vent oder Konsole direkt antippen (oder Befehls-Buttons nutzen).\n")
                    append("• Gegner auf dem Brett antippen → zeigt HP, Rüstung & Konter-Tipp!\n\n")
                    append("TAG & NACHT:\n")
                    append("• Tag: Marines RW 3, Geschütze RW 4.\n")
                    append("• Nacht / Licht-Aus: Marines RW 1, Lauerer +2 Schaden, Eier schlüpfen 1R schneller.\n\n")
                    append("KONTER & ÜBERZAHL:\n")
                    append("• Säure-Brut schmilzt Panzer-/Geschütz-Rüstung weg.\n")
                    append("• Lauerer nutzt Vents (V) und Schattenschaden.\n")
                    append("• Koloss blockt als Tank Front & Geschütze.\n")
                    append("• Netz-Brut fesselt Gegner; Schwarm-Psyche stärkt Überzahl.\n")
                    append("• Niederlage = Raumverlust: Überlebt ein Gegner, fällt ein eroberter Raum zurück!")
                },
                buildString {
                    append("TACTICAL BRIEFING (${lvl.title}):\n")
                    append(lvl.hint).append("\n\n")
                    append("CONTROLS (PHASE 1):\n")
                    append("• Pick an alien species at the bottom → tap a glowing purple Creep/deploy tile.\n")
                    append("• Tap a placed alien → directly tap highlighted cyan tiles, linked Vents, or Consoles (or use the command buttons).\n")
                    append("• Tap any enemy on the grid → inspect HP, armor & counter tip!\n\n")
                    append("DAY & NIGHT CYCLES:\n")
                    append("• Day: Marines range 3, Auto-Turrets range 4.\n")
                    append("• Night / Light-Off: Marines range 1, Stalkers +2 damage, Eggs hatch 1 round faster.\n\n")
                    append("COUNTERS & OUTNUMBERING:\n")
                    append("• Acid Spitter melts Siege Tank & Turret armor.\n")
                    append("• Stalker uses Vents (V) and deals lethal shadow ambush damage.\n")
                    append("• Brute tanks corridors and smashes closed bulkhead doors.\n")
                    append("• Web Weaver roots hostiles; Hive Psion empowers outnumbering swarms.\n")
                    append("• Defeat = Room Loss: if hostiles survive or escape, a conquered room is lost!")
                }
            )
        }
        scroll.setBackgroundColor(UiTheme.BG_PANEL)
        scroll.addView(body)
        AlertDialog.Builder(this)
            .setTitle(GameLocale.tr("Einsatz-Briefing", "Mission Briefing"))
            .setView(scroll)
            .setPositiveButton(GameLocale.tr("Verstanden", "Got it"), null)
            .show()
    }

    private fun confirmResetCampaign() {
        AlertDialog.Builder(this)
            .setTitle(GameLocale.tr("Kampagne zurücksetzen?", "Reset Campaign?"))
            .setMessage(
                GameLocale.tr(
                    "Alle eroberten Räume, Ausbauten und Mutationen werden auf den Startzustand zurückgesetzt. Gekaufte Shop-Artikel bleiben erhalten.",
                    "All conquered rooms, upgrades, and mutations will reset. Purchased Shop items remain unlocked."
                )
            )
            .setPositiveButton(GameLocale.tr("Zurücksetzen", "Reset")) { _, _ ->
                progress = storage.resetCampaignProgress(BuiltInCampaign.shipNodes)
                selectedMapNodeId = BuiltInCampaign.shipNodes.first().levelId
                loadLevel(BuiltInCampaign.levels.first(), editorTest = false, customLevel = false)
                renderCurrentState()
            }
            .setNegativeButton(GameLocale.tr("Abbrechen", "Cancel"), null)
            .show()
    }

    companion object {
        const val EXTRA_LEVEL_ID = "extra_level_id"
        const val EXTRA_TEST_LEVEL_JSON = "extra_test_level_json"
    }
}

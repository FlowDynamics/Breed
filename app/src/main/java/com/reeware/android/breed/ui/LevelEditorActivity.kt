package com.reeware.android.breed.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.reeware.android.breed.audio.AlienSoundEngine
import com.reeware.android.breed.data.BuiltInCampaign
import com.reeware.android.breed.data.LevelJsonCodec
import com.reeware.android.breed.data.LocalStorage
import com.reeware.android.breed.engine.*
import com.reeware.android.breed.render.BattleBoardView

/**
 * Zweisprachiger Hochformat-Touch-Level-Editor für BREED (DE & EN).
 * Nutzt dasselbe LevelJsonCodec-Format wie BuiltInCampaign inkl. Live-Test, Speichern & JSON Export/Import.
 */
class LevelEditorActivity : Activity() {

    private sealed class EditorTool(private val labelDe: String, private val labelEn: String) {
        val label: String get() = GameLocale.tr(labelDe, labelEn)

        data class TileBrush(
            val nameDe: String,
            val nameEn: String,
            val type: TileType,
            val deployable: Boolean = false
        ) : EditorTool(nameDe, nameEn)

        data class HumanBrush(
            val nameDe: String,
            val nameEn: String,
            val humanType: HumanType
        ) : EditorTool(nameDe, nameEn)

        data object RouteBrush : EditorTool("Route+", "Route+")
        data object RemoveHuman : EditorTool("Gegner✕", "Del Hostile")
    }

    private lateinit var storage: LocalStorage

    private var levelId: String = "custom_${System.currentTimeMillis()}"
    private var title: String = GameLocale.tr("Eigenes Deck", "Custom Deck")
    private var sectorName: String = GameLocale.tr("Benutzer-Sektor", "User Sector")
    private var hint: String = GameLocale.tr(
        "Nutze Konsole, Vent und Konter-Einheiten!",
        "Use Consoles, Vents, and counter units!"
    )
    private var planetTheme: PlanetTheme = PlanetTheme.XENO_NEBULA
    private var width: Int = 7
    private var height: Int = 9
    private var maxBiomass: Int = 8
    private var maxCommands: Int = 8
    private var maxRounds: Int = 7
    private var dayNight: DayNightConfig = DayNightConfig(startsAtNight = false, switchEveryRounds = 3)

    private val gridCells = mutableMapOf<GridPos, TileCell>()
    private val humanSpawns = mutableListOf<HumanSpawnSpec>()

    private var activeTool: EditorTool = EditorTool.TileBrush("Creep", "Creep", TileType.CREEP)
    private var activeLinkId: Int = 1
    private var selectedHumanId: String? = null
    private var nextHumanNum = 1

    private lateinit var headerTitle: TextView
    private lateinit var dayNightBtn: Button
    private lateinit var linkBtn: Button
    private lateinit var boardView: BattleBoardView
    private lateinit var editorStatus: TextView
    private val toolButtons = mutableListOf<Pair<EditorTool, Button>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storage = LocalStorage(this)
        GameLocale.applyPreference(storage.loadLanguageCode())

        initBlankRoom(width, height)
        buildUi()
        refreshEditorUi()
    }

    override fun onResume() {
        super.onResume()
        GameLocale.applyPreference(storage.loadLanguageCode())
        AlienSoundEngine.setMuted(storage.isSoundMuted())
        AlienSoundEngine.acquire()
        AlienSoundEngine.setMode(AlienSoundEngine.SoundMode.MAP, isNight = true)
    }

    override fun onPause() {
        super.onPause()
        AlienSoundEngine.release()
    }

    private fun initBlankRoom(w: Int, h: Int) {
        width = w.coerceIn(5, 9)
        height = h.coerceIn(6, 12)
        gridCells.clear()
        humanSpawns.clear()
        selectedHumanId = null

        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = GridPos(x, y)
                val isBorder = x == 0 || y == 0 || x == width - 1 || y == height - 1
                val type = when {
                    x == width / 2 && y == 0 -> TileType.ENTRY
                    x == width / 2 && y == height - 1 -> TileType.FLOOR
                    isBorder -> TileType.WALL
                    y >= height - 2 && x in 1..2 -> TileType.CREEP
                    else -> TileType.FLOOR
                }
                gridCells[p] = TileCell(pos = p, type = type, linkId = 0)
            }
        }
        val starterSpawn = GridPos(width / 2, 1)
        val spec = HumanSpawnSpec(
            id = "h_${nextHumanNum++}",
            type = HumanType.MARINE,
            spawn = starterSpawn,
            facing = Direction.SOUTH,
            route = listOf(starterSpawn, GridPos(width / 2, 2))
        )
        humanSpawns += spec
        selectedHumanId = spec.id
    }

    private fun loadFromDefinition(def: LevelDefinition, keepCustomId: Boolean) {
        levelId = if (keepCustomId) def.id else "custom_${System.currentTimeMillis()}"
        title = def.title
        sectorName = def.sectorName
        hint = def.hint
        planetTheme = def.planetTheme
        width = def.width
        height = def.height
        maxBiomass = def.maxBiomass
        maxCommands = def.maxCommands
        maxRounds = def.maxRounds
        dayNight = def.dayNight

        gridCells.clear()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = GridPos(x, y)
                gridCells[p] = TileCell(p, TileType.FLOOR)
            }
        }
        for (c in def.cells) {
            if (c.pos.x in 0 until width && c.pos.y in 0 until height) {
                gridCells[c.pos] = c
            }
        }

        humanSpawns.clear()
        humanSpawns.addAll(def.humanSpawns)
        selectedHumanId = humanSpawns.firstOrNull()?.id
        nextHumanNum = humanSpawns.size + 1
    }

    private fun buildCurrentLevel(): LevelDefinition {
        val orderedCells = ArrayList<TileCell>(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = GridPos(x, y)
                orderedCells += gridCells[p] ?: TileCell(p, TileType.FLOOR)
            }
        }
        return LevelDefinition(
            id = levelId,
            title = title,
            sectorName = sectorName,
            hint = hint,
            planetTheme = planetTheme,
            width = width,
            height = height,
            maxBiomass = maxBiomass,
            maxCommands = maxCommands,
            maxRounds = maxRounds,
            dayNight = dayNight,
            cells = orderedCells,
            humanSpawns = humanSpawns.toList()
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

        val topHud = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiTheme.BG_PANEL)
            val p = UiTheme.dp(this@LevelEditorActivity, 6)
            setPadding(p, p, p, p)
        }

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerTitle = TextView(this).apply {
            setTextColor(UiTheme.ACCENT_AMBER)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { showLevelMetaDialog() }
        }
        row1.addView(headerTitle)

        row1.addView(UiTheme.makeHudButton(this, GameLocale.tr("Daten", "Meta")) { showLevelMetaDialog() }
            .apply { layoutParams = UiTheme.rowLayoutParams(marginDp = 2, context = this@LevelEditorActivity) })
        row1.addView(UiTheme.makeHudButton(this, GameLocale.tr("Laden", "Load")) { showLoadTemplateDialog() }
            .apply { layoutParams = UiTheme.rowLayoutParams(marginDp = 2, context = this@LevelEditorActivity) })
        row1.addView(UiTheme.makeHudButton(this, "JSON") { showJsonDialog() }
            .apply { layoutParams = UiTheme.rowLayoutParams(marginDp = 2, context = this@LevelEditorActivity) })
        row1.addView(UiTheme.makeHudButton(this, GameLocale.tr("Zurück", "Back")) { finish() }
            .apply { layoutParams = UiTheme.rowLayoutParams(marginDp = 2, context = this@LevelEditorActivity) })
        topHud.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = UiTheme.dp(this@LevelEditorActivity, 4)
            layoutParams = lp
        }
        dayNightBtn = UiTheme.makeHudButton(this, GameLocale.tr("Zyklus", "Cycle"), textColor = UiTheme.ACCENT_CYAN) {
            dayNight = when {
                !dayNight.startsAtNight && dayNight.switchEveryRounds == 0 -> DayNightConfig(true, 0)
                dayNight.startsAtNight && dayNight.switchEveryRounds == 0 -> DayNightConfig(false, 3)
                !dayNight.startsAtNight && dayNight.switchEveryRounds == 3 -> DayNightConfig(true, 2)
                else -> DayNightConfig(false, 0)
            }
            refreshEditorUi()
        }.apply { layoutParams = UiTheme.rowLayoutParams(weight = 1.15f, marginDp = 2, context = this@LevelEditorActivity) }
        row2.addView(dayNightBtn)

        linkBtn = UiTheme.makeHudButton(this, "Link:1", textColor = UiTheme.ACCENT_AMBER) {
            activeLinkId = (activeLinkId + 1) % 4
            refreshEditorUi()
        }.apply { layoutParams = UiTheme.rowLayoutParams(weight = 0.7f, marginDp = 2, context = this@LevelEditorActivity) }
        row2.addView(linkBtn)

        row2.addView(UiTheme.makeHudButton(this, GameLocale.tr("Speichern", "Save"), textColor = UiTheme.ACCENT_ALIEN) {
            saveCurrentLevel()
        }.apply { layoutParams = UiTheme.rowLayoutParams(weight = 0.9f, marginDp = 2, context = this@LevelEditorActivity) })

        row2.addView(UiTheme.makeHudButton(
            this,
            GameLocale.tr("TESTEN ▶", "TEST ▶"),
            Color.parseColor("#183E28"),
            UiTheme.ACCENT_ALIEN,
            UiTheme.ACCENT_ALIEN
        ) {
            launchLiveTest()
        }.apply { layoutParams = UiTheme.rowLayoutParams(weight = 0.95f, marginDp = 2, context = this@LevelEditorActivity) })

        topHud.addView(row2)
        root.addView(topHud)

        boardView = BattleBoardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            onCellTap = { pos -> handleGridTap(pos) }
        }
        root.addView(boardView)

        editorStatus = TextView(this).apply {
            setTextColor(UiTheme.TEXT_PRIMARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            maxLines = 2
            val pH = UiTheme.dp(this@LevelEditorActivity, 8)
            val pV = UiTheme.dp(this@LevelEditorActivity, 4)
            setPadding(pH, pV, pH, pV)
            background = UiTheme.panelDrawable(UiTheme.BG_PANEL, UiTheme.BORDER_STEEL, 2, 0f)
        }
        root.addView(editorStatus)

        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = UiTheme.panelDrawable(UiTheme.BG_PANEL, UiTheme.BORDER_STEEL, 3, 0f)
            val p = UiTheme.dp(this@LevelEditorActivity, 5)
            setPadding(p, p, p, p)
        }

        val terrainTools = listOf(
            EditorTool.TileBrush("Boden", "Floor", TileType.FLOOR),
            EditorTool.TileBrush("Wand", "Wall", TileType.WALL),
            EditorTool.TileBrush("Creep", "Creep", TileType.CREEP),
            EditorTool.TileBrush("Start", "Deploy", TileType.FLOOR, deployable = true),
            EditorTool.TileBrush("Vent", "Vent", TileType.VENT),
            EditorTool.TileBrush("Schott", "Door", TileType.DOOR),
            EditorTool.TileBrush("Kons.", "Console", TileType.CONSOLE),
            EditorTool.TileBrush("Säure", "Acid", TileType.ACID_BARREL),
            EditorTool.TileBrush("Fenst.", "Window", TileType.WINDOW),
            EditorTool.TileBrush("Ein", "Entry", TileType.ENTRY),
            EditorTool.TileBrush("Ziel", "Exit", TileType.EXIT)
        )
        dock.addView(buildToolScrollRow(terrainTools))

        val unitTools = listOf(
            EditorTool.HumanBrush("Marine", "Marine", HumanType.MARINE),
            EditorTool.HumanBrush("Pyro", "Pyro", HumanType.PYRO),
            EditorTool.HumanBrush("Panzer", "Tank", HumanType.TANK),
            EditorTool.HumanBrush("Geschütz", "Turret", HumanType.TURRET),
            EditorTool.RouteBrush,
            EditorTool.RemoveHuman
        )
        val unitRow = buildToolScrollRow(unitTools)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = UiTheme.dp(this, 3)
        unitRow.layoutParams = lp
        dock.addView(unitRow)

        root.addView(dock)
        setContentView(root)
    }

    private fun buildToolScrollRow(tools: List<EditorTool>): HorizontalScrollView {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (tool in tools) {
            val btn = UiTheme.makeHudButton(this, tool.label, textSizeSp = 11f) {
                activeTool = tool
                AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.UI_CLICK)
                UiTheme.buzz(this@LevelEditorActivity, 15L)
                refreshEditorUi()
            }.apply {
                layoutParams = UiTheme.rowLayoutParams(marginDp = 2, context = this@LevelEditorActivity)
            }
            toolButtons += tool to btn
            row.addView(btn)
        }
        scroll.addView(row)
        return scroll
    }

    private fun handleGridTap(pos: GridPos) {
        UiTheme.buzz(this, 16L)
        when (val tool = activeTool) {
            is EditorTool.TileBrush -> {
                val link = if (tool.type == TileType.VENT || tool.type == TileType.DOOR || tool.type == TileType.CONSOLE) {
                    activeLinkId
                } else {
                    0
                }
                gridCells[pos] = TileCell(
                    pos = pos,
                    type = tool.type,
                    linkId = link,
                    deployable = tool.deployable
                )
            }
            is EditorTool.HumanBrush -> {
                val existingIdx = humanSpawns.indexOfFirst { it.spawn == pos }
                if (existingIdx >= 0 && humanSpawns[existingIdx].type == tool.humanType) {
                    val existing = humanSpawns[existingIdx]
                    val nextDir = Direction.entries[(existing.facing.ordinal + 1) % Direction.entries.size]
                    humanSpawns[existingIdx] = existing.copy(facing = nextDir)
                    selectedHumanId = existing.id
                } else {
                    humanSpawns.removeAll { it.spawn == pos }
                    val newId = "h_${nextHumanNum++}"
                    val link = if (tool.humanType == HumanType.TURRET) activeLinkId else 0
                    val created = HumanSpawnSpec(
                        id = newId,
                        type = tool.humanType,
                        spawn = pos,
                        facing = Direction.SOUTH,
                        route = if (tool.humanType.isStationary) emptyList() else listOf(pos),
                        linkedConsoleId = link
                    )
                    humanSpawns += created
                    selectedHumanId = newId
                }
            }
            EditorTool.RouteBrush -> {
                val selIdx = humanSpawns.indexOfFirst { it.id == selectedHumanId }
                if (selIdx < 0) {
                    val hit = humanSpawns.firstOrNull { it.spawn == pos }
                    if (hit != null) {
                        selectedHumanId = hit.id
                    } else {
                        Toast.makeText(
                            this,
                            GameLocale.tr("Zuerst einen Gegner antippen oder setzen.", "Tap or place a hostile first."),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    val existingHumanAtTap = humanSpawns.firstOrNull { it.spawn == pos && it.id != selectedHumanId }
                    if (existingHumanAtTap != null) {
                        selectedHumanId = existingHumanAtTap.id
                    } else {
                        val h = humanSpawns[selIdx]
                        if (h.type.isStationary) {
                            Toast.makeText(
                                this,
                                GameLocale.tr("Auto-Geschütze sind stationär (keine Route).", "Auto-Turrets are stationary (no route)."),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else if (pos == h.spawn) {
                            humanSpawns[selIdx] = h.copy(route = listOf(h.spawn))
                        } else {
                            humanSpawns[selIdx] = h.copy(route = h.route + pos)
                        }
                    }
                }
            }
            EditorTool.RemoveHuman -> {
                val removed = humanSpawns.removeAll { it.spawn == pos }
                if (removed && humanSpawns.none { it.id == selectedHumanId }) {
                    selectedHumanId = humanSpawns.lastOrNull()?.id
                }
            }
        }
        refreshEditorUi()
    }

    private fun refreshEditorUi() {
        val draft = buildCurrentLevel()
        headerTitle.text = "EDITOR: ${draft.title} (${draft.width}×${draft.height} · ${draft.planetTheme.displayName})"
        dayNightBtn.text = when {
            dayNight.switchEveryRounds > 0 && dayNight.startsAtNight ->
                GameLocale.tr("☾/☀ alle ${dayNight.switchEveryRounds}R", "☾/☀ /${dayNight.switchEveryRounds}R")
            dayNight.switchEveryRounds > 0 ->
                GameLocale.tr("☀/☾ alle ${dayNight.switchEveryRounds}R", "☀/☾ /${dayNight.switchEveryRounds}R")
            dayNight.startsAtNight -> GameLocale.tr("☾ Dauer-Nacht", "☾ Always Night")
            else -> GameLocale.tr("☀ Dauer-Tag", "☀ Always Day")
        }
        linkBtn.text = "Link:$activeLinkId"

        for ((tool, btn) in toolButtons) {
            val selected = tool == activeTool
            val accent = when (tool) {
                is EditorTool.TileBrush -> UiTheme.ACCENT_CYAN
                is EditorTool.HumanBrush -> UiTheme.ACCENT_ALERT
                EditorTool.RouteBrush -> UiTheme.ACCENT_AMBER
                EditorTool.RemoveHuman -> UiTheme.ACCENT_ALERT
            }
            UiTheme.styleToggleButton(btn, selected, accent, true)
        }

        val selHuman = humanSpawns.firstOrNull { it.id == selectedHumanId }
        val humanInfo = if (selHuman != null) {
            GameLocale.tr(
                " | Aktiv: ${selHuman.type.displayName} (${selHuman.facing.label}, Route ${selHuman.route.size}P)",
                " | Active: ${selHuman.type.displayName} (${selHuman.facing.label}, Route ${selHuman.route.size}pt)"
            )
        } else ""
        editorStatus.text = GameLocale.tr(
            "Werkzeug: ${activeTool.label} (Link:$activeLinkId) · Bio:$maxBiomass Bef:$maxCommands MaxR:$maxRounds$humanInfo",
            "Tool: ${activeTool.label} (Link:$activeLinkId) · Bio:$maxBiomass Cmd:$maxCommands MaxR:$maxRounds$humanInfo"
        )

        boardView.setPhase1State(draft, emptyList(), null, highlightDeployable = true)
    }

    private fun validatePlayableLevel(lvl: LevelDefinition): String? {
        if (lvl.cells.none { it.allowsAlienPlacement() }) {
            return GameLocale.tr(
                "Mindestens ein Creep- oder Startfeld wird benötigt.",
                "At least one Creep or deploy tile is required."
            )
        }
        if (lvl.humanSpawns.isEmpty()) {
            return GameLocale.tr(
                "Mindestens ein Verteidiger (Marine/Pyro/Panzer/Geschütz) wird benötigt.",
                "At least one hostile (Marine/Pyro/Tank/Turret) is required."
            )
        }
        return null
    }

    private fun saveCurrentLevel() {
        val lvl = buildCurrentLevel()
        val err = validatePlayableLevel(lvl)
        if (err != null) {
            Toast.makeText(this, err, Toast.LENGTH_LONG).show()
            return
        }
        storage.saveCustomLevel(lvl)
        Toast.makeText(
            this,
            GameLocale.tr(
                "'${lvl.title}' gespeichert (unter Schiffskarte -> Eigene spielbar).",
                "'${lvl.title}' saved (playable via Ship Map -> Custom)."
            ),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun launchLiveTest() {
        val lvl = buildCurrentLevel()
        val err = validatePlayableLevel(lvl)
        if (err != null) {
            Toast.makeText(this, err, Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_TEST_LEVEL_JSON, LevelJsonCodec.encodeLevel(lvl))
        }
        startActivity(intent)
    }

    private fun showLevelMetaDialog() {
        val scroll = ScrollView(this).apply { setPadding(24, 14, 24, 14) }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(layout)

        fun labeledInput(label: String, initial: String): EditText {
            layout.addView(TextView(this).apply {
                text = label
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
            })
            return EditText(this).apply {
                setText(initial)
                layout.addView(this)
            }
        }

        val titleEt = labeledInput(GameLocale.tr("Level-Name", "Level Name"), title)
        val hintEt = labeledInput(GameLocale.tr("Taktik-Hinweis", "Tactical Hint"), hint)
        val wEt = labeledInput(GameLocale.tr("Breite (5..9)", "Width (5..9)"), width.toString())
        val hEt = labeledInput(GameLocale.tr("Höhe (6..12)", "Height (6..12)"), height.toString())
        val bioEt = labeledInput(GameLocale.tr("Max Biomasse", "Max Biomass"), maxBiomass.toString())
        val cmdEt = labeledInput(GameLocale.tr("Max Befehle", "Max Commands"), maxCommands.toString())
        val rndEt = labeledInput(GameLocale.tr("Max Runden", "Max Rounds"), maxRounds.toString())

        layout.addView(TextView(this).apply {
            text = GameLocale.tr("Planet / Atmosphäre", "Planet / Atmosphere")
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        })
        val planetSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@LevelEditorActivity,
                android.R.layout.simple_spinner_dropdown_item,
                PlanetTheme.entries.map { "${it.displayName} (${it.subtitle})" }
            )
            setSelection(planetTheme.ordinal)
            layout.addView(this)
        }

        AlertDialog.Builder(this)
            .setTitle(GameLocale.tr("Level-Parameter", "Level Settings"))
            .setView(scroll)
            .setPositiveButton(GameLocale.tr("Übernehmen", "Apply")) { _, _ ->
                title = titleEt.text.toString().ifBlank { GameLocale.tr("Eigenes Deck", "Custom Deck") }
                hint = hintEt.text.toString().ifBlank { GameLocale.tr("Kein Hinweis.", "No hint.") }
                val newW = (wEt.text.toString().toIntOrNull() ?: width).coerceIn(5, 9)
                val newH = (hEt.text.toString().toIntOrNull() ?: height).coerceIn(6, 12)
                maxBiomass = (bioEt.text.toString().toIntOrNull() ?: maxBiomass).coerceIn(2, 30)
                maxCommands = (cmdEt.text.toString().toIntOrNull() ?: maxCommands).coerceIn(2, 30)
                maxRounds = (rndEt.text.toString().toIntOrNull() ?: maxRounds).coerceIn(3, 20)
                planetTheme = PlanetTheme.entries[planetSpinner.selectedItemPosition]

                if (newW != width || newH != height) {
                    resizeGridPreservingCells(newW, newH)
                }
                refreshEditorUi()
            }
            .setNegativeButton(GameLocale.tr("Abbrechen", "Cancel"), null)
            .show()
    }

    private fun resizeGridPreservingCells(newW: Int, newH: Int) {
        val oldCells = gridCells.toMap()
        gridCells.clear()
        width = newW
        height = newH
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = GridPos(x, y)
                gridCells[p] = oldCells[p] ?: TileCell(
                    pos = p,
                    type = if (x == 0 || y == 0 || x == width - 1 || y == height - 1) TileType.WALL else TileType.FLOOR
                )
            }
        }
        humanSpawns.removeAll { it.spawn.x !in 0 until width || it.spawn.y !in 0 until height }
        for (i in humanSpawns.indices) {
            val h = humanSpawns[i]
            humanSpawns[i] = h.copy(
                route = h.route.filter { it.x in 0 until width && it.y in 0 until height }
            )
        }
    }

    private fun showLoadTemplateDialog() {
        val customs = storage.loadCustomLevels()
        val campaign = BuiltInCampaign.levels
        val labels = mutableListOf<String>()
        val items = mutableListOf<Pair<LevelDefinition, Boolean>>()

        labels += GameLocale.tr("+ Neues Standard-Deck (7×9)", "+ New Standard Deck (7×9)")
        for (c in customs) {
            labels += "${GameLocale.tr("[Eigen]", "[Custom]")} ${c.title} (${c.width}×${c.height})"
            items += c to true
        }
        for (b in campaign) {
            labels += "${GameLocale.tr("[Vorlage]", "[Template]")} ${b.title}"
            items += b to false
        }

        AlertDialog.Builder(this)
            .setTitle(GameLocale.tr("Vorlage oder eigenes Level laden", "Load Template or Custom Level"))
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == 0) {
                    levelId = "custom_${System.currentTimeMillis()}"
                    title = GameLocale.tr("Neues Deck", "New Deck")
                    initBlankRoom(7, 9)
                } else {
                    val (chosen, isCustom) = items[which - 1]
                    loadFromDefinition(chosen, keepCustomId = isCustom)
                }
                refreshEditorUi()
            }
            .setNegativeButton(GameLocale.tr("Abbrechen", "Cancel"), null)
            .show()
    }

    private fun showJsonDialog() {
        val jsonText = LevelJsonCodec.encodeLevel(buildCurrentLevel())
        val edit = EditText(this).apply {
            setText(jsonText)
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setMinLines(8)
            setMaxLines(14)
        }

        AlertDialog.Builder(this)
            .setTitle("Level JSON (Export / Import)")
            .setView(edit)
            .setNeutralButton(GameLocale.tr("Kopieren", "Copy")) { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("BREED Level JSON", edit.text.toString()))
                Toast.makeText(
                    this,
                    GameLocale.tr("JSON in Zwischenablage kopiert.", "JSON copied to clipboard."),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setPositiveButton(GameLocale.tr("JSON importieren", "Import JSON")) { _, _ ->
                runCatching { LevelJsonCodec.decodeLevel(edit.text.toString()) }
                    .onSuccess { imported ->
                        loadFromDefinition(imported, keepCustomId = false)
                        refreshEditorUi()
                        Toast.makeText(
                            this,
                            GameLocale.tr("Level '${imported.title}' importiert.", "Level '${imported.title}' imported."),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .onFailure { ex ->
                        Toast.makeText(
                            this,
                            GameLocale.tr("Ungültiges JSON: ${ex.message}", "Invalid JSON: ${ex.message}"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
            .setNegativeButton(GameLocale.tr("Schließen", "Close"), null)
            .show()
    }
}

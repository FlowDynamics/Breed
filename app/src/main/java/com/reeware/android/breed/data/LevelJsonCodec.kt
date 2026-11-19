package com.reeware.android.breed.data

import com.reeware.android.breed.engine.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes and parses LevelDefinition and CampaignProgress using standard org.json
 * so levels built in the in-game editor and campaign levels share one identical format.
 */
object LevelJsonCodec {

    fun encodeLevel(level: LevelDefinition): String {
        val root = JSONObject()
        root.put("id", level.id)
        root.put("title", level.title)
        root.put("sectorName", level.sectorName)
        root.put("hint", level.hint)
        root.put("planetTheme", level.planetTheme.name)
        root.put("width", level.width)
        root.put("height", level.height)
        root.put("maxBiomass", level.maxBiomass)
        root.put("maxCommands", level.maxCommands)
        root.put("maxRounds", level.maxRounds)

        val dn = JSONObject()
        dn.put("startsAtNight", level.dayNight.startsAtNight)
        dn.put("switchEveryRounds", level.dayNight.switchEveryRounds)
        root.put("dayNight", dn)

        val cellsArr = JSONArray()
        for (c in level.cells) {
            if (c.type == TileType.FLOOR && c.linkId == 0 && !c.lightOff && !c.deployable) continue
            val obj = JSONObject()
            obj.put("x", c.pos.x)
            obj.put("y", c.pos.y)
            obj.put("type", c.type.name)
            if (c.linkId != 0) obj.put("linkId", c.linkId)
            if (c.doorOpen) obj.put("doorOpen", true)
            if (c.lightOff) obj.put("lightOff", true)
            if (c.deployable) obj.put("deployable", true)
            cellsArr.put(obj)
        }
        root.put("cells", cellsArr)

        val spawnsArr = JSONArray()
        for (h in level.humanSpawns) {
            val obj = JSONObject()
            obj.put("id", h.id)
            obj.put("type", h.type.name)
            obj.put("x", h.spawn.x)
            obj.put("y", h.spawn.y)
            obj.put("facing", h.facing.name)
            if (h.linkedConsoleId != 0) obj.put("linkedConsoleId", h.linkedConsoleId)
            if (h.startDelayRounds != 0) obj.put("startDelayRounds", h.startDelayRounds)
            val routeArr = JSONArray()
            for (step in h.route) {
                val rp = JSONObject()
                rp.put("x", step.x)
                rp.put("y", step.y)
                routeArr.put(rp)
            }
            obj.put("route", routeArr)
            spawnsArr.put(obj)
        }
        root.put("humanSpawns", spawnsArr)
        return root.toString(2)
    }

    fun decodeLevel(jsonText: String): LevelDefinition {
        val root = JSONObject(jsonText)
        val width = root.getInt("width").coerceIn(5, 10)
        val height = root.getInt("height").coerceIn(6, 14)

        val cellMap = mutableMapOf<GridPos, TileCell>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = GridPos(x, y)
                cellMap[p] = TileCell(p, TileType.FLOOR)
            }
        }

        val cellsArr = root.getJSONArray("cells")
        for (i in 0 until cellsArr.length()) {
            val obj = cellsArr.getJSONObject(i)
            val pos = GridPos(obj.getInt("x"), obj.getInt("y"))
            if (pos.x in 0 until width && pos.y in 0 until height) {
                cellMap[pos] = TileCell(
                    pos = pos,
                    type = TileType.valueOf(obj.getString("type")),
                    linkId = obj.optInt("linkId", 0),
                    doorOpen = obj.optBoolean("doorOpen", false),
                    lightOff = obj.optBoolean("lightOff", false),
                    deployable = obj.optBoolean("deployable", false)
                )
            }
        }

        val spawns = mutableListOf<HumanSpawnSpec>()
        val spawnsArr = root.getJSONArray("humanSpawns")
        for (i in 0 until spawnsArr.length()) {
            val obj = spawnsArr.getJSONObject(i)
            val routeArr = obj.optJSONArray("route") ?: JSONArray()
            val route = mutableListOf<GridPos>()
            for (j in 0 until routeArr.length()) {
                val rp = routeArr.getJSONObject(j)
                route += GridPos(rp.getInt("x"), rp.getInt("y"))
            }
            spawns += HumanSpawnSpec(
                id = obj.getString("id"),
                type = HumanType.valueOf(obj.getString("type")),
                spawn = GridPos(obj.getInt("x"), obj.getInt("y")),
                facing = Direction.valueOf(obj.optString("facing", Direction.SOUTH.name)),
                route = route,
                linkedConsoleId = obj.optInt("linkedConsoleId", 0),
                startDelayRounds = obj.optInt("startDelayRounds", 0)
            )
        }

        val dnObj = root.optJSONObject("dayNight")
        val dayNight = DayNightConfig(
            startsAtNight = dnObj?.optBoolean("startsAtNight", false) ?: false,
            switchEveryRounds = dnObj?.optInt("switchEveryRounds", 0) ?: 0
        )

        return LevelDefinition(
            id = root.getString("id"),
            title = root.getString("title"),
            sectorName = root.optString("sectorName", "Eigener Sektor"),
            hint = root.optString("hint", ""),
            planetTheme = PlanetTheme.valueOf(root.optString("planetTheme", PlanetTheme.ACHERON_ASH.name)),
            width = width,
            height = height,
            maxBiomass = root.optInt("maxBiomass", 10),
            maxCommands = root.optInt("maxCommands", 6),
            maxRounds = root.optInt("maxRounds", 12),
            dayNight = dayNight,
            cells = cellMap.values.toList(),
            humanSpawns = spawns
        )
    }

    fun encodeProgress(progress: CampaignProgress): String {
        val root = JSONObject()
        root.put("mutagens", progress.mutagens)

        val statusObj = JSONObject()
        for ((k, v) in progress.roomStatuses) {
            statusObj.put(k, v.name)
        }
        root.put("roomStatuses", statusObj)

        val upgradeObj = JSONObject()
        for ((k, v) in progress.roomUpgrades) {
            upgradeObj.put(k, v.name)
        }
        root.put("roomUpgrades", upgradeObj)

        val mutArr = JSONArray()
        progress.unlockedMutations.forEach { mutArr.put(it.name) }
        root.put("unlockedMutations", mutArr)

        val alphaArr = JSONArray()
        progress.alphaUnlockedSpecies.forEach { alphaArr.put(it.name) }
        root.put("alphaUnlockedSpecies", alphaArr)

        val histArr = JSONArray()
        progress.conquestHistory.forEach { histArr.put(it) }
        root.put("conquestHistory", histArr)
        return root.toString()
    }

    fun decodeProgress(jsonText: String, nodes: List<ShipRoomNode>): CampaignProgress {
        val base = CampaignProgress.initial(nodes)
        val root = JSONObject(jsonText)

        val statuses = base.roomStatuses.toMutableMap()
        val statusObj = root.optJSONObject("roomStatuses")
        if (statusObj != null) {
            val keys = statusObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                runCatching { RoomStatus.valueOf(statusObj.getString(k)) }.onSuccess { statuses[k] = it }
            }
        }

        val upgrades = mutableMapOf<String, RoomUpgrade>()
        val upgradeObj = root.optJSONObject("roomUpgrades")
        if (upgradeObj != null) {
            val keys = upgradeObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                runCatching { RoomUpgrade.valueOf(upgradeObj.getString(k)) }.onSuccess { upgrades[k] = it }
            }
        }

        val muts = mutableSetOf<MutationNode>()
        val mutArr = root.optJSONArray("unlockedMutations")
        if (mutArr != null) {
            for (i in 0 until mutArr.length()) {
                runCatching { MutationNode.valueOf(mutArr.getString(i)) }.onSuccess { muts += it }
            }
        }

        val alphas = mutableSetOf<AlienSpecies>()
        val alphaArr = root.optJSONArray("alphaUnlockedSpecies")
        if (alphaArr != null) {
            for (i in 0 until alphaArr.length()) {
                runCatching { AlienSpecies.valueOf(alphaArr.getString(i)) }.onSuccess { alphas += it }
            }
        }

        val history = mutableListOf<String>()
        val histArr = root.optJSONArray("conquestHistory")
        if (histArr != null) {
            for (i in 0 until histArr.length()) {
                history += histArr.getString(i)
            }
        }

        return CampaignProgress(
            mutagens = root.optInt("mutagens", base.mutagens),
            roomStatuses = statuses,
            roomUpgrades = upgrades,
            unlockedMutations = muts,
            alphaUnlockedSpecies = alphas,
            conquestHistory = history
        )
    }
}

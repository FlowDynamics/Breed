package com.reeware.android.breed.data

import android.content.Context
import com.reeware.android.breed.engine.CampaignProgress
import com.reeware.android.breed.engine.LevelDefinition
import com.reeware.android.breed.engine.ShipRoomNode
import org.json.JSONArray

/**
 * Persists campaign progression (including lost/conquered rooms, upgrades, mutations)
 * and user-created custom levels via SharedPreferences using LevelJsonCodec.
 */
class LocalStorage(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadCampaignProgress(nodes: List<ShipRoomNode> = BuiltInCampaign.shipNodes): CampaignProgress {
        val raw = prefs.getString(KEY_CAMPAIGN_PROGRESS, null) ?: return CampaignProgress.initial(nodes)
        return runCatching { LevelJsonCodec.decodeProgress(raw, nodes) }
            .getOrElse { CampaignProgress.initial(nodes) }
    }

    fun saveCampaignProgress(progress: CampaignProgress) {
        prefs.edit()
            .putString(KEY_CAMPAIGN_PROGRESS, LevelJsonCodec.encodeProgress(progress))
            .apply()
    }

    fun resetCampaignProgress(nodes: List<ShipRoomNode> = BuiltInCampaign.shipNodes): CampaignProgress {
        val fresh = CampaignProgress.initial(nodes)
        saveCampaignProgress(fresh)
        return fresh
    }

    fun loadCustomLevels(): List<LevelDefinition> {
        val raw = prefs.getString(KEY_CUSTOM_LEVELS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val result = mutableListOf<LevelDefinition>()
            for (i in 0 until arr.length()) {
                val entryJson = arr.getString(i)
                runCatching { LevelJsonCodec.decodeLevel(entryJson) }
                    .onSuccess { result += it }
            }
            result
        }.getOrElse { emptyList() }
    }

    fun saveCustomLevel(level: LevelDefinition) {
        val current = loadCustomLevels().toMutableList()
        val index = current.indexOfFirst { it.id == level.id }
        if (index >= 0) {
            current[index] = level
        } else {
            current.add(0, level)
        }
        persistCustomLevels(current)
    }

    fun deleteCustomLevel(levelId: String) {
        val updated = loadCustomLevels().filterNot { it.id == levelId }
        persistCustomLevels(updated)
    }

    private fun persistCustomLevels(levels: List<LevelDefinition>) {
        val arr = JSONArray()
        for (lvl in levels) {
            arr.put(LevelJsonCodec.encodeLevel(lvl))
        }
        prefs.edit()
            .putString(KEY_CUSTOM_LEVELS, arr.toString())
            .apply()
    }

    fun isSoundMuted(): Boolean = prefs.getBoolean(KEY_SOUND_MUTED, false)

    fun setSoundMuted(muted: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND_MUTED, muted).apply()
    }

    fun loadLanguageCode(): String? = prefs.getString(KEY_LANGUAGE, null)

    fun saveLanguageCode(code: String) {
        prefs.edit().putString(KEY_LANGUAGE, code).apply()
    }

    fun loadOwnedProductIds(): Set<String> =
        prefs.getStringSet(KEY_OWNED_PRODUCTS, emptySet())?.toSet() ?: emptySet()

    fun saveOwnedProductIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_OWNED_PRODUCTS, ids.toSet()).apply()
    }

    fun loadEquippedWeapons(): Set<com.reeware.android.breed.engine.BioWeapon> {
        val raw = prefs.getStringSet(KEY_EQUIPPED_WEAPONS, emptySet()) ?: emptySet()
        return raw.mapNotNull { name ->
            runCatching { com.reeware.android.breed.engine.BioWeapon.valueOf(name) }.getOrNull()
        }.toSet()
    }

    fun saveEquippedWeapons(weapons: Set<com.reeware.android.breed.engine.BioWeapon>) {
        prefs.edit().putStringSet(KEY_EQUIPPED_WEAPONS, weapons.map { it.name }.toSet()).apply()
    }

    companion object {
        private const val PREFS_NAME = "breed_save_data"
        private const val KEY_CAMPAIGN_PROGRESS = "campaign_progress_json"
        private const val KEY_CUSTOM_LEVELS = "custom_levels_json"
        private const val KEY_SOUND_MUTED = "sound_muted"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_OWNED_PRODUCTS = "owned_iap_products"
        private const val KEY_EQUIPPED_WEAPONS = "equipped_bio_weapons"
    }
}

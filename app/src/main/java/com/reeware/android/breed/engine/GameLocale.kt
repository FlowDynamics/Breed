package com.reeware.android.breed.engine

import java.util.Locale

/** Unterstützte App-Sprachen für BREED (Deutsch & Englisch). */
enum class AppLanguage(val code: String) {
    DE("de"),
    EN("en");

    companion object {
        fun fromCode(code: String?): AppLanguage? = when (code?.lowercase(Locale.ROOT)) {
            "de" -> DE
            "en" -> EN
            else -> null
        }

        fun systemDefault(): AppLanguage =
            if (Locale.getDefault().language.equals("de", ignoreCase = true)) DE else EN
    }
}

/**
 * Leichtgewichtiger, Android-freier Lokalisierungs-Helper für Engine, Kampagne und UI.
 * Standardmäßig richtet sich die Sprache nach dem Gerät (DE oder EN) und lässt sich im HUD umschalten.
 */
object GameLocale {

    @Volatile
    var current: AppLanguage = AppLanguage.systemDefault()

    fun applyPreference(savedCode: String?) {
        current = AppLanguage.fromCode(savedCode) ?: AppLanguage.systemDefault()
    }

    fun toggle(): AppLanguage {
        current = if (current == AppLanguage.DE) AppLanguage.EN else AppLanguage.DE
        return current
    }

    fun tr(de: String, en: String): String =
        if (current == AppLanguage.DE) de else en
}

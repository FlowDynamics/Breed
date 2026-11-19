package com.reeware.android.breed.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.reeware.android.breed.audio.AlienSoundEngine
import com.reeware.android.breed.data.LocalStorage
import com.reeware.android.breed.engine.GameLocale
import com.reeware.android.breed.render.SpaceSplashView

/**
 * Cineastischer Startbildschirm im Hochformat mit düsterem Weltraum-Gasriesen (Jupiter/Saturn),
 * aufblitzendem "BREED"-Titel und atmosphärischem Sci-Fi-Horror-Intro-Sound.
 */
class SplashActivity : Activity() {

    private lateinit var splashView: SpaceSplashView
    private var launchedMain = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val storage = LocalStorage(this)
        GameLocale.applyPreference(storage.loadLanguageCode())
        AlienSoundEngine.setMuted(storage.isSoundMuted())
        AlienSoundEngine.setMode(AlienSoundEngine.SoundMode.SPLASH, isNight = true)

        splashView = SpaceSplashView(this).apply {
            onTitleFlash = { flashNum ->
                if (flashNum == 1 || flashNum == 3) {
                    AlienSoundEngine.playSfx(AlienSoundEngine.Sfx.TITLE_FLASH)
                    UiTheme.buzz(this@SplashActivity, if (flashNum == 1) 42L else 24L)
                }
            }
            onSplashFinished = {
                openMainGame()
            }
        }
        setContentView(splashView)
    }

    override fun onResume() {
        super.onResume()
        AlienSoundEngine.acquire()
        AlienSoundEngine.setMode(AlienSoundEngine.SoundMode.SPLASH, isNight = true)
        splashView.startSequence()
    }

    override fun onPause() {
        super.onPause()
        splashView.stopSequence()
        AlienSoundEngine.release()
    }

    private fun openMainGame() {
        if (launchedMain) return
        launchedMain = true
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}

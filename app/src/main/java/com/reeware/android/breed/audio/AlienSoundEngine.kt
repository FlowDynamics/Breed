package com.reeware.android.breed.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.reeware.android.breed.engine.EffectKind
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.*

/**
 * Eigenständige prozedurale Sci-Fi-Horror-Audio-Engine für BREED.
 * Erzeugt einen düsteren, von klassischen Alien-Filmen inspirierten Ambient-Soundtrack
 * (tiefer Schiffs-Sub-Drone, dissonante Streicher-Swells, kalter Rumpf-Hall, Motion-Tracker-Pings
 * und Kampf-Herzschlag) sowie latenzarme prozedurale Kampf- & UI-Soundeffekte über AudioTrack.
 */
object AlienSoundEngine {

    enum class SoundMode {
        SPLASH,
        PLANNING,
        COMBAT,
        MAP
    }

    enum class Sfx {
        TITLE_FLASH,
        UI_CLICK,
        PLACE_ALIEN,
        CMD_STEP,
        VENT_JUMP,
        CONSOLE,
        CLAW,
        ACID,
        EXPLOSION,
        LASER,
        FLAME,
        PSIONIC,
        HATCH,
        VICTORY,
        DEFEAT
    }

    private const val SAMPLE_RATE = 22050
    private const val FRAMES_PER_CHUNK = 512
    private const val TWO_PI = 2.0 * PI

    @Volatile
    private var mode: SoundMode = SoundMode.SPLASH

    @Volatile
    private var nightAtmosphere: Boolean = false

    @Volatile
    private var muted: Boolean = false

    @Volatile
    private var running: Boolean = false

    private var activeClients: Int = 0
    private var audioThread: Thread? = null
    private val pendingSfx = ConcurrentLinkedQueue<Sfx>()
    private val lifecycleLock = Any()

    fun isMuted(): Boolean = muted

    fun setMuted(value: Boolean) {
        muted = value
        if (value) {
            pendingSfx.clear()
        }
    }

    fun setMode(newMode: SoundMode, isNight: Boolean = nightAtmosphere) {
        mode = newMode
        nightAtmosphere = isNight
    }

    fun setNightAtmosphere(isNight: Boolean) {
        nightAtmosphere = isNight
    }

    fun playSfx(sfx: Sfx) {
        if (muted || !running) return
        if (pendingSfx.size < 16) {
            pendingSfx.offer(sfx)
        }
    }

    fun playForCombatEffect(kind: EffectKind) {
        val sfx = when (kind) {
            EffectKind.CLAW -> Sfx.CLAW
            EffectKind.ACID -> Sfx.ACID
            EffectKind.LASER -> Sfx.LASER
            EffectKind.FLAME -> Sfx.FLAME
            EffectKind.EXPLOSION -> Sfx.EXPLOSION
            EffectKind.PSIONIC -> Sfx.PSIONIC
            EffectKind.CONSOLE -> Sfx.CONSOLE
            EffectKind.VENT -> Sfx.VENT_JUMP
            EffectKind.HATCH -> Sfx.HATCH
        }
        playSfx(sfx)
    }

    /** Wird in Activity.onResume() aufgerufen – hält Audio beim Activity-Wechsel unterbrechungsfrei. */
    fun acquire() {
        synchronized(lifecycleLock) {
            activeClients++
            if (!running) {
                startThreadLocked()
            }
        }
    }

    /** Wird in Activity.onPause() aufgerufen – stoppt Audio, sobald keine Activity mehr aktiv ist. */
    fun release() {
        synchronized(lifecycleLock) {
            activeClients = (activeClients - 1).coerceAtLeast(0)
            if (activeClients == 0 && running) {
                stopThreadLocked()
            }
        }
    }

    private fun startThreadLocked() {
        running = true
        val thread = Thread({ renderLoop() }, "BreedAudioThread").apply {
            priority = Thread.MAX_PRIORITY - 1
            isDaemon = true
        }
        audioThread = thread
        thread.start()
    }

    private fun stopThreadLocked() {
        running = false
        audioThread?.interrupt()
        audioThread = null
    }

    private class ActiveVoice(
        val sfx: Sfx,
        var elapsedSec: Double = 0.0,
        val durationSec: Double,
        val pan: Double,
        var phaseA: Double = 0.0,
        var phaseB: Double = 0.0,
        var lpState: Double = 0.0
    )

    private fun renderLoop() {
        val minBufBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(FRAMES_PER_CHUNK * 4 * 2)

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBufBytes * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return

        val pcm = ShortArray(FRAMES_PER_CHUNK * 2)
        val voices = ArrayList<ActiveVoice>(12)
        val dt = 1.0 / SAMPLE_RATE

        // Zustände für den fortlaufenden Alien-Ambient-Soundtrack
        var timeSec = 0.0
        var phSub1 = 0.0
        var phSub2 = 0.0
        var phFifth = 0.0
        var phStr1 = 0.0
        var phStr2 = 0.0
        var phStr3 = 0.0
        var phStr4 = 0.0
        var phPing = 0.0
        var noiseLpL = 0.0
        var noiseLpR = 0.0
        var rng = 0x51A7E11L

        // Motion-Tracker-Ping-Timer & Echo-Verzögerungspuffer (~240 ms)
        var nextPingAt = 2.6
        var pingEnv = 0.0
        val delayLen = (SAMPLE_RATE * 0.24).toInt()
        val delayL = DoubleArray(delayLen)
        val delayR = DoubleArray(delayLen)
        var delayIdx = 0

        // Sanfter Lautstärke-Fade beim Starten/Stummschalten
        var masterGain = 0.0

        runCatching { track.play() }.onFailure {
            runCatching { track.release() }
            return
        }

        try {
            while (running && !Thread.currentThread().isInterrupted) {
                while (true) {
                    val req = pendingSfx.poll() ?: break
                    if (voices.size < 12) {
                        voices += createVoice(req, rng)
                        rng = nextRng(rng)
                    }
                }

                val curMode = mode
                val isNight = nightAtmosphere
                val targetMaster = if (muted) 0.0 else 0.78

                for (i in 0 until FRAMES_PER_CHUNK) {
                    timeSec += dt
                    masterGain += (targetMaster - masterGain) * 0.0025

                    rng = nextRng(rng)
                    val white = ((rng ushr 16) and 0xFFFFL).toDouble() / 32768.0 - 1.0
                    rng = nextRng(rng)
                    val white2 = ((rng ushr 16) and 0xFFFFL).toDouble() / 32768.0 - 1.0

                    // 1. Tiefer Schiffs-Sub-Drone (Nostromo-Reaktor / Weltraum-Kälte)
                    val baseHz = when (curMode) {
                        SoundMode.SPLASH -> 36.71 // D1 - extrem tiefes All
                        SoundMode.COMBAT -> 43.65 // F1 - angespannter Kampf
                        SoundMode.PLANNING -> if (isNight) 38.89 else 41.20 // Eb1 / E1
                        SoundMode.MAP -> 41.20
                    }
                    val drift = sin(timeSec * 0.11) * 0.22
                    phSub1 = wrapPhase(phSub1 + TWO_PI * (baseHz + drift) * dt)
                    phSub2 = wrapPhase(phSub2 + TWO_PI * (baseHz * 1.008 - drift) * dt)
                    phFifth = wrapPhase(phFifth + TWO_PI * (baseHz * 1.498) * dt)

                    val droneL = (sin(phSub1) * 0.22 + sin(phFifth) * 0.07) * (0.75 + 0.25 * sin(timeSec * 0.19))
                    val droneR = (sin(phSub2) * 0.22 + sin(phFifth + 0.7) * 0.07) * (0.75 + 0.25 * cos(timeSec * 0.17))

                    // 2. Kalter Weltraum-/Lüftungsschacht-Wind (gefiltertes Stereo-Rauschen)
                    val windCutoff = 0.014 + 0.012 * (0.5 + 0.5 * sin(timeSec * 0.23))
                    noiseLpL += (white - noiseLpL) * windCutoff
                    noiseLpR += (white2 - noiseLpR) * windCutoff
                    val windLevel = if (curMode == SoundMode.SPLASH) 0.16 else if (isNight) 0.14 else 0.10

                    // 3. Unheimliche dissonante Streicher-/Synth-Swells (kleine Sekunde + Tritonus)
                    val swellCycle = when (curMode) {
                        SoundMode.SPLASH -> 0.21
                        SoundMode.COMBAT -> 0.42
                        else -> 0.16
                    }
                    val swellEnv = ((sin(timeSec * swellCycle - 1.2) + 1.0) * 0.5).pow(2.4) *
                        when (curMode) {
                            SoundMode.SPLASH -> 0.11
                            SoundMode.COMBAT -> 0.12
                            SoundMode.PLANNING -> if (isNight) 0.13 else 0.09
                            SoundMode.MAP -> 0.08
                        }

                    val rootStr = baseHz * 4.0 // 2 Oktaven höher (~147–175 Hz)
                    phStr1 = wrapPhase(phStr1 + TWO_PI * rootStr * dt)
                    phStr2 = wrapPhase(phStr2 + TWO_PI * (rootStr * 1.0595) * dt) // kleine Sekunde (Alien-Spannung!)
                    phStr3 = wrapPhase(phStr3 + TWO_PI * (rootStr * 1.4142) * dt) // Tritonus
                    phStr4 = wrapPhase(phStr4 + TWO_PI * (rootStr * 2.004) * dt)  // schwebende Oktave

                    val stringsL = (sin(phStr1) * 0.35 + sin(phStr2) * 0.30 + sin(phStr3) * 0.20 + sin(phStr4) * 0.15) * swellEnv
                    val stringsR = (sin(phStr1 + 0.6) * 0.30 + sin(phStr2 + 1.1) * 0.35 + sin(phStr3 + 1.7) * 0.22 + sin(phStr4 + 0.4) * 0.15) * swellEnv

                    // 4. Phase-2 Kampf-Herzschlag / Industrial-Puls
                    var pulse = 0.0
                    if (curMode == SoundMode.COMBAT) {
                        val beatPos = (timeSec * 1.85) % 1.0
                        if (beatPos < 0.16) {
                            val env = exp(-beatPos * 26.0)
                            val f = 78.0 - beatPos * 220.0
                            pulse = sin(beatPos * TWO_PI * f) * env * 0.28
                        } else if (beatPos in 0.22..0.34) {
                            val b2 = beatPos - 0.22
                            val env = exp(-b2 * 34.0)
                            pulse = sin(b2 * TWO_PI * 64.0) * env * 0.17
                        }
                    }

                    // 5. Sporadischer Motion-Tracker-Sonar-Ping (Aliens-Flair!)
                    var pingSig = 0.0
                    if (curMode != SoundMode.SPLASH) {
                        if (timeSec >= nextPingAt) {
                            pingEnv = 1.0
                            val interval = when {
                                curMode == SoundMode.COMBAT -> 1.55
                                isNight -> 3.6
                                else -> 5.8
                            }
                            nextPingAt = timeSec + interval
                        }
                        if (pingEnv > 0.0008) {
                            phPing = wrapPhase(phPing + TWO_PI * 2280.0 * dt)
                            pingSig = sin(phPing) * pingEnv * (if (curMode == SoundMode.COMBAT) 0.10 else 0.065)
                            pingEnv *= 0.9983
                        }
                    }

                    // Kaltes Schiffs-Echo für Streicher + Motion-Tracker-Ping
                    val echoL = delayL[delayIdx]
                    val echoR = delayR[delayIdx]
                    delayL[delayIdx] = stringsL + pingSig * 0.7 + echoR * 0.36
                    delayR[delayIdx] = stringsR + pingSig * 0.7 + echoL * 0.36
                    delayIdx = (delayIdx + 1) % delayLen

                    var mixL = droneL + noiseLpL * windLevel + stringsL + echoL * 0.38 + pulse + pingSig * 0.85
                    var mixR = droneR + noiseLpR * windLevel + stringsR + echoR * 0.38 + pulse + pingSig * 0.65

                    // 6. Aktive prozedurale SFX-Stimmen zumischen
                    var vIdx = voices.size - 1
                    while (vIdx >= 0) {
                        val v = voices[vIdx]
                        val sample = synthVoiceSample(v, dt, white)
                        v.elapsedSec += dt
                        val gL = 0.5 - v.pan * 0.35
                        val gR = 0.5 + v.pan * 0.35
                        mixL += sample * gL
                        mixR += sample * gR
                        if (v.elapsedSec >= v.durationSec) {
                            voices.removeAt(vIdx)
                        }
                        vIdx--
                    }

                    // Soft-Clipping via tanh für satten, verzerrungsfreien Klang
                    val outL = (tanh(mixL * masterGain) * 32000.0).toInt().coerceIn(-32767, 32767).toShort()
                    val outR = (tanh(mixR * masterGain) * 32000.0).toInt().coerceIn(-32767, 32767).toShort()
                    pcm[i * 2] = outL
                    pcm[i * 2 + 1] = outR
                }

                val written = track.write(pcm, 0, pcm.size)
                if (written < 0) break
            }
        } catch (_: InterruptedException) {
            // Regulärer Stopp
        } finally {
            runCatching { track.stop() }
            runCatching { track.release() }
        }
    }

    private fun createVoice(sfx: Sfx, seed: Long): ActiveVoice {
        val pan = (((seed ushr 8) and 0xFFL).toDouble() / 255.0 - 0.5) * 0.5
        val dur = when (sfx) {
            Sfx.TITLE_FLASH -> 1.15
            Sfx.UI_CLICK -> 0.06
            Sfx.PLACE_ALIEN -> 0.22
            Sfx.CMD_STEP -> 0.09
            Sfx.VENT_JUMP -> 0.34
            Sfx.CONSOLE -> 0.30
            Sfx.CLAW -> 0.24
            Sfx.ACID -> 0.36
            Sfx.EXPLOSION -> 0.68
            Sfx.LASER -> 0.26
            Sfx.FLAME -> 0.42
            Sfx.PSIONIC -> 0.48
            Sfx.HATCH -> 0.32
            Sfx.VICTORY -> 1.10
            Sfx.DEFEAT -> 0.95
        }
        return ActiveVoice(sfx = sfx, durationSec = dur, pan = pan)
    }

    private fun synthVoiceSample(v: ActiveVoice, dt: Double, noise: Double): Double {
        val t = v.elapsedSec
        val u = (t / v.durationSec).coerceIn(0.0, 1.0)

        return when (v.sfx) {
            Sfx.TITLE_FLASH -> {
                // Kino-Sub-Drop + elektrischer Korona-Blitz beim BREED-Aufblitzen
                val subFreq = 98.0 * exp(-t * 3.2) + 30.0
                v.phaseA = wrapPhase(v.phaseA + TWO_PI * subFreq * dt)
                val sub = sin(v.phaseA) * exp(-t * 2.1) * 1.15
                v.phaseB = wrapPhase(v.phaseB + TWO_PI * (920.0 + 420.0 * sin(t * 48.0)) * dt)
                val crackle = (noise * 0.65 + sin(v.phaseB) * 0.35) * exp(-t * 7.5) * 0.95
                sub + crackle
            }
            Sfx.UI_CLICK -> {
                v.phaseA = wrapPhase(v.phaseA + TWO_PI * 1380.0 * dt)
                sin(v.phaseA) * exp(-t * 58.0) * 0.40
            }
            Sfx.PLACE_ALIEN -> {
                // Organisches Bio-Plopp + tiefer Sub-Druck
                val f = 145.0 * exp(-t * 11.0) + 52.0
                v.phaseA = wrapPhase(v.phaseA + TWO_PI * f * dt)
                v.lpState += (noise - v.lpState) * 0.14
                (sin(v.phaseA) * 0.75 + v.lpState * 0.55) * exp(-t * 12.0) * 0.85
            }
            Sfx.CMD_STEP -> {
                val f = 1720.0 - u * 360.0
                v.phaseA = wrapPhase(v.phaseA + TWO_PI * f * dt)
                sin(v.phaseA) * exp(-t * 36.0) * 0.42
            }
            Sfx.VENT_JUMP -> {
                // Metallisches Klappern + Luftstoß
                v.phaseA = wrapPhase(v.phaseA + TWO_PI * (310.0 + 180.0 * exp(-t * 15.0)) * dt)
                v.lpState += (noise - v.lpState) * 0.20
                val metallic = sin(v.phaseA) * sin(v.phaseA * 2.41) * exp(-t * 12.0) * 0.55
                val air = v.lpState * sin(u * PI) * 0.65
                metallic + air
            }
            Sfx.CONSOLE -> {
                // Zweiton-Relais + Hydraulik-Zischen
                val f = if (t < 0.10) 880.0 else 1320.0
                v.phaseA = wrapPhase(v.phaseA + TWO_PI * f * dt)
                v.lpState += (noise - v.lpState) * 0.18
                val beep = sin(v.phaseA) * exp(-((t % 0.10) * 26.0)) * 0.42
                val hiss = v.lpState * exp(-t * 6.5) * 0.50
                beep + hiss
            }
            Sfx.CLAW -> {
                // Alien-Fauchen + scharfer Hieb
                val hissFreq = 1900.0 * exp(-t * 6.5) + 420.0
                v.phaseA = wrapPhase(v.phaseA + TWO_PI * hissFreq * dt)
                v.lpState += (noise - v.lpState) * 0.42
                (v.lpState * 0.72 + sin(v.phaseA) * 0.35) * exp(-t * 9.5) * 0.95
            }
            Sfx.ACID -> {
                // Ätzendes Blubbern + Hochfrequenz-Zischen
                val bubbleFreq = 460.0 + 280.0 * sin(t * 42.0)
                v.phaseA = wrapPhase(v.phaseA + TWO_PI * bubbleFreq * dt)
                val hiNoise = noise - v.lpState
                v.lpState += (noise - v.lpState) * 0.25
                (hiNoise * 0.72 + sin(v.phaseA) * 0.28) * exp(-t * 5.2) * 0.85
            }
            Sfx.EXPLOSION -> {
                // Satter Druckwellen-Boom + Säure-Nachzischen
                val f = 92.0 * exp(-t * 5.8) + 26.0
                v.phaseA = wrapPhase(v.phaseA + TWO_PI * f * dt)
                v.lpState += (noise - v.lpState) * (0.28 * (1.0 - u) + 0.04)
                (sin(v.phaseA) * exp(-t * 3.8) * 1.15 + v.lpState * exp(-t * 3.2) * 0.95)
            }
            Sfx.LASER -> {
                // Pulse-Rifle 3er-Salve im Aliens-Stil
                val burst = (t % 0.082)
                val f = 1480.0 * exp(-burst * 34.0) + 190.0
                v.phaseA = wrapPhase(v.phaseA + TWO_PI * f * dt)
                val env = exp(-burst * 38.0) * (1.0 - u * 0.35)
                (sin(v.phaseA) * 0.55 + noise * 0.45) * env * 0.82
            }
            Sfx.FLAME -> {
                // Rauschender Pyro-Flammenstoß
                v.lpState += (noise - v.lpState) * 0.16
                val roar = sin(t * TWO_PI * 115.0) * 0.25
                (v.lpState * 0.95 + roar) * sin(u * PI).pow(0.6) * 0.95
            }
            Sfx.PSIONIC -> {
                // Unheimlicher schwebender Psi-Sweep
                val f = 620.0 + 360.0 * sin(t * 16.0) - u * 240.0
                v.phaseA = wrapPhase(v.phaseA + TWO_PI * f * dt)
                v.phaseB = wrapPhase(v.phaseB + TWO_PI * (f * 1.04) * dt)
                (sin(v.phaseA) + sin(v.phaseB)) * 0.42 * sin(u * PI)
            }
            Sfx.HATCH -> {
                // Schalen-Knacken + organisches Zischen
                val crack = if (t < 0.05) noise * exp(-t * 60.0) * 0.9 else 0.0
                v.phaseA = wrapPhase(v.phaseA + TWO_PI * (240.0 + u * 220.0) * dt)
                v.lpState += (noise - v.lpState) * 0.30
                crack + (v.lpState * 0.55 + sin(v.phaseA) * 0.35) * exp(-t * 7.5) * 0.75
            }
            Sfx.VICTORY -> {
                // Dunkler, erhabener Schwarm-Dreiklang (d-Moll / Quinte)
                val env = sin(u * PI).pow(0.75) * exp(-t * 0.9)
                val f1 = 146.83 // D3
                val f2 = 220.00 // A3
                val f3 = 293.66 // D4
                v.phaseA = wrapPhase(v.phaseA + TWO_PI * f1 * dt)
                v.phaseB = wrapPhase(v.phaseB + TWO_PI * f2 * dt)
                val s = sin(v.phaseA) * 0.5 + sin(v.phaseB) * 0.35 + sin(t * TWO_PI * f3) * 0.25
                s * env * 0.85
            }
            Sfx.DEFEAT -> {
                // Schriller zweistufiger Rumpfalarm
                val step = ((t / 0.22).toInt() % 2)
                val f = if (step == 0) 587.33 else 415.30 // D5 -> Ab4 (Tritonus-Alarm!)
                v.phaseA = wrapPhase(v.phaseA + TWO_PI * f * dt)
                val sq = tanh(sin(v.phaseA) * 2.2)
                sq * exp(-t * 1.4) * 0.65
            }
        }
    }

    private fun wrapPhase(p: Double): Double = if (p >= TWO_PI) p - TWO_PI else p

    private fun nextRng(state: Long): Long = (state * 6364136223846793005L + 1442695040888963407L)
}

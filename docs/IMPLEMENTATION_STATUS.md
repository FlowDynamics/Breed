# Implementierungsplan & Status – **BREED**

Letzte Aktualisierung: **15.11.2026**

---

## Aktueller Status (Vollständig implementiert & verifiziert)

### Umgesetzte Module (`com.reeware.android.breed`)
- **Gradle-/Android-Setup, Signing & Play Publishing:**
  - `build.gradle`, `app/build.gradle` (inkl. `com.github.triplet.play` 3.8.6, `billing-ktx:7.1.1`, `signingConfigs.release`), `settings.gradle`, `gradle.properties`, `local.properties`, `app/proguard-rules.pro`, `gradle/wrapper/*`
  - `app/src/main/AndroidManifest.xml` (Hochformat für `SplashActivity`, `MainActivity` & `LevelEditorActivity`), `app/src/main/res/drawable/ic_launcher_breed.xml`, `app/src/main/res/values/strings.xml` (EN) & `values-de/strings.xml` (DE)
  - `app/src/main/play/` (Store-Listings, Release Notes sowie 512×512 App-Icon & 1024×500 Feature-Grafiken auf `de-DE` & `en-US`) + `docs/PLAY_STORE_CHECKLIST.md` + `README.md`
- **Deterministische Spiellogik (`engine/` – ohne Android-UI-Imports):**
  - `GameLocale.kt` (DE & EN), `GridModel.kt`, `UnitModel.kt` (5 Basis- + 2 Elite-Aliens), `ShopCatalog.kt` (Elite-Aliens & Bio-Waffen), `CommandPlanner.kt`, `TurnSimulator.kt`, `CampaignState.kt`
- **Kampagne, Serialisierung & Speicher (`data/`):**
  - `LevelJsonCodec.kt`, `BuiltInCampaign.kt` (8 zweisprachige Kampagnen-Räume über *Nostromo-IX* / Acheron IV & *Kryon-Bastion* / Kryon VII), `LocalStorage.kt`
- **Google Play In-App-Billing (`billing/`):**
  - `BillingManager.kt` (Billing Library v7, Einmalkäufe, Restore Purchases, Acknowledge)
- **Audio-Engine (`audio/`):**
  - `AlienSoundEngine.kt` (prozeduraler Alien-Horror-Ambient-Soundtrack mit Sub-Drone, dissonanten Streicher-Swells, Motion-Tracker-Pings, Phase-2-Herzschlag und 15 Kampf-/UI-SFX über `AudioTrack`)
- **Biomechanisches *StarCraft* × *Alien Breed* Hochformat-Rendering (`render/`):**
  - `BiomechTheme.kt`, `PlanetBackdrop.kt`, `SpritePainter.kt`, `SpaceSplashView.kt`, `BattleBoardView.kt` (60-FPS-Bewegungsinterpolation, Kampf-Effekte, Screen-Shake, Lichtkegel, Partikel, Direct-Tap-Zielfelder)
- **Splashscreen, HUD, Shop, Schiffskarte, Mutationen & Level-Editor (`ui/`):**
  - `SplashActivity.kt`, `UiTheme.kt`, `ShipMapView.kt`, `MutationsDialog.kt`, `ShopDialog.kt`, `MainActivity.kt` (inkl. `DE`/`EN`-Schalter & Shop), `LevelEditorActivity.kt`
- **Unit-Tests (`app/src/test/java/com/reeware/android/breed/`):**
  - `TurnSimulatorTest.kt`, `CampaignLevelSolvabilityTest.kt`, `CampaignAndCodecTest.kt`, `LocalizationAndShopTest.kt`

---

## Verifikations-Checkliste

- **Schritt 1 (Engine, Kampagnen-Level & Unit-Tests):** Abgeschlossen (`TurnSimulatorTest`, `CampaignLevelSolvabilityTest`, `CampaignAndCodecTest` grün).
- **Schritt 2 (Hochformat-Rendering `render/`):** Abgeschlossen (`BiomechTheme`, `PlanetBackdrop`, `SpritePainter`, `BattleBoardView`).
- **Schritt 3 (Bottom-Dock Phase-1/2-UI, Schiffskarte, Mutationen & Raumverlust):** Abgeschlossen (`MainActivity`, `ShipMapView`, `MutationsDialog`, `LocalStorage`).
- **Schritt 4 (Integrierter Touch-Level-Editor inkl. JSON Export/Import & Live-Test):** Abgeschlossen (`LevelEditorActivity`).
- **Offen (optional auf Hardware):** Installation & manueller Spieltest per `adb install app/build/outputs/apk/debug/app-debug.apk`, sobald das Android-Testgerät angeschlossen ist.

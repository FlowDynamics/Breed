# AGENTS.md – BREED Development Guidelines

Dieses Dokument beschreibt das Spielkonzept, den Tech-Stack, die Toolchain und den aktuellen Projektstand für **BREED** (`com.reeware.android.breed`), damit jede neue Session sofort nahtlos weiterarbeiten kann.

---

## 1. Projektübersicht

- **Projektname:** BREED
- **Package / Application ID:** `com.reeware.android.breed`
- **Plattform & Orientierung:** Android, festes **Hochformat (`portrait`)**
- **Dokumente:**
  - Vollständiges Spielkonzept: `docs/GAME_DESIGN.md`
  - Umsetzungsplan & aktueller Stand: `docs/IMPLEMENTATION_STATUS.md`

### Kern-Vorgaben des Nutzers
- **Inspiration & Optik:** *Alien* (Film) × *Alien Breed* (Amiga) × *StarCraft* (Blizzard), als eigenständiges, düsteres **biomechanisches Design** (H. R. Giger-inspiriert – ohne Kopie) mit taktischer Videospiel-Lesbarkeit.
- **2-Phasen-Gameplay (Schach-/Schalter-Rätsel + Konter + Überzahl):**
  - **Phase 1 (Variante A):** Am unteren Bildschirmrand stehen Buttons für die Alien-/Verteidiger-Auswahl. Button antippen → auf ein gültiges Feld im Zimmerplan tippen → pro Einheit feste Züge/Befehle vorprogrammieren.
  - **Phase 2:** Vollautomatische, **100 % deterministische Rundenschritte** (berechenbar wie beim Schach).
- **Wichtige Mechaniken:**
  - Mischung aus **Schalter-/Schach-Puzzle** (Schotts, Konsolen, Vents, Säurefässer) und **Einheiten-Konter-Rätsel**.
  - Sowohl **Überzahl** (mehr eigene Einheiten am Ziel) als auch **Spezialisierung** (Konter gegen Panzer, Pyro-Marines, Geschütze) entscheiden.
  - **Tag- und Nacht-Zyklen** (Orbit-/Beleuchtungswechsel beeinflussen Sichtweite, Lauerer-Schattenschaden und Ei-Brutzeit).
  - **5 Alien-Arten** mit Entwicklungsstufen (`EGG`, `LARVA`, `ADULT`, `ALPHA`) und **eigenen festen Mutations-Bäumen** (keine Arten-Kreuzung).
  - **Niederlage = Raumverlust:** Überleben Menschen/Panzer oder erreichen `EXIT`, verliert der Spieler einen bereits eroberten Raum.
  - **Integrierter Level-Editor** mit einheitlichem JSON-Format (`LevelJsonCodec`).

---

## 2. Tech-Stack & Lokale Windows-Toolchain

Orientiert am Schwesterprojekt `..\GrillSimulator`:

- **Sprache:** Kotlin (`1.9.20`) + natives Android (`SurfaceView` / `Canvas` + Android Views)
- **Gradle Wrapper:** `8.7` (`gradle/wrapper/gradle-wrapper.properties`)
- **Android Gradle Plugin:** `8.5.2`
- **SDK-Konfiguration (`app/build.gradle`):** `compileSdk 35`, `targetSdk 36`, `minSdk 24`, `buildToolsVersion '35.0.0'`, Java `17`
- **Lokale Pfade auf diesem Rechner:**
  - Android SDK: `C:\Program Files (x86)\Android\android-sdk` (in `local.properties`)
  - OpenJDK 17: `C:\Program Files (x86)\Android\openjdk\jdk-17.0.12`
  - ADB: `C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe`
  - Referenzgerät (aus `..\GrillSimulator\AGENTS.md`): Samsung Galaxy A34 5G (`1080×2340`)

### Wichtiger Build-Hinweis (IPv6-Hänger auf diesem PC)
Auf dieser Maschine blockiert IPv6 bei Zugriffen auf Maven Central (`curl` ohne `-4` läuft in einen Timeout, während `curl -4` sofort lädt). Beim Ausführen von Gradle in PowerShell deshalb **immer IPv4 erzwingen**:

```powershell
$env:JAVA_HOME = "C:\Program Files (x86)\Android\openjdk\jdk-17.0.12"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:JAVA_TOOL_OPTIONS = "-Djava.net.preferIPv4Stack=true"
$env:GRADLE_OPTS = "-Djava.net.preferIPv4Stack=true"
.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon
```

*(Hinweis: Im beobachteten `gradle-wrapper.properties` hatte eine IDE-Hintergrund-Erweiterung zeitweise auf `9.7.1` umgestellt; aktuell steht dort wieder `gradle-8.7-bin.zip`, passend zu AGP `8.5.2`.)*

---

## 3. Verzeichnisstruktur (Stand heute)

```text
Breed/
├── AGENTS.md
├── build.gradle / settings.gradle / gradle.properties / local.properties
├── docs/
│   ├── GAME_DESIGN.md
│   └── IMPLEMENTATION_STATUS.md
└── app/
    ├── build.gradle
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/
        │   │   ├── drawable/ic_launcher_breed.xml
        │   │   ├── values/strings.xml      # Englischer Default
        │   │   └── values-de/strings.xml   # Deutsche Lokalisierung
        │   ├── play/                       # Google Play Store Listings & Release Notes (de-DE & en-US)
        │   └── java/com/reeware/android/breed/
        │       ├── engine/
        │       │   ├── GameLocale.kt       # Zweisprachigkeit DE & EN (Android-frei)
        │       │   ├── GridModel.kt        # Raster, Tiles, PlanetTheme, DayNightConfig, LevelDefinition
        │       │   ├── UnitModel.kt        # 5 Basis- + 2 Elite-Aliens, Stages, feste Mutations-Bäume, HumanType
        │       │   ├── ShopCatalog.kt      # In-App-Shop-Katalog (Elite-Aliens, Bio-Waffen, Bundle)
        │       │   ├── CommandPlanner.kt   # Phase-1 Platzierungs- & Befehls-Validierung
        │       │   ├── TurnSimulator.kt    # Deterministische Phase-2 Rundenschritt-Engine
        │       │   └── CampaignState.kt    # Schiffskarte, Upgrades, feste Mutations-Bäume, Raumverlust
        │       ├── data/
        │       │   ├── LevelJsonCodec.kt   # JSON Encode/Decode für Level & CampaignProgress
        │       │   ├── BuiltInCampaign.kt  # 8 vordefinierte Kampagnen-Level über 2 Planeten (DE & EN)
        │       │   └── LocalStorage.kt     # SharedPreferences-Persistenz (Fortschritt, Sprache, Shop, eigene Level)
        │       ├── billing/
        │       │   └── BillingManager.kt   # Google Play Billing v7 (Einmalkäufe, Restore, Acknowledge)
        │       ├── audio/
        │       │   └── AlienSoundEngine.kt # Prozeduraler Alien-Horror-Soundtrack & Kampf-SFX (AudioTrack)
        │       ├── render/
        │       │   ├── BiomechTheme.kt     # Düstere biomechanische Farbpalette & Kachel-Renderer
        │       │   ├── PlanetBackdrop.kt   # Prozedurale Planeten- & Sternen-Hintergründe
        │       │   ├── SpritePainter.kt    # Top-Down-Silhouetten aller 7 Alien-Arten & Marines/Panzer/Geschütze
        │       │   ├── SpaceSplashView.kt  # Düsterer Weltraum-Splashscreen (Jupiter/Saturn + aufblitzendes BREED)
        │       │   └── BattleBoardView.kt  # Animiertes 60-FPS-Hochformat-Spielfeld (Direct-Tap & Phase-2-FX)
        │       └── ui/
        │           ├── UiTheme.kt          # Gemeinsame HUD-Styles & sicheres haptisches Feedback
        │           ├── SplashActivity.kt   # Startbildschirm-Activity (MAIN/LAUNCHER) mit Intro-Sound
        │           ├── ShipMapView.kt      # Interaktive Raumschiff-Sektorkarte inkl. Raumverlust/Ausbau
        │           ├── MutationsDialog.kt  # Feste Art-Mutations-Bäume + Alpha-Freischaltung (DE & EN)
        │           ├── ShopDialog.kt       # In-App-Shop für Elite-Aliens & ausrüstbare Bio-Waffen (DE & EN)
        │           ├── MainActivity.kt     # Hauptspiel (DE/EN, Shop, Direct-Tap, 2-Reihen-Befehle, Bottom-Dock)
        │           └── LevelEditorActivity.kt # Hochformat Touch-Level-Editor inkl. Live-Test & JSON-Export
        └── test/java/com/reeware/android/breed/
            ├── TurnSimulatorTest.kt        # Engine-, Konter-, Vent-, Schalter-, Barrel- & Tag/Nacht-Tests
            ├── CampaignLevelSolvabilityTest.kt # Lösbarkeits- & Routen-Prüfung aller 8 Kampagnen-Level
            ├── CampaignAndCodecTest.kt     # Raumverlust-, Mutationsbaum-, Upgrade- & JSON-Tests
            └── LocalizationAndShopTest.kt  # DE/EN-Lokalisierungs-, Shop-, Elite-Alien- & Bio-Waffen-Tests
```

---

## 4. Konventionen

- Alle Game-Logic-Klassen unter `engine/` bleiben frei von Android-UI-Imports, damit sie schnell in reinen JVM-Unit-Tests prüfbar sind.
- `TurnSimulator.simulate(...)` muss 100 % deterministisch bleiben (keine Zufallszahlen).
- Kommunikation, Kommentare neuer Spiellogik und Doku auf Deutsch (wie in `..\GrillSimulator`).

# Google Play Console Setup & Release-Checkliste – **BREED**

Dieses Dokument fasst die einmaligen Schritte in der [Google Play Console](https://play.google.com/console) sowie den automatisierten Release-Workflow (übernommen von `..\GrillSimulator`) zusammen.

---

## 1. Einmaliges Anlegen der App in der Google Play Console

Google erlaubt über die Play Developer API erst Uploads, nachdem eine App einmalig im Web-Interface erstellt wurde:

1. **Alle Apps → App erstellen**
   - **App-Name:** `BREED`
   - **Standardsprache:** `Deutsch (Deutschland)` *(zusätzlich `Englisch (Vereinigte Staaten) – en-US` unter Store-Einträge hinzufügen)*
   - **App oder Spiel:** `Spiel`
   - **Kostenlos oder kostenpflichtig:** `Kostenlos` *(enthält optionale In-App-Käufe)*
   - **Erklärungen:** Entwicklerprogrammrichtlinien & US-Exportgesetze bestätigen → **App erstellen**
2. **Erstes signiertes App Bundle hochladen** (z. B. im Track *Interner Test* oder *Produktion*):
   - Datei: `app/build/outputs/bundle/release/app-release.aab`
   - Package ID: `com.reeware.android.breed` (Version `1.0.0`, `versionCode 1`)
3. **Store-Texte & -Grafiken (DE & EN)** liegen fertig vorbereitet unter:
   - Deutsch: `app/src/main/play/listings/de-DE/` (`title.txt`, `short-description.txt`, `full-description.txt`, `graphics/icon/icon.png` [512×512], `graphics/feature-graphic/feature-graphic.png` [1024×500])
   - Englisch: `app/src/main/play/listings/en-US/` (`title.txt`, `short-description.txt`, `full-description.txt`, `graphics/icon/icon.png` [512×512], `graphics/feature-graphic/feature-graphic.png` [1024×500])

---

## 2. In-App-Produkte (Einmalkäufe / Non-Consumables) anlegen

Unter **Monetarisierung → Produkte → In-App-Produkte** in der Play Console exakt diese 5 Produkt-IDs anlegen und aktivieren (sie entsprechen 1:1 `ShopCatalog.kt` & `BillingManager.kt`):

| Produkt-ID | Typ | Vorschlag Preis | Beschreibung (DE / EN) |
| :--- | :--- | :--- | :--- |
| `breed_alien_reaper` | Einmalkauf (`INAPP`) | `1,99 €` / `$1.99` | Elite-Alien: Klingen-Bestie / Blade Reaper |
| `breed_alien_bomber` | Einmalkauf (`INAPP`) | `1,99 €` / `$1.99` | Elite-Alien: Sporen-Bomber / Spore Bomber |
| `breed_weapon_plasma_venom` | Einmalkauf (`INAPP`) | `1,49 €` / `$1.49` | Bio-Waffe: Plasma-Giftdrüsen / Plasma Venom Glands |
| `breed_weapon_chitin_blades` | Einmalkauf (`INAPP`) | `1,49 €` / `$1.49` | Bio-Waffe: Sensen-Chitin-Panzer / Scythe Chitin Armor |
| `breed_pack_apex_arsenal` | Einmalkauf (`INAPP`) | `4,99 €` / `$4.99` | Komplettpaket: Apex-Schwarm & Arsenal / Apex Brood & Arsenal |

*(Wichtig: Für lokale Zahlungstests die eigene Google-E-Mail unter **Einstellungen → Lizenztests** eintragen.)*

---

## 3. Automatischer Upload künftiger Updates (wie beim *GrillSimulator*)

Das *Gradle Play Publisher*-Plugin (`com.github.triplet.play`) sowie der 2048-Bit-RSA Upload-Keystore (`breed-upload.keystore`, git-ignoriert), `signing.properties` (git-ignoriert) und `secrets/play-service-account.json` (git-ignoriert) sind bereits eingerichtet.

### Signiertes App Bundle lokal neu bauen:
```powershell
$env:JAVA_HOME = "C:\Program Files (x86)\Android\openjdk\jdk-17.0.12"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:JAVA_TOOL_OPTIONS = "-Djava.net.preferIPv4Stack=true"
$env:GRADLE_OPTS = "-Djava.net.preferIPv4Stack=true"
.\gradlew.bat bundleRelease --no-daemon
```

### Automatisch in die Play Console hochladen (nach dem ersten manuellen Upload):
```powershell
# Interner Test-Track (Standard):
.\gradlew.bat publishReleaseBundle --no-daemon

# Oder direkt in Produktion (nach Erhöhung von versionCode & versionName):
.\gradlew.bat publishReleaseBundle -PplayTrack=production --no-daemon

# Store-Texte (DE & EN) automatisch synchronisieren:
.\gradlew.bat publishReleaseListing --no-daemon
```

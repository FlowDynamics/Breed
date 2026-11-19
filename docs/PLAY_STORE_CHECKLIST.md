# Google Play Console Setup & Release-Checkliste – **BREED**

Dieses Dokument fasst den aktuellen Veröffentlichungsstand von **BREED** (`com.reeware.android.breed`) in der [Google Play Console](https://play.google.com/console), die fertige Ausfüllhilfe für die Web-Fragebögen unter **App-Inhalte** sowie den automatisierten Release-Workflow zusammen.

---

## 1. Bereits erledigt (automatisiert per API & Play Console)

- ✅ **App angelegt & erstes signiertes App Bundle hochgeladen:**
  - Package ID: `com.reeware.android.breed`
  - Aktueller Stand im Track *Interner Test*: **Version `1.0.2` (`versionCode 3`)** mit **Google Play Billing Library `8.3.0`** (signiert mit 2048-Bit-RSA `breed-upload.keystore`).
  - Lokale Release-Datei: `app/build/outputs/bundle/release/app-release.aab`
- ✅ **Store-Texte & Grafiken (`de-DE` & `en-US`) hochgeladen:**
  - `app/src/main/play/listings/de-DE/` & `en-US/`:
    - `title.txt`, `short-description.txt`, `full-description.txt`
    - `graphics/icon/icon.png` (`512×512`)
    - `graphics/feature-graphic/feature-graphic.png` (`1024×500`)
    - `graphics/phone-screenshots/` (`1080×2340` Hochformat-Screenshots auf Deutsch & Englisch)
- ✅ **Alle 5 In-App-Einmalkäufe (One-Time Products) angelegt, weltweit bepreist & aktiviert** über die neue Google Play Developer API v3 (`monetization.onetimeproducts` + `pricing:convertRegionPrices` für Regionsversion `2026/01`):

| Produkt-ID | Typ | Basispreis (EUR) | Status | Bezeichnung (DE / EN) |
| :--- | :--- | :--- | :--- | :--- |
| `breed_alien_reaper` | Einmalkauf (`buy-default`) | `1,99 €` | Aktiv | Elite-Alien: Klingen-Bestie / Blade Reaper |
| `breed_alien_bomber` | Einmalkauf (`buy-default`) | `1,99 €` | Aktiv | Elite-Alien: Sporen-Bomber / Spore Bomber |
| `breed_weapon_plasma_venom` | Einmalkauf (`buy-default`) | `1,49 €` | Aktiv | Bio-Waffe: Plasma-Giftdrüsen / Plasma Venom Glands |
| `breed_weapon_chitin_blades` | Einmalkauf (`buy-default`) | `1,49 €` | Aktiv | Bio-Waffe: Sensen-Chitin-Panzer / Scythe Chitin Armor |
| `breed_pack_apex_arsenal` | Einmalkauf (`buy-default`) | `4,99 €` | Aktiv | Apex-Schwarm & Arsenal (Komplett) / Apex Brood & Arsenal |

*(Tipp für eigene Zahlungstests ohne reale Abbuchung: In der Play Console unter **Einstellungen → Lizenztests** die eigene Google-Test-E-Mail hinzufügen.)*

---

## 2. Noch einmalig im Browser ausfüllen: **Richtlinien → App-Inhalte**

Für rechtliche Erklärungen stellt Google keine API bereit — diese Punkte müssen einmalig in der Play Console angeklickt werden (Dauer ca. 3–5 Minuten):

1. **Datenschutzerklärung (Privacy Policy):**
   - Fertige zweisprachige Datenschutzerklärung (DE & EN): [`docs/PRIVACY_POLICY.md`](PRIVACY_POLICY.md)
   - Nach `git push` auf GitHub (sofern das Repo öffentlich ist) als URL eintragen:
     `https://github.com/FlowDynamics/Breed/blob/main/docs/PRIVACY_POLICY.md`
     *(oder auf deiner eigenen Website/GitHub Pages hosten).*
2. **App-Zugriff:**
   - **„Alle Funktionen sind ohne besonderen Zugriff verfügbar“** *(kein Login, kein Account nötig).*
3. **Werbung:**
   - **„Nein, meine App enthält keine Werbung.“**
4. **Altersfreigabe (IARC-Fragebogen):**
   - Kategorie: **Spiele**
   - Gewalt: **Ja → Fantasie-/Sci-Fi-Gewalt** (abstrakte Top-Down-Taktik, keine realistische Gewaltdarstellung, kein Gore, keine verletzten realen Menschen)
   - Sprache, Sexualität, Drogen, Glücksspiel, Furcht/Horror: jeweils **Nein** *(oder bei Furcht/Horror maximal „leichte düstere Sci-Fi-Atmosphäre“)*
   - Nutzerinteraktion / Chat / Standortweitergabe: **Nein** *(reines Offline-Einzelspieler-Spiel)*
   - **Digitale Käufe:** **Ja** *(optionale Einmalkäufe über Google Play Billing)*
5. **Zielgruppe und Inhalte:**
   - Altersgruppe: **13–15, 16–17 und 18+** *(ab 13 Jahren, damit keine gesonderten Kinder-/Familien-Richtlinien ausgelöst werden)*
   - *„Könnte dein Store-Eintrag unbeabsichtigt Kinder ansprechen?“* → **Nein** *(düsteres Sci-Fi-Taktik-Design)*
6. **Datensicherheit (Data Safety):**
   - *„Erhebt oder teilt deine App die erforderlichen Arten von Nutzerdaten?“* → **Nein**
   - Begründung: BREED ist vollständig offline spielbar, speichert Spielstände ausschließlich lokal in Android `SharedPreferences` und nutzt keinerlei Analytics-, Werbe- oder Tracking-SDKs. Zahlungen laufen ausschließlich über Google Play Billing, ohne dass der Entwickler personenbezogene Zahlungsdaten erhält.
7. **Werbe-ID (Advertising ID):**
   - **Nein** *(BREED nutzt weder Werbung noch die Werbe-ID).*
8. **Weitere Erklärungen (News, Gesundheit, Finanzfunktionen, Regierungs-App):**
   - Jeweils **Nein / Nicht zutreffend** bestätigen.
9. **Store-Einstellungen → App-Kategorie & Kontaktdaten:**
   - Kategorie: **Spiele → Strategie** (oder *Puzzle*)
   - Entwickler-E-Mail-Adresse hinterlegen.
10. **Internen Test / Geschlossenen Test / Produktion freigeben:**
    - Den hochgeladenen Entwurf (`versionCode 3`, Version `1.0.2`) unter **Testen → Interner Test** prüfen, Tester-E-Mails hinzufügen und auf **Release überprüfen → Rollout starten** klicken.

---

## 3. Automatisierter Upload künftiger Updates

Das *Gradle Play Publisher*-Plugin (`com.github.triplet.play`) sowie der 2048-Bit-RSA Upload-Keystore (`breed-upload.keystore`, git-ignoriert), `signing.properties` (git-ignoriert) und `secrets/play-service-account.json` (git-ignoriert) sind fertig konfiguriert.

### Vor jedem Gradle-Aufruf auf diesem Windows-PC (IPv4 + HTTP-Timeouts):
```powershell
$env:JAVA_HOME = "C:\Program Files (x86)\Android\openjdk\jdk-17.0.12"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:JAVA_TOOL_OPTIONS = "-Djava.net.preferIPv4Stack=true -Dsun.net.client.defaultConnectTimeout=30000 -Dsun.net.client.defaultReadTimeout=60000"
$env:GRADLE_OPTS = "-Djava.net.preferIPv4Stack=true"
```

### Befehle:
```powershell
# 1. Tests & signiertes Release App Bundle lokal bauen:
.\gradlew.bat testDebugUnitTest bundleRelease --no-daemon

# 2. Bundle in den internen Test-Track hochladen (Standard):
.\gradlew.bat :app:publishReleaseBundle --no-daemon --console=plain

# 3. Oder direkt in Produktion (nach Erhöhung von versionCode & versionName):
.\gradlew.bat :app:publishReleaseBundle -PplayTrack=production --no-daemon --console=plain

# 4. Store-Texte, Icon, Feature-Grafik & Screenshots (DE & EN) synchronisieren
#    (--max-workers=1 verhindert Race-Conditions bei parallelen Play-API-Edits):
.\gradlew.bat :app:publishReleaseListing --max-workers=1 --no-daemon --console=plain
```

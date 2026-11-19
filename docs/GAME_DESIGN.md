# Game Design Document: **BREED**

> **Genre:** Taktik-Schach-Puzzle, Reverse-Tower-Defense & Schwarm-Zucht (Android, **Hochformat 9:16**, Package `com.reeware.android.breed`)  
> **Visuelles Setup:** *StarCraft*-artige Videospiel-Übersicht & Taktik-HUD × eigenständiges, düsteres **biomechanisches Design** (H. R. Giger-inspiriert – Stahlrippen, Kabelstränge, organisches Metall, keine Kopie) × *Alien Breed*-Korridor-Atmosphäre  
> **Tech-Stack:** Nativ Android (**Kotlin** + Custom `SurfaceView`/`Canvas` + Gradle `8.7` / AGP `8.5.2` / SDK `35`)

---

## 1. Visuelles Design & Hochformat-Aufbau (9:16)

### Biomechanischer Sci-Fi-Look (Eigenständig, Giger-inspiriert × StarCraft)
- **Umgebung (*Alien Breed* & Biomechanik):**
  - Raumschiff-Korridore bestehen aus dunklen Metallgittern, gerippten Stahlträgern, verflochtenen Leitungen, Schotts und flackerndem Notlicht.
  - Kontraststarke Akzente: kaltes Stahlblau/Grau, warnendes Amber/Rot an Geschützen und Schleusen, giftgrüne Säure-Kanister und violett pulsierender **Biomasse-Creep**.
- **Einheiten-Lesbarkeit (*StarCraft*-Videospiel-Klarheit):**
  - Klare Top-Down-Silhouetten und fraktionsspezifische Lichtakzente, damit auf dem Handy-Display im Hochformat jedes Feld sofort lesbar bleibt:
    - **Alien-Schwarm:** Dunkles Chitin mit leuchtenden Art-Akzenten (`STALKER` violett, `SPITTER` giftgrün, `BRUTE` orange, `WEAVER` cyan, `PSION` magenta).
    - **Menschen-Einheiten:** Space Marines (blau/weiß/rot), Pyro-Marines (Flammenwerfer), schwere Belagerungs-Panzer und rot blinkende Auto-Geschütze.
- **Planeten-Hintergründe:**
  - Durch Rumpf-Fenster (`WINDOW`) in den Außenwänden und in der Sektorkarte ist der jeweilige Planet im Orbit sichtbar (z. B. `ACHERON_ASH`, `KRYON_ICE`, `XENO_NEBULA`, `ORBIT_WRECK`).

### Hochformat-Bildschirmaufteilung (1-Hand-Bedienung)
```text
+------------------------------------------+
| [Planet-Ausblick] Sektor / Raum-Name     |  <- StarCraft-Taktik-HUD:
| Biomasse | Befehle | Tag/Nacht | Tick    |     Wellen-Info, Speed, Editor
+------------------------------------------+
|                                          |
|      Taktisches Raumschiff-Grid          |
|      (z. B. 7x9 oder 8x10 Felder)        |  <- Biomechanische Wände,
|                                          |     Vents, Schalter, Schotts,
|   -> Sichtbare Marine-Route              |     Geschütz-Sichtlinien,
|   -> Geplante Alien-Pfade (Variante A)   |     Planeten-Aussichtsfenster
|                                          |
+------------------------------------------+
| [Lauer] [Säure] [Koloss] [Netz] [Psyche] |  <- Unten: Button-Leiste der
| [Stufe: Ei / Larve / Kampf / Alpha]      |     Aliens/Verteidiger zum
| [Befehle: Gehen / Vent / Schalter / Warten]    Antippen -> dann aufs Feld
|          [ ANGRIFF STARTEN ]             |     tippen (Phase 1 -> Phase 2)
+------------------------------------------+
```

---

## 2. Core-Loop: Platzierung, Variante A & Deterministische Phase 2

1. **Raum auf der Raumschiff-Karte wählen** (inkl. Ausbau eroberter Räume & Mutations-Bäume).
2. **Phase 1 – Button-Auswahl, Platzierung & Befehls-Planung (Variante A):**
   - Unten am Bildschirmrand wählt der Spieler einen der **5 Alien-/Verteidiger-Buttons** (samt Entwicklungsstufe Ei/Larve/Kampf/Alpha).
   - Durch Antippen eines gültigen Feldes im Zimmerplan (`CREEP` oder `deployable`) wird die Einheit platziert.
   - Jeder platzierten Einheit werden vorab feste Befehle zugewiesen (`Move`, `VentJump`, `ToggleConsole`, `Wait`, `HatchNow`).
   - Im Zimmerplan werden **Marine-Routen**, **Geschütz-Korridore**, **Schalter-Verknüpfungen** und der **Tag-/Nacht-Wechsel** transparent angezeigt.
3. **Phase 2 – Automatischer Angriff in knackigen Rundenschritten:**
   - Mit Abschluss von Phase 1 („Angriff starten“) läuft Phase 2 **100 % deterministisch Runde für Runde** ab (ohne Zufallszahlen, wie beim Schach).
   - **Feste Runden-Reihenfolge:**
     1. Säurepfützen-Ticks & Ei-Schlupf (`EGG` → `ADULT`, doppelt so schnell bei Nacht).
     2. Geplante Alien-Befehle (`Move`, `VentJump`, `ToggleConsole`, `Wait`; `BRUTE` durchbricht geschlossene Schotts).
     3. Alien-Angriffe inkl. **Überzahl-Bonus** (`2v1`, `3v1`), **Psion-Aura** und **Spezialisierungs-Konter** (Säure schmilzt Rüstung / sprengt Fässer, Lauerer-Schattenschaden, Netz-Fesselung).
     4. Menschliche Phase: aktive **Geschütze** feuern ihren Korridor ab → **Marines/Pyros/Panzer** feuern in Sichtweite und rücken entlang ihrer Route vor (sofern nicht durch Schott, Koloss oder Netz blockiert).
     5. Auswertung: Alle mobilen Gegner tot & alle Geschütze zerstört oder stromlos → **Sieg!** Erreicht ein Gegner `EXIT` oder überlebt bis `maxRounds` → **Niederlage!**

### Niederlage = Raumverlust
Überleben Menschen oder erreichen das Ziel-Feld, verliert der Spieler einen **bereits eroberten Raum** (`RoomStatus.LOST`) inklusive dessen Nest-Ausbau bis zur Rückeroberung.

---

## 3. Tag- und Nacht-Zyklen (`DayNightConfig`)

Jeder Raum definiert Startzustand (`startsAtNight`) und Wechselintervall (`switchEveryRounds`):
- **Tag-Zyklus:** Volle Sicht- und Feuerreichweite bei Marines (`3`), Pyros (`2`), Panzern (`3`) und Geschützen (`4`).
- **Nacht-Zyklus:** Reduzierte Sichtweite bei Marines (`1`), Pyros (`1`), Panzern (`2`) und Geschützen (`2`). **Lauerer** sind ab Distanz `> 1` getarnt und erhalten `+2` Hinterhalt-Schaden; **Eier** brüten doppelt so schnell.
- Konsolen können zusätzlich das Raumlicht (`lightOff`) lokal ausschalten.

---

## 4. Die 5 Alien-Arten & eigene feste Mutations-Bäume

Keine Kreuzung zwischen Arten – jede Art hat Entwicklungsstufen (`EGG`, `LARVA`, `ADULT`, `ALPHA`) und **2 feste Mutations-Knoten**:

| Art | Kosten (Kampf) | Kern-Rolle & Konter | Feste Mutations-Knoten |
| :--- | :--- | :--- | :--- |
| **1. Lauerer (`STALKER`)** | 2 | Vent-Sprung, Konsole schalten, stark aus Nacht/Dunkelheit & von hinten | `STALKER_SHADOW` (+2 Nachtschaden), `STALKER_LEAP` (+1 Schaden nach Vent) |
| **2. Säure-Brut (`SPITTER`)** | 3 | Fernkampf (`2`), schmilzt Panzerung von **Panzern/Geschützen**, zündet `ACID_BARREL` | `SPITTER_CORROSION` (Rüstung auf 0), `SPITTER_POOL` (hinterlässt Ätzpfütze) |
| **3. Koloss (`BRUTE`)** | 4 | Hohe HP/Rüstung, blockt Wege, durchbricht geschlossene Schotts | `BRUTE_CARAPAX` (+3 HP, +1 Rüstung), `BRUTE_SLAM` (stößt Marines 1 Feld zurück) |
| **4. Netz-Brut (`WEAVER`)** | 3 | Vent-fähig, fesselt Gegner (`rootedRounds`), vermehrt den Schwarm | `WEAVER_HOST` (Marine-Kill spawnt Larve), `WEAVER_WEB` (Fesselung 2 Runden) |
| **5. Schwarm-Psyche (`PSION`)** | 4 | Aura (`+1` Schaden in 2 Feldern), löst **Panik** aus (Gegner setzt aus) | `PSION_TERROR` (Panik drückt zurück), `PSION_LINK` (verdoppelt Überzahl-Bonus) |

---

## 5. Überzahl- vs. Spezialisierungs-Regel

- **Überzahl:** Greifen `2` Aliens dasselbe Ziel an, erhält jedes Alien `+1` Schaden; bei `3+` Aliens `+2` Schaden (durch `PSION_LINK` verdoppelt).
- **Rüstung & Konter:** Ein `TANK` hat `armor = 3` (`TURRET` hat `armor = 2`). Einfache Larven/Angriffe gegen `armor >= 3` machen `0` Schaden – es braucht **Säure-Brut** (oder ein `ACID_BARREL` / `BRUTE` / `ALPHA`) plus Schwarm-Überzahl.

---

## 6. Meta-Basisbau & Integrierter Level-Editor

- **Nest-Ausbau auf eroberten Räumen:**
  - `BROOD_CHAMBER`: `+1` Start-Biomasse im Gefecht
  - `BIO_EXTRACTOR`: `+10` Mutagene pro Sieg
  - `GENE_INCUBATOR`: `+1` Max-Befehl in Phase 1
- **Level-Editor (`LevelJsonCodec`):** Einheitliches JSON-Format für Kampagne & eigene Touch-Editor-Maps (Kacheln malen, Konsolen ↔ Schotts/Geschütze verknüpfen, Routen zeichnen, testen, speichern).

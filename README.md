# LS TruckNav

Firmeninterne LKW-Navigations-App für Android. Karte, Adresssuche, LKW-Routing
mit Fahrzeugprofil und Sprachansagen – ohne eigenen Server, ohne API-Key.

**Version:** 0.3.0

---

## Was die App kann

- **Karte** mit OpenStreetMap-Daten (MapLibre + OpenFreeMap), dunkles Design
- **Adresssuche** (Photon) mit Treffern in der Nähe
- **LKW-Routing** über Valhalla mit realistischen Fahrzeugmaßen:
  Höhe, Breite, Länge, Gesamtgewicht, Achslast, Achszahl, Gefahrgut,
  Maut vermeiden, Autobahn vermeiden
- **Navigation** mit:
  - Kartenverfolgung in Fahrtrichtung
  - Abbiegehinweisen und Manöver-Icons
  - Reststrecke, Restfahrzeit, Ankunftszeit, aktuelle Geschwindigkeit
  - deutschen Sprachansagen (TTS)
  - Erkennung von Routenabweichungen und automatischer Neuberechnung
  - Zielerreichung
- **Tempolimit** des aktuellen Straßenabschnitts als StVO-Schild 274
  (weißer Kreis, roter Ring) – in der Statusleiste und groß während der
  Navigation; bei Überschreitung wird die Geschwindigkeit rot
- **Blitzer-Warnungen** (abschaltbar): Hinweisbanner + Ansage vor stationären
  Blitzern aus OpenStreetMap, Marker auf der Karte
- **Live-Verkehr** von der Autobahn GmbH (kostenlos, ohne Key): Stau,
  stockender Verkehr, Sperrungen und Baustellen auf Autobahnen – als
  Hinweisbanner und Markierung auf der Karte
- **LKW-POIs** (abschaltbar): LKW-Parkplätze, Raststätten/Autohöfe und
  LKW-taugliche Tankstellen aus OpenStreetMap
- **Durchfahrtshöhen-Warnung** (standardmäßig an): Brücken und Tunnel mit
  lichter Höhe aus OpenStreetMap; Warnung, wenn die eingestellte Fahrzeughöhe
  nicht passt
- **Spurführung**: an Verzweigungen wird die zu nehmende Spur angezeigt, dazu
  eine aus OpenStreetMap generierte Beschilderung (Ausfahrtsnummer und Ziele)
- **Tag/Nacht-Karte**: automatischer Wechsel nach Sonnenauf-/untergang am
  Standort, manuell übersteuerbar (Automatisch/Hell/Dunkel); im dunklen Design
  sind die Straßen hervorgehoben
- **Bedienelemente** in der Firmenfarbe Rot (Zoom, Kompass, Zentrieren, Sprache)
- **PKW-Fallback**, wenn keine LKW-Route gefunden wird (abschaltbar)
- Einstellungen dauerhaft gespeichert (DataStore)

---

## Installation auf dem Handy

1. `LS-TruckNav-0.3.0.apk` auf das Gerät kopieren (USB, Cloud, Messenger).
2. Datei antippen → „Installieren“. Falls gefragt: „Unbekannte Quellen“ für den
   Dateimanager erlauben.
3. Beim ersten Start **Standortberechtigung** erteilen.

Alternativ per USB-Debugging:

```powershell
adb install -r "C:\Users\joshu\Documents\Standardprojekt\LSTruckNav\LS-TruckNav-0.3.0.apk"
```

Voraussetzung: Android 10 oder neuer.

---

## Bedienung

| Aktion | So geht's |
|---|---|
| Ziel suchen | Suchfeld oben antippen, mindestens 3 Zeichen eingeben |
| LKW-Profil ändern | LKW-Symbol oben links |
| Route planen | Ziel auswählen – Route wird automatisch berechnet |
| Navigation starten | „Navigation starten“ in der Routenkarte |
| Sprache an/aus | Lautsprecher-Symbol unten |
| Karte zentrieren | Zielscheiben-Symbol unten |
| Navigation beenden | „Navigation beenden“ |

Während der Navigation bleibt der Bildschirm an.

### Tempolimit und Blitzer

- Das **Tempolimit** wird aus OpenStreetMap über Valhalla (`trace_attributes`)
  für den gerade befahrenen Abschnitt ermittelt und als StVO-Schild 274
  angezeigt (Statusleiste und Navigationsansicht). Fährt man schneller als
  erlaubt, wird die aktuelle Geschwindigkeit rot dargestellt.
- **Blitzer** werden aus OpenStreetMap (Overpass) im Umkreis geladen und auf
  der Karte markiert. Liegt ein Blitzer in Fahrtrichtung voraus (auf der Route),
  erscheint ein Hinweisbanner und – bei aktiver Sprachansage – einmalig eine
  Ansage. Die Warnung lässt sich in den Einstellungen abschalten.
- Beide Angaben stammen aus OpenStreetMap und sind **nicht vollständig**.
  Beschilderung vor Ort hat immer Vorrang. Der öffentliche Overpass-Server
  antwortet unter Last teils erst nach 30–40 s; die Blitzer-Abfrage läuft
  daher im Hintergrund mit großzügigem Timeout und blockiert die Navigation
  nicht.

### Positionsanzeige und Neuberechnung

- GPS- und Netzwerkortung werden zusammengeführt und gefiltert (veraltete,
  doppelte, zu ungenaue und unmögliche Sprünge werden verworfen).
- Position, Kurs und Geschwindigkeit werden zeitbasiert geglättet, die Karte
  folgt mit einer weichen Interpolation statt bei jedem GPS-Fix zu springen.
- Die eigene Position ist ein **Richtungspfeil** (weiß gefüllt, roter Rand),
  der mit der Fahrtrichtung dreht – nativ als Vektor gezeichnet
  (`res/drawable/ic_nav_arrow.xml`), damit er bei jeder Displaydichte scharf
  bleibt.
- Wird die Route verlassen, berechnet die App automatisch neu (frühestens
  alle 8 Sekunden). Schlägt das fehl, erscheint eine Meldung; nach mehreren
  Fehlversuchen hilft ein Neustart der Navigation. Beim Zurückkehren auf die
  Route wird der Zähler zurückgesetzt.
- Abseits der Route zeigt der Fahrzeugpunkt die echte Position, damit die
  Abweichung sichtbar bleibt.

---

## Wichtige Grenzen (bitte lesen)

- **Kein Ersatz für zugelassene LKW-Navigation.** OpenStreetMap-Daten zu
  Brückenhöhen, Gewichtsbeschränkungen und Durchfahrtsverboten sind nicht
  vollständig oder tagesaktuell. **Beschilderung vor Ort hat immer Vorrang.**
- Der voreingestellte Routing-Server `valhalla1.openstreetmap.de` ist ein
  öffentlicher Testserver ohne Verfügbarkeitsgarantie. Für den regelmäßigen
  Einsatz in der Firma sollte ein eigener Valhalla-Server eingetragen werden
  (Einstellungen → Routing-Server).
- Karten- und Routendaten: © OpenStreetMap-Mitwirkende (ODbL).
- **Tempolimits und Blitzer** werden aus OpenStreetMap abgeleitet. Die Daten
  sind unvollständig, können veraltet sein und sind keine amtliche Auskunft.
  Es gibt keine Garantie, dass ein Blitzer erkannt wird; die Anzeige ist nur
  eine Unterstützung. Beschilderung und Verkehrsregeln haben immer Vorrang.
- Die App benötigt eine Internetverbindung (Karte, Suche, Routing, Tempolimit,
  Blitzer). Offline-Karten sind noch nicht enthalten.

---

## Technik

| Baustein | Umsetzung |
|---|---|
| Sprache | Kotlin, Jetpack Compose (Material 3) |
| Karte | MapLibre Native Android 11.5.2 |
| Kartenstil | OpenFreeMap „Liberty" (hell) / „Dark" (dunkel), Vektor-Tiles, kein Key |
| Suche | Photon (Komoot), deutschsprachig |
| Routing | Valhalla, `costing=truck` mit `costing_options` |
| Spurführung | Valhalla `turn_lanes` (Spuren, Beschilderung) |
| Tempolimit | Valhalla `trace_attributes` (Map-Matching, `edge.speed_limit`) |
| Blitzer | OpenStreetMap über Overpass API (`highway=speed_camera`) |
| LKW-POIs & Durchfahrtshöhen | OpenStreetMap über Overpass API |
| Live-Verkehr | Autobahn App API (`verkehr.autobahn.de`, kein Key) |
| Tag/Nacht | NOAA-Sonnenstandsberechnung nach Standort |
| Standort | Android LocationManager, gefiltert und geglättet (kein Google Play Services nötig) |
| Sprache | Android TextToSpeech, deutsch |
| Einstellungen | DataStore Preferences |

### LKW-Parameter (Valhalla)

```
height, width, length (m)
weight, axle_load (t)
axle_count
hazmat
use_tolls, use_highways
```

---

## Selbst bauen

Voraussetzungen (auf diesem Rechner bereits eingerichtet):

- JDK 21: `C:\dev\jdk21`
- Android SDK: `C:\dev\android-sdk` (Platform 35, Build-Tools 35.0.0)
- Gradle 8.9: `C:\dev\gradle-8.9`

```powershell
$env:JAVA_HOME = 'C:\dev\jdk21'
& C:\dev\gradle-8.9\bin\gradle.bat -p "C:\Users\joshu\Documents\Standardprojekt\LSTruckNav" :app:assembleDebug
```

Die Debug-APK liegt danach unter:

```
app\build\outputs\apk\debug\app-debug.apk
```

Oder einfach:

```powershell
.\build-apk.ps1
```

Das Skript baut die Release-Variante (mit Debug-Key signiert, intern
installierbar) und kopiert sie als `LS-TruckNav-0.3.0.apk` ins Projektverzeichnis.

### App-Icon

Das Launcher-Icon wird aus `appicon.jpeg` erzeugt (Motiv wird automatisch
freigestellt bzw. quadratisch zugeschnitten, weiße Fläche normalisiert):

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\generate-icons.ps1
```

Erzeugt werden:

- adaptive Icons (`mipmap-mdpi` … `mipmap-xxxhdpi`, `ic_launcher_foreground.png`)
  mit weißem Hintergrund (`@color/ic_launcher_background`) und Motiv in der Safe-Zone
- Legacy-Icons `ic_launcher.png` und `ic_launcher_round.png` für alle Dichten
- `appicon-512.png` als Referenz-/Doku-Icon

Der Zuschnitt ist auf das Motiv in `appicon.jpeg` abgestimmt
(Ausschnitt `x 676…1076`, `y 121…521`). Bei einem neuen Logo den `$cropRect`
im Skript anpassen.

---

## Projektstruktur

```
app/src/main/java/de/ls/trucknav/
├── MainActivity.kt
├── data/            TruckProfile, SettingsRepository (DataStore)
├── location/        LocationTracker, LocationFilter, LocationSmoother
├── model/           GeoPoint, Place, Maneuver, RouteResult, SpeedCamera
├── nav/             RouteGeometry (Snapping), NavigationEngine
├── net/             ValhallaApi, PhotonApi, SpeedLimitService, SpeedCameraService, Http
├── tts/             SpeechPlayer
├── ui/              MapScreen, Such- und Einstellungs-Sheets, Theme
│   ├── components/  StVO-Schilder (Tempolimit 274, Warndreieck 101)
│   └── map/         MapLibre-Einbindung, Ebenen, Kamera
└── util/            Geo-Mathematik, Polyline6, Formatierung
```

`ui-assets/` enthält das gelieferte Design-Blatt als Referenz (siehe
`ui-assets/README.md`); die Symbole der App sind nativ als Vektor umgesetzt.

---

## Nächste Ausbaustufen

1. **Eigener Valhalla-Server** (Docker) – stabile Routing-Ergebnisse, keine Rate-Limits.
2. **LKW-Parkplätze, Raststätten, Tankstellen** als POIs entlang der Route.
3. **Offline-Karten** und Offline-Routing für Regionen.
4. **Umleitungen / Verkehr** über Valhalla `exclude_locations` oder Traffic-Daten.
5. **Lenk- und Ruhezeiten** – Anbindung an die vorhandenen Tools
   (`Fahrerkarte`, `Spesen`) wäre naheliegend.
6. **Fahrtenbuch** und Export der gefahrenen Strecken.

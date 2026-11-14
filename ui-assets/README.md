# UI-Assets (Referenz)

`ui-assets-sheet.png` ist das vom Auftraggeber gelieferte Design-Blatt
„LS TRUCKK NAV – UI-ASSETS" (1024 × 572 px). Es dient **nur als Design-Referenz**.

## Warum daraus keine Icons übernommen werden

- Das Blatt liegt nur in 1024 × 572 px vor; die einzelnen Icons sind
  entsprechend klein (ca. 66 × 70 px) und auf dunklem Grund abgelegt.
- Für Launcher-Icons, Kartenmarker und Statusanzeigen sind scharfe,
  skalierbare Vektoren nötig (verschiedene Displaydichten, Zooms).
- Die Symbole sind teils Pixel-/Emoji-Stil und nicht einheitlich.

Deshalb werden die benötigten Symbole **nativ als Vektor** gezeichnet –
insbesondere die StVO-Schilder (siehe `ui/components/StvoSigns.kt`).

## Inhalt des Blattes (Abschnitte)

| Abschnitt | Inhalt | Verwendung in der App |
|---|---|---|
| A | Marken-Elemente: Logo, App-Icon, Wortmarke, Splash (2560 × 1600) | App-Icon bereits aus `appicon.jpeg` erzeugt; Splash zeigt das Brand-Icon |
| B | Status & Warnungen: Lenkzeit, Tank niedrig, Durchfahrtshöhe 3,80 m, Wartung, Übergewicht, Speed-Streak | Warnfarben/-stil als Referenz; konkrete Meldungen folgen mit den Fachmodulen |
| C | Aktions-Icons: Navigieren, Problem melden, Mein LKW, Position, Abbiegepfeile, POI-Marker | Abbiegepfeile sind bereits als Material-Icons umgesetzt |
| E | UI-Komponenten: Karten-UI, Tachograph-UI, Routenliste, Dateiformate (.SVC .XML .PNC .WEBP) | Referenz für spätere Ausbaustufen (Tachograph, Routenliste) |

## Neu gezeichnete Symbole

| Symbol | Datei | Vorbild |
|---|---|---|
| Tempolimit | `SpeedLimitSign` | StVO-Zeichen 274 (weißer Kreis, roter Ring, schwarze Zahl) |
| Gefahr/Blitzer | `WarningTriangle` | StVO-Zeichen 101 (Warndreieck) als allgemeine Warnung |
| Eigene Position | `ic_nav_arrow.xml` | Richtungspfeil (Abschnitt C „Position"), dreht mit der Fahrtrichtung |

Die Blitzerwarnung nutzt bewusst das allgemeine Warndreieck, da Blitzer
selbst keine StVO-Schilder sind.

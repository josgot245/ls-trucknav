package de.ls.trucknav.data

/** Auswahl des Kartenstils (hell/dunkel). */
enum class MapStyleMode {
    /** Automatisch: hell am Tag, dunkel in der Nacht (nach Sonnenstand). */
    AUTO,

    /** Immer helle Karte. */
    LIGHT,

    /** Immer dunkle Karte. */
    DARK,
}

/** URLs der verfuegbaren OpenFreeMap-Kartenstile. */
object MapStyles {
    const val LIGHT = "https://tiles.openfreemap.org/styles/liberty"
    const val DARK = "https://tiles.openfreemap.org/styles/dark"

    /** Liefert die Stil-URL fuer den Modus; [night] gilt nur bei [MapStyleMode.AUTO]. */
    fun url(mode: MapStyleMode, night: Boolean): String = when (mode) {
        MapStyleMode.LIGHT -> LIGHT
        MapStyleMode.DARK -> DARK
        MapStyleMode.AUTO -> if (night) DARK else LIGHT
    }
}

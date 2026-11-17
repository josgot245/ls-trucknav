package de.ls.trucknav.data

/**
 * Sortierung der Routenvorschlaege in der Vorschau. Die Wahl wird gespeichert,
 * damit sie beim naechsten Start erhalten bleibt.
 */
enum class RouteSort {
    /** Schnellste zuerst (nach Fahrzeit). */
    DURATION,

    /** Kuerzeste zuerst (nach Laenge). */
    LENGTH,
}

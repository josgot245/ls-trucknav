package de.ls.trucknav.data

/** Fahrzeugprofil fuer die LKW-Routenberechnung (Valhalla "truck" costing). */
data class TruckProfile(
    /** Gesamthoehe in Metern. */
    val height: Double = 4.0,
    /** Gesamtbreite in Metern. */
    val width: Double = 2.55,
    /** Gesamtlaenge in Metern (Zug inkl. Auflieger). */
    val length: Double = 18.75,
    /** Gesamtgewicht in Tonnen. */
    val weight: Double = 40.0,
    /** Achslast in Tonnen. */
    val axleLoad: Double = 11.5,
    /** Anzahl Achsen. */
    val axles: Int = 5,
    /** Gefahrgut an Bord. */
    val hazmat: Boolean = false,
    /** Mautpflichtige Strassen vermeiden. */
    val avoidTolls: Boolean = false,
    /** Autobahnen vermeiden. */
    val avoidHighways: Boolean = false,
) {
    fun sanitized(): TruckProfile = copy(
        height = height.coerceIn(1.5, 5.0),
        width = width.coerceIn(1.5, 3.5),
        length = length.coerceIn(3.0, 30.0),
        weight = weight.coerceIn(1.0, 80.0),
        axleLoad = axleLoad.coerceIn(1.0, 20.0),
        axles = axles.coerceIn(2, 12),
    )

    /** Kurztext fuer die Kopfzeile, z. B. "4,0 m · 40 t". */
    fun summary(): String = String.format(
        java.util.Locale.GERMANY,
        "%.1f m · %.0f t · %d Achsen",
        height,
        weight,
        axles,
    )

    companion object {
        val DEFAULT = TruckProfile()
    }
}

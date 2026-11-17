package de.ls.trucknav.data

/**
 * Fahrzeugmodus der App.
 *
 * Standard ist der LKW-Modus mit vollem Fahrzeugprofil (Maße, Gewicht,
 * Achsen). Im PKW-Modus wird wie ein Auto geroutet und es gelten die
 * allgemeinen Tempolimits; die LKW-Maße bleiben gespeichert, werden aber
 * nicht verwendet.
 */
enum class VehicleMode {
    /** Routen mit LKW-Profil, LKW-Tempolimits und Durchfahrtshoehen-Warnung. */
    TRUCK,

    /** Routen wie ein PKW, ohne LKW-Beschraenkungen. */
    CAR,
}

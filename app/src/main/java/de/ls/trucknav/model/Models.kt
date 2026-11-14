package de.ls.trucknav.model

/** Ein geografischer Punkt in Grad. */
data class GeoPoint(val lat: Double, val lon: Double)

/** Ein Suchtreffer bzw. ein Ziel. */
data class Place(
    val name: String,
    val subtitle: String,
    val point: GeoPoint,
)

/**
 * Eine einzelne Fahrtspur an der naechsten Verzweigung.
 *
 * [directions] ist eine Bitmaske der moeglichen Fahrtrichtungen (siehe
 * [LaneGuide.Companion]); [active] markiert die Spur, die die Route nimmt.
 */
data class LaneGuide(
    val directions: Int,
    val active: Boolean,
) {
    /** true, wenn die Spur die angegebene Richtung erlaubt. */
    fun has(direction: Int): Boolean = directions and direction != 0

    companion object {
        const val NONE = 1
        const val THROUGH = 2
        const val SHARP_LEFT = 4
        const val LEFT = 8
        const val SLIGHT_LEFT = 16
        const val SLIGHT_RIGHT = 32
        const val RIGHT = 64
        const val SHARP_RIGHT = 128
        const val U_TURN = 256
        const val MERGE_LEFT = 512
        const val MERGE_RIGHT = 1024

        /**
         * Waehlt die fuer die Anzeige wichtigste Richtung, wenn eine Spur
         * mehrere Richtungen erlaubt (z. B. gerade + rechts).
         */
        fun primaryDirection(directions: Int): Int = when {
            directions and U_TURN != 0 -> U_TURN
            directions and SHARP_LEFT != 0 -> SHARP_LEFT
            directions and SHARP_RIGHT != 0 -> SHARP_RIGHT
            directions and LEFT != 0 -> LEFT
            directions and RIGHT != 0 -> RIGHT
            directions and SLIGHT_LEFT != 0 -> SLIGHT_LEFT
            directions and SLIGHT_RIGHT != 0 -> SLIGHT_RIGHT
            directions and MERGE_LEFT != 0 -> MERGE_LEFT
            directions and MERGE_RIGHT != 0 -> MERGE_RIGHT
            directions and THROUGH != 0 -> THROUGH
            else -> NONE
        }
    }
}

/**
 * Generierte Beschilderung einer Ausfahrt/Verzweigung (Autobahn-Schild).
 * Alle Angaben stammen aus OpenStreetMap und ersetzen keine reale Beschilderung.
 */
data class JunctionSign(
    /** Ausfahrtsnummer, z. B. "22". */
    val exitNumber: String?,
    /** Zielorte auf dem Schild (Richtung). */
    val toward: List<String>,
    /** Name der Ausfahrt, falls vorhanden. */
    val name: String?,
)

/** Ein Fahranweisungsschritt aus der Route. */
data class Maneuver(
    val index: Int,
    val type: Int,
    val instruction: String,
    val verbal: String,
    val streetNames: List<String>,
    val startDistance: Double,
    val length: Double,
    val time: Double,
    /** Fahrtspuren an der Verzweigung (leer, wenn unbekannt). */
    val lanes: List<LaneGuide> = emptyList(),
    /** Beschilderung der Verzweigung (null, wenn unbekannt). */
    val sign: JunctionSign? = null,
)

/** Ein stationaerer Blitzer aus OpenStreetMap. */
data class SpeedCamera(
    val id: Long,
    val point: GeoPoint,
    val maxSpeedKmh: Int?,
    val name: String?,
)

/** Art einer Verkehrsmeldung der Autobahn GmbH. */
enum class TrafficKind {
    /** Verkehrsmeldung (Stau, stockender Verkehr, Unfall …). */
    TRAFFIC,

    /** Sperrung. */
    CLOSURE,

    /** Baustelle. */
    ROADWORKS,
}

/**
 * Eine Verkehrsmeldung von der Autobahn GmbH (verkehr.autobahn.de).
 * Gilt nur fuer Bundesautobahnen.
 */
data class TrafficWarning(
    val id: String,
    val point: GeoPoint,
    val title: String,
    val subtitle: String?,
    val kind: TrafficKind,
    val blocked: Boolean,
    val averageSpeedKmh: Int?,
    val delayMinutes: Int?,
    /** true bei Stau/stockendem Verkehr. */
    val jam: Boolean,
)

/** Art eines LKW-relevanten Punktes aus OpenStreetMap. */
enum class TruckPoiKind {
    /** LKW-Parkplatz. */
    PARKING,

    /** Raststaette / Autohof. */
    REST,

    /** Tankstelle (LKW-tauglich). */
    FUEL,
}

/** Ein LKW-relevanter Punkt (Parkplatz, Raststaette, Tankstelle). */
data class TruckPoi(
    val id: Long,
    val point: GeoPoint,
    val name: String?,
    val kind: TruckPoiKind,
)

/** Eine Durchfahrtshoehen-Beschraenkung aus OpenStreetMap. */
data class HeightRestriction(
    val id: Long,
    val point: GeoPoint,
    /** Zulaessige Durchfahrtshoehe in Metern. */
    val maxHeightM: Double,
    val name: String?,
)

/** Eine berechnete Route inklusive Fahranweisungen. */
data class RouteResult(
    val points: List<GeoPoint>,
    val maneuvers: List<Maneuver>,
    val distance: Double,
    val time: Double,
    val destination: Place?,
    val truckUsed: Boolean,
)

package de.ls.trucknav.util

import de.ls.trucknav.model.GeoPoint
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Bestimmt, ob es an einem Ort zu einer Zeit dunkel ist (Sonne unter dem
 * Horizont). Grundlage ist der bekannte Sonnenstands-Algorithmus
 * (NOAA / "Almanac"), Genauigkeit im Bereich von ein bis zwei Minuten.
 *
 * Ohne Standort wird ein fester Zeitraum angenommen (hell von 07:00 bis
 * 19:00 Uhr), damit auch vor dem ersten GPS-Fix ein sinnvoller Stil gewaehlt
 * wird.
 */
object DayNight {

    /** Offizieller Sonnenauf-/-untergang: Sonnenmittelpunkt 0,833 Grad unter dem Horizont. */
    private const val ZENITH_DEG = 90.833

    /** Rueckfall-Zeitraum ohne Standort: hell von 07:00 bis 19:00 Uhr. */
    private const val FALLBACK_DAY_START_MIN = 7 * 60
    private const val FALLBACK_DAY_END_MIN = 19 * 60

    /** Sonnenauf- und -untergang als Minuten seit Mitternacht (lokal). */
    data class SunTimes(val sunriseMinutes: Int, val sunsetMinutes: Int)

    /**
     * @return true, wenn es am Standort dunkel ist (Sonne untergegangen oder
     *         noch nicht aufgegangen).
     */
    fun isNight(
        location: GeoPoint?,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): Boolean {
        val minutes = localMinutes(nowMillis, timeZone)
        val sun = location?.let { sunTimes(it, nowMillis, timeZone) }
        if (sun == null) {
            return minutes < FALLBACK_DAY_START_MIN || minutes >= FALLBACK_DAY_END_MIN
        }
        return minutes < sun.sunriseMinutes || minutes >= sun.sunsetMinutes
    }

    /**
     * Berechnet Sonnenauf- und -untergang fuer den Tag von [nowMillis].
     *
     * @return lokale Minuten seit Mitternacht oder `null` bei Polartag/-nacht.
     */
    fun sunTimes(
        location: GeoPoint,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): SunTimes? {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
        val lat = location.lat
        val lon = location.lon

        val sunrise = event(cal, dayOfYear, lat, lon, sunrise = true, timeZone) ?: return null
        val sunset = event(cal, dayOfYear, lat, lon, sunrise = false, timeZone) ?: return null
        return SunTimes(sunrise, sunset)
    }

    private fun localMinutes(millis: Long, timeZone: TimeZone): Int {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    private fun event(
        date: Calendar,
        dayOfYear: Int,
        lat: Double,
        lon: Double,
        sunrise: Boolean,
        tz: TimeZone,
    ): Int? {
        val lngHour = lon / 15.0
        val t = dayOfYear + ((if (sunrise) 6.0 else 18.0) - lngHour) / 24.0
        val m = (0.9856 * t) - 3.289
        var l = m + (1.916 * sin(Math.toRadians(m))) +
            (0.020 * sin(Math.toRadians(2 * m))) + 282.634
        l = normalize(l, 360.0)

        var ra = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(l))))
        ra = normalize(ra, 360.0)
        val lQuadrant = floor(l / 90.0) * 90.0
        val raQuadrant = floor(ra / 90.0) * 90.0
        ra += (lQuadrant - raQuadrant)
        ra /= 15.0

        val sinDec = 0.39782 * sin(Math.toRadians(l))
        val cosDec = cos(asin(sinDec))
        val cosH = (cos(Math.toRadians(ZENITH_DEG)) - (sinDec * sin(Math.toRadians(lat)))) /
            (cosDec * cos(Math.toRadians(lat)))
        // Sonne geht nie auf (> 1) bzw. nie unter (< -1): Polartag/-nacht.
        if (cosH > 1.0 || cosH < -1.0) return null

        var h = if (sunrise) 360.0 - Math.toDegrees(acos(cosH)) else Math.toDegrees(acos(cosH))
        h /= 15.0

        val localMeanTime = h + ra - (0.06571 * t) - 6.622
        val utcHours = normalize(localMeanTime - lngHour, 24.0)
        return utcToLocalMinutes(date, utcHours, tz)
    }

    /** Rechnet UTC-Stunden (0..24) des Tages in lokale Minuten um (DST-sicher). */
    private fun utcToLocalMinutes(date: Calendar, utcHours: Double, tz: TimeZone): Int {
        val baseUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                date.get(Calendar.YEAR),
                date.get(Calendar.MONTH),
                date.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0,
            )
        }
        val millis = baseUtc.timeInMillis + Math.round(utcHours * 60.0) * 60_000L
        val local = Calendar.getInstance(tz).apply { timeInMillis = millis }
        return local.get(Calendar.HOUR_OF_DAY) * 60 + local.get(Calendar.MINUTE)
    }

    private fun normalize(value: Double, range: Double): Double {
        val result = value % range
        return if (result < 0) result + range else result
    }
}

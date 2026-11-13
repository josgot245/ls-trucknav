package de.ls.trucknav.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

fun formatDistance(meters: Double): String {
    if (meters < 950) {
        val m = (meters / 10.0).roundToInt() * 10
        return "$m m"
    }
    return String.format(Locale.GERMANY, "%.1f km", meters / 1000.0)
}

fun formatDuration(seconds: Double): String {
    val totalMinutes = (seconds / 60.0).roundToInt().coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}

fun formatEta(seconds: Double): String {
    val millis = System.currentTimeMillis() + (seconds * 1000).toLong()
    return SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(millis))
}

fun formatSpeed(kmh: Double): String =
    if (kmh < 1.0) "0 km/h" else "${kmh.roundToInt()} km/h"

/** Abstand neutral runden (z. B. 342 -> 350), wie in Navigationsansaagen ueblich. */
fun roundedDistance(meters: Double): Int {
    if (meters < 100) return ((meters / 10.0).roundToInt() * 10).coerceAtLeast(10)
    if (meters < 400) return ((meters / 50.0).roundToInt() * 50).coerceAtLeast(50)
    if (meters < 1000) return (meters / 100.0).roundToInt() * 100
    return (meters / 500.0).roundToInt() * 500
}

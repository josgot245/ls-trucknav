package de.ls.trucknav.location

import android.location.Location
import android.location.LocationManager

/**
 * Siebt rohe Standortmeldungen aus, bevor sie in die Navigation gehen.
 *
 * Ohne Filter kommen GPS- und Netzwerkortung im Wechsel an. Die
 * Netzwerkortung springt dabei gern um mehrere hundert Meter und laesst
 * die Position (und damit Kamera und Fahrzeugpunkt) zappeln. Ausserdem
 * werden veraltete, doppelte und offensichtlich unmoegliche Fixes verworfen.
 */
class LocationFilter {

    private var lastAccepted: Location? = null
    private var lastGpsElapsedMs = 0L

    fun reset() {
        lastAccepted = null
        lastGpsElapsedMs = 0L
    }

    /**
     * @return den Fix, wenn er verwendet werden soll, sonst null.
     */
    fun accept(location: Location): Location? {
        // 0/0 ist ein typischer Platzhalter fuer "kein Fix".
        if (location.latitude == 0.0 && location.longitude == 0.0) return null

        // Zu alte Fixes (z. B. der letzte bekannte Standort von gestern)
        // wuerden die Navigation sofort aus der Route werfen.
        if (location.time > 0L && System.currentTimeMillis() - location.time > MAX_AGE_MS) {
            return null
        }

        val now = elapsedMs(location)
        val isGps = location.provider == LocationManager.GPS_PROVIDER
        val hasElapsed = location.elapsedRealtimeNanos > 0L
        if (isGps && hasElapsed) lastGpsElapsedMs = now

        // Netzwerkortung nur als Lueckenfueller, solange (noch) kein GPS da ist.
        if (!isGps && hasElapsed && now - lastGpsElapsedMs < NETWORK_GRACE_MS) return null

        val accuracy = if (location.hasAccuracy()) location.accuracy else BAD_ACCURACY_M
        val previous = lastAccepted
        val maxAccuracy = if (previous == null) FIRST_FIX_ACCURACY_M else MAX_ACCURACY_M
        if (accuracy > maxAccuracy) return null

        if (previous != null && hasElapsed) {
            val dt = (now - elapsedMs(previous)) / 1000.0
            // verspaetet eintreffende Fixes verwerfen
            if (dt < -0.5) return null
            // exakte Dublette (gleicher Fix ueber mehrere Listener)
            if (dt <= 0.4 && location.distanceTo(previous) < 0.5f) return null
            // unmoeglicher Sprung (GPS-Aussetzer/Teleport)
            if (dt > 0.3 && location.distanceTo(previous) / dt > MAX_IMPLIED_SPEED_MPS) {
                return null
            }
        }

        lastAccepted = location
        return location
    }

    private fun elapsedMs(location: Location): Long =
        if (location.elapsedRealtimeNanos > 0L) {
            location.elapsedRealtimeNanos / 1_000_000L
        } else {
            location.time
        }

    private companion object {
        /** So lange nach einem GPS-Fix wird die Netzwerkortung ignoriert. */
        const val NETWORK_GRACE_MS = 15_000L

        /** Fixes aelter als zwei Minuten sind fuer die Navigation wertlos. */
        const val MAX_AGE_MS = 120_000L

        /** Erster Fix darf grob sein, damit die Karte schnell etwas zeigt. */
        const val FIRST_FIX_ACCURACY_M = 250f

        /** Schlechtere Fixes bringen nur Unruhe in Position und Kamera. */
        const val MAX_ACCURACY_M = 100f

        /** ~290 km/h – alles darueber ist ein Sprung, kein Fahren. */
        const val MAX_IMPLIED_SPEED_MPS = 80.0

        const val BAD_ACCURACY_M = 9_999f
    }
}

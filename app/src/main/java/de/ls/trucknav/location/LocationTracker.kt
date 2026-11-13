package de.ls.trucknav.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Standortermittlung ohne Google Play Services.
 * Nutzt GPS (1 s) und Netzwerkortung (5 s) direkt ueber den LocationManager.
 */
class LocationTracker(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var callback: ((Location) -> Unit)? = null
    private var running = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            callback?.invoke(location)
        }

        @Deprecated("Deprecated in API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    fun hasPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun lastKnown(): Location? {
        if (!hasPermission()) return null
        return try {
            val candidates = listOfNotNull(
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER),
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER),
            )
            val newest = candidates.maxByOrNull { it.time } ?: return null
            // Ein alter Cache-Wert wuerde die Karte an die falsche Stelle setzen.
            if (System.currentTimeMillis() - newest.time > 120_000L) null else newest
        } catch (e: SecurityException) {
            null
        }
    }

    fun start(onLocation: (Location) -> Unit): Boolean {
        callback = onLocation
        if (!hasPermission()) return false

        lastKnown()?.let(onLocation)
        if (running) return true

        return try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    10000L,
                    25f,
                    listener,
                    Looper.getMainLooper(),
                )
            }
            running = true
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    fun stop() {
        if (!running) return
        try {
            locationManager.removeUpdates(listener)
        } catch (e: Exception) {
            // Ignorieren: Tracker wird ohnehin verworfen.
        }
        running = false
    }
}

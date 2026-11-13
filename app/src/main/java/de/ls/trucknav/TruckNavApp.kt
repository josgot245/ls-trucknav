package de.ls.trucknav

import android.app.Application
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer

/**
 * MapLibre muss vor dem ersten MapView initialisiert werden.
 * OpenFreeMap liefert die Kacheln ohne API-Key, daher leerer Key.
 */
class TruckNavApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this, "", WellKnownTileServer.MapLibre)
    }
}

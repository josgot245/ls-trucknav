package de.ls.trucknav.ui.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Bruecke zwischen den Compose-Bedienelementen und der MapLibre-Karte.
 *
 * [de.ls.trucknav.ui.MapScreen] legt eine Instanz an, reicht sie an
 * [TruckMapView] weiter und loest darueber Zoom und Kompass aus. Die
 * eigentlichen Aktionen werden erst gesetzt, sobald die Karte bereit ist.
 *
 * [northUp] ist beobachtbar, damit das Kompass-Symbol den aktuellen Zustand
 * anzeigt (Norden ausgerichtet vs. in Fahrtrichtung).
 */
class MapControls {

    /** true = Karte nach Norden ausgerichtet, false = in Fahrtrichtung. */
    var northUp by mutableStateOf(false)

    internal var zoomInAction: (() -> Unit)? = null
    internal var zoomOutAction: (() -> Unit)? = null
    internal var compassAction: (() -> Unit)? = null

    fun zoomIn() {
        zoomInAction?.invoke()
    }

    fun zoomOut() {
        zoomOutAction?.invoke()
    }

    fun compass() {
        compassAction?.invoke()
    }
}

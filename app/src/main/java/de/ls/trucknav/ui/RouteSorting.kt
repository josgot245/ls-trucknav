package de.ls.trucknav.ui

import de.ls.trucknav.data.RouteSort
import de.ls.trucknav.model.RouteResult

/**
 * Sortiert die Routenvorschlaege fuer die Anzeige. Der Originalindex bleibt in
 * [IndexedValue.index] erhalten, damit die Auswahl (selectedRouteIndex) auch
 * nach dem Umsortieren auf die richtige Route zeigt.
 */
internal fun sortRouteOptions(
    options: List<RouteResult>,
    sort: RouteSort,
): List<IndexedValue<RouteResult>> = when (sort) {
    RouteSort.DURATION -> options.withIndex().sortedBy { it.value.time }
    RouteSort.LENGTH -> options.withIndex().sortedBy { it.value.distance }
}

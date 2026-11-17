package de.ls.trucknav.ui

import de.ls.trucknav.model.RouteResult

/**
 * Eine Zeile der Routenvorschlagsliste: Beschriftung und zugehoerige Route.
 * [index] ist der Originalindex in [de.ls.trucknav.ui.UiState.routeOptions],
 * damit die Auswahl (selectedRouteIndex) auch nach dem Umsortieren stimmt.
 */
internal data class RouteOptionEntry(
    val index: Int,
    val route: RouteResult,
    val label: String,
)

/**
 * Beschriftet die Routenvorschlaege in fester Reihenfolge: zuerst die
 * schnellste Route ("Schnellste"), danach die kuerzeste ("Kuerzeste") und
 * anschliessend die uebrigen als "Alternative" (bei mehreren durchnummeriert).
 *
 * Ist die schnellste Route zugleich die kuerzeste, entfaellt die eigene
 * "Kuerzeste"-Zeile; die restlichen Routen sind dann Alternativen.
 */
internal fun labelRouteOptions(options: List<RouteResult>): List<RouteOptionEntry> {
    if (options.isEmpty()) return emptyList()
    val fastest = options.indices.minByOrNull { options[it].time } ?: return emptyList()
    val shortest = options.indices.minByOrNull { options[it].distance } ?: fastest
    val distinctShortest = shortest != fastest

    val order = buildList {
        add(fastest)
        if (distinctShortest) add(shortest)
        options.indices.forEach { if (it != fastest && it != shortest) add(it) }
    }

    val alternativeCount = order.size - 1 - if (distinctShortest) 1 else 0
    var alternativeNumber = 0
    return order.mapIndexed { position, index ->
        val label = when {
            position == 0 -> "Schnellste"
            position == 1 && distinctShortest -> "Kürzeste"
            else -> {
                alternativeNumber++
                if (alternativeCount > 1) "Alternative $alternativeNumber" else "Alternative"
            }
        }
        RouteOptionEntry(index = index, route = options[index], label = label)
    }
}

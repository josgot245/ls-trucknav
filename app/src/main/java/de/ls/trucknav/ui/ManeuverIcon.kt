package de.ls.trucknav.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.UTurnRight
import androidx.compose.ui.graphics.vector.ImageVector

/** Valhalla-Manövertyp -> Material-Icon. */
fun maneuverIcon(type: Int): ImageVector = when (type) {
    2, 9, 10, 11, 18, 20, 23, 37 -> Icons.Filled.TurnRight
    3, 14, 15, 16, 19, 21, 24, 38 -> Icons.Filled.TurnLeft
    12 -> Icons.Filled.UTurnRight
    13 -> Icons.Filled.UTurnLeft
    25 -> Icons.Filled.Merge
    26, 27 -> Icons.Filled.RoundaboutRight
    4, 5, 6 -> Icons.Filled.Flag
    else -> Icons.Filled.Navigation
}

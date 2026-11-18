package de.ls.trucknav.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.UTurnRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

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

/** true, wenn der Manövertyp ein Kreisverkehr ist (Einfahrt oder Ausfahrt). */
fun isRoundabout(type: Int): Boolean = type == 26 || type == 27

/**
 * Fahranweisungs-Symbol. Kreisverkehre werden eigens gezeichnet: für
 * Rechtsverkehr (Gegenverkehr im Uhrzeigersinn) und mit der zu nehmenden
 * Ausfahrt, damit die Anweisung eindeutig ist.
 */
@Composable
fun ManeuverGlyph(
    type: Int,
    roundaboutExit: Int?,
    size: Dp,
    tint: Color,
) {
    if (isRoundabout(type)) {
        RoundaboutGlyph(roundaboutExit = roundaboutExit, size = size, tint = tint)
    } else {
        Icon(
            imageVector = maneuverIcon(type),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}

/**
 * Kreisverkehr-Symbol für Rechtsverkehr: Die Ausfahrten sind – von der
 * Zufahrt unten aus gesehen – gegen den Uhrzeigersinn angeordnet (1. Ausfahrt
 * rechts, 2. oben, 3. links, 4. unten). Die zu nehmende Ausfahrt wird mit
 * Pfeil und Nummer hervorgehoben.
 */
@Composable
private fun RoundaboutGlyph(roundaboutExit: Int?, size: Dp, tint: Color) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val ringRadius = s * 0.28f
            val stroke = s * 0.09f

            // Ring des Kreisverkehrs.
            drawCircle(
                color = tint,
                radius = ringRadius,
                center = center,
                style = Stroke(width = stroke),
            )
            // Zufahrt von unten in den Kreisverkehr.
            drawLine(
                color = tint,
                start = Offset(center.x, center.y + s * 0.50f),
                end = Offset(center.x, center.y + ringRadius),
                strokeWidth = stroke,
            )

            val selected = roundaboutExit?.coerceIn(1, 4)
            for (exit in 1..4) {
                // Rechtsverkehr: gegen den Uhrzeigersinn, 1. Ausfahrt rechts.
                val angle = Math.toRadians((-(exit - 1) * 90).toDouble())
                val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                val inner = center + dir * ringRadius
                if (exit == selected) {
                    val tip = center + dir * (ringRadius + s * 0.22f)
                    drawLine(color = tint, start = inner, end = tip, strokeWidth = stroke)
                    drawArrowHead(tip = tip, dir = dir, color = tint, headSize = s * 0.14f)
                } else {
                    val stub = center + dir * (ringRadius + s * 0.06f)
                    drawLine(color = tint, start = inner, end = stub, strokeWidth = stroke * 0.7f)
                }
            }
        }
        if (roundaboutExit != null) {
            Text(
                text = roundaboutExit.toString(),
                color = tint,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.30f).sp,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** Pfeilspitze an [tip], ausgerichtet entlang [dir]. */
private fun DrawScope.drawArrowHead(tip: Offset, dir: Offset, color: Color, headSize: Float) {
    val perp = Offset(-dir.y, dir.x)
    val base = tip - dir * headSize
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        val p1 = base + perp * (headSize * 0.62f)
        lineTo(p1.x, p1.y)
        val p2 = base - perp * (headSize * 0.62f)
        lineTo(p2.x, p2.y)
        close()
    }
    drawPath(path, color)
}

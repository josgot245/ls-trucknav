package de.ls.trucknav.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** StVO-Rot, wie es auf Verkehrsschildern verwendet wird. */
val StvoRed = Color(0xFFD32029)

private val SignWhite = Color(0xFFF7F4EE)
private val SignBlack = Color(0xFF161616)

/**
 * Verkehrszeichen 274 „Zulaessige Hoechstgeschwindigkeit": weisser Kreis,
 * roter Ring, schwarze Zahl. Als Vektor gezeichnet, daher in jeder Groesse
 * scharf (im Gegensatz zu den Pixel-Icons aus dem UI-Sheet).
 */
@Composable
fun SpeedLimitSign(
    limitKmh: Int,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val ringWidth = this.size.minDimension * 0.13f
            drawCircle(color = SignWhite)
            drawCircle(
                color = StvoRed,
                radius = this.size.minDimension / 2f - ringWidth / 2f,
                style = Stroke(width = ringWidth),
            )
        }
        Text(
            text = limitKmh.toString(),
            color = SignBlack,
            fontWeight = FontWeight.Bold,
            fontSize = digitSize(limitKmh, size).sp,
        )
    }
}

/** Schriftgroesse je nach Stellenanzahl, damit auch 100/120 passen. */
private fun digitSize(limitKmh: Int, size: Dp): Float = when {
    limitKmh >= 100 -> size.value * 0.30f
    limitKmh >= 10 -> size.value * 0.40f
    else -> size.value * 0.46f
}

/**
 * Allgemeines Gefahrenzeichen (StVO 101) als Warndreieck. Wird fuer die
 * Blitzerwarnung verwendet – Blitzer selbst sind keine StVO-Schilder.
 */
@Composable
fun WarningTriangle(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height
            val triangle = Path().apply {
                moveTo(w / 2f, h * 0.06f)
                lineTo(w * 0.96f, h * 0.92f)
                lineTo(w * 0.04f, h * 0.92f)
                close()
            }
            drawPath(triangle, color = SignWhite)
            drawPath(
                path = triangle,
                color = StvoRed,
                style = Stroke(width = w * 0.085f, join = StrokeJoin.Round),
            )
        }
        Text(
            text = "!",
            color = SignBlack,
            fontWeight = FontWeight.Black,
            fontSize = (size.value * 0.40f).sp,
            modifier = Modifier.padding(top = size * 0.14f),
        )
    }
}

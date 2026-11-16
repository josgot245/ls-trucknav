package de.ls.trucknav.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import org.maplibre.android.maps.Style

/**
 * Zeichnet die Kartenmarker (Ziel, Blitzer, Verkehrsmeldungen, LKW-Punkte und
 * Durchfahrtshoehen) als Bitmaps und registriert sie im Kartenstil.
 *
 * Dadurch werden statt einfarbiger Kreise echte Symbole angezeigt (Stil wie
 * Verkehrszeichen bzw. uebliche Navigations-Piktogramme). Alles wird zur
 * Laufzeit gezeichnet, es werden also keine Bilddateien benoetigt.
 */
object MapIconFactory {

    /** Rastergroesse eines Symbols in dp (scharf auch auf hochaufloesenden Displays). */
    private const val ICON_DP = 30

    const val DESTINATION = "ls-icon-destination"
    const val CAMERA = "ls-icon-camera"
    const val TRAFFIC = "ls-icon-traffic"
    const val CLOSURE = "ls-icon-closure"
    const val ROADWORKS = "ls-icon-roadworks"
    const val PARKING = "ls-icon-parking"
    const val REST = "ls-icon-rest"
    const val FUEL = "ls-icon-fuel"
    const val HEIGHT = "ls-icon-height"

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val INK = 0xFF161616.toInt()
    private const val POI_BLUE = 0xFF3DA5FF.toInt()
    private const val POI_GREEN = 0xFF37C871.toInt()
    private const val POI_PURPLE = 0xFFB06BFF.toInt()
    private const val AMBER = 0xFFFFC246.toInt()
    private const val ORANGE = 0xFFFF8A3D.toInt()
    private const val RED = 0xFFE02B36.toInt()
    private const val DARK = 0xFF0B141B.toInt()

    /** Registriert alle Symbole im Stil (idempotent). */
    fun addAll(style: Style, context: Context) {
        val metrics = context.resources.displayMetrics
        val size = (ICON_DP * metrics.density).toInt().coerceAtLeast(48)

        fun icon(id: String, draw: (Canvas, Float) -> Unit) {
            if (style.getImage(id) != null) return
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            bitmap.density = metrics.densityDpi
            draw(Canvas(bitmap), size.toFloat())
            style.addImage(id, bitmap)
        }

        icon(DESTINATION) { c, s -> badge(c, s, AMBER, DARK); flag(c, s, INK) }
        icon(CAMERA) { c, s -> badge(c, s, RED, WHITE); camera(c, s) }
        icon(TRAFFIC) { c, s -> badge(c, s, AMBER, WHITE); bars(c, s, INK) }
        icon(CLOSURE) { c, s -> badge(c, s, RED, WHITE); bar(c, s, WHITE) }
        icon(ROADWORKS) { c, s -> badge(c, s, ORANGE, WHITE); exclamation(c, s, INK) }
        icon(PARKING) { c, s -> badge(c, s, POI_BLUE, WHITE); letter(c, s, "P", WHITE) }
        icon(REST) { c, s -> badge(c, s, POI_GREEN, WHITE); letter(c, s, "R", WHITE) }
        icon(FUEL) { c, s -> badge(c, s, POI_PURPLE, WHITE); letter(c, s, "T", WHITE) }
        icon(HEIGHT) { c, s -> badge(c, s, WHITE, RED); letter(c, s, "m", INK) }
    }

    /** Runder Marker mit Rand. */
    private fun badge(c: Canvas, s: Float, fill: Int, ring: Int) {
        val stroke = s * 0.09f
        val radius = s / 2f - stroke / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.FILL
        paint.color = fill
        c.drawCircle(s / 2f, s / 2f, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = ring
        c.drawCircle(s / 2f, s / 2f, radius, paint)
    }

    private fun letter(c: Canvas, s: Float, label: String, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = s * 0.54f
        val fm = paint.fontMetrics
        val y = s / 2f - (fm.ascent + fm.descent) / 2f
        c.drawText(label, s / 2f, y, paint)
    }

    /** Waagerechter Balken (Sperrung). */
    private fun bar(c: Canvas, s: Float, color: Int) {
        val h = s * 0.17f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val rect = RectF(s * 0.22f, s / 2f - h / 2f, s * 0.78f, s / 2f + h / 2f)
        c.drawRoundRect(rect, h / 2f, h / 2f, paint)
    }

    /** Drei Balken (Stau / stockender Verkehr). */
    private fun bars(c: Canvas, s: Float, color: Int) {
        val h = s * 0.12f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        for (cy in floatArrayOf(s * 0.35f, s * 0.50f, s * 0.65f)) {
            val rect = RectF(s * 0.27f, cy - h / 2f, s * 0.73f, cy + h / 2f)
            c.drawRoundRect(rect, h / 2f, h / 2f, paint)
        }
    }

    /** Ausrufezeichen (Baustelle). */
    private fun exclamation(c: Canvas, s: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        val w = s * 0.15f
        val stem = RectF(s / 2f - w / 2f, s * 0.28f, s / 2f + w / 2f, s * 0.58f)
        c.drawRoundRect(stem, w / 2f, w / 2f, paint)
        c.drawCircle(s / 2f, s * 0.70f, w * 0.62f, paint)
    }

    /** Kamera-Symbol (Blitzer). */
    private fun camera(c: Canvas, s: Float) {
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE }
        c.drawRoundRect(RectF(s * 0.22f, s * 0.34f, s * 0.78f, s * 0.72f), s * 0.08f, s * 0.08f, body)
        val bump = Path().apply {
            moveTo(s * 0.38f, s * 0.35f)
            lineTo(s * 0.44f, s * 0.25f)
            lineTo(s * 0.56f, s * 0.25f)
            lineTo(s * 0.62f, s * 0.35f)
            close()
        }
        c.drawPath(bump, body)
        val lens = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED }
        c.drawCircle(s * 0.5f, s * 0.53f, s * 0.14f, lens)
        val glint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WHITE }
        c.drawCircle(s * 0.455f, s * 0.485f, s * 0.045f, glint)
    }

    /** Ziel-Flagge. */
    private fun flag(c: Canvas, s: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        c.drawRoundRect(RectF(s * 0.36f, s * 0.22f, s * 0.43f, s * 0.80f), s * 0.035f, s * 0.035f, paint)
        val cloth = Path().apply {
            moveTo(s * 0.43f, s * 0.24f)
            lineTo(s * 0.74f, s * 0.36f)
            lineTo(s * 0.43f, s * 0.48f)
            close()
        }
        c.drawPath(cloth, paint)
    }
}

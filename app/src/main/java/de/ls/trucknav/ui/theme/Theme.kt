package de.ls.trucknav.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Farbwelt aus dem Design-Blatt: Rot auf Dunkelgrau.
val AppBackground = Color(0xFF101012)
val AppSurface = Color(0xFF1B1B1E)
val AppSurfaceVariant = Color(0xFF2A2A2F)
val AppPrimary = Color(0xFFE02B36)
val AppSecondary = Color(0xFFFFC246)
val AppText = Color(0xFFF3F3F4)
val AppTextMuted = Color(0xFF9E9EA5)
val AppDanger = Color(0xFFFF6B6B)

private val Colors = darkColorScheme(
    primary = AppPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF4A1218),
    onPrimaryContainer = Color(0xFFFFDADD),
    secondary = AppSecondary,
    onSecondary = Color(0xFF241A00),
    secondaryContainer = Color(0xFF3D2F00),
    onSecondaryContainer = AppText,
    background = AppBackground,
    onBackground = AppText,
    surface = AppSurface,
    onSurface = AppText,
    surfaceVariant = AppSurfaceVariant,
    onSurfaceVariant = AppTextMuted,
    outline = Color(0xFF3A3A40),
    error = AppDanger,
    onError = Color(0xFF2A0000),
)

@Composable
fun LSTruckNavTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        content = content,
    )
}

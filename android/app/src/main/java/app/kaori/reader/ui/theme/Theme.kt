package app.kaori.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalPalette = staticCompositionLocalOf { PaletteKaori }

@Composable
fun KaoriTheme(
    palette: KaoriPalette = PaletteKaori,
    content: @Composable () -> Unit,
) {
    val scheme = darkColorScheme(
        primary = palette.accent,
        onPrimary = palette.uiBg,
        secondary = palette.accentSecondary,
        onSecondary = palette.uiBg,
        background = palette.uiBg,
        onBackground = palette.text,
        surface = palette.uiSurface,
        onSurface = palette.text,
        surfaceVariant = palette.uiSurfaceRaised,
        onSurfaceVariant = palette.textMuted,
        outline = palette.border,
    )

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

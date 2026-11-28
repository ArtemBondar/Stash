package org.example.stash

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    surface = Color(0xFFFAFAF7), surfaceVariant = Color(0xFFF0F3EE),
    onSurface = Color(0xFF1C2822), onSurfaceVariant = Color(0xFF48594F),
    primary = Color(0xFF20593F), onPrimary = Color.White,
    secondaryContainer = Color(0xFFD9EBDF), onSecondaryContainer = Color(0xFF173B29),
    error = Color(0xFF9C2927),
)
private val DarkColors = darkColorScheme(
    surface = Color(0xFF141916), surfaceVariant = Color(0xFF1E2722),
    onSurface = Color(0xFFE5EEE7), onSurfaceVariant = Color(0xFFB3C5B9),
    primary = Color(0xFFA4D7BA), onPrimary = Color(0xFF113826),
    secondaryContainer = Color(0xFF2A4737), onSecondaryContainer = Color(0xFFDCF0E2),
    error = Color(0xFFFFB4AB),
)

internal fun stashColorScheme(dark: Boolean) = if (dark) DarkColors else LightColors

@Composable
fun StashTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = stashColorScheme(isSystemInDarkTheme()), content = content)
}

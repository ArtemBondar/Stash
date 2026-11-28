package org.example.stash

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemeTest {
    @Test fun usesStashColorsForBothSystemAppearances() {
        assertEquals(Color(0xFFFAFAF7), stashColorScheme(dark = false).surface)
        assertEquals(Color(0xFF1C2822), stashColorScheme(dark = false).onSurface)
        assertEquals(Color(0xFF141916), stashColorScheme(dark = true).surface)
        assertEquals(Color(0xFFE5EEE7), stashColorScheme(dark = true).onSurface)
    }
}

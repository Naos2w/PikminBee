package com.naos.pikminbee.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * A complete Material 3 colour scheme must accompany the user-selected
 * appearance. Changing the map glass background alone leaves text fields,
 * unselected chips and text buttons using unreadable light-theme colours.
 */
object PikminBeeThemeColors {
    val dark: ColorScheme = darkColorScheme(
        primary = Color(0xFFE5C6FF),
        onPrimary = Color(0xFF29153C),
        primaryContainer = Color(0xFF513667),
        onPrimaryContainer = Color(0xFFF7ECFF),
        secondary = Color(0xFFD4E1FA),
        onSecondary = Color(0xFF22324A),
        secondaryContainer = Color(0xFF3D4B66),
        onSecondaryContainer = Color(0xFFF5F8FF),
        background = Color(0xFF0C182A),
        onBackground = Color(0xFFF3F7FF),
        surface = Color(0xFF1E2B3C),
        onSurface = Color(0xFFF3F7FF),
        surfaceVariant = Color(0xFF35455B),
        onSurfaceVariant = Color(0xFFD6E0F1),
        outline = Color(0xFFBCC9DB),
        outlineVariant = Color(0xFF8496AB),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
    )
    val light: ColorScheme = lightColorScheme(
        primary = Color(0xFF57427D),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE8DBF9),
        onPrimaryContainer = Color(0xFF26183D),
        secondary = Color(0xFF4A5F80),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE5EAF4),
        onSecondaryContainer = Color(0xFF1A2940),
        background = Color(0xFFF4F7FC),
        onBackground = Color(0xFF18263A),
        surface = Color.White,
        onSurface = Color(0xFF18263A),
        surfaceVariant = Color(0xFFE9EEF6),
        onSurfaceVariant = Color(0xFF41536E),
        outline = Color(0xFF64758D),
        outlineVariant = Color(0xFFA9B6C9),
        error = Color(0xFFB3261E),
        onError = Color.White,
    )

    fun forDarkMode(dark: Boolean): ColorScheme = if (dark) this.dark else light
}

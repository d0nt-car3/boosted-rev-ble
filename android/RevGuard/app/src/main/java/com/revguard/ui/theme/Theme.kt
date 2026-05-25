package com.revguard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * Color palette for Rev Guard.
 *
 * Dark theme inspired by GitHub's dark mode. Status colors follow
 * traffic-light conventions: green = good, amber = transitional, red = problem.
 */

// Background and surface tones
val Background     = Color(0xFF0D1117)
val Surface        = Color(0xFF161B22)
val SurfaceVariant = Color(0xFF21262D)

// Text
val OnBackground   = Color(0xFFE6EDF3)
val OnSurface      = Color(0xFFE6EDF3)
val TextSecondary  = Color(0xFF8B949E)

// Accent
val Primary        = Color(0xFF00D4FF)

// Domain-specific status colors (not part of Material roles)
val StatusLocked     = Color(0xFF3FB950)
val StatusWarning    = Color(0xFFD29922)
val StatusError      = Color(0xFFF85149)
val StatusConnecting = Color(0xFF00D4FF)

private val RevGuardColorScheme = darkColorScheme(
    primary = Primary,
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    onBackground = OnBackground,
    onSurface = OnSurface,
    onSurfaceVariant = TextSecondary
)

@Composable
fun RevGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RevGuardColorScheme,
        content = content
    )
}

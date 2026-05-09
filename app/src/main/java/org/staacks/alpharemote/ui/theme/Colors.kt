package org.staacks.alpharemote.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color


val Cyan = Color(0xFF0BA6D0)
val MidnightLight = Color(0xFF006607) // Corrected from 0xF066079 (assuming 0xFF006607 or similar)
val Midnight = Color(0xFF054D61)
val Fulvous = Color(0xFFE98A15)
val Ochre = Color(0xFFC47412)
val Black = Color(0xFF000000)
val Gray10 = Color(0xFF1A1A1A)
val Gray20 = Color(0xFF333333)
val Gray30 = Color(0xFF4D4D4D)
val Gray40 = Color(0xFF666666)
val Gray50 = Color(0xFF808080)
val Gray60 = Color(0xFF999999)
val Gray70 = Color(0xFFB3B3B3)
val Gray80 = Color(0xFFCCCCCC)
val Gray90 = Color(0xFFE6E6E6)
val Gray95 = Color(0xFFF2F2F2)
val White = Color(0xFFFFFFFF)

val DarkRed = Color(0xFF800000)
val LightRed = Color(0xFFFF8080)
val DarkGreen = Color(0xFF008000)
val LightGreen = Color(0xFF80FF80)

val LightColorScheme = lightColorScheme(
    primary = Cyan,
    onPrimary = Black,
    primaryContainer = Midnight, // M3 uses containers; primaryVariant is mapped here
    onPrimaryContainer = White,  // Adjust as needed
    secondary = Fulvous,
    onSecondary = Black,
    secondaryContainer = Fulvous,  // M3 uses containers; secondaryVariant is mapped here
    onSecondaryContainer = White, // Adjust as needed
    tertiary = Fulvous, // Example, adjust if you have a tertiary color
    onTertiary = White,
    surface = White,
    onSurface = Black,
    surfaceVariant = Gray95,
    onSurfaceVariant = Gray30,
    onError = White,
)

val DarkColorScheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Black,
    primaryContainer = Midnight,
    onPrimaryContainer = White,
    secondary = Fulvous,
    onSecondary = Black,
    secondaryContainer = Midnight,
    onSecondaryContainer = White,
    tertiary = Fulvous,
    onTertiary = Black,
    surface = Gray10,
    onSurface = White,
    surfaceVariant = Gray20,
    onSurfaceVariant = Gray80,
    onError = White,
)

// Extension properties for custom colors on MaterialTheme.colorScheme
val androidx.compose.material3.ColorScheme.textError: Color
    get() = if (this == LightColorScheme) DarkRed else DarkRed // Or define a different dark error if needed

val androidx.compose.material3.ColorScheme.textConnected: Color
    get() = if (this == LightColorScheme) DarkGreen else DarkGreen // Or define a different dark connected if needed

val androidx.compose.material3.ColorScheme.customButton: Color
    get() = if (this == LightColorScheme) Black else White

val androidx.compose.material3.ColorScheme.customButtonBG: Color
    get() = if (this == LightColorScheme) Gray95 else Color(0xFF2A2A2A)

val androidx.compose.material3.ColorScheme.zoomFocusBG: Color
    get() = if (this == LightColorScheme) Gray95 else Color(0xFF2A2A2A)


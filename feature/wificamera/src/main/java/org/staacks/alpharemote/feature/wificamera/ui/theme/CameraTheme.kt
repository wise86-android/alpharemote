package org.staacks.alpharemote.feature.wificamera.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A fixed dark palette for the camera HUD, deliberately outside the app's Material theme.
 *
 * This screen sits over a live view, so it follows the conventions of a camera back rather than
 * the host app: dark regardless of system theme, values in amber so they read against any scene,
 * and labels quiet enough to disappear when you are looking at the picture.
 */
object CameraColors {
    val Background = Color(0xFF0D0D0F)
    val LiveViewTop = Color(0xFF1B1B1E)
    val LiveViewBottom = Color(0xFF08080A)

    val SurfaceElevated = Color(0xFF18181B)
    val ChipIdle = Color(0xFF1C1C1F)
    val ChipActive = Color(0xFF26262B)
    val Divider = Color(0xFF2A2A2E)

    /** Values the photographer changes per shot. */
    val AccentAmber = Color(0xFFF2A93C)
    val AccentAmberDim = Color(0xFF8A6428)

    /** Connection state only. */
    val AccentTeal = Color(0xFF4FD1C5)
    val AccentRed = Color(0xFFE5484D)

    val TextPrimary = Color(0xFFF5F3EF)
    val TextSecondary = Color(0xFF8A8A8E)
    val TextTertiary = Color(0xFF57575B)

    /** Scrim behind the status readout so it stays legible over a bright scene. */
    val OverlayTop = Color(0xD90A0A0C)
}

/**
 * Monospace for anything numeric so digits do not shift as values change — a jittering shutter
 * speed is unreadable at a glance. Labels are letter-spaced and small, like engraving on a dial.
 */
object CameraType {
    val label = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp,
        color = CameraColors.TextSecondary
    )

    val hudSmallDim = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        color = CameraColors.TextSecondary
    )

    val hudMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 15.sp,
        color = CameraColors.TextPrimary
    )

    val hudLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 30.sp,
        color = CameraColors.AccentAmber
    )
}

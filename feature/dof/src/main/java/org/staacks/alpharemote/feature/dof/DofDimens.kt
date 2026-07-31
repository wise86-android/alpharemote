package org.staacks.alpharemote.feature.dof

import androidx.compose.ui.unit.dp

/**
 * Spacing shared by the depth of field composables. The app's own `ui/theme/Dimens.kt` lives in the
 * application module and cannot be reached from a library, so this module keeps its own copy of the
 * few values it needs.
 */

internal val ScreenMargin = 16.dp
internal val SectionSpacing = 24.dp
internal val ItemSpacing = 12.dp
internal val LabelSpacing = 4.dp

/**
 * Insets the slider tracks from the screen edges so dragging a thumb to either extreme does not
 * land in the system's back gesture zone. On top of the screen margin this puts the ends of every
 * track well clear of the edge.
 */
internal val SliderTrackInset = 12.dp

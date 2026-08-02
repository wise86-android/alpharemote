package org.staacks.alpharemote.feature.dof

import androidx.compose.ui.unit.dp

/**
 * Spacing specific to the depth of field composables. The screen margin itself is
 * [org.staacks.alpharemote.core.ui.theme.FragmentMargin] — the same value every other screen
 * uses — so it is not redefined here; only the values below have no equivalent outside this
 * feature.
 */

internal val SectionSpacing = 24.dp
internal val ItemSpacing = 12.dp
internal val LabelSpacing = 4.dp

/**
 * Insets the slider tracks from the screen edges so dragging a thumb to either extreme does not
 * land in the system's back gesture zone. On top of the screen margin this puts the ends of every
 * track well clear of the edge.
 */
internal val SliderTrackInset = 12.dp

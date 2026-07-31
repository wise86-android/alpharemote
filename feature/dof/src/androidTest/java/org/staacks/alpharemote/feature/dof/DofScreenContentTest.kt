package org.staacks.alpharemote.feature.dof

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers how the screen assembles its parts. The parts themselves are covered by
 * [SensorPickerTest] and [DofResultCardTest].
 */
@RunWith(AndroidJUnit4::class)
class DofScreenContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun setContent(
        input: DofSettings.DofInput = DofSettings.DEFAULT_INPUT,
        onSensorChange: (SensorType) -> Unit = {}
    ) {
        val result = calculateDof(
            input.distanceMeters * 1000f,
            input.focalLengthMm,
            input.aperture,
            input.sensor
        )
        composeTestRule.setContent {
            MaterialTheme {
                DofScreenContent(
                    input = input,
                    result = result,
                    onDistanceChange = {},
                    onFocalLengthChange = {},
                    onApertureChange = {},
                    onSensorChange = onSensorChange
                )
            }
        }
    }

    @Test
    fun showsTheCurrentInputValues() {
        setContent()

        composeTestRule.onNodeWithText(context.formatDistance(1800f)).assertExists()
        composeTestRule.onNodeWithText(context.formatFocalLength(50f)).assertExists()
        composeTestRule.onNodeWithText(context.formatAperture(1.8f)).assertExists()
    }

    @Test
    fun showsTheComputedLimits() {
        setContent()

        // 50 mm at f/1.8 on full frame, 1.8 m away: roughly 1.74 m to 1.87 m.
        composeTestRule.onNodeWithText(context.formatDistance(1736.61f)).assertExists()
        composeTestRule.onNodeWithText(context.formatDistance(1868.19f)).assertExists()
    }

    @Test
    fun forwardsSensorChangesFromThePicker() {
        val reported = mutableListOf<SensorType>()
        setContent(onSensorChange = reported::add)

        composeTestRule
            .onNodeWithText(context.getString(SensorType.APS_C.labelRes))
            .performClick()

        assertEquals(listOf(SensorType.APS_C), reported)
    }
}

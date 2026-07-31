package org.staacks.alpharemote.feature.dof

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SensorPickerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun setContent(
        selected: SensorType = SensorType.FULL_FRAME,
        onSensorChange: (SensorType) -> Unit = {}
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                SensorPicker(selected = selected, onSensorChange = onSensorChange)
            }
        }
    }

    /** Every format is on screen at once, which is the point of the segmented control. */
    @Test
    fun offersEverySensorFormat() {
        setContent()

        for (sensor in SensorType.entries) {
            composeTestRule.onNodeWithText(context.getString(sensor.labelRes)).assertExists()
        }
    }

    @Test
    fun marksOnlyTheSelectedFormat() {
        setContent(selected = SensorType.APS_C)

        composeTestRule
            .onNodeWithText(context.getString(SensorType.APS_C.labelRes))
            .assertIsSelected()
        composeTestRule
            .onNodeWithText(context.getString(SensorType.FULL_FRAME.labelRes))
            .assertIsNotSelected()
    }

    @Test
    fun pickingAFormatReportsIt() {
        val reported = mutableListOf<SensorType>()
        setContent(onSensorChange = reported::add)

        composeTestRule
            .onNodeWithText(context.getString(SensorType.APS_C.labelRes))
            .performClick()
        composeTestRule
            .onNodeWithText(context.getString(SensorType.MICRO_FOUR_THIRDS.labelRes))
            .performClick()

        assertEquals(listOf(SensorType.APS_C, SensorType.MICRO_FOUR_THIRDS), reported)
    }
}

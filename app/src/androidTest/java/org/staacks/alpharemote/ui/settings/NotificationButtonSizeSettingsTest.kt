package org.staacks.alpharemote.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.staacks.alpharemote.R
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme

@RunWith(AndroidJUnit4::class)
class NotificationButtonSizeSettingsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val smallerLabel = context.getString(R.string.settings_button_size_smaller)
    private val largerLabel = context.getString(R.string.settings_button_size_larger)

    private fun setContent(selectedIndex: Int, onIndexChange: (Int) -> Unit) {
        composeTestRule.setContent {
            BluetoothRemoteForSonyCamerasTheme {
                NotificationButtonSizeSettings(
                    selectedIndex = selectedIndex,
                    maxIndex = 6,
                    onIndexChange = onIndexChange,
                )
            }
        }
    }

    @Test
    fun clickingLargerIncrementsIndex() {
        val reported = mutableListOf<Int>()
        setContent(selectedIndex = 3, onIndexChange = reported::add)

        composeTestRule.onNodeWithText(largerLabel).performClick()

        assertEquals(listOf(4), reported)
    }

    @Test
    fun clickingSmallerDecrementsIndex() {
        val reported = mutableListOf<Int>()
        setContent(selectedIndex = 3, onIndexChange = reported::add)

        composeTestRule.onNodeWithText(smallerLabel).performClick()

        assertEquals(listOf(2), reported)
    }

    @Test
    fun smallerIsClampedAtMinimum() {
        val reported = mutableListOf<Int>()
        setContent(selectedIndex = 0, onIndexChange = reported::add)

        composeTestRule.onNodeWithText(smallerLabel).performClick()

        assertEquals(listOf(0), reported)
    }

    @Test
    fun largerIsClampedAtMaximum() {
        val reported = mutableListOf<Int>()
        setContent(selectedIndex = 6, onIndexChange = reported::add)

        composeTestRule.onNodeWithText(largerLabel).performClick()

        assertEquals(listOf(6), reported)
    }
}

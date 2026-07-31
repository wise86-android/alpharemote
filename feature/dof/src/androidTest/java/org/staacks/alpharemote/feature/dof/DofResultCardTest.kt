package org.staacks.alpharemote.feature.dof

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DofResultCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun setContent(result: DofResult) {
        composeTestRule.setContent {
            MaterialTheme { DofResultCard(result) }
        }
    }

    /** 50 mm at f/1.8 on full frame, 1.8 m away: roughly 1.74 m to 1.87 m. */
    @Test
    fun showsNearFarAndTotal() {
        setContent(calculateDof(1800f, 50f, 1.8f, SensorType.FULL_FRAME))

        composeTestRule.onNodeWithText(context.formatDistance(1736.61f)).assertExists()
        composeTestRule.onNodeWithText(context.formatDistance(1868.19f)).assertExists()
        composeTestRule.onNodeWithText(context.getString(R.string.dof_total)).assertExists()
    }

    @Test
    fun reportsInfiniteDepthOfFieldPastTheHyperfocalDistance() {
        setContent(calculateDof(5000f, 24f, 8f, SensorType.FULL_FRAME))

        // Shown twice: once as the far limit and once as the total depth of field.
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.dof_infinite))
            .assertCountEquals(2)
    }
}

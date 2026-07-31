package org.staacks.alpharemote.ui.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.staacks.alpharemote.R
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme

@RunWith(AndroidJUnit4::class)
class CustomButtonsSettingsSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val buttons = listOf(
        CameraAction(false, null, CameraActionPreset.TRIGGER_ONCE),
        CameraAction(false, null, CameraActionPreset.SHUTTER),
        CameraAction(false, null, CameraActionPreset.RECORD),
    )

    private fun setContent(
        onAddClick: () -> Unit = {},
        onHelpClick: () -> Unit = {},
        onEditClick: (Int, CameraAction) -> Unit = { _, _ -> },
        onDelete: (Int) -> Unit = {},
    ) {
        composeTestRule.setContent {
            BluetoothRemoteForSonyCamerasTheme {
                CustomButtonsSettingsSection(
                    buttons = buttons,
                    onAddClick = onAddClick,
                    onHelpClick = onHelpClick,
                    onEditClick = onEditClick,
                    onMove = { _, _ -> },
                    onDelete = onDelete,
                )
            }
        }
    }

    @Test
    fun showsOneRowPerConfiguredButton() {
        setContent()

        buttons.forEach { action ->
            composeTestRule.onNodeWithText(action.getName(context)).assertIsDisplayed()
        }
        composeTestRule.onAllNodesWithContentDescription(context.getString(R.string.delete))
            .assertCountEquals(buttons.size)
    }

    @Test
    fun addButtonInvokesCallback() {
        var addClicks = 0
        setContent(onAddClick = { addClicks++ })

        composeTestRule.onNodeWithText(context.getString(R.string.settings_custom_buttons_add))
            .performClick()

        assertEquals(1, addClicks)
    }

    @Test
    fun helpButtonInvokesCallback() {
        var helpClicks = 0
        setContent(onHelpClick = { helpClicks++ })

        composeTestRule.onNodeWithText(context.getString(R.string.help)).performClick()

        assertEquals(1, helpClicks)
    }

    @Test
    fun editReportsIndexAndActionOfTheClickedRow() {
        val edits = mutableListOf<Pair<Int, CameraAction>>()
        setContent(onEditClick = { index, action -> edits.add(index to action) })

        composeTestRule.onAllNodesWithContentDescription("Edit")[1].performClick()

        assertEquals(listOf(1 to buttons[1]), edits)
    }

    @Test
    fun deleteReportsIndexOfTheClickedRow() {
        val deletes = mutableListOf<Int>()
        setContent(onDelete = deletes::add)

        composeTestRule.onAllNodesWithContentDescription(context.getString(R.string.delete))[2]
            .performClick()

        assertEquals(listOf(2), deletes)
    }
}

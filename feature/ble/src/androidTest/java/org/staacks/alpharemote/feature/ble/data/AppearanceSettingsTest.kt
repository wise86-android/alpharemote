package org.staacks.alpharemote.feature.ble.data

import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.staacks.alpharemote.feature.ble.camera.CameraAction
import org.staacks.alpharemote.feature.ble.camera.CameraActionPreset

/**
 * Verifies that AppearanceSettings persists the notification button size and the custom button
 * list correctly in the shared DataStore without affecting each other.
 */
@RunWith(AndroidJUnit4::class)
class AppearanceSettingsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val appearanceSettings = AppearanceSettings(context)

    @Before
    fun clearDataStore() {
        runBlocking {
            context.settingsDataStore.edit { it.clear() }
        }
    }

    @Test
    fun buttonListIsNullAndScaleDefaultsUntilSet() = runTest {
        assertNull(appearanceSettings.getCustomButtonList())
        assertEquals(1.0f, appearanceSettings.customButtonSettings.first().scale)
    }

    @Test
    fun setNotificationButtonSizeChangesOnlyTheScale() = runTest {
        appearanceSettings.setNotificationButtonSize(1.3f)

        val settings = appearanceSettings.customButtonSettings.first()
        assertEquals(1.3f, settings.scale)
        assertEquals(1.3f, appearanceSettings.getNotificationButtonSize())
        assertNull(settings.customButtonList)
    }

    @Test
    fun saveCustomButtonListRoundTripsAllActionOptions() = runTest {
        val list = listOf(
            CameraAction(false, null, CameraActionPreset.TRIGGER_ONCE),
            CameraAction(true, 0.5f, CameraActionPreset.ZOOM_IN),
            CameraAction(false, null, CameraActionPreset.RECORD),
        )

        appearanceSettings.saveCustomButtonList(list)

        assertEquals(list, appearanceSettings.getCustomButtonList())
        assertEquals(list, appearanceSettings.customButtonSettings.first().customButtonList)
    }

    @Test
    fun savingAShorterListRemovesTheLeftoverEntries() = runTest {
        appearanceSettings.saveCustomButtonList(
            listOf(
                CameraAction(false, null, CameraActionPreset.TRIGGER_ONCE),
                CameraAction(false, null, CameraActionPreset.SHUTTER),
                CameraAction(false, null, CameraActionPreset.RECORD),
            )
        )

        val shorterList = listOf(
            CameraAction(false, null, CameraActionPreset.SHUTTER),
        )
        appearanceSettings.saveCustomButtonList(shorterList)

        assertEquals(shorterList, appearanceSettings.getCustomButtonList())
    }

    @Test
    fun savingEmptyListIsDistinctFromUnsetList() = runTest {
        appearanceSettings.saveCustomButtonList(emptyList())

        assertEquals(emptyList<CameraAction>(), appearanceSettings.getCustomButtonList())
    }

    @Test
    fun savingButtonListKeepsTheScale() = runTest {
        appearanceSettings.setNotificationButtonSize(0.7f)

        appearanceSettings.saveCustomButtonList(
            listOf(CameraAction(false, null, CameraActionPreset.SHUTTER))
        )

        assertEquals(0.7f, appearanceSettings.customButtonSettings.first().scale)
    }
}

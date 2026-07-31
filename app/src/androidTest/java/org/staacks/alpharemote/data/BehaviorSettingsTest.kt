package org.staacks.alpharemote.data

import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that each BehaviorSettings setter changes exactly the setting it is responsible for
 * in the shared DataStore, leaving the other settings untouched.
 */
@RunWith(AndroidJUnit4::class)
class BehaviorSettingsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val behaviorSettings = BehaviorSettings(context)

    @Before
    fun clearDataStore() {
        runBlocking {
            context.settingsDataStore.edit { it.clear() }
        }
    }

    @Test
    fun updateCameraLocationDefaultsToFalse() = runTest {
        assertFalse(behaviorSettings.updateCameraLocation.first())
    }

    @Test
    fun setUpdateCameraLocationChangesOnlyThatSetting() = runTest {
        behaviorSettings.setUpdateCameraLocation(true)

        assertTrue(behaviorSettings.updateCameraLocation.first())
        assertFalse(behaviorSettings.broadcastControl.first())

        behaviorSettings.setUpdateCameraLocation(false)
        assertFalse(behaviorSettings.updateCameraLocation.first())
    }

    @Test
    fun setBroadcastControlChangesOnlyThatSetting() = runTest {
        behaviorSettings.setBroadcastControl(true)

        assertTrue(behaviorSettings.broadcastControl.first())
        assertTrue(behaviorSettings.getBroadcastControl())
        assertFalse(behaviorSettings.updateCameraLocation.first())

        behaviorSettings.setBroadcastControl(false)
        assertFalse(behaviorSettings.getBroadcastControl())
    }

    @Test
    fun setCameraIdStoresNameAndAddress() = runTest {
        behaviorSettings.setCameraId("Alpha 1", "00:11:22:33:44:55")

        val (address, name) = behaviorSettings.getCameraId()
        assertEquals("00:11:22:33:44:55", address)
        assertEquals("Alpha 1", name)
    }
}

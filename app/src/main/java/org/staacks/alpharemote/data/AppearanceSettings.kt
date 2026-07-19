package org.staacks.alpharemote.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset

/**
 * Settings describing how the app looks: the custom buttons shown in the notification and
 * their size. For settings about how the app behaves, see [BehaviorSettings].
 */
class AppearanceSettings(context: Context) {
    private val settings = context.settingsDataStore

    companion object {
        private val NOTIFICATION_BUTTON_SIZE_KEY = floatPreferencesKey("notificationButtonSize")

        private const val CUSTOM_BUTTON_LIST_BASE_KEY = "customButtonList"
        private val CUSTOM_BUTTON_LIST_SET_BY_USER_KEY =
            booleanPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_setbyuser")
    }

    data class CustomButtonSettings (
        val customButtonList: List<CameraAction>?,
        val scale: Float
    )

    val customButtonSettings: Flow<CustomButtonSettings> = settings.data.map {
        CustomButtonSettings(
            assembleCameraActionList(it),
            it[NOTIFICATION_BUTTON_SIZE_KEY] ?: 1.0f
        )
    }.distinctUntilChanged()

    suspend fun setNotificationButtonSize(size: Float) {
        settings.edit { data ->
            data[NOTIFICATION_BUTTON_SIZE_KEY] = size
        }
    }

    suspend fun getNotificationButtonSize(): Float? {
        return settings.data.firstOrNull()?.get(NOTIFICATION_BUTTON_SIZE_KEY)
    }

    suspend fun saveCustomButtonList(list: List<CameraAction>) {
        settings.edit { data ->
            data[CUSTOM_BUTTON_LIST_SET_BY_USER_KEY] = true

            for ((i, item) in list.withIndex()) {
                val keyPreset = stringPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_preset")
                val keyToggle = booleanPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_toggle")
                val keySelftimer = floatPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_selftimer")
                val keyDuration = floatPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_duration")
                val keyStep = floatPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_step")

                data[keyPreset] = item.preset.name
                data[keyToggle] = item.toggle

                setNullableFloat(data, keySelftimer, item.selfTimer)
                setNullableFloat(data, keyDuration, item.duration)
                setNullableFloat(data, keyStep, item.step)
            }
            var i = list.count()
            while (true) {
                val keyPreset = stringPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_preset")
                val keyToggle = booleanPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_toggle")
                val keySelftimer = floatPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_selftimer")
                val keyDuration = floatPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_duration")
                val keyStep = floatPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_step")

                if (!data.contains(keyPreset))
                    break

                data -= keyPreset
                data -= keyToggle
                data -= keySelftimer
                data -= keyDuration
                data -= keyStep
                i++
            }
        }
    }

    suspend fun getCustomButtonList(): List<CameraAction>? {
        return settings.data.firstOrNull()?.let {
            assembleCameraActionList(it)
        }
    }

    private fun setNullableFloat(data: MutablePreferences, key: Preferences.Key<Float>, value: Float?) {
        if (value == null) {
            data -= key
        } else {
            data[key] = value
        }
    }

    private fun assembleCameraActionList(data: Preferences): List<CameraAction>? {
        if (data[CUSTOM_BUTTON_LIST_SET_BY_USER_KEY] != true)
            return null

        val list = mutableListOf<CameraAction>()
        var i = 0
        while (true) {
            val keyPreset = stringPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_preset")
            val keyToggle = booleanPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_toggle")
            val keySelftimer = floatPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_selftimer")
            val keyDuration = floatPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_duration")
            val keyStep = floatPreferencesKey(CUSTOM_BUTTON_LIST_BASE_KEY + "_" + i + "_step")

            if (!data.contains(keyPreset))
                break

            list.add(CameraAction(
                data[keyToggle] ?: false,
                data[keySelftimer],
                data[keyDuration],
                data[keyStep],
                CameraActionPreset.valueOf(data[keyPreset] ?: CameraActionPreset.STOP.name)
            ))

            i++
        }

        return list
    }
}

package org.staacks.alpharemote

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

const val PREFERENCES_NAME = "alpharemote"

val Context.settings: DataStore<Preferences> by preferencesDataStore(name = PREFERENCES_NAME)


class SettingsStore(context: Context) {
    private val settings = context.settings

    private val notificationGrantedKey = booleanPreferencesKey("notificationGranted")
    private val bluetoothGrantedKey = booleanPreferencesKey("bluetoothGranted")

    private val cameraIdNameKey = stringPreferencesKey("cameraIdName")
    private val cameraIdAddressKey = stringPreferencesKey("cameraIdAddress")
    private val notificationButtonSizeKey = floatPreferencesKey("notificationButtonSize")

    private val customButtonListBaseKey = "customButtonList"
    private val customButtonListSetByUserKey = booleanPreferencesKey(customButtonListBaseKey + "_setbyuser")

    private val broadcastControlKey = booleanPreferencesKey("broadcastControl")

    val handleExternalBroadcastMessage: Boolean
        get() = runBlocking { settings.data.first()[broadcastControlKey] ?: false }

    private fun setNullableFloat(data: MutablePreferences, key: Preferences.Key<Float>, value: Float?) {
        if (value == null) {
            data -= key
        } else {
            data[key] = value
        }
    }

    suspend fun setBluetoothGranted(granted: Boolean) {
        settings.edit { data ->
            data[bluetoothGrantedKey] = granted
        }
    }

    suspend fun setNotificationGranted(granted: Boolean) {
        settings.edit { data ->
            data[notificationGrantedKey] = granted
        }
    }

    suspend fun setCameraId(name: String, address: String) {
        settings.edit { data ->
            data[cameraIdNameKey] = name
            data[cameraIdAddressKey] = address
        }
    }

    suspend fun getCameraId():  Pair<String?, String?> {
        val data = settings.data.firstOrNull()
        return Pair(data?.get(cameraIdAddressKey),data?.get(cameraIdNameKey))
    }

    suspend fun setNotificationButtonSize(size: Float) {
        settings.edit { data ->
            data[notificationButtonSizeKey] = size
        }
    }



    data class Permissions (
        val bluetooth: Boolean,
        val notification: Boolean,
        val broadcastControl: Boolean
    )

    val permissions: Flow<Permissions> = settings.data.map{
        Permissions(
            it[bluetoothGrantedKey] ?: false,
            it[notificationGrantedKey] ?: false,
            it[broadcastControlKey] ?: false
        )
    }.distinctUntilChanged()

    suspend fun getNotificationButtonSize(): Float? {
        return settings.data.firstOrNull()?.get(notificationButtonSizeKey)
    }

    data class CustomButtonSettings (
        val customButtonList: List<CameraAction>?,
        val scale: Float
    )

    val customButtonSettings: Flow<CustomButtonSettings> = settings.data.map {
        CustomButtonSettings(
            assembleCameraActionList(it),
            it[notificationButtonSizeKey] ?: 1.0f
        )
    }.distinctUntilChanged()

    suspend fun saveCustomButtonList(list: List<CameraAction>) {
        settings.edit { data ->
            data[customButtonListSetByUserKey] = true

            for ((i, item) in list.withIndex()) {
                val keyPreset = stringPreferencesKey(customButtonListBaseKey + "_" + i + "_preset")
                val keyToggle = booleanPreferencesKey(customButtonListBaseKey + "_" + i + "_toggle")
                val keySelftimer = floatPreferencesKey(customButtonListBaseKey + "_" + i + "_selftimer")
                val keyDuration = floatPreferencesKey(customButtonListBaseKey + "_" + i + "_duration")
                val keyStep = floatPreferencesKey(customButtonListBaseKey + "_" + i + "_step")

                data[keyPreset] = item.preset.name
                data[keyToggle] = item.toggle

                setNullableFloat(data, keySelftimer, item.selfTimer)
                setNullableFloat(data, keyDuration, item.duration)
                setNullableFloat(data, keyStep, item.step)
            }
            var i = list.count()
            while (true) {
                val keyPreset = stringPreferencesKey(customButtonListBaseKey + "_" + i + "_preset")
                val keyToggle = booleanPreferencesKey(customButtonListBaseKey + "_" + i + "_toggle")
                val keySelftimer = floatPreferencesKey(customButtonListBaseKey + "_" + i + "_selftimer")
                val keyDuration = floatPreferencesKey(customButtonListBaseKey + "_" + i + "_duration")
                val keyStep = floatPreferencesKey(customButtonListBaseKey + "_" + i + "_step")

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

    private fun assembleCameraActionList(data: Preferences): List<CameraAction>? {
        if (data[customButtonListSetByUserKey] != true)
            return null

        val list = mutableListOf<CameraAction>()
        var i = 0
        while (true) {
            val keyPreset = stringPreferencesKey(customButtonListBaseKey + "_" + i + "_preset")
            val keyToggle = booleanPreferencesKey(customButtonListBaseKey + "_" + i + "_toggle")
            val keySelftimer = floatPreferencesKey(customButtonListBaseKey + "_" + i + "_selftimer")
            val keyDuration = floatPreferencesKey(customButtonListBaseKey + "_" + i + "_duration")
            val keyStep = floatPreferencesKey(customButtonListBaseKey + "_" + i + "_step")

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

    suspend fun getCustomButtonList(): List<CameraAction>? {
        return settings.data.firstOrNull()?.let {
            assembleCameraActionList(it)
        }
    }

    suspend fun setBroadcastControl(allow: Boolean) {
        settings.edit { data ->
            data[broadcastControlKey] = allow
        }
    }

    suspend fun getBroadcastControl():  Boolean {
        return settings.data.firstOrNull()?.get(broadcastControlKey) ?: false
    }
}
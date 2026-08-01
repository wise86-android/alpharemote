package org.staacks.alpharemote.feature.wificamera.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.staacks.alpharemote.feature.wificamera.domain.WifiCredentials

private const val PREFERENCES_NAME = "wifi_camera_credentials"

/**
 * Its own preferences file rather than a corner of the app's store: this module is self-contained
 * and shares no settings with the Bluetooth remote.
 */
internal val Context.wifiCameraCredentialsStore: DataStore<Preferences> by preferencesDataStore(
    name = PREFERENCES_NAME
)

/**
 * The last camera tapped, so a reconnection does not need another tap.
 *
 * The camera regenerates its password whenever the owner resets the network settings, so what is
 * stored here is a cache of the most recent tap and not a permanent pairing — tapping again simply
 * overwrites it.
 */
class CameraCredentialsStore(context: Context) {

    private val store = context.wifiCameraCredentialsStore

    val credentials: Flow<WifiCredentials?> = store.data
        .map { data ->
            val ssid = data[SSID_KEY]
            val password = data[PASSWORD_KEY]
            if (ssid.isNullOrBlank() || password.isNullOrBlank()) {
                null
            } else {
                WifiCredentials(ssid, password)
            }
        }
        .distinctUntilChanged()

    /**
     * The description URL the tapped camera advertised, if it wrote one.
     *
     * Cleared alongside the credentials because it belongs to that camera on that network.
     */
    val deviceDescriptionUrl: Flow<String?> = store.data
        .map { it[DESCRIPTION_URL_KEY] }
        .distinctUntilChanged()

    suspend fun save(
        credentials: WifiCredentials,
        deviceDescriptionUrl: String? = null
    ) {
        store.edit { data ->
            data[SSID_KEY] = credentials.ssid
            data[PASSWORD_KEY] = credentials.password
            if (deviceDescriptionUrl.isNullOrBlank()) {
                data.remove(DESCRIPTION_URL_KEY)
            } else {
                data[DESCRIPTION_URL_KEY] = deviceDescriptionUrl
            }
        }
    }

    suspend fun clear() {
        store.edit { data ->
            data.remove(SSID_KEY)
            data.remove(PASSWORD_KEY)
            data.remove(DESCRIPTION_URL_KEY)
        }
    }

    private companion object {
        val SSID_KEY = stringPreferencesKey("ssid")
        val PASSWORD_KEY = stringPreferencesKey("password")
        val DESCRIPTION_URL_KEY = stringPreferencesKey("deviceDescriptionUrl")
    }
}

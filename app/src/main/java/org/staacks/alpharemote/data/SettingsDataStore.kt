package org.staacks.alpharemote.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private const val PREFERENCES_NAME = "alpharemote"

// Single DataStore backing all settings classes. The preferences file (and its keys) is shared
// between AppearanceSettings and BehaviorSettings, so existing user settings are preserved.
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = PREFERENCES_NAME)

package org.staacks.alpharemote.data

import kotlinx.coroutines.flow.Flow

interface FeatureSettings {
    val updateCameraLocation: Flow<Boolean>
    suspend fun setUpdateCameraLocation(value: Boolean)
}
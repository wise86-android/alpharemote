package org.staacks.alpharemote.ui.about

import androidx.lifecycle.ViewModel
import org.staacks.alpharemote.BuildConfig

class AboutViewModel : ViewModel() {
    val versionCode = BuildConfig.VERSION_CODE
    val versionName = BuildConfig.VERSION_NAME
}
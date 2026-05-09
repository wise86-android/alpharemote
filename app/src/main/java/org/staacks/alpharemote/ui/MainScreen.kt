package org.staacks.alpharemote.ui

import android.content.IntentSender
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import org.staacks.alpharemote.R
import org.staacks.alpharemote.ui.about.aboutEntries
import org.staacks.alpharemote.ui.camera.CameraViewModel
import org.staacks.alpharemote.ui.camera.cameraEntries
import org.staacks.alpharemote.ui.settings.SettingsViewModel
import org.staacks.alpharemote.ui.settings.settingsEntries

@Composable
fun MainScreen(
    onPairRequested: (IntentSender) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val topLevelRoutes = setOf(
        AlphaRemoteNavKey.Camera,
        AlphaRemoteNavKey.Settings,
        AlphaRemoteNavKey.About
    )

    val navigationState = rememberNavigationState(
        startRoute = AlphaRemoteNavKey.Camera,
        topLevelRoutes = topLevelRoutes
    )

    val navigator = remember { Navigator(navigationState) }

    val cameraViewModel: CameraViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    val entryProvider = entryProvider {
        cameraEntries(cameraViewModel, navigator)
        settingsEntries(settingsViewModel, navigator, onPairRequested, onOpenUrl)
        aboutEntries(onOpenUrl)
        commonEntries(navigator)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = navigationState.topLevelRoute == AlphaRemoteNavKey.Camera,
                    onClick = { navigator.navigate(AlphaRemoteNavKey.Camera) },
                    icon = { Icon(Icons.Default.PhotoCamera, null) },
                    label = { Text(stringResource(R.string.title_camera)) }
                )
                NavigationBarItem(
                    selected = navigationState.topLevelRoute == AlphaRemoteNavKey.Settings,
                    onClick = { navigator.navigate(AlphaRemoteNavKey.Settings) },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text(stringResource(R.string.title_settings)) }
                )
                NavigationBarItem(
                    selected = navigationState.topLevelRoute == AlphaRemoteNavKey.About,
                    onClick = { navigator.navigate(AlphaRemoteNavKey.About) },
                    icon = { Icon(Icons.Default.Info, null) },
                    label = { Text(stringResource(R.string.title_about)) }
                )
            }
        }
    ) { padding ->
        NavDisplay(
            modifier = Modifier.padding(padding).fillMaxSize(),
            entries = navigationState.toEntries(entryProvider),
            onBack = { navigator.goBack() },
            sceneStrategies = listOf(DialogSceneStrategy())
        )
    }
}

package org.staacks.alpharemote.ui

import androidx.annotation.StringRes
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
import androidx.compose.ui.graphics.vector.ImageVector
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

private data class TopLevelDestination(
    val route: AlphaRemoteNavKey,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(AlphaRemoteNavKey.Camera, Icons.Default.PhotoCamera, R.string.title_camera),
    TopLevelDestination(AlphaRemoteNavKey.Settings, Icons.Default.Settings, R.string.title_settings),
    TopLevelDestination(AlphaRemoteNavKey.About, Icons.Default.Info, R.string.title_about),
)

@Composable
fun MainScreen() {
    val navigationState = rememberNavigationState(
        startRoute = AlphaRemoteNavKey.Camera,
        topLevelRoutes = topLevelDestinations.map { it.route }.toSet()
    )

    val navigator = remember { Navigator(navigationState) }

    val cameraViewModel: CameraViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    val entryProvider = entryProvider {
        cameraEntries(cameraViewModel, navigator)
        settingsEntries(settingsViewModel, navigator)
        aboutEntries()
        commonEntries(navigator)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                topLevelDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = navigationState.topLevelRoute == destination.route,
                        onClick = { navigator.navigate(destination.route) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) }
                    )
                }
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

package org.staacks.alpharemote.ui.settings2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.staacks.alpharemote.data.FeatureSettings
import org.staacks.alpharemote.ui.theme.BluetoothRemoteForSonyCamerasTheme
import org.staacks.alpharemote.ui.theme.FragmentMargin

@Composable
fun SettingsScreen(viewModel: Settings2ViewModel) {
    val devices by viewModel.devices.collectAsState()
    val updateCameraLocation by viewModel.updateCameraLocation.collectAsState(false)

    Surface {
        Column(
            modifier = Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing) // Handles system bars and cutouts
                .padding(FragmentMargin),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LocationSettings(updateCameraLocation, onCheckedChange = {
                viewModel.setUpdateCameraLocation(it)
            })
            // Device List Section
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (devices.isEmpty()) {
                    Text("No devices associated.", modifier = Modifier.padding(bottom = 8.dp))
                    Button(onClick = { viewModel.refreshDevices() }) {
                        Text("Find new Devices")
                    }
                } else {
                    Text("Associated Devices:", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(devices, key = { it.address }) {
                            DeviceItem(device = it, onRemoveClick = { address ->
                                viewModel.removeDevice(address)
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceItem(device: AssociatedDevices.Device, onRemoveClick: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = device.name, style = MaterialTheme.typography.titleMedium)
                Text(text = device.address, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { onRemoveClick(device.address) }) {
                Text("Remove")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        // For preview, we need a dummy ViewModel and associated devices
        val dummySettings = object : FeatureSettings {
            override val updateCameraLocation: Flow<Boolean> = flowOf(false)
            override suspend fun setUpdateCameraLocation(value: Boolean) {
                TODO("Not yet implemented")
            }
        }
        val dummyAssociatedDevices = object : AssociatedDevices {
            override fun loadAssociatedDevices(): List<AssociatedDevices.Device> {
                return listOf(
                    AssociatedDevices.Device(0, "AA:BB:CC:DD:EE:01", "Test Device 1", null, true),
                    AssociatedDevices.Device(1, "AA:BB:CC:DD:EE:02", "Test Device 2", null, false)
                )
            }
        }
        val dummyViewModel = Settings2ViewModel(dummySettings, dummyAssociatedDevices)
        SettingsScreen(viewModel = dummyViewModel)
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenEmptyPreview() {
    BluetoothRemoteForSonyCamerasTheme {
        val dummySettings = object : FeatureSettings {
            override val updateCameraLocation: Flow<Boolean> = flowOf(false)
            override suspend fun setUpdateCameraLocation(value: Boolean) {
                TODO("Not yet implemented")
            }
        }
        val dummyAssociatedDevices = object : AssociatedDevices {
            override fun loadAssociatedDevices(): List<AssociatedDevices.Device> {
                return emptyList()
            }
        }
        val dummyViewModel = Settings2ViewModel(dummySettings, dummyAssociatedDevices)
        SettingsScreen(viewModel = dummyViewModel)
    }
}

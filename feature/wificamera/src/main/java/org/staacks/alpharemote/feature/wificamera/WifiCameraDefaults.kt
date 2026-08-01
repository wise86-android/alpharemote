package org.staacks.alpharemote.feature.wificamera

import org.staacks.alpharemote.feature.wificamera.domain.WifiCredentials

/**
 * Placeholder credentials for bring-up.
 *
 * Hardcoded on purpose while the connection path is being built. The real source is the BLE
 * handover of PROTOCOL.md §6: characteristic `0000CC06` carries the SSID and `0000CC07` the
 * password, both ASCII from byte 3. The app module already holds a BLE connection to the camera,
 * so that path is a matter of reading two more characteristics and passing the result to
 * `WifiCameraRepository.connect` — nothing else has to change, which is why the repository takes
 * credentials as an argument rather than reading them from here.
 *
 * The camera shows both under MENU > Network > Ctrl w/ Smartphone > Connection info.
 */
object WifiCameraDefaults {

    // TODO: replace with the values from your camera, then move to the BLE handover.
    val CREDENTIALS = WifiCredentials(
        ssid = "DIRECT-m3E1:wiseCam6600",
        password = "pTQRUiBd"
    )
}

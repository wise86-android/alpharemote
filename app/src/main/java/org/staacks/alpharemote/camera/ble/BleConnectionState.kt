package org.staacks.alpharemote.camera.ble

enum class BleConnectionState {
        Idle,
        Connecting,
        Connected,
        Disconnected,
        BoundLost,
        ErrorDuringConnection
}
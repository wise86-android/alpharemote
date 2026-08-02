package org.staacks.alpharemote.core.ble

enum class BleConnectionState {
        Idle,
        Connecting,
        Connected,
        Disconnected,
        BoundLost,
        ErrorDuringConnection
}
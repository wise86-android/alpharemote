package org.staacks.alpharemote.feature.ble.camera.ble

/**
 * Decodes the `CC09` status TLV of the camera control service (`8000CC00`, PROTOCOL.md §6.1).
 *
 * The payload is a run of `[length, idHigh, idLow, value...]` records back to back, where
 * `length` counts the id and value bytes that follow it (not itself). Sony's own app names the
 * fields this feeds via literal callbacks (`onRemoteControlAvailabilityUpdated`,
 * `onImageTransferAvailabilityUpdated`) even though the byte layout itself was reverse engineered,
 * so this walks defensively: any record that doesn't fit is treated as "not present" rather than
 * trusted partially.
 */
internal object CameraControlStatusParsing {

    private const val TLV_ID_REMOTE_CONTROL_AVAILABLE = 3

    /**
     * Returns whether the payload contains a RemoteControlAvailable (id 3) record, and if so,
     * whether it reports the remote as available. `null` means the record was not present in
     * this particular notification (the camera also reports other fields on the same
     * characteristic) — callers should keep whatever value they last knew, not treat this as
     * "unavailable".
     */
    fun remoteControlAvailable(payload: ByteArray): Boolean? {
        var offset = 0
        while (offset < payload.size) {
            val recordLength = payload[offset].toInt() and 0xFF
            if (recordLength < 2 || offset + 1 + recordLength > payload.size) return null

            val idHigh = payload[offset + 1].toInt() and 0xFF
            val idLow = payload[offset + 2].toInt() and 0xFF
            val id = (idHigh shl 8) or idLow

            if (id == TLV_ID_REMOTE_CONTROL_AVAILABLE) {
                val valueStart = offset + 3
                return if (valueStart < offset + 1 + recordLength)
                    payload[valueStart] != 0.toByte()
                else
                    null
            }

            offset += 1 + recordLength
        }
        return null
    }
}

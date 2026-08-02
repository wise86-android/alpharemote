package org.staacks.alpharemote.feature.wificamera.data.ble

/**
 * Pure decoding for the Wi-Fi handover characteristics (PROTOCOL.md §6), kept apart from
 * [WifiHandoverService] so it is testable without any of the Android BLE framework classes that
 * service depends on.
 */
internal object WifiHandoverParsing {

    private const val LAUNCH_STATUS_BYTE = 3
    private const val LAUNCH_FAILURE_REASON_BYTE = 4
    private const val PAYLOAD_START_BYTE = 3

    /** True when byte 3 of a `CC05` notification says the camera's Wi-Fi launched. */
    fun launchSucceeded(payload: ByteArray): Boolean =
        payload.getOrNull(LAUNCH_STATUS_BYTE)?.toInt() == 1

    /** Byte 4 of a `CC05` notification — only meaningful once [launchSucceeded] is false. */
    fun failureReason(payload: ByteArray): Byte? = payload.getOrNull(LAUNCH_FAILURE_REASON_BYTE)

    /**
     * The SSID and password characteristics both carry their ASCII payload starting at byte 3;
     * bytes 0-2 are a header this module has no use for.
     */
    fun asciiFromByteThree(payload: ByteArray): String {
        if (payload.size <= PAYLOAD_START_BYTE) return ""
        return String(
            payload,
            PAYLOAD_START_BYTE,
            payload.size - PAYLOAD_START_BYTE,
            Charsets.US_ASCII
        )
    }
}

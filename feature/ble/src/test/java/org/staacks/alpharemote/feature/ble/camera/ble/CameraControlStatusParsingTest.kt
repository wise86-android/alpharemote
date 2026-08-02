package org.staacks.alpharemote.feature.ble.camera.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraControlStatusParsingTest {

    @Test
    fun `reads remote control available`() {
        val payload = byteArrayOf(0x03, 0x00, 0x03, 0x01)

        assertEquals(true, CameraControlStatusParsing.remoteControlAvailable(payload))
    }

    @Test
    fun `reads remote control unavailable`() {
        val payload = byteArrayOf(0x03, 0x00, 0x03, 0x00)

        assertEquals(false, CameraControlStatusParsing.remoteControlAvailable(payload))
    }

    @Test
    fun `finds the record when other records precede it`() {
        // id 1 (WifiStatus, 2-byte value) followed by id 3 (RemoteControlAvailable)
        val wifiStatusRecord = byteArrayOf(0x04, 0x00, 0x01, 0x00, 0x00)
        val remoteControlRecord = byteArrayOf(0x03, 0x00, 0x03, 0x01)

        assertEquals(
            true,
            CameraControlStatusParsing.remoteControlAvailable(wifiStatusRecord + remoteControlRecord)
        )
    }

    @Test
    fun `returns null when the record is absent`() {
        // id 2 (ImageTransferAvailable) only, no id 3
        val payload = byteArrayOf(0x03, 0x00, 0x02, 0x01)

        assertNull(CameraControlStatusParsing.remoteControlAvailable(payload))
    }

    @Test
    fun `returns null for an empty payload`() {
        assertNull(CameraControlStatusParsing.remoteControlAvailable(ByteArray(0)))
    }

    @Test
    fun `returns null rather than throwing on a truncated record`() {
        // Declares a 5-byte record but only 2 bytes actually follow the length byte.
        val payload = byteArrayOf(0x05, 0x00, 0x03)

        assertNull(CameraControlStatusParsing.remoteControlAvailable(payload))
    }

    @Test
    fun `returns null for a record too short to carry a value`() {
        // length says only the id bytes follow, no value byte at all.
        val payload = byteArrayOf(0x02, 0x00, 0x03)

        assertNull(CameraControlStatusParsing.remoteControlAvailable(payload))
    }
}

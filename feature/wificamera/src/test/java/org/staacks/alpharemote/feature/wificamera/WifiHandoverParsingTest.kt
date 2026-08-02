package org.staacks.alpharemote.feature.wificamera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.staacks.alpharemote.feature.wificamera.data.ble.WifiHandoverParsing

class WifiHandoverParsingTest {

    @Test
    fun `reads a successful launch status`() {
        val payload = byteArrayOf(0x00, 0x00, 0x00, 0x01)

        assertTrue(WifiHandoverParsing.launchSucceeded(payload))
    }

    @Test
    fun `reads a failed launch status with its reason`() {
        val payload = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x07)

        assertFalse(WifiHandoverParsing.launchSucceeded(payload))
        assertEquals(0x07.toByte(), WifiHandoverParsing.failureReason(payload))
    }

    /** A failure notification may be shorter and simply omit the reason byte. */
    @Test
    fun `tolerates a failure notification with no reason byte`() {
        val payload = byteArrayOf(0x00, 0x00, 0x00, 0x00)

        assertFalse(WifiHandoverParsing.launchSucceeded(payload))
        assertNull(WifiHandoverParsing.failureReason(payload))
    }

    @Test
    fun `treats a payload too short to hold a status byte as not launched`() {
        assertFalse(WifiHandoverParsing.launchSucceeded(byteArrayOf(0x00, 0x00)))
        assertFalse(WifiHandoverParsing.launchSucceeded(ByteArray(0)))
    }

    @Test
    fun `reads the ssid from byte three onward`() {
        val payload = byteArrayOf(0x00, 0x00, 0x00) + "DIRECT-abcd:ILCE-6600".toByteArray(Charsets.US_ASCII)

        assertEquals("DIRECT-abcd:ILCE-6600", WifiHandoverParsing.asciiFromByteThree(payload))
    }

    @Test
    fun `reads the password from byte three onward`() {
        val payload = byteArrayOf(0x01, 0x02, 0x03) + "pTQRUiBd".toByteArray(Charsets.US_ASCII)

        assertEquals("pTQRUiBd", WifiHandoverParsing.asciiFromByteThree(payload))
    }

    @Test
    fun `returns an empty string for a payload with no ascii content`() {
        assertEquals("", WifiHandoverParsing.asciiFromByteThree(byteArrayOf(0x00, 0x00, 0x00)))
        assertEquals("", WifiHandoverParsing.asciiFromByteThree(byteArrayOf(0x00)))
        assertEquals("", WifiHandoverParsing.asciiFromByteThree(ByteArray(0)))
    }
}

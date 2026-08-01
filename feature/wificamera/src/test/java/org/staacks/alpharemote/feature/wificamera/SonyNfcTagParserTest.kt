package org.staacks.alpharemote.feature.wificamera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.staacks.alpharemote.feature.wificamera.data.nfc.SonyNfcTagParser
import java.io.ByteArrayOutputStream

class SonyNfcTagParserTest {

    /** Builds a TLV entry with a big-endian length, as a well-formed tag would. */
    private fun entry(tag: Int, value: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write((tag shr 8) and 0xFF)
        write(tag and 0xFF)
        write((value.size shr 8) and 0xFF)
        write(value.size and 0xFF)
        write(value)
    }.toByteArray()

    private fun entry(tag: Int, value: String) = entry(tag, value.toByteArray(Charsets.US_ASCII))

    private fun payload(vararg entries: ByteArray) = ByteArrayOutputStream().apply {
        entries.forEach { write(it) }
    }.toByteArray()

    private val ssidTag = 0x1000
    private val passwordTag = 0x1001
    private val versionTag = 0x0001
    private val urlTag = 0x1003

    @Test
    fun `reads ssid and password from a camera tag`() {
        val tag = SonyNfcTagParser.parse(
            payload(
                entry(versionTag, byteArrayOf(0x01)),
                entry(ssidTag, "DIRECT-m3E1:wiseCam6600"),
                entry(passwordTag, "pTQRUiBd")
            )
        )

        assertEquals("DIRECT-m3E1:wiseCam6600", tag?.ssid)
        assertEquals("pTQRUiBd", tag?.password)
        assertEquals(1, tag?.version)
        assertTrue(tag!!.hasCredentials)
    }

    /** `10 03` lets a first connection skip SSDP; absent on bodies that do not write it. */
    @Test
    fun `reads the device description url when present`() {
        val tag = SonyNfcTagParser.parse(
            payload(
                entry(ssidTag, "DIRECT-abcd:ILCE-6600"),
                entry(passwordTag, "password"),
                entry(urlTag, "http://192.168.122.1:64321/dd.xml")
            )
        )

        assertEquals("http://192.168.122.1:64321/dd.xml", tag?.deviceDescriptionUrl)
    }

    @Test
    fun `tolerates a tag without the optional entries`() {
        val tag = SonyNfcTagParser.parse(
            payload(entry(ssidTag, "DIRECT-abcd:ILCE-6600"), entry(passwordTag, "password"))
        )

        assertNull(tag?.version)
        assertNull(tag?.deviceDescriptionUrl)
        assertTrue(tag!!.hasCredentials)
    }

    /**
     * Both readings of the length field agree below 100, which covers every real SSID (32 bytes)
     * and password (63). This pins that the common case is unambiguous.
     */
    @Test
    fun `both length readings agree for realistic lengths`() {
        val ssid = "DIRECT-m3E1:wiseCam6600"
        assertTrue(ssid.length < 100)

        val decimalEncoded = ByteArrayOutputStream().apply {
            write(0x10); write(0x00)
            // The decompiled reading: high * 10 + low. With a zero high byte it is just the low
            // byte, exactly as big-endian would read it.
            write(0x00); write(ssid.length)
            write(ssid.toByteArray(Charsets.US_ASCII))
        }.toByteArray()

        assertEquals(ssid, SonyNfcTagParser.parse(decimalEncoded)?.ssid)
    }

    /** A truncated tag must not be read as though the missing bytes were there. */
    @Test
    fun `rejects an entry that runs past the end of the payload`() {
        val truncated = byteArrayOf(0x10, 0x00, 0x00, 0x40, 'A'.code.toByte(), 'B'.code.toByte())

        val tag = SonyNfcTagParser.parse(truncated)

        // Neither length reading fits, so nothing is invented.
        assertNull(tag)
    }

    @Test
    fun `ignores padding beyond the values`() {
        val padded = payload(
            entry(ssidTag, "DIRECT-abcd:ILCE-6600"),
            entry(passwordTag, "password")
        ) + ByteArray(8)

        val tag = SonyNfcTagParser.parse(padded)

        assertEquals("DIRECT-abcd:ILCE-6600", tag?.ssid)
        assertEquals("password", tag?.password)
    }

    /** Some bodies pad values to a fixed width; the NULs must not survive into the SSID. */
    @Test
    fun `strips padding from inside a value`() {
        val padded = "ILCE-6600".toByteArray(Charsets.US_ASCII) + ByteArray(6)

        val tag = SonyNfcTagParser.parse(payload(entry(ssidTag, padded)))

        assertEquals("ILCE-6600", tag?.ssid)
    }

    @Test
    fun `reports a tag with no credentials as unusable`() {
        val tag = SonyNfcTagParser.parse(payload(entry(versionTag, byteArrayOf(0x01))))

        assertFalse(tag?.hasCredentials ?: false)
    }

    @Test
    fun `returns null for an empty payload`() {
        assertNull(SonyNfcTagParser.parse(ByteArray(0)))
    }
}

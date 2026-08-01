package org.staacks.alpharemote.feature.wificamera.data.nfc

/**
 * What a Sony camera's NFC tag carries.
 *
 * Every field is optional because the tag is a TLV list and older bodies write fewer entries than
 * newer ones — a missing entry is normal, not corruption.
 */
data class CameraNfcTag(
    val version: Int? = null,
    val ssid: String? = null,
    val password: String? = null,
    /**
     * The device description URL, when the camera writes one.
     *
     * Worth having: it points straight at `dd.xml` and lets the first connection skip SSDP
     * entirely. It goes stale the moment the camera restarts its Wi-Fi, so it is a hint to try
     * first, never a replacement for discovery.
     */
    val deviceDescriptionUrl: String? = null
) {
    /** True when the tag carries enough to join the camera's access point. */
    val hasCredentials: Boolean
        get() = !ssid.isNullOrBlank() && !password.isNullOrBlank()
}

/**
 * Reads the TLV payload of a Sony camera's NDEF record.
 *
 * Layout, from `nfc/NdefDescription` (PROTOCOL.md §1.2):
 *
 * ```
 * 0-1  tag (2 bytes)
 * 2-3  length
 * 4..  value          next entry at +4+length
 * ```
 *
 * **The length encoding is not certain.** The decompiled app computes it as
 * `(b[2] & 0xFF) * 10 + (b[3] & 0xFF)` — decimal rather than a big-endian shift — which the
 * protocol notes looks wrong above 99 and flags for verification against real hardware. In
 * practice the two agree for every length below 100, and an SSID is capped at 32 bytes and a
 * password at 63, so real tags almost certainly never distinguish them. Rather than gamble, this
 * walks the payload with the standard big-endian reading and falls back to the decompiled one if
 * that produces a structure that does not fit the buffer.
 */
object SonyNfcTagParser {

    /** MIME type of the NDEF record Sony cameras publish. */
    const val MIME_TYPE = "application/x-sony-pmm"

    private const val TAG_VERSION = 0x0001
    private const val TAG_SSID = 0x1000
    private const val TAG_PASSWORD = 0x1001
    private const val TAG_DEVICE_DESCRIPTION_URL = 0x1003

    private const val HEADER_SIZE = 4
    private const val NUL = '\u0000'

    /**
     * Returns what could be read, or null if the payload is not a TLV list at all.
     */
    fun parse(payload: ByteArray): CameraNfcTag? {
        val entries = walk(payload, ::bigEndianLength)
            ?: walk(payload, ::decimalLength)
            ?: return null

        if (entries.isEmpty()) return null

        return CameraNfcTag(
            version = entries.firstValue(TAG_VERSION)?.toUnsignedInt(),
            ssid = entries.firstValue(TAG_SSID)?.toAsciiOrNull(),
            password = entries.firstValue(TAG_PASSWORD)?.toAsciiOrNull(),
            deviceDescriptionUrl = entries.firstValue(TAG_DEVICE_DESCRIPTION_URL)?.toAsciiOrNull()
        )
    }

    /**
     * Walks the list with one length interpretation.
     *
     * Null means this interpretation does not describe the buffer — an entry claimed to run past
     * the end, which is the signal to try the other one.
     */
    private fun walk(payload: ByteArray, lengthOf: (Byte, Byte) -> Int): List<Entry>? {
        val entries = mutableListOf<Entry>()
        var offset = 0

        while (offset + HEADER_SIZE <= payload.size) {
            val tag = ((payload[offset].toInt() and 0xFF) shl 8) or
                (payload[offset + 1].toInt() and 0xFF)
            val length = lengthOf(payload[offset + 2], payload[offset + 3])

            if (length < 0) return null
            val valueStart = offset + HEADER_SIZE
            val valueEnd = valueStart + length
            if (valueEnd > payload.size) return null

            entries += Entry(tag, payload.copyOfRange(valueStart, valueEnd))
            offset = valueEnd

            // A zero-length trailing entry with a zero tag is padding, not a record.
            if (tag == 0 && length == 0) break
        }
        return entries
    }

    private fun bigEndianLength(high: Byte, low: Byte): Int =
        ((high.toInt() and 0xFF) shl 8) or (low.toInt() and 0xFF)

    /** What the decompiled bytecode literally does. Kept as the fallback reading. */
    private fun decimalLength(high: Byte, low: Byte): Int =
        (high.toInt() and 0xFF) * 10 + (low.toInt() and 0xFF)

    private fun List<Entry>.firstValue(tag: Int): ByteArray? =
        firstOrNull { it.tag == tag && it.value.isNotEmpty() }?.value

    private fun ByteArray.toAsciiOrNull(): String? = String(this, Charsets.US_ASCII)
        // Some bodies pad the value out to a fixed width rather than truncating it. NUL
        // is not whitespace, so trim() alone would leave it embedded in the SSID.
        .trim { it == NUL || it.isWhitespace() }
        .takeIf { it.isNotEmpty() }

    private fun ByteArray.toUnsignedInt(): Int =
        fold(0) { accumulator, byte -> (accumulator shl 8) or (byte.toInt() and 0xFF) }

    private class Entry(val tag: Int, val value: ByteArray)
}

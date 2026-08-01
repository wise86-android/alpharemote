package org.staacks.alpharemote.feature.wificamera.data.liveview

import org.staacks.alpharemote.feature.wificamera.domain.LiveViewFrame
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * Reads Sony's live view stream.
 *
 * The stream is **not** MJPEG and must never be handed to a video player. Each frame is preceded
 * by a 136-byte Sony header that a decoder will choke on; strip it and what is left is an
 * ordinary JPEG (PROTOCOL.md §3.2).
 *
 * Pure I/O over an [InputStream] with no Android dependency, so the framing is unit-testable
 * against a byte array.
 */
class LiveViewStream(private val source: InputStream) {

    private val header = ByteArray(HEADER_SIZE)

    /**
     * Reads the next payload, or null at end of stream.
     *
     * Returns frame-info payloads as well as images; callers that only draw pictures filter on
     * [LiveViewPayload.isImage].
     */
    @Throws(IOException::class)
    fun readPayload(): LiveViewPayload? {
        if (!fillHeader()) return null

        val payloadSize = header.uint24(PAYLOAD_SIZE_OFFSET)
        val paddingSize = header[PADDING_SIZE_OFFSET].toInt() and 0xFF

        val payload = ByteArray(payloadSize)
        source.readFullyOrThrow(payload)
        source.skipFully(paddingSize.toLong())

        return LiveViewPayload(
            type = header[PAYLOAD_TYPE_OFFSET].toInt() and 0xFF,
            sequenceNumber = header.uint16(SEQUENCE_OFFSET),
            timestampMs = header.uint32(TIMESTAMP_OFFSET),
            data = payload
        )
    }

    /**
     * Reads a header, resynchronising if the stream has drifted.
     *
     * A single dropped byte would otherwise turn every following frame into garbage, so an
     * invalid header is not fatal: the reader shifts forward one byte at a time until the start
     * byte and the `$5hy` start code line up again.
     */
    private fun fillHeader(): Boolean {
        if (!source.readFully(header)) return false

        var resyncBytes = 0
        while (!headerIsValid()) {
            if (resyncBytes++ > MAX_RESYNC_BYTES) {
                throw IOException("Live view stream out of sync after $MAX_RESYNC_BYTES bytes")
            }
            // Shift the window one byte along and read one fresh byte into the gap.
            System.arraycopy(header, 1, header, 0, HEADER_SIZE - 1)
            val next = source.read()
            if (next < 0) return false
            header[HEADER_SIZE - 1] = next.toByte()
        }
        return true
    }

    private fun headerIsValid(): Boolean {
        if (header[0].toInt() and 0xFF != START_BYTE) return false
        return START_CODE.indices.all { header[START_CODE_OFFSET + it] == START_CODE[it] }
    }

    private fun InputStream.readFully(buffer: ByteArray): Boolean {
        var read = 0
        while (read < buffer.size) {
            val count = read(buffer, read, buffer.size - read)
            if (count < 0) return false
            read += count
        }
        return true
    }

    private fun InputStream.readFullyOrThrow(buffer: ByteArray) {
        if (!readFully(buffer)) throw EOFException("Live view stream ended mid-payload")
    }

    private fun InputStream.skipFully(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) {
                if (read() < 0) throw EOFException("Live view stream ended mid-padding")
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun ByteArray.uint16(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 8) or (this[offset + 1].toInt() and 0xFF)

    private fun ByteArray.uint24(offset: Int): Int =
        ((this[offset].toInt() and 0xFF) shl 16) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            (this[offset + 2].toInt() and 0xFF)

    private fun ByteArray.uint32(offset: Int): Long =
        ((this[offset].toLong() and 0xFF) shl 24) or
            ((this[offset + 1].toLong() and 0xFF) shl 16) or
            ((this[offset + 2].toLong() and 0xFF) shl 8) or
            (this[offset + 3].toLong() and 0xFF)

    companion object {
        const val HEADER_SIZE = 136
        const val PAYLOAD_TYPE_IMAGE = 0x01
        const val PAYLOAD_TYPE_FRAME_INFO = 0x02

        private const val START_BYTE = 0xFF
        private const val PAYLOAD_TYPE_OFFSET = 1
        private const val SEQUENCE_OFFSET = 2
        private const val TIMESTAMP_OFFSET = 4
        private const val START_CODE_OFFSET = 8
        private const val PAYLOAD_SIZE_OFFSET = 12
        private const val PADDING_SIZE_OFFSET = 15
        private const val MAX_RESYNC_BYTES = 1 shl 20

        /** `$5hy` — the payload header's start code. */
        private val START_CODE = byteArrayOf(0x24, 0x35, 0x68, 0x79)
    }
}

/**
 * One payload off the stream, still untyped.
 */
class LiveViewPayload(
    val type: Int,
    val sequenceNumber: Int,
    val timestampMs: Long,
    val data: ByteArray
) {
    val isImage: Boolean get() = type == LiveViewStream.PAYLOAD_TYPE_IMAGE

    fun toFrame() = LiveViewFrame(
        jpeg = data,
        sequenceNumber = sequenceNumber,
        timestampMs = timestampMs
    )
}

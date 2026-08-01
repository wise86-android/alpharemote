package org.staacks.alpharemote.feature.wificamera

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.staacks.alpharemote.feature.wificamera.data.liveview.LiveViewStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class LiveViewStreamTest {

    /** Builds one framed payload exactly as the camera sends it. */
    private fun frame(
        payload: ByteArray,
        type: Int = LiveViewStream.PAYLOAD_TYPE_IMAGE,
        sequence: Int = 1,
        timestamp: Long = 0,
        padding: Int = 0
    ): ByteArray {
        val header = ByteArray(LiveViewStream.HEADER_SIZE)
        header[0] = 0xFF.toByte()
        header[1] = type.toByte()
        header[2] = (sequence shr 8).toByte()
        header[3] = sequence.toByte()
        header[4] = (timestamp shr 24).toByte()
        header[5] = (timestamp shr 16).toByte()
        header[6] = (timestamp shr 8).toByte()
        header[7] = timestamp.toByte()
        // "$5hy" start code
        header[8] = 0x24; header[9] = 0x35; header[10] = 0x68; header[11] = 0x79
        header[12] = (payload.size shr 16).toByte()
        header[13] = (payload.size shr 8).toByte()
        header[14] = payload.size.toByte()
        header[15] = padding.toByte()

        return ByteArrayOutputStream().apply {
            write(header)
            write(payload)
            write(ByteArray(padding))
        }.toByteArray()
    }

    private fun streamOf(vararg chunks: ByteArray) = LiveViewStream(
        ByteArrayInputStream(
            ByteArrayOutputStream().apply { chunks.forEach { write(it) } }.toByteArray()
        )
    )

    @Test
    fun `reads a jpeg payload`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())
        val stream = streamOf(frame(jpeg, sequence = 7, timestamp = 123456))

        val payload = stream.readPayload()!!

        assertTrue(payload.isImage)
        assertEquals(7, payload.sequenceNumber)
        assertEquals(123456L, payload.timestampMs)
        assertArrayEquals(jpeg, payload.data)
    }

    @Test
    fun `discards padding between frames`() {
        val first = ByteArray(10) { 1 }
        val second = ByteArray(10) { 2 }
        val stream = streamOf(
            frame(first, sequence = 1, padding = 5),
            frame(second, sequence = 2, padding = 3)
        )

        assertArrayEquals(first, stream.readPayload()!!.data)
        // Only correct if the padding was consumed; otherwise this header would not line up.
        val next = stream.readPayload()!!
        assertEquals(2, next.sequenceNumber)
        assertArrayEquals(second, next.data)
    }

    @Test
    fun `reports frame info payloads separately`() {
        val stream = streamOf(
            frame(ByteArray(11), type = LiveViewStream.PAYLOAD_TYPE_FRAME_INFO)
        )

        val payload = stream.readPayload()!!

        assertEquals(LiveViewStream.PAYLOAD_TYPE_FRAME_INFO, payload.type)
        assertTrue(!payload.isImage)
    }

    /**
     * A single stray byte must not destroy the rest of the stream — without resynchronisation
     * every following frame would be misread.
     */
    @Test
    fun `resynchronises after junk in the stream`() {
        val jpeg = ByteArray(20) { it.toByte() }
        val stream = streamOf(byteArrayOf(0x00, 0x13, 0x37), frame(jpeg, sequence = 42))

        val payload = stream.readPayload()!!

        assertEquals(42, payload.sequenceNumber)
        assertArrayEquals(jpeg, payload.data)
    }

    @Test
    fun `returns null at end of stream`() {
        val stream = streamOf(frame(ByteArray(4)))

        assertEquals(4, stream.readPayload()!!.data.size)
        assertNull(stream.readPayload())
    }

    @Test
    fun `reads a long run of frames in order`() {
        val frames = (1..50).map { frame(ByteArray(64) { _ -> it.toByte() }, sequence = it) }
        val stream = streamOf(*frames.toTypedArray())

        repeat(50) { index ->
            assertEquals(index + 1, stream.readPayload()!!.sequenceNumber)
        }
        assertNull(stream.readPayload())
    }
}

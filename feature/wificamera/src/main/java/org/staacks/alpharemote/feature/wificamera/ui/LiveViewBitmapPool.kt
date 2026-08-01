package org.staacks.alpharemote.feature.wificamera.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.staacks.alpharemote.feature.wificamera.domain.LiveViewFrame

/**
 * Decodes live view frames into a small set of bitmaps that are used over and over.
 *
 * Without this, a 30 fps stream allocates a full-size bitmap thirty times a second and the
 * collector spends its life feeding the garbage collector. `BitmapFactory.Options.inBitmap` lets
 * the decoder write straight into a buffer we already own, so a steady stream settles at zero
 * allocations.
 *
 * **Why a ring rather than two buffers.** The hazard with reuse is overwriting a bitmap while it
 * is still being drawn. Compose's draw phase only records the bitmap into a display list; the GPU
 * reads it later on the render thread, so "the composable returned" does not mean the pixels are
 * finished with, and there is no callback that does mean it. Instead of trying to track that, the
 * ring makes the reuse distance long enough that it cannot matter: with [CAPACITY] buffers at
 * 30 fps a buffer is untouched for about 100 ms before it comes round again, against a render
 * pipeline measured in single-digit milliseconds. Raise [CAPACITY] if a frame ever tears.
 *
 * Not thread-safe, and does not need to be: decoding happens sequentially in one coroutine.
 */
class LiveViewBitmapPool {

    private val buffers = arrayOfNulls<Bitmap>(CAPACITY)
    private var next = 0

    private var framesDecoded = 0L
    private var allocations = 0L

    /**
     * Returns the frame as an [ImageBitmap], or null if it will not decode.
     *
     * The wrapper is new each time even when the underlying bitmap is not. That is deliberate:
     * the wrapper's identity is what tells `StateFlow` and Compose that there is a new frame, and
     * returning the same instance would leave the viewfinder frozen on the first image.
     */
    fun decode(frame: LiveViewFrame): ImageBitmap? {
        val jpeg = frame.jpeg
        val candidate = buffers[next]

        val bitmap = decodeInto(jpeg, candidate) ?: run {
            // A camera can change live view size mid-stream, and a buffer of the old size cannot
            // be reused for the new one. Drop the whole pool and start again at the new size.
            if (candidate != null) {
                Log.d(TAG, "Frame size changed, resetting the buffer pool")
                buffers.fill(null)
            }
            decodeInto(jpeg, null)
        } ?: return null

        if (bitmap !== candidate) allocations++
        buffers[next] = bitmap
        next = (next + 1) % CAPACITY

        framesDecoded++
        if (framesDecoded % STATS_INTERVAL_FRAMES == 0L) {
            Log.d(
                TAG,
                "$framesDecoded frames decoded, $allocations bitmap allocations " +
                    "(${bitmap.width}x${bitmap.height})"
            )
        }
        return bitmap.asImageBitmap()
    }

    /**
     * Drops the buffers so they can be collected.
     *
     * Deliberately not `Bitmap.recycle()`: the most recent frame is very likely still on screen,
     * and recycling it out from under the compositor crashes the draw. Releasing the references
     * is enough — the collector takes them once nothing is drawing them.
     */
    fun clear() {
        buffers.fill(null)
        next = 0
    }

    /**
     * Null when [reuse] cannot hold this frame — either it is the wrong size, or the decoder
     * rejected the data.
     */
    private fun decodeInto(jpeg: ByteArray, reuse: Bitmap?): Bitmap? {
        val options = BitmapFactory.Options().apply {
            // The decoded bitmap has to be mutable to be reusable on a later frame.
            inMutable = true
            inBitmap = reuse
        }
        return try {
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
        } catch (_: IllegalArgumentException) {
            // "Problem decoding into existing bitmap" — the buffer is too small for this frame.
            null
        }
    }

    private companion object {
        const val TAG = "WifiCameraLiveView"

        /** Buffers in the ring. Three gives ~100 ms of slack at 30 fps. */
        const val CAPACITY = 3

        const val STATS_INTERVAL_FRAMES = 300L
    }
}

package org.staacks.alpharemote.feature.wificamera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.staacks.alpharemote.feature.wificamera.data.rpc.CameraApiError
import org.staacks.alpharemote.feature.wificamera.data.rpc.captureFailureMessage
import org.staacks.alpharemote.feature.wificamera.domain.CameraSnapshot
import org.staacks.alpharemote.feature.wificamera.domain.CameraStatus

class CanShootTest {

    private fun snapshot(
        status: CameraStatus = CameraStatus.IDLE,
        apis: Set<String> = setOf(CameraSnapshot.CAPTURE_METHOD)
    ) = CameraSnapshot(status = status, availableApis = apis)

    @Test
    fun `offers the shutter when the camera lists it`() {
        assertTrue(snapshot().canShoot)
    }

    /**
     * The reason the button used to die at random: `cameraStatus` is not dependably IDLE, and
     * gating on it alone disabled the shutter for reasons unrelated to whether a shot was
     * possible.
     */
    @Test
    fun `offers the shutter in states that are not IDLE but are not busy either`() {
        assertTrue(snapshot(status = CameraStatus.UNKNOWN).canShoot)
        assertTrue(snapshot(status = CameraStatus.STREAMING).canShoot)
        assertTrue(snapshot(status = CameraStatus.MOVIE_WAIT_REC_START).canShoot)
    }

    @Test
    fun `withholds the shutter while the camera is working`() {
        assertFalse(snapshot(status = CameraStatus.STILL_CAPTURING).canShoot)
        assertFalse(snapshot(status = CameraStatus.STILL_SAVING).canShoot)
        assertFalse(snapshot(status = CameraStatus.STILL_POST_PROCESSING).canShoot)
        assertFalse(snapshot(status = CameraStatus.NOT_READY).canShoot)
        assertFalse(snapshot(status = CameraStatus.ERROR).canShoot)
    }

    @Test
    fun `withholds the shutter when the camera does not list it`() {
        assertFalse(snapshot(apis = setOf("getEvent", "setFNumber")).canShoot)
    }

    /** Not having been told is not a reason to refuse — let the camera do the refusing. */
    @Test
    fun `offers the shutter when the camera has listed nothing at all`() {
        assertTrue(snapshot(apis = emptySet()).canShoot)
    }

    /**
     * 40400 is the camera saying autofocus was never engaged (PROTOCOL.md §2.4), and it arrives
     * with an empty message — so it reads as a mode problem unless it is named.
     */
    @Test
    fun `points 40400 at autofocus rather than at the camera's state`() {
        val message = captureFailureMessage(CameraApiError.NOT_AVAILABLE_NOW)

        assertTrue(message.contains("Autofocus", ignoreCase = true))
        assertFalse(message.contains("current state", ignoreCase = true))
    }

    @Test
    fun `names the likely cause of the other refusals`() {
        assertTrue(
            captureFailureMessage(CameraApiError.ANY).contains("autofocus", ignoreCase = true)
        )
        assertTrue(
            captureFailureMessage(CameraApiError.CAMERA_NOT_READY).contains("busy")
        )
        assertTrue(
            captureFailureMessage(CameraApiError.API_NOT_PREPARED).contains("not ready")
        )
        assertTrue(
            captureFailureMessage(CameraApiError.NO_SUCH_METHOD).contains("does not support")
        )
        // An unmapped code still says something, and says which code.
        assertTrue(captureFailureMessage(99).contains("99"))
    }
}

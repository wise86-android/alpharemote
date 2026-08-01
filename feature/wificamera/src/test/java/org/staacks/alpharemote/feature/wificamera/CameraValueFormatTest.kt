package org.staacks.alpharemote.feature.wificamera

import org.junit.Assert.assertEquals
import org.junit.Test
import org.staacks.alpharemote.feature.wificamera.domain.CameraSettingId
import org.staacks.alpharemote.feature.wificamera.ui.CameraValueFormat

class CameraValueFormatTest {

    @Test
    fun `decorates aperture`() {
        assertEquals("ƒ5.6", CameraValueFormat.chipValue(CameraSettingId.F_NUMBER, "5.6"))
        assertEquals("ƒ5.6", CameraValueFormat.sheetValue(CameraSettingId.F_NUMBER, "5.6"))
    }

    @Test
    fun `adds a unit only where the sheet has room`() {
        assertEquals(
            "-0.7",
            CameraValueFormat.chipValue(CameraSettingId.EXPOSURE_COMPENSATION, "-0.7")
        )
        assertEquals(
            "-0.7 EV",
            CameraValueFormat.sheetValue(CameraSettingId.EXPOSURE_COMPENSATION, "-0.7")
        )
    }

    @Test
    fun `leaves conventional values alone`() {
        assertEquals("1/250", CameraValueFormat.chipValue(CameraSettingId.SHUTTER_SPEED, "1/250"))
        assertEquals("400", CameraValueFormat.chipValue(CameraSettingId.ISO_SPEED_RATE, "400"))
        assertEquals("AUTO", CameraValueFormat.chipValue(CameraSettingId.ISO_SPEED_RATE, "AUTO"))
    }

    @Test
    fun `abbreviates exposure modes the way a camera back does`() {
        assertEquals("M", CameraValueFormat.exposureModeCode("Manual"))
        assertEquals("A", CameraValueFormat.exposureModeCode("Aperture"))
        assertEquals("S", CameraValueFormat.exposureModeCode("Shutter"))
        assertEquals("P", CameraValueFormat.exposureModeCode("Program Auto"))
        assertEquals("iA", CameraValueFormat.exposureModeCode("Intelligent Auto"))
    }

    @Test
    fun `is not thrown by casing or spacing`() {
        assertEquals("M", CameraValueFormat.exposureModeCode("  manual  "))
        assertEquals("P", CameraValueFormat.exposureModeCode("PROGRAM AUTO"))
    }

    /** Bodies use wording the map will not have; showing something beats showing a dash. */
    @Test
    fun `falls back to the raw value for unknown modes`() {
        assertEquals("HAN", CameraValueFormat.exposureModeCode("Handheld Twilight"))
    }

    @Test
    fun `shows a placeholder when the camera has said nothing`() {
        assertEquals("--", CameraValueFormat.chipValue(CameraSettingId.F_NUMBER, null))
        assertEquals("--", CameraValueFormat.chipValue(CameraSettingId.ISO_SPEED_RATE, ""))
        assertEquals("--", CameraValueFormat.sheetValue(CameraSettingId.SHUTTER_SPEED, null))
    }
}

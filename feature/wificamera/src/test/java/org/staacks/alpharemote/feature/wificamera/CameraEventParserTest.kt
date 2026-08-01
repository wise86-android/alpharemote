package org.staacks.alpharemote.feature.wificamera

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.staacks.alpharemote.feature.wificamera.data.rpc.CameraEventParser
import org.staacks.alpharemote.feature.wificamera.data.rpc.ExposureCompensationScale
import org.staacks.alpharemote.feature.wificamera.domain.CameraSettingId
import org.staacks.alpharemote.feature.wificamera.domain.CameraSnapshot
import org.staacks.alpharemote.feature.wificamera.domain.CameraStatus

class CameraEventParserTest {

    private fun parse(json: String, previous: CameraSnapshot = CameraSnapshot()) =
        CameraEventParser.merge(previous, Json.parseToJsonElement(json) as JsonArray)

    @Test
    fun `reads status and settings from a full event`() {
        val snapshot = parse(
            """
            [
              {"type":"availableApiList","names":["getEvent","setFNumber","setIsoSpeedRate"]},
              {"type":"cameraStatus","cameraStatus":"IDLE"},
              null,
              {"type":"liveviewStatus","liveviewStatus":true},
              {"type":"fNumber","currentFNumber":"5.6","fNumberCandidates":["4.0","5.6","8.0"]},
              {"type":"isoSpeedRate","currentIsoSpeedRate":"400","isoSpeedRateCandidates":["100","400"]},
              {"type":"shutterSpeed","currentShutterSpeed":"1/250","shutterSpeedCandidates":["1/250"]}
            ]
            """.trimIndent()
        )

        assertEquals(CameraStatus.IDLE, snapshot.status)
        assertTrue(snapshot.liveViewActive)
        assertTrue(snapshot.canShoot)
        assertEquals("5.6", snapshot[CameraSettingId.F_NUMBER]?.current?.label)
        assertEquals(
            listOf("4.0", "5.6", "8.0"),
            snapshot[CameraSettingId.F_NUMBER]?.available?.map { it.label }
        )
        assertEquals("1/250", snapshot[CameraSettingId.SHUTTER_SPEED]?.current?.label)
    }

    /** Whether a setting can be written is decided by the API list, not by its own entry. */
    @Test
    fun `marks settings writable only when the camera offers a setter`() {
        val snapshot = parse(
            """
            [
              {"type":"availableApiList","names":["setFNumber"]},
              {"type":"fNumber","currentFNumber":"5.6","fNumberCandidates":["5.6"]},
              {"type":"shutterSpeed","currentShutterSpeed":"1/250","shutterSpeedCandidates":["1/250"]}
            ]
            """.trimIndent()
        )

        assertTrue(snapshot[CameraSettingId.F_NUMBER]!!.writable)
        assertFalse(snapshot[CameraSettingId.SHUTTER_SPEED]!!.writable)
    }

    /** Writability must not depend on where the API list sits in the response. */
    @Test
    fun `applies the api list to settings that preceded it`() {
        val snapshot = parse(
            """
            [
              {"type":"fNumber","currentFNumber":"5.6","fNumberCandidates":["5.6"]},
              {"type":"availableApiList","names":["setFNumber"]}
            ]
            """.trimIndent()
        )

        assertTrue(snapshot[CameraSettingId.F_NUMBER]!!.writable)
    }

    /** Leaving a mode can withdraw a setter; the setting must stop being offered. */
    @Test
    fun `revokes writability when the setter disappears`() {
        val first = parse(
            """
            [
              {"type":"availableApiList","names":["setShutterSpeed"]},
              {"type":"shutterSpeed","currentShutterSpeed":"1/250","shutterSpeedCandidates":["1/250"]}
            ]
            """.trimIndent()
        )
        assertTrue(first[CameraSettingId.SHUTTER_SPEED]!!.writable)

        val second = parse("""[{"type":"availableApiList","names":["getEvent"]}]""", previous = first)

        assertFalse(second[CameraSettingId.SHUTTER_SPEED]!!.writable)
    }

    /**
     * The point of the design: a long poll reports only what changed, so anything the camera
     * leaves out has to survive the merge.
     */
    @Test
    fun `merges a partial event into the previous snapshot`() {
        val first = parse(
            """
            [
              {"type":"availableApiList","names":["setFNumber","setIsoSpeedRate"]},
              {"type":"cameraStatus","cameraStatus":"IDLE"},
              {"type":"fNumber","currentFNumber":"5.6","fNumberCandidates":["4.0","5.6"]},
              {"type":"isoSpeedRate","currentIsoSpeedRate":"400","isoSpeedRateCandidates":["100","400"]}
            ]
            """.trimIndent()
        )

        val second = parse(
            """[null,null,{"type":"isoSpeedRate","currentIsoSpeedRate":"100","isoSpeedRateCandidates":["100","400"]}]""",
            previous = first
        )

        assertEquals("100", second[CameraSettingId.ISO_SPEED_RATE]?.current?.label)
        // Untouched by the second event, and still present.
        assertEquals("5.6", second[CameraSettingId.F_NUMBER]?.current?.label)
        assertEquals(CameraStatus.IDLE, second.status)
    }

    /** Entries are keyed by `type`, so a shifted or version-extended array still parses. */
    @Test
    fun `ignores position`() {
        val snapshot = parse(
            """
            [
              {"type":"fNumber","currentFNumber":"2.8","fNumberCandidates":["2.8"]},
              {"type":"cameraStatus","cameraStatus":"MovieRecording"},
              {"type":"somethingFromAFutureVersion","value":42}
            ]
            """.trimIndent()
        )

        assertEquals(CameraStatus.MOVIE_RECORDING, snapshot.status)
        assertEquals("2.8", snapshot[CameraSettingId.F_NUMBER]?.current?.label)
    }

    @Test
    fun `unwraps array entries`() {
        val snapshot = parse(
            """
            [
              [{"type":"storageInformation","storageID":"Memory Card 1","numberOfRecordableImages":3421,"recordableTime":95}],
              [{"type":"takePicture","takePictureUrl":["http://192.168.122.1:60151/postview/1.JPG"]}]
            ]
            """.trimIndent()
        )

        assertEquals(3421, snapshot.storage?.recordableImages)
        assertEquals("Memory Card 1", snapshot.storage?.storageId)
        assertEquals("http://192.168.122.1:60151/postview/1.JPG", snapshot.latestPostviewUrl)
    }

    /** Reported as an index; the EV reading has to be reconstructed from the step size. */
    @Test
    fun `converts exposure compensation indices to EV`() {
        val snapshot = parse(
            """
            [{"type":"exposureCompensation","currentExposureCompensation":-2,
              "maxExposureCompensation":9,"minExposureCompensation":-9,
              "stepIndexOfExposureCompensation":1}]
            """.trimIndent()
        )

        val setting = snapshot[CameraSettingId.EXPOSURE_COMPENSATION]!!
        assertEquals("-0.7", setting.current?.label)
        // The camera is still given the index, not the EV reading.
        assertEquals("-2", setting.current?.param.toString())
        assertEquals(19, setting.available.size)
        assertEquals("0.0", setting.available[9].label)
    }

    @Test
    fun `honours half stop exposure bodies`() {
        assertEquals(-1.0, ExposureCompensationScale.fromStepIndex(2).evFor(-2), 1e-9)
        assertEquals("+1.0", ExposureCompensationScale.fromStepIndex(2).optionFor(2).label)
        // Missing step index falls back to third stops.
        assertEquals("+0.3", ExposureCompensationScale.fromStepIndex(null).optionFor(1).label)
    }

    @Test
    fun `numeric candidates keep their values`() {
        val snapshot = parse(
            """[{"type":"selfTimer","currentSelfTimer":0,"selfTimerCandidates":[0,2,10]}]"""
        )

        val setting = snapshot[CameraSettingId.SELF_TIMER]!!
        assertEquals("0", setting.current?.label)
        assertEquals(listOf("0", "2", "10"), setting.available.map { it.label })
    }

    @Test
    fun `tolerates an empty event`() {
        val snapshot = parse("[null,null,null]")

        assertEquals(CameraStatus.UNKNOWN, snapshot.status)
        assertTrue(snapshot.settings.isEmpty())
        assertNull(snapshot.storage)
    }
}

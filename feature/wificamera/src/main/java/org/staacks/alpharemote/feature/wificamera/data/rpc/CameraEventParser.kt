package org.staacks.alpharemote.feature.wificamera.data.rpc

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.staacks.alpharemote.feature.wificamera.domain.BatteryInfo
import org.staacks.alpharemote.feature.wificamera.domain.CameraOption
import org.staacks.alpharemote.feature.wificamera.domain.CameraSetting
import org.staacks.alpharemote.feature.wificamera.domain.CameraSettingId
import org.staacks.alpharemote.feature.wificamera.domain.CameraSnapshot
import org.staacks.alpharemote.feature.wificamera.domain.CameraStatus
import org.staacks.alpharemote.feature.wificamera.domain.StorageInfo

/**
 * Turns a `getEvent` response into a [CameraSnapshot].
 *
 * **Entries are matched on their `type` field, not on their position.** The response is
 * documented as a positional array, but the layout is only inferred from the official app's
 * constructor order, it grows at the end with every API version, and entries are null whenever
 * nothing changed. Every entry also carries its own `type`, so keying on that is both simpler and
 * immune to the indices being wrong — which PROTOCOL.md §2.5 explicitly warns they might be.
 *
 * Merging rather than replacing is required: a long poll reports only what changed and leaves
 * everything else null.
 */
object CameraEventParser {

    /**
     * Entry types this parser does something with.
     *
     * Exposed so callers can notice the ones it does not — the only way to find out what an
     * undocumented body actually reports, since anything unrecognised is silently ignored.
     */
    val handledTypes: Set<String> = setOf(
        "availableApiList",
        "cameraStatus",
        "liveviewStatus",
        "cameraFunction",
        "focusStatus",
        "takePicture",
        "storageInformation",
        "batteryInfo"
    ) + CameraSettingId.entries.map { it.eventType }

    fun merge(previous: CameraSnapshot, result: JsonArray): CameraSnapshot {
        var snapshot = previous
        // Some entries are objects, some are arrays of objects (storage, takePicture), and
        // unchanged ones are null. Flatten to the objects and let each one apply itself.
        result.flatMap { it.asObjects() }.forEach { entry ->
            snapshot = snapshot.apply(entry)
        }
        return snapshot.withWritabilityFromApiList()
    }

    /**
     * Recomputes which settings have a setter, once, after everything else has been applied.
     *
     * Whether a setting can be written changes with the camera's mode and is reported by the API
     * list rather than by the setting's own entry. Doing this at the end means the result does
     * not depend on whether the API list happened to come before or after the settings in the
     * response.
     */
    private fun CameraSnapshot.withWritabilityFromApiList(): CameraSnapshot = copy(
        settings = settings.mapValues { (id, setting) ->
            setting.copy(writable = id.setMethod in availableApis)
        }
    )

    private fun JsonElement.asObjects(): List<JsonObject> = when (this) {
        is JsonObject -> listOf(this)
        is JsonArray -> flatMap { it.asObjects() }
        else -> emptyList()
    }

    private fun CameraSnapshot.apply(entry: JsonObject): CameraSnapshot {
        val type = entry.string("type") ?: return this

        return when (type) {
            "availableApiList" ->
                copy(availableApis = entry.stringList("names").toSet())

            "cameraStatus" ->
                copy(status = CameraStatus.fromWireName(entry.string("cameraStatus")))

            "liveviewStatus" ->
                copy(liveViewActive = entry.bool("liveviewStatus") ?: liveViewActive)

            "cameraFunction" ->
                copy(cameraFunction = entry.string("currentCameraFunction") ?: cameraFunction)

            // Undocumented for the bodies we target, and absent on the α6600. Read
            // opportunistically: if a camera does report focus, we show it; if not, nothing
            // depends on it.
            "focusStatus" ->
                copy(focusStatus = entry.string("focusStatus") ?: focusStatus)

            "takePicture" ->
                copy(latestPostviewUrl = entry.stringList("takePictureUrl").lastOrNull()
                    ?: latestPostviewUrl)

            "storageInformation" -> copy(
                storage = StorageInfo(
                    storageId = entry.string("storageID"),
                    recordableImages = entry.int("numberOfRecordableImages"),
                    recordableTimeMinutes = entry.int("recordableTime")
                )
            )

            "batteryInfo" -> copy(
                battery = BatteryInfo(
                    levelPercent = entry.batteryPercent(),
                    status = entry.string("status")
                )
            )

            CameraSettingId.EXPOSURE_COMPENSATION.eventType -> withSetting(parseExposure(entry))

            else -> CameraSettingId.fromEventType(type)
                ?.let { withSetting(parseSetting(it, entry)) }
                ?: this
        }
    }

    private fun CameraSnapshot.withSetting(setting: CameraSetting): CameraSnapshot =
        copy(settings = settings + (setting.id to setting))

    private fun parseSetting(id: CameraSettingId, entry: JsonObject): CameraSetting {
        val current = entry.scalar(id.currentKey)
        val candidates = (entry[id.candidatesKey] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull } }
            ?.map { CameraOption(it.content, it) }
            .orEmpty()

        return CameraSetting(
            id = id,
            current = current?.let { CameraOption(it.content, it) },
            available = candidates
        )
    }

    /**
     * Exposure compensation is the one setting the camera reports as a bare index instead of a
     * value: the readable "-0.3" has to be reconstructed from the index and the step size the
     * camera is using. The index still travels as the parameter, so [CameraOption] carries both.
     */
    private fun parseExposure(entry: JsonObject): CameraSetting {
        val id = CameraSettingId.EXPOSURE_COMPENSATION
        val currentIndex = entry.int(id.currentKey)
        val min = entry.int("minExposureCompensation")
        val max = entry.int("maxExposureCompensation")
        val step = ExposureCompensationScale.fromStepIndex(entry.int("stepIndexOfExposureCompensation"))

        val available = if (min != null && max != null && min <= max) {
            (min..max).map { step.optionFor(it) }
        } else {
            emptyList()
        }

        return CameraSetting(
            id = id,
            current = currentIndex?.let { step.optionFor(it) },
            available = available
        )
    }

    /** Bodies disagree on the key; accept the ones seen in the wild. */
    private fun JsonObject.batteryPercent(): Int? =
        int("batteryLevelPercent") ?: int("levelPercent") ?: int("batteryLevel")

    private fun JsonObject.scalar(key: String): JsonPrimitive? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }

    private fun JsonObject.string(key: String): String? = scalar(key)?.content

    private fun JsonObject.int(key: String): Int? = scalar(key)?.intOrNull

    private fun JsonObject.bool(key: String): Boolean? = when (scalar(key)?.content) {
        "true" -> true
        "false" -> false
        else -> null
    }

    private fun JsonObject.stringList(key: String): List<String> =
        (this[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }
            .orEmpty()
}

/**
 * Converts between exposure compensation indices and EV readings.
 *
 * `stepIndexOfExposureCompensation` is 1 for third-stop bodies and 2 for half-stop ones; an
 * index of -2 therefore means -0.7 EV on one camera and -1.0 EV on another.
 */
enum class ExposureCompensationScale(val evPerStep: Double) {
    THIRD_STOP(1.0 / 3.0),
    HALF_STOP(0.5);

    fun evFor(index: Int): Double = index * evPerStep

    fun optionFor(index: Int): CameraOption =
        CameraOption(label = format(evFor(index)), param = JsonPrimitive(index))

    private fun format(ev: Double): String {
        val rounded = Math.round(ev * 10.0) / 10.0
        val sign = if (rounded > 0) "+" else ""
        return sign + String.format(java.util.Locale.US, "%.1f", rounded)
    }

    companion object {
        /** Unknown or absent step index: third stops, which is what most bodies use. */
        fun fromStepIndex(stepIndex: Int?): ExposureCompensationScale =
            if (stepIndex == 2) HALF_STOP else THIRD_STOP
    }
}

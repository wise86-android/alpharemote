package org.staacks.alpharemote.feature.wificamera.data.rpc

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.staacks.alpharemote.feature.wificamera.domain.CameraSetting
import org.staacks.alpharemote.feature.wificamera.domain.CameraSettingId
import org.staacks.alpharemote.feature.wificamera.domain.CameraSnapshot

/**
 * Formats `getEvent` results for logcat.
 *
 * Separate from the repository and free of Android types so the formatting is testable, and so
 * the cost of building these strings is visible at the call site rather than hidden in a log
 * statement.
 */
internal object CameraEventLog {

    /**
     * One line per thing that actually changed. An unchanged long poll produces nothing, which is
     * what keeps the log readable over a session lasting minutes.
     */
    fun describeChanges(previous: CameraSnapshot, current: CameraSnapshot): List<String> {
        val lines = mutableListOf<String>()

        if (previous.status != current.status) {
            lines += "status ${previous.status} -> ${current.status}"
        }
        if (previous.availableApis != current.availableApis) {
            lines += "availableApis ${previous.availableApis.size} -> ${current.availableApis.size}"
        }
        if (previous.liveViewActive != current.liveViewActive) {
            lines += "liveView ${previous.liveViewActive} -> ${current.liveViewActive}"
        }
        if (previous.latestPostviewUrl != current.latestPostviewUrl) {
            lines += "postview ${current.latestPostviewUrl}"
        }
        if (previous.focusStatus != current.focusStatus) {
            lines += "focusStatus ${previous.focusStatus} -> ${current.focusStatus}"
        }

        CameraSettingId.entries.forEach { id ->
            val before = previous[id]
            val after = current[id]
            if (before != after) {
                lines += "${id.name} ${describe(before)} -> ${describe(after)}"
            }
        }

        return lines
    }

    /**
     * A setting rendered for the log: current value, how many options came with it, and whether
     * the camera offered a setter. All three matter when a control looks wrong on screen — an
     * empty option list and a missing setter both produce a chip that will not open.
     */
    fun describe(setting: CameraSetting?): String {
        if (setting == null) return "absent"
        val current = setting.current?.label ?: "none"
        val flags = if (setting.writable) "writable" else "read-only"
        return "$current [${setting.available.size} options, $flags]"
    }

    /**
     * The raw entry of a given type, straight from the camera.
     *
     * Worth having next to the parsed value: it is the only way to tell a parsing mistake from a
     * camera that genuinely reported nothing.
     */
    fun rawEntry(result: JsonArray, type: String): String =
        result.entryOfType(type)?.toString() ?: "no \"$type\" entry in this event"

    /**
     * Every `type` the camera mentioned in this event.
     *
     * Used to notice entries nothing in the parser handles. That is the only way to find out
     * whether a body reports something undocumented — a focus status, say — since an unrecognised
     * entry is otherwise silently ignored.
     */
    fun typesIn(result: JsonArray): Set<String> = result.asSequence()
        .flatMap { element ->
            when (element) {
                is JsonObject -> sequenceOf(element)
                is JsonArray -> element.asSequence().filterIsInstance<JsonObject>()
                else -> emptySequence()
            }
        }
        .mapNotNull { (it["type"] as? JsonPrimitive)?.jsonPrimitive?.content }
        .toSet()
}

/** The entry of a given `type` in a `getEvent` result, or null when it did not report one. */
internal fun JsonArray.entryOfType(type: String): JsonObject? = asSequence()
    .flatMap { element ->
        when (element) {
            is JsonObject -> sequenceOf(element)
            is JsonArray -> element.asSequence().filterIsInstance<JsonObject>()
            else -> emptySequence()
        }
    }
    .firstOrNull { (it["type"] as? JsonPrimitive)?.jsonPrimitive?.content == type }

package org.staacks.alpharemote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.staacks.alpharemote.camera.CameraAction
import org.staacks.alpharemote.camera.CameraActionPreset
import org.staacks.alpharemote.service.AlphaRemoteService
import java.io.Serializable
import java.util.Locale

const val EXTERNAL_INTENT_ACTION = "org.staacks.alpharemote.EXT_BUTTON"
const val EXTERNAL_INTENT_PRESET_EXTRA = "preset"
const val EXTERNAL_INTENT_TOGGLE_EXTRA = "toggle"
const val EXTERNAL_INTENT_SELFTIMER_EXTRA = "selftimer"
const val EXTERNAL_INTENT_DURATION_EXTRA = "duration"
const val EXTERNAL_INTENT_STEP_EXTRA = "step"
const val EXTERNAL_INTENT_DOWN_EXTRA = "down"
const val EXTERNAL_INTENT_UP_EXTRA = "up"

class CameraBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("intent", "BroadcastReceiver received: $intent")

        val ctx = context ?: return
        if (!AlphaRemoteService.broadcastControl) {
            Log.w("intent", "Broadcast control not allowed by user.")
            return
        }

        intent?.let { extIntent ->
            if (extIntent.action == EXTERNAL_INTENT_ACTION) {
                val presetIn = extIntent.getStringExtra(EXTERNAL_INTENT_PRESET_EXTRA) ?: ""
                val toggle = extIntent.getBooleanExtra(EXTERNAL_INTENT_TOGGLE_EXTRA, false)
                val selftimerIn = extIntent.getFloatExtra(EXTERNAL_INTENT_SELFTIMER_EXTRA, -1.0f)
                val durationIn = extIntent.getFloatExtra(EXTERNAL_INTENT_DURATION_EXTRA, -1.0f)
                val stepIn = extIntent.getFloatExtra(EXTERNAL_INTENT_STEP_EXTRA, -1.0f)

                val selftimer = if (selftimerIn > 0) selftimerIn else null
                val duration = if (durationIn > 0) durationIn else null
                val step = if (stepIn >= 0) stepIn else null

                val down = extIntent.getBooleanExtra(EXTERNAL_INTENT_DOWN_EXTRA, true)
                val up = extIntent.getBooleanExtra(EXTERNAL_INTENT_UP_EXTRA, true)

                try {
                    val preset = CameraActionPreset.valueOf(presetIn.uppercase(Locale.getDefault()))
                    val cameraAction = CameraAction(toggle, selftimer, duration, step, preset)
                    val serviceIntent = Intent(context, AlphaRemoteService::class.java).apply {
                        action = AlphaRemoteService.BUTTON_INTENT_ACTION
                        putExtra(AlphaRemoteService.BUTTON_INTENT_CAMERA_ACTION_EXTRA, cameraAction as Serializable)
                        putExtra(AlphaRemoteService.BUTTON_INTENT_CAMERA_ACTION_UP_EXTRA, up)
                        putExtra(AlphaRemoteService.BUTTON_INTENT_CAMERA_ACTION_DOWN_EXTRA, down)
                    }
                    ctx.startService(serviceIntent)
                } catch (e: Exception) {
                    Log.e("intent","Invalid intent:\nPreset = $presetIn\nToggle = $toggle\nSelftimer = $selftimerIn\nDuration = $durationIn\nStep = $stepIn\nError: $e")
                }
            }
        }
    }
}
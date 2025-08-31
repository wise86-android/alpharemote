package org.staacks.alpharemote.ui.camera

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.TypedValue
import org.staacks.alpharemote.R
import org.staacks.alpharemote.camera.ButtonCode
import org.staacks.alpharemote.camera.CameraState
import org.staacks.alpharemote.camera.JogCode

class DefaultRemoteButton(context: Context, attrs: AttributeSet?): androidx.appcompat.widget.AppCompatImageView(context, attrs) {

    enum class Button { //IMPORTANT! Make sure that this matches the enum in attrs.xml
        SHUTTER,
        SHUTTER_HALF,
        SELFTIMER_3S,
        RECORD,
        C1,
        AF_ON,
        ZOOM_IN,
        ZOOM_OUT,
        FOCUS_FAR,
        FOCUS_NEAR
    }

    val button: Button = attrs?.let {
        val typedArray = context.obtainStyledAttributes(it, R.styleable.DefaultRemoteButton)
        val buttonIndex = typedArray.getInt(R.styleable.DefaultRemoteButton_button, 0)
        typedArray.recycle()
        Button.entries[buttonIndex]
    } ?: Button.SHUTTER

    fun updateCameraState(cameraState: CameraState) {
        if (button == Button.RECORD)
            return

        if (cameraState is CameraState.Ready) {
            val pressed = when (button) {
                Button.SHUTTER -> ButtonCode.SHUTTER_FULL in cameraState.pressedButtons
                Button.SHUTTER_HALF -> ButtonCode.SHUTTER_HALF in cameraState.pressedButtons
                Button.C1 -> ButtonCode.C1 in cameraState.pressedButtons
                Button.AF_ON -> ButtonCode.AF_ON in cameraState.pressedButtons
                Button.ZOOM_IN -> JogCode.ZOOM_IN in cameraState.pressedJogs
                Button.ZOOM_OUT -> JogCode.ZOOM_OUT in cameraState.pressedJogs
                Button.FOCUS_FAR -> JogCode.FOCUS_FAR in cameraState.pressedJogs
                Button.FOCUS_NEAR -> JogCode.FOCUS_NEAR in cameraState.pressedJogs
                else -> false
            }
            val colorAttr = TypedValue()
            val color = if (pressed) {
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorSecondary, colorAttr,true)
                context.getColor(colorAttr.resourceId)
            } else {
                context.theme.resolveAttribute(R.attr.colorCustomButton, colorAttr,true)
                context.getColor(colorAttr.resourceId)
            }
            this.imageTintList = ColorStateList.valueOf(color)
        } else {
            this.imageTintList = ColorStateList.valueOf(context.getColor(R.color.gray50))
        }
    }
}
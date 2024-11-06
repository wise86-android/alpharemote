package org.staacks.alpharemote.camera

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat.getColor
import androidx.core.content.ContextCompat.getDrawable
import org.staacks.alpharemote.R
import kotlin.math.roundToInt


class CameraActionIcon(val context: Context, private val cameraAction: CameraAction) : Drawable() {

    private val baseDrawable: Drawable? = getDrawable(context, cameraAction.preset.template.icon)
    private val paint = Paint()
    private val paintBG = Paint()
    private val black = getColor(context, R.color.black)
    private val white = getColor(context, R.color.white)

    init {
        paint.color = black
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT

        paintBG.color = white
        paintBG.isAntiAlias = true
        paintBG.style = Paint.Style.FILL
    }

    private fun drawTextInBox(canvas: Canvas, x: Float, y: Float, margin: Float, textSize: Float, text: String, anchorRight: Boolean, anchorBottom: Boolean) {
        paint.textSize = textSize

        val textBounds = Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)

        val cornerRadius = textBounds.height() / 4.0f

        val xoffset = if (anchorRight) {
            x - textBounds.right - cornerRadius - margin
        } else {
            x - textBounds.left + cornerRadius + margin
        }
        val yoffset = if (anchorBottom) {
            y - textBounds.bottom - margin
        } else {
            y - textBounds.top + margin
        }

        val backgroundBounds = RectF(
            textBounds.left + xoffset - margin - cornerRadius,
            textBounds.top + yoffset - margin,
            textBounds.right + xoffset + margin + cornerRadius,
            textBounds.bottom + yoffset + margin
        )

        canvas.drawRoundRect(backgroundBounds, cornerRadius, cornerRadius, paintBG)
        canvas.drawText(text, xoffset, yoffset, paint)
    }

    override fun draw(canvas: Canvas) {

        baseDrawable?.bounds = bounds
        baseDrawable?.draw(canvas)

        val w = bounds.width()

        cameraAction.selftimer?.let {
            val text = "T" + it.roundToInt().toString()
            drawTextInBox(canvas, 0.0f, 0.0f, w*0.02f, 0.3f * w, text,
                anchorRight = false,
                anchorBottom = false
            )
        }

        cameraAction.duration?.let {
            val text = "D" + it.roundToInt().toString()
            drawTextInBox(canvas, w.toFloat(), w.toFloat(), w*0.02f, 0.3f * w, text,
                anchorRight = true,
                anchorBottom = true
            )
        }

        if (cameraAction.toggle) {
            val text = "↹"
            drawTextInBox(canvas, 0.0f, w.toFloat(), w*0.02f, 0.5f * w, text,
                anchorRight = false,
                anchorBottom = true
            )
        }

        cameraAction.step?.let {
            val text = "›".repeat((3.0*it).roundToInt())
            if (text.isNotEmpty())
                drawTextInBox(canvas, w.toFloat(), 0.0f, w*0.03f, 0.5f * w, text,
                    anchorRight = true,
                    anchorBottom = false
                )
        }

    }

    override fun setAlpha(alpha: Int) {
        baseDrawable?.alpha = alpha
        paint.alpha = alpha
        paintBG.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        baseDrawable?.colorFilter = colorFilter
        paint.colorFilter = colorFilter
        paintBG.colorFilter = colorFilter
    }

    override fun setTint(tintColor: Int) {
        super.setTint(tintColor)
        if (!cameraAction.preset.template.preserveColor)
            baseDrawable?.setTint(tintColor)
    }

    override fun setTintBlendMode(blendMode: BlendMode?) {
        super.setTintBlendMode(blendMode)
        if (!cameraAction.preset.template.preserveColor)
            baseDrawable?.setTintBlendMode(blendMode)
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        super.setTintMode(tintMode)
        if (!cameraAction.preset.template.preserveColor)
            baseDrawable?.setTintMode(tintMode)
    }

    override fun setTintList(tint: ColorStateList?) {
        super.setTintList(tint)
        if (!cameraAction.preset.template.preserveColor)
            baseDrawable?.setTintList(tint)
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }
}
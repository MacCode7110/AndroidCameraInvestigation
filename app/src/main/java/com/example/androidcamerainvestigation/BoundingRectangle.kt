package com.example.androidcamerainvestigation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class BoundingRectangle(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var boundingRect: RectF? = null
    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    fun setBoundingRect(rect: Rect, imageWidth: Int, imageHeight: Int) {
        val viewWidthF = width.toFloat()
        val viewHeightF = height.toFloat()

        if (viewWidthF == 0f || viewHeightF == 0f || imageWidth == 0 || imageHeight == 0) {
            return
        }

        val imgWidthF = imageWidth.toFloat()
        val imgHeightF = imageHeight.toFloat()

        // Scale factor for ContentScale.Crop
        val scaleFactor = max(viewWidthF / imgWidthF, viewHeightF / imgHeightF)

        val scaledImageWidth = imgWidthF * scaleFactor
        val scaledImageHeight = imgHeightF * scaleFactor

        // Calculated offsets for centering the cropped image
        val offsetX = (viewWidthF - scaledImageWidth) / 2
        val offsetY = (viewHeightF - scaledImageHeight) / 2

        boundingRect = RectF(
            rect.left * scaleFactor + offsetX,
            rect.top * scaleFactor + offsetY,
            rect.right * scaleFactor + offsetX,
            rect.bottom * scaleFactor + offsetY
        )
        invalidate()
    }

    fun clear() {
        if (boundingRect != null) {
            boundingRect = null
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        boundingRect?.let {
            canvas.drawRect(it, paint)
        }
    }
}

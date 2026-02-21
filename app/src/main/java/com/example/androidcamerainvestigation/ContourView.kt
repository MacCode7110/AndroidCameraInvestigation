package com.example.androidcamerainvestigation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.face.Face
import kotlin.math.max

class ContourView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var faces: List<Face> = emptyList()
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    private val paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        strokeWidth = 4f
    }

    fun setContours(faces: List<Face>, imageWidth: Int, imageHeight: Int) {
        this.faces = faces
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        invalidate()
    }

    fun clear() {
        if (faces.isNotEmpty()) {
            faces = emptyList()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (faces.isEmpty() || imageWidth == 0 || imageHeight == 0) return

        val viewWidthF = width.toFloat()
        val viewHeightF = height.toFloat()
        val imgWidthF = imageWidth.toFloat()
        val imgHeightF = imageHeight.toFloat()

        val scaleFactor = max(viewWidthF / imgWidthF, viewHeightF / imgHeightF)
        val scaledImageWidth = imgWidthF * scaleFactor
        val scaledImageHeight = imgHeightF * scaleFactor
        val offsetX = (viewWidthF - scaledImageWidth) / 2
        val offsetY = (viewHeightF - scaledImageHeight) / 2

        for (face in faces) {
            for (contour in face.allContours) {
                for (point in contour.points) {
                    val scaledPoint = PointF(
                        point.x * scaleFactor + offsetX,
                        point.y * scaleFactor + offsetY
                    )
                    canvas.drawCircle(scaledPoint.x, scaledPoint.y, 3f, paint)
                }
            }
        }
    }
}

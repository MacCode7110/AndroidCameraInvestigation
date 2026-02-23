package com.example.androidcamerainvestigation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.facemesh.FaceMesh
import kotlin.math.max

class MeshView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var faceMeshes: List<FaceMesh> = emptyList()
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0

    private val pointPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.FILL
        strokeWidth = 2f
    }

    private val linePaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 1f
        alpha = 128
    }

    fun setMeshes(meshes: List<FaceMesh>, imageWidth: Int, imageHeight: Int) {
        this.faceMeshes = meshes
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight

        post { invalidate() }
    }

    fun clear() {
        if (faceMeshes.isNotEmpty()) {
            faceMeshes = emptyList()
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (faceMeshes.isEmpty() || imageWidth == 0 || imageHeight == 0) {
            return
        }

        val viewWidthF = width.toFloat()
        val viewHeightF = height.toFloat()
        val imgWidthF = imageWidth.toFloat()
        val imgHeightF = imageHeight.toFloat()

        val scaleFactor = max(viewWidthF / imgWidthF, viewHeightF / imgHeightF)
        val scaledImageWidth = imgWidthF * scaleFactor
        val scaledImageHeight = imgHeightF * scaleFactor
        val offsetX = (viewWidthF - scaledImageWidth) / 2
        val offsetY = (viewHeightF - scaledImageHeight) / 2

        for (mesh in faceMeshes) {

            mesh.allTriangles.forEach { triangle ->
                val points = triangle.allPoints
                if (points.size == 3) {
                    val p1 = scalePoint(points[0], scaleFactor, offsetX, offsetY)
                    val p2 = scalePoint(points[1], scaleFactor, offsetX, offsetY)
                    val p3 = scalePoint(points[2], scaleFactor, offsetX, offsetY)

                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint)
                    canvas.drawLine(p2.x, p2.y, p3.x, p3.y, linePaint)
                    canvas.drawLine(p3.x, p3.y, p1.x, p1.y, linePaint)
                }
            }

            mesh.allPoints.forEach { point ->
                val scaledPoint = scalePoint(point, scaleFactor, offsetX, offsetY)
                canvas.drawCircle(scaledPoint.x, scaledPoint.y, 2f, pointPaint)
            }
        }
    }

    private fun scalePoint(
        point: com.google.mlkit.vision.facemesh.FaceMeshPoint,
        scaleFactor: Float,
        offsetX: Float,
        offsetY: Float
    ): PointF {
        return PointF(
            point.position.x * scaleFactor + offsetX,
            point.position.y * scaleFactor + offsetY
        )
    }
}


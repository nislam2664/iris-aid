package com.example.irisaid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt

class FaceOverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var faceRect: Rect? = null
    private var landmarks: List<PointF> = emptyList()
    private val textPaint: Paint = Paint().apply {
        color = "#48F4E8".toColorInt()
        textSize = 50f
        textAlign = Paint.Align.CENTER
    }
    private val paint: Paint = Paint().apply {
        color = "#48F4E8".toColorInt()
        style = Paint.Style.STROKE
        strokeWidth = 7f
    }
    private val landmarkPaint = Paint().apply {
        color = "#FFA666".toColorInt()
        style = Paint.Style.FILL
    }

    var previewWidth: Int = 0
    var previewHeight: Int = 0

    fun setFaceBounds(rect: Rect?) {
        faceRect = rect
        postInvalidate()
    }

    fun setLandmarks(points: List<PointF>) {
        landmarks = points
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (previewWidth == 0 || previewHeight == 0) return

        if (faceRect == null) {
            canvas.drawText(
                "No face detected",
                width / 2f,
                height / 2f,
                textPaint
            )
        } else {
            val rectToDraw: Rect = faceRect!!
            val offsetX = -100f
            val offsetY = -200f

            val scaleX = width.toFloat() / previewWidth
            val scaleY = height.toFloat() / previewHeight
            val left: Float = (previewWidth - rectToDraw.right) * scaleX + offsetX
            val right: Float = (previewWidth - rectToDraw.left) * scaleX + offsetX
            val top = rectToDraw.top * scaleY + offsetY
            val bottom = rectToDraw.bottom * scaleY  + offsetY

            canvas.drawRect(left, top, right, bottom, paint)

            for (point in landmarks) {
                val cx = (previewWidth - point.x) * scaleX
                val cy = point.y * scaleY

                canvas.drawCircle(
                    cx + offsetX,
                    cy + offsetY,
                    5f,
                    landmarkPaint
                )
            }
        }
    }
}
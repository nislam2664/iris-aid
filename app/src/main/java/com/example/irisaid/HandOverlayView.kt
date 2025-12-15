/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.irisaid

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.max
import kotlin.math.min

class HandOverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var results: HandLandmarkerResult? = null
    private var linePaint = Paint()
    private var pointPaint = Paint()
    private var textPaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    init {
        initPaints()
    }

    fun clear() {
        results = null
        linePaint.reset()
        pointPaint.reset()
        textPaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        linePaint.color = "#A281F7".toColorInt()
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        pointPaint.style = Paint.Style.FILL

        textPaint.color = "#A281F7".toColorInt()
        textPaint.textSize = 50f
        textPaint.textAlign = Paint.Align.CENTER
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        if (results == null || results!!.landmarks().isEmpty()) {
            canvas.drawText(
                "No hand detected",
                width / 2f,
                height / 2f,
                textPaint
            )
        } else {
            // Scale from image size → view size
            val scaleX = width.toFloat() / imageWidth
            val scaleY = height.toFloat() / imageHeight

            val offsetX = -10f
            val offsetY = -30f

            for (landmarkList in results!!.landmarks()) {
                // Draw points
                for (lm in landmarkList) {

                    // Normalized → pixel coordinates in input image
                    val x = lm.x() * imageWidth + offsetX
                    val y = lm.y() * imageHeight + offsetY

                    val drawX = x * scaleX
                    val drawY = y * scaleY

                    canvas.drawPoint(drawX, drawY, pointPaint)
                }

                // Draw connections
                for (conn in HandLandmarker.HAND_CONNECTIONS) {
                    val start = landmarkList[conn!!.start()]
                    val end = landmarkList[conn.end()]

                    // Convert start
                    val sx = start.x() * imageWidth + offsetX
                    val sy = start.y() * imageHeight + offsetY
                    val drawSX = sx * scaleX
                    val drawSY = sy * scaleY

                    // Convert end
                    val ex = end.x() * imageWidth + offsetX
                    val ey = end.y() * imageHeight + offsetY
                    val drawEX = ex * scaleX
                    val drawEY = ey * scaleY

                    canvas.drawLine(drawSX, drawSY, drawEX, drawEY, linePaint)
                }
            }
        }
    }

    fun setResults(
        handLandmarkerResults: HandLandmarkerResult?,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        results = handLandmarkerResults

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        scaleFactor = when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                min(width * 1f / imageWidth, height * 1f / imageHeight)
            }
            RunningMode.LIVE_STREAM -> {
                // PreviewView is in FILL_START mode. So we need to scale up the
                // landmarks to match with the size that the captured images will be
                // displayed.
                max(width * 1f / imageWidth, height * 1f / imageHeight)
            }
        }
        invalidate()
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 8F
    }
}

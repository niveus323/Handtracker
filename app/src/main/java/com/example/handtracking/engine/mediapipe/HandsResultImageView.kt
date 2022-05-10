package com.example.handtracking.engine.mediapipe

import android.content.Context
import android.graphics.*
import androidx.appcompat.widget.AppCompatImageView
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark
import com.google.mediapipe.solutions.hands.Hands
import com.google.mediapipe.solutions.hands.HandsResult

class HandsResultImageView(context: Context): AppCompatImageView(context) {
    private val TAG = "HandsResultImageView"

    private val LEFT_HAND_CONNECTION_COLOR = Color.parseColor("#30FF30")
    private val RIGHT_HAND_CONNECTION_COLOR = Color.parseColor("#FF3030")
    private val CONNECTION_THICKNESS = 8 // Pixels

    private val LEFT_HAND_HOLLOW_CIRCLE_COLOR = Color.parseColor("#30FF30")
    private val RIGHT_HAND_HOLLOW_CIRCLE_COLOR = Color.parseColor("#FF3030")
    private val HOLLOW_CIRCLE_WIDTH = 5 // Pixels

    private val LEFT_HAND_LANDMARK_COLOR = Color.parseColor("#FF3030")
    private val RIGHT_HAND_LANDMARK_COLOR = Color.parseColor("#30FF30")
    private val LANDMARK_RADIUS = 10 // Pixels

    private var latest: Bitmap? = null

    init {
        scaleType = ScaleType.FIT_CENTER
    }



    /**
     * Sets a [HandsResult] to render.
     *
     * @param result a [HandsResult] object that contains the solution outputs and the input
     * [Bitmap].
     */
    fun setHandsResult(result: HandsResult?) {
        if (result == null) {
            return
        }
        val bmInput = result.inputBitmap()
        val width = bmInput.width
        val height = bmInput.height
        latest = Bitmap.createBitmap(width, height, bmInput.config)
        val canvas = Canvas(latest!!)
        canvas.drawBitmap(bmInput, Matrix(), null)
        val numHands = result.multiHandLandmarks().size
        for (i in 0 until numHands) {
            drawLandmarksOnCanvas(
                result.multiHandLandmarks()[i].landmarkList,
                result.multiHandedness()[i].label == "Left",
                canvas,
                width,
                height
            )
        }
    }

    /** Updates the image view with the latest [HandsResult].  */
    fun update() {
        postInvalidate()
        if (latest != null) {
            setImageBitmap(latest)
        }
    }

    private fun drawLandmarksOnCanvas(
        handLandmarkList: List<NormalizedLandmark>,
        isLeftHand: Boolean,
        canvas: Canvas,
        width: Int,
        height: Int
    ) {
        // Draw connections.
        for (c in Hands.HAND_CONNECTIONS) {
            val connectionPaint = Paint()
            connectionPaint.color =
                if (isLeftHand) LEFT_HAND_CONNECTION_COLOR else RIGHT_HAND_CONNECTION_COLOR
            connectionPaint.strokeWidth = CONNECTION_THICKNESS.toFloat()
            val start = handLandmarkList[c.start()]
            val end = handLandmarkList[c.end()]
            canvas.drawLine(
                start.x * width,
                start.y * height,
                end.x * width,
                end.y * height,
                connectionPaint
            )
        }
        val landmarkPaint = Paint()
        landmarkPaint.color =
            if (isLeftHand) LEFT_HAND_LANDMARK_COLOR else RIGHT_HAND_LANDMARK_COLOR
        // Draws landmarks.
        for (landmark in handLandmarkList) {
            canvas.drawCircle(
                landmark.x * width, landmark.y * height, LANDMARK_RADIUS.toFloat(), landmarkPaint
            )
        }
        // Draws hollow circles around landmarks.
        landmarkPaint.color =
            if (isLeftHand) LEFT_HAND_HOLLOW_CIRCLE_COLOR else RIGHT_HAND_HOLLOW_CIRCLE_COLOR
        landmarkPaint.strokeWidth = HOLLOW_CIRCLE_WIDTH.toFloat()
        landmarkPaint.style = Paint.Style.STROKE
        for (landmark in handLandmarkList) {
            canvas.drawCircle(
                landmark.x * width,
                landmark.y * height, (
                        LANDMARK_RADIUS + HOLLOW_CIRCLE_WIDTH).toFloat(),
                landmarkPaint
            )
        }
    }
}
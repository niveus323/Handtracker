package com.example.handtracking.engine.mediapipe

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.TextureFrame
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView
import com.google.mediapipe.solutions.hands.HandLandmark
import com.google.mediapipe.solutions.hands.Hands
import com.google.mediapipe.solutions.hands.HandsOptions
import com.google.mediapipe.solutions.hands.HandsResult

class HandsTracker(private var context: Context, private var frameLayout: FrameLayout) {
    private lateinit var hands: Hands
    private var inputSource: InputSource = InputSource.UNKNOWN
    private lateinit var cameraInput: CameraInput
    private lateinit var glSurfaceView: SolutionGlSurfaceView<HandsResult>

    fun onCreate(lifecycleOwner: LifecycleOwner) {
        cameraInput = CameraInput()
        cameraInput.setNewFrameListener { textureFrame: TextureFrame? ->
            hands.send(
                textureFrame
            )
        }
//        stopCurrentPipeline()
        setupStreamingModePipeline(InputSource.CAMERA, lifecycleOwner)
    }

    fun onDestroy() {
        stopCurrentPipeline()
    }

    //Start Camera
    /** Sets up core workflow for streaming mode.  */
    private fun setupStreamingModePipeline(inputSource: InputSource, lifecycleOwner: LifecycleOwner) {
        this.inputSource = inputSource
        // Initializes a new MediaPipe Hands solution instance in the streaming mode.
        hands = Hands(
            context,
            HandsOptions.builder()
                .setStaticImageMode(false)
                .setMaxNumHands(2)
                .setRunOnGpu(RUN_ON_GPU)
                .build()
        )
        hands.setErrorListener { message: String, e: RuntimeException? ->
            Log.e(
                TAG, "MediaPipe Hands error:$message"
            )
        }
        cameraInput = CameraInput()
        cameraInput.setNewFrameListener { textureFrame: TextureFrame? ->
            hands.send(
                textureFrame
            )
        }

        // Initializes a new Gl surface view with a user-defined HandsResultGlRenderer.
        glSurfaceView = SolutionGlSurfaceView(context, hands.glContext, hands.glMajorVersion)
        glSurfaceView.setSolutionResultRenderer(HandsResultGlRenderer())
        glSurfaceView.setRenderInputImage(true)
        hands.setResultListener { handsResult: HandsResult? ->
            if (handsResult != null) {
                logFingerTipLandmark(handsResult,  /*showPixelValues=*/false)
            }
            glSurfaceView.setRenderData(handsResult)
            glSurfaceView.requestRender()
        }

        // The runnable to start camera after the gl surface view is attached.
        // For video input source, videoInput.start() will be called when the video uri is available.
        if (inputSource == InputSource.CAMERA) {
            glSurfaceView.post { this.startCamera(lifecycleOwner) }
        }

        // Updates the preview layout.
//        val frameLayout: FrameLayout = findViewById<FrameLayout>(R.id.preview_display_layout)
        frameLayout.removeAllViewsInLayout()
        frameLayout.addView(glSurfaceView)
        glSurfaceView.visibility = View.VISIBLE
//        glSurfaceView.visibility = View.INVISIBLE
        frameLayout.requestLayout()
        frameLayout.visibility = View.VISIBLE
//        frameLayout.visibility = View.INVISIBLE
    }

    private fun startCamera(lifecycleOwner: LifecycleOwner) {
        cameraInput.start(
            context,
            lifecycleOwner,
            hands.glContext,
            CameraInput.CameraFacing.FRONT,
            glSurfaceView.width,
            glSurfaceView.height
        )
    }

    private fun stopCurrentPipeline() {
        cameraInput.setNewFrameListener(null)
        cameraInput.close()
        glSurfaceView.visibility = View.GONE
        hands.close()
    }

    private fun logFingerTipLandmark(result: HandsResult, showPixelValues: Boolean) {
        if (result.multiHandLandmarks().isEmpty()) {
            return
        }
        val landmark = result.multiHandLandmarks()[0].landmarkList[HandLandmark.INDEX_FINGER_TIP]
        // For Bitmaps, show the pixel values. For texture inputs, show the normalized coordinates.
        if (showPixelValues) {
            val width = result.inputBitmap().width
            val height = result.inputBitmap().height
            Log.i(
                TAG, String.format(
                    "MediaPipe Hand coordinates (pixel values): x=%f, y=%f",
                    landmark.x * width, landmark.y * height
                )
            )
        } else {
            Log.i(
                TAG, String.format(
                    "MediaPipe Hand normalized coordinates (value range: [0, 1]): x=%f, y=%f",
                    landmark.x, landmark.y
                )
            )
        }
        if (result.multiHandWorldLandmarks().isEmpty()) {
            return
        }
        val worldLandmark =
            result.multiHandWorldLandmarks()[0].landmarkList[HandLandmark.INDEX_FINGER_TIP]
        Log.i(
            TAG, String.format(
                "MediaPipe Hand world coordinates (in meters with the origin at the hand's"
                        + " approximate geometric center): x=%f m, y=%f m, z=%f m",
                worldLandmark.x, worldLandmark.y, worldLandmark.z
            )
        )
    }

    companion object {
        private val TAG: String = HandsTracker::class.java.simpleName
        private const val RUN_ON_GPU: Boolean = true
        private enum class InputSource {
            UNKNOWN,
            IMAGE,
            VIDEO,
            CAMERA,
        }
    }

}
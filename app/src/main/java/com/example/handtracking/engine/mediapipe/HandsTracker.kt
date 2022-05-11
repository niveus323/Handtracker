package com.example.handtracking.engine.mediapipe

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import com.example.handtracking.R
import com.example.handtracking.extensions.ScreenMetrics
import com.google.mediapipe.framework.TextureFrame
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView
import com.google.mediapipe.solutions.hands.HandLandmark
import com.google.mediapipe.solutions.hands.Hands
import com.google.mediapipe.solutions.hands.HandsOptions
import com.google.mediapipe.solutions.hands.HandsResult

class HandsTracker(private var context: Context, private var frameLayout: FrameLayout, private var screenMetrics: ScreenMetrics) {
    private lateinit var hands: Hands
    private var inputSource: InputSource = InputSource.UNKNOWN
    private lateinit var cameraInput: CameraInput
    private lateinit var glSurfaceView: SolutionGlSurfaceView<HandsResult>
    private var targetLayout: View? = null
    private val layoutParams: WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        ScreenMetrics.TYPE_COMPAT_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT)
    private val windowManager = context.getSystemService(WindowManager::class.java)

    fun onCreate(lifecycleOwner: LifecycleOwner) {
        createLayout()
        initializeCameraInput()
        setupStreamingModePipeline(InputSource.CAMERA, lifecycleOwner)
    }

    @SuppressLint("InflateParams")
    private fun createLayout() {
        targetLayout = context.getSystemService(LayoutInflater::class.java).inflate(R.layout.target, null) as View
        targetLayout?.bringToFront()
        WindowManager.LayoutParams().apply {
            copyFrom(layoutParams)
        }
        layoutParams.gravity = Gravity.TOP or Gravity.START
        windowManager.addView(targetLayout, layoutParams)
    }

    private fun initializeCameraInput() {
        cameraInput = CameraInput()
        cameraInput.setNewFrameListener { textureFrame: TextureFrame? ->
            hands.send(
                textureFrame
            )
        }
    }

    fun onDestroy() {
        stopCurrentPipeline()
        destroyLayout()
    }

    private fun destroyLayout() {
        windowManager.removeView(targetLayout)
        targetLayout = null
    }

    fun setTargetVisibility(visibility: Int) {
        targetLayout?.visibility = visibility
    }

    private fun setTargetLayoutPosition(handsResult: HandsResult) {
        val displaySize = screenMetrics.screenSize
        val x = handsResult.multiHandLandmarks()[0].landmarkList[HandLandmark.INDEX_FINGER_TIP].x * displaySize.x.toFloat()
        val y = handsResult.multiHandLandmarks()[0].landmarkList[HandLandmark.INDEX_FINGER_TIP].y * displaySize.y.toFloat()
        Log.i(TAG, "Target Position: ($x, $y)")
        layoutParams.x = x.toInt()
        layoutParams.y = y.toInt()
        targetLayout?.post {
            windowManager.updateViewLayout(targetLayout, layoutParams)
        }
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
            if (handsResult != null && !handsResult.multiHandLandmarks().isEmpty()) {
//                logFingerTipLandmark(handsResult,  /*showPixelValues=*/false)
                setTargetLayoutPosition(handsResult)
            }
//            glSurfaceView.setRenderData(handsResult)
//            glSurfaceView.requestRender()
        }

        // The runnable to start camera after the gl surface view is attached.
        // For video input source, videoInput.start() will be called when the video uri is available.
        if (inputSource == InputSource.CAMERA) {
            glSurfaceView.post { this.startCamera(lifecycleOwner) }
        }

        // Updates the preview layout.
        frameLayout.removeAllViewsInLayout()
        frameLayout.addView(glSurfaceView)
        glSurfaceView.visibility = View.INVISIBLE
        frameLayout.requestLayout()
        frameLayout.visibility = View.INVISIBLE
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
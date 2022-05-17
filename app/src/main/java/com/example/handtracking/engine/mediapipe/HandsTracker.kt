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

class HandsTracker(private var context: Context, private var lifecycleOwner: LifecycleOwner) {
    private lateinit var hands: Hands
    private var inputSource: InputSource = InputSource.UNKNOWN
    private lateinit var cameraInput: CameraInput
    private lateinit var glSurfaceView: SolutionGlSurfaceView<HandsResult>
    var handResultListener: HandResultListener? = null


    fun onCreate(frameLayout: FrameLayout) {
        initializeCameraInput()
        setupStreamingModePipeline(InputSource.CAMERA, frameLayout)
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
    }

    //Start Camera
    /** Sets up core workflow for streaming mode.  */
    private fun setupStreamingModePipeline(inputSource: InputSource, frameLayout: FrameLayout) {
        this.inputSource = inputSource
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

        glSurfaceView = SolutionGlSurfaceView(context, hands.glContext, hands.glMajorVersion)
        glSurfaceView.setSolutionResultRenderer(HandsResultGlRenderer())
        glSurfaceView.setRenderInputImage(true)
        hands.setResultListener { handsResult: HandsResult? ->
            if (handsResult != null && !handsResult.multiHandLandmarks().isEmpty()) {
                handResultListener?.onHandResultDetected(
                    handsResult.multiHandLandmarks()[0].landmarkList[HandLandmark.INDEX_FINGER_TIP].x,
                    handsResult.multiHandLandmarks()[0].landmarkList[HandLandmark.INDEX_FINGER_TIP].y
                )
            }
            glSurfaceView.setRenderData(handsResult)
            glSurfaceView.requestRender()
        }

        if (inputSource == InputSource.CAMERA) {
            glSurfaceView.post { this.startCamera(lifecycleOwner) }
        }

        frameLayout.removeAllViewsInLayout()
        frameLayout.addView(glSurfaceView)
        glSurfaceView.visibility = View.VISIBLE
        frameLayout.requestLayout()
        frameLayout.visibility = View.VISIBLE
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

    interface HandResultListener {
        fun onHandResultDetected(x: Float, y: Float)
    }

}


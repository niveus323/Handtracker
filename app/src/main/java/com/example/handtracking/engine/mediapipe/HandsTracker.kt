package com.example.handtracking.engine.mediapipe

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.TextureFrame
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView
import com.google.mediapipe.solutions.hands.Hands
import com.google.mediapipe.solutions.hands.HandsOptions
import com.google.mediapipe.solutions.hands.HandsResult
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class HandsTracker(private var context: Context, private var lifecycleOwner: LifecycleOwner) {
    private lateinit var hands: Hands
    private var inputSource: InputSource = InputSource.UNKNOWN
    private lateinit var cameraInput: CameraInput
    private lateinit var glSurfaceView: SolutionGlSurfaceView<HandsResult>
    private var processingScope: CoroutineScope? = null
    private var processingJob: Job? = null

    var handResultListener: HandResultListener? = null

    interface HandResultListener {
        fun onHandResultDetected(handsResult: HandsResult)
    }

    fun onCreate(frameLayout: FrameLayout) {
        initializeCameraInput()
        setupStreamingModePipeline(InputSource.CAMERA, frameLayout)
        processingScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))
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
                handResultListener?.onHandResultDetected(handsResult)
//                coroutine(handsResult)
            }
//            glSurfaceView.setRenderData(handsResult)
//            glSurfaceView.requestRender()
        }

        if (inputSource == InputSource.CAMERA) {
            glSurfaceView.post { this.startCamera(lifecycleOwner) }
        }

        frameLayout.removeAllViewsInLayout()
        frameLayout.addView(glSurfaceView)
        glSurfaceView.visibility = View.INVISIBLE
        frameLayout.requestLayout()
        frameLayout.visibility = View.INVISIBLE
    }

    private fun coroutine(handsResult: HandsResult){
        CoroutineScope(Dispatchers.Main).launch {
            withContext(CoroutineScope(Dispatchers.Default).coroutineContext) {
                val url = "http://www.icehandtracking.com/gesture"
                val okHttpClient = OkHttpClient()
                val json = JSONObject()
                var result = ""
                    handsResult.multiHandLandmarks()[0].landmarkList.forEach {
                    result += "${it.x} ${it.y} ${it.z}\n"
                }
                json.put("result", result )
                val body = json.toString().toRequestBody()
                val request = Request.Builder().url(url).post(body).build()
                val response = okHttpClient.newCall(request).execute()
                if(response.isSuccessful) {
                    Log.i("HTML", response.toString())
                }else{
                    Log.w("HTML", "Response Failed")
                }
            }
        }
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
        glSurfaceView.post{
            glSurfaceView.visibility = View.GONE
        }
        if(handResultListener != null) handResultListener = null
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

}


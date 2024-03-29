package com.example.handtracking.engine

import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.handtracking.domain.Action
import com.example.handtracking.extensions.ScreenMetrics
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalCoroutinesApi::class)
class GestureEngine(context: Context) {

    companion object {
        private const val TAG = "GestureEngine"
        @Volatile
        private var INSTANCE: GestureEngine? = null

        fun getGestureEngine(context: Context): GestureEngine {
            return INSTANCE ?: synchronized(this) {
                Log.i(TAG, "Instantiates new Gesture Engine")
                val instance = GestureEngine(context)
                INSTANCE = instance
                instance
            }
        }

        private fun cleanInstance() {
            synchronized(this) {
                INSTANCE = null
            }
        }
    }

    private val screenMetrics = ScreenMetrics(context)
    private val screenRecorder = ScreenRecorder()
    private var gestureExecutor: ((GestureDescription) -> Unit)? = null
    private var processingScope: CoroutineScope? = null
    private var processingJob: Job? = null

    private var isScreenRecording = MutableStateFlow(false)
//    private var actions: MutableList<Action>? = mutableListOf() //초기화에 넣어주기
    private var actionExecutor: ActionExecutor? = null



    fun start(
        context: Context,
        resultCode: Int,
        data: Intent,
        gestureExecutor: (GestureDescription) -> Unit
        ) {
        this.gestureExecutor = gestureExecutor
        this.actionExecutor = ActionExecutor(gestureExecutor)
        screenMetrics.registerOrientationListener(::onOrientationChanged)
        processingScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))
        screenRecorder.apply {
            startProjection(context, resultCode, data) {
                this@GestureEngine.stop()
            }

            processingScope?.launch {
                startScreenRecord(context, screenMetrics.screenSize)
                isScreenRecording.emit(true)
            }
        }
    }

    fun processTap(pos: Array<Float>) {
        process(mutableListOf(Action.Tap(pos[0], pos[1])))
    }

    fun processSlide(from: Array<Float>, to: Array<Float>) {
        process(mutableListOf(Action.Slide(from[0] ,from[1], to[0], to[1])))
    }

    fun processDrag(pos: Array<Float>) {
        process(mutableListOf(Action.Drag(pos[0] ,pos[1])))
    }

    fun terminateDrag(pos: Array<Float>) {
        processingJob = processingScope?.launch {
            actionExecutor?.terminateDrag(Action.Drag(pos[0], pos[1]))
        }
    }

    fun processZoom(pos1: Array<Float>, pos2: Array<Float>) {
        process(mutableListOf(Action.Zoom(pos1[0], pos1[1], pos2[0], pos2[1])))
    }

    fun terminateZoom() {
        actionExecutor?.terminateZoom()
    }

    fun processZoomIn() {
        process(mutableListOf(
            Action.Zoom(300F,300F, 500F,500F),
            Action.Zoom(250F,250F, 500F,500F)
        ))
        actionExecutor?.terminateZoom()
    }

    fun processZoomOut() {
        process(mutableListOf(
            Action.Zoom(250F,250F, 500F,500F),
            Action.Zoom(300F,300F, 500F,500F)
        ))
        actionExecutor?.terminateZoom()
    }

    private fun process(actions: MutableList<Action>) {
        processingJob = processingScope?.launch {
            actions.let { actions ->
                actionExecutor?.executeActions(actions)
            }
        }
    }

    fun stop() {
        screenMetrics.unregisterOrientationListener()
        processingScope?.launch {
            screenRecorder.stopProjection()
            isScreenRecording.emit(false)
            Log.i(TAG, "stop")
            processingJob?.cancelAndJoin()
            processingJob = null
        }
    }

    private fun onOrientationChanged(context: Context) {
        processingScope?.launch {
            processingJob?.cancelAndJoin()

            screenRecorder.stopScreenRecord()
            screenRecorder.startScreenRecord(context, screenMetrics.screenSize)

        }
    }

//    private suspend fun processLatestImage() {
//        screenRecorder.acquireLatestImage()?.use { image ->
//
//        }
//    }

    fun stopScreenRecord() {
        if(!isScreenRecording.value) {
            return
        }

        screenMetrics.unregisterOrientationListener()
        processingScope?.launch {
            screenRecorder.stopProjection()
            isScreenRecording.emit(false)
            processingScope?.cancel()
            processingScope = null
        }
    }

    fun clear() {
        if (isScreenRecording.value) {
            stopScreenRecord()
        }

        gestureExecutor = null
        actionExecutor = null
        cleanInstance()
    }
}
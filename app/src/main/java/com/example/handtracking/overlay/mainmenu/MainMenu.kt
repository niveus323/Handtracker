package com.example.handtracking.overlay.mainmenu

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import com.example.handtracking.R
import com.example.handtracking.activity.MainActivity
import com.example.handtracking.engine.Camera
import com.example.handtracking.engine.GestureRecognizer
import com.example.handtracking.engine.GestureRecognizer.Gesture.*
import com.example.handtracking.engine.mediapipe.HandsTracker
import com.example.handtracking.overlay.OverlayMenuController
import com.google.mediapipe.solutions.hands.HandLandmark
import com.google.mediapipe.solutions.hands.HandsResult
import java.util.*
import kotlin.concurrent.timer
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class MainMenu(context: Context) : OverlayMenuController(context), HandsTracker.HandResultListener {
    private var viewModel: MainMenuModel? = MainMenuModel(context).apply {
        attachToLifecycle(this@MainMenu)
    }

    private lateinit var camera: Camera
    private lateinit var handsTracker: HandsTracker
    private lateinit var gestureRecognizer: GestureRecognizer
    private lateinit var cardView: View
    private lateinit var feedbackView: View
    private lateinit var btnViews: Array<View>
    private lateinit var textView: TextView
    private var gestureState: GestureRecognizer.Gesture = NONE
    private var feedbackState: FeedbackState = FeedbackState.UI_GONE
    private var isTimerStarted = false
    private var time:Int = 0
    private var timer: Timer? = null
    private lateinit var firstTarget: View

    override fun onCreateMenu(layoutInflater: LayoutInflater): ViewGroup = layoutInflater.inflate(R.layout.overlay_menu, null) as ViewGroup
    override fun onCreateTarget(layoutInflater: LayoutInflater): ViewGroup = layoutInflater.inflate(R.layout.target, null) as ViewGroup
    override fun onCreateOverlayViewLayoutParams(): WindowManager.LayoutParams = super.onCreateOverlayViewLayoutParams().apply {
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
    }

    override fun onCreate() {
        super.onCreate()
        cardView = getMenuItemView(R.id.cardView)!!
        feedbackView = getMenuItemView(R.id.btn_feedback)!!
        handsTracker = HandsTracker(context, this)
        handsTracker.onCreate(getMenuItemView(R.id.frameLayout)!!)
        handsTracker.handResultListener = this
        cursorItem = getTargetItemView(R.id.cursor)!!
        doubleCursorItems = arrayOf(getTargetItemView(R.id.cursorFinger)!!, getTargetItemView(R.id.cursorThumb)!!)
        btnViews = arrayOf(getMenuItemView(R.id.btn_tap)!!
            ,getMenuItemView(R.id.btn_slide)!!,
            getMenuItemView(R.id.btn_drag)!!,
            getMenuItemView(R.id.btn_dismiss)!!,
            getMenuItemView(R.id.btn_move)!!)
//        camera = Camera(context, getMenuItemView(R.id.surfaceView)!!)
//        camera.initialize()
        gestureRecognizer = GestureRecognizer(context.assets, context.filesDir)
        textView = getTargetItemView(R.id.gestureResult)!!
        firstTarget = getTargetItemView<View>(R.id.firstTarget)!!
    }

    override fun onDismissed() {
        super.onDismissed()
        viewModel = null
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
        }
        context.startActivity(intent)
    }

    override fun onStop() {
        handsTracker.onDestroy()
        super.onStop()
//        camera.destroy()
    }

    private var isRecording : Boolean = false

    private fun calculatePosition(handsResult: HandsResult, landmark: Int): Array<Float> {
        val displaySize = screenMetrics.screenSize
        // 관절 0-9 거리(민감도에 활용)
        val zeroToNine = sqrt((handsResult.multiHandLandmarks()[0].landmarkList[HandLandmark.WRIST].x
                - handsResult.multiHandLandmarks()[0].landmarkList[landmark].x).pow(2)
                + (handsResult.multiHandLandmarks()[0].landmarkList[HandLandmark.WRIST].y
                - handsResult.multiHandLandmarks()[0].landmarkList[landmark].y).pow(2))
        // 민감도(거리가 멀어질수록 증가)
        var sensitivity = 1 / (zeroToNine * 2)
        if (sensitivity < 2) sensitivity = 2F

        var normalx: Float
        var normaly: Float
        if(screenMetrics.orientation == Configuration.ORIENTATION_PORTRAIT) {
            normalx = handsResult.multiHandLandmarks()[0].landmarkList[landmark].x
            normaly = handsResult.multiHandLandmarks()[0].landmarkList[landmark].y
        }else{
            normalx = handsResult.multiHandLandmarks()[0].landmarkList[landmark].y
            normaly = 1-handsResult.multiHandLandmarks()[0].landmarkList[landmark].x
        }

        if (normalx > 1) normalx = 1F
        if (normalx < 0) normalx = 0F
        if (normaly > 1) normaly = 1F
        if (normaly < 0) normaly = 0F
        // 민감도에 따른 커서 조작 영역 설정
        var pixelx = normalx * displaySize.x.toFloat()
        var pixely = normaly * displaySize.y.toFloat()
        val upperboundaryx = displaySize.x.toFloat() * (1 + (1 / sensitivity)) / 2
        val upperboundaryy = displaySize.y.toFloat() * (1 + (1 / sensitivity)) / 2
        val lowerboundaryx = displaySize.x.toFloat() * (1 - (1 / sensitivity)) / 2
        val lowerboundaryy = displaySize.y.toFloat() * (1 - (1 / sensitivity)) / 2

        if (pixelx > upperboundaryx) pixelx = upperboundaryx
        if (pixelx < lowerboundaryx) pixelx = lowerboundaryx
        if (pixely > upperboundaryy) pixely = upperboundaryy
        if (pixely < lowerboundaryy) pixely = lowerboundaryy
        // 커서 조작 영역에 따른 커서 좌표 설정
        var x = (pixelx - lowerboundaryx) * sensitivity
        var y = (pixely - lowerboundaryy) * sensitivity
        // 커서 Boundary 지정
        val boundaryx: Double
        val boundaryy: Double
        if(screenMetrics.orientation == Configuration.ORIENTATION_PORTRAIT) {
            boundaryx = displaySize.x.toFloat() * 0.9
            boundaryy = displaySize.y.toFloat() * 0.96
        }else{
            boundaryx = displaySize.x.toFloat() * 0.9
            boundaryy = displaySize.y.toFloat() * 0.96
        }

        if (x > boundaryx) x = boundaryx.toFloat()
        if (x < 0) x = 0F
        if (y > boundaryy) y = boundaryy.toFloat()
        if (y < 0) y = 0F

        return arrayOf(x, y)
    }

    private var isGestureRecognized = false
    private var isTimeToCheck = false
    /**
     * Called when HandsTracker Result Detected.
     * Handle x,y position of finger tip to move [targetLayout]
     * convert HandsResult position to Screen position
     * then, call super with param [screenX], [screenY]
     *
     * @param  position of finger tip detected by [HandsTracker]
     *
     */
    override fun onHandResultDetected(handsResult: HandsResult) {
        updateScreenMetrics()
        val position = calculatePosition(handsResult, HandLandmark.MIDDLE_FINGER_MCP)
        Log.i(TAG, position.contentToString())
        setCursorPosition(position, cursorItem)
        val fingerTipPos = calculatePosition(handsResult, HandLandmark.INDEX_FINGER_TIP)
        val thumbTipPos = calculatePosition(handsResult, HandLandmark.THUMB_TIP)
        setCursorPosition(fingerTipPos, doubleCursorItems[0])
        setCursorPosition(thumbTipPos, doubleCursorItems[1])
        when(gestureState){
            SLIDE -> {
                val result = gestureRecognizer.recognizeGesture(handsResult)
                if(abs(cursorPosition[0]-initialTargetPosition[0])>200F
                    || abs(cursorPosition[1]-initialTargetPosition[1])>200F){
                    viewModel!!.playSlide(arrayOf(initialTargetPosition[0], initialTargetPosition[1]),cursorPosition)
                    if(isTimeToCheck && result != SLIDE){
                        terminateSlide()
                    }
                }
            }
            DRAG -> {
                val result = gestureRecognizer.recognizeGesture(handsResult)
                timerOnTargetNotMoved({
                    viewModel!!.playDrag(cursorPosition)
                }) {
                    if(result != DRAG) {
                        terminateDrag()
                    }
                }
            }
            else -> {
                checkFeedbackState(handsResult)
            }
        }
    }

    private fun terminateSlide() {
        setResultText("SLIDE_END", textView)
        firstTarget.post {
            firstTarget.visibility = View.GONE
        }
        feedbackState = FeedbackState.UI_VISIBLE
        gestureState = NONE
        isGestureRecognized = false
        gestureRecognizer.endRecognition()
    }

    private fun terminateDrag() {
        setResultText("DRAG_END", textView)
        viewModel?.terminateDrag(cursorPosition)
        feedbackState = FeedbackState.UI_VISIBLE
        gestureState = NONE
        isGestureRecognized = false
        gestureRecognizer.endRecognition()
    }

    //피드백 사용하지 않는 상태에서 움직이지 않을 경우
    //타이머가 돌아가는동안 손관절 좌표를 넣어서 각도 데이터 저장
    //타이머가 끝나면 저장한 시퀀스 값으로 제스처 예측, 예측 결과에 맞는 행동 수행
    //예측이 끝나면 시퀀스 데이터는 별도 공간에 저장, 다음 제스처 예측이 실행되거나 피드백 상태 변경에 의한 행동이 수행될 경우 데이터를 저장
    //동작 수행 중에는 feedback 버튼이 눌리지 않았으면 함. -> feedback버튼이 눌릴 수 있는 상황과 아닌 상황을 구별해야함.
    //눌리는게 가능하다고 생각하는 케이스 : 슬라이드, 드래그, 줌인/줌아웃과 같은 ON/OFF가 이루어지는 상황

    private fun checkFeedbackState(handsResult: HandsResult) {
        if(feedbackState < FeedbackState.UI_GONE && !isCursorIntersected(feedbackView) && !isCursorIntersected(cardView)){
            cardView.post{
                cardView.visibility = View.GONE
            }
            feedbackState = FeedbackState.UI_GONE
            return
        }

        when(feedbackState){
            FeedbackState.UI_GONE -> {
                if(isCursorIntersected(feedbackView)){
                    stopTimer()
                    cardView.post{
                        cardView.visibility = View.VISIBLE
                    }
                    feedbackState = FeedbackState.UI_VISIBLE
                }else{
                    //타이머동안 인식되면 동작 수행하고 타이머 끝날때까지는 인식수행을 막는 방법.
                    timerOnTargetNotMoved ({
                        //움직이면 초기화
                        gestureRecognizer.endRecognition()
                    },{
                        //움직이지 않았는데 타이머 끝나면 데이터를 저장. 피드백 동작을 수행하면 이때 만들어진 데이터로 학습시킨다.
                        gestureRecognizer.endRecognition()
                        setResultText("", textView)
                        isGestureRecognized = false
                    })
                    if(isTimerStarted) {
                        val result = gestureRecognizer.recognizeGesture(handsResult)
                        if(result != NONE)  setResultText(result.name, textView)
                        if(!isGestureRecognized){
                            isGestureRecognized = true
                            //동작수행
                            when(result) {
                                TAP -> {
                                    viewModel!!.playTap(cursorPosition)
                                    gestureRecognizer.endRecognition()
                                }
                                SLIDE -> {
                                    firstTarget.post {
                                        firstTarget.visibility = View.VISIBLE
                                        firstTarget.x = cursorPosition[0]
                                        firstTarget.y = cursorPosition[1]
                                    }
                                    gestureState = SLIDE
                                    gestureRecognizer.endRecognition()
                                    initialTargetPosition = cursorPosition.copyOf()
                                    isTimeToCheck = false
                                    timer(initialDelay = 1000,period = 1000) {
                                        isTimeToCheck = true
                                        this.cancel()
                                    }
                                }
                                DRAG -> {
                                    viewModel!!.playDrag(cursorPosition)
                                    gestureState = DRAG
                                    gestureRecognizer.endRecognition()
                                    initialTargetPosition = cursorPosition.copyOf()
                                }
                                ZOOM_IN -> {
                                    viewModel?.playZoomIn()
                                    gestureRecognizer.endRecognition()
                                }
                                ZOOM_OUT -> {
                                    viewModel?.playZoomOut()
                                    gestureRecognizer.endRecognition()
                                }
                                VOLUME_UP -> {
                                    viewModel?.turnUpVolume()
                                    gestureRecognizer.endRecognition()
                                }
                                VOLUME_DOWN -> {
                                    viewModel?.turnDownVolume()
                                    gestureRecognizer.endRecognition()
                                }
                                else -> {
                                    isGestureRecognized = false
                                }
                            }
                        }
                    }

                }
            }
            FeedbackState.UI_VISIBLE -> {
                isTimerStarted = false
                if(isCursorIntersected(btnViews[FeedbackState.UI_TAP.ordinal])) feedbackState = FeedbackState.UI_TAP
                else if(isCursorIntersected(btnViews[FeedbackState.UI_SLIDE.ordinal])) feedbackState = FeedbackState.UI_SLIDE
                else if(isCursorIntersected(btnViews[FeedbackState.UI_DRAG.ordinal])) feedbackState = FeedbackState.UI_DRAG
                else if(isCursorIntersected(btnViews[FeedbackState.UI_DISMISS.ordinal])) feedbackState = FeedbackState.UI_DISMISS
                else if(isCursorIntersected(btnViews[FeedbackState.UI_MOVE.ordinal])) feedbackState = FeedbackState.UI_MOVE
            }
            FeedbackState.TAP -> {
                timerOnTargetNotMoved {
                    viewModel?.playTap(cursorPosition)
                    feedbackState = FeedbackState.UI_VISIBLE
                    gestureRecognizer.apply {
                        writeData(TAP)
                    }
                }
            }
            FeedbackState.SLIDE_1 -> {
                timerOnTargetNotMoved {
                    cursorItem = getTargetItemView(R.id.cursor)!!
                    cursorItem.post{
                        cursorItem.visibility = View.VISIBLE
                    }
                    feedbackState = FeedbackState.SLIDE_2
                }
            }
            FeedbackState.SLIDE_2 -> {
                timerOnTargetNotMoved({
                        viewModel!!.playSlide(arrayOf(firstTarget.x, firstTarget.y),cursorPosition)
                    }, {
                    terminateSlide()
                })
            }
            FeedbackState.DRAG_ON -> {
                timerOnTargetNotMoved {
                    viewModel?.playDrag(initialTargetPosition)
                    feedbackState = FeedbackState.DRAG
                }
            }
            FeedbackState.DRAG -> {
                timerOnTargetNotMoved({
                    viewModel!!.playDrag(cursorPosition)
                }) {
                    terminateDrag()
                }
            }
            FeedbackState.MOVE -> {
                timerOnTargetNotMoved{
                    feedbackState = FeedbackState.UI_VISIBLE
                }
                moveMenuLayout()
            }
            FeedbackState.UI_TAP -> {
                timerWithCollisionCheck(btnViews[feedbackState.ordinal]) {
                    initialTargetPosition = cursorPosition.copyOf()
                    feedbackState = FeedbackState.TAP
                }
            }
            FeedbackState.UI_SLIDE -> {
                timerWithCollisionCheck(btnViews[feedbackState.ordinal]) {
                    initialTargetPosition = cursorPosition.copyOf()
                    feedbackState = FeedbackState.SLIDE_1
                    cursorItem.post {
                        cursorItem.visibility = View.GONE
                        cursorItem = getTargetItemView(R.id.firstTarget)!!
                        cursorItem.visibility = View.VISIBLE
                    }
                }
            }
            FeedbackState.UI_DRAG -> {
                timerWithCollisionCheck(btnViews[feedbackState.ordinal]) {
                    initialTargetPosition = cursorPosition.copyOf()
                    feedbackState = FeedbackState.DRAG_ON
                }
            }
            FeedbackState.UI_DISMISS -> {
                timerWithCollisionCheck(btnViews[feedbackState.ordinal]) {
                    Handler(Looper.getMainLooper()).post{
                        dismiss()
                    }
                }
            }
            FeedbackState.UI_MOVE -> {
                timerWithCollisionCheck(btnViews[feedbackState.ordinal]) {
                    setDelta(btnViews[feedbackState.ordinal], feedbackView)
                    initialTargetPosition = INITIAL_POS.copyOf()
                    feedbackState = FeedbackState.MOVE
                }
            }
        }
    }

    private var initialTargetPosition: Array<Float> = arrayOf(-1F, -1F)
    private fun timerOnTargetNotMoved(onTimerEnd: () -> Unit) {
        if(initialTargetPosition.contentEquals(INITIAL_POS)
            || abs(cursorPosition[0]-initialTargetPosition[0])>100F
            || abs(cursorPosition[1]-initialTargetPosition[1])>100F){
            initialTargetPosition = cursorPosition.copyOf()
            stopTimer()
        }else{
            startTimer(onTimerEnd)
        }
    }

    private fun timerOnTargetNotMoved(operationOnMoving: () -> Unit, onTimerEnd: () -> Unit) {
        if(abs(cursorPosition[0]-initialTargetPosition[0])>125F
            || abs(cursorPosition[1]-initialTargetPosition[1])>125F){
            operationOnMoving()
            initialTargetPosition = cursorPosition.copyOf()
            stopTimer()
        }else{
            startTimer(onTimerEnd)
        }
    }

    private fun startTimer(onTimerEnd: () -> Unit) {
        if(isTimerStarted) return
        isTimerStarted = true
        timer = timer(period = 50) {
            time++
            cursorItem.progress = time
            if(time >= 30){
                onTimerEnd()
                stopTimer()
            }
        }
    }

    private fun stopTimer() {
        timer?.cancel()
        cursorItem.progress = 0
        time = 0
        isTimerStarted = false
    }

    private fun timerWithCollisionCheck(view: View, onTimerEnd: () -> Unit) {
        if(isCursorIntersected(view))
            startTimer(onTimerEnd)
        else
            onCollisionNotDetected()
    }

    private fun onCollisionNotDetected() {
        stopTimer()
        feedbackState = FeedbackState.UI_VISIBLE
    }

    /** State of Feedback.*/
    private enum class FeedbackState {
        UI_TAP, UI_SLIDE, UI_DRAG, UI_DISMISS, UI_MOVE, UI_VISIBLE, UI_GONE, TAP, SLIDE_1, SLIDE_2, DRAG_ON, DRAG, MOVE
    }

    private val INITIAL_POS = arrayOf(-1F,-1F)
    private var TAG = "MainMenu"
}
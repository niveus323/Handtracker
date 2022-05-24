package com.example.handtracking.overlay.mainmenu

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.example.handtracking.R
import com.example.handtracking.activity.MainActivity
import com.example.handtracking.engine.Camera
import com.example.handtracking.engine.mediapipe.HandsTracker
import com.example.handtracking.overlay.OverlayMenuController
import java.util.*
import kotlin.concurrent.timer
import kotlin.math.abs

class MainMenu(context: Context) : OverlayMenuController(context){
    private var viewModel: MainMenuModel? = MainMenuModel(context).apply {
        attachToLifecycle(this@MainMenu)
    }

    private lateinit var camera: Camera
    private lateinit var handsTracker: HandsTracker
    private lateinit var cardView: View
    private lateinit var feedbackView: View
    private lateinit var btnViews: Array<View>
    private var feedbackState: FeedbackState = FeedbackState.UI_GONE
    private var isTimerStarted = false
    private var time:Int = 0
    private var timer: Timer?= null

    override fun onCreateMenu(layoutInflater: LayoutInflater): ViewGroup = layoutInflater.inflate(R.layout.overlay_menu, null) as ViewGroup
    override fun onCreateTarget(layoutInflater: LayoutInflater): ViewGroup = layoutInflater.inflate(R.layout.target, null) as ViewGroup
    override fun onCreateOverlayViewLayoutParams(): WindowManager.LayoutParams = super.onCreateOverlayViewLayoutParams().apply {
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    }

    override fun onCreate() {
        super.onCreate()
        cardView = getMenuItemView(R.id.cardView)!!
        feedbackView = getMenuItemView(R.id.btn_feedback)!!
        handsTracker = HandsTracker(context, this)
        handsTracker.onCreate(getMenuItemView(R.id.frameLayout)!!)
        handsTracker.handResultListener = this
        cursorItem = getTargetItemView(R.id.cursorImage)!!
        btnViews = arrayOf(getMenuItemView(R.id.btn_tap)!!
            ,getMenuItemView(R.id.btn_slide)!!,
            getMenuItemView(R.id.btn_drag)!!,
            getMenuItemView(R.id.btn_dismiss)!!,
            getMenuItemView(R.id.btn_move)!!)
//        camera = Camera(context, getMenuItemView(R.id.surfaceView)!!)
//        camera.initialize()
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

    /**
     * Called when HandsTracker Result Detected.
     * Handle x,y position of finger tip to move [targetLayout]
     * convert HandsResult position to Screen position
     * then, call super with param [screenX], [screenY]
     *
     * @param x, y position of finger tip detected by [HandsTracker]
     *
     */
    override fun onHandResultDetected(x: Float, y: Float) {
        val displaySize = screenMetrics.screenSize
        val screenX = x * displaySize.x.toFloat()
        val screenY = y * displaySize.y.toFloat()

        super.onHandResultDetected(screenX, screenY)

        /** UI_TAP, UI_SLIDE, UI_DRAG, UI_DISMISS, UI_MOVE, UI_VISIBLE 상태일때 [feedbackView], [cardView]의 충돌을 확인. */
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
                    cardView.post{
                        cardView.visibility = View.VISIBLE
                    }
                    feedbackState = FeedbackState.UI_VISIBLE
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
                    viewModel?.playTap(initialTargetPosition)
                    feedbackState = FeedbackState.UI_VISIBLE
                }
            }
            FeedbackState.SLIDE_1 -> {
                timerOnTargetNotMoved {
                    cursorItem = getTargetItemView(R.id.cursorImage)!!
                    cursorItem.post{
                        cursorItem.visibility = View.VISIBLE
                    }
                    feedbackState = FeedbackState.SLIDE_2
                }
            }
            FeedbackState.SLIDE_2 -> {
                val firstTarget = getTargetItemView<View>(R.id.firstTarget)!!
                timerOnTargetNotMoved({
                        viewModel!!.playSlide(arrayOf(firstTarget.x, firstTarget.y),cursorPosition)
                    }, {
                    firstTarget.post {
                        firstTarget.visibility = View.GONE
                    }
                    feedbackState = FeedbackState.UI_VISIBLE
                })
            }
            /** Drag는 첫 위치를 지정하고, 그 다음에는 슬라이드처럼 이동. */
            FeedbackState.DRAG_ON -> {
                timerOnTargetNotMoved {
                    viewModel?.playDrag(initialTargetPosition)
                    feedbackState = FeedbackState.DRAG_OFF
                }
            }
            FeedbackState.DRAG_OFF -> {
                timerOnTargetNotMoved({
                    viewModel!!.playDrag(cursorPosition)
                }) {
                    viewModel?.terminateDrag(cursorPosition)
                    feedbackState = FeedbackState.UI_VISIBLE
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
            || abs(cursorPosition[0]-initialTargetPosition[0])>40F
            || abs(cursorPosition[1]-initialTargetPosition[1])>40F){
            initialTargetPosition = cursorPosition.copyOf()
            stopTimer()
        }else{
            startTimer(onTimerEnd)
        }
    }

    private fun timerOnTargetNotMoved(operation: () -> Unit, onTimerEnd: () -> Unit) {
        if(abs(cursorPosition[0]-initialTargetPosition[0])>100F
            || abs(cursorPosition[1]-initialTargetPosition[1])>100F){
            operation()
            initialTargetPosition = cursorPosition.copyOf()
            stopTimer()
        }else{
            startTimer(onTimerEnd)
        }
    }

    private fun startTimer(onTimerEnd: () -> Unit) {
        if(isTimerStarted) return
        timer = timer(period = 50) {
            time++
            cursorItem.progress = time
            if(time >= 30){
                onTimerEnd()
                stopTimer()
            }
        }
        isTimerStarted = true
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
        UI_TAP, UI_SLIDE, UI_DRAG, UI_DISMISS, UI_MOVE, UI_VISIBLE, UI_GONE, TAP, SLIDE_1, SLIDE_2, DRAG_ON, DRAG_OFF, MOVE
    }

    private val INITIAL_POS = arrayOf(-1F,-1F)
    private var TAG = "MainMenu"
}
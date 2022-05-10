package com.example.handtracking.overlay.mainmenu

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.example.handtracking.R
import com.example.handtracking.activity.MainActivity
import com.example.handtracking.engine.Camera
import com.example.handtracking.engine.mediapipe.HandsTracker
import com.example.handtracking.overlay.OverlayMenuController
import java.io.File

class MainMenu(context: Context) : OverlayMenuController(context){
    private var viewModel: MainMenuModel? = MainMenuModel(context).apply {
        attachToLifecycle(this@MainMenu)
    }

    private lateinit var camera: Camera
    private lateinit var outputFolder : File
    private lateinit var handsTracker: HandsTracker

    override fun onCreateMenu(layoutInflater: LayoutInflater): ViewGroup = layoutInflater.inflate(R.layout.overlay_menu, null) as ViewGroup
    override fun onCreateOverlayViewLayoutParams(): WindowManager.LayoutParams = super.onCreateOverlayViewLayoutParams().apply {
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    }

    override fun onCreate() {
        super.onCreate()
        setOverlayViewVisibility(View.GONE)
        val outputPath = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS+File.separator+"Handtracking")
        outputPath?.mkdirs()
        outputFolder = File("$outputPath")
//        camera = Camera(context, getMenuItemView(R.id.surfaceView)!!)
//        camera.initialize()
        handsTracker = HandsTracker(context, getMenuItemView(R.id.frameLayout)!!)
        handsTracker.onCreate(this)
    }

    override fun onStart() {
        super.onStart()

//        getMenuItemView<View>(R.id.btn_play)?.setOnLongClickListener {
//            viewModel?.playTap()
//            true
//        }
//
//        getMenuItemView<View>(R.id.btn_play2)?.setOnLongClickListener {
//            viewModel?.playZoom()
//            true
//        }
    }


    override fun onDismissed() {
        super.onDismissed()
        handsTracker.onDestroy()
        viewModel = null

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
        }
        context.startActivity(intent)
    }

    override fun onStop() {
        super.onStop()
//        camera.destroy()
    }

    private var isRecording : Boolean = false

    override fun onMenuItemClicked(viewId: Int) {
        when (viewId) {
            R.id.btn_play -> viewModel?.playTap()
            R.id.btn_play2 -> viewModel?.playZoom()
            R.id.btn_stop -> dismiss()
            R.id.btn_record -> {
//                if(isRecording) {
//                    val intent = camera.stopRecording()
////                    setMenuVisibility(View.GONE)
//                    context.startActivity(intent)
//                }
//                else camera.startRecording()
//                isRecording = !isRecording
            }
        }
    }


    private var TAG = "MainMenu"
}
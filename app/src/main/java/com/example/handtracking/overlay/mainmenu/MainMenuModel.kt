package com.example.handtracking.overlay.mainmenu

import android.content.Context
import com.example.handtracking.engine.GestureEngine
import com.example.handtracking.overlay.OverlayViewModel
import kotlinx.coroutines.delay

class MainMenuModel(context: Context) : OverlayViewModel(context) {

    private var gestureEngine: GestureEngine = GestureEngine.getGestureEngine(context)

    fun playTap() {
        gestureEngine.apply {
            processTap()
        }
    }

    fun playZoom() {
        gestureEngine.apply {
            processZoomIn()
//            processZoomOut()
        }
    }
}
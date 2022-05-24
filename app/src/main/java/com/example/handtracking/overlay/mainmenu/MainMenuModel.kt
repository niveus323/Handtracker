package com.example.handtracking.overlay.mainmenu

import android.content.Context
import com.example.handtracking.engine.GestureEngine
import com.example.handtracking.overlay.OverlayViewModel

class MainMenuModel(context: Context) : OverlayViewModel(context) {

    private var gestureEngine: GestureEngine = GestureEngine.getGestureEngine(context)

    fun playTap(pos: Array<Float>) {
        gestureEngine.apply {
            processTap(pos)
        }
    }

    fun playSlide(from: Array<Float>, to: Array<Float>) {
        gestureEngine.apply {
            processSlide(from, to)
        }
    }

    fun playZoom() {
        gestureEngine.apply {
            processZoomIn()
//            processZoomOut()
        }
    }

    fun playDrag(pos: Array<Float>) {
        gestureEngine.apply {
            processDrag(pos)
        }
    }

    fun terminateDrag(pos: Array<Float>) {
        gestureEngine.apply {
            this.terminateDrag(pos)
        }
    }
}
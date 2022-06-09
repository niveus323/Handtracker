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

    fun playZoomIn() {
        gestureEngine.apply {
            processZoomIn()
        }
    }
    fun playZoomOut() {
        gestureEngine.apply {
            processZoomOut()
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

    fun terminateSlide(from: Array<Float>, to: Array<Float>) {
        gestureEngine.apply {
            this.terminateSlide(from, to)
        }
    }
}
package com.example.handtracking.overlay.mainmenu

import android.content.Context
import android.media.AudioManager
import com.example.handtracking.engine.GestureEngine
import com.example.handtracking.overlay.OverlayViewModel

class MainMenuModel(context: Context) : OverlayViewModel(context) {

    private var gestureEngine: GestureEngine = GestureEngine.getGestureEngine(context)
    private var audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun turnUpVolume() {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_RAISE,
            AudioManager.FLAG_SHOW_UI)
    }

    fun turnDownVolume() {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
            AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI)
    }

    fun playTap(pos: Array<Float>) {
        gestureEngine.apply {
            processTap(pos)
        }
    }

    fun playSlide(from: Array<Float>, to: Array<Float>) {
        gestureEngine.apply {
            processSlide(to, from)
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
}
package com.example.handtracking.extensions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager

class ScreenMetrics(private val context: Context) {

    companion object {
        /** WindowManager LayoutParams type for a window over applications. */
        @JvmField
        val TYPE_COMPAT_OVERLAY =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE
    }

    /** */
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    /** The display to get the value from. It will always be the first one available. */
    private val display = displayManager.getDisplay(0)
    /** The listener upon orientation changes. */
    private var orientationListener: ((Context) -> Unit)? = null

    /** The orientation of the display. */
    var orientation = computeOrientation()
        private set
    /** The screen size. */
    var screenSize: Point = computeScreenSize()
        private set

    /** Listen to the configuration changes and calls [orientationListener] when needed. */
    private val configChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateScreenMetrics()
        }
    }

    /** @return the limit y position in screen coordinates where it is safe to draw (notch, status bar...) */
    fun getTopSafeArea(): Int {
        var topSafeArea = 0

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            topSafeArea = display.cutout?.safeInsetTop ?: 0
        }

        if (topSafeArea == 0 && Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            topSafeArea = context.getSystemService(WindowManager::class.java)
                .currentWindowMetrics.windowInsets.displayCutout?.safeInsetTop ?: 0
        }

        if (topSafeArea == 0) {
            topSafeArea = context.resources.getDimensionPixelSize(
                context.resources.getIdentifier("status_bar_height", "dimen", "android")
            )
        }

        return topSafeArea
    }

    /**
     * Register a new orientation listener.
     * If a previous listener was registered, the new one will replace it.
     *
     * @param listener the listener to be registered.
     */
    //Overlay 화면에서 클릭 좌표 설정시 실행됨.
    fun registerOrientationListener(listener: (Context) -> Unit) {
        if (listener == orientationListener) {
            return
        }

        unregisterOrientationListener()
        orientationListener = listener
        context.registerReceiver(configChangedReceiver, IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED))
    }

    /** Unregister a previously registered listener. */
    fun unregisterOrientationListener() {
        orientationListener?.let {
            context.unregisterReceiver(configChangedReceiver)
            orientationListener = null
        }
    }

    /** Update orientation and screen size, if needed. Should be called after a configuration change. */
    private fun updateScreenMetrics() {
        val newOrientation = computeOrientation()
        if (orientation != newOrientation) {
            orientation = newOrientation
            screenSize = computeScreenSize()
            orientationListener?.invoke(context)
        }
    }

    /** @return the size of the display, in pixels. */
    private fun computeScreenSize(): Point {
        val size = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val currentWindowMetricsBound = context.getSystemService(WindowManager::class.java)
                .currentWindowMetrics.bounds

            if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                Point(currentWindowMetricsBound.right, currentWindowMetricsBound.bottom)
            } else {
                Point(currentWindowMetricsBound.bottom, currentWindowMetricsBound.right)
            }
        } else {
            val realSize = Point()
            display.getRealSize(realSize)
            realSize
        }

        // Some phone can be messy with the size change with the orientation. Correct it here.
        return if (orientation == Configuration.ORIENTATION_PORTRAIT && size.x > size.y ||
            orientation == Configuration.ORIENTATION_LANDSCAPE && size.x < size.y) {
            Point(size.y, size.x)
        } else {
            size
        }
    }

    /**  @return the orientation of the screen. */
    private fun computeOrientation(): Int = when (display.rotation) {
        Surface.ROTATION_0, Surface.ROTATION_180 -> Configuration.ORIENTATION_PORTRAIT
        Surface.ROTATION_90, Surface.ROTATION_270 -> Configuration.ORIENTATION_LANDSCAPE
        else -> Configuration.ORIENTATION_UNDEFINED
    }
}
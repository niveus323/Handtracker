package com.example.handtracking.engine

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import com.example.handtracking.domain.Action
import com.example.handtracking.domain.Action.Tap
import com.example.handtracking.domain.Action.Zoom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal class ActionExecutor(private val gestureExecutor: (GestureDescription) -> Unit) {
    private var prevZoom: Zoom? = null

    suspend fun executeActions(actions: List<Action>) {
        actions.forEach { action ->
            when (action) {
                is Tap -> executeTap(action)
                is Zoom -> executeZoom(action)
            }
        }
    }

    private suspend fun executeTap(tap: Tap) {
        val tapPath = Path()
        val clickBuilder = GestureDescription.Builder()
        tapPath.moveTo(tap.x!!.toFloat(), tap.y!!.toFloat())
        clickBuilder.addStroke(GestureDescription.StrokeDescription(tapPath, 0, 1))

        withContext(Dispatchers.Main) {
            gestureExecutor(clickBuilder.build())
        }
        delay(1)
    }

    private suspend fun executeZoom(zoom: Zoom) {
        if(prevZoom == null) {
            prevZoom = zoom
            Log.v(TAG, "prevZoom was null")
            logPrevZoom()
        }else {
            //Duration 값은 제스처 인식 도입 후 값을 변경해보아야 함 
            val swipePath1 = Path()
            val swipePath2 = Path()
            val clickBuilder = GestureDescription.Builder()
            swipePath1.moveTo(prevZoom!!.x1!!.toFloat(), prevZoom!!.y1!!.toFloat())
            swipePath1.lineTo(zoom.x1!!.toFloat(), zoom.y1!!.toFloat())
            clickBuilder.addStroke(GestureDescription.StrokeDescription(swipePath1, 0, 200))
            swipePath2.moveTo(prevZoom!!.x2!!.toFloat(), prevZoom!!.y2!!.toFloat())
            swipePath2.lineTo(zoom.x2!!.toFloat(), zoom.y2!!.toFloat())
            clickBuilder.addStroke(GestureDescription.StrokeDescription(swipePath2, 0, 200))

            withContext(Dispatchers.Main) {
                gestureExecutor(clickBuilder.build())
            }
            prevZoom = zoom
            Log.w(TAG, "Zoom executed")
            logPrevZoom()
            delay(200)
        }
    }


    fun terminateZoom() {
        prevZoom = null
        Log.v(TAG, "prevZoom is null")
    }

    private fun logPrevZoom() {
        Log.v(TAG, "prevZoom Changed : (%d,%d), (%d,%d)".format(prevZoom!!.x1, prevZoom!!.y1, prevZoom!!.x2, prevZoom!!.y2))
    }
}

private const val TAG = "ActionExecutor"
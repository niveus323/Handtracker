package com.example.handtracking.engine

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import com.example.handtracking.domain.Action
import com.example.handtracking.domain.Action.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.properties.Delegates


internal class ActionExecutor(private val gestureExecutor: (GestureDescription) -> Unit) {
    private var prevZoom: Zoom? = null
    private var prevDrag: Drag?= null
    private var prevStroke: GestureDescription.StrokeDescription? = null

    suspend fun executeActions(actions: List<Action>) {
        actions.forEach { action ->
            when (action) {
                is Tap -> executeTap(action)
                is Zoom -> executeZoom(action)
                is Slide -> executeSlide(action)
                is Drag -> executeDrag(action)
            }
        }
    }

    private suspend fun executeTap(tap: Tap) {
        val tapPath = Path()
        val clickBuilder = GestureDescription.Builder()
        tapPath.moveTo(tap.x!!, tap.y!!)
        clickBuilder.addStroke(GestureDescription.StrokeDescription(tapPath, 0, 1))

        withContext(Dispatchers.Main) {
            gestureExecutor(clickBuilder.build())
        }
        delay(1)
    }

    private suspend fun executeSlide(slide: Slide) {
        if(slide.x1!!<0 ||  slide.y1!!<0 ){
            Log.w(TAG, "FROM VALUE NEGATIVE")
            return
        }
        if(slide.x2!!<0 || slide.y2!!<0){
            Log.w(TAG, "TO VALUE NEGATIVE")
            return
        }
        val slidePath = Path()
        val clickBuilder = GestureDescription.Builder()
        slidePath.moveTo(slide.x1!!, slide.y1!!)
        slidePath.lineTo(slide.x2!!, slide.y2!!)
        clickBuilder.addStroke(GestureDescription.StrokeDescription(slidePath, 0, 20))
        withContext(Dispatchers.Main) {
            gestureExecutor(clickBuilder.build())
        }
        delay(20)
    }

    suspend fun terminateSlide(slide: Slide) {
//        val dragPath = Path()
//        dragPath.moveTo(prevSlide!!.x2!!, prevSlide!!.y2!!)
//        dragPath.lineTo(slide.x2!!, slide.y2!!)
//        val clickBuilder = GestureDescription.Builder()
//        val strokeDescription = prevStroke!!.continueStroke(dragPath, 0, 1, false)
//        clickBuilder.addStroke(strokeDescription)
//        withContext(Dispatchers.Main) {
//            gestureExecutor(clickBuilder.build())
//        }
        prevStroke = null
    }


    private suspend fun executeDrag(drag: Drag) {
        val dragPath = Path()
        val clickBuilder = GestureDescription.Builder()
        var time by Delegates.notNull<Long>()
        val strokeDescription = if(prevStroke == null){
            time = 1000
            dragPath.moveTo(drag.x!!, drag.y!!)
            GestureDescription.StrokeDescription(dragPath, 0, time, true)
        }else{
            time = 50
            dragPath.moveTo(prevDrag!!.x!!, prevDrag!!.y!!)
            dragPath.lineTo(drag.x!!, drag.y!!)
            prevStroke!!.continueStroke(dragPath, 0, time, true)
        }
        clickBuilder.addStroke(strokeDescription)
        withContext(Dispatchers.Main) {
            gestureExecutor(clickBuilder.build())
        }
        prevStroke = strokeDescription
        prevDrag = drag
        delay(time)
    }

    suspend fun terminateDrag(drag: Drag) {
        val dragPath = Path()
        dragPath.moveTo(prevDrag!!.x!!, prevDrag!!.y!!)
        dragPath.lineTo(drag.x!!, drag.y!!)
        val clickBuilder = GestureDescription.Builder()
        val strokeDescription = prevStroke!!.continueStroke(dragPath, 0, 10, false)
        clickBuilder.addStroke(strokeDescription)
        withContext(Dispatchers.Main) {
            gestureExecutor(clickBuilder.build())
        }
        Log.i(TAG, "Drag Terminate in (${drag.x}, ${drag.y})")
        prevStroke = null
    }

    private suspend fun executeZoom(zoom: Zoom) {
        if(prevZoom == null) {
            prevZoom = zoom
        }else {
            //Duration 값은 제스처 인식 도입 후 값을 변경해보아야 함 
            val swipePath1 = Path()
            val swipePath2 = Path()
            val clickBuilder = GestureDescription.Builder()
            swipePath1.moveTo(prevZoom!!.x1!!, prevZoom!!.y1!!)
            swipePath1.lineTo(zoom.x1!!, zoom.y1!!)
            clickBuilder.addStroke(GestureDescription.StrokeDescription(swipePath1, 0, 200))
            swipePath2.moveTo(prevZoom!!.x2!!, prevZoom!!.y2!!)
            swipePath2.lineTo(zoom.x2!!, zoom.y2!!)
            clickBuilder.addStroke(GestureDescription.StrokeDescription(swipePath2, 0, 200))
            withContext(Dispatchers.Main) {
                gestureExecutor(clickBuilder.build())
            }
            prevZoom = zoom
            delay(200)
        }
    }


    fun terminateZoom() {
        prevZoom = null
        Log.v(TAG, "prevZoom is null")
    }
}

private const val TAG = "ActionExecutor"
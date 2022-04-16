package com.example.handtracking.engine

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log
import androidx.annotation.WorkerThread
import java.lang.IllegalArgumentException

class ScreenRecorder {

    internal companion object {
        private const val TAG = "ScreenRecorder"
        const val VIRTUAL_DISPLAY_NAME = "Clicker"
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var stopListener: (() -> Unit)? =null
    var imageReader: ImageReader? =null
        private set

    fun startProjection(context: Context, resultCode: Int, data: Intent, stoppedListener: () -> Unit) {
        if (projection != null) {
            Log.w(TAG, "StartProjection - Media Projection already started")
            return
        }

        stopListener = stoppedListener
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = projectionManager.getMediaProjection(resultCode, data).apply {
            registerCallback(projectionCallback, null)
        }
    }

    fun startScreenRecord(context: Context, displaySize: Point) {
        if (projection == null || imageReader != null) {
            Log.w(TAG, "StartScreenRecord - Already Started")
            return
        }
        @SuppressLint("WrongConstant")
        imageReader = ImageReader.newInstance(displaySize.x, displaySize.y, PixelFormat.RGBA_8888,2)
        virtualDisplay = projection!!.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME, displaySize.x, displaySize.y, context.resources.configuration.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader!!.surface, null, null)

    }

    fun acquireLatestImage(): Image? = imageReader?.acquireLatestImage()

    @WorkerThread
    fun stopScreenRecord() {
        virtualDisplay?.apply {
            release()
            virtualDisplay = null
        }
        imageReader?.apply {
            close()
            imageReader = null
        }
    }

    fun stopProjection() {
        stopScreenRecord()
        projection?.apply {
            unregisterCallback(projectionCallback)
            stop()
            projection =null
        }
        stopListener =null
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopListener?.invoke()
        }
    }

    internal fun Image.toBitmap(resultBitmap: Bitmap? = null): Bitmap {
        var bitmap = resultBitmap
        val imageWidth = width + (planes[0].rowStride - planes[0].pixelStride * width) / planes[0].pixelStride

        if (bitmap == null) {
            bitmap = Bitmap.createBitmap(imageWidth, height, Bitmap.Config.ARGB_8888)
        } else if (bitmap.width != imageWidth || bitmap.height != height) {
            try {
                bitmap.reconfigure(imageWidth, height, Bitmap.Config.ARGB_8888)
            } catch (ex: IllegalArgumentException) {
                bitmap = Bitmap.createBitmap(imageWidth, height, Bitmap.Config.ARGB_8888)
            }
        }

        bitmap?.copyPixelsFromBuffer(planes[0].buffer)
        return bitmap!!
    }
}
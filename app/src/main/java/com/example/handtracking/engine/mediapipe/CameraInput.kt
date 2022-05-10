package com.example.handtracking.engine.mediapipe

import android.app.Activity
import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import android.util.Size
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.components.*
import com.google.mediapipe.components.CameraHelper.OnCameraStartedListener
import com.google.mediapipe.framework.MediaPipeException
import javax.microedition.khronos.egl.EGLContext

class CameraInput {
    private val TAG = CameraInput::class.java.simpleName

    /** Represents the direction the camera faces relative to device screen.  */
    enum class CameraFacing {
        FRONT, BACK
    }

    private var cameraHelper: CameraXPreviewHelper = CameraXPreviewHelper()
    private var customOnCameraStartedListener: OnCameraStartedListener? = null
    private var newFrameListener: TextureFrameConsumer? = null

    // {@link SurfaceTexture} where the camera-preview frames can be accessed.
    private var frameTexture: SurfaceTexture? = null
    private var converter: ExternalTextureConverter? = null

    /**
     * Sets a callback to be invoked when new frames available.
     *
     * @param listener the callback.
     */
    fun setNewFrameListener(listener: TextureFrameConsumer?) {
        newFrameListener = listener
    }

    /**
     * Sets a callback to be invoked when camera start is complete.
     *
     * @param listener the callback.
     */
    fun setOnCameraStartedListener(listener: OnCameraStartedListener?) {
        customOnCameraStartedListener = listener
    }

    /**
     * Sets up the external texture converter and starts the camera.
     *
     * @param activity an Android [Activity].
     * @param eglContext an OpenGL [EGLContext].
     * @param cameraFacing the direction the camera faces relative to device screen.
     * @param width the desired width of the converted texture.
     * @param height the desired height of the converted texture.
     */
    fun start(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        eglContext: EGLContext?,
        cameraFacing: CameraFacing,
        width: Int,
        height: Int
    ) {
        if (converter == null) {
            converter = ExternalTextureConverter(eglContext, 2)
        }
        if (newFrameListener == null) {
            throw MediaPipeException(
                MediaPipeException.StatusCode.FAILED_PRECONDITION.ordinal,
                "newFrameListener is not set."
            )
        }
        frameTexture = converter!!.surfaceTexture
        converter!!.setConsumer(newFrameListener)
        cameraHelper.setOnCameraStartedListener { surfaceTexture: SurfaceTexture? ->
            if (width != 0 && height != 0) {
                // Sets the size of the output texture frame.
                updateOutputSize(width, height)
            }
            customOnCameraStartedListener?.onCameraStarted(surfaceTexture)
        }
        cameraHelper.startCamera(
            context,
            lifecycleOwner,
            if (cameraFacing == CameraFacing.FRONT) CameraHelper.CameraFacing.FRONT else CameraHelper.CameraFacing.BACK,
            /*surfaceTexture=*/ frameTexture,
            if (width == 0 || height == 0) null else Size(width, height)
        )
    }

    /**
     * Sets or updates the size of the output [TextureFrame]. Can be invoked by `SurfaceHolder.Callback.surfaceChanged` when the surface size is changed.
     *
     * @param width the desired width of the converted texture.
     * @param height the desired height of the converted texture.
     */
    private fun updateOutputSize(width: Int, height: Int) {
        val displaySize = cameraHelper.computeDisplaySizeFromViewSize(Size(width, height))
        val isCameraRotated = cameraHelper.isCameraRotated
        Log.i(
            TAG,
            "Set camera output texture frame size to width="
                    + displaySize.width
                    + " , height="
                    + displaySize.height
        )
        // Configure the output width and height as the computed
        // display size.
        converter!!.setDestinationSize(
            if (isCameraRotated) displaySize.height else displaySize.width,
            if (isCameraRotated) displaySize.width else displaySize.height
        )
    }

    /** Closes the camera input.  */
    fun close() {
        if (converter != null) {
            converter!!.close()
        }
    }

    /** Returns a boolean which is true if the camera is in Portrait mode, false in Landscape mode.  */
    fun isCameraRotated(): Boolean {
        return cameraHelper.isCameraRotated
    }
}
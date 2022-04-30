package com.example.handtracking.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.camera2.*
import android.media.MediaCodec
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import com.example.handtracking.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CameraFragment(private val mcontext: Context, private val surfaceView: SurfaceView ) : LifecycleOwner {
    private val lifecycleRegistry: LifecycleRegistry by lazy { LifecycleRegistry(this) }
    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    private val cameraManager: CameraManager by lazy {
        mcontext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(cameraManager.cameraIdList[1])
    }

    private val outputFile: File by lazy {
        createFile(mcontext, "mp4")
    }

    private val recorderSurface: Surface by lazy {
        val surface = MediaCodec.createPersistentInputSurface()
        createRecorder(surface).apply {
            prepare()
            release()
        }
        surface
    }

    private val recorder: MediaRecorder by lazy { createRecorder(recorderSurface) }

    private val cameraThread = HandlerThread("CameraThread").apply { start() }

    private val cameraHandler = Handler(cameraThread.looper)

    private lateinit var session: CameraCaptureSession

    private lateinit var camera: CameraDevice

    private val previewRequest: CaptureRequest by lazy {
        session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surfaceView.holder.surface)
        }.build()
    }

    private val recordRequest: CaptureRequest by lazy {
        session.device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            addTarget(surfaceView.holder.surface)
            addTarget(recorderSurface)
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30,30))
        }.build()
    }

    @SuppressLint("MissingPermission")
    fun initialize() {

        surfaceView.holder.addCallback(object: SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                surfaceView.post { initializeCamera() }

            }

            override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) = Unit
            override fun surfaceDestroyed(p0: SurfaceHolder) = Unit
        })
    }


    @Suppress("DEPRECATION")
    private fun createRecorder(surface: Surface) = MediaRecorder().apply {
        setVideoSource(MediaRecorder.VideoSource.SURFACE)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setOutputFile(outputFile.absolutePath)
        setVideoEncodingBitRate(RECORDER_VIDEO_BITRATE)
        setVideoFrameRate(30)
        setVideoSize(1280, 960)
        setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        setInputSurface(surface)
    }

    private fun initializeCamera() = lifecycleScope.launch(Dispatchers.Main) {
        camera = openCamera(cameraManager, cameraManager.cameraIdList[1], cameraHandler)

        val targets = listOf(surfaceView.holder.surface, recorderSurface)

        session = createCaptureSession(camera, targets, cameraHandler)

        session.setRepeatingRequest(previewRequest, null, cameraHandler)
    }

    fun startRecording() {
        lifecycleScope.launch(Dispatchers.IO) {
            session.setRepeatingRequest(recordRequest, null, cameraHandler)

            recorder.apply {
                setOrientationHint(270)
                prepare()
                start()
            }
        }
    }

    fun stopRecording() : Intent {
        Log.d(TAG, "Recording stopped. Output file: $outputFile")
        recorder.stop()
        MediaScannerConnection.scanFile(mcontext, arrayOf(outputFile.absolutePath), null, null)
        return Intent().apply {
            action = Intent.ACTION_VIEW
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(outputFile.extension)
            val authority = "${BuildConfig.APPLICATION_ID}.provider"
            data = FileProvider.getUriForFile(mcontext, authority, outputFile)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
    }

    @SuppressLint("MissingPermission")
    private suspend fun openCamera(manager: CameraManager, cameraId: String, handler: Handler? = null) : CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) = cont.resume(device)

            override fun onDisconnected(p0: CameraDevice) {
                Log.w(TAG, "Camera $cameraId has been disconnected")
            }

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when(error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Log.e(TAG, exc.message, exc)
                if(cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }

    @Suppress("DEPRECATION")
    private suspend fun createCaptureSession(device: CameraDevice, targets: List<Surface>, handler: Handler? = null): CameraCaptureSession = suspendCoroutine { cont ->
        device.createCaptureSession(targets, object: CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)
            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }, handler)
    }

    fun destroy() {
        try {
            camera.close()
            cameraThread.quitSafely()
            recorder.release()
            recorderSurface.release()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error in closing camera", exc)
        }
    }

    companion object {
        private val TAG = CameraFragment::class.java.simpleName

        private const val RECORDER_VIDEO_BITRATE: Int = 10_000_000

        private fun createFile(context: Context, extension: String) : File {
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.KOREA)
            return File(context.filesDir, "VID_${sdf.format(Date())}.$extension")
        }
    }

}
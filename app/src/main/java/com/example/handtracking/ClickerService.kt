package com.example.handtracking

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.example.handtracking.activity.MainActivity
import com.example.handtracking.engine.GestureEngine
import com.example.handtracking.overlay.OverlayController
import com.example.handtracking.overlay.mainmenu.MainMenu

class ClickerService : AccessibilityService() {
    companion object {
        private const val NOTIFICATION_ID = 60
        private const val NOTIFICATION_CHANNEL_ID = "ClickerService"
        private var LOCAL_SERVICE_INSTANCE: LocalService? = null
            set(value) {
                field = value
                LOCAL_SERVICE_CALLBACK?.invoke(field)
            }
        private var LOCAL_SERVICE_CALLBACK: ((LocalService?) -> Unit)? =null
            set(value) {
                field = value
                value?.invoke(LOCAL_SERVICE_INSTANCE)
            }

        fun getLocalService(stateCallBack: ((LocalService?) -> Unit)?) {
            LOCAL_SERVICE_CALLBACK = stateCallBack
        }
    }

    private var gestureEngine: GestureEngine? = null
    private var rootOverlayController: OverlayController? = null
    private var isStarted: Boolean = false

    inner class LocalService {

        fun start(resultCode: Int, data: Intent) {
            if (isStarted) {
                return
            }
            isStarted = true
            startForeground(NOTIFICATION_ID, createNotification())

            gestureEngine = GestureEngine.getGestureEngine(this@ClickerService).apply {
                start(this@ClickerService, resultCode, data) {
                    gesture -> dispatchGesture(gesture, null, null)
                }
            }

            rootOverlayController = MainMenu(this@ClickerService).apply {
                create(::stop)
            }

        }

        fun stop() {
            if (!isStarted) {
                return
            }

            isStarted = false


            rootOverlayController?.dismiss()
            rootOverlayController = null

            gestureEngine?.let { detector ->
                detector.stop()
                detector.clear()
            }
            gestureEngine = null

            stopForeground(true)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        LOCAL_SERVICE_INSTANCE = LocalService()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        LOCAL_SERVICE_INSTANCE?.stop()
        LOCAL_SERVICE_INSTANCE = null
        return super.onUnbind(intent)
    }

    private fun createNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager!!.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW
            )
        )
        val intent = Intent(this, MainActivity::class.java)
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_message))
            .setContentIntent(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
            .build()
    }

    override fun onAccessibilityEvent(p0: AccessibilityEvent?) { /*unused*/ }
    override fun onInterrupt() { /*unused*/ }
}
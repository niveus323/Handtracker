package com.example.handtracking.activity

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import com.example.handtracking.ClickerService

class ViewModel(application: Application) : AndroidViewModel(application) {

    private var clickerService: ClickerService.LocalService? = null
    private var serviceConnection: (ClickerService.LocalService?) -> Unit = {
        localService -> clickerService = localService
    }


    init {
        ClickerService.getLocalService(serviceConnection)
    }

    override fun onCleared() {
        ClickerService.getLocalService(null)
        super.onCleared()
    }

    fun isOverlayPermissionValid(): Boolean = Settings.canDrawOverlays(getApplication())
    fun isAccessibilityPermissionValid(): Boolean = clickerService != null
    fun isCameraPermissionValid(): Boolean =
        ContextCompat.checkSelfPermission(getApplication<Application>().applicationContext, android.Manifest.permission.CAMERA )  == PackageManager.PERMISSION_GRANTED
    fun arePermissionsGranted(): Boolean = isOverlayPermissionValid() && isAccessibilityPermissionValid()

    fun loadScenario(resultCode: Int, data: Intent) {
        clickerService?.start(resultCode, data)
    }

    fun stopScenario() {
        clickerService?.stop()
    }

}
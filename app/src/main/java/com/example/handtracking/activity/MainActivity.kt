package com.example.handtracking.activity

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.handtracking.ClickerService
import com.example.handtracking.R

class MainActivity : AppCompatActivity(), PermissionsDialogFragment.PermissionDialogListener {

    private val viewModel: ViewModel by viewModels()

    private lateinit var screenCaptureLauncher: ActivityResultLauncher<Intent>
    private lateinit var goOverlayView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        viewModel.stopScenario()
        goOverlayView = findViewById(R.id.btn_go_overlay)
        goOverlayView.setOnClickListener{ onClicked() }

        screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode != RESULT_OK) {
                Toast.makeText(this, "User denied screen sharing permission", Toast.LENGTH_SHORT).show()
            } else {
              viewModel.loadScenario(it.resultCode, it.data!!)
              finish()
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        ClickerService.getLocalService(null)
    }

    //Main화면의 버튼을 누르면 해당 함수 호출.
    private fun onClicked() {
        if (!viewModel.arePermissionsGranted()) {
            PermissionsDialogFragment.newInstance().show(supportFragmentManager, "fragment_edit_name")
            return
        }
        onPermissionsGranted()
    }

    override fun onPermissionsGranted() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}
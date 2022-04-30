package com.example.handtracking.activity

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.handtracking.ClickerService
import com.example.handtracking.R

class PermissionsDialogFragment : DialogFragment() {
    companion object {
        /** Intent extra bundle key for the Android settings app. */
        private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
        /** Intent extra bundle key for the Android settings app. */
        private const val EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":settings:show_fragment_args"

        fun newInstance() : PermissionsDialogFragment {
            return PermissionsDialogFragment()
        }
    }

    interface PermissionDialogListener {
        /** Called when all permissions are granted and the user press ok. */
        fun onPermissionsGranted()
    }

    private val viewModel: ViewModel by activityViewModels()
    private lateinit var activity: Activity
    private lateinit var overlayView: View
    private lateinit var overlayStateView: ImageView
    private lateinit var accessibilityView: View
    private lateinit var accessibilityStateView: ImageView
    private lateinit var cameraView: View
    private lateinit var cameraStateView: ImageView

    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        this.activity = activity
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setView(R.layout.dialog_permissions)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                (activity as PermissionDialogListener).onPermissionsGranted()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.let {
            overlayStateView = it.findViewById(R.id.img_config_overlay_status)
            overlayView = it.findViewById(R.id.item_overlay_permission)
            overlayView.setOnClickListener{ onOverlayClicked() }
            accessibilityStateView = it.findViewById(R.id.img_config_accessibility_status)
            accessibilityView = it.findViewById(R.id.item_accessibility_permission)
            accessibilityView.setOnClickListener { onAccessibilityClicked() }
            cameraStateView = it.findViewById(R.id.img_config_camera_status)
            cameraView = it.findViewById(R.id.item_camera_permission)
            cameraView.setOnClickListener { onCameraClicked() }
        }
    }

    override fun onResume() {
        super.onResume()
        //권한을 확인하고 View 업데이트.
        setConfigStateDrawable(overlayStateView, viewModel.isOverlayPermissionValid())
        setConfigStateDrawable(accessibilityStateView, viewModel.isAccessibilityPermissionValid())
        setConfigStateDrawable(cameraStateView, viewModel.isCameraPermissionValid())
        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled =
            viewModel.isOverlayPermissionValid() && viewModel.isAccessibilityPermissionValid() && viewModel.isCameraPermissionValid()
    }

    private fun onOverlayClicked() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${requireContext().packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)

        requireContext().startActivity(intent)
    }

    private fun onAccessibilityClicked() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)

        val bundle = Bundle()
        val showArgs = requireContext().packageName + "/" + ClickerService::class.java.name
        bundle.putString(EXTRA_FRAGMENT_ARG_KEY, showArgs)
        intent.putExtra(EXTRA_FRAGMENT_ARG_KEY, showArgs)
        intent.putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, bundle)

        requireContext().startActivity(intent)
    }

    private fun onCameraClicked() {
        ActivityCompat.requestPermissions(activity,  arrayOf(Manifest.permission.CAMERA) ,1000 )
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
        requireContext().startActivity(intent)
    }

    private fun setConfigStateDrawable(view: ImageView, state: Boolean) {
        if (state) {
            view.setImageResource(R.drawable.ic_confirm)
            view.drawable.setTint(Color.GREEN)
        } else {
            view.setImageResource(R.drawable.ic_cancel)
            view.drawable.setTint(Color.RED)
        }
    }
}
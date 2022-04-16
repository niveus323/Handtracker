package com.example.handtracking.overlay.mainmenu

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.example.handtracking.R
import com.example.handtracking.overlay.OverlayMenuController
import kotlinx.coroutines.launch

class MainMenu(context: Context) : OverlayMenuController(context) {
    private var viewModel: MainMenuModel? = MainMenuModel(context).apply {
        attachToLifecycle(this@MainMenu)
    }

    override fun onCreateMenu(layoutInflater: LayoutInflater): ViewGroup = layoutInflater.inflate(R.layout.overlay_menu, null) as ViewGroup
    override fun onCreateOverlayViewLayoutParams(): WindowManager.LayoutParams = super.onCreateOverlayViewLayoutParams().apply {
        flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    }

    override fun onCreate() {
        super.onCreate()
        setOverlayViewVisibility(View.GONE)
    }

    override fun onStart() {
        super.onStart()

        getMenuItemView<View>(R.id.btn_play)?.setOnLongClickListener {
            viewModel?.playTap()
            true
        }

        getMenuItemView<View>(R.id.btn_play2)?.setOnLongClickListener {
            viewModel?.playZoom()
            true
        }
    }

    override fun onDismissed() {
        super.onDismissed()
        viewModel = null
    }

    override fun onMenuItemClicked(viewId: Int) {
        when (viewId) {
            R.id.btn_play -> viewModel?.playTap()
            R.id.btn_play2 -> viewModel?.playZoom()
            R.id.btn_stop -> dismiss()
        }
    }

}
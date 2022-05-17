package com.example.handtracking.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.*
import android.widget.ImageView
import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.VisibleForTesting
import androidx.core.view.forEach
import com.example.handtracking.R
import com.example.handtracking.engine.mediapipe.HandsTracker
import com.example.handtracking.extensions.ScreenMetrics
import java.util.*
import kotlin.concurrent.timer

abstract class OverlayMenuController(context: Context) : OverlayController(context), HandsTracker.HandResultListener {

    @VisibleForTesting
    internal companion object {
        /** Name of the preference file. */
        const val PREFERENCE_NAME = "OverlayMenuController"
        /** Preference key referring to the landscape X position of the menu during the last call to [dismiss]. */
        const val PREFERENCE_MENU_X_LANDSCAPE_KEY = "Menu_X_Landscape_Position"
        /** Preference key referring to the landscape Y position of the menu during the last call to [dismiss]. */
        const val PREFERENCE_MENU_Y_LANDSCAPE_KEY = "Menu_Y_Landscape_Position"
        /** Preference key referring to the portrait X position of the menu during the last call to [dismiss]. */
        const val PREFERENCE_MENU_X_PORTRAIT_KEY = "Menu_X_Portrait_Position"
        /** Preference key referring to the portrait Y position of the menu during the last call to [dismiss]. */
        const val PREFERENCE_MENU_Y_PORTRAIT_KEY = "Menu_Y_Portrait_Position"
    }

    /** Monitors the state of the screen. */
    protected val screenMetrics = ScreenMetrics(context)
    /** The layout parameters of the menu layout. */
    private val menuLayoutParams: WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        ScreenMetrics.TYPE_COMPAT_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT)
    /** The shared preference storing the position of the menu in order to save/restore the last user position. */
    private val sharedPreferences = context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
    /** The Android window manager. Used to add/remove the overlay menu and view. */
    private val windowManager = context.getSystemService(WindowManager::class.java)!!
    /** Value of the alpha for a disabled item view in the menu. */
    @SuppressLint("ResourceType")
    private val disabledItemAlpha = context.resources.getFraction(R.dimen.alpha_menu_item_disabled, 1, 1)

    /** The root view of the menu overlay. Retrieved from [onCreateMenu] implementation. */
    private var menuLayout: ViewGroup? = null
    /** The layout parameters of the overlay view. */
    private lateinit var overlayLayoutParams:  WindowManager.LayoutParams
    /** Another view to mark up the Mediapipe Hands finger. Retrieved from [onCreateTarget] implementation  */
    private var targetLayout: ViewGroup? = null
    private lateinit var targetLayoutParams:  WindowManager.LayoutParams

    /** The initial position of the overlay menu when pressing the move menu item. */
    private var moveInitialMenuPosition = 0 to 0
    /** The initial position of the touch event that as initiated the move of the overlay menu. */
    private var moveInitialTouchPosition = 0 to 0

    /** Listener upon the screen orientation changes. */
    private val orientationListener = ::onOrientationChanged

    /**
     * Creates the root view of the menu overlay.
     *
     * @param layoutInflater the Android layout inflater.
     *
     * @return the menu root view. It MUST contains a view group within a depth of 2 that contains all menu items in
     *         order for move and hide to work as expected.
     */
    protected abstract fun onCreateMenu(layoutInflater: LayoutInflater): ViewGroup

    /**
     * Creates the root view of the target overlay.
     *
     * @param layoutInflater the Android layout inflater.
     *
     * @return the target view.
     */
    protected abstract fun onCreateTarget(layoutInflater: LayoutInflater): ViewGroup

    /**
     * Creates the layout parameters for the [overlayLayoutParams].
     * Default implementation uses the same parameters as the floating menu, but in fullscreen.
     *
     * @return the layout parameters to apply to the overlay view.
     */
    protected open fun onCreateOverlayViewLayoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams().apply {
        copyFrom(menuLayoutParams)
        screenMetrics.screenSize.let { size ->
            width = size.x
            height = size.y
        }
    }

    @CallSuper
    override fun onCreate() {
        // First, call implementation methods to check what we should display
        val layoutInflater = context.getSystemService(LayoutInflater::class.java)
        menuLayout = onCreateMenu(layoutInflater)
        overlayLayoutParams = onCreateOverlayViewLayoutParams()
        targetLayout = onCreateTarget(layoutInflater)
        targetLayoutParams = WindowManager.LayoutParams().apply {
            copyFrom(overlayLayoutParams)
        }
        // Set the clicks listener on the menu items
        menuLayout!!.let {
            val parentLayout = it.findViewById<ViewGroup>(R.id.view_group_buttons)
            parentLayout.forEach { view ->
                @SuppressLint("ClickableViewAccessibility") // View is only drag and drop, no click
                when (view.id) {
                    R.id.btn_move -> view.setOnTouchListener { _: View, event: MotionEvent -> onMoveTouched(event) }
                    else -> view.setOnClickListener { v -> onMenuItemClicked(v.id) }
                }
            }
        }

        // Restore the last menu position, if any.
        menuLayoutParams.gravity = Gravity.TOP or Gravity.START
        overlayLayoutParams.gravity = Gravity.TOP or Gravity.START
        targetLayoutParams.gravity = Gravity.TOP or Gravity.START
        loadMenuPosition(screenMetrics.orientation)
    }

    protected open fun onMenuItemClicked(@IdRes viewId: Int): Unit? = null

    @CallSuper
    override fun onStart() {
        screenMetrics.registerOrientationListener(orientationListener)
        windowManager.addView(menuLayout, menuLayoutParams)
        windowManager.addView(targetLayout, targetLayoutParams)
    }

    @CallSuper
    override fun onStop() {
        windowManager.removeView(menuLayout)
        windowManager.removeView(targetLayout)

        screenMetrics.unregisterOrientationListener()
    }

    @CallSuper
    override fun onDismissed() {
        // Save last user position
        saveMenuPosition(screenMetrics.orientation)
        menuLayout = null
        targetLayout = null
    }

    /**
     * Change the menu view visibility.
     * @param visibility the new visibility to apply.
     */
    fun setMenuVisibility(visibility: Int) {
        menuLayout?.visibility = visibility
    }

    /**
     * Set the enabled state of a menu item.
     *
     * @param viewId the view identifier of the menu item to change the state of.
     * @param enabled true to enable the view, false to disable it.
     * @param clickable true to keep the view clickable, false to ignore all clicks on the view. False by default.
     */
    protected fun setMenuItemViewEnabled(@IdRes viewId: Int, enabled: Boolean, clickable: Boolean = false) {
        menuLayout?.findViewById<View>(viewId)?.apply {
            isEnabled = enabled || clickable
            alpha = if (enabled) 1.0f else disabledItemAlpha
        }
    }

    /**
     * Set the drawable resource of a menu item.
     *
     * @param viewId the view identifier of the menu item to change the drawable of.
     * @param imageId the identifier of the new drawable.
     */
    protected fun setMenuItemViewImageResource(@IdRes viewId: Int, @DrawableRes imageId: Int) {
        (menuLayout?.findViewById<View>(viewId) as ImageView).setImageResource(imageId)
    }

    /**
     * Set the drawable of a menu item.
     *
     * @param viewId the view identifier of the menu item to change the drawable of.
     * @param drawable the new drawable.
     */
    protected fun setMenuItemViewDrawable(@IdRes viewId: Int, drawable: Drawable) {
        (menuLayout?.findViewById<View>(viewId) as ImageView).setImageDrawable(drawable)
    }

    /** */
    protected fun <T: View> getMenuItemView(@IdRes viewId: Int): T? = menuLayout?.findViewById(viewId)
    protected fun <T: View> getTargetItemView(@IdRes viewId: Int): T? = targetLayout?.findViewById(viewId)

    /**
     * Called when the user touch the [R.id.btn_move] menu item.
     * Handle the long press and move on this button in order to drag and drop the overlay menu on the screen.
     *
     * @param event the touch event occurring on the menu item.
     *
     * @return true if the event is handled, false if not.
     */
    private fun onMoveTouched(event: MotionEvent) : Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                menuLayout!!.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                moveInitialMenuPosition = menuLayoutParams.x to menuLayoutParams.y
                moveInitialTouchPosition = event.rawX.toInt() to event.rawY.toInt()
                true
            }
            MotionEvent.ACTION_MOVE -> {
                setMenuLayoutPosition(
                    moveInitialMenuPosition.first + (event.rawX.toInt() - moveInitialTouchPosition.first),
                    moveInitialMenuPosition.second + (event.rawY.toInt() - moveInitialTouchPosition.second)
                )
                windowManager.updateViewLayout(menuLayout, menuLayoutParams)
                true
            }
            else -> false
        }
    }

    /**
     * Safe setter for the position of the overlay menu ensuring it will not be displayed outside the screen.
     *
     * @param x the horizontal position.
     * @param y the vertical position.
     */
    private fun setMenuLayoutPosition(x: Int, y: Int) {
        val displaySize = screenMetrics.screenSize
        menuLayoutParams.x = x.coerceIn(0, displaySize.x - menuLayout!!.width)
        menuLayoutParams.y = y.coerceIn(0, displaySize.y - menuLayout!!.height)
    }

    /**
     * Handles the screen orientation changes.
     * It will save the menu position for the previous orientation and load and apply the correct position for the new
     * orientation.
     *
     * @param context the Android context.
     */
    private fun onOrientationChanged(context: Context) {
        saveMenuPosition(if (screenMetrics.orientation == Configuration.ORIENTATION_LANDSCAPE)
            Configuration.ORIENTATION_PORTRAIT
        else
            Configuration.ORIENTATION_LANDSCAPE
        )
        loadMenuPosition(screenMetrics.orientation)

        windowManager.updateViewLayout(menuLayout, menuLayoutParams)
    }

    /**
     * Load last user menu position for the current orientation, if any.
     *
     * @param orientation the orientation to load the position for.
     */
    private fun loadMenuPosition(orientation: Int) {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setMenuLayoutPosition(
                sharedPreferences.getInt(PREFERENCE_MENU_X_LANDSCAPE_KEY, 0),
                sharedPreferences.getInt(PREFERENCE_MENU_Y_LANDSCAPE_KEY, 0)
            )
        } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            setMenuLayoutPosition(
                sharedPreferences.getInt(PREFERENCE_MENU_X_PORTRAIT_KEY, 0),
                sharedPreferences.getInt(PREFERENCE_MENU_Y_PORTRAIT_KEY, 0)
            )
        }
    }

    /**
     * Save the last user menu position for the current orientation.
     *
     * @param orientation the orientation to save the position for.
     */
    private fun saveMenuPosition(orientation: Int) {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            sharedPreferences.edit()
                .putInt(PREFERENCE_MENU_X_LANDSCAPE_KEY, menuLayoutParams.x)
                .putInt(PREFERENCE_MENU_Y_LANDSCAPE_KEY, menuLayoutParams.y)
                .apply()
        } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            sharedPreferences.edit()
                .putInt(PREFERENCE_MENU_X_PORTRAIT_KEY, menuLayoutParams.x)
                .putInt(PREFERENCE_MENU_Y_PORTRAIT_KEY, menuLayoutParams.y)
                .apply()
        }
    }

    /**
     * Called when HandsTracker .
     * Handle x,y position of finger tip to move [targetLayout]
     *
     * @param x, y position of finger tip detected by [HandsTracker]
     *
     */
    override fun onHandResultDetected(x:Float, y:Float) {
        targetLayoutParams.x = x.toInt()
        targetLayoutParams.y = y.toInt()
        targetLayout?.post {
            windowManager.updateViewLayout(targetLayout, targetLayoutParams)
        }
        if(checkCollisionWithView(getMenuItemView<View>(R.id.btn_feedback)!!)){
            //Timer 돌려서 0.3초마다? Animation 갱신하고 3초 지나면 Button UI를 보이는걸로.
            //Button UI 표기가 가능해지면 Animation을 붙인다.
            startTimer()
        }else{
            stopTimer()
        }
    }

    private var isTimerStarted = false
    private var time:Int = 0
    private var timer: Timer?= null

    private fun startTimer() {
        if(isTimerStarted) return
        timer = timer(period = 3000, initialDelay = 1000) {
            time++
            if(time >= 3){
                val buttongroup = getMenuItemView<ViewGroup>(R.id.view_group_buttons)
                buttongroup?.post{
                    buttongroup.visibility = View.VISIBLE
                }


//                stopTimer()
            }
        }
        isTimerStarted = true
    }
    private fun stopTimer() {
        timer?.cancel()
        time = 0
        isTimerStarted = false
    }

    private fun checkCollisionWithView(view: View): Boolean {
        val r1 = Rect(menuLayoutParams.x + view.left,
            menuLayoutParams.y + view.top,
            menuLayoutParams.x + view.right,
            menuLayoutParams.y + view.bottom)
        val targetItem = getTargetItemView<View>(R.id.targetImage)!!
        val r2 = Rect(targetLayoutParams.x + targetItem.left,
            targetLayoutParams.y + targetItem.top,
            targetLayoutParams.x + targetItem.right,
            targetLayoutParams.y + targetItem.bottom)

        return r1.intersect(r2)
    }
}
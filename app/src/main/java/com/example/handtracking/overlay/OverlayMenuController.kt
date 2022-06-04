package com.example.handtracking.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.*
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.VisibleForTesting
import com.example.handtracking.R
import com.example.handtracking.engine.mediapipe.HandsTracker
import com.example.handtracking.extensions.ScreenMetrics

abstract class OverlayMenuController(context: Context) : OverlayController(context){

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
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
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
    private var cursorLayout: ViewGroup? = null
    protected lateinit var cursorItem: ProgressBar
    private lateinit var cursorLayoutParams:  WindowManager.LayoutParams

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
        cursorLayout = onCreateTarget(layoutInflater)
        cursorLayoutParams = WindowManager.LayoutParams().apply {
            copyFrom(overlayLayoutParams)
        }

        // Restore the last menu position, if any.
        menuLayoutParams.gravity = Gravity.TOP or Gravity.START
        overlayLayoutParams.gravity = Gravity.TOP or Gravity.START
        cursorLayoutParams.gravity = Gravity.TOP or Gravity.START
        loadMenuPosition(screenMetrics.orientation)
    }

    @CallSuper
    override fun onStart() {
        screenMetrics.registerOrientationListener(orientationListener)
        windowManager.addView(menuLayout, menuLayoutParams)
        windowManager.addView(cursorLayout, cursorLayoutParams)
    }

    @CallSuper
    override fun onStop() {
        windowManager.removeView(menuLayout)
        windowManager.removeView(cursorLayout)

        screenMetrics.unregisterOrientationListener()
    }

    @CallSuper
    override fun onDismissed() {
        // Save last user position
        saveMenuPosition(screenMetrics.orientation)
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
    protected fun <T: View> getTargetItemView(@IdRes viewId: Int): T? = cursorLayout?.findViewById(viewId)

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
     * Handle x,y position of finger tip to move [cursorLayout]
     *
     * @param x, y position of finger tip detected by [HandsTracker]
     *
     */
    protected fun setCursorPosition(x:Float, y:Float) {
        cursorLayout?.post {
            cursorItem.x = x
            cursorItem.y = y
            windowManager.updateViewLayout(cursorLayout, cursorLayoutParams)
        }
    }

    protected fun isCursorIntersected(view: View): Boolean {
        val viewLocation = IntArray(2)
        view.getLocationOnScreen(viewLocation)
        val r1 = Rect(viewLocation[0], viewLocation[1], viewLocation[0] + view.width, viewLocation[1] + view.height)
        val r2 = Rect(cursorItem.x.toInt(), cursorItem.y.toInt(), cursorItem.x.toInt() + cursorItem.width, cursorItem.y.toInt() + cursorItem.height)

        return r1.intersect(r2)
    }

    protected val cursorPosition:Array<Float>
        get() {
            return arrayOf(cursorItem.x, cursorItem.y)
        }

    protected fun moveMenuLayout() {
        menuLayout?.post {
            setMenuLayoutPosition(cursorItem.x.toInt()-deltaX, cursorItem.y.toInt()-deltaY)
            windowManager.updateViewLayout(menuLayout, menuLayoutParams)
        }
    }

    private var deltaX : Int = 0
    private var deltaY : Int = 0

    protected fun setDelta(moveView: View, menuView: View){
        val menuViewLocation = IntArray(2)
        menuView.getLocationOnScreen(menuViewLocation)
        val moveViewLocation = IntArray(2)
        moveView.getLocationOnScreen(moveViewLocation)
        deltaX = moveViewLocation[0] - menuViewLocation[0]
        deltaY = moveViewLocation[1] - menuViewLocation[1]
    }
}
package com.example.handtracking.overlay

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

abstract class OverlayViewModel(protected val context: Context): DefaultLifecycleObserver {

    /** Tells if this view model is attached to a lifecycle. */
    private var isAttachedToLifecycle: Boolean = false

    /** The scope for all coroutines executed by this model. */
    protected val viewModelScope = CoroutineScope(Job())

    /**
     * Attach the view model to a lifecycle.
     * @param owner the owner of the lifecycle to attach to.
     */
    fun attachToLifecycle(owner: LifecycleOwner) {
        if (isAttachedToLifecycle) {
            throw IllegalStateException("Model is already attached to a lifecycle owner")
        }

        isAttachedToLifecycle = true
        owner.lifecycle.addObserver(this)
    }

    final override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)

        onCleared()

        owner.lifecycle.removeObserver(this)
        viewModelScope.cancel()
        isAttachedToLifecycle = false
    }

    /**
     * Called when the lifecycle owner is destroyed.
     * Override to clear any resources associated with this view model.
     */
    open fun onCleared() {}
}
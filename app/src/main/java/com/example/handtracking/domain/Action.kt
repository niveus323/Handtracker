package com.example.handtracking.domain

sealed class Action {

    internal abstract fun isComplete(): Boolean

    data class Tap(
        var x: Float? = null,
        var y: Float? = null
    ) : Action() {

        override fun isComplete(): Boolean = (x != null && y != null)

    }

    data class Zoom(
        var x1: Float? = null,
        var y1: Float? = null,
        var x2: Float? = null,
        var y2: Float? = null,
    ) : Action() {

        override fun isComplete(): Boolean = x1 != null && y1 != null && x2 != null && y2 != null
    }

    data class Slide(
        var x1: Float? = null,
        var y1: Float? = null,
        var x2: Float? = null,
        var y2: Float? = null,
    ) : Action() {
        override fun isComplete(): Boolean = x1 != null && y1 != null && x2 != null && y2 != null
    }

    data class Drag (
        var x: Float? = null,
        var y: Float? = null,
    ) : Action() {
        override fun isComplete(): Boolean = x != null && y != null
    }
}
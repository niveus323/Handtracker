package com.example.handtracking.domain

sealed class Action {

    internal abstract fun isComplete(): Boolean

    data class Tap(
        var x: Int? = null,
        var y: Int? = null
    ) : Action() {

        override fun isComplete(): Boolean = (x != null && y != null)

    }

    data class Zoom(
        var x1: Int? = null,
        var y1: Int? = null,
        var x2: Int? = null,
        var y2: Int? = null,
    ) : Action() {

        override fun isComplete(): Boolean = x1 != null && y1 != null && x2 != null && y2 != null
    }

}
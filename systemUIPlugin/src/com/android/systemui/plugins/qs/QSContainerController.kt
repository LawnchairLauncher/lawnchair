package com.android.systemui.plugins.qs

interface QSContainerController {
    fun setCustomizerAnimating(animating: Boolean)

    fun setCustomizerShowing(showing: Boolean) = setCustomizerShowing(showing, 0L)

    fun setCustomizerShowing(showing: Boolean, animationDuration: Long)

    fun setDetailShowing(showing: Boolean)
}

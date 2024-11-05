/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.wm.shell.common.bubbles

import android.graphics.Point
import android.graphics.RectF
import android.view.View
import androidx.annotation.VisibleForTesting
import androidx.core.animation.Animator
import androidx.core.animation.AnimatorListenerAdapter
import androidx.core.animation.ObjectAnimator
import com.android.wm.shell.common.bubbles.BaseBubblePinController.LocationChangeListener
import com.android.wm.shell.common.bubbles.BubbleBarLocation.LEFT
import com.android.wm.shell.common.bubbles.BubbleBarLocation.RIGHT

/**
 * Base class for common logic shared between different bubble views to support pinning bubble bar
 * to left or right edge of screen.
 *
 * Handles drag events and allows a [LocationChangeListener] to be registered that is notified when
 * location of the bubble bar should change.
 *
 * Shows a drop target when releasing a view would update the [BubbleBarLocation].
 */
abstract class BaseBubblePinController(private val screenSizeProvider: () -> Point) {

    private var initialLocationOnLeft = false
    private var onLeft = false
    private var dismissZone: RectF? = null
    private var stuckToDismissTarget = false
    private var screenCenterX = 0
    private var listener: LocationChangeListener? = null
    private var dropTargetAnimator: ObjectAnimator? = null

    /**
     * Signal the controller that dragging interaction has started.
     *
     * @param initialLocationOnLeft side of the screen where bubble bar is pinned to
     */
    fun onDragStart(initialLocationOnLeft: Boolean) {
        this.initialLocationOnLeft = initialLocationOnLeft
        onLeft = initialLocationOnLeft
        screenCenterX = screenSizeProvider.invoke().x / 2
        dismissZone = getExclusionRect()
    }

    /** View has moved to [x] and [y] screen coordinates */
    fun onDragUpdate(x: Float, y: Float) {
        if (dismissZone?.contains(x, y) == true) return

        val wasOnLeft = onLeft
        onLeft = x < screenCenterX
        if (wasOnLeft != onLeft) {
            onLocationChange(if (onLeft) LEFT else RIGHT)
        } else if (stuckToDismissTarget) {
            // Moved out of the dismiss view back to initial side, if we have a drop target, show it
            getDropTargetView()?.apply { animateIn() }
        }
        // Make sure this gets cleared
        stuckToDismissTarget = false
    }

    /** Signal the controller that view has been dragged to dismiss view. */
    fun onStuckToDismissTarget() {
        stuckToDismissTarget = true
        // Notify that location may be reset
        val shouldResetLocation = onLeft != initialLocationOnLeft
        if (shouldResetLocation) {
            onLeft = initialLocationOnLeft
            listener?.onChange(if (onLeft) LEFT else RIGHT)
        }
        getDropTargetView()?.apply {
            animateOut {
                if (shouldResetLocation) {
                    updateLocation(if (onLeft) LEFT else RIGHT)
                }
            }
        }
    }

    /** Signal the controller that dragging interaction has finished. */
    fun onDragEnd() {
        getDropTargetView()?.let { view -> view.animateOut { removeDropTargetView(view) } }
        dismissZone = null
        listener?.onRelease(if (onLeft) LEFT else RIGHT)
    }

    /**
     * [LocationChangeListener] that is notified when dragging interaction has resulted in bubble
     * bar to be pinned on the other edge
     */
    fun setListener(listener: LocationChangeListener?) {
        this.listener = listener
    }

    /** Get width for exclusion rect where dismiss takes over drag */
    protected abstract fun getExclusionRectWidth(): Float
    /** Get height for exclusion rect where dismiss takes over drag */
    protected abstract fun getExclusionRectHeight(): Float

    /** Create the drop target view and attach it to the parent */
    protected abstract fun createDropTargetView(): View

    /** Get the drop target view if it exists */
    protected abstract fun getDropTargetView(): View?

    /** Remove the drop target view */
    protected abstract fun removeDropTargetView(view: View)

    /** Update size and location of the drop target view */
    protected abstract fun updateLocation(location: BubbleBarLocation)

    private fun onLocationChange(location: BubbleBarLocation) {
        showDropTarget(location)
        listener?.onChange(location)
    }

    private fun getExclusionRect(): RectF {
        val rect = RectF(0f, 0f, getExclusionRectWidth(), getExclusionRectHeight())
        // Center it around the bottom center of the screen
        val screenBottom = screenSizeProvider.invoke().y
        rect.offsetTo(screenCenterX - rect.width() / 2, screenBottom - rect.height())
        return rect
    }

    private fun showDropTarget(location: BubbleBarLocation) {
        val targetView = getDropTargetView() ?: createDropTargetView().apply { alpha = 0f }
        if (targetView.alpha > 0) {
            targetView.animateOut {
                updateLocation(location)
                targetView.animateIn()
            }
        } else {
            updateLocation(location)
            targetView.animateIn()
        }
    }

    private fun View.animateIn() {
        dropTargetAnimator?.cancel()
        dropTargetAnimator =
            ObjectAnimator.ofFloat(this, View.ALPHA, 1f)
                .setDuration(DROP_TARGET_ALPHA_IN_DURATION)
                .addEndAction { dropTargetAnimator = null }
        dropTargetAnimator?.start()
    }

    private fun View.animateOut(endAction: Runnable? = null) {
        dropTargetAnimator?.cancel()
        dropTargetAnimator =
            ObjectAnimator.ofFloat(this, View.ALPHA, 0f)
                .setDuration(DROP_TARGET_ALPHA_OUT_DURATION)
                .addEndAction {
                    endAction?.run()
                    dropTargetAnimator = null
                }
        dropTargetAnimator?.start()
    }

    private fun <T : Animator> T.addEndAction(runnable: Runnable): T {
        addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    runnable.run()
                }
            }
        )
        return this
    }

    /** Receive updates on location changes */
    interface LocationChangeListener {
        /**
         * Bubble bar has been dragged to a new [BubbleBarLocation]. And the drag is still in
         * progress.
         *
         * Triggered when drag gesture passes the middle of the screen and before touch up. Can be
         * triggered multiple times per gesture.
         *
         * @param location new location as a result of the ongoing drag operation
         */
        fun onChange(location: BubbleBarLocation) {}

        /**
         * Bubble bar has been released in the [BubbleBarLocation].
         *
         * @param location final location of the bubble bar once drag is released
         */
        fun onRelease(location: BubbleBarLocation)
    }

    companion object {
        @VisibleForTesting const val DROP_TARGET_ALPHA_IN_DURATION = 150L
        @VisibleForTesting const val DROP_TARGET_ALPHA_OUT_DURATION = 100L
    }
}

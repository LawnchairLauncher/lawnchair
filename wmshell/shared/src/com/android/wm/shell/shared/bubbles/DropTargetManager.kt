/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.shared.bubbles

import android.content.Context
import android.graphics.Rect
import android.graphics.RectF
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.core.animation.Animator
import androidx.core.animation.AnimatorListenerAdapter
import androidx.core.animation.ValueAnimator
import com.android.wm.shell.R

/**
 * Manages animating drop targets in response to dragging bubble icons or bubble expanded views
 * across different drag zones.
 */
class DropTargetManager(
    private val context: Context,
    private val container: FrameLayout,
    private val dragZoneChangedListener: DragZoneChangedListener,
) {

    private var state: DragState? = null
    private val dropTargetView = DropTargetView(context)
    private var animator: ValueAnimator? = null
    private var morphRect: RectF = RectF(0f, 0f, 0f, 0f)
    private val isLayoutRtl = container.isLayoutRtl

    private companion object {
        const val MORPH_ANIM_DURATION = 250L
        const val DROP_TARGET_ALPHA_IN_DURATION = 150L
        const val DROP_TARGET_ALPHA_OUT_DURATION = 100L
    }

    /** Must be called when a drag gesture is starting. */
    fun onDragStarted(draggedObject: DraggedObject, dragZones: List<DragZone>) {
        val state = DragState(dragZones, draggedObject)
        dragZoneChangedListener.onInitialDragZoneSet(state.initialDragZone)
        this.state = state
        animator?.cancel()
        setupDropTarget()
    }

    private fun setupDropTarget() {
        if (dropTargetView.parent != null) container.removeView(dropTargetView)
        container.addView(dropTargetView, 0)
        dropTargetView.alpha = 0f
        dropTargetView.elevation = context.resources.getDimension(R.dimen.drop_target_elevation)
        // Match parent and the target is drawn within the view
        dropTargetView.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    }

    /** Called when the user drags to a new location. */
    fun onDragUpdated(x: Int, y: Int) {
        val state = state ?: return
        val oldDragZone = state.currentDragZone
        val newDragZone = state.getMatchingDragZone(x = x, y = y)
        state.currentDragZone = newDragZone
        if (oldDragZone != newDragZone) {
            dragZoneChangedListener.onDragZoneChanged(
                draggedObject = state.draggedObject,
                from = oldDragZone,
                to = newDragZone
            )
            updateDropTarget()
        }
    }

    /** Called when the drag ended. */
    fun onDragEnded() {
        val dropState = state ?: return
        startFadeAnimation(from = dropTargetView.alpha, to = 0f) {
            container.removeView(dropTargetView)
        }
        dragZoneChangedListener.onDragEnded(dropState.currentDragZone)
        state = null
    }

    private fun updateDropTarget() {
        val currentDragZone = state?.currentDragZone ?: return
        val dropTargetBounds = currentDragZone.dropTarget
        when {
            dropTargetBounds == null -> startFadeAnimation(from = dropTargetView.alpha, to = 0f)
            dropTargetView.alpha == 0f -> {
                dropTargetView.update(RectF(dropTargetBounds))
                startFadeAnimation(from = 0f, to = 1f)
            }
            else -> startMorphAnimation(dropTargetBounds)
        }
    }

    private fun startFadeAnimation(from: Float, to: Float, onEnd: (() -> Unit)? = null) {
        animator?.cancel()
        val duration =
            if (from < to) DROP_TARGET_ALPHA_IN_DURATION else DROP_TARGET_ALPHA_OUT_DURATION
        val animator = ValueAnimator.ofFloat(from, to).setDuration(duration)
        animator.addUpdateListener { _ -> dropTargetView.alpha = animator.animatedValue as Float }
        if (onEnd != null) {
            animator.doOnEnd(onEnd)
        }
        this.animator = animator
        animator.start()
    }

    private fun startMorphAnimation(endBounds: Rect) {
        animator?.cancel()
        val startAlpha = dropTargetView.alpha
        val startRect = dropTargetView.getRect()
        val animator = ValueAnimator.ofFloat(0f, 1f).setDuration(MORPH_ANIM_DURATION)
        animator.addUpdateListener { _ ->
            val fraction = animator.animatedValue as Float
            dropTargetView.alpha = startAlpha + (1 - startAlpha) * fraction

            morphRect.left = (startRect.left + (endBounds.left - startRect.left) * fraction)
            morphRect.top = (startRect.top + (endBounds.top - startRect.top) * fraction)
            morphRect.right = (startRect.right + (endBounds.right - startRect.right) * fraction)
            morphRect.bottom = (startRect.bottom + (endBounds.bottom - startRect.bottom) * fraction)
            dropTargetView.update(morphRect)
        }
        this.animator = animator
        animator.start()
    }

    /** Stores the current drag state. */
    private inner class DragState(
        private val dragZones: List<DragZone>,
        val draggedObject: DraggedObject
    ) {
        val initialDragZone =
            if (draggedObject.initialLocation.isOnLeft(isLayoutRtl)) {
                dragZones.filterIsInstance<DragZone.Bubble.Left>().first()
            } else {
                dragZones.filterIsInstance<DragZone.Bubble.Right>().first()
            }
        var currentDragZone: DragZone = initialDragZone

        fun getMatchingDragZone(x: Int, y: Int): DragZone {
            return dragZones.firstOrNull { it.contains(x, y) } ?: currentDragZone
        }
    }

    /** An interface to be notified when drag zones change. */
    interface DragZoneChangedListener {
        /** An initial drag zone was set. Called when a drag starts. */
        fun onInitialDragZoneSet(dragZone: DragZone)

        /** Called when the object was dragged to a different drag zone. */
        fun onDragZoneChanged(draggedObject: DraggedObject, from: DragZone, to: DragZone)

        /** Called when the drag has ended with the zone it ended in. */
        fun onDragEnded(zone: DragZone)
    }

    private fun Animator.doOnEnd(onEnd: () -> Unit) {
        addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd()
                }
            }
        )
    }
}

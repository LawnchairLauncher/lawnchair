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
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.VisibleForTesting
import androidx.core.animation.Animator
import androidx.core.animation.AnimatorListenerAdapter
import androidx.core.animation.ValueAnimator
import com.android.wm.shell.shared.bubbles.DragZone.Bounds.CircleZone
import com.android.wm.shell.shared.bubbles.DragZone.Bounds.RectZone
import com.android.wm.shell.shared.bubbles.DragZone.DropTargetRect
import com.android.wm.shell.shared.bubbles.DraggedObject.Bubble
import com.android.wm.shell.shared.bubbles.DraggedObject.BubbleBar
import com.android.wm.shell.shared.bubbles.DraggedObject.ExpandedView
import com.android.wm.shell.shared.bubbles.DraggedObject.LauncherIcon

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

    @VisibleForTesting val dropTargetView = DropTargetView(context)
    @VisibleForTesting var secondDropTargetView: DropTargetView? = null
    private var morphRect: RectF = RectF(0f, 0f, 0f, 0f)
    private val isLayoutRtl = container.isLayoutRtl
    private val viewAnimatorsMap = mutableMapOf<View, ValueAnimator>()
    private var onDropTargetsRemovedAction: Runnable? = null

    private companion object {
        const val MORPH_ANIM_DURATION = 250L
        const val DROP_TARGET_ALPHA_IN_DURATION = 150L
        const val DROP_TARGET_ALPHA_OUT_DURATION = 100L
        const val DROP_TARGET_ELEVATION_DP = 2f
    }

    /** Must be called when a drag gesture is starting. */
    fun onDragStarted(draggedObject: DraggedObject, dragZones: List<DragZone>) {
        val state = DragState(dragZones, draggedObject)
        dragZoneChangedListener.onInitialDragZoneSet(state.initialDragZone)
        this.state = state
        viewAnimatorsMap.values.forEach { it.cancel() }
        setupDropTarget(dropTargetView)
        if (dragZones.any { it.secondDropTarget != null }) {
            secondDropTargetView = secondDropTargetView ?: DropTargetView(context)
            setupDropTarget(secondDropTargetView)
        } else {
            secondDropTargetView?.let { container.removeView(it) }
            secondDropTargetView = null
        }
    }

    private fun setupDropTarget(view: View?) {
        if (view == null) return
        if (view.parent != null) container.removeView(view)
        view.tag = getDropViewTag()
        container.addView(view, 0)
        view.alpha = 0f

        view.elevation = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            DROP_TARGET_ELEVATION_DP, context.resources.displayMetrics
        )
        // Match parent and the target is drawn within the view
        view.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
    }

    /**
     * Called when the user drags to a new location.
     *
     * @return DragZone that matches provided x and y coordinates.
     */
    fun onDragUpdated(x: Int, y: Int): DragZone? {
        val state = state ?: return null
        val oldDragZone = state.currentDragZone
        val newDragZone = state.getMatchingDragZone(x = x, y = y)
        state.currentDragZone = newDragZone
        if (oldDragZone != newDragZone) {
            dragZoneChangedListener.onDragZoneChanged(
                draggedObject = state.draggedObject,
                from = oldDragZone,
                to = newDragZone,
            )
            updateDropTarget()
        }
        return newDragZone
    }

    /**
     * Called when the drop target views should be hidden. This method will not remove the drop
     * target views from the container.
     *
     * It is mandatory to call [onDragEnded] when the drag operation ends, ensuring that the drop
     * target views are removed from the container.
     */
    fun hideDropTargets() {
        val dragZones = state?.dragZones
        if (dragZones.isNullOrEmpty()) return
        val lowestBottom = dragZones.map { it.bounds }.maxOf { dragZone ->
            when (dragZone) {
                is RectZone -> dragZone.rect.bottom // rect bottom
                is CircleZone -> dragZone.y - dragZone.radius // circle bottom
            }
        }
        // use coordinate that will not hit any drag zone, so drop targets will be hidden
        onDragUpdated(0, lowestBottom + 1)
    }

    /** Called when the drag ended. */
    fun onDragEnded() {
        val dropState = state ?: return
        startFadeAnimation(dropTargetView, to = 0f) {
            container.removeView(dropTargetView)
            onDropTargetRemoved()
        }
        startFadeAnimation(secondDropTargetView, to = 0f) {
            container.removeView(secondDropTargetView)
            secondDropTargetView = null
            onDropTargetRemoved()
        }
        dragZoneChangedListener.onDragEnded(dropState.currentDragZone)
        state = null
    }

    /**
     * Runs the provided action once all drop target views are removed from the container.
     * If there are no drop target views currently present or being animated, the action will be
     * executed immediately.
     */
    fun onDropTargetRemoved(action: Runnable) {
        onDropTargetsRemovedAction = action
        onDropTargetRemoved()
    }

    private fun updateDropTarget() {
        val dropState = state ?: return
        val currentDragZone = dropState.currentDragZone
        if (currentDragZone == null) {
            startFadeAnimation(dropTargetView, to = 0f)
            startFadeAnimation(secondDropTargetView, to = 0f)
            return
        }
        val dropTargetRect = currentDragZone.dropTarget
        when {
            dropTargetRect == null -> startFadeAnimation(dropTargetView, to = 0f)

            dropTargetView.alpha == 0f -> {
                dropTargetView.update(RectF(dropTargetRect.rect), dropTargetRect.cornerRadius)
                startFadeAnimation(dropTargetView, to = 1f)
            }

            else -> startMorphAnimation(dropTargetRect)
        }

        val secondDropTargetRect = currentDragZone.secondDropTarget
        when {
            secondDropTargetRect == null -> startFadeAnimation(secondDropTargetView, to = 0f)
            else -> {
                val secondDropTargetView = secondDropTargetView ?: return
                secondDropTargetView.update(
                    RectF(secondDropTargetRect.rect),
                    secondDropTargetRect.cornerRadius,
                )
                startFadeAnimation(secondDropTargetView, to = 1f)
            }
        }
    }

    private fun startFadeAnimation(view: View?, to: Float, onEnd: (() -> Unit)? = null) {
        if (view == null) return
        val from = view.alpha
        viewAnimatorsMap[view]?.cancel()
        val duration =
            if (from < to) DROP_TARGET_ALPHA_IN_DURATION else DROP_TARGET_ALPHA_OUT_DURATION
        val animator = ValueAnimator.ofFloat(from, to).setDuration(duration)
        animator.addUpdateListener { _ -> view.alpha = animator.animatedValue as Float }
        if (onEnd != null) {
            animator.doOnEnd(onEnd)
        }
        viewAnimatorsMap[view] = animator
        animator.start()
    }

    private fun startMorphAnimation(dropTargetRect: DropTargetRect) {
        viewAnimatorsMap[dropTargetView]?.cancel()
        val startAlpha = dropTargetView.alpha
        val startRect = dropTargetView.getRect()
        val endRect = dropTargetRect.rect
        val animator = ValueAnimator.ofFloat(0f, 1f).setDuration(MORPH_ANIM_DURATION)
        animator.addUpdateListener { _ ->
            val fraction = animator.animatedValue as Float
            dropTargetView.alpha = startAlpha + (1 - startAlpha) * fraction

            morphRect.left = (startRect.left + (endRect.left - startRect.left) * fraction)
            morphRect.top = (startRect.top + (endRect.top - startRect.top) * fraction)
            morphRect.right = (startRect.right + (endRect.right - startRect.right) * fraction)
            morphRect.bottom = (startRect.bottom + (endRect.bottom - startRect.bottom) * fraction)
            dropTargetView.update(morphRect, dropTargetRect.cornerRadius)
        }
        viewAnimatorsMap[dropTargetView] = animator
        animator.start()
    }

    private fun onDropTargetRemoved() {
        val action = onDropTargetsRemovedAction ?: return
        if ((0 until container.childCount).any {
                val childView = container.getChildAt(it)
                childView is DropTargetView && childView.tag == getDropViewTag()
            }) {
            return
        }
        onDropTargetsRemovedAction = null
        action.run()
    }

    /** Stores the current drag state. */
    private inner class DragState(
        val dragZones: List<DragZone>,
        val draggedObject: DraggedObject,
    ) {
        val initialDragZone =
            draggedObject.initialLocation?.let {
                if (it.isOnLeft(isLayoutRtl)) {
                    dragZones.filterIsInstance<DragZone.Bubble.Left>().first()
                } else {
                    dragZones.filterIsInstance<DragZone.Bubble.Right>().first()
                }
            }
        var currentDragZone: DragZone? = initialDragZone

        fun getMatchingDragZone(x: Int, y: Int): DragZone? {
            return dragZones.firstOrNull { it.contains(x, y) }
        }
    }

    private fun getDropViewTag(): Int = this.hashCode()

    private val DraggedObject.initialLocation: BubbleBarLocation?
        get() =
            when (this) {
                is Bubble -> initialLocation
                is BubbleBar -> initialLocation
                is ExpandedView -> initialLocation
                is LauncherIcon -> null
            }

    /** An interface to be notified when drag zones change. */
    interface DragZoneChangedListener {
        /** An initial drag zone was set. Called when a drag starts. */
        fun onInitialDragZoneSet(dragZone: DragZone?)

        /** Called when the object was dragged to a different drag zone. */
        fun onDragZoneChanged(draggedObject: DraggedObject, from: DragZone?, to: DragZone?)

        /** Called when the drag has ended with the zone it ended in. */
        fun onDragEnded(zone: DragZone?)
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

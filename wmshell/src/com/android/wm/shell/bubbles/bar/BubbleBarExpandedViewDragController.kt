/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.bubbles.bar

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.annotation.VisibleForTesting
import com.android.wm.shell.R
import com.android.wm.shell.bubbles.BubblePositioner
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import com.android.wm.shell.shared.bubbles.DismissView
import com.android.wm.shell.shared.bubbles.DragZoneFactory
import com.android.wm.shell.shared.bubbles.DraggedObject
import com.android.wm.shell.shared.bubbles.DropTargetManager
import com.android.wm.shell.shared.bubbles.RelativeTouchListener
import com.android.wm.shell.shared.magnetictarget.MagnetizedObject

/** Controller for handling drag interactions with [BubbleBarExpandedView] */
@SuppressLint("ClickableViewAccessibility")
class BubbleBarExpandedViewDragController(
    private val context: Context,
    private val expandedView: BubbleBarExpandedView,
    private val dismissView: DismissView,
    private val animationHelper: BubbleBarAnimationHelper,
    private val bubblePositioner: BubblePositioner,
    private val pinController: BubbleExpandedViewPinController,
    private val dropTargetManager: DropTargetManager?,
    private val dragZoneFactory: DragZoneFactory?,
    @get:VisibleForTesting val dragListener: DragListener,
) {

    var isStuckToDismiss: Boolean = false
        private set

    var isDragged: Boolean = false
        private set

    private var expandedViewInitialTranslationX = 0f
    private var expandedViewInitialTranslationY = 0f
    private val magnetizedExpandedView: MagnetizedObject<BubbleBarExpandedView> =
        MagnetizedObject.magnetizeView(expandedView)
    private val magnetizedDismissTarget: MagnetizedObject.MagneticTarget

    private val draggedBubbleElevation: Float

    init {
        magnetizedExpandedView.magnetListener = MagnetListener()
        magnetizedExpandedView.animateStuckToTarget =
            {
                target: MagnetizedObject.MagneticTarget,
                _: Float,
                _: Float,
                _: Boolean,
                after: (() -> Unit)? ->
                animationHelper.animateIntoTarget(target, after)
            }

        magnetizedDismissTarget =
            MagnetizedObject.MagneticTarget(dismissView.circle, dismissView.circle.width)
        magnetizedExpandedView.addTarget(magnetizedDismissTarget)

        draggedBubbleElevation = context.resources.getDimension(
            R.dimen.dragged_bubble_elevation)
        val dragMotionEventHandler = HandleDragListener()

        expandedView.handleView.setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                expandedViewInitialTranslationX = expandedView.translationX
                expandedViewInitialTranslationY = expandedView.translationY
            }
            val magnetConsumed = magnetizedExpandedView.maybeConsumeMotionEvent(event)
            // Move events can be consumed by the magnetized object
            if (event.actionMasked == MotionEvent.ACTION_MOVE && magnetConsumed) {
                return@setOnTouchListener true
            }
            return@setOnTouchListener dragMotionEventHandler.onTouch(view, event) || magnetConsumed
        }
    }

    /** Listener to get notified about drag events */
    interface DragListener {
        /**
         * Bubble bar was released
         *
         * @param inDismiss `true` if view was release in dismiss target
         */
        fun onReleased(inDismiss: Boolean)
    }

    private inner class HandleDragListener : RelativeTouchListener() {

        private var isMoving = false

        override fun onDown(v: View, ev: MotionEvent): Boolean {
            // While animating, don't allow new touch events
            if (expandedView.isAnimating) return false
            expandedView.z = draggedBubbleElevation
            if (dropTargetManager != null && dragZoneFactory != null) {
                val draggedObject = DraggedObject.ExpandedView(
                    if (bubblePositioner.isBubbleBarOnLeft) {
                        BubbleBarLocation.LEFT
                    } else {
                        BubbleBarLocation.RIGHT
                    }
                )
                dropTargetManager.onDragStarted(
                    draggedObject,
                    dragZoneFactory.createSortedDragZones(draggedObject)
                )
            } else {
                pinController.onDragStart(bubblePositioner.isBubbleBarOnLeft)
            }
            isDragged = true
            return true
        }

        override fun onMove(
            v: View,
            ev: MotionEvent,
            viewInitialX: Float,
            viewInitialY: Float,
            dx: Float,
            dy: Float,
        ) {
            if (!isMoving) {
                isMoving = true
                animationHelper.animateStartDrag()
            }
            expandedView.translationX = expandedViewInitialTranslationX + dx
            expandedView.translationY = expandedViewInitialTranslationY + dy
            dismissView.show()
            if (dropTargetManager != null) {
                dropTargetManager.onDragUpdated(ev.rawX.toInt(), ev.rawY.toInt())
            } else {
                pinController.onDragUpdate(ev.rawX, ev.rawY)
            }
        }

        override fun onUp(
            v: View,
            ev: MotionEvent,
            viewInitialX: Float,
            viewInitialY: Float,
            dx: Float,
            dy: Float,
            velX: Float,
            velY: Float,
        ) {
            v.translationZ = 0f
            finishDrag()
        }

        override fun onCancel(v: View, ev: MotionEvent, viewInitialX: Float, viewInitialY: Float) {
            isStuckToDismiss = false
            v.translationZ = 0f
            finishDrag()
        }

        private fun finishDrag() {
            if (!isStuckToDismiss) {
                if (dropTargetManager != null) {
                    dropTargetManager.onDragEnded()
                } else {
                    pinController.onDragEnd()
                }
                dragListener.onReleased(inDismiss = false)
                animationHelper.animateToRestPosition()
                dismissView.hide()
            }
            isMoving = false
            isDragged = false
        }
    }

    private inner class MagnetListener : MagnetizedObject.MagnetListener {
        override fun onStuckToTarget(
            target: MagnetizedObject.MagneticTarget,
            draggedObject: MagnetizedObject<*>,
        ) {
            isStuckToDismiss = true
            pinController.onStuckToDismissTarget()
        }

        override fun onUnstuckFromTarget(
            target: MagnetizedObject.MagneticTarget,
            draggedObject: MagnetizedObject<*>,
            velX: Float,
            velY: Float,
            wasFlungOut: Boolean,
        ) {
            isStuckToDismiss = false
            animationHelper.animateUnstuckFromDismissView(target)
        }

        override fun onReleasedInTarget(
            target: MagnetizedObject.MagneticTarget,
            draggedObject: MagnetizedObject<*>,
        ) {
            dragListener.onReleased(inDismiss = true)
            if (dropTargetManager != null) {
                dropTargetManager.onDragEnded()
            } else {
                pinController.onDragEnd()
            }
            dismissView.hide()
        }
    }
}

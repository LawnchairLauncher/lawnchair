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

package com.android.launcher3.popup

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.DragSource
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.R
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.ShortcutUtil
import com.android.launcher3.views.ActivityContext

/**
 * Base popup container for shortcuts associated with the item {@code originalView}
 *
 * @param <T> The activity on which the popup shows </T>
 * @param context The context in which the popup is created.
 * @param originalView The view from which this popup was opened.
 */
open class PopupContainer<T>(context: Context?, val originalView: View, val itemInfo: ItemInfo) :
    ArrowPopup<T>(context), DragSource, DragController.DragListener, Popup
    where T : Context, T : ActivityContext {
    /** Here we hold the system shortcuts that we show for the Popup. */
    // TODO b/441320297
    var systemShortcutContainer: ViewGroup? = null

    /** If the distance the user drags surpasses this number, then we should start drag. */
    val startDragThreshold =
        originalView.context.resources.getDimensionPixelSize(
            R.dimen.deep_shortcuts_start_drag_threshold
        )

    @CallSuper
    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val dl = popupContainer
            if (!dl.isEventOverView(this, ev)) {
                // TODO: add WW log if want to log if tap closed deep shortcut container.
                close(true)

                // We let touches on the original view go through so that users can launch
                // the item with one tap.
                return !dl.isEventOverView(originalView, ev)
            }
        }
        return false
    }

    @CallSuper
    override fun getTargetObjectLocation(outPos: Rect) {
        popupContainer.getDescendantRectRelativeToSelf(originalView, outPos)
        outPos.top += originalView.paddingTop
        outPos.left += originalView.paddingLeft
        outPos.right -= originalView.paddingRight
        outPos.bottom = outPos.top + originalView.height
    }

    override fun isOfType(type: Int): Boolean {
        return (type and AbstractFloatingView.TYPE_ACTION_POPUP) != 0
    }

    @CallSuper
    override fun onDragStart(dragObject: DragObject, options: DragOptions) {
        // Either the original item or one of the shortcuts was dragged.
        // Hide the container, but don't remove it yet because that interferes with touch events.
        mDeferContainerRemoval = true
        animateClose()
    }

    override fun onDropCompleted(target: View, d: DragObject, success: Boolean) {}

    @CallSuper
    override fun onDragEnd() {
        if (!isOpen) {
            if (mOpenCloseAnimator != null) {
                // Close animation is running.
                mDeferContainerRemoval = false
            } else {
                // Close animation is not running.
                if (mDeferContainerRemoval) {
                    closeComplete()
                }
            }
        }
    }

    /**
     * Determines when the deferred drag should be started.
     *
     * Current behavior:
     * - Start the drag if the touch passes a certain distance from the original touch down.
     */
    override fun createPreDragCondition(): DragOptions.PreDragCondition {
        return object : DragOptions.PreDragCondition {
            override fun shouldStartDrag(distanceDragged: Double): Boolean {
                return distanceDragged > startDragThreshold
            }

            override fun onPreDragStart(dragObject: DragObject) {}

            override fun onPreDragEnd(dragObject: DragObject, dragStarted: Boolean) {}
        }
    }

    companion object {
        /** Returns a PopupContainer which is already open or null */
        @JvmStatic
        fun <T> getOpen(context: T): PopupContainer<*>? where T : Context?, T : ActivityContext? {
            return getOpenView(context, TYPE_ACTION_POPUP)
        }

        /** Dismisses the popup if it is no longer valid */
        @JvmStatic
        fun <T> dismissInvalidPopup(activity: T) where T : Context?, T : ActivityContext? {
            val popup = getOpen(activity)
            val originalView = popup?.originalView
            if (
                originalView != null &&
                    (!originalView.isAttachedToWindow ||
                        !ShortcutUtil.supportsShortcuts(popup?.itemInfo))
            ) {
                popup.animateClose()
            }
        }

        /**
         * Creates a new instance of [PopupContainer].
         *
         * @param context The context in which the popup will be created.
         * @param originalView The view that the popup is associated with.
         * @return A new instance of [PopupContainer].
         */
        @JvmStatic
        fun <T> create(context: Context, originalView: View, itemInfo: ItemInfo): PopupContainer<T>
            where T : Context, T : ActivityContext {
            val container = PopupContainer<T>(context, originalView, itemInfo)
            container.id = R.id.popup_container
            container.clipChildren = false
            container.clipToPadding = false
            container.orientation = VERTICAL
            container.layoutParams =
                LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            return container
        }
    }
}

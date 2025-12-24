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

package com.android.wm.shell.windowdecor

import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Region
import android.os.UserHandle
import android.view.Display
import android.view.InsetsState
import android.view.MotionEvent
import android.view.SurfaceControl
import android.view.View.OnClickListener
import android.view.View.OnGenericMotionListener
import android.view.View.OnLongClickListener
import android.view.View.OnTouchListener
import android.window.DesktopExperienceFlags
import android.window.WindowContainerTransaction
import com.android.wm.shell.windowdecor.DragPositioningCallbackUtility.DragEventListener
import com.android.wm.shell.windowdecor.DragResizeWindowGeometry.DisabledEdge
import com.android.wm.shell.windowdecor.common.ExclusionRegionListener

/**
 * Temporary class that acts as an interface between three captions:
 * - The new [DefaultWindowDecoration] which is used when
 *   [DesktopExperienceFlags.ENABLE_WINDOW_DECORATION_REFACTOR] is true
 * - The old [DesktopWindowDecoration]
 * - The [CaptionWindowDecoration]
 *
 * TODO: b/409648813 - Remove class and call decorations directly once refactor is enabled.
 */
class WindowDecorationWrapper
private constructor(
    private val desktopWindowDecor: DesktopModeWindowDecoration? = null,
    private val defaultWindowDecor: DefaultWindowDecoration? = null,
    private val captionWindowDecoration: CaptionWindowDecoration? = null,
) {
    private fun requireDefaultWindowDecor(): DefaultWindowDecoration =
        checkNotNull(defaultWindowDecor) { "expected non-null default window decor" }

    private fun requireDesktopWindowDecor(): DesktopModeWindowDecoration =
        checkNotNull(desktopWindowDecor) { "expected non-null desktop window decor" }

    private fun requireCaptionWindowDecor(): CaptionWindowDecoration =
        checkNotNull(captionWindowDecoration) { "expected non-null caption window decor" }

    /** Returns the window decor's [RunningTaskInfo]. */
    val taskInfo: RunningTaskInfo
        get() =
            when {
                defaultWindowDecor != null -> requireDefaultWindowDecor().taskInfo
                desktopWindowDecor != null -> requireDesktopWindowDecor().mTaskInfo
                captionWindowDecoration != null -> requireCaptionWindowDecor().mTaskInfo
                else -> error("Expected Non-null window decoration")
            }

    /** Returns the [SurfaceControl] of the task associated with the window decor. */
    val taskSurface: SurfaceControl
        get() =
            when {
                defaultWindowDecor != null -> requireDefaultWindowDecor().taskSurface
                desktopWindowDecor != null -> requireDesktopWindowDecor().leash
                captionWindowDecoration != null -> requireCaptionWindowDecor().leash
                else -> error("Expected Non-null window decoration")
            }

    /** Returns the [user] associated with the window decor. */
    val user: UserHandle
        get() =
            when {
                defaultWindowDecor != null -> requireDefaultWindowDecor().user
                desktopWindowDecor != null -> requireDesktopWindowDecor().user
                captionWindowDecoration != null -> requireCaptionWindowDecor().mUserContext.user
                else -> error("Expected Non-null default or desktop window decoration")
            }

    /** Returns the exclusion region of the window decor's display. */
    val exclusionRegion: Region
        get() =
            when {
                defaultWindowDecor != null -> requireDefaultWindowDecor().exclusionRegion
                desktopWindowDecor != null -> requireDesktopWindowDecor().mExclusionRegion
                captionWindowDecoration != null -> requireCaptionWindowDecor().mExclusionRegion
                else -> error("Expected Non-null window decoration")
            }

    /** Returns if the task associated with the window decor has global focus. */
    val hasGlobalFocus: Boolean
        get() =
            when {
                defaultWindowDecor != null -> requireDefaultWindowDecor().hasGlobalFocus
                desktopWindowDecor != null -> requireDesktopWindowDecor().mHasGlobalFocus
                captionWindowDecoration != null -> requireCaptionWindowDecor().mHasGlobalFocus
                else -> error("Expected Non-null window decoration")
            }

    /** Returns the [Display] the window decor is on. */
    val display: Display?
        get() =
            when {
                defaultWindowDecor != null -> requireDefaultWindowDecor().display
                desktopWindowDecor != null -> requireDesktopWindowDecor().mDisplay
                captionWindowDecoration != null -> requireCaptionWindowDecor().mDisplay
                else -> error("Expected Non-null default or desktop window decoration")
            }

    /** Returns the [Context] of the window decor. */
    val decorWindowContext: Context
        get() =
            when {
                defaultWindowDecor != null -> requireDefaultWindowDecor().decorWindowContext
                desktopWindowDecor != null -> requireDesktopWindowDecor().mDecorWindowContext
                captionWindowDecoration != null -> requireCaptionWindowDecor().mDecorWindowContext
                else -> error("Expected Non-null window decoration")
            }

    /** Returns the system's [Context]. */
    val context: Context
        get() =
            when {
                defaultWindowDecor != null -> requireDefaultWindowDecor().context
                desktopWindowDecor != null -> requireDesktopWindowDecor().mContext
                captionWindowDecoration != null -> requireCaptionWindowDecor().mContext
                else -> error("Expected Non-null default or desktop window decoration")
            }

    /** Returns if the task associated with the window decor is focused. */
    val isFocused: Boolean
        get() =
            when {
                defaultWindowDecor != null -> requireDefaultWindowDecor().hasGlobalFocus
                desktopWindowDecor != null -> requireDesktopWindowDecor().isFocused
                captionWindowDecoration != null -> requireCaptionWindowDecor().mHasGlobalFocus
                else -> error("Expected Non-null default or desktop window decoration")
            }

    val maximizeMenuController: MaximizeMenuController?
        get() =
            when {
                defaultWindowDecor != null -> requireDefaultWindowDecor().maximizeMenuController
                desktopWindowDecor != null -> requireDesktopWindowDecor()
                else -> error("Expected Non-null default or desktop window decoration")
            }

    val manageWindowsMenuController: ManageWindowsMenuController?
        get() =
            when {
                defaultWindowDecor != null ->
                    requireDefaultWindowDecor().manageWindowsMenuController
                desktopWindowDecor != null -> requireDesktopWindowDecor()
                else -> error("Expected Non-null default or desktop window decoration")
            }

    val handleMenuController: HandleMenuController?
        get() =
            when {
                defaultWindowDecor != null -> requireDefaultWindowDecor().handleMenuController
                desktopWindowDecor != null -> requireDesktopWindowDecor()
                else -> error("Expected Non-null default or desktop window decoration")
            }

    /**
     * Returns the valid drag area for this task associated with the window decor if the task can be
     * dragged. Otherwise, returns null.
     */
    fun getValidDragArea() =
        when {
            defaultWindowDecor != null -> requireDefaultWindowDecor().lastValidDragArea
            desktopWindowDecor != null -> requireDesktopWindowDecor().lastValidDragArea
            captionWindowDecoration != null -> requireCaptionWindowDecor().calculateValidDragArea()
            else -> error("Expected Non-null window decoration")
        }

    /** Updates all window decorations, including any existing caption. */
    fun relayout(taskInfo: RunningTaskInfo, isFocused: Boolean, exclusionRegion: Region) =
        when {
            defaultWindowDecor != null -> {
                requireDefaultWindowDecor().relayout(taskInfo, isFocused, exclusionRegion)
            }

            desktopWindowDecor != null -> {
                requireDesktopWindowDecor().relayout(taskInfo, isFocused, exclusionRegion)
            }

            captionWindowDecoration != null -> {
                requireCaptionWindowDecor().relayout(taskInfo, isFocused, exclusionRegion)
            }

            else -> error("Expected Non-null window decoration")
        }

    /** Updates all window decorations, including any existing caption. */
    fun relayout(
        taskInfo: RunningTaskInfo,
        startT: SurfaceControl.Transaction,
        finishT: SurfaceControl.Transaction,
        applyStartTransactionOnDraw: Boolean,
        shouldSetTaskVisibilityPositionAndCrop: Boolean,
        hasGlobalFocus: Boolean,
        displayExclusionRegion: Region,
        inSyncWithTransition: Boolean,
        taskSurface: SurfaceControl?,
    ) =
        when {
            defaultWindowDecor != null -> {
                requireDefaultWindowDecor()
                    .relayout(
                        taskInfo,
                        startT,
                        finishT,
                        applyStartTransactionOnDraw,
                        shouldSetTaskVisibilityPositionAndCrop,
                        hasGlobalFocus,
                        displayExclusionRegion,
                        inSyncWithTransition,
                        taskSurface,
                    )
            }

            desktopWindowDecor != null -> {
                requireDesktopWindowDecor()
                    .relayout(
                        taskInfo,
                        startT,
                        finishT,
                        applyStartTransactionOnDraw,
                        shouldSetTaskVisibilityPositionAndCrop,
                        hasGlobalFocus,
                        displayExclusionRegion,
                        inSyncWithTransition,
                        taskSurface,
                    )
            }

            captionWindowDecoration != null -> {
                requireCaptionWindowDecor()
                    .relayout(
                        taskInfo,
                        startT,
                        finishT,
                        applyStartTransactionOnDraw,
                        shouldSetTaskVisibilityPositionAndCrop,
                        hasGlobalFocus,
                        displayExclusionRegion,
                        inSyncWithTransition,
                    )
            }

            else -> error("Expected Non-null window decoration")
        }

    /**
     * To be called when exclusion region is changed to allow the window decor to be updated
     * accordingly.
     */
    fun onExclusionRegionChanged(exclusionRegion: Region) =
        when {
            defaultWindowDecor != null -> {
                requireDefaultWindowDecor().onExclusionRegionChanged(exclusionRegion)
            }

            desktopWindowDecor != null -> {
                requireDesktopWindowDecor().onExclusionRegionChanged(exclusionRegion)
            }

            captionWindowDecoration != null -> {
                requireCaptionWindowDecor().onExclusionRegionChanged(exclusionRegion)
            }

            else -> error("Expected Non-null window decoration")
        }

    /** Declares whether a Recents transition is currently active. */
    fun setIsRecentsTransitionRunning(isRecentsRunning: Boolean) =
        when {
            defaultWindowDecor != null -> {
                requireDefaultWindowDecor().isRecentsTransitionRunning = isRecentsRunning
            }

            desktopWindowDecor != null -> {
                requireDesktopWindowDecor().setIsRecentsTransitionRunning(isRecentsRunning)
            }

            else -> error("Expected Non-null default or desktop window decoration")
        }

    /** Closes the window decoration. */
    fun close() =
        when {
            defaultWindowDecor != null -> requireDefaultWindowDecor().close()
            desktopWindowDecor != null -> requireDesktopWindowDecor().close()
            captionWindowDecoration != null -> requireCaptionWindowDecor().close()
            else -> error("Expected Non-null window decoration")
        }

    /** Adds inset for caption if one exists. */
    fun addCaptionInset(wct: WindowContainerTransaction) =
        when {
            defaultWindowDecor != null -> requireDefaultWindowDecor().addCaptionInset(wct)
            desktopWindowDecor != null -> requireDesktopWindowDecor().addCaptionInset(wct)
            else -> error("Expected Non-null default or desktop window decoration")
        }

    /** Dispose of the view used to forward inputs in status bar region. */
    fun disposeStatusBarInputLayer() =
        when {
            desktopWindowDecor != null -> requireDesktopWindowDecor().disposeStatusBarInputLayer()
            defaultWindowDecor != null -> {} // No-op
            else -> error("Expected Non-null default or desktop window decoration")
        }

    /** Returns [true] if [dragResizeListener] is handling the motion event. */
    fun isHandlingDragResize() =
        when {
            defaultWindowDecor != null -> {
                requireDefaultWindowDecor().isHandlingDragResize()
            }

            desktopWindowDecor != null -> {
                requireDesktopWindowDecor().isHandlingDragResize
            }

            captionWindowDecoration != null -> {
                requireCaptionWindowDecor().isHandlingDragResize
            }

            else -> error("Expected Non-null window decoration")
        }

    /** Checks if touch event occurred in caption's customizable region. */
    fun checkTouchEventInCustomizableRegion(e: MotionEvent) =
        when {
            defaultWindowDecor != null -> {
                requireDefaultWindowDecor().checkTouchEventInCustomizableRegion(e)
            }

            desktopWindowDecor != null -> {
                requireDesktopWindowDecor().checkTouchEventInCustomizableRegion(e)
            }

            captionWindowDecoration != null -> {
                requireCaptionWindowDecor().checkTouchEventInCustomizableRegion(e)
            }

            else -> error("Expected Non-null window decoration")
        }

    /** Returns [true] if [dragResizeListener] should handle the motion event. */
    fun shouldResizeListenerHandleEvent(e: MotionEvent, offset: Point) =
        when {
            defaultWindowDecor != null -> {
                requireDefaultWindowDecor().shouldResizeListenerHandleEvent(e, offset)
            }

            desktopWindowDecor != null -> {
                requireDesktopWindowDecor().shouldResizeListenerHandleEvent(e, offset)
            }

            captionWindowDecoration != null -> {
                requireCaptionWindowDecor().shouldResizeListenerHandleEvent(e, offset)
            }

            else -> error("Expected Non-null window decoration")
        }

    /** Updates whether the task is being dragged. */
    fun setIsDragging(dragging: Boolean) =
        when {
            defaultWindowDecor != null -> {
                requireDefaultWindowDecor().isDragging = dragging
            }

            desktopWindowDecor != null -> {
                requireDesktopWindowDecor().setIsDragging(dragging)
            }

            else -> error("Expected Non-null default or desktop window decoration")
        }

    /**
     * Check if touch event occurred in caption when caption is unable to receive touch events (i.e.
     * when caption is behind the status bar).
     */
    fun checkTouchEventInCaption(e: MotionEvent) =
        when {
            defaultWindowDecor != null -> requireDefaultWindowDecor().checkTouchEventInCaption(e)
            desktopWindowDecor != null -> requireDesktopWindowDecor().checkTouchEventInCaption(e)
            else -> error("Expected Non-null default or desktop window decoration")
        }

    /** Request direct a11y focus on the maximize button */
    fun requestFocusMaximizeButton() =
        when {
            defaultWindowDecor != null -> requireDefaultWindowDecor().a11yFocusMaximizeButton()
            desktopWindowDecor != null -> requireDesktopWindowDecor().a11yFocusMaximizeButton()
            else -> error("Expected Non-null default or desktop window decoration")
        }

    /**
     * Updates hover and pressed status of views in this decoration. Should only be called when
     * status cannot be updated normally.
     */
    fun updateHoverAndPressStatus(e: MotionEvent) =
        when {
            defaultWindowDecor != null -> {} // No-op
            desktopWindowDecor != null -> requireDesktopWindowDecor().updateHoverAndPressStatus(e)
            else -> error("Expected Non-null default or desktop window decoration")
        }

    /** Handles a interruption to a drag event. */
    fun handleDragInterrupted() =
        when {
            defaultWindowDecor != null -> requireDefaultWindowDecor().handleDragInterrupted()
            desktopWindowDecor != null -> requireDesktopWindowDecor().handleDragInterrupted()
            else -> error("Expected Non-null default or desktop window decoration")
        }

    /** Returns true if task resize or reposition is currently being animated. */
    fun setAnimatingTaskResizeOrReposition(animatingTaskResizeOrReposition: Boolean) =
        when {
            defaultWindowDecor != null -> {
                requireDefaultWindowDecor()
                    .setAnimatingTaskResizeOrReposition(animatingTaskResizeOrReposition)
            }

            desktopWindowDecor != null -> {
                requireDesktopWindowDecor()
                    .setAnimatingTaskResizeOrReposition(animatingTaskResizeOrReposition)
            }

            else -> error("Expected Non-null default or desktop window decoration")
        }

    /** Show the resize veil. */
    fun showResizeVeil(t: SurfaceControl.Transaction, bounds: Rect) =
        when {
            defaultWindowDecor != null -> {
                requireDefaultWindowDecor().showResizeVeil(t, bounds)
            }

            desktopWindowDecor != null -> {
                requireDesktopWindowDecor().showResizeVeil(t, bounds)
            }

            else -> error("Expected Non-null default or desktop window decoration")
        }

    /** Show the resize veil. */
    fun showResizeVeil(bounds: Rect) =
        when {
            defaultWindowDecor != null -> requireDefaultWindowDecor().showResizeVeil(bounds)
            desktopWindowDecor != null -> requireDesktopWindowDecor().showResizeVeil(bounds)
            else -> error("Expected Non-null default or desktop window decoration")
        }

    /** Set new bounds for the resize veil */
    fun updateResizeVeil(t: SurfaceControl.Transaction, bounds: Rect) =
        when {
            defaultWindowDecor != null -> requireDefaultWindowDecor().updateResizeVeil(t, bounds)
            desktopWindowDecor != null -> requireDesktopWindowDecor().updateResizeVeil(t, bounds)
            else -> error("Expected Non-null default or desktop window decoration")
        }

    /** Set new bounds for the resize veil */
    fun updateResizeVeil(bounds: Rect) =
        when {
            defaultWindowDecor != null -> requireDefaultWindowDecor().updateResizeVeil(bounds)
            desktopWindowDecor != null -> requireDesktopWindowDecor().updateResizeVeil(bounds)
            else -> error("Expected Non-null default or desktop window decoration")
        }

    /** Fades the resize veil out. */
    fun hideResizeVeil() =
        when {
            defaultWindowDecor != null -> requireDefaultWindowDecor().hideResizeVeil()
            desktopWindowDecor != null -> requireDesktopWindowDecor().hideResizeVeil()
            else -> error("Expected Non-null default or desktop window decoration")
        }

    /** Updates the window decorations when keyguard visibility changes. */
    fun onKeyguardStateChanged(visible: Boolean, occluded: Boolean) =
        when {
            defaultWindowDecor != null -> {
                requireDefaultWindowDecor().onKeyguardStateChanged(visible, occluded)
            }

            desktopWindowDecor != null -> {
                requireDesktopWindowDecor().onKeyguardStateChanged(visible, occluded)
            }

            else -> error("Expected Non-null default or desktop window decoration")
        }

    /** Updates the window decoration on status bar visibility changed. */
    fun onInsetsStateChanged(insetsState: InsetsState) =
        when {
            defaultWindowDecor != null -> {
                requireDefaultWindowDecor().onInsetsStateChanged(insetsState)
            }

            desktopWindowDecor != null -> {
                requireDesktopWindowDecor().onInsetsStateChanged(insetsState)
            }

            else -> error("Expected Non-null default or desktop window decoration")
        }

    /**
     * Check a passed MotionEvent if it has occurred on any button related to this decor. Note this
     * should only be called when a regular onClick is not possible (i.e. the button was clicked
     * through status bar layer)
     */
    fun checkTouchEvent(e: MotionEvent) =
        when {
            defaultWindowDecor != null -> {} // No-op
            desktopWindowDecor != null -> requireDesktopWindowDecor().checkTouchEvent(e)
            else -> error("Expected Non-null default or desktop window decoration")
        }

    /**
     * Checks if motion event occurs in the caption handle area of a focused caption (the caption on
     * a task in fullscreen or in multi-windowing mode). This should be used in cases where
     * onTouchListener will not work (i.e. when caption is in status bar area).
     */
    fun checkTouchEventInFocusedCaptionHandle(e: MotionEvent) =
        when {
            defaultWindowDecor != null -> {
                false
            }

            desktopWindowDecor != null -> {
                requireDesktopWindowDecor().checkTouchEventInFocusedCaptionHandle(e)
            }

            else -> error("Expected Non-null default or desktop window decoration")
        }

    /** Adds the [dragResizeListener] which gets notified on the task being drag resized. */
    fun addDragResizeListener(listener: DragEventListener) =
        when {
            defaultWindowDecor != null -> {
                requireDefaultWindowDecor().addDragResizeListener(listener)
            }

            desktopWindowDecor != null -> {
                requireDesktopWindowDecor().addDragResizeListener(listener)
            }

            else -> error("Expected Non-null default or desktop window decoration")
        }

    /**
     * Disables resizing for the [disabledResizingEdge]. Executes immediately or delays if
     * [shouldDelayUpdate].
     */
    fun updateDisabledResizingEdge(disabledResizingEdge: DisabledEdge, shouldDelayUpdate: Boolean) =
        when {
            defaultWindowDecor != null -> {
                requireDefaultWindowDecor()
                    .updateDisabledResizingEdge(disabledResizingEdge, shouldDelayUpdate)
            }

            desktopWindowDecor != null -> {
                requireDesktopWindowDecor()
                    .updateDisabledResizingEdge(disabledResizingEdge, shouldDelayUpdate)
            }

            else -> error("Expected Non-null default or desktop window decoration")
        }

    /** Removes the [dragResizeListener] if previously added. */
    fun removeDragResizeListener(listener: DragEventListener) =
        when {
            defaultWindowDecor != null -> {
                requireDefaultWindowDecor().removeDragResizeListener(listener)
            }

            desktopWindowDecor != null -> {
                requireDesktopWindowDecor().removeDragResizeListener(listener)
            }

            else -> error("Expected Non-null default or desktop window decoration")
        }

    /** Sets the [TaskDragResizer] which allows task to be drag-resized. */
    fun setTaskDragResizer(taskPositioner: TaskPositioner) =
        when {
            defaultWindowDecor != null -> {
                requireDefaultWindowDecor().taskDragResizer = taskPositioner
            }

            desktopWindowDecor != null -> {
                requireDesktopWindowDecor().setTaskDragResizer(taskPositioner)
            }

            captionWindowDecoration != null -> {
                requireCaptionWindowDecor().setTaskDragResizer(taskPositioner)
            }

            else -> error("Expected Non-null window decoration")
        }

    /** Set the listeners for the decorations. */
    fun setCaptionListeners(
        onClickListener: OnClickListener,
        onTouchListener: OnTouchListener,
        onLongClickListener: OnLongClickListener?,
        onGenericMotionListener: OnGenericMotionListener?,
    ) =
        when {
            defaultWindowDecor != null -> {
                requireDefaultWindowDecor()
                    .setListeners(
                        onClickListener,
                        onTouchListener,
                        checkNotNull(onLongClickListener) {
                            "Expected non-null long click listener"
                        },
                        checkNotNull(onGenericMotionListener) {
                            "Expected non-null generic motion listener"
                        },
                    )
            }

            desktopWindowDecor != null -> {
                requireDesktopWindowDecor()
                    .setCaptionListeners(
                        onClickListener,
                        onTouchListener,
                        checkNotNull(onLongClickListener) {
                            "Expected non-null long click listener"
                        },
                        checkNotNull(onGenericMotionListener) {
                            "Expected non-null generic motion listener"
                        },
                    )
            }

            captionWindowDecoration != null -> {
                requireCaptionWindowDecor().setCaptionListeners(onClickListener, onTouchListener)
            }

            else -> error("Expected Non-null window decoration")
        }

    /**
     * Sets the [exclusionRegionListener] which is notified when the exclusion region is changed or
     * dismissed.
     */
    fun setExclusionRegionListener(exclusionRegionListener: ExclusionRegionListener) =
        when {
            defaultWindowDecor != null -> {
                requireDefaultWindowDecor().setExclusionRegionListener(exclusionRegionListener)
            }

            desktopWindowDecor != null -> {
                requireDesktopWindowDecor().setExclusionRegionListener(exclusionRegionListener)
            }

            else -> error("Expected Non-null default or desktop window decoration")
        }

    /** Sets the [dragPositioningCallback] which is called when a task is repositioned via drag. */
    fun setDragPositioningCallback(taskPositioner: TaskPositioner) =
        when {
            defaultWindowDecor != null -> {
                requireDefaultWindowDecor().setDragPositioningCallback(taskPositioner)
            }

            desktopWindowDecor != null -> {
                requireDesktopWindowDecor().setDragPositioningCallback(taskPositioner)
            }

            captionWindowDecoration != null -> {
                requireCaptionWindowDecor().setDragPositioningCallback(taskPositioner)
            }

            else -> error("Expected Non-null window decoration")
        }

    /** Announces that the app window is now being focused for accessibility. */
    fun a11yAnnounceNewFocusedWindow() =
        when {
            defaultWindowDecor != null -> requireDefaultWindowDecor().a11yAnnounceNewFocusedWindow()
            desktopWindowDecor != null -> requireDesktopWindowDecor().a11yAnnounceNewFocusedWindow()
            else -> error("Expected Non-null default or desktop window decoration")
        }

    /**
     * Updates last App-to-Web education request timestamp. Returns true if new request to show
     * education has been received.
     */
    fun updateAppToWebEducationRequestTimestamp(
        latestOpenInBrowserEducationTimestamp: Long
    ): Boolean =
        when {
            desktopWindowDecor != null -> {
                requireDesktopWindowDecor()
                    .updateAppToWebEducationRequestTimestamp(latestOpenInBrowserEducationTimestamp)
            }
            else -> false
        }

    /** Returns [true] if browser session is available to switch from App-to-Web. */
    fun isBrowserSessionAvailable() =
        when {
            desktopWindowDecor != null -> requireDesktopWindowDecor().isBrowserSessionAvailable()
            else -> false
        }

    /** Returns [true] if captured link is available. */
    fun isCapturedLinkAvailable() =
        when {
            desktopWindowDecor != null -> requireDesktopWindowDecor().isCapturedLinkAvailable
            else -> false
        }

    /** Factory for [WindowDecorationWrapper]. */
    class Factory {

        /** Returns a [WindowDecorationWrapper]. */
        fun fromDesktopDecoration(desktopWindowDecoration: DesktopModeWindowDecoration) =
            WindowDecorationWrapper(
                desktopWindowDecor = desktopWindowDecoration,
                defaultWindowDecor = null,
                captionWindowDecoration = null,
            )

        /** Returns a [WindowDecorationWrapper]. */
        fun fromDefaultDecoration(defaultWindowDecor: DefaultWindowDecoration) =
            WindowDecorationWrapper(
                desktopWindowDecor = null,
                defaultWindowDecor = defaultWindowDecor,
                captionWindowDecoration = null,
            )

        /** Returns a [WindowDecorationWrapper]. */
        fun fromCaptionDecoration(captionWindowDecoration: CaptionWindowDecoration) =
            WindowDecorationWrapper(
                desktopWindowDecor = null,
                defaultWindowDecor = null,
                captionWindowDecoration = captionWindowDecoration,
            )
    }
}

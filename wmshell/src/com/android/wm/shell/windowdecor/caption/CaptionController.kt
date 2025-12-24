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
package com.android.wm.shell.windowdecor.caption

import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Region
import android.graphics.RegionIterator
import android.os.Binder
import android.os.Trace
import android.view.Display
import android.view.MotionEvent
import android.view.SurfaceControl
import android.view.View
import android.view.WindowManager
import android.window.WindowContainerTransaction
//import com.android.app.tracing.traceSection
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_WINDOW_DECORATION
import com.android.wm.shell.windowdecor.HandleMenuController
import com.android.wm.shell.windowdecor.ManageWindowsMenuController
import com.android.wm.shell.windowdecor.MaximizeMenuController
import com.android.wm.shell.windowdecor.TaskFocusStateConsumer
import com.android.wm.shell.windowdecor.WindowDecoration2.RelayoutParams
import com.android.wm.shell.windowdecor.WindowDecoration2.SurfaceControlViewHostFactory
import com.android.wm.shell.windowdecor.WindowDecorationInsets
import com.android.wm.shell.windowdecor.common.CaptionRegionHelper
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier
import com.android.wm.shell.windowdecor.extension.identityHashCode
import com.android.wm.shell.windowdecor.viewholder.WindowDecorationViewHolder

/**
 * Creates, updates, and removes the caption and its related menus based on [RunningTaskInfo]
 * changes and user interactions.
 *
 * @param <T> The type of the caption's root view
 */
abstract class CaptionController<T>(
    protected var taskInfo: RunningTaskInfo,
    private val windowDecorViewHostSupplier: WindowDecorViewHostSupplier<WindowDecorViewHost>,
    private val surfaceControlBuilderSupplier: () -> SurfaceControl.Builder = {
        SurfaceControl.Builder()
    },
    private val surfaceControlViewHostFactory: SurfaceControlViewHostFactory =
        object : SurfaceControlViewHostFactory {},
) where T : View, T : TaskFocusStateConsumer {

    private var captionInsets: WindowDecorationInsets? = null
    private val insetsOwner = Binder()
    private var captionViewHost: WindowDecorViewHost? = null
    private var windowDecorationViewHolder: WindowDecorationViewHolder<*>? = null
    protected lateinit var captionLayoutResult: CaptionRelayoutResult
    private lateinit var decorWindowContext: Context
    private var windowDecorationInsets: WindowDecorationInsets? = null

    protected var isCaptionVisible = false
    var isRecentsTransitionRunning = false
    var hasGlobalFocus = false
    var isDragging = false

    /** Controller for maximize menu or null if caption does not implement a maximize menu. */
    open val maximizeMenuController: MaximizeMenuController? = null
    /** Controller for handle menu or null if caption does not implement a handle menu. */
    open val handleMenuController: HandleMenuController? = null
    /**
     * Controller for manage windows menu or null if caption does not implement a manage windows
     * menu.
     */
    open val manageWindowsMenuController: ManageWindowsMenuController? = null

    /** Inflates the correct caption view and returns the view's view holder. */
    protected abstract fun createCaptionView(): WindowDecorationViewHolder<*>

    /** Type of caption. */
    abstract val captionType: CaptionType

    /**
     * Returns the caption height including any additional padding that will be added to the
     * caption.
     */
    abstract fun getCaptionHeight(): Int

    /** Returns the width of the caption. */
    protected abstract fun getCaptionWidth(): Int

    /** The occluding elements inside the caption. */
    abstract val occludingElements: List<OccludingElement>

    /** Returns the valid drag area for a task or null if task cannot be dragged. */
    open fun calculateValidDragArea(): Rect? = null

    /** Called when a task is animating after being repositioned or resized. */
    open fun onAnimatingTaskRepositioningOrResize(animatingTaskResizeOrReposition: Boolean) {}

    /**
     * Called by [WindowDecoration2] to trigger a new relayout to update the caption and its views.
     */
    open fun relayout(
        params: RelayoutParams,
        parentContainer: SurfaceControl,
        display: Display,
        decorWindowContext: Context,
        startT: SurfaceControl.Transaction,
        finishT: SurfaceControl.Transaction,
        wct: WindowContainerTransaction,
    ): CaptionRelayoutResult {
            taskInfo = params.runningTaskInfo
            hasGlobalFocus = params.hasGlobalFocus
            this.decorWindowContext = decorWindowContext

            val viewHolder = getOrCreateViewHolder()
            isCaptionVisible = taskInfo.isVisible && params.isCaptionVisible
            val viewHost = getOrCreateViewHost(decorWindowContext, display)
            val taskBounds = taskInfo.getConfiguration().windowConfiguration.bounds
            val captionTopPadding = getCaptionTopPadding()
            val captionHeight = getCaptionHeight()
            val captionWidth = getCaptionWidth()
            val captionX = (taskBounds.width() - captionWidth) / 2
            val captionY = 0
            val elements = occludingElements
            val localCaptionBounds =
                Rect(captionX, captionY, captionX + captionWidth, captionY + captionHeight)
            val customizableCaptionRegion =
                CaptionRegionHelper.calculateCustomizableRegion(
                    context = decorWindowContext,
                    taskInfo = taskInfo,
                    elements = elements,
                    localCaptionBounds = localCaptionBounds,
                )
            val touchableRegion =
                CaptionRegionHelper.calculateLimitedTouchableRegion(
                    context = decorWindowContext,
                    taskInfo = taskInfo,
                    displayExclusionRegion = params.displayExclusionRegion,
                    elements = elements,
                    localCaptionBounds = localCaptionBounds,
                )

            logD(
                "relayout with taskBounds=%s captionSize=%dx%d captionTopPadding=%d " +
                    "captionX=%d captionY=%d elements=%s customizableCaptionRegion=%s " +
                    "touchableRegion=%s",
                taskBounds,
                captionHeight,
                captionWidth,
                captionTopPadding,
                captionX,
                captionY,
                elements,
                customizableCaptionRegion.toReadableString(),
                touchableRegion,
            )

            updateCaptionContainerSurface(
                parentContainer,
                startT,
                captionWidth,
                captionHeight,
                captionX,
            )
            updateCaptionInsets(
                params = params,
                elements = elements,
                decorWindowContext = decorWindowContext,
                wct = wct,
                captionHeight = captionHeight,
                taskBounds = taskBounds,
                localCaptionBounds = localCaptionBounds,
            )

            viewHolder.setTopPadding(captionTopPadding)
            viewHolder.setTaskFocusState(params.hasGlobalFocus)
            updateViewHierarchy(
                params,
                viewHost,
                viewHolder.rootView,
                captionWidth,
                captionHeight,
                startT,
                touchableRegion,
            )

            captionLayoutResult =
                CaptionRelayoutResult(
                    captionHeight = captionHeight,
                    captionWidth = captionWidth,
                    captionX = captionX,
                    captionY = captionY,
                    captionTopPadding = captionTopPadding,
                    customizableCaptionRegion = customizableCaptionRegion,
                )
            return captionLayoutResult
        }

    protected open fun getCaptionTopPadding(): Int = 0

    /** Updates the caption's view hierarchy. */
    @OptIn(ExperimentalStdlibApi::class)
    private fun updateViewHierarchy(
        params: RelayoutParams,
        viewHost: WindowDecorViewHost,
        view: View,
        captionWidth: Int,
        captionHeight: Int,
        startT: SurfaceControl.Transaction,
        touchableRegion: Region?,
    ) {
            val taskId = taskInfo.taskId
            logD(
                "updateViewHierarchy of taskId=%d size=%dx%d touchableRegion=%s " +
                    "view=%s viewParent=%s viewHost=%s",
                taskId,
                captionWidth,
                captionHeight,
                touchableRegion,
                view.identityHashCode.toHexString(),
                view.parent.identityHashCode.toHexString(),
                viewHost,
            )
            val lp =
                WindowManager.LayoutParams(
                        captionWidth,
                        captionHeight,
                        WindowManager.LayoutParams.TYPE_APPLICATION,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSPARENT,
                    )
                    .apply {
                        title = "Caption of Task=$taskId"
                        setTrustedOverlay()
                        inputFeatures = params.inputFeatures
                    }
            if (params.asyncViewHost) {
                require(!params.applyStartTransactionOnDraw) {
                    "Cannot use sync draw tx with async relayout"
                }
                viewHost.updateViewAsync(view, lp, taskInfo.configuration, touchableRegion)
            } else {
                viewHost.updateView(
                    view,
                    lp,
                    taskInfo.configuration,
                    touchableRegion,
                    if (params.applyStartTransactionOnDraw) startT else null,
                )
            }
        }

    private fun updateCaptionContainerSurface(
        parentContainer: SurfaceControl,
        startT: SurfaceControl.Transaction,
        captionWidth: Int,
        captionHeight: Int,
        captionX: Int,
    ) {
        val captionSurface = captionViewHost?.surfaceControl ?: return
        startT
            .reparent(captionSurface, parentContainer)
            .setWindowCrop(captionSurface, captionWidth, captionHeight)
            .setPosition(captionSurface, captionX.toFloat(), /* y= */ 0f)
            .setLayer(captionSurface, CAPTION_LAYER_Z_ORDER)
            .show(captionSurface)
    }

    /** Adds caption inset source to a WCT */
    fun addCaptionInset(wct: WindowContainerTransaction) {
        val captionHeight = getCaptionHeight()
        if (captionHeight == 0 || !isCaptionVisible) {
            return
        }

        val captionInsets = Rect(0, 0, 0, captionHeight)
        val newInsets =
            WindowDecorationInsets(
                token = taskInfo.token,
                owner = insetsOwner,
                frame = captionInsets,
                taskFrame = null,
                boundingRects = emptyList(),
                flags = 0,
                shouldAddCaptionInset = true,
                excludedFromAppBounds = false,
            )
        if (newInsets != windowDecorationInsets) {
            windowDecorationInsets = newInsets.apply { update(wct) }
        }
    }

    private fun updateCaptionInsets(
        params: RelayoutParams,
        elements: List<OccludingElement>,
        decorWindowContext: Context,
        wct: WindowContainerTransaction,
        captionHeight: Int,
        taskBounds: Rect,
        localCaptionBounds: Rect,
    ) {
        if (!isCaptionVisible || !params.isInsetSource) {
            captionInsets?.remove(wct)
            captionInsets = null
        }
        // Caption inset is the full width of the task with the |captionHeight| and
        // positioned at the top of the task bounds, also in absolute coordinates.
        // So just reuse the task bounds and adjust the bottom coordinate.
        val captionInsetsRect = Rect(taskBounds)
        captionInsetsRect.bottom = captionInsetsRect.top + captionHeight

        // Caption bounding rectangles: these are optional, and are used to present finer
        // insets than traditional |Insets| to apps about where their content is occluded.
        // These are in coordinates relative to the caption frame.
        val boundingRects =
            CaptionRegionHelper.calculateBoundingRectsInsets(
                context = decorWindowContext,
                captionFrame = localCaptionBounds,
                elements = elements,
            )

        val newInsets =
            WindowDecorationInsets(
                taskInfo.token,
                insetsOwner,
                captionInsetsRect,
                taskBounds,
                boundingRects,
                params.insetSourceFlags,
                params.isInsetSource,
                params.shouldSetAppBounds,
            )
        if (newInsets != captionInsets) {
            // Add or update this caption as an insets source.
            captionInsets = newInsets
            newInsets.update(wct)
        }
    }

    /** Checks whether the touch event falls inside the customizable caption region. */
    fun checkTouchEventInCustomizableRegion(ev: MotionEvent): Boolean =
        captionLayoutResult.customizableCaptionRegion.contains(ev.rawX.toInt(), ev.rawY.toInt())

    /**
     * Returns caption's view holder if not null. Otherwise, inflates caption view and returns new
     * view holder.
     */
    private fun getOrCreateViewHolder(): WindowDecorationViewHolder<*> {
        val currentViewHolder = windowDecorationViewHolder
        if (currentViewHolder != null) {
            return currentViewHolder
        }
        val viewHolder = createCaptionView()
        logD("getOrCreateViewHolder() created new caption view: %s", viewHolder)
        windowDecorationViewHolder = viewHolder
        return viewHolder
    }

    /** Releases all caption views. Returns true if caption view host is released. */
    open fun close(wct: WindowContainerTransaction, t: SurfaceControl.Transaction): Boolean {
            captionInsets?.remove(wct)
            captionInsets = null
            windowDecorationViewHolder = null

            val viewHost = captionViewHost ?: return false
            windowDecorViewHostSupplier.release(viewHost, t)
            captionViewHost = null
            return true
        }

    private fun getOrCreateViewHost(context: Context, display: Display): WindowDecorViewHost {
            val currentViewHost = captionViewHost
            if (currentViewHost != null) {
                return currentViewHost
            }
            val viewHost = windowDecorViewHostSupplier.acquire(context, display)
            logD("getOrCreateViewHost() acquired viewHost: %s", viewHost)
            captionViewHost = viewHost
            return viewHost
        }

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_WINDOW_DECORATION, "%s: $msg", TAG, *arguments)
    }

    private fun Region.toReadableString(): String {
        val iterator = RegionIterator(this)
        val rect = Rect()
        val sb = StringBuilder()
        sb.append("Region[")
        var first = true
        while (iterator.next(rect)) {
            if (!first) {
                sb.append(", ")
            }
            sb.append(rect.toShortString())
            first = false
        }
        sb.append("]")
        return sb.toString()
    }

    /** Caption data calculated during [relayout]. */
    data class CaptionRelayoutResult(
        // The caption height with caption padding included
        val captionHeight: Int,
        val captionWidth: Int,
        // The caption x position relative to its parent task
        val captionX: Int,
        // The caption y position relative to its parent task
        val captionY: Int,
        val captionTopPadding: Int,
        // The region within the caption that is allowed to be customized by the app, in display
        // coordinates.
        val customizableCaptionRegion: Region,
    )

    /** The type of caption added by this controller. */
    enum class CaptionType {
        APP_HANDLE,
        APP_HEADER,
        NO_CAPTION,
    }

    companion object {
        private const val TAG = "CaptionController"

        /**
         * The Z-order of the caption surface.
         *
         * We use [decorationContainerSurface] to define input window for task resizing; by layering
         * it in front of the caption surface, we can allow it to handle input prior to caption view
         * itself, treating corner inputs as resize events rather than repositioning.
         */
        private const val CAPTION_LAYER_Z_ORDER: Int = -1
    }
}

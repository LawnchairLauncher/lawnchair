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
import android.os.Binder
import android.view.Display
import android.view.SurfaceControl
import android.view.View
import android.view.WindowManager
import android.window.WindowContainerTransaction
import com.android.app.tracing.traceSection
import com.android.wm.shell.windowdecor.TaskFocusStateConsumer
import com.android.wm.shell.windowdecor.WindowDecoration2.RelayoutParams
import com.android.wm.shell.windowdecor.WindowDecoration2.RelayoutParams.OccludingCaptionElement.Alignment
import com.android.wm.shell.windowdecor.WindowDecorationInsets
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier
import com.android.wm.shell.windowdecor.extension.getDimensionPixelSize
import com.android.wm.shell.windowdecor.extension.isRtl
import com.android.wm.shell.windowdecor.viewholder.WindowDecorationViewHolder


/**
 * Creates, updates, and removes the caption and its related menus based on [RunningTaskInfo]
 * changes and user interactions.
 *
 * @param <T> The type of the caption's root view
 */
abstract class CaptionController<T>(
    private val windowDecorViewHostSupplier:
    WindowDecorViewHostSupplier<WindowDecorViewHost>,
) where T : View, T : TaskFocusStateConsumer {

    private lateinit var taskInfo: RunningTaskInfo
    private var captionInsets: WindowDecorationInsets? = null
    private val insetsOwner = Binder()
    private var captionViewHost: WindowDecorViewHost? = null
    private var windowDecorationViewHolder: WindowDecorationViewHolder<*>? = null

    private var isCaptionVisible = false

    /** Inflates the correct caption view and returns the view's view holder. */
    abstract fun createCaptionView(): WindowDecorationViewHolder<*>

    /** Type of caption.*/
    abstract val captionType: CaptionType

    /**
     * Returns the caption height given the additional padding that will be added to the top of the
     * caption.
     */
    abstract fun getCaptionHeight(captionPadding: Int): Int

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
    ): CaptionRelayoutResult = traceSection("CaptionController#relayout") {
        val viewHolder = getOrCreateViewHolder()
        isCaptionVisible = params.isCaptionVisible
        val viewHost = getOrCreateViewHost(decorWindowContext, display)
        val resources = decorWindowContext.resources
        val taskBounds = taskInfo.getConfiguration().windowConfiguration.bounds
        val captionHeight = getCaptionHeight(params.captionTopPadding)
        val captionWidth = resources.getDimensionPixelSize(
            params.captionWidthId,
            taskBounds.width()
        )
        val captionX = (taskBounds.width() - captionWidth) / 2
        val captionY = 0
        val captionTopPadding = params.captionTopPadding

        updateCaptionContainerSurface(
            parentContainer,
            startT,
            captionWidth,
            captionHeight,
            captionX
        )
        val customizableCaptionRegion =
            updateCaptionInsets(params, decorWindowContext, wct, captionHeight, taskBounds)

        traceSection("CaptionController#relayout-updateViewHost") {
            viewHolder.setTopPadding(params.captionTopPadding)
            viewHolder.setTaskFocusState(params.hasGlobalFocus)
            val localCaptionBounds = Rect(
                captionX,
                captionY,
                captionX + captionWidth,
                captionY + captionHeight
            )
            val touchableRegion = if (params.limitTouchRegionToSystemAreas) {
                calculateLimitedTouchableRegion(
                    params,
                    decorWindowContext,
                    localCaptionBounds
                )
            } else null
            updateViewHierarchy(
                params,
                viewHost,
                viewHolder.rootView,
                captionWidth,
                captionHeight,
                startT,
                touchableRegion
            )
        }

        return CaptionRelayoutResult(
            captionHeight = captionHeight,
            captionWidth = captionWidth,
            captionX = captionX,
            captionY = captionY,
            captionTopPadding = captionTopPadding,
            customizableCaptionRegion = customizableCaptionRegion,
        )
    }

    private fun updateViewHierarchy(
        params: RelayoutParams,
        viewHost: WindowDecorViewHost,
        view: View,
        captionWidth: Int,
        captionHeight: Int,
        startT: SurfaceControl.Transaction,
        touchableRegion: Region?
    ) = traceSection("CaptionController#updateViewHierarchy") {
        val lp = WindowManager.LayoutParams(
            captionWidth,
            captionHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSPARENT
        ).apply {
            title = "Caption of Task=" + taskInfo.taskId
            setTrustedOverlay()
            inputFeatures = params.inputFeatures
        }
        if (params.asyncViewHost) {
            require(!params.applyStartTransactionOnDraw) {
                "Cannot use sync draw tx with async relayout"
            }
            viewHost.updateViewAsync(
                view,
                lp,
                taskInfo.configuration,
                touchableRegion
            )
        } else {
            viewHost.updateView(
                view,
                lp,
                taskInfo.configuration,
                touchableRegion,
                if (params.applyStartTransactionOnDraw) startT else null
            )
        }
    }

    private fun calculateLimitedTouchableRegion(
        params: RelayoutParams,
        decorWindowContext: Context,
        localCaptionBounds: Rect,
    ): Region {
        // Make caption bounds relative to display to align with exclusion region.
        val positionInParent = params.runningTaskInfo.positionInParent
        val captionBoundsInDisplay = Rect(localCaptionBounds).apply {
            offsetTo(positionInParent.x, positionInParent.y)
        }

        val boundingRects = calculateBoundingRectsRegion(
            params,
            decorWindowContext,
            captionBoundsInDisplay
        )

        val customizedRegion = Region.obtain().apply {
            set(captionBoundsInDisplay)
            op(boundingRects, Region.Op.DIFFERENCE)
            op(params.displayExclusionRegion, Region.Op.INTERSECT)
        }

        val touchableRegion = Region.obtain().apply {
            set(captionBoundsInDisplay)
            op(customizedRegion, Region.Op.DIFFERENCE)
            // Return resulting region back to window coordinates.
            translate(-positionInParent.x, -positionInParent.y)
        }

        boundingRects.recycle()
        customizedRegion.recycle()
        return touchableRegion
    }

    private fun calculateBoundingRectsRegion(
        params: RelayoutParams,
        decorWindowContext: Context,
        captionBoundsInDisplay: Rect,
    ): Region {
        val numOfElements = params.occludingCaptionElements.size
        val region = Region.obtain()
        if (numOfElements == 0) {
            // The entire caption is a bounding rect.
            region.set(captionBoundsInDisplay)
            return region
        }
        val resources = decorWindowContext.resources
        params.occludingCaptionElements.forEach { element ->
            val elementWidthPx = resources.getDimensionPixelSize(element.widthResId)
            val boundingRect = calculateBoundingRectLocal(
                element,
                elementWidthPx,
                captionBoundsInDisplay,
                decorWindowContext,
            )
            // Bounding rect is initially calculated relative to the caption, so offset it to make
            // it relative to the display.
            boundingRect.offset(captionBoundsInDisplay.left, captionBoundsInDisplay.top)
            region.union(boundingRect)
        }
        return region
    }

    private fun calculateBoundingRectLocal(
        element: RelayoutParams.OccludingCaptionElement,
        elementWidthPx: Int,
        captionRect: Rect,
        decorWindowContext: Context,
    ): Rect {
        val isRtl = decorWindowContext.isRtl
        return when (element.alignment) {
            Alignment.START -> {
                if (isRtl) {
                    Rect(
                        captionRect.width() - elementWidthPx,
                        0,
                        captionRect.width(),
                        captionRect.height()
                    )
                } else {
                    Rect(0, 0, elementWidthPx, captionRect.height())
                }
            }

            Alignment.END -> {
                if (isRtl) {
                    Rect(0, 0, elementWidthPx, captionRect.height())
                } else {
                    Rect(
                        captionRect.width() - elementWidthPx, 0,
                        captionRect.width(), captionRect.height()
                    )
                }
            }
        }
    }

    private fun updateCaptionContainerSurface(
        parentContainer: SurfaceControl,
        startT: SurfaceControl.Transaction,
        captionWidth: Int,
        captionHeight: Int,
        captionX: Int
    ) {
        val captionSurface = captionViewHost?.surfaceControl ?: return
        startT.reparent(captionSurface, parentContainer)
            .setWindowCrop(captionSurface, captionWidth, captionHeight)
            .setPosition(captionSurface, captionX.toFloat(), /* y= */ 0f)
            .setLayer(captionSurface, CAPTION_LAYER_Z_ORDER)
            .show(captionSurface)
    }

    private fun updateCaptionInsets(
        params: RelayoutParams,
        decorWindowContext: Context,
        wct: WindowContainerTransaction,
        captionHeight: Int,
        taskBounds: Rect
    ): Region {
        if (!isCaptionVisible || !params.isInsetSource) {
            captionInsets?.remove(wct)
            captionInsets = null
            return Region.obtain()
        }
        // Caption inset is the full width of the task with the |captionHeight| and
        // positioned at the top of the task bounds, also in absolute coordinates.
        // So just reuse the task bounds and adjust the bottom coordinate.
        val captionInsetsRect = Rect(taskBounds)
        captionInsetsRect.bottom = captionInsetsRect.top + captionHeight

        // Caption bounding rectangles: these are optional, and are used to present finer
        // insets than traditional |Insets| to apps about where their content is occluded.
        // These are also in absolute coordinates.
        val boundingRects: Array<Rect>?
        val numOfElements = params.occludingCaptionElements.size
        val customizableCaptionRegion = Region.obtain()
        if (numOfElements == 0) {
            boundingRects = null
        } else {
            // The customizable region can at most be equal to the caption bar.
            if (params.hasInputFeatureSpy()) {
                customizableCaptionRegion.set(captionInsetsRect)
            }
            val resources = decorWindowContext.resources
            boundingRects = Array(numOfElements) { Rect() }

            for (i in 0 until numOfElements) {
                val element = params.occludingCaptionElements[i]
                val elementWidthPx = resources.getDimensionPixelSize(element.widthResId)
                boundingRects[i].set(
                    calculateBoundingRectLocal(
                        element,
                        elementWidthPx,
                        captionInsetsRect,
                        decorWindowContext
                    )
                )
                // Subtract the regions used by the caption elements, the rest is
                // customizable.
                if (params.hasInputFeatureSpy()) {
                    customizableCaptionRegion.op(
                        boundingRects[i],
                        Region.Op.DIFFERENCE
                    )
                }
            }
        }

        val newInsets = WindowDecorationInsets(
            taskInfo.token,
            insetsOwner,
            captionInsetsRect,
            taskBounds,
            boundingRects,
            params.insetSourceFlags,
            params.isInsetSource,
            params.shouldSetAppBounds
        )
        if (newInsets != captionInsets) {
            // Add or update this caption as an insets source.
            captionInsets = newInsets
            newInsets.update(wct)
        }

        return customizableCaptionRegion
    }

    /**
     * Returns caption's view holder if not null. Otherwise, inflates caption view and returns new
     * view holder.
     */
    private fun getOrCreateViewHolder(): WindowDecorationViewHolder<*> {
        val viewHolder = windowDecorationViewHolder ?: createCaptionView()
        windowDecorationViewHolder = viewHolder
        return viewHolder
    }

    /** Releases all caption views. Returns true if caption view host is released. */
    fun releaseViews(
        wct: WindowContainerTransaction,
        t: SurfaceControl.Transaction
    ): Boolean {
        captionInsets?.remove(wct)
        captionInsets = null

        val viewHost = captionViewHost ?: return false
        viewHost.release(t)
        captionViewHost = null
        return true
    }

    private fun getOrCreateViewHost(
        context: Context,
        display: Display
    ): WindowDecorViewHost = traceSection("CaptionController#getOrCreateViewHost") {
        return captionViewHost ?: windowDecorViewHostSupplier.acquire(context, display)
    }

    /** Caption data calculated during [relayout]. */
    data class CaptionRelayoutResult(
        // The caption height with caption padding included
        val captionHeight: Int,
        val captionWidth: Int,
        val captionX: Int,
        val captionY: Int,
        val captionTopPadding: Int,
        val customizableCaptionRegion: Region,
    )

    /** The type of caption added by this controller. */
    enum class CaptionType {
        APP_HANDLE, APP_HEADER
    }

    companion object {
        /**
         * The Z-order of the caption surface.
         *
         *
         * We use [decorationContainerSurface] to define input window for task resizing; by
         * layering it in front of the caption surface, we can allow it to handle input
         * prior to caption view itself, treating corner inputs as resize events rather than
         * repositioning.
         */
        private const val CAPTION_LAYER_Z_ORDER: Int = -1
    }
}

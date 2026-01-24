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
package com.android.wm.shell.common

import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.graphics.Rect
import android.os.Trace
import android.view.SurfaceControl
import android.window.TaskConstants
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.R
import com.android.wm.shell.shared.annotations.ShellDesktopThread

/**
 * Represents the indicator surface that visualizes the current position of a dragged window during
 * a multi-display drag operation.
 *
 * This class manages the creation, display, and manipulation of the [SurfaceControl] that act as a
 * visual indicator, providing feedback to the user about the dragged window's location.
 */
@ShellDesktopThread
class MultiDisplayDragMoveIndicatorSurface(context: Context, taskSurface: SurfaceControl) {
    public enum class Visibility {
        INVISIBLE,
        TRANSLUCENT,
        VISIBLE,
    }

    private var visibility = Visibility.INVISIBLE

    private var surface: SurfaceControl? = null

    private val cornerRadius =
        context.resources
            .getDimensionPixelSize(R.dimen.desktop_windowing_freeform_rounded_corner_radius)
            .toFloat()

    init {
        Trace.beginSection("DragIndicatorSurface#init")

        surface = SurfaceControl.mirrorSurface(taskSurface)

        Trace.endSection()
    }

    /** Disposes the indicator surface using the provided [transaction]. */
    fun dispose(transaction: SurfaceControl.Transaction) {
        surface?.let { sc -> transaction.remove(sc) }
        surface = null
    }

    /**
     * Shows the indicator surface at [bounds] on the specified display ([displayId]), with the
     * [scale], visualizing the drag of the [taskInfo]. The indicator surface is shown using
     * [transaction], and the [rootTaskDisplayAreaOrganizer] is used to reparent the surfaces.
     */
    fun show(
        transaction: SurfaceControl.Transaction,
        taskInfo: RunningTaskInfo,
        rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
        displayId: Int,
        bounds: Rect,
        visibility: Visibility,
        scale: Float,
    ) {
        val sc = surface
        if (sc == null) {
            ProtoLog.w(
                WM_SHELL_DESKTOP_MODE,
                "Cannot show drag indicator for Task %d on Display %d because " +
                    "indicator surface is null.",
                taskInfo.taskId,
                displayId,
            )
            return
        }

        rootTaskDisplayAreaOrganizer.reparentToDisplayArea(displayId, sc, transaction)
        relayout(bounds, transaction, visibility)
        transaction.show(sc).setLayer(sc, MOVE_INDICATOR_LAYER).setScale(sc, scale, scale)
    }

    /**
     * Repositions and resizes the indicator surface based on [bounds] using [transaction]. The
     * [newVisibility] flag indicates whether the indicator is within the display after relayout.
     */
    fun relayout(bounds: Rect, transaction: SurfaceControl.Transaction, newVisibility: Visibility) {
        if (visibility == Visibility.INVISIBLE && newVisibility == Visibility.INVISIBLE) {
            // No need to relayout if the surface is already invisible and should not be visible.
            return
        }

        visibility = newVisibility
        val sc = surface ?: return
        transaction
            .setCornerRadius(sc, cornerRadius)
            .setPosition(sc, bounds.left.toFloat(), bounds.top.toFloat())
        when (visibility) {
            Visibility.VISIBLE ->
                transaction.setAlpha(sc, ALPHA_FOR_MOVE_INDICATOR_ON_DISPLAY_WITH_CURSOR)
            Visibility.TRANSLUCENT ->
                transaction.setAlpha(sc, ALPHA_FOR_MOVE_INDICATOR_ON_NON_CURSOR_DISPLAY)
            Visibility.INVISIBLE -> {
                // Do nothing intentionally. Falling into this means the bounds is outside
                // of the display, so no need to hide the surface explicitly.
            }
        }
    }

    /** Factory for creating [MultiDisplayDragMoveIndicatorSurface] instances. */
    class Factory() {
        /**
         * Creates a new [MultiDisplayDragMoveIndicatorSurface] instance to visualize the drag
         * operation of the [taskInfo] on the given [display].
         */
        fun create(displayContext: Context, taskSurface: SurfaceControl) =
            MultiDisplayDragMoveIndicatorSurface(displayContext, taskSurface)
    }

    companion object {
        private const val TAG = "MultiDisplayDragMoveIndicatorSurface"

        private const val MOVE_INDICATOR_LAYER = TaskConstants.TASK_CHILD_LAYER_RESIZE_VEIL

        private const val ALPHA_FOR_MOVE_INDICATOR_ON_DISPLAY_WITH_CURSOR = 1.0f
        private const val ALPHA_FOR_MOVE_INDICATOR_ON_NON_CURSOR_DISPLAY = 0.7f
    }
}

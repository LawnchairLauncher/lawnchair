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

import android.app.ActivityManager
import android.content.Context
import android.graphics.Rect
import android.graphics.Region
import android.os.Handler
import android.view.SurfaceControl
import android.view.View
import android.view.View.OnLongClickListener
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.windowdecor.caption.AppHandleController
import com.android.wm.shell.windowdecor.caption.AppHeaderController
import com.android.wm.shell.windowdecor.caption.CaptionController
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier

/**
 * Default window decoration implementation that controls both the app handle and the app header
 * captions. This class also adds various decorations to the window including the [ResizeVeil].
 */
class DefaultWindowDecoration(
    context: Context,
    displayController: DisplayController,
    taskSurface: SurfaceControl,
    surfaceControlSupplier: () -> SurfaceControl,
    taskOrganizer: ShellTaskOrganizer,
    private val windowDecorViewHostSupplier: WindowDecorViewHostSupplier<WindowDecorViewHost>,
    private val windowDecorationActions: WindowDecorationActions,
    private val onCaptionTouchListener: View.OnTouchListener,
    private val onCaptionButtonClickListener: View.OnClickListener,
    private val onLongClickListener: OnLongClickListener,
    private val onCaptionGenericMotionListener: View.OnGenericMotionListener,
    private val onMaximizeHoverAnimationFinishedListener: () -> Unit,
    private val desktopModeUiEventLogger: DesktopModeUiEventLogger,
    private val windowManagerWrapper: WindowManagerWrapper,
    @ShellMainThread private val handler: Handler,
) : WindowDecoration2<WindowDecorLinearLayout>(
    context,
    displayController,
    taskSurface,
    surfaceControlSupplier,
    taskOrganizer,
    handler,
) {

    /**
         * Calculates the valid drag area for this task based on elements in the app chip.
     */
    override fun calculateValidDragArea(): Rect = Rect()

    override fun relayout(
        taskInfo: ActivityManager.RunningTaskInfo,
        hasGlobalFocus: Boolean,
        displayExclusionRegion: Region
    ) {
    }

    override fun createCaptionController(
        captionType: CaptionController.CaptionType
    ): CaptionController<WindowDecorLinearLayout> = when (captionType) {
        CaptionController.CaptionType.APP_HEADER -> {
            AppHeaderController(
                decorWindowContext,
                windowDecorationActions,
                onCaptionTouchListener,
                onCaptionButtonClickListener,
                onLongClickListener,
                onCaptionGenericMotionListener,
                onMaximizeHoverAnimationFinishedListener,
                desktopModeUiEventLogger,
                windowDecorViewHostSupplier,
            )
        }
        CaptionController.CaptionType.APP_HANDLE -> {
            AppHandleController(
                decorWindowContext,
                onCaptionTouchListener,
                onCaptionButtonClickListener,
                windowManagerWrapper,
                handler,
                desktopModeUiEventLogger,
                windowDecorViewHostSupplier,
            )
        }
    }
}

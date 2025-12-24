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

import android.annotation.IdRes
import android.app.ActivityManager
import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.Region
import android.hardware.input.InputManager
import android.os.Handler
import android.view.Display
import android.view.SurfaceControl
import android.view.View
import android.view.WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND
import android.view.WindowManager
import com.android.internal.jank.InteractionJankMonitor
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.MultiDisplayDragMoveIndicatorController
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.FakeShellDesktopState
import com.android.wm.shell.desktopmode.ShellDesktopState
import com.android.wm.shell.shared.desktopmode.DesktopConfig
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.FocusTransitionObserver
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.util.StubTransaction
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import com.android.wm.shell.windowdecor.common.WindowDecorationGestureExclusionTracker
import com.android.wm.shell.windowdecor.common.viewhost.DefaultWindowDecorViewHost
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier
import kotlinx.coroutines.CoroutineScope
import org.mockito.kotlin.mock

/** A test helper to create window decorations. */
object WindowDecorationTestHelper {

    /** Creates a task that should be decorated with an app header. */
    fun createAppHeaderTask(bounds: Rect = Rect(200, 200, 800, 600)): RunningTaskInfo {
        return createFreeformTask(bounds = bounds).apply { isVisible = true }
    }

    /** Creates a task that should be decorated with an opaque (not customizable) app header. */
    fun createOpaqueAppHeaderTask(bounds: Rect = Rect(200, 200, 800, 600)): RunningTaskInfo {
        return createAppHeaderTask(bounds = bounds).apply {
            taskDescription =
                (taskDescription ?: ActivityManager.TaskDescription()).apply {
                    topOpaqueSystemBarsAppearance =
                        topOpaqueSystemBarsAppearance and
                            APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND.inv()
                }
        }
    }

    /** Creates a task that should be decorated with an app header and is being customized. */
    fun createCustomAppHeaderTask(bounds: Rect = Rect(200, 200, 800, 600)): RunningTaskInfo {
        return createAppHeaderTask(bounds = bounds).apply {
            taskDescription =
                (taskDescription ?: ActivityManager.TaskDescription()).apply {
                    topOpaqueSystemBarsAppearance =
                        topOpaqueSystemBarsAppearance or
                            APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND
                }
        }
    }

    /** Creates a window decoration. */
    fun createWindowDecoration(
        context: Context,
        taskInfo: RunningTaskInfo,
        windowDecorationFinder: ((Int) -> WindowDecorationWrapper?),
        desktopState: DesktopState,
        shellDesktopState: ShellDesktopState =
            FakeShellDesktopState(desktopState as FakeDesktopState),
        desktopConfig: DesktopConfig,
        scope: CoroutineScope,
        handler: Handler,
        executor: ShellExecutor,
        shellInit: ShellInit = ShellInit(executor),
        displayController: DisplayController,
        desktopUserRepositories: DesktopUserRepositories,
        splitScreenController: SplitScreenController,
        desktopTasksController: DesktopTasksController,
        taskOperations: TaskOperations,
        desktopModeUiEventLogger: DesktopModeUiEventLogger = mock(),
        windowDecorationActions: WindowDecorationActions = mock(),
    ): TestWindowDecoration {
        val viewHostSupplier = TestWindowDecorViewHostSupplier(scope)
        val decoration =
            DefaultWindowDecoration(
                taskInfo = taskInfo,
                taskSurface = SurfaceControl(),
                context = context,
                userContext = context,
                displayController = displayController,
                taskResourceLoader = TestWindowDecorTaskResourceLoader(),
                splitScreenController = splitScreenController,
                desktopUserRepositories = desktopUserRepositories,
                taskOrganizer = mock(),
                handler = handler,
                mainExecutor = executor,
                mainDispatcher = mock(),
                mainScope = scope,
                bgExecutor = mock(),
                transitions = mock(),
                choreographer = mock(),
                syncQueue = mock(),
                rootTaskDisplayAreaOrganizer = mock(),
                windowDecorViewHostSupplier = viewHostSupplier,
                multiInstanceHelper = mock(),
                windowDecorCaptionRepository = mock(),
                desktopModeEventLogger = mock(),
                desktopModeUiEventLogger = desktopModeUiEventLogger,
                desktopModeCompatPolicy = mock(),
                desktopState = desktopState,
                desktopConfig = desktopConfig,
                windowDecorationActions = windowDecorationActions,
                appToWebRepository = mock(),
                windowManagerWrapper = mock(),
                lockTaskChangeListener = mock(),
                surfaceControlTransactionSupplier = { StubTransaction() },
            )
        val wrapped = decoration.wrapped()
        val positioner =
            MultiDisplayVeiledResizeTaskPositioner(
                taskOrganizer = mock<ShellTaskOrganizer>(),
                windowDecoration = wrapped,
                displayController = displayController,
                transactionSupplier = { StubTransaction() },
                transitions = mock<Transitions>(),
                interactionJankMonitor = mock<InteractionJankMonitor>(),
                handler = handler,
                multiDisplayDragMoveIndicatorController =
                    mock<MultiDisplayDragMoveIndicatorController>(),
                desktopState = desktopState,
                desktopTasksController = desktopTasksController,
            )
        val touchEventListener =
            DesktopModeTouchEventListener(
                context,
                taskInfo,
                positioner,
                windowDecorationFinder,
                desktopTasksController,
                taskOperations,
                desktopModeUiEventLogger,
                windowDecorationActions,
                desktopUserRepositories,
                WindowDecorationGestureExclusionTracker(
                    context,
                    mock(),
                    mock(),
                    executor,
                    shellInit,
                    { _, _ -> },
                ),
                mock<InputManager>(),
                mock<FocusTransitionObserver>(),
                shellDesktopState,
                mock<MultiDisplayDragMoveIndicatorController>(),
                { StubTransaction() },
                mock<DesktopModeWindowDecorViewModel.CaptionTouchStatusListener>(),
                mock<DesktopModeWindowDecorViewModel.AppHandleMotionEventHandler>(),
            )
        wrapped.setCaptionListeners(
            onClickListener = touchEventListener,
            onTouchListener = touchEventListener,
            onLongClickListener = touchEventListener,
            onGenericMotionListener = touchEventListener,
        )
        wrapped.setDragPositioningCallback(positioner)
        wrapped.setExclusionRegionListener(mock())
        return TestWindowDecoration(decoration, wrapped, viewHostSupplier)
    }

    private fun DefaultWindowDecoration.wrapped(): WindowDecorationWrapper =
        WindowDecorationWrapper.Factory().fromDefaultDecoration(this)

    /** A test supplier for [WindowDecorViewHost]. */
    class TestWindowDecorViewHostSupplier(private val scope: CoroutineScope) :
        WindowDecorViewHostSupplier<WindowDecorViewHost> {

        private val viewHosts = mutableListOf<DefaultWindowDecorViewHost>()

        /** Finds a task's caption view by its id. */
        fun getView(taskId: Int, @IdRes id: Int): View? {
            for (vh in viewHosts) {
                val view = vh.viewHostAdapter.viewHost?.view ?: continue
                val lp = view.layoutParams as? WindowManager.LayoutParams ?: continue
                if (!lp.title.contains(taskId.toString())) continue
                return view.findViewById(id)
            }
            return null
        }

        override fun acquire(context: Context, display: Display): WindowDecorViewHost =
            DefaultWindowDecorViewHost(context = context, mainScope = scope, display = display)
                .also { viewHosts.add(it) }

        override fun release(viewHost: WindowDecorViewHost, t: SurfaceControl.Transaction) {
            viewHosts.remove(viewHost)
        }
    }

    /** A test wrapper for a real window decoration. */
    class TestWindowDecoration(
        val defaultWindowDecoration: DefaultWindowDecoration,
        val wrapped: WindowDecorationWrapper,
        val viewHostSupplier: TestWindowDecorViewHostSupplier,
    ) {
        /** Relayouts the window decoration. */
        fun relayout(taskInfo: RunningTaskInfo) {
            defaultWindowDecoration.relayout(
                taskInfo = taskInfo,
                startT = StubTransaction(),
                finishT = StubTransaction(),
                applyStartTransactionOnDraw = true,
                shouldSetTaskVisibilityPositionAndCrop = false,
                hasGlobalFocus = taskInfo.isFocused,
                displayExclusionRegion = Region.obtain(),
                inSyncWithTransition = true,
                taskSurface = null,
            )
        }

        /** Finds a view by its id. */
        fun findViewById(@IdRes id: Int): View? {
            return viewHostSupplier.getView(
                taskId = defaultWindowDecoration.taskInfo.taskId,
                id = id,
            )
        }
    }

    private class TestWindowDecorTaskResourceLoader : WindowDecorTaskResourceLoader {
        override suspend fun getNameAndHeaderIcon(
            taskInfo: RunningTaskInfo
        ): Pair<CharSequence, Bitmap> = Pair("Test", mock())

        override fun getNameAndHeaderIcon(
            taskInfo: RunningTaskInfo,
            callback: (CharSequence, Bitmap) -> Unit,
        ) = callback.invoke("Test", mock())

        override suspend fun getVeilIcon(taskInfo: RunningTaskInfo): Bitmap = mock()

        override fun onWindowDecorCreated(taskInfo: RunningTaskInfo) {
            // No-op.
        }

        override fun onWindowDecorClosed(taskInfo: RunningTaskInfo) {
            // No-op.
        }
    }
}

/*
 * Copyright (C) 2024 The Android Open Source Project
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
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.app.WindowConfiguration.WindowingMode
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.input.InputManager
import android.os.Handler
import android.os.UserHandle
import android.testing.TestableContext
import android.util.SparseArray
import android.view.Choreographer
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.IWindowManager
import android.view.InputChannel
import android.view.InputMonitor
import android.view.MotionEvent
import android.view.SurfaceControl
import android.view.WindowInsets.Type.statusBars
import com.android.dx.mockito.inline.extended.StaticMockitoSession
import com.android.internal.jank.InteractionJankMonitor
import com.android.window.flags.Flags
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.apptoweb.AppToWebGenericLinksParser
import com.android.wm.shell.apptoweb.AssistContentRequester
import com.android.wm.shell.bubbles.BubbleController
import com.android.wm.shell.common.DisplayChangeController
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayInsetsController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.MultiDisplayDragMoveIndicatorController
import com.android.wm.shell.common.MultiInstanceHelper
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.compatui.api.CompatUIHandler
import com.android.wm.shell.desktopmode.DesktopActivityOrientationChangeHandler
import com.android.wm.shell.desktopmode.DesktopImmersiveController
import com.android.wm.shell.desktopmode.DesktopModeEventLogger
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.desktopmode.DesktopRepository
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.DesktopTasksLimiter
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.WindowDecorCaptionRepository
import com.android.wm.shell.desktopmode.education.AppHandleEducationController
import com.android.wm.shell.desktopmode.education.AppToWebEducationController
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.freeform.FreeformTaskTransitionStarter
import com.android.wm.shell.recents.RecentsTransitionHandler
import com.android.wm.shell.recents.RecentsTransitionStateListener
import com.android.wm.shell.shared.desktopmode.DesktopModeCompatPolicy
import com.android.wm.shell.shared.desktopmode.FakeDesktopConfig
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.FocusTransitionObserver
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.util.StubTransaction
import com.android.wm.shell.windowdecor.DesktopModeWindowDecorViewModel.DesktopModeKeyguardChangeListener
import com.android.wm.shell.windowdecor.DesktopModeWindowDecorViewModel.DesktopModeOnInsetsChangedListener
import com.android.wm.shell.windowdecor.common.AppHandleAndHeaderVisibilityHelper
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier
import com.android.wm.shell.windowdecor.tiling.DesktopTilingDecorViewModel
import com.android.wm.shell.windowdecor.viewholder.AppHandleViewHolder
import com.android.wm.shell.windowdecor.viewholder.AppHeaderViewHolder
import java.util.Optional
import java.util.function.Supplier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainCoroutineDispatcher
import org.junit.After
import org.junit.Before
import org.mockito.Mockito
import org.mockito.Mockito.anyInt
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Utility class for tests of [DesktopModeWindowDecorViewModel]
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
open class DesktopModeWindowDecorViewModelTestsBase : ShellTestCase() {
    private val mockDesktopModeWindowDecorFactory = mock<DesktopModeWindowDecoration.Factory>()
    protected val mockMainHandler = mock<Handler>()
    protected val mockMainChoreographer = mock<Choreographer>()
    protected val mockTaskOrganizer = mock<ShellTaskOrganizer>()
    protected val mockDisplayController = mock<DisplayController>()
    protected val mockSplitScreenController = mock<SplitScreenController>()
    protected val mockDesktopUserRepositories = mock<DesktopUserRepositories>()
    protected val mockDisplayLayout = mock<DisplayLayout>()
    protected val displayInsetsController = mock<DisplayInsetsController>()
    protected val mockSyncQueue = mock<SyncTransactionQueue>()
    protected val mockDesktopTasksController = mock<DesktopTasksController>()
    protected val mockDesktopImmersiveController = mock<DesktopImmersiveController>()
    protected val mockInputMonitor = mock<InputMonitor>()
    protected val mockTransitions = mock<Transitions>()
    internal val mockInputMonitorFactory =
        mock<DesktopModeWindowDecorViewModel.InputMonitorFactory>()
    protected val mockShellController = mock<ShellController>()
    protected val testShellExecutor = TestShellExecutor()
    protected val mockAppHeaderViewHolderFactory = mock<AppHeaderViewHolder.Factory>()
    protected val mockAppHandleViewHolderFactory = mock<AppHandleViewHolder.Factory>()
    protected val mockRootTaskDisplayAreaOrganizer = mock<RootTaskDisplayAreaOrganizer>()
    protected val mockShellCommandHandler = mock<ShellCommandHandler>()
    protected val mockWindowManager = mock<IWindowManager>()
    protected val mockInteractionJankMonitor = mock<InteractionJankMonitor>()
    protected val mockGenericLinksParser = mock<AppToWebGenericLinksParser>()
    protected val mockUserHandle = mock<UserHandle>()
    protected val mockAssistContentRequester = mock<AssistContentRequester>()
    protected val bgExecutor = TestShellExecutor()
    protected val mockMultiInstanceHelper = mock<MultiInstanceHelper>()
    private val mockWindowDecorViewHostSupplier =
        mock<WindowDecorViewHostSupplier<WindowDecorViewHost>>()
    protected val mockTasksLimiter = mock<DesktopTasksLimiter>()
    protected val mockFreeformTaskTransitionStarter = mock<FreeformTaskTransitionStarter>()
    protected val mockActivityOrientationChangeHandler =
        mock<DesktopActivityOrientationChangeHandler>()
    protected val mockMultiDisplayDragMoveIndicatorController =
        mock<MultiDisplayDragMoveIndicatorController>()
    protected val mockCompatUIHandler = mock<CompatUIHandler>()
    protected val mockDesksOrganizer = mock<DesksOrganizer>()
    protected val mockInputManager = mock<InputManager>()
    private val mockTaskPositionerFactory =
        mock<DesktopModeWindowDecorViewModel.TaskPositionerFactory>()
    protected val mockTaskPositioner = mock<TaskPositioner>()
    protected val mockAppHandleEducationController = mock<AppHandleEducationController>()
    protected val mockAppToWebEducationController = mock<AppToWebEducationController>()
    protected val mockFocusTransitionObserver = mock<FocusTransitionObserver>()
    protected val mockCaptionHandleRepository = mock<WindowDecorCaptionRepository>()
    protected val mockDesktopRepository: DesktopRepository = mock<DesktopRepository>()
    protected val mockRecentsTransitionHandler = mock<RecentsTransitionHandler>()
    protected val mockTilingWindowDecoration = mock<DesktopTilingDecorViewModel>()
    protected val mockWindowDecoration = mock<DesktopModeWindowDecoration>()
    protected val motionEvent = mock<MotionEvent>()
    private val displayLayout = mock<DisplayLayout>()
    private val display = mock<Display>()
    private val packageManager = mock<PackageManager>()
    protected val mockBubbleController = mock<BubbleController>()
    protected val homeComponentName = ComponentName(HOME_LAUNCHER_PACKAGE_NAME, /* class */ "")
    protected lateinit var spyContext: TestableContext
    private lateinit var desktopModeEventLogger: DesktopModeEventLogger
    protected lateinit var desktopState: FakeDesktopState
    protected lateinit var desktopConfig: FakeDesktopConfig

    private val transactionFactory = Supplier<SurfaceControl.Transaction> {
        SurfaceControl.Transaction()
    }
    protected val windowDecorByTaskIdSpy = spy(SparseArray<DesktopModeWindowDecoration>())

    protected lateinit var mockitoSession: StaticMockitoSession
    protected lateinit var shellInit: ShellInit
    internal lateinit var desktopModeOnInsetsChangedListener: DesktopModeOnInsetsChangedListener
    protected lateinit var desktopModeRecentsTransitionStateListener: RecentsTransitionStateListener
    protected lateinit var displayChangingListener:
            DisplayChangeController.OnDisplayChangingListener
    internal lateinit var desktopModeOnKeyguardChangedListener: DesktopModeKeyguardChangeListener
    protected lateinit var desktopModeWindowDecorViewModel: DesktopModeWindowDecorViewModel
    protected lateinit var desktopModeCompatPolicy: DesktopModeCompatPolicy
    protected lateinit var appHandleAndHeaderVisibilityHelper: AppHandleAndHeaderVisibilityHelper

    @Before
    fun earlySetUp() {
        desktopState = FakeDesktopState()
        desktopConfig = FakeDesktopConfig()
    }

    fun setUpCommon() {
        spyContext = spy(mContext)
        doNothing().`when`(spyContext).startActivity(any())
        doNothing().`when`(mockWindowDecoration).a11yAnnounceNewFocusedWindow()
        shellInit = ShellInit(testShellExecutor)
        windowDecorByTaskIdSpy.clear()
        spyContext.addMockSystemService(InputManager::class.java, mockInputManager)
        desktopModeEventLogger = mock<DesktopModeEventLogger>()
        whenever(mockDesktopUserRepositories.current).thenReturn(mockDesktopRepository)
        whenever(mockDisplayController.getDisplayContext(any())).thenReturn(spyContext)
        whenever(mockDisplayController.getDisplay(any())).thenReturn(display)
        whenever(display.type).thenReturn(Display.TYPE_INTERNAL)
        whenever(mockDesktopUserRepositories.getProfile(anyInt()))
            .thenReturn(mockDesktopRepository)
        desktopModeCompatPolicy = DesktopModeCompatPolicy(spyContext)
        appHandleAndHeaderVisibilityHelper = AppHandleAndHeaderVisibilityHelper(
            displayController = mockDisplayController,
            desktopModeCompatPolicy = desktopModeCompatPolicy,
            desktopState = desktopState,
            bubbleController = Optional.of(mockBubbleController),
        )
        desktopModeWindowDecorViewModel = DesktopModeWindowDecorViewModel(
            spyContext,
            testShellExecutor,
            mockMainHandler,
            mockMainChoreographer,
            mock<MainCoroutineDispatcher>(),
            mock<CoroutineScope>(),
            bgExecutor,
            shellInit,
            mockShellCommandHandler,
            mockWindowManager,
            mockTaskOrganizer,
            mockDesktopUserRepositories,
            mockDisplayController,
            mockShellController,
            displayInsetsController,
            mockSyncQueue,
            mockTransitions,
            Optional.of(mockDesktopTasksController),
            mockDesktopImmersiveController,
            mockGenericLinksParser,
            mockAssistContentRequester,
            mockWindowDecorViewHostSupplier,
            mockMultiInstanceHelper,
            mockDesktopModeWindowDecorFactory,
            mockInputMonitorFactory,
            transactionFactory,
            mockAppHeaderViewHolderFactory,
            mockAppHandleViewHolderFactory,
            mockRootTaskDisplayAreaOrganizer,
            windowDecorByTaskIdSpy,
            mockInteractionJankMonitor,
            Optional.of(mockTasksLimiter),
            mockAppHandleEducationController,
            mockAppToWebEducationController,
            appHandleAndHeaderVisibilityHelper,
            mockCaptionHandleRepository,
            Optional.of(mockActivityOrientationChangeHandler),
            mockTaskPositionerFactory,
            mockFocusTransitionObserver,
            desktopModeEventLogger,
            mock<DesktopModeUiEventLogger>(),
            mock<WindowDecorTaskResourceLoader>(),
            mockRecentsTransitionHandler,
            desktopModeCompatPolicy,
            mockTilingWindowDecoration,
            mockMultiDisplayDragMoveIndicatorController,
            mockCompatUIHandler,
            mockDesksOrganizer,
            desktopState,
            desktopConfig,
        )
        desktopModeWindowDecorViewModel.setSplitScreenController(mockSplitScreenController)
        whenever(mockDisplayController.getDisplayLayout(any())).thenReturn(mockDisplayLayout)
        whenever(mockDisplayLayout.stableInsets()).thenReturn(STABLE_INSETS)
        whenever(mockInputMonitorFactory.create(any(), any())).thenReturn(mockInputMonitor)
        whenever(
            mockTaskPositionerFactory.create(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        )
            .thenReturn(mockTaskPositioner)

        // InputChannel cannot be mocked because it passes to InputEventReceiver.
        val inputChannels = InputChannel.openInputChannelPair(TAG)
        inputChannels.first().dispose()
        whenever(mockInputMonitor.inputChannel).thenReturn(inputChannels[1])

        shellInit.init()

        val displayChangingListenerCaptor =
            argumentCaptor<DisplayChangeController.OnDisplayChangingListener>()
        verify(mockDisplayController)
            .addDisplayChangingController(displayChangingListenerCaptor.capture())
        displayChangingListener = displayChangingListenerCaptor.firstValue
        val insetsChangedCaptor =
            argumentCaptor<DesktopModeWindowDecorViewModel.DesktopModeOnInsetsChangedListener>()
        verify(displayInsetsController)
            .addGlobalInsetsChangedListener(insetsChangedCaptor.capture())
        desktopModeOnInsetsChangedListener = insetsChangedCaptor.firstValue
        val recentsTransitionStateListenerCaptor = argumentCaptor<RecentsTransitionStateListener>()
        if (Flags.enableDesktopRecentsTransitionsCornersBugfix()) {
            verify(mockRecentsTransitionHandler)
                .addTransitionStateListener(recentsTransitionStateListenerCaptor.capture())
            desktopModeRecentsTransitionStateListener =
                recentsTransitionStateListenerCaptor.firstValue
        }
        val keyguardChangedCaptor =
            argumentCaptor<DesktopModeKeyguardChangeListener>()
        verify(mockShellController).addKeyguardChangeListener(keyguardChangedCaptor.capture())
        desktopModeOnKeyguardChangedListener = keyguardChangedCaptor.firstValue
        whenever(mockDisplayController.getDisplayLayout(anyInt())).thenReturn(displayLayout)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(STABLE_BOUNDS)
        }
        spyContext.setMockPackageManager(packageManager)
        whenever(packageManager.getHomeActivities(ArrayList())).thenReturn(homeComponentName)
        whenever(mockDesktopTasksController.getNextFocusedTask(any())).thenReturn(testTaskId)
        whenever(windowDecorByTaskIdSpy.get(testTaskId)).thenReturn(mockWindowDecoration)
    }

    @After
    fun tearDown() {
        mockitoSession.finishMocking()
    }

    protected fun createTask(
        displayId: Int = DEFAULT_DISPLAY,
        @WindowingMode windowingMode: Int,
        activityType: Int = ACTIVITY_TYPE_STANDARD,
        activityInfo: ActivityInfo = ActivityInfo(),
        requestingImmersive: Boolean = false
    ): RunningTaskInfo {
        return TestRunningTaskInfoBuilder()
            .setDisplayId(displayId)
            .setWindowingMode(windowingMode)
            .setVisible(true)
            .setActivityType(activityType)
            .build().apply {
                topActivityInfo = activityInfo
                isResizeable = true
                requestedVisibleTypes = if (requestingImmersive) {
                    statusBars().inv()
                } else {
                    statusBars()
                }
                userId = context.userId
            }
    }

    protected fun setUpMockDecorationForTask(
        task: RunningTaskInfo,
        windowDecorationActions: KArgumentCaptor<WindowDecorationActions> =
            argumentCaptor<WindowDecorationActions>()
    ): DesktopModeWindowDecoration {
        val decoration = Mockito.mock(DesktopModeWindowDecoration::class.java)
        whenever(
            mockDesktopModeWindowDecorFactory.create(
                any(), any(), any(), any(), any(), any(), any(), eq(task), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(),
                windowDecorationActions.capture())
        ).thenReturn(decoration)
        decoration.mTaskInfo = task
        whenever(decoration.user).thenReturn(mockUserHandle)
        if (task.windowingMode == WINDOWING_MODE_MULTI_WINDOW) {
            whenever(mockSplitScreenController.isTaskInSplitScreen(task.taskId))
                .thenReturn(true)
        }
        whenever(decoration.calculateValidDragArea()).thenReturn(Rect(0, 60, 2560, 1600))
        return decoration
    }


    protected fun onTaskOpening(task: RunningTaskInfo, leash: SurfaceControl = SurfaceControl()) {
        desktopModeWindowDecorViewModel.onTaskOpening(
            task,
            leash,
            StubTransaction(),
            StubTransaction()
        )
    }

    protected fun onTaskChanging(
        task: RunningTaskInfo,
        leash: SurfaceControl = SurfaceControl(),
    ) {
        desktopModeWindowDecorViewModel.onTaskChanging(
            task,
            leash,
            StubTransaction(),
            StubTransaction(),
        )
    }

    protected fun RunningTaskInfo.setWindowingMode(@WindowingMode mode: Int) {
        configuration.windowConfiguration.windowingMode = mode
    }

    protected fun RunningTaskInfo.setActivityType(type: Int) {
        configuration.windowConfiguration.activityType = type
    }

    companion object {
        const val TAG = "DesktopModeWindowDecorViewModelTestsBase"
        val STABLE_INSETS = Rect(0, 100, 0, 0)
        val INITIAL_BOUNDS = Rect(0, 0, 100, 100)
        val STABLE_BOUNDS = Rect(0, 0, 1000, 1000)
        val HOME_LAUNCHER_PACKAGE_NAME = "com.android.launcher"

        val testTaskId = -1234
    }
}

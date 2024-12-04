/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.desktopmode

import android.app.ActivityManager.RecentTaskInfo
import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.ACTIVITY_TYPE_HOME
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ActivityInfo.CONFIG_DENSITY
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.os.Binder
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl
import android.view.WindowManager
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.DisplayAreaInfo
import android.window.IWindowContainerToken
import android.window.RemoteTransition
import android.window.TransitionRequestInfo
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_LAUNCH_TASK
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_PENDING_INTENT
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REMOVE_TASK
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REORDER
import android.window.WindowContainerTransaction.HierarchyOp.LAUNCH_KEY_TASK_ID
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn
import com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession
import com.android.dx.mockito.inline.extended.ExtendedMockito.never
import com.android.dx.mockito.inline.extended.StaticMockitoSession
import com.android.window.flags.Flags
import com.android.wm.shell.MockToken
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.LaunchAdjacentController
import com.android.wm.shell.common.MultiInstanceHelper
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.common.desktopmode.DesktopModeTransitionSource.UNKNOWN
import com.android.wm.shell.common.split.SplitScreenConstants
import com.android.wm.shell.desktopmode.DesktopTestHelpers.Companion.createFreeformTask
import com.android.wm.shell.desktopmode.DesktopTestHelpers.Companion.createFullscreenTask
import com.android.wm.shell.desktopmode.DesktopTestHelpers.Companion.createHomeTask
import com.android.wm.shell.desktopmode.DesktopTestHelpers.Companion.createSplitScreenTask
import com.android.wm.shell.draganddrop.DragAndDropController
import com.android.wm.shell.recents.RecentTasksController
import com.android.wm.shell.recents.RecentsTransitionHandler
import com.android.wm.shell.recents.RecentsTransitionStateListener
import com.android.wm.shell.shared.DesktopModeStatus
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.OneShotRemoteHandler
import com.android.wm.shell.transition.TestRemoteTransition
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.ENABLE_SHELL_TRANSITIONS
import com.android.wm.shell.transition.Transitions.TransitionHandler
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.Optional
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isA
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when` as whenever
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.capture
import org.mockito.quality.Strictness

/**
 * Test class for {@link DesktopTasksController}
 *
 * Usage: atest WMShellUnitTests:DesktopTasksControllerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopTasksControllerTest : ShellTestCase() {

  @JvmField @Rule val setFlagsRule = SetFlagsRule()

  @Mock lateinit var testExecutor: ShellExecutor
  @Mock lateinit var shellCommandHandler: ShellCommandHandler
  @Mock lateinit var shellController: ShellController
  @Mock lateinit var displayController: DisplayController
  @Mock lateinit var displayLayout: DisplayLayout
  @Mock lateinit var shellTaskOrganizer: ShellTaskOrganizer
  @Mock lateinit var syncQueue: SyncTransactionQueue
  @Mock lateinit var rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer
  @Mock lateinit var transitions: Transitions
  @Mock lateinit var exitDesktopTransitionHandler: ExitDesktopTaskTransitionHandler
  @Mock lateinit var enterDesktopTransitionHandler: EnterDesktopTaskTransitionHandler
  @Mock
  lateinit var toggleResizeDesktopTaskTransitionHandler: ToggleResizeDesktopTaskTransitionHandler
  @Mock lateinit var dragToDesktopTransitionHandler: DragToDesktopTransitionHandler
  @Mock lateinit var launchAdjacentController: LaunchAdjacentController
  @Mock lateinit var splitScreenController: SplitScreenController
  @Mock lateinit var recentsTransitionHandler: RecentsTransitionHandler
  @Mock lateinit var dragAndDropController: DragAndDropController
  @Mock lateinit var multiInstanceHelper: MultiInstanceHelper
  @Mock lateinit var desktopModeLoggerTransitionObserver: DesktopModeLoggerTransitionObserver
  @Mock lateinit var desktopModeVisualIndicator: DesktopModeVisualIndicator
  @Mock lateinit var recentTasksController: RecentTasksController

  private lateinit var mockitoSession: StaticMockitoSession
  private lateinit var controller: DesktopTasksController
  private lateinit var shellInit: ShellInit
  private lateinit var desktopModeTaskRepository: DesktopModeTaskRepository
  private lateinit var desktopTasksLimiter: DesktopTasksLimiter
  private lateinit var recentsTransitionStateListener: RecentsTransitionStateListener

  private val shellExecutor = TestShellExecutor()

  // Mock running tasks are registered here so we can get the list from mock shell task organizer
  private val runningTasks = mutableListOf<RunningTaskInfo>()

  private val DISPLAY_DIMENSION_SHORT = 1600
  private val DISPLAY_DIMENSION_LONG = 2560
  private val DEFAULT_LANDSCAPE_BOUNDS = Rect(320, 200, 2240, 1400)
  private val DEFAULT_PORTRAIT_BOUNDS = Rect(200, 320, 1400, 2240)
  private val RESIZABLE_LANDSCAPE_BOUNDS = Rect(25, 680, 1575, 1880)
  private val RESIZABLE_PORTRAIT_BOUNDS = Rect(680, 200, 1880, 1400)
  private val UNRESIZABLE_LANDSCAPE_BOUNDS = Rect(25, 699, 1575, 1861)
  private val UNRESIZABLE_PORTRAIT_BOUNDS = Rect(830, 200, 1730, 1400)

  @Before
  fun setUp() {
    mockitoSession =
        mockitoSession()
            .strictness(Strictness.LENIENT)
            .spyStatic(DesktopModeStatus::class.java)
            .startMocking()
    whenever(DesktopModeStatus.isEnabled()).thenReturn(true)
    doReturn(true).`when` { DesktopModeStatus.isDesktopModeSupported(any()) }

    shellInit = spy(ShellInit(testExecutor))
    desktopModeTaskRepository = DesktopModeTaskRepository()
    desktopTasksLimiter =
        DesktopTasksLimiter(transitions, desktopModeTaskRepository, shellTaskOrganizer)

    whenever(shellTaskOrganizer.getRunningTasks(anyInt())).thenAnswer { runningTasks }
    whenever(transitions.startTransition(anyInt(), any(), isNull())).thenAnswer { Binder() }
    whenever(enterDesktopTransitionHandler.moveToDesktop(any(), any())).thenAnswer { Binder() }
    whenever(displayController.getDisplayLayout(anyInt())).thenReturn(displayLayout)
    whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
      (i.arguments.first() as Rect).set(STABLE_BOUNDS)
    }

    val tda = DisplayAreaInfo(MockToken().token(), DEFAULT_DISPLAY, 0)
    tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
    whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)).thenReturn(tda)

    controller = createController()
    controller.setSplitScreenController(splitScreenController)

    shellInit.init()

    val captor = ArgumentCaptor.forClass(RecentsTransitionStateListener::class.java)
    verify(recentsTransitionHandler).addTransitionStateListener(captor.capture())
    recentsTransitionStateListener = captor.value
  }

  private fun createController(): DesktopTasksController {
    return DesktopTasksController(
        context,
        shellInit,
        shellCommandHandler,
        shellController,
        displayController,
        shellTaskOrganizer,
        syncQueue,
        rootTaskDisplayAreaOrganizer,
        dragAndDropController,
        transitions,
        enterDesktopTransitionHandler,
        exitDesktopTransitionHandler,
        toggleResizeDesktopTaskTransitionHandler,
        dragToDesktopTransitionHandler,
        desktopModeTaskRepository,
        desktopModeLoggerTransitionObserver,
        launchAdjacentController,
        recentsTransitionHandler,
        multiInstanceHelper,
        shellExecutor,
        Optional.of(desktopTasksLimiter),
        recentTasksController)
  }

  @After
  fun tearDown() {
    mockitoSession.finishMocking()

    runningTasks.clear()
  }

  @Test
  fun instantiate_addInitCallback() {
    verify(shellInit).addInitCallback(any(), any<DesktopTasksController>())
  }

  @Test
  fun instantiate_flagOff_doNotAddInitCallback() {
    whenever(DesktopModeStatus.isEnabled()).thenReturn(false)
    clearInvocations(shellInit)

    createController()

    verify(shellInit, never()).addInitCallback(any(), any<DesktopTasksController>())
  }

  @Test
  @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
  fun showDesktopApps_allAppsInvisible_bringsToFront_desktopWallpaperDisabled() {
    val homeTask = setUpHomeTask()
    val task1 = setUpFreeformTask()
    val task2 = setUpFreeformTask()
    markTaskHidden(task1)
    markTaskHidden(task2)

    controller.showDesktopApps(DEFAULT_DISPLAY, RemoteTransition(TestRemoteTransition()))

    val wct = getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
    assertThat(wct.hierarchyOps).hasSize(3)
    // Expect order to be from bottom: home, task1, task2
    wct.assertReorderAt(index = 0, homeTask)
    wct.assertReorderAt(index = 1, task1)
    wct.assertReorderAt(index = 2, task2)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
  fun showDesktopApps_allAppsInvisible_bringsToFront_desktopWallpaperEnabled() {
    val task1 = setUpFreeformTask()
    val task2 = setUpFreeformTask()
    markTaskHidden(task1)
    markTaskHidden(task2)

    controller.showDesktopApps(DEFAULT_DISPLAY, RemoteTransition(TestRemoteTransition()))

    val wct = getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
    assertThat(wct.hierarchyOps).hasSize(3)
    // Expect order to be from bottom: wallpaper intent, task1, task2
    wct.assertPendingIntentAt(index = 0, desktopWallpaperIntent)
    wct.assertReorderAt(index = 1, task1)
    wct.assertReorderAt(index = 2, task2)
  }

  @Test
  @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
  fun showDesktopApps_appsAlreadyVisible_bringsToFront_desktopWallpaperDisabled() {
    val homeTask = setUpHomeTask()
    val task1 = setUpFreeformTask()
    val task2 = setUpFreeformTask()
    markTaskVisible(task1)
    markTaskVisible(task2)

    controller.showDesktopApps(DEFAULT_DISPLAY, RemoteTransition(TestRemoteTransition()))

    val wct = getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
    assertThat(wct.hierarchyOps).hasSize(3)
    // Expect order to be from bottom: home, task1, task2
    wct.assertReorderAt(index = 0, homeTask)
    wct.assertReorderAt(index = 1, task1)
    wct.assertReorderAt(index = 2, task2)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
  fun showDesktopApps_appsAlreadyVisible_bringsToFront_desktopWallpaperEnabled() {
    val task1 = setUpFreeformTask()
    val task2 = setUpFreeformTask()
    markTaskVisible(task1)
    markTaskVisible(task2)

    controller.showDesktopApps(DEFAULT_DISPLAY, RemoteTransition(TestRemoteTransition()))

    val wct = getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
    assertThat(wct.hierarchyOps).hasSize(3)
    // Expect order to be from bottom: wallpaper intent, task1, task2
    wct.assertPendingIntentAt(index = 0, desktopWallpaperIntent)
    wct.assertReorderAt(index = 1, task1)
    wct.assertReorderAt(index = 2, task2)
  }

  @Test
  @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
  fun showDesktopApps_someAppsInvisible_reordersAll_desktopWallpaperDisabled() {
    val homeTask = setUpHomeTask()
    val task1 = setUpFreeformTask()
    val task2 = setUpFreeformTask()
    markTaskHidden(task1)
    markTaskVisible(task2)

    controller.showDesktopApps(DEFAULT_DISPLAY, RemoteTransition(TestRemoteTransition()))

    val wct = getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
    assertThat(wct.hierarchyOps).hasSize(3)
    // Expect order to be from bottom: home, task1, task2
    wct.assertReorderAt(index = 0, homeTask)
    wct.assertReorderAt(index = 1, task1)
    wct.assertReorderAt(index = 2, task2)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
  fun showDesktopApps_someAppsInvisible_reordersAll_desktopWallpaperEnabled() {
    val task1 = setUpFreeformTask()
    val task2 = setUpFreeformTask()
    markTaskHidden(task1)
    markTaskVisible(task2)

    controller.showDesktopApps(DEFAULT_DISPLAY, RemoteTransition(TestRemoteTransition()))

    val wct = getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
    assertThat(wct.hierarchyOps).hasSize(3)
    // Expect order to be from bottom: wallpaper intent, task1, task2
    wct.assertPendingIntentAt(index = 0, desktopWallpaperIntent)
    wct.assertReorderAt(index = 1, task1)
    wct.assertReorderAt(index = 2, task2)
  }

  @Test
  @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
  fun showDesktopApps_noActiveTasks_reorderHomeToTop_desktopWallpaperDisabled() {
    val homeTask = setUpHomeTask()

    controller.showDesktopApps(DEFAULT_DISPLAY, RemoteTransition(TestRemoteTransition()))

    val wct = getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
    assertThat(wct.hierarchyOps).hasSize(1)
    wct.assertReorderAt(index = 0, homeTask)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
  fun showDesktopApps_noActiveTasks_addDesktopWallpaper_desktopWallpaperEnabled() {
    controller.showDesktopApps(DEFAULT_DISPLAY, RemoteTransition(TestRemoteTransition()))

    val wct = getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
    wct.assertPendingIntentAt(index = 0, desktopWallpaperIntent)
  }

  @Test
  @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
  fun showDesktopApps_twoDisplays_bringsToFrontOnlyOneDisplay_desktopWallpaperDisabled() {
    val homeTaskDefaultDisplay = setUpHomeTask(DEFAULT_DISPLAY)
    val taskDefaultDisplay = setUpFreeformTask(DEFAULT_DISPLAY)
    setUpHomeTask(SECOND_DISPLAY)
    val taskSecondDisplay = setUpFreeformTask(SECOND_DISPLAY)
    markTaskHidden(taskDefaultDisplay)
    markTaskHidden(taskSecondDisplay)

    controller.showDesktopApps(DEFAULT_DISPLAY, RemoteTransition(TestRemoteTransition()))

    val wct = getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
    assertThat(wct.hierarchyOps).hasSize(2)
    // Expect order to be from bottom: home, task
    wct.assertReorderAt(index = 0, homeTaskDefaultDisplay)
    wct.assertReorderAt(index = 1, taskDefaultDisplay)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
  fun showDesktopApps_twoDisplays_bringsToFrontOnlyOneDisplay_desktopWallpaperEnabled() {
    val taskDefaultDisplay = setUpFreeformTask(DEFAULT_DISPLAY)
    setUpHomeTask(SECOND_DISPLAY)
    val taskSecondDisplay = setUpFreeformTask(SECOND_DISPLAY)
    markTaskHidden(taskDefaultDisplay)
    markTaskHidden(taskSecondDisplay)

    controller.showDesktopApps(DEFAULT_DISPLAY, RemoteTransition(TestRemoteTransition()))

    val wct = getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
    assertThat(wct.hierarchyOps).hasSize(2)
    // Expect order to be from bottom: wallpaper intent, task
    wct.assertPendingIntentAt(index = 0, desktopWallpaperIntent)
    wct.assertReorderAt(index = 1, taskDefaultDisplay)
  }

  @Test
  fun showDesktopApps_dontReorderMinimizedTask() {
    val homeTask = setUpHomeTask()
    val freeformTask = setUpFreeformTask()
    val minimizedTask = setUpFreeformTask()
    markTaskHidden(freeformTask)
    markTaskHidden(minimizedTask)
    desktopModeTaskRepository.minimizeTask(DEFAULT_DISPLAY, minimizedTask.taskId)

    controller.showDesktopApps(DEFAULT_DISPLAY, RemoteTransition(TestRemoteTransition()))

    val wct = getLatestWct(type = TRANSIT_TO_FRONT, handlerClass = OneShotRemoteHandler::class.java)
    assertThat(wct.hierarchyOps).hasSize(2)
    // Reorder home and freeform task to top, don't reorder the minimized task
    wct.assertReorderAt(index = 0, homeTask, toTop = true)
    wct.assertReorderAt(index = 1, freeformTask, toTop = true)
  }

  @Test
  fun getVisibleTaskCount_noTasks_returnsZero() {
    assertThat(controller.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(0)
  }

  @Test
  fun getVisibleTaskCount_twoTasks_bothVisible_returnsTwo() {
    setUpHomeTask()
    setUpFreeformTask().also(::markTaskVisible)
    setUpFreeformTask().also(::markTaskVisible)
    assertThat(controller.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(2)
  }

  @Test
  fun getVisibleTaskCount_twoTasks_oneVisible_returnsOne() {
    setUpHomeTask()
    setUpFreeformTask().also(::markTaskVisible)
    setUpFreeformTask().also(::markTaskHidden)
    assertThat(controller.getVisibleTaskCount(DEFAULT_DISPLAY)).isEqualTo(1)
  }

  @Test
  fun getVisibleTaskCount_twoTasksVisibleOnDifferentDisplays_returnsOne() {
    setUpHomeTask()
    setUpFreeformTask(DEFAULT_DISPLAY).also(::markTaskVisible)
    setUpFreeformTask(SECOND_DISPLAY).also(::markTaskVisible)
    assertThat(controller.getVisibleTaskCount(SECOND_DISPLAY)).isEqualTo(1)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS)
  fun moveToDesktop_landscapeDevice_resizable_undefinedOrientation_defaultLandscapeBounds() {
    val task = setUpFullscreenTask()
    setUpLandscapeDisplay()

    controller.moveToDesktop(task, transitionSource = UNKNOWN)
    val wct = getLatestEnterDesktopWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_LANDSCAPE_BOUNDS)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS)
  fun moveToDesktop_landscapeDevice_resizable_landscapeOrientation_defaultLandscapeBounds() {
    val task = setUpFullscreenTask(screenOrientation = SCREEN_ORIENTATION_LANDSCAPE)
    setUpLandscapeDisplay()

    controller.moveToDesktop(task, transitionSource = UNKNOWN)
    val wct = getLatestEnterDesktopWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_LANDSCAPE_BOUNDS)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS)
  fun moveToDesktop_landscapeDevice_resizable_portraitOrientation_resizablePortraitBounds() {
    val task =
        setUpFullscreenTask(screenOrientation = SCREEN_ORIENTATION_PORTRAIT, shouldLetterbox = true)
    setUpLandscapeDisplay()

    controller.moveToDesktop(task, transitionSource = UNKNOWN)
    val wct = getLatestEnterDesktopWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(RESIZABLE_PORTRAIT_BOUNDS)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS)
  fun moveToDesktop_landscapeDevice_unResizable_landscapeOrientation_defaultLandscapeBounds() {
    val task =
        setUpFullscreenTask(isResizable = false, screenOrientation = SCREEN_ORIENTATION_LANDSCAPE)
    setUpLandscapeDisplay()

    controller.moveToDesktop(task, transitionSource = UNKNOWN)
    val wct = getLatestEnterDesktopWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_LANDSCAPE_BOUNDS)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS)
  fun moveToDesktop_landscapeDevice_unResizable_portraitOrientation_unResizablePortraitBounds() {
    val task =
        setUpFullscreenTask(
            isResizable = false,
            screenOrientation = SCREEN_ORIENTATION_PORTRAIT,
            shouldLetterbox = true)
    setUpLandscapeDisplay()

    controller.moveToDesktop(task, transitionSource = UNKNOWN)
    val wct = getLatestEnterDesktopWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(UNRESIZABLE_PORTRAIT_BOUNDS)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS)
  fun moveToDesktop_portraitDevice_resizable_undefinedOrientation_defaultPortraitBounds() {
    val task = setUpFullscreenTask(deviceOrientation = ORIENTATION_PORTRAIT)
    setUpPortraitDisplay()

    controller.moveToDesktop(task, transitionSource = UNKNOWN)
    val wct = getLatestEnterDesktopWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_PORTRAIT_BOUNDS)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS)
  fun moveToDesktop_portraitDevice_resizable_portraitOrientation_defaultPortraitBounds() {
    val task =
        setUpFullscreenTask(
            deviceOrientation = ORIENTATION_PORTRAIT,
            screenOrientation = SCREEN_ORIENTATION_PORTRAIT)
    setUpPortraitDisplay()

    controller.moveToDesktop(task, transitionSource = UNKNOWN)
    val wct = getLatestEnterDesktopWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_PORTRAIT_BOUNDS)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS)
  fun moveToDesktop_portraitDevice_resizable_landscapeOrientation_resizableLandscapeBounds() {
    val task =
        setUpFullscreenTask(
            deviceOrientation = ORIENTATION_PORTRAIT,
            screenOrientation = SCREEN_ORIENTATION_LANDSCAPE,
            shouldLetterbox = true)
    setUpPortraitDisplay()

    controller.moveToDesktop(task, transitionSource = UNKNOWN)
    val wct = getLatestEnterDesktopWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(RESIZABLE_LANDSCAPE_BOUNDS)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS)
  fun moveToDesktop_portraitDevice_unResizable_portraitOrientation_defaultPortraitBounds() {
    val task =
        setUpFullscreenTask(
            isResizable = false,
            deviceOrientation = ORIENTATION_PORTRAIT,
            screenOrientation = SCREEN_ORIENTATION_PORTRAIT)
    setUpPortraitDisplay()

    controller.moveToDesktop(task, transitionSource = UNKNOWN)
    val wct = getLatestEnterDesktopWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_PORTRAIT_BOUNDS)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS)
  fun moveToDesktop_portraitDevice_unResizable_landscapeOrientation_unResizableLandscapeBounds() {
    val task =
        setUpFullscreenTask(
            isResizable = false,
            deviceOrientation = ORIENTATION_PORTRAIT,
            screenOrientation = SCREEN_ORIENTATION_LANDSCAPE,
            shouldLetterbox = true)
    setUpPortraitDisplay()

    controller.moveToDesktop(task, transitionSource = UNKNOWN)
    val wct = getLatestEnterDesktopWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(UNRESIZABLE_LANDSCAPE_BOUNDS)
  }

  @Test
  fun moveToDesktop_tdaFullscreen_windowingModeSetToFreeform() {
    val task = setUpFullscreenTask()
    val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
    tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
    controller.moveToDesktop(task, transitionSource = UNKNOWN)
    val wct = getLatestEnterDesktopWct()
    assertThat(wct.changes[task.token.asBinder()]?.windowingMode).isEqualTo(WINDOWING_MODE_FREEFORM)
  }

  @Test
  fun moveToDesktop_tdaFreeform_windowingModeSetToUndefined() {
    val task = setUpFullscreenTask()
    val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
    tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM
    controller.moveToDesktop(task, transitionSource = UNKNOWN)
    val wct = getLatestEnterDesktopWct()
    assertThat(wct.changes[task.token.asBinder()]?.windowingMode)
        .isEqualTo(WINDOWING_MODE_UNDEFINED)
  }

  @Test
  fun moveToDesktop_nonExistentTask_doesNothing() {
    controller.moveToDesktop(999, transitionSource = UNKNOWN)
    verifyEnterDesktopWCTNotExecuted()
  }

  @Test
  fun moveToDesktop_nonRunningTask_launchesInFreeform() {
    whenever(shellTaskOrganizer.getRunningTaskInfo(anyInt())).thenReturn(null)

    val task = createTaskInfo(1)

    whenever(recentTasksController.findTaskInBackground(anyInt())).thenReturn(task)

    controller.moveToDesktop(task.taskId, transitionSource = UNKNOWN)
    with(getLatestEnterDesktopWct()) {
      assertLaunchTaskAt(0, task.taskId, WINDOWING_MODE_FREEFORM)
    }
  }

  @Test
  fun moveToDesktop_topActivityTranslucent_doesNothing() {
    setFlagsRule.enableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY)
    val task =
        setUpFullscreenTask().apply {
          isTopActivityTransparent = true
          numActivities = 1
        }

    controller.moveToDesktop(task, transitionSource = UNKNOWN)
    verifyEnterDesktopWCTNotExecuted()
  }

  @Test
  fun moveToDesktop_systemUIActivity_doesNothing() {
    val task = setUpFullscreenTask()

    // Set task as systemUI package
    val systemUIPackageName = context.resources.getString(
      com.android.internal.R.string.config_systemUi)
    val baseComponent = ComponentName(systemUIPackageName, /* class */ "")
    task.baseActivity = baseComponent

    controller.moveToDesktop(task, transitionSource = UNKNOWN)
    verifyEnterDesktopWCTNotExecuted()
  }

  @Test
  fun moveToDesktop_deviceSupported_taskIsMovedToDesktop() {
    val task = setUpFullscreenTask()

    controller.moveToDesktop(task, transitionSource = UNKNOWN)

    val wct = getLatestEnterDesktopWct()
    assertThat(wct.changes[task.token.asBinder()]?.windowingMode).isEqualTo(WINDOWING_MODE_FREEFORM)
  }

  @Test
  @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
  fun moveToDesktop_otherFreeformTasksBroughtToFront_desktopWallpaperDisabled() {
    val homeTask = setUpHomeTask()
    val freeformTask = setUpFreeformTask()
    val fullscreenTask = setUpFullscreenTask()
    markTaskHidden(freeformTask)

    controller.moveToDesktop(fullscreenTask, transitionSource = UNKNOWN)

    with(getLatestEnterDesktopWct()) {
      // Operations should include home task, freeform task
      assertThat(hierarchyOps).hasSize(3)
      assertReorderSequence(homeTask, freeformTask, fullscreenTask)
      assertThat(changes[fullscreenTask.token.asBinder()]?.windowingMode)
          .isEqualTo(WINDOWING_MODE_FREEFORM)
    }
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
  fun moveToDesktop_otherFreeformTasksBroughtToFront_desktopWallpaperEnabled() {
    val freeformTask = setUpFreeformTask()
    val fullscreenTask = setUpFullscreenTask()
    markTaskHidden(freeformTask)

    controller.moveToDesktop(fullscreenTask, transitionSource = UNKNOWN)

    with(getLatestEnterDesktopWct()) {
      // Operations should include wallpaper intent, freeform task, fullscreen task
      assertThat(hierarchyOps).hasSize(3)
      assertPendingIntentAt(index = 0, desktopWallpaperIntent)
      assertReorderAt(index = 1, freeformTask)
      assertReorderAt(index = 2, fullscreenTask)
      assertThat(changes[fullscreenTask.token.asBinder()]?.windowingMode)
          .isEqualTo(WINDOWING_MODE_FREEFORM)
    }
  }

  @Test
  fun moveToDesktop_onlyFreeformTasksFromCurrentDisplayBroughtToFront() {
    setUpHomeTask(displayId = DEFAULT_DISPLAY)
    val freeformTaskDefault = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
    val fullscreenTaskDefault = setUpFullscreenTask(displayId = DEFAULT_DISPLAY)
    markTaskHidden(freeformTaskDefault)

    val homeTaskSecond = setUpHomeTask(displayId = SECOND_DISPLAY)
    val freeformTaskSecond = setUpFreeformTask(displayId = SECOND_DISPLAY)
    markTaskHidden(freeformTaskSecond)

    controller.moveToDesktop(fullscreenTaskDefault, transitionSource = UNKNOWN)

    with(getLatestEnterDesktopWct()) {
      // Check that hierarchy operations do not include tasks from second display
      assertThat(hierarchyOps.map { it.container }).doesNotContain(homeTaskSecond.token.asBinder())
      assertThat(hierarchyOps.map { it.container })
          .doesNotContain(freeformTaskSecond.token.asBinder())
    }
  }

  @Test
  fun moveToDesktop_splitTaskExitsSplit() {
    val task = setUpSplitScreenTask()
    controller.moveToDesktop(task, transitionSource = UNKNOWN)
    val wct = getLatestEnterDesktopWct()
    assertThat(wct.changes[task.token.asBinder()]?.windowingMode).isEqualTo(WINDOWING_MODE_FREEFORM)
    verify(splitScreenController)
        .prepareExitSplitScreen(any(), anyInt(), eq(SplitScreenController.EXIT_REASON_DESKTOP_MODE))
  }

  @Test
  fun moveToDesktop_fullscreenTaskDoesNotExitSplit() {
    val task = setUpFullscreenTask()
    controller.moveToDesktop(task, transitionSource = UNKNOWN)
    val wct = getLatestEnterDesktopWct()
    assertThat(wct.changes[task.token.asBinder()]?.windowingMode).isEqualTo(WINDOWING_MODE_FREEFORM)
    verify(splitScreenController, never())
        .prepareExitSplitScreen(any(), anyInt(), eq(SplitScreenController.EXIT_REASON_DESKTOP_MODE))
  }

  @Test
  fun moveToDesktop_bringsTasksOverLimit_dontShowBackTask() {
    val taskLimit = desktopTasksLimiter.getMaxTaskLimit()
    val homeTask = setUpHomeTask()
    val freeformTasks = (1..taskLimit).map { _ -> setUpFreeformTask() }
    val newTask = setUpFullscreenTask()

    controller.moveToDesktop(newTask, transitionSource = UNKNOWN)

    val wct = getLatestEnterDesktopWct()
    assertThat(wct.hierarchyOps.size).isEqualTo(taskLimit + 1) // visible tasks + home
    wct.assertReorderAt(0, homeTask)
    for (i in 1..<taskLimit) { // Skipping freeformTasks[0]
      wct.assertReorderAt(index = i, task = freeformTasks[i])
    }
    wct.assertReorderAt(taskLimit, newTask)
  }

  @Test
  fun moveToFullscreen_tdaFullscreen_windowingModeSetToUndefined() {
    val task = setUpFreeformTask()
    val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
    tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
    controller.moveToFullscreen(task.taskId, transitionSource = UNKNOWN)
    val wct = getLatestExitDesktopWct()
    assertThat(wct.changes[task.token.asBinder()]?.windowingMode)
        .isEqualTo(WINDOWING_MODE_UNDEFINED)
  }

  @Test
  fun moveToFullscreen_tdaFreeform_windowingModeSetToFullscreen() {
    val task = setUpFreeformTask()
    val tda = rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)!!
    tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FREEFORM
    controller.moveToFullscreen(task.taskId, transitionSource = UNKNOWN)
    val wct = getLatestExitDesktopWct()
    assertThat(wct.changes[task.token.asBinder()]?.windowingMode)
        .isEqualTo(WINDOWING_MODE_FULLSCREEN)
  }

  @Test
  fun moveToFullscreen_nonExistentTask_doesNothing() {
    controller.moveToFullscreen(999, transitionSource = UNKNOWN)
    verifyExitDesktopWCTNotExecuted()
  }

  @Test
  fun moveToFullscreen_secondDisplayTaskHasFreeform_secondDisplayNotAffected() {
    val taskDefaultDisplay = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
    val taskSecondDisplay = setUpFreeformTask(displayId = SECOND_DISPLAY)

    controller.moveToFullscreen(taskDefaultDisplay.taskId, transitionSource = UNKNOWN)

    with(getLatestExitDesktopWct()) {
      assertThat(changes.keys).contains(taskDefaultDisplay.token.asBinder())
      assertThat(changes.keys).doesNotContain(taskSecondDisplay.token.asBinder())
    }
  }

  @Test
  fun moveTaskToFront_postsWctWithReorderOp() {
    val task1 = setUpFreeformTask()
    setUpFreeformTask()

    controller.moveTaskToFront(task1)

    val wct = getLatestWct(type = TRANSIT_TO_FRONT)
    assertThat(wct.hierarchyOps).hasSize(1)
    wct.assertReorderAt(index = 0, task1)
  }

  @Test
  fun moveTaskToFront_bringsTasksOverLimit_minimizesBackTask() {
    val taskLimit = desktopTasksLimiter.getMaxTaskLimit()
    setUpHomeTask()
    val freeformTasks = (1..taskLimit + 1).map { _ -> setUpFreeformTask() }

    controller.moveTaskToFront(freeformTasks[0])

    val wct = getLatestWct(type = TRANSIT_TO_FRONT)
    assertThat(wct.hierarchyOps.size).isEqualTo(2) // move-to-front + minimize
    wct.assertReorderAt(0, freeformTasks[0], toTop = true)
    wct.assertReorderAt(1, freeformTasks[1], toTop = false)
  }

  @Test
  fun moveToNextDisplay_noOtherDisplays() {
    whenever(rootTaskDisplayAreaOrganizer.displayIds).thenReturn(intArrayOf(DEFAULT_DISPLAY))
    val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
    controller.moveToNextDisplay(task.taskId)
    verifyWCTNotExecuted()
  }

  @Test
  fun moveToNextDisplay_moveFromFirstToSecondDisplay() {
    // Set up two display ids
    whenever(rootTaskDisplayAreaOrganizer.displayIds)
        .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
    // Create a mock for the target display area: second display
    val secondDisplayArea = DisplayAreaInfo(MockToken().token(), SECOND_DISPLAY, 0)
    whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(SECOND_DISPLAY))
        .thenReturn(secondDisplayArea)

    val task = setUpFreeformTask(displayId = DEFAULT_DISPLAY)
    controller.moveToNextDisplay(task.taskId)
    with(getLatestWct(type = TRANSIT_CHANGE)) {
      assertThat(hierarchyOps).hasSize(1)
      assertThat(hierarchyOps[0].container).isEqualTo(task.token.asBinder())
      assertThat(hierarchyOps[0].isReparent).isTrue()
      assertThat(hierarchyOps[0].newParent).isEqualTo(secondDisplayArea.token.asBinder())
      assertThat(hierarchyOps[0].toTop).isTrue()
    }
  }

  @Test
  fun moveToNextDisplay_moveFromSecondToFirstDisplay() {
    // Set up two display ids
    whenever(rootTaskDisplayAreaOrganizer.displayIds)
        .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
    // Create a mock for the target display area: default display
    val defaultDisplayArea = DisplayAreaInfo(MockToken().token(), DEFAULT_DISPLAY, 0)
    whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY))
        .thenReturn(defaultDisplayArea)

    val task = setUpFreeformTask(displayId = SECOND_DISPLAY)
    controller.moveToNextDisplay(task.taskId)

    with(getLatestWct(type = TRANSIT_CHANGE)) {
      assertThat(hierarchyOps).hasSize(1)
      assertThat(hierarchyOps[0].container).isEqualTo(task.token.asBinder())
      assertThat(hierarchyOps[0].isReparent).isTrue()
      assertThat(hierarchyOps[0].newParent).isEqualTo(defaultDisplayArea.token.asBinder())
      assertThat(hierarchyOps[0].toTop).isTrue()
    }
  }

  @Test
  fun getTaskWindowingMode() {
    val fullscreenTask = setUpFullscreenTask()
    val freeformTask = setUpFreeformTask()

    assertThat(controller.getTaskWindowingMode(fullscreenTask.taskId))
        .isEqualTo(WINDOWING_MODE_FULLSCREEN)
    assertThat(controller.getTaskWindowingMode(freeformTask.taskId))
        .isEqualTo(WINDOWING_MODE_FREEFORM)
    assertThat(controller.getTaskWindowingMode(999)).isEqualTo(WINDOWING_MODE_UNDEFINED)
  }

  @Test
  fun onDesktopWindowClose_noActiveTasks() {
    val wct = WindowContainerTransaction()
    controller.onDesktopWindowClose(wct, 1 /* taskId */)
    // Doesn't modify transaction
    assertThat(wct.hierarchyOps).isEmpty()
  }

  @Test
  fun onDesktopWindowClose_singleActiveTask_noWallpaperActivityToken() {
    val task = setUpFreeformTask()
    val wct = WindowContainerTransaction()
    controller.onDesktopWindowClose(wct, task.taskId)
    // Doesn't modify transaction
    assertThat(wct.hierarchyOps).isEmpty()
  }

  @Test
  fun onDesktopWindowClose_singleActiveTask_hasWallpaperActivityToken() {
    val task = setUpFreeformTask()
    val wallpaperToken = MockToken().token()
    desktopModeTaskRepository.wallpaperActivityToken = wallpaperToken

    val wct = WindowContainerTransaction()
    controller.onDesktopWindowClose(wct, task.taskId)
    // Adds remove wallpaper operation
    wct.assertRemoveAt(index = 0, wallpaperToken)
  }

  @Test
  fun onDesktopWindowClose_multipleActiveTasks() {
    val task1 = setUpFreeformTask()
    setUpFreeformTask()
    val wallpaperToken = MockToken().token()
    desktopModeTaskRepository.wallpaperActivityToken = wallpaperToken

    val wct = WindowContainerTransaction()
    controller.onDesktopWindowClose(wct, task1.taskId)
    // Doesn't modify transaction
    assertThat(wct.hierarchyOps).isEmpty()
  }

  @Test
  fun handleRequest_fullscreenTask_freeformVisible_returnSwitchToFreeformWCT() {
    assumeTrue(ENABLE_SHELL_TRANSITIONS)

    val freeformTask = setUpFreeformTask()
    markTaskVisible(freeformTask)
    val fullscreenTask = createFullscreenTask()

    val result = controller.handleRequest(Binder(), createTransition(fullscreenTask))
    assertThat(result?.changes?.get(fullscreenTask.token.asBinder())?.windowingMode)
        .isEqualTo(WINDOWING_MODE_FREEFORM)
  }

  @Test
  fun handleRequest_fullscreenTaskToFreeform_underTaskLimit_dontMinimize() {
    assumeTrue(ENABLE_SHELL_TRANSITIONS)

    val freeformTask = setUpFreeformTask()
    markTaskVisible(freeformTask)
    val fullscreenTask = createFullscreenTask()

    val wct = controller.handleRequest(Binder(), createTransition(fullscreenTask))

    // Make sure we only reorder the new task to top (we don't reorder the old task to bottom)
    assertThat(wct?.hierarchyOps?.size).isEqualTo(1)
    wct!!.assertReorderAt(0, fullscreenTask, toTop = true)
  }

  @Test
  fun handleRequest_fullscreenTaskToFreeform_bringsTasksOverLimit_otherTaskIsMinimized() {
    assumeTrue(ENABLE_SHELL_TRANSITIONS)

    val taskLimit = desktopTasksLimiter.getMaxTaskLimit()
    val freeformTasks = (1..taskLimit).map { _ -> setUpFreeformTask() }
    freeformTasks.forEach { markTaskVisible(it) }
    val fullscreenTask = createFullscreenTask()

    val wct = controller.handleRequest(Binder(), createTransition(fullscreenTask))

    // Make sure we reorder the new task to top, and the back task to the bottom
    assertThat(wct!!.hierarchyOps.size).isEqualTo(2)
    wct.assertReorderAt(0, fullscreenTask, toTop = true)
    wct.assertReorderAt(1, freeformTasks[0], toTop = false)
  }

  @Test
  fun handleRequest_fullscreenTask_freeformNotVisible_returnNull() {
    assumeTrue(ENABLE_SHELL_TRANSITIONS)

    val freeformTask = setUpFreeformTask()
    markTaskHidden(freeformTask)
    val fullscreenTask = createFullscreenTask()
    assertThat(controller.handleRequest(Binder(), createTransition(fullscreenTask))).isNull()
  }

  @Test
  fun handleRequest_fullscreenTask_noOtherTasks_returnNull() {
    assumeTrue(ENABLE_SHELL_TRANSITIONS)

    val fullscreenTask = createFullscreenTask()
    assertThat(controller.handleRequest(Binder(), createTransition(fullscreenTask))).isNull()
  }

  @Test
  fun handleRequest_fullscreenTask_freeformTaskOnOtherDisplay_returnNull() {
    assumeTrue(ENABLE_SHELL_TRANSITIONS)

    val fullscreenTaskDefaultDisplay = createFullscreenTask(displayId = DEFAULT_DISPLAY)
    createFreeformTask(displayId = SECOND_DISPLAY)

    val result = controller.handleRequest(Binder(), createTransition(fullscreenTaskDefaultDisplay))
    assertThat(result).isNull()
  }

  @Test
  fun handleRequest_freeformTask_freeformVisible_aboveTaskLimit_minimize() {
    assumeTrue(ENABLE_SHELL_TRANSITIONS)

    val taskLimit = desktopTasksLimiter.getMaxTaskLimit()
    val freeformTasks = (1..taskLimit).map { _ -> setUpFreeformTask() }
    freeformTasks.forEach { markTaskVisible(it) }
    val newFreeformTask = createFreeformTask()

    val wct = controller.handleRequest(Binder(), createTransition(newFreeformTask, TRANSIT_OPEN))

    assertThat(wct?.hierarchyOps?.size).isEqualTo(1)
    wct!!.assertReorderAt(0, freeformTasks[0], toTop = false) // Reorder to the bottom
  }

  @Test
  fun handleRequest_freeformTask_freeformNotVisible_reorderedToTop() {
    assumeTrue(ENABLE_SHELL_TRANSITIONS)

    val freeformTask1 = setUpFreeformTask()
    markTaskHidden(freeformTask1)

    val freeformTask2 = createFreeformTask()
    val result =
        controller.handleRequest(Binder(), createTransition(freeformTask2, type = TRANSIT_TO_FRONT))

    assertThat(result?.hierarchyOps?.size).isEqualTo(2)
    result!!.assertReorderAt(1, freeformTask2, toTop = true)
  }

  @Test
  fun handleRequest_freeformTask_noOtherTasks_reorderedToTop() {
    assumeTrue(ENABLE_SHELL_TRANSITIONS)

    val task = createFreeformTask()
    val result = controller.handleRequest(Binder(), createTransition(task))

    assertThat(result?.hierarchyOps?.size).isEqualTo(1)
    result!!.assertReorderAt(0, task, toTop = true)
  }

  @Test
  fun handleRequest_freeformTask_freeformOnOtherDisplayOnly_reorderedToTop() {
    assumeTrue(ENABLE_SHELL_TRANSITIONS)

    val taskDefaultDisplay = createFreeformTask(displayId = DEFAULT_DISPLAY)
    val taskSecondDisplay = createFreeformTask(displayId = SECOND_DISPLAY)

    val result = controller.handleRequest(Binder(), createTransition(taskDefaultDisplay))
    assertThat(result?.hierarchyOps?.size).isEqualTo(1)
    result!!.assertReorderAt(0, taskDefaultDisplay, toTop = true)
  }

  @Test
  fun handleRequest_freeformTask_alreadyInDesktop_noOverrideDensity_noConfigDensityChange() {
    assumeTrue(ENABLE_SHELL_TRANSITIONS)
    whenever(DesktopModeStatus.useDesktopOverrideDensity()).thenReturn(false)

    val freeformTask1 = setUpFreeformTask()
    markTaskVisible(freeformTask1)

    val freeformTask2 = createFreeformTask()
    val result =
        controller.handleRequest(freeformTask2.token.asBinder(), createTransition(freeformTask2))
    assertFalse(result.anyDensityConfigChange(freeformTask2.token))
  }

  @Test
  fun handleRequest_freeformTask_alreadyInDesktop_overrideDensity_hasConfigDensityChange() {
    assumeTrue(ENABLE_SHELL_TRANSITIONS)
    whenever(DesktopModeStatus.useDesktopOverrideDensity()).thenReturn(true)

    val freeformTask1 = setUpFreeformTask()
    markTaskVisible(freeformTask1)

    val freeformTask2 = createFreeformTask()
    val result =
        controller.handleRequest(freeformTask2.token.asBinder(), createTransition(freeformTask2))
    assertTrue(result.anyDensityConfigChange(freeformTask2.token))
  }

  @Test
  fun handleRequest_notOpenOrToFrontTransition_returnNull() {
    assumeTrue(ENABLE_SHELL_TRANSITIONS)

    val task =
        TestRunningTaskInfoBuilder()
            .setActivityType(ACTIVITY_TYPE_STANDARD)
            .setWindowingMode(WINDOWING_MODE_FULLSCREEN)
            .build()
    val transition = createTransition(task = task, type = WindowManager.TRANSIT_CLOSE)
    val result = controller.handleRequest(Binder(), transition)
    assertThat(result).isNull()
  }

  @Test
  fun handleRequest_noTriggerTask_returnNull() {
    assumeTrue(ENABLE_SHELL_TRANSITIONS)
    assertThat(controller.handleRequest(Binder(), createTransition(task = null))).isNull()
  }

  @Test
  fun handleRequest_triggerTaskNotStandard_returnNull() {
    assumeTrue(ENABLE_SHELL_TRANSITIONS)
    val task = TestRunningTaskInfoBuilder().setActivityType(ACTIVITY_TYPE_HOME).build()
    assertThat(controller.handleRequest(Binder(), createTransition(task))).isNull()
  }

  @Test
  fun handleRequest_triggerTaskNotFullscreenOrFreeform_returnNull() {
    assumeTrue(ENABLE_SHELL_TRANSITIONS)

    val task =
        TestRunningTaskInfoBuilder()
            .setActivityType(ACTIVITY_TYPE_STANDARD)
            .setWindowingMode(WINDOWING_MODE_MULTI_WINDOW)
            .build()
    assertThat(controller.handleRequest(Binder(), createTransition(task))).isNull()
  }

  @Test
  fun handleRequest_recentsAnimationRunning_returnNull() {
    // Set up a visible freeform task so a fullscreen task should be converted to freeform
    val freeformTask = setUpFreeformTask()
    markTaskVisible(freeformTask)

    // Mark recents animation running
    recentsTransitionStateListener.onAnimationStateChanged(true)

    // Open a fullscreen task, check that it does not result in a WCT with changes to it
    val fullscreenTask = createFullscreenTask()
    assertThat(controller.handleRequest(Binder(), createTransition(fullscreenTask))).isNull()
  }

  @Test
  fun handleRequest_shouldLaunchAsModal_returnSwitchToFullscreenWCT() {
    setFlagsRule.enableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY)
    val task =
        setUpFreeformTask().apply {
          isTopActivityTransparent = true
          numActivities = 1
        }

    val result = controller.handleRequest(Binder(), createTransition(task))
    assertThat(result?.changes?.get(task.token.asBinder())?.windowingMode)
        .isEqualTo(WINDOWING_MODE_UNDEFINED) // inherited FULLSCREEN
  }

  @Test
  fun handleRequest_systemUIActivity_returnSwitchToFullscreenWCT() {
    val task = setUpFreeformTask()

    // Set task as systemUI package
    val systemUIPackageName = context.resources.getString(
      com.android.internal.R.string.config_systemUi)
    val baseComponent = ComponentName(systemUIPackageName, /* class */ "")
    task.baseActivity = baseComponent

    val result = controller.handleRequest(Binder(), createTransition(task))
    assertThat(result?.changes?.get(task.token.asBinder())?.windowingMode)
            .isEqualTo(WINDOWING_MODE_UNDEFINED) // inherited FULLSCREEN
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
  fun handleRequest_backTransition_singleActiveTask_noToken() {
    val task = setUpFreeformTask()
    val result = controller.handleRequest(Binder(), createTransition(task, type = TRANSIT_TO_BACK))
    // Doesn't handle request
    assertThat(result).isNull()
  }

  @Test
  @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
  fun handleRequest_backTransition_singleActiveTask_hasToken_desktopWallpaperDisabled() {
    desktopModeTaskRepository.wallpaperActivityToken = MockToken().token()

    val task = setUpFreeformTask()
    val result = controller.handleRequest(Binder(), createTransition(task, type = TRANSIT_TO_BACK))
    // Doesn't handle request
    assertThat(result).isNull()
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
  fun handleRequest_backTransition_singleActiveTask_hasToken_desktopWallpaperEnabled() {
    val wallpaperToken = MockToken().token()
    desktopModeTaskRepository.wallpaperActivityToken = wallpaperToken

    val task = setUpFreeformTask()
    val result = controller.handleRequest(Binder(), createTransition(task, type = TRANSIT_TO_BACK))
    assertThat(result).isNotNull()
    // Creates remove wallpaper transaction
    result!!.assertRemoveAt(index = 0, wallpaperToken)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_WALLPAPER_ACTIVITY)
  fun handleRequest_backTransition_multipleActiveTasks() {
    desktopModeTaskRepository.wallpaperActivityToken = MockToken().token()

    val task1 = setUpFreeformTask()
    setUpFreeformTask()
    val result = controller.handleRequest(Binder(), createTransition(task1, type = TRANSIT_TO_BACK))
    // Doesn't handle request
    assertThat(result).isNull()
  }

  @Test
  fun desktopTasksVisibilityChange_visible_setLaunchAdjacentDisabled() {
    val task = setUpFreeformTask()
    clearInvocations(launchAdjacentController)

    markTaskVisible(task)
    shellExecutor.flushAll()
    verify(launchAdjacentController).launchAdjacentEnabled = false
  }

  @Test
  fun desktopTasksVisibilityChange_invisible_setLaunchAdjacentEnabled() {
    val task = setUpFreeformTask()
    markTaskVisible(task)
    clearInvocations(launchAdjacentController)

    markTaskHidden(task)
    shellExecutor.flushAll()
    verify(launchAdjacentController).launchAdjacentEnabled = true
  }

  @Test
  fun moveFocusedTaskToDesktop_fullscreenTaskIsMovedToDesktop() {
    val task1 = setUpFullscreenTask()
    val task2 = setUpFullscreenTask()
    val task3 = setUpFullscreenTask()

    task1.isFocused = true
    task2.isFocused = false
    task3.isFocused = false

    controller.moveFocusedTaskToDesktop(DEFAULT_DISPLAY, transitionSource = UNKNOWN)

    val wct = getLatestEnterDesktopWct()
    assertThat(wct.changes[task1.token.asBinder()]?.windowingMode)
        .isEqualTo(WINDOWING_MODE_FREEFORM)
  }

  @Test
  fun moveFocusedTaskToDesktop_splitScreenTaskIsMovedToDesktop() {
    val task1 = setUpSplitScreenTask()
    val task2 = setUpFullscreenTask()
    val task3 = setUpFullscreenTask()
    val task4 = setUpSplitScreenTask()

    task1.isFocused = true
    task2.isFocused = false
    task3.isFocused = false
    task4.isFocused = true

    task4.parentTaskId = task1.taskId

    controller.moveFocusedTaskToDesktop(DEFAULT_DISPLAY, transitionSource = UNKNOWN)

    val wct = getLatestEnterDesktopWct()
    assertThat(wct.changes[task4.token.asBinder()]?.windowingMode)
        .isEqualTo(WINDOWING_MODE_FREEFORM)
    verify(splitScreenController)
        .prepareExitSplitScreen(any(), anyInt(), eq(SplitScreenController.EXIT_REASON_DESKTOP_MODE))
  }

  @Test
  fun moveFocusedTaskToFullscreen() {
    val task1 = setUpFreeformTask()
    val task2 = setUpFreeformTask()
    val task3 = setUpFreeformTask()

    task1.isFocused = false
    task2.isFocused = true
    task3.isFocused = false

    controller.enterFullscreen(DEFAULT_DISPLAY, transitionSource = UNKNOWN)

    val wct = getLatestExitDesktopWct()
    assertThat(wct.changes[task2.token.asBinder()]?.windowingMode)
        .isEqualTo(WINDOWING_MODE_UNDEFINED) // inherited FULLSCREEN
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS)
  fun dragToDesktop_landscapeDevice_resizable_undefinedOrientation_defaultLandscapeBounds() {
    val spyController = spy(controller)
    whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
    whenever(desktopModeVisualIndicator.updateIndicatorType(anyOrNull(), anyOrNull()))
        .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)

    val task = setUpFullscreenTask()
    setUpLandscapeDisplay()

    spyController.onDragPositioningEndThroughStatusBar(PointF(800f, 1280f), task)
    val wct = getLatestDragToDesktopWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_LANDSCAPE_BOUNDS)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS)
  fun dragToDesktop_landscapeDevice_resizable_landscapeOrientation_defaultLandscapeBounds() {
    val spyController = spy(controller)
    whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
    whenever(desktopModeVisualIndicator.updateIndicatorType(anyOrNull(), anyOrNull()))
        .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)

    val task = setUpFullscreenTask(screenOrientation = SCREEN_ORIENTATION_LANDSCAPE)
    setUpLandscapeDisplay()

    spyController.onDragPositioningEndThroughStatusBar(PointF(800f, 1280f), task)
    val wct = getLatestDragToDesktopWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_LANDSCAPE_BOUNDS)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS)
  fun dragToDesktop_landscapeDevice_resizable_portraitOrientation_resizablePortraitBounds() {
    val spyController = spy(controller)
    whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
    whenever(desktopModeVisualIndicator.updateIndicatorType(anyOrNull(), anyOrNull()))
        .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)

    val task =
        setUpFullscreenTask(screenOrientation = SCREEN_ORIENTATION_PORTRAIT, shouldLetterbox = true)
    setUpLandscapeDisplay()

    spyController.onDragPositioningEndThroughStatusBar(PointF(800f, 1280f), task)
    val wct = getLatestDragToDesktopWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(RESIZABLE_PORTRAIT_BOUNDS)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS)
  fun dragToDesktop_landscapeDevice_unResizable_landscapeOrientation_defaultLandscapeBounds() {
    val spyController = spy(controller)
    whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
    whenever(desktopModeVisualIndicator.updateIndicatorType(anyOrNull(), anyOrNull()))
        .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)

    val task =
        setUpFullscreenTask(isResizable = false, screenOrientation = SCREEN_ORIENTATION_LANDSCAPE)
    setUpLandscapeDisplay()

    spyController.onDragPositioningEndThroughStatusBar(PointF(800f, 1280f), task)
    val wct = getLatestDragToDesktopWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_LANDSCAPE_BOUNDS)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS)
  fun dragToDesktop_landscapeDevice_unResizable_portraitOrientation_unResizablePortraitBounds() {
    val spyController = spy(controller)
    whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
    whenever(desktopModeVisualIndicator.updateIndicatorType(anyOrNull(), anyOrNull()))
        .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)

    val task =
        setUpFullscreenTask(
            isResizable = false,
            screenOrientation = SCREEN_ORIENTATION_PORTRAIT,
            shouldLetterbox = true)
    setUpLandscapeDisplay()

    spyController.onDragPositioningEndThroughStatusBar(PointF(800f, 1280f), task)
    val wct = getLatestDragToDesktopWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(UNRESIZABLE_PORTRAIT_BOUNDS)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS)
  fun dragToDesktop_portraitDevice_resizable_undefinedOrientation_defaultPortraitBounds() {
    val spyController = spy(controller)
    whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
    whenever(desktopModeVisualIndicator.updateIndicatorType(anyOrNull(), anyOrNull()))
        .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)

    val task = setUpFullscreenTask(deviceOrientation = ORIENTATION_PORTRAIT)
    setUpPortraitDisplay()

    spyController.onDragPositioningEndThroughStatusBar(PointF(800f, 1280f), task)
    val wct = getLatestDragToDesktopWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_PORTRAIT_BOUNDS)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS)
  fun dragToDesktop_portraitDevice_resizable_portraitOrientation_defaultPortraitBounds() {
    val spyController = spy(controller)
    whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
    whenever(desktopModeVisualIndicator.updateIndicatorType(anyOrNull(), anyOrNull()))
        .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)

    val task =
        setUpFullscreenTask(
            deviceOrientation = ORIENTATION_PORTRAIT,
            screenOrientation = SCREEN_ORIENTATION_PORTRAIT)
    setUpPortraitDisplay()

    spyController.onDragPositioningEndThroughStatusBar(PointF(800f, 1280f), task)
    val wct = getLatestDragToDesktopWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_PORTRAIT_BOUNDS)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS)
  fun dragToDesktop_portraitDevice_resizable_landscapeOrientation_resizableLandscapeBounds() {
    val spyController = spy(controller)
    whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
    whenever(desktopModeVisualIndicator.updateIndicatorType(anyOrNull(), anyOrNull()))
        .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)

    val task =
        setUpFullscreenTask(
            deviceOrientation = ORIENTATION_PORTRAIT,
            screenOrientation = SCREEN_ORIENTATION_LANDSCAPE,
            shouldLetterbox = true)
    setUpPortraitDisplay()

    spyController.onDragPositioningEndThroughStatusBar(PointF(800f, 1280f), task)
    val wct = getLatestDragToDesktopWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(RESIZABLE_LANDSCAPE_BOUNDS)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS)
  fun dragToDesktop_portraitDevice_unResizable_portraitOrientation_defaultPortraitBounds() {
    val spyController = spy(controller)
    whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
    whenever(desktopModeVisualIndicator.updateIndicatorType(anyOrNull(), anyOrNull()))
        .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)

    val task =
        setUpFullscreenTask(
            isResizable = false,
            deviceOrientation = ORIENTATION_PORTRAIT,
            screenOrientation = SCREEN_ORIENTATION_PORTRAIT)
    setUpPortraitDisplay()

    spyController.onDragPositioningEndThroughStatusBar(PointF(800f, 1280f), task)
    val wct = getLatestDragToDesktopWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(DEFAULT_PORTRAIT_BOUNDS)
  }

  @Test
  @EnableFlags(Flags.FLAG_ENABLE_WINDOWING_DYNAMIC_INITIAL_BOUNDS)
  fun dragToDesktop_portraitDevice_unResizable_landscapeOrientation_unResizableLandscapeBounds() {
    val spyController = spy(controller)
    whenever(spyController.getVisualIndicator()).thenReturn(desktopModeVisualIndicator)
    whenever(desktopModeVisualIndicator.updateIndicatorType(anyOrNull(), anyOrNull()))
        .thenReturn(DesktopModeVisualIndicator.IndicatorType.TO_DESKTOP_INDICATOR)

    val task =
        setUpFullscreenTask(
            isResizable = false,
            deviceOrientation = ORIENTATION_PORTRAIT,
            screenOrientation = SCREEN_ORIENTATION_LANDSCAPE,
            shouldLetterbox = true)
    setUpPortraitDisplay()

    spyController.onDragPositioningEndThroughStatusBar(PointF(200f, 200f), task)
    val wct = getLatestDragToDesktopWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(UNRESIZABLE_LANDSCAPE_BOUNDS)
  }

  @Test
  fun onDesktopDragMove_endsOutsideValidDragArea_snapsToValidBounds() {
    val task = setUpFreeformTask()
    val mockSurface = mock(SurfaceControl::class.java)
    val mockDisplayLayout = mock(DisplayLayout::class.java)
    whenever(displayController.getDisplayLayout(task.displayId)).thenReturn(mockDisplayLayout)
    whenever(mockDisplayLayout.stableInsets()).thenReturn(Rect(0, 100, 2000, 2000))
    controller.onDragPositioningMove(task, mockSurface, 200f, Rect(100, -100, 500, 1000))

    controller.onDragPositioningEnd(
        task,
        Point(100, -100), /* position */
        PointF(200f, -200f), /* inputCoordinate */
        Rect(100, -100, 500, 1000), /* taskBounds */
        Rect(0, 50, 2000, 2000) /* validDragArea */)
    val rectAfterEnd = Rect(100, 50, 500, 1150)
    verify(transitions)
        .startTransition(
            eq(TRANSIT_CHANGE),
            Mockito.argThat { wct ->
              return@argThat wct.changes.any { (token, change) ->
                change.configuration.windowConfiguration.bounds == rectAfterEnd
              }
            },
            eq(null))
  }

  fun enterSplit_freeformTaskIsMovedToSplit() {
    val task1 = setUpFreeformTask()
    val task2 = setUpFreeformTask()
    val task3 = setUpFreeformTask()

    task1.isFocused = false
    task2.isFocused = true
    task3.isFocused = false

    controller.enterSplit(DEFAULT_DISPLAY, false)

    verify(splitScreenController)
        .requestEnterSplitSelect(
            task2,
            any(),
            SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT,
            task2.configuration.windowConfiguration.bounds)
  }

  @Test
  fun toggleBounds_togglesToStableBounds() {
    val bounds = Rect(0, 0, 100, 100)
    val task = setUpFreeformTask(DEFAULT_DISPLAY, bounds)

    controller.toggleDesktopTaskSize(task)
    // Assert bounds set to stable bounds
    val wct = getLatestToggleResizeDesktopTaskWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(STABLE_BOUNDS)
  }

  @Test
  fun toggleBounds_lastBoundsBeforeMaximizeSaved() {
    val bounds = Rect(0, 0, 100, 100)
    val task = setUpFreeformTask(DEFAULT_DISPLAY, bounds)

    controller.toggleDesktopTaskSize(task)
    assertThat(desktopModeTaskRepository.removeBoundsBeforeMaximize(task.taskId)).isEqualTo(bounds)
  }

  @Test
  fun toggleBounds_togglesFromStableBoundsToLastBoundsBeforeMaximize() {
    val boundsBeforeMaximize = Rect(0, 0, 100, 100)
    val task = setUpFreeformTask(DEFAULT_DISPLAY, boundsBeforeMaximize)

    // Maximize
    controller.toggleDesktopTaskSize(task)
    task.configuration.windowConfiguration.bounds.set(STABLE_BOUNDS)

    // Restore
    controller.toggleDesktopTaskSize(task)

    // Assert bounds set to last bounds before maximize
    val wct = getLatestToggleResizeDesktopTaskWct()
    assertThat(findBoundsChange(wct, task)).isEqualTo(boundsBeforeMaximize)
  }

  @Test
  fun toggleBounds_removesLastBoundsBeforeMaximizeAfterRestoringBounds() {
    val boundsBeforeMaximize = Rect(0, 0, 100, 100)
    val task = setUpFreeformTask(DEFAULT_DISPLAY, boundsBeforeMaximize)

    // Maximize
    controller.toggleDesktopTaskSize(task)
    task.configuration.windowConfiguration.bounds.set(STABLE_BOUNDS)

    // Restore
    controller.toggleDesktopTaskSize(task)

    // Assert last bounds before maximize removed after use
    assertThat(desktopModeTaskRepository.removeBoundsBeforeMaximize(task.taskId)).isNull()
  }

  private val desktopWallpaperIntent: Intent
    get() = Intent(context, DesktopWallpaperActivity::class.java)

  private fun setUpFreeformTask(
      displayId: Int = DEFAULT_DISPLAY,
      bounds: Rect? = null
  ): RunningTaskInfo {
    val task = createFreeformTask(displayId, bounds)
    val activityInfo = ActivityInfo()
    task.topActivityInfo = activityInfo
    whenever(shellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
    desktopModeTaskRepository.addActiveTask(displayId, task.taskId)
    desktopModeTaskRepository.addOrMoveFreeformTaskToTop(displayId, task.taskId)
    runningTasks.add(task)
    return task
  }

  private fun setUpHomeTask(displayId: Int = DEFAULT_DISPLAY): RunningTaskInfo {
    val task = createHomeTask(displayId)
    whenever(shellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
    runningTasks.add(task)
    return task
  }

  private fun setUpFullscreenTask(
      displayId: Int = DEFAULT_DISPLAY,
      isResizable: Boolean = true,
      windowingMode: Int = WINDOWING_MODE_FULLSCREEN,
      deviceOrientation: Int = ORIENTATION_LANDSCAPE,
      screenOrientation: Int = SCREEN_ORIENTATION_UNSPECIFIED,
      shouldLetterbox: Boolean = false
  ): RunningTaskInfo {
    val task = createFullscreenTask(displayId)
    val activityInfo = ActivityInfo()
    activityInfo.screenOrientation = screenOrientation
    with(task) {
      topActivityInfo = activityInfo
      isResizeable = isResizable
      configuration.orientation = deviceOrientation
      configuration.windowConfiguration.windowingMode = windowingMode

      if (shouldLetterbox) {
        if (deviceOrientation == ORIENTATION_LANDSCAPE &&
            screenOrientation == SCREEN_ORIENTATION_PORTRAIT) {
          // Letterbox to portrait size
          appCompatTaskInfo.topActivityBoundsLetterboxed = true
          appCompatTaskInfo.topActivityLetterboxWidth = 1200
          appCompatTaskInfo.topActivityLetterboxHeight = 1600
        } else if (deviceOrientation == ORIENTATION_PORTRAIT &&
            screenOrientation == SCREEN_ORIENTATION_LANDSCAPE) {
          // Letterbox to landscape size
          appCompatTaskInfo.topActivityBoundsLetterboxed = true
          appCompatTaskInfo.topActivityLetterboxWidth = 1600
          appCompatTaskInfo.topActivityLetterboxHeight = 1200
        }
      } else {
        appCompatTaskInfo.topActivityBoundsLetterboxed = false
      }

      if (deviceOrientation == ORIENTATION_LANDSCAPE) {
        configuration.windowConfiguration.appBounds =
            Rect(0, 0, DISPLAY_DIMENSION_LONG, DISPLAY_DIMENSION_SHORT)
      } else {
        configuration.windowConfiguration.appBounds =
            Rect(0, 0, DISPLAY_DIMENSION_SHORT, DISPLAY_DIMENSION_LONG)
      }
    }
    whenever(shellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
    runningTasks.add(task)
    return task
  }

  private fun setUpLandscapeDisplay() {
    whenever(displayLayout.width()).thenReturn(DISPLAY_DIMENSION_LONG)
    whenever(displayLayout.height()).thenReturn(DISPLAY_DIMENSION_SHORT)
  }

  private fun setUpPortraitDisplay() {
    whenever(displayLayout.width()).thenReturn(DISPLAY_DIMENSION_SHORT)
    whenever(displayLayout.height()).thenReturn(DISPLAY_DIMENSION_LONG)
  }

  private fun setUpSplitScreenTask(displayId: Int = DEFAULT_DISPLAY): RunningTaskInfo {
    val task = createSplitScreenTask(displayId)
    whenever(splitScreenController.isTaskInSplitScreen(task.taskId)).thenReturn(true)
    whenever(shellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
    runningTasks.add(task)
    return task
  }

  private fun markTaskVisible(task: RunningTaskInfo) {
    desktopModeTaskRepository.updateVisibleFreeformTasks(
        task.displayId, task.taskId, visible = true)
  }

  private fun markTaskHidden(task: RunningTaskInfo) {
    desktopModeTaskRepository.updateVisibleFreeformTasks(
        task.displayId, task.taskId, visible = false)
  }

  private fun getLatestWct(
      @WindowManager.TransitionType type: Int = TRANSIT_OPEN,
      handlerClass: Class<out TransitionHandler>? = null
  ): WindowContainerTransaction {
    val arg = ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
    if (ENABLE_SHELL_TRANSITIONS) {
      if (handlerClass == null) {
        verify(transitions).startTransition(eq(type), arg.capture(), isNull())
      } else {
        verify(transitions).startTransition(eq(type), arg.capture(), isA(handlerClass))
      }
    } else {
      verify(shellTaskOrganizer).applyTransaction(arg.capture())
    }
    return arg.value
  }

  private fun getLatestToggleResizeDesktopTaskWct(): WindowContainerTransaction {
    val arg: ArgumentCaptor<WindowContainerTransaction> =
        ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
    if (ENABLE_SHELL_TRANSITIONS) {
      verify(toggleResizeDesktopTaskTransitionHandler, atLeastOnce()).startTransition(capture(arg))
    } else {
      verify(shellTaskOrganizer).applyTransaction(capture(arg))
    }
    return arg.value
  }

  private fun getLatestEnterDesktopWct(): WindowContainerTransaction {
    val arg = ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
    if (ENABLE_SHELL_TRANSITIONS) {
      verify(enterDesktopTransitionHandler).moveToDesktop(arg.capture(), any())
    } else {
      verify(shellTaskOrganizer).applyTransaction(arg.capture())
    }
    return arg.value
  }

  private fun getLatestDragToDesktopWct(): WindowContainerTransaction {
    val arg: ArgumentCaptor<WindowContainerTransaction> =
        ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
    if (ENABLE_SHELL_TRANSITIONS) {
      verify(dragToDesktopTransitionHandler).finishDragToDesktopTransition(capture(arg))
    } else {
      verify(shellTaskOrganizer).applyTransaction(capture(arg))
    }
    return arg.value
  }

  private fun getLatestExitDesktopWct(): WindowContainerTransaction {
    val arg = ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
    if (ENABLE_SHELL_TRANSITIONS) {
      verify(exitDesktopTransitionHandler).startTransition(any(), arg.capture(), any(), any())
    } else {
      verify(shellTaskOrganizer).applyTransaction(arg.capture())
    }
    return arg.value
  }

  private fun findBoundsChange(wct: WindowContainerTransaction, task: RunningTaskInfo): Rect? =
      wct.changes[task.token.asBinder()]?.configuration?.windowConfiguration?.bounds

  private fun verifyWCTNotExecuted() {
    if (ENABLE_SHELL_TRANSITIONS) {
      verify(transitions, never()).startTransition(anyInt(), any(), isNull())
    } else {
      verify(shellTaskOrganizer, never()).applyTransaction(any())
    }
  }

  private fun verifyExitDesktopWCTNotExecuted() {
    if (ENABLE_SHELL_TRANSITIONS) {
      verify(exitDesktopTransitionHandler, never()).startTransition(any(), any(), any(), any())
    } else {
      verify(shellTaskOrganizer, never()).applyTransaction(any())
    }
  }

  private fun verifyEnterDesktopWCTNotExecuted() {
    if (ENABLE_SHELL_TRANSITIONS) {
      verify(enterDesktopTransitionHandler, never()).moveToDesktop(any(), any())
    } else {
      verify(shellTaskOrganizer, never()).applyTransaction(any())
    }
  }

  private fun createTransition(
      task: RunningTaskInfo?,
      @WindowManager.TransitionType type: Int = TRANSIT_OPEN
  ): TransitionRequestInfo {
    return TransitionRequestInfo(type, task, null /* remoteTransition */)
  }

  companion object {
    const val SECOND_DISPLAY = 2
    private val STABLE_BOUNDS = Rect(0, 0, 1000, 1000)
  }
}

private fun WindowContainerTransaction.assertIndexInBounds(index: Int) {
  assertWithMessage("WCT does not have a hierarchy operation at index $index")
      .that(hierarchyOps.size)
      .isGreaterThan(index)
}

private fun WindowContainerTransaction.assertReorderAt(
    index: Int,
    task: RunningTaskInfo,
    toTop: Boolean? = null
) {
  assertIndexInBounds(index)
  val op = hierarchyOps[index]
  assertThat(op.type).isEqualTo(HIERARCHY_OP_TYPE_REORDER)
  assertThat(op.container).isEqualTo(task.token.asBinder())
  toTop?.let { assertThat(op.toTop).isEqualTo(it) }
}

private fun WindowContainerTransaction.assertReorderSequence(vararg tasks: RunningTaskInfo) {
  for (i in tasks.indices) {
    assertReorderAt(i, tasks[i])
  }
}

private fun WindowContainerTransaction.assertRemoveAt(index: Int, token: WindowContainerToken) {
  assertIndexInBounds(index)
  val op = hierarchyOps[index]
  assertThat(op.type).isEqualTo(HIERARCHY_OP_TYPE_REMOVE_TASK)
  assertThat(op.container).isEqualTo(token.asBinder())
}

private fun WindowContainerTransaction.assertPendingIntentAt(index: Int, intent: Intent) {
  assertIndexInBounds(index)
  val op = hierarchyOps[index]
  assertThat(op.type).isEqualTo(HIERARCHY_OP_TYPE_PENDING_INTENT)
  assertThat(op.pendingIntent?.intent?.component).isEqualTo(intent.component)
}

private fun WindowContainerTransaction.assertLaunchTaskAt(
    index: Int,
    taskId: Int,
    windowingMode: Int
) {
  val keyLaunchWindowingMode = "android.activity.windowingMode"

  assertIndexInBounds(index)
  val op = hierarchyOps[index]
  assertThat(op.type).isEqualTo(HIERARCHY_OP_TYPE_LAUNCH_TASK)
  assertThat(op.launchOptions?.getInt(LAUNCH_KEY_TASK_ID)).isEqualTo(taskId)
  assertThat(op.launchOptions?.getInt(keyLaunchWindowingMode, WINDOWING_MODE_UNDEFINED))
      .isEqualTo(windowingMode)
}

private fun WindowContainerTransaction?.anyDensityConfigChange(
    token: WindowContainerToken
): Boolean {
  return this?.changes?.any { change ->
    change.key == token.asBinder() && ((change.value.configSetMask and CONFIG_DENSITY) != 0)
  } ?: false
}

private fun createTaskInfo(id: Int) =
    RecentTaskInfo().apply {
      taskId = id
      token = WindowContainerToken(mock(IWindowContainerToken::class.java))
    }

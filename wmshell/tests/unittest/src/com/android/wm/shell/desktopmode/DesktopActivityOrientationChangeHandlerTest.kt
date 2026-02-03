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

package com.android.wm.shell.desktopmode

import android.app.ActivityManager.RunningTaskInfo
import android.content.pm.ActivityInfo
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
import android.graphics.Rect
import android.os.Binder
import android.os.UserManager
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession
import com.android.dx.mockito.inline.extended.ExtendedMockito.never
import com.android.dx.mockito.inline.extended.StaticMockitoSession
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE
import com.android.window.flags.Flags.FLAG_RESPECT_ORIENTATION_CHANGE_FOR_UNRESIZEABLE
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.TaskStackListenerImpl
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFullscreenTask
import com.android.wm.shell.desktopmode.persistence.Desktop
import com.android.wm.shell.desktopmode.persistence.DesktopPersistentRepository
import com.android.wm.shell.desktopmode.persistence.DesktopRepositoryInitializer
import com.android.wm.shell.shared.desktopmode.FakeDesktopConfig
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import kotlin.test.assertNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.capture
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Test class for {@link DesktopActivityOrientationChangeHandler}
 *
 * Usage: atest WMShellUnitTests:DesktopActivityOrientationChangeHandlerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
@ExperimentalCoroutinesApi
@EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_MODE, FLAG_RESPECT_ORIENTATION_CHANGE_FOR_UNRESIZEABLE)
class DesktopActivityOrientationChangeHandlerTest : ShellTestCase() {
    private val testExecutor: ShellExecutor = mock()
    private val shellTaskOrganizer: ShellTaskOrganizer = mock()
    private val transitions: Transitions = mock()
    private val resizeTransitionHandler: ToggleResizeDesktopTaskTransitionHandler = mock()
    private val taskStackListener: TaskStackListenerImpl = mock()
    private val persistentRepository: DesktopPersistentRepository = mock()
    private val repositoryInitializer: DesktopRepositoryInitializer = mock()
    private val userManager: UserManager = mock()
    private val shellController: ShellController = mock()
    private val displayController: DisplayController = mock()
    private val displayLayout: DisplayLayout = mock()
    private val desktopState = FakeDesktopState()
    private val desktopConfig = FakeDesktopConfig()

    private lateinit var mockitoSession: StaticMockitoSession
    private lateinit var handler: DesktopActivityOrientationChangeHandler
    private lateinit var shellInit: ShellInit
    private lateinit var userRepositories: DesktopUserRepositories
    private lateinit var testScope: CoroutineScope

    // Mock running tasks are registered here so we can get the list from mock shell task organizer.
    private val runningTasks = mutableListOf<RunningTaskInfo>()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        mockitoSession = mockitoSession().strictness(Strictness.LENIENT).startMocking()
        desktopState.canEnterDesktopMode = true

        testScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        shellInit = spy(ShellInit(testExecutor))
        userRepositories =
            DesktopUserRepositories(
                shellInit,
                shellController,
                persistentRepository,
                repositoryInitializer,
                testScope,
                userManager,
                desktopState,
                desktopConfig,
            )
        whenever(shellTaskOrganizer.getRunningTasks(anyInt())).thenAnswer { runningTasks }
        whenever(transitions.startTransition(anyInt(), any(), isNull())).thenAnswer { Binder() }
        whenever(runBlocking { persistentRepository.readDesktop(any(), any()) })
            .thenReturn(Desktop.getDefaultInstance())
        whenever(displayController.getDisplayLayout(anyInt())).thenReturn(displayLayout)

        handler =
            DesktopActivityOrientationChangeHandler(
                context,
                shellInit,
                shellTaskOrganizer,
                taskStackListener,
                resizeTransitionHandler,
                userRepositories,
                displayController,
                desktopState,
            )

        shellInit.init()
    }

    @After
    fun tearDown() {
        mockitoSession.finishMocking()

        runningTasks.clear()
        testScope.cancel()
    }

    @Test
    fun instantiate_addInitCallback() {
        verify(shellInit).addInitCallback(any(), any<DesktopActivityOrientationChangeHandler>())
    }

    @Test
    fun instantiate_cannotEnterDesktopMode_doNotAddInitCallback() {
        desktopState.canEnterDesktopMode = false
        clearInvocations(shellInit)

        handler =
            DesktopActivityOrientationChangeHandler(
                context,
                shellInit,
                shellTaskOrganizer,
                taskStackListener,
                resizeTransitionHandler,
                userRepositories,
                displayController,
                desktopState,
            )

        verify(shellInit, never())
            .addInitCallback(any(), any<DesktopActivityOrientationChangeHandler>())
    }

    @Test
    fun handleActivityOrientationChange_resizeable_doNothing() {
        userRepositories.current.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        userRepositories.current.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val task = setUpFreeformTask()

        taskStackListener.onActivityRequestedOrientationChanged(
            task.taskId,
            SCREEN_ORIENTATION_LANDSCAPE,
        )

        verify(resizeTransitionHandler, never()).startTransition(any(), any(), any())
    }

    @Test
    fun handleActivityOrientationChange_nonResizeableFullscreen_doNothing() {
        userRepositories.current.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        userRepositories.current.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val task = createFullscreenTask()
        task.isResizeable = false
        val activityInfo = ActivityInfo()
        activityInfo.screenOrientation = SCREEN_ORIENTATION_PORTRAIT
        task.topActivityInfo = activityInfo
        whenever(shellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        userRepositories.current.addTask(
            DEFAULT_DISPLAY,
            task.taskId,
            isVisible = true,
            task.configuration.windowConfiguration.bounds,
        )
        runningTasks.add(task)

        taskStackListener.onActivityRequestedOrientationChanged(
            task.taskId,
            SCREEN_ORIENTATION_LANDSCAPE,
        )

        verify(resizeTransitionHandler, never()).startTransition(any(), any(), any())
    }

    @Test
    fun handleActivityOrientationChange_nonResizeablePortrait_requestSameOrientation_doNothing() {
        userRepositories.current.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        userRepositories.current.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val task = setUpFreeformTask(isResizeable = false)
        val newTask =
            setUpFreeformTask(
                isResizeable = false,
                orientation = SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            )

        handler.handleActivityOrientationChange(task, newTask)

        verify(resizeTransitionHandler, never()).startTransition(any(), any(), any())
    }

    @Test
    fun handleActivityOrientationChange_notInDesktopMode_doNothing() {
        userRepositories.current.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        userRepositories.current.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val task = setUpFreeformTask(isResizeable = false)
        userRepositories.current.updateTask(
            task.displayId,
            task.taskId,
            isVisible = false,
            task.configuration.windowConfiguration.bounds,
        )

        taskStackListener.onActivityRequestedOrientationChanged(
            task.taskId,
            SCREEN_ORIENTATION_LANDSCAPE,
        )

        verify(resizeTransitionHandler, never()).startTransition(any(), any(), any())
    }

    @Test
    fun handleActivityOrientationChange_nonResizeablePortrait_respectLandscapeRequest() {
        userRepositories.current.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        userRepositories.current.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val task = setUpFreeformTask(isResizeable = false)
        val oldBounds = task.configuration.windowConfiguration.bounds
        val newTask =
            setUpFreeformTask(isResizeable = false, orientation = SCREEN_ORIENTATION_LANDSCAPE)
        whenever(displayLayout.height()).thenReturn(800)
        whenever(displayLayout.width()).thenReturn(2000)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(Rect(0, 0, 2000, 800))
        }

        handler.handleActivityOrientationChange(task, newTask)

        val wct = getLatestResizeDesktopTaskWct()
        val finalBounds = findBoundsChange(wct, newTask)
        assertNotNull(finalBounds)
        val finalWidth = finalBounds.width()
        val finalHeight = finalBounds.height()
        // Bounds is landscape.
        assertTrue(finalWidth > finalHeight)
        // Aspect ratio remains the same.
        assertEquals(oldBounds.height() / oldBounds.width(), finalWidth / finalHeight)
        // Anchor point for resizing is at the center.
        assertEquals(oldBounds.centerX(), finalBounds.centerX())
    }

    @Test
    fun handleActivityOrientationChange_nonResizeableLandscape_respectPortraitRequest() {
        userRepositories.current.addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        userRepositories.current.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        val oldBounds = Rect(0, 0, 500, 200)
        val task =
            setUpFreeformTask(
                isResizeable = false,
                orientation = SCREEN_ORIENTATION_LANDSCAPE,
                bounds = oldBounds,
            )
        val newTask = setUpFreeformTask(isResizeable = false, bounds = oldBounds)
        whenever(displayLayout.height()).thenReturn(2000)
        whenever(displayLayout.width()).thenReturn(800)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(Rect(0, 0, 800, 2000))
        }

        handler.handleActivityOrientationChange(task, newTask)

        val wct = getLatestResizeDesktopTaskWct()
        val finalBounds = findBoundsChange(wct, newTask)
        assertNotNull(finalBounds)
        val finalWidth = finalBounds.width()
        val finalHeight = finalBounds.height()
        // Bounds is portrait.
        assertTrue(finalHeight > finalWidth)
        // Aspect ratio remains the same.
        assertEquals(oldBounds.width() / oldBounds.height(), finalHeight / finalWidth)
        // Anchor point for resizing is at the center.
        assertEquals(oldBounds.centerX(), finalBounds.centerX())
    }

    private fun setUpFreeformTask(
        displayId: Int = DEFAULT_DISPLAY,
        isResizeable: Boolean = true,
        orientation: Int = SCREEN_ORIENTATION_PORTRAIT,
        bounds: Rect? = Rect(0, 0, 200, 500),
    ): RunningTaskInfo {
        val task = createFreeformTask(displayId, bounds)
        val activityInfo = ActivityInfo()
        activityInfo.screenOrientation = orientation
        task.topActivityInfo = activityInfo
        task.isResizeable = isResizeable
        whenever(shellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        userRepositories.current.addTask(
            displayId,
            task.taskId,
            isVisible = true,
            task.configuration.windowConfiguration.bounds,
        )
        runningTasks.add(task)
        return task
    }

    private fun getLatestResizeDesktopTaskWct(
        currentBounds: Rect? = null
    ): WindowContainerTransaction {
        val arg: ArgumentCaptor<WindowContainerTransaction> =
            ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
        verify(resizeTransitionHandler, atLeastOnce())
            .startTransition(capture(arg), eq(currentBounds), isNull())
        return arg.value
    }

    private fun findBoundsChange(wct: WindowContainerTransaction, task: RunningTaskInfo): Rect? =
        wct.changes[task.token.asBinder()]?.configuration?.windowConfiguration?.bounds
}

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
import android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WindowingMode
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.anyBoolean
import com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession
import com.android.window.flags.Flags
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Tests of [DesktopModeWindowDecorViewModelAppHandleOnlyTest]
 *
 * A subset of tests from [DesktopModeWindowDecorViewModel] for when DesktopMode is not active
 * but we still need to show AppHandle
 * Usage: atest WMShellUnitTests:DesktopModeWindowDecorViewModelAppHandleOnlyTest
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidTestingRunner::class)
@EnableFlags(Flags.FLAG_UNIVERSAL_RESIZABLE_BY_DEFAULT)
@RunWithLooper
class DesktopModeWindowDecorViewModelAppHandleOnlyTest :
    DesktopModeWindowDecorViewModelTestsBase() {

    protected val mockDisplay = mock<Display>()

    @Before
    fun setUp() {
        mockitoSession =
            mockitoSession()
                .strictness(Strictness.LENIENT)
                .spyStatic(DragPositioningCallbackUtility::class.java)
                .startMocking()
        desktopState.canEnterDesktopMode = false
        desktopState.overridesShowAppHandle = true

        setUpCommon()
        whenever(mockDisplayController.getDisplay(anyInt())).thenReturn(mockDisplay)
        setDisplayInTopology(true)
    }

    @Test
    fun testWindowDecor_showAppHandle_decorCreated() {
        val task = createTask()

        setUpMockDecorationForTask(task)

        onTaskOpening(task)
        assertTrue(windowDecorByTaskIdSpy.contains(task.taskId))
    }

    @Test
    fun testWindowDecor_dontShowAppHandle_decorNotCreated() {
        // Simulate device that doesn't support showing app handle
        desktopState.overridesShowAppHandle = false

        val task = createTask()

        onTaskOpening(task)
        assertFalse(windowDecorByTaskIdSpy.contains(task.taskId))
    }

    @Test
    fun testDeleteDecorationOnChangeTransitionWhenNecessary() {
        val task = createTask()
        val taskSurface = SurfaceControl()
        val decoration = setUpMockDecorationForTask(task)

        onTaskOpening(task, taskSurface)
        assertTrue(windowDecorByTaskIdSpy.contains(task.taskId))
        task.setActivityType(ACTIVITY_TYPE_UNDEFINED)
        onTaskChanging(task, taskSurface)

        assertFalse(windowDecorByTaskIdSpy.contains(task.taskId))
        verify(decoration).close()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun testDecor_invokeOpenHandleMenuCallback_openHandleMenu() {
        val task = createTask()
        val decor = setUpMockDecorationForTask(task)
        val openHandleMenuCallbackCaptor = argumentCaptor<(Int) -> Unit>()
        // Set task as gmail
        val gmailPackageName = "com.google.android.gm"
        val baseComponent = ComponentName(gmailPackageName, /* class */ "")
        task.baseActivity = baseComponent

        onTaskOpening(task)
        verify(
            mockAppHandleEducationController,
            times(1)
        ).setAppHandleEducationTooltipCallbacks(openHandleMenuCallbackCaptor.capture(), any())
        openHandleMenuCallbackCaptor.lastValue.invoke(task.taskId)
        bgExecutor.flushAll()
        testShellExecutor.flushAll()

        verify(decor, times(1)).createHandleMenu(anyBoolean())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY)
    fun testDecorationIsNotCreatedForSystemUIActivities() {
        val task = createTask()

        // Set task as systemUI package
        val systemUIPackageName = context.resources.getString(
            com.android.internal.R.string.config_systemUi)
        val baseComponent = ComponentName(systemUIPackageName, /* class */ "")
        task.baseActivity = baseComponent

        onTaskOpening(task)

        assertFalse(windowDecorByTaskIdSpy.contains(task.taskId))
    }

    @Test
    fun testAppHandleShowsOnlyOnLargeDisplay() {
        val task = createTask()
        val taskSurface = SurfaceControl()
        setUpMockDecorationForTask(task)
        onTaskOpening(task, taskSurface)
        assertTrue(windowDecorByTaskIdSpy.contains(task.taskId))


        setLargeScreen(false)
        setUpMockDecorationForTask(task)
        onTaskChanging(task, taskSurface)
        assertFalse(windowDecorByTaskIdSpy.contains(task.taskId))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY)
    fun testAppHandleShowsOnlyOnDisplayInTopology() {
        val task = createTask()
        val taskSurface = SurfaceControl()
        setUpMockDecorationForTask(task)
        onTaskOpening(task, taskSurface)
        assertTrue(windowDecorByTaskIdSpy.contains(task.taskId))


        setDisplayInTopology(false)
        setUpMockDecorationForTask(task)
        onTaskChanging(task, taskSurface)
        assertFalse(windowDecorByTaskIdSpy.contains(task.taskId))
    }

    private fun createTask(
        displayId: Int = DEFAULT_DISPLAY,
        @WindowingMode windowingMode: Int = WINDOWING_MODE_FULLSCREEN,
        activityType: Int = ACTIVITY_TYPE_STANDARD,
        activityInfo: ActivityInfo = ActivityInfo(),
        requestingImmersive: Boolean = false,
        shouldShowAspectRatioButton: Boolean = true
    ): RunningTaskInfo {
        val task = createTask(
            displayId, windowingMode, activityType, activityInfo, requestingImmersive)
        setLargeScreen(shouldShowAspectRatioButton)
        return task
    }

    private fun setLargeScreen(large: Boolean) {
        val size: Float = if (large) 1000f else 100f
        whenever(mockDisplay.getMinSizeDimensionDp()).thenReturn(size)
    }

    private fun setDisplayInTopology(inTopology: Boolean) {
        whenever(mockDisplayController.isDisplayInTopology(anyInt())).thenReturn(inTopology)
    }
}

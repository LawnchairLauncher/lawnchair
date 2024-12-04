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

import android.app.ActivityManager
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.view.Display
import android.view.LayoutInflater
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.View
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.R
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT
import com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT
import com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.windowdecor.additionalviewcontainer.AdditionalSystemViewContainer
import com.android.wm.shell.windowdecor.additionalviewcontainer.AdditionalViewHostViewContainer
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/**
 * Tests for [HandleMenu].
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:HandleMenuTest
 */
@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner::class)
class HandleMenuTest : ShellTestCase() {
    @JvmField
    @Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Mock
    private lateinit var mockDesktopWindowDecoration: DesktopModeWindowDecoration
    @Mock
    private lateinit var onClickListener: View.OnClickListener
    @Mock
    private lateinit var onTouchListener: View.OnTouchListener
    @Mock
    private lateinit var appIcon: Bitmap
    @Mock
    private lateinit var appName: CharSequence
    @Mock
    private lateinit var displayController: DisplayController
    @Mock
    private lateinit var splitScreenController: SplitScreenController
    @Mock
    private lateinit var displayLayout: DisplayLayout
    @Mock
    private lateinit var mockSurfaceControlViewHost: SurfaceControlViewHost

    private lateinit var handleMenu: HandleMenu

    @Before
    fun setUp() {
        val mockAdditionalViewHostViewContainer = AdditionalViewHostViewContainer(
            mock(SurfaceControl::class.java),
            mockSurfaceControlViewHost,
        ) {
            SurfaceControl.Transaction()
        }
        val menuView = LayoutInflater.from(context).inflate(
            R.layout.desktop_mode_window_decor_handle_menu, null)
        whenever(mockDesktopWindowDecoration.addWindow(
            anyInt(), any(), any(), any(), anyInt(), anyInt(), anyInt(), anyInt())
        ).thenReturn(mockAdditionalViewHostViewContainer)
        whenever(mockAdditionalViewHostViewContainer.view).thenReturn(menuView)
        whenever(displayController.getDisplayLayout(anyInt())).thenReturn(displayLayout)
        whenever(displayLayout.width()).thenReturn(DISPLAY_BOUNDS.width())
        whenever(displayLayout.height()).thenReturn(DISPLAY_BOUNDS.height())
        whenever(displayLayout.isLandscape).thenReturn(true)
        mockDesktopWindowDecoration.mDecorWindowContext = context
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ADDITIONAL_WINDOWS_ABOVE_STATUS_BAR)
    fun testFullscreenMenuUsesSystemViewContainer() {
        createTaskInfo(WINDOWING_MODE_FULLSCREEN, SPLIT_POSITION_UNDEFINED)
        val handleMenu = createAndShowHandleMenu()
        assertTrue(handleMenu.mHandleMenuViewContainer is AdditionalSystemViewContainer)
        // Verify menu is created at coordinates that, when added to WindowManager,
        // show at the top-center of display.
        assertTrue(handleMenu.mHandleMenuPosition.equals(16f, -512f))
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ADDITIONAL_WINDOWS_ABOVE_STATUS_BAR)
    fun testFreeformMenu_usesViewHostViewContainer() {
        createTaskInfo(WINDOWING_MODE_FREEFORM, SPLIT_POSITION_UNDEFINED)
        handleMenu = createAndShowHandleMenu()
        assertTrue(handleMenu.mHandleMenuViewContainer is AdditionalViewHostViewContainer)
        // Verify menu is created near top-left of task.
        assertTrue(handleMenu.mHandleMenuPosition.equals(12f, 8f))
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ADDITIONAL_WINDOWS_ABOVE_STATUS_BAR)
    fun testSplitLeftMenu_usesSystemViewContainer() {
        createTaskInfo(WINDOWING_MODE_MULTI_WINDOW, SPLIT_POSITION_TOP_OR_LEFT)
        handleMenu = createAndShowHandleMenu()
        assertTrue(handleMenu.mHandleMenuViewContainer is AdditionalSystemViewContainer)
        // Verify menu is created at coordinates that, when added to WindowManager,
        // show at the top of split left task.
        assertTrue(handleMenu.mHandleMenuPosition.equals(-624f, -512f))
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ADDITIONAL_WINDOWS_ABOVE_STATUS_BAR)
    fun testSplitRightMenu_usesSystemViewContainer() {
        createTaskInfo(WINDOWING_MODE_MULTI_WINDOW, SPLIT_POSITION_BOTTOM_OR_RIGHT)
        handleMenu = createAndShowHandleMenu()
        assertTrue(handleMenu.mHandleMenuViewContainer is AdditionalSystemViewContainer)
        // Verify menu is created at coordinates that, when added to WindowManager,
        // show at the top of split right task.
        assertTrue(handleMenu.mHandleMenuPosition.equals(656f, -512f))
    }

    private fun createTaskInfo(windowingMode: Int, splitPosition: Int) {
        val taskDescriptionBuilder = ActivityManager.TaskDescription.Builder()
            .setBackgroundColor(Color.YELLOW)
        val bounds = when (windowingMode) {
            WINDOWING_MODE_FULLSCREEN -> DISPLAY_BOUNDS
            WINDOWING_MODE_FREEFORM -> FREEFORM_BOUNDS
            WINDOWING_MODE_MULTI_WINDOW -> {
                if (splitPosition == SPLIT_POSITION_TOP_OR_LEFT) {
                    SPLIT_LEFT_BOUNDS
                } else {
                    SPLIT_RIGHT_BOUNDS
                }
            }
            else -> error("Unsupported windowing mode")
        }
        mockDesktopWindowDecoration.mTaskInfo = TestRunningTaskInfoBuilder()
            .setDisplayId(Display.DEFAULT_DISPLAY)
            .setTaskDescriptionBuilder(taskDescriptionBuilder)
            .setWindowingMode(windowingMode)
            .setBounds(bounds)
            .setVisible(true)
            .build()
        // Calculate captionX similar to how WindowDecoration calculates it.
        whenever(mockDesktopWindowDecoration.captionX).thenReturn(
            (mockDesktopWindowDecoration.mTaskInfo.configuration.windowConfiguration
                .bounds.width() - context.resources.getDimensionPixelSize(
                R.dimen.desktop_mode_fullscreen_decor_caption_width)) / 2)
        whenever(splitScreenController.getSplitPosition(any())).thenReturn(splitPosition)
        whenever(splitScreenController.getStageBounds(any(), any())).thenAnswer {
            (it.arguments.first() as Rect).set(SPLIT_LEFT_BOUNDS)
        }
    }

    private fun createAndShowHandleMenu(): HandleMenu {
        val layoutId = if (mockDesktopWindowDecoration.mTaskInfo.isFreeform) {
            R.layout.desktop_mode_app_header
        } else {
            R.layout.desktop_mode_app_header
        }
        val handleMenu = HandleMenu(mockDesktopWindowDecoration, layoutId,
            onClickListener, onTouchListener, appIcon, appName, displayController,
            splitScreenController, true /* shouldShowWindowingPill */,
            50 /* captionHeight */ )
        handleMenu.show()
        return handleMenu
    }

    companion object {
        private val DISPLAY_BOUNDS = Rect(0, 0, 2560, 1600)
        private val FREEFORM_BOUNDS = Rect(500, 500, 2000, 1200)
        private val SPLIT_LEFT_BOUNDS = Rect(0, 0, 1280, 1600)
        private val SPLIT_RIGHT_BOUNDS = Rect(1280, 0, 2560, 1600)
    }
}

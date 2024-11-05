/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.util

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.util.ArrayMap
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import androidx.test.annotation.UiThreadTest
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.TASKBAR_PINNING
import com.android.launcher3.LauncherPrefs.Companion.TASKBAR_PINNING_IN_DESKTOP_MODE
import com.android.launcher3.util.DisplayController.CHANGE_DENSITY
import com.android.launcher3.util.DisplayController.CHANGE_ROTATION
import com.android.launcher3.util.DisplayController.CHANGE_TASKBAR_PINNING
import com.android.launcher3.util.DisplayController.DisplayInfoChangeListener
import com.android.launcher3.util.MainThreadInitializedObject.SandboxContext
import com.android.launcher3.util.window.CachedDisplayInfo
import com.android.launcher3.util.window.WindowManagerProxy
import kotlin.math.min
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.stubbing.Answer

/** Unit tests for {@link DisplayController} */
@SmallTest
@RunWith(LauncherMultivalentJUnit::class)
class DisplayControllerTest {

    private val appContext: Context = ApplicationProvider.getApplicationContext()

    private val context: SandboxContext = mock()
    private val windowManagerProxy: WindowManagerProxy = mock()
    private val launcherPrefs: LauncherPrefs = mock()
    private val displayManager: DisplayManager = mock()
    private val display: Display = mock()
    private val resources: Resources = mock()
    private val displayInfoChangeListener: DisplayInfoChangeListener = mock()

    private lateinit var displayController: DisplayController

    private val width = 2208
    private val height = 1840
    private val inset = 110
    private val densityDpi = 420
    private val density = densityDpi / DisplayMetrics.DENSITY_DEFAULT.toFloat()
    private val bounds =
        arrayOf(
            WindowBounds(Rect(0, 0, width, height), Rect(0, inset, 0, 0), Surface.ROTATION_0),
            WindowBounds(Rect(0, 0, height, width), Rect(0, inset, 0, 0), Surface.ROTATION_90),
            WindowBounds(Rect(0, 0, width, height), Rect(0, inset, 0, 0), Surface.ROTATION_180),
            WindowBounds(Rect(0, 0, height, width), Rect(0, inset, 0, 0), Surface.ROTATION_270)
        )
    private val configuration =
        Configuration(appContext.resources.configuration).apply {
            densityDpi = this@DisplayControllerTest.densityDpi
            screenWidthDp = (bounds[0].bounds.width() / density).toInt()
            screenHeightDp = (bounds[0].bounds.height() / density).toInt()
            smallestScreenWidthDp = min(screenWidthDp, screenHeightDp)
        }

    @Before
    fun setUp() {
        whenever(context.getObject(eq(WindowManagerProxy.INSTANCE))).thenReturn(windowManagerProxy)
        whenever(context.getObject(eq(LauncherPrefs.INSTANCE))).thenReturn(launcherPrefs)
        whenever(launcherPrefs.get(TASKBAR_PINNING)).thenReturn(false)
        whenever(launcherPrefs.get(TASKBAR_PINNING_IN_DESKTOP_MODE)).thenReturn(true)

        // Mock WindowManagerProxy
        val displayInfo = CachedDisplayInfo(Point(width, height), Surface.ROTATION_0)
        whenever(windowManagerProxy.getDisplayInfo(any())).thenReturn(displayInfo)
        whenever(windowManagerProxy.estimateInternalDisplayBounds(any()))
            .thenAnswer(
                Answer {
                    // Always create a new copy of bounds
                    val perDisplayBounds = ArrayMap<CachedDisplayInfo, List<WindowBounds>>()
                    perDisplayBounds[displayInfo] = bounds.toList()
                    return@Answer perDisplayBounds
                }
            )
        whenever(windowManagerProxy.getRealBounds(any(), any())).thenAnswer { i ->
            bounds[i.getArgument<CachedDisplayInfo>(1).rotation]
        }

        whenever(windowManagerProxy.getNavigationMode(any())).thenReturn(NavigationMode.NO_BUTTON)
        // Mock context
        whenever(context.createWindowContext(any(), any(), anyOrNull())).thenReturn(context)
        whenever(context.getSystemService(eq(DisplayManager::class.java)))
            .thenReturn(displayManager)
        doNothing().whenever(context).registerComponentCallbacks(any())

        // Mock display
        whenever(display.rotation).thenReturn(displayInfo.rotation)
        whenever(context.display).thenReturn(display)
        whenever(displayManager.getDisplay(any())).thenReturn(display)

        // Mock resources
        doReturn(context).whenever(context).applicationContext
        whenever(resources.configuration).thenReturn(configuration)
        whenever(context.resources).thenReturn(resources)

        // Initialize DisplayController
        displayController = DisplayController(context)
        displayController.addChangeListener(displayInfoChangeListener)
    }

    @Test
    @UiThreadTest
    fun testRotation() {
        val displayInfo = CachedDisplayInfo(Point(height, width), Surface.ROTATION_90)
        whenever(windowManagerProxy.getDisplayInfo(any())).thenReturn(displayInfo)
        whenever(display.rotation).thenReturn(displayInfo.rotation)
        val configuration =
            Configuration(configuration).apply {
                screenWidthDp = configuration.screenHeightDp
                screenHeightDp = configuration.screenWidthDp
            }
        whenever(resources.configuration).thenReturn(configuration)

        displayController.onConfigurationChanged(configuration)

        verify(displayInfoChangeListener).onDisplayInfoChanged(any(), any(), eq(CHANGE_ROTATION))
    }

    @Test
    @UiThreadTest
    fun testFontScale() {
        val configuration = Configuration(configuration).apply { fontScale = 1.2f }
        whenever(resources.configuration).thenReturn(configuration)

        displayController.onConfigurationChanged(configuration)

        verify(displayInfoChangeListener).onDisplayInfoChanged(any(), any(), eq(CHANGE_DENSITY))
    }

    @Test
    @UiThreadTest
    fun testTaskbarPinning() {
        whenever(launcherPrefs.get(TASKBAR_PINNING)).thenReturn(true)
        displayController.handleInfoChange(display)
        verify(displayInfoChangeListener)
            .onDisplayInfoChanged(any(), any(), eq(CHANGE_TASKBAR_PINNING))
    }

    @Test
    @UiThreadTest
    fun testTaskbarPinningChangeInDesktopMode() {
        whenever(launcherPrefs.get(TASKBAR_PINNING_IN_DESKTOP_MODE)).thenReturn(false)
        displayController.handleInfoChange(display)
        verify(displayInfoChangeListener)
            .onDisplayInfoChanged(any(), any(), eq(CHANGE_TASKBAR_PINNING))
    }
}

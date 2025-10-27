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

package com.android.quickstep.recents.window

import android.graphics.Point
import android.hardware.display.DisplayManager
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.Display
import android.view.DisplayAdjustments
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.Flags.FLAG_ENABLE_FALLBACK_OVERVIEW_IN_WINDOW
import com.android.launcher3.Flags.FLAG_ENABLE_LAUNCHER_OVERVIEW_IN_WINDOW
import com.android.launcher3.util.LauncherModelHelper
import com.android.launcher3.util.window.CachedDisplayInfo
import com.android.quickstep.fallback.window.RecentsDisplayModel
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(FLAG_ENABLE_LAUNCHER_OVERVIEW_IN_WINDOW, FLAG_ENABLE_FALLBACK_OVERVIEW_IN_WINDOW)
class RecentsDisplayModelTest {
    @get:Rule val setFlagsRule = SetFlagsRule()

    // initiate dagger components for injection
    private val launcherModelHelper = LauncherModelHelper()
    private val context = spy(launcherModelHelper.sandboxContext)
    private val displayManager: DisplayManager = context.spyService(DisplayManager::class.java)
    private val display: Display = mock()

    private lateinit var recentsDisplayModel: RecentsDisplayModel

    private val width = 2208
    private val height = 1840

    @Before
    fun setup() {
        // Mock display
        val displayInfo = CachedDisplayInfo(Point(width, height), Surface.ROTATION_0)
        whenever(display.rotation).thenReturn(displayInfo.rotation)
        whenever(display.displayAdjustments).thenReturn(DisplayAdjustments())
        whenever(context.display).thenReturn(display)

        // Mock displayManager
        whenever(displayManager.getDisplay(anyInt())).thenReturn(display)

        runOnMainSync { recentsDisplayModel = RecentsDisplayModel.INSTANCE.get(context) }
    }

    @Test
    fun testEnsureSingleton() {
        val recentsDisplayModel2 = RecentsDisplayModel.INSTANCE.get(context)
        assert(recentsDisplayModel == recentsDisplayModel2)
    }

    @Test
    fun testDefaultDisplayCreation() {
        Assert.assertNotNull(recentsDisplayModel.getRecentsWindowManager(Display.DEFAULT_DISPLAY))
        Assert.assertNotNull(
            recentsDisplayModel.getFallbackWindowInterface(Display.DEFAULT_DISPLAY)
        )
    }

    @Test
    fun testCreateSeparateInstances() {
        val displayId = Display.DEFAULT_DISPLAY + 1
        runOnMainSync { recentsDisplayModel.storeDisplayResource(displayId) }

        val defaultManager = recentsDisplayModel.getRecentsWindowManager(Display.DEFAULT_DISPLAY)
        val secondaryManager = recentsDisplayModel.getRecentsWindowManager(displayId)
        Assert.assertNotSame(defaultManager, secondaryManager)

        val defaultInterface =
            recentsDisplayModel.getFallbackWindowInterface(Display.DEFAULT_DISPLAY)
        val secondInterface = recentsDisplayModel.getFallbackWindowInterface(displayId)
        Assert.assertNotSame(defaultInterface, secondInterface)
    }

    @Test
    fun testDestroy() {
        Assert.assertNotNull(recentsDisplayModel.getRecentsWindowManager(Display.DEFAULT_DISPLAY))
        Assert.assertNotNull(
            recentsDisplayModel.getFallbackWindowInterface(Display.DEFAULT_DISPLAY)
        )
        recentsDisplayModel.destroy()
        Assert.assertNull(recentsDisplayModel.getRecentsWindowManager(Display.DEFAULT_DISPLAY))
        Assert.assertNull(recentsDisplayModel.getFallbackWindowInterface(Display.DEFAULT_DISPLAY))
    }

    private fun runOnMainSync(f: Runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { f.run() }
    }
}

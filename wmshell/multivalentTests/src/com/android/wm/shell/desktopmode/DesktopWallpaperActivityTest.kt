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

package com.android.wm.shell.desktopmode

import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager
import androidx.activity.BackEventCompat
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.window.flags2.Flags
import com.android.window.flags2.Flags.FLAG_ENABLE_EMPTY_DESK_ON_MINIMIZE
import com.android.window.flags2.Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [DesktopWallpaperActivity].
 *
 * Build/Install/Run: atest WMShellRobolectricTests:DesktopWallpaperActivityTest (on host) atest
 * WMShellMultivalentTestsOnDevice:DesktopWallpaperActivityTest (on device)
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class DesktopWallpaperActivityTest() {

    @get:Rule val setFlagsRule = SetFlagsRule()
    @Rule
    @JvmField
    val activityScenarioRule = ActivityScenarioRule(DesktopWallpaperActivity::class.java)

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
        Flags.FLAG_ENABLE_EMPTY_DESK_ON_MINIMIZE,
    )
    fun onTopResumedActivityChanged_whenTrue_setsWindowFocusable() {
        activityScenarioRule.scenario.onActivity { activity ->
            activity.onTopResumedActivityChanged(true)

            val windowFlags = activity.window.attributes.flags
            assertThat(windowFlags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE).isEqualTo(0)
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
        Flags.FLAG_ENABLE_EMPTY_DESK_ON_MINIMIZE,
    )
    fun onTopResumedActivityChanged_whenFalse_setsWindowNotFocusable() {
        activityScenarioRule.scenario.onActivity { activity ->
            activity.onTopResumedActivityChanged(true)
            assertThat(
                    activity.window.attributes.flags and
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                )
                .isEqualTo(0)

            activity.onTopResumedActivityChanged(false)

            val windowFlags = activity.window.attributes.flags
            assertThat(windowFlags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                .isNotEqualTo(0)
        }
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND,
        Flags.FLAG_ENABLE_EMPTY_DESK_ON_MINIMIZE,
    )
    fun onBackPressed_movesTaskToBack() {
        var wallpaperActivity: FragmentActivity? = null

        activityScenarioRule.scenario.onActivity { activity ->
            wallpaperActivity = activity
            activity.onBackPressedDispatcher.dispatchOnBackStarted(backEvent())
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(wallpaperActivity?.isFinishing).isFalse()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun onMovedToDisplay_finishesActivity() {
        var wallpaperActivity: FragmentActivity? = null

        activityScenarioRule.scenario.onActivity { activity ->
            wallpaperActivity = activity
            activity.onMovedToDisplay(DEFAULT_DISPLAY, null)
        }

        assertThat(wallpaperActivity?.isFinishing).isTrue()
    }

    private fun backEvent(progress: Float = 0f): BackEventCompat {
        return BackEventCompat(
            touchX = 0f,
            touchY = 0f,
            progress = progress,
            swipeEdge = BackEventCompat.EDGE_LEFT,
        )
    }
}

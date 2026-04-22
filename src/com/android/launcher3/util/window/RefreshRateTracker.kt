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

package com.android.launcher3.util.window

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManager.DisplayListener
import android.view.Display.DEFAULT_DISPLAY
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors
import javax.inject.Inject

/** Utility class to track refresh rate of the current device */
interface RefreshRateTracker {

    val singleFrameMs: Int

    @LauncherAppSingleton
    class RefreshRateTrackerImpl
    @Inject
    constructor(@ApplicationContext ctx: Context, tracker: DaggerSingletonTracker) :
        RefreshRateTracker, DisplayListener {

        private val displayManager: DisplayManager =
            ctx.getSystemService(DisplayManager::class.java)!!.also {
                it.registerDisplayListener(this, Executors.UI_HELPER_EXECUTOR.handler)
                tracker.addCloseable { it.unregisterDisplayListener(this) }
            }

        override var singleFrameMs: Int = updateSingleFrameMs()

        private fun updateSingleFrameMs(): Int {
            val refreshRate = displayManager.getDisplay(DEFAULT_DISPLAY)?.refreshRate
            return if (refreshRate != null && refreshRate > 0) (1000 / refreshRate).toInt() else 16
        }

        override fun onDisplayChanged(displayId: Int) {
            if (displayId == DEFAULT_DISPLAY) {
                singleFrameMs = updateSingleFrameMs()
            }
        }

        override fun onDisplayAdded(displayId: Int) {}

        override fun onDisplayRemoved(displayId: Int) {}
    }

    companion object {

        /** Returns the single frame time in ms */
        @JvmStatic fun Context.getSingleFrameMs() = appComponent.frameRateProvider.singleFrameMs
    }
}

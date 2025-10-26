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

package com.android.quickstep.fallback.window

import android.content.Context
import android.util.Log
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import androidx.core.util.valueIterator
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.WallpaperColorHints
import com.android.quickstep.DisplayModel
import com.android.quickstep.FallbackWindowInterface
import com.android.quickstep.RecentsAnimationDeviceState
import com.android.quickstep.TaskAnimationManager
import com.android.quickstep.dagger.QuickstepBaseAppComponent
import com.android.quickstep.fallback.window.RecentsDisplayModel.RecentsDisplayResource
import com.android.quickstep.fallback.window.RecentsWindowFlags.Companion.enableOverviewInWindow
import java.io.PrintWriter
import javax.inject.Inject

@LauncherAppSingleton
class RecentsDisplayModel
@Inject
constructor(
    @ApplicationContext context: Context,
    private val wallpaperColorHints: WallpaperColorHints,
    tracker: DaggerSingletonTracker,
) : DisplayModel<RecentsDisplayResource>(context) {

    companion object {
        private const val TAG = "RecentsDisplayModel"
        private const val DEBUG = false

        @JvmStatic
        val INSTANCE: DaggerSingletonObject<RecentsDisplayModel> =
            DaggerSingletonObject<RecentsDisplayModel>(
                QuickstepBaseAppComponent::getRecentsDisplayModel
            )
    }

    init {
        if (enableOverviewInWindow) {
            initializeDisplays()
        } else {
            // Always create resource for default display
            storeDisplayResource(DEFAULT_DISPLAY)
        }
        tracker.addCloseable { destroy() }
    }

    override fun createDisplayResource(display: Display): RecentsDisplayResource {
        return RecentsDisplayResource(
            display.displayId,
            context.createDisplayContext(display),
            wallpaperColorHints.hints,
        )
    }

    fun getRecentsWindowManager(displayId: Int): RecentsWindowManager? {
        if (DEBUG) Log.d(TAG, "getRecentsWindowManager for display $displayId")
        return getDisplayResource(displayId)?.recentsWindowManager
    }

    fun getFallbackWindowInterface(displayId: Int): FallbackWindowInterface? {
        if (DEBUG) Log.d(TAG, "getFallbackWindowInterface for display $displayId")
        return getDisplayResource(displayId)?.fallbackWindowInterface
    }

    fun getTaskAnimationManager(displayId: Int): TaskAnimationManager? {
        return getDisplayResource(displayId)?.taskAnimationManager
    }

    val activeDisplayResources: Iterable<RecentsDisplayResource>
        get() =
            object : Iterable<RecentsDisplayResource> {
                override fun iterator() = displayResourceArray.valueIterator()
            }

    data class RecentsDisplayResource(
        val displayId: Int,
        val displayContext: Context,
        val wallpaperColorHints: Int,
    ) : DisplayResource() {
        val recentsWindowManager =
            if (enableOverviewInWindow) RecentsWindowManager(displayContext, wallpaperColorHints)
            else null
        val fallbackWindowInterface =
            if (enableOverviewInWindow) FallbackWindowInterface(recentsWindowManager) else null
        val taskAnimationManager =
            TaskAnimationManager(
                displayContext,
                RecentsAnimationDeviceState.INSTANCE.get(displayContext),
                displayId,
            )

        override fun cleanup() {
            recentsWindowManager?.destroy()
        }

        override fun dump(prefix: String, writer: PrintWriter) {
            writer.println("${prefix}RecentsDisplayResource:")

            writer.println("${prefix}\tdisplayId=${displayId}")
            writer.println("${prefix}\tdisplayContext=${displayContext}")
            writer.println("${prefix}\twallpaperColorHints=${wallpaperColorHints}")
            writer.println("${prefix}\trecentsWindowManager=${recentsWindowManager}")
            writer.println("${prefix}\tfallbackWindowInterface=${fallbackWindowInterface}")
        }
    }
}

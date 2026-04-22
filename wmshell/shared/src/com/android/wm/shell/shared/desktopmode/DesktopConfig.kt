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

package com.android.wm.shell.shared.desktopmode

import android.app.TaskInfo
import android.content.Context
import com.android.internal.annotations.VisibleForTesting
import java.io.PrintWriter

/**
 * Configuration of the desktop mode. Defines the parameters used by various features.
 *
 * This class shouldn't be used outside of WM Shell.
 */
@Suppress("INAPPLICABLE_JVM_NAME")
interface DesktopConfig {
    /**
     * Whether a window should be maximized when it's dragged to the top edge of the screen.
     */
    @Deprecated("Deprecated with desktop-first based drag-to-maximize")
    val shouldMaximizeWhenDragToTopEdge: Boolean

    /** Whether the override desktop density is enabled and valid. */
    @get:JvmName("useDesktopOverrideDensity")
    val useDesktopOverrideDensity: Boolean

    /** The number of [WindowDecorViewHost] instances to warm up on system start. */
    val windowDecorPreWarmSize: Int

    /**
     * The maximum size of the window decoration surface control view host pool, or zero if there
     * should be no pooling.
     */
    val windowDecorScvhPoolSize: Int

    /**
     * Whether veiled resizing is enabled.
     */
    val isVeiledResizeEnabled: Boolean

    /** Returns `true` if the app-to-web feature is using the build-time generic links list. */
    @get:JvmName("useAppToWebBuildTimeGenericLinks")
    val useAppToWebBuildTimeGenericLinks: Boolean

    /** Returns whether to use rounded corners for windows. */
    @get:JvmName("useRoundedCorners")
    val useRoundedCorners: Boolean

    /**
     * Returns whether to use window shadows, [isFocusedWindow] indicating whether or not the window
     * currently holds the focus.
     */
    fun useWindowShadow(isFocusedWindow: Boolean): Boolean

    /**
     * Whether we set opaque background for all freeform tasks.
     *
     * This might be done to prevent freeform tasks below from being visible if freeform task window
     * above is translucent. Otherwise if fluid resize is enabled, add a background to freeform
     * tasks.
     */
    fun shouldSetBackground(taskInfo: TaskInfo): Boolean

    /** Returns the maximum limit on the number of tasks to show in on a desk at any one time. */
    val maxTaskLimit: Int

    /** Returns the maximum limit on the number of desks a user can create. */
    val maxDeskLimit: Int

    /** Override density for tasks when they're inside the desktop.  */
    val desktopDensityOverride: Int

    /** Dumps DesktopModeStatus flags and configs. */
    fun dump(pw: PrintWriter, prefix: String)

    companion object {
        /** Create a [DesktopConfig] from a context. Should only be used for testing. */
        @VisibleForTesting
        fun fromContext(context: Context): DesktopConfig = DesktopConfigImpl(context)
    }
}

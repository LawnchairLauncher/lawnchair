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
import android.os.SystemProperties
import android.util.IndentingPrintWriter
import android.window.DesktopExperienceFlags
import android.window.DesktopModeFlags
import com.android.internal.R
import com.android.internal.annotations.VisibleForTesting
import com.android.wm.shell.shared.desktopmode.DesktopConfigImpl.Companion.WINDOW_DECOR_PRE_WARM_SIZE
import java.io.PrintWriter

@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
class DesktopConfigImpl(
    private val context: Context,
    private val desktopState: DesktopState,
) : DesktopConfig {

    constructor(context: Context) : this(context, DesktopState.fromContext(context))

    override val shouldMaximizeWhenDragToTopEdge: Boolean
        get() {
            if (!DesktopExperienceFlags.ENABLE_DRAG_TO_MAXIMIZE.isTrue) return false
            return SystemProperties.getBoolean(
                ENABLE_DRAG_TO_MAXIMIZE_SYS_PROP,
                context.getResources().getBoolean(R.bool.config_dragToMaximizeInDesktopMode),
            )
        }

    override val useDesktopOverrideDensity: Boolean =
        DESKTOP_DENSITY_OVERRIDE_ENABLED && isValidDesktopDensityOverrideSet()

    /** Return `true` if the override desktop density is set and within a valid range. */
    private fun isValidDesktopDensityOverrideSet() =
        DESKTOP_DENSITY_OVERRIDE >= DESKTOP_DENSITY_MIN &&
            DESKTOP_DENSITY_OVERRIDE <= DESKTOP_DENSITY_MAX

    override val windowDecorPreWarmSize: Int =
        SystemProperties.getInt(WINDOW_DECOR_PRE_WARM_SIZE_SYS_PROP, WINDOW_DECOR_PRE_WARM_SIZE)

    override val windowDecorScvhPoolSize: Int
        get() {
            if (!DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_SCVH_CACHE.isTrue) return 0

            if (maxTaskLimit > 0) return maxTaskLimit

            // TODO: b/368032552 - task limit equal to 0 means unlimited. Figure out what the pool
            //  size should be in that case.
            return 0
        }

    override val isVeiledResizeEnabled: Boolean =
        SystemProperties.getBoolean("persist.wm.debug.desktop_veiled_resizing", true)

    override val useAppToWebBuildTimeGenericLinks: Boolean =
        SystemProperties.getBoolean(
            "persist.wm.debug.use_app_to_web_build_time_generic_links",
            true,
        )

    override val useRoundedCorners: Boolean =
        SystemProperties.getBoolean("persist.wm.debug.desktop_use_rounded_corners", true)

    override fun useWindowShadow(isFocusedWindow: Boolean): Boolean =
        USE_WINDOW_SHADOWS || (isFocusedWindow && USE_WINDOW_SHADOWS_FOCUSED_WINDOW)

    override fun shouldSetBackground(taskInfo: TaskInfo): Boolean =
        taskInfo.isFreeform &&
            (!isVeiledResizeEnabled ||
                DesktopModeFlags.ENABLE_OPAQUE_BACKGROUND_FOR_TRANSPARENT_WINDOWS.isTrue)

    override val maxTaskLimit: Int =
        SystemProperties.getInt(
            MAX_TASK_LIMIT_SYS_PROP,
            context.getResources().getInteger(R.integer.config_maxDesktopWindowingActiveTasks),
        )

    override val maxDeskLimit: Int =
        SystemProperties.getInt(
            MAX_DESK_LIMIT_SYS_PROP,
            context.getResources().getInteger(R.integer.config_maxDesktopWindowingDesks),
        )

    override val desktopDensityOverride: Int =
        SystemProperties.getInt("persist.wm.debug.desktop_mode_density", 284)

    override fun dump(pw: PrintWriter, prefix: String) {
        val ipw = IndentingPrintWriter(pw, /* singleIndent= */ "  ", /* prefix= */ prefix)
        ipw.increaseIndent()
        pw.println(TAG)
        pw.println("maxTaskLimit=$maxTaskLimit")

        pw.print(
            "maxTaskLimit config override=${
                context.getResources()
                    .getInteger(R.integer.config_maxDesktopWindowingActiveTasks)
            }"
        )

        val maxTaskLimitHandle = SystemProperties.find(MAX_TASK_LIMIT_SYS_PROP)
        pw.println("maxTaskLimit sysprop=${maxTaskLimitHandle?.getInt( /* def= */-1) ?: "null"}")

        pw.println("showAppHandle config override=${desktopState.overridesShowAppHandle}")
    }

    companion object {
        private const val TAG: String = "DesktopConfig"

        /** The minimum override density allowed for tasks inside the desktop. */
        private const val DESKTOP_DENSITY_MIN: Int = 100

        /** The maximum override density allowed for tasks inside the desktop. */
        private const val DESKTOP_DENSITY_MAX: Int = 1000

        /** The number of [WindowDecorViewHost] instances to warm up on system start. */
        private const val WINDOW_DECOR_PRE_WARM_SIZE: Int = 2

        /**
         * Sysprop declaring the number of [WindowDecorViewHost] instances to warm up on system
         * start.
         *
         * If it is not defined, then [WINDOW_DECOR_PRE_WARM_SIZE] is used.
         */
        private const val WINDOW_DECOR_PRE_WARM_SIZE_SYS_PROP =
            "persist.wm.debug.desktop_window_decor_pre_warm_size"

        /**
         * Sysprop declaring the maximum number of Tasks to show in Desktop Mode at any one time.
         *
         * If it is not defined, then `R.integer.config_maxDesktopWindowingActiveTasks` is used.
         *
         * The limit does NOT affect Picture-in-Picture, Bubbles, or System Modals (like a screen
         * recording window, or Bluetooth pairing window).
         */
        private const val MAX_TASK_LIMIT_SYS_PROP = "persist.wm.debug.desktop_max_task_limit"

        /**
         * Sysprop declaring the maximum number of Desks a user can create.
         *
         * If it is not defined, then `R.integer.config_maxDesktopWindowingDesks` is used.
         *
         * The limit does NOT affect desks created by connecting additional displays.
         */
        private const val MAX_DESK_LIMIT_SYS_PROP = "persist.wm.debug.desktop_max_desk_limit"

        /**
         * Sysprop declaring whether to enable drag-to-maximize for desktop windows.
         *
         * If it is not defined, then `R.integer.config_dragToMaximizeInDesktopMode`
         * is used.
         */
        private const val ENABLE_DRAG_TO_MAXIMIZE_SYS_PROP =
            "persist.wm.debug.enable_drag_to_maximize"

        /** Flag to indicate whether to apply shadows to windows in desktop mode. */
        private val USE_WINDOW_SHADOWS =
            SystemProperties.getBoolean("persist.wm.debug.desktop_use_window_shadows", true)

        /**
         * Flag to indicate whether to apply shadows to the focused window in desktop mode.
         *
         * Note: this flag is only relevant if USE_WINDOW_SHADOWS is false.
         */
        private val USE_WINDOW_SHADOWS_FOCUSED_WINDOW =
            SystemProperties.getBoolean(
                "persist.wm.debug.desktop_use_window_shadows_focused_window",
                false,
            )

        /** Whether the desktop density override is enabled. */
        private val DESKTOP_DENSITY_OVERRIDE_ENABLED =
            SystemProperties.getBoolean("persist.wm.debug.desktop_mode_density_enabled", false)

        /** Override density for tasks when they're inside the desktop. */
        private val DESKTOP_DENSITY_OVERRIDE: Int =
            SystemProperties.getInt("persist.wm.debug.desktop_mode_density", 284)
    }
}

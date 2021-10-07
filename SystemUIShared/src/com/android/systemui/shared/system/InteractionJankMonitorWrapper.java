/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.shared.system;

import android.annotation.IntDef;
import android.os.Build;
import android.view.View;

import com.android.internal.jank.InteractionJankMonitor;
import com.android.internal.jank.InteractionJankMonitor.Configuration;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class InteractionJankMonitorWrapper {
    private static final String TAG = "JankMonitorWrapper";

    // Launcher journeys.
    public static final int CUJ_APP_LAUNCH_FROM_RECENTS =
            InteractionJankMonitor.CUJ_LAUNCHER_APP_LAUNCH_FROM_RECENTS;
    public static final int CUJ_APP_LAUNCH_FROM_ICON =
            InteractionJankMonitor.CUJ_LAUNCHER_APP_LAUNCH_FROM_ICON;
    public static final int CUJ_APP_CLOSE_TO_HOME =
            InteractionJankMonitor.CUJ_LAUNCHER_APP_CLOSE_TO_HOME;
    public static final int CUJ_APP_CLOSE_TO_PIP =
            InteractionJankMonitor.CUJ_LAUNCHER_APP_CLOSE_TO_PIP;
    public static final int CUJ_QUICK_SWITCH =
            InteractionJankMonitor.CUJ_LAUNCHER_QUICK_SWITCH;
    public static final int CUJ_OPEN_ALL_APPS =
            InteractionJankMonitor.CUJ_LAUNCHER_OPEN_ALL_APPS;
    public static final int CUJ_ALL_APPS_SCROLL =
            InteractionJankMonitor.CUJ_LAUNCHER_ALL_APPS_SCROLL;
    public static final int CUJ_APP_LAUNCH_FROM_WIDGET =
            InteractionJankMonitor.CUJ_LAUNCHER_APP_LAUNCH_FROM_WIDGET;

    @IntDef({
            CUJ_APP_LAUNCH_FROM_RECENTS,
            CUJ_APP_LAUNCH_FROM_ICON,
            CUJ_APP_CLOSE_TO_HOME,
            CUJ_APP_CLOSE_TO_PIP,
            CUJ_QUICK_SWITCH,
            CUJ_APP_LAUNCH_FROM_WIDGET,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface CujType {
    }

    /**
     * Begin a trace session.
     *
     * @param v       an attached view.
     * @param cujType the specific {@link InteractionJankMonitor.CujType}.
     */
    public static void begin(View v, @CujType int cujType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        InteractionJankMonitor.getInstance().begin(v, cujType);
    }

    /**
     * Begin a trace session.
     *
     * @param v       an attached view.
     * @param cujType the specific {@link InteractionJankMonitor.CujType}.
     * @param timeout duration to cancel the instrumentation in ms
     */
    public static void begin(View v, @CujType int cujType, long timeout) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        Configuration.Builder builder =
                new Configuration.Builder(cujType)
                        .setView(v)
                        .setTimeout(timeout);
        InteractionJankMonitor.getInstance().begin(builder);
    }

    /**
     * End a trace session.
     *
     * @param cujType the specific {@link InteractionJankMonitor.CujType}.
     */
    public static void end(@CujType int cujType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        InteractionJankMonitor.getInstance().end(cujType);
    }

    /**
     * Cancel the trace session.
     */
    public static void cancel(@CujType int cujType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        InteractionJankMonitor.getInstance().cancel(cujType);
    }
}

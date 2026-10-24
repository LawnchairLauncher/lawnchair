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

import android.os.Build;
import android.text.TextUtils;
import android.view.View;

import com.android.internal.jank.Cuj;
import com.android.internal.jank.InteractionJankMonitor;
import com.android.internal.jank.InteractionJankMonitor.Configuration;

public final class InteractionJankMonitorWrapper {
    /**
     * Begin a trace session.
     *
     * @param v       an attached view.
     * @param cujType the specific {@link Cuj.CujType}.
     */
    public static void begin(View v, @Cuj.CujType int cujType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        InteractionJankMonitor.getInstance().begin(v, cujType);
    }

    /**
     * Begin a trace session.
     *
     * @param v       an attached view.
     * @param cujType the specific {@link Cuj.CujType}.
     * @param timeout duration to cancel the instrumentation in ms
     */
    public static void begin(View v, @Cuj.CujType int cujType, long timeout) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        Configuration.Builder builder =
                Configuration.Builder.withView(cujType, v)
                        .setTimeout(timeout);
        InteractionJankMonitor.getInstance().begin(builder);
    }

    /**
     * Begin a trace session.
     *
     * @param v       an attached view.
     * @param cujType the specific {@link Cuj.CujType}.
     * @param tag the tag to distinguish different flow of same type CUJ.
     */
    public static void begin(View v, @Cuj.CujType int cujType, String tag) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        Configuration.Builder builder =
                Configuration.Builder.withView(cujType, v);
        if (!TextUtils.isEmpty(tag)) {
            builder.setTag(tag);
        }
        InteractionJankMonitor.getInstance().begin(builder);
    }

    /**
     * End a trace session.
     *
     * @param cujType the specific {@link Cuj.CujType}.
     */
    public static void end(@Cuj.CujType int cujType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        InteractionJankMonitor.getInstance().end(cujType);
    }

    /**
     * Cancel the trace session.
     */
    public static void cancel(@Cuj.CujType int cujType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        InteractionJankMonitor.getInstance().cancel(cujType);
    }

    /** Return true if currently instrumenting a trace session. */
    public static boolean isInstrumenting(@Cuj.CujType int cujType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        return InteractionJankMonitor.getInstance().isInstrumenting(cujType);
    }
}

/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.util;

import android.os.Trace;

import androidx.annotation.MainThread;

import java.util.function.Supplier;

/**
 * A wrapper around {@link Trace} to allow better testing.
 *
 * To enable any tracing log, execute the following command:
 * $ adb shell setprop log.tag.LAUNCHER_TRACE VERBOSE
 * $ adb shell setprop log.tag.TAGNAME VERBOSE
 */
public class TraceHelper {

    // Track binder class for this trace
    public static final int FLAG_ALLOW_BINDER_TRACKING = 1 << 0;

    // Temporarily ignore blocking binder calls for this trace.
    public static final int FLAG_IGNORE_BINDERS = 1 << 1;

    public static final int FLAG_CHECK_FOR_RACE_CONDITIONS = 1 << 2;

    public static final int FLAG_UI_EVENT =
            FLAG_ALLOW_BINDER_TRACKING | FLAG_CHECK_FOR_RACE_CONDITIONS;

    /**
     * Static instance of Trace helper, overridden in tests.
     */
    public static TraceHelper INSTANCE = new TraceHelper();

    /**
     * @return a token to pass into {@link #endSection(Object)}.
     */
    public Object beginSection(String sectionName) {
        return beginSection(sectionName, 0);
    }

    public Object beginSection(String sectionName, int flags) {
        Trace.beginSection(sectionName);
        return null;
    }

    /**
     * @param token the token returned from {@link #beginSection(String, int)}
     */
    public void endSection(Object token) {
        Trace.endSection();
    }

    /**
     * Similar to {@link #beginSection} but doesn't add a trace section.
     */
    public Object beginFlagsOverride(int flags) {
        return null;
    }

    public void endFlagsOverride(Object token) { }

    /**
     * Temporarily ignore blocking binder calls for the duration of this {@link Supplier}.
     */
    @MainThread
    public static <T> T allowIpcs(String rpcName, Supplier<T> supplier) {
        Object traceToken = INSTANCE.beginSection(rpcName, FLAG_IGNORE_BINDERS);
        try {
            return supplier.get();
        } finally {
            INSTANCE.endSection(traceToken);
        }
    }
}

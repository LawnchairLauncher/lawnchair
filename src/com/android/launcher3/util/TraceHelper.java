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

import android.annotation.SuppressLint;
import android.os.Trace;

import androidx.annotation.MainThread;

import com.android.launcher3.Utilities;

import java.util.function.Supplier;

import kotlin.random.Random;

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

    /**
     * Static instance of Trace helper, overridden in tests.
     */
    public static TraceHelper INSTANCE = new TraceHelper();

    /**
     * @see Trace#beginSection(String)
     */
    public void beginSection(String sectionName) {
        Trace.beginSection(sectionName);
    }

    /**
     * @see Trace#endSection()
     */
    public void endSection() {
        Trace.endSection();
    }

    /**
     * @see Trace#beginAsyncSection(String, int)
     * @return a SafeCloseable that can be used to end the session
     */
    @SuppressWarnings("NewApi")
    @SuppressLint("NewApi")
    public SafeCloseable beginAsyncSection(String sectionName) {
        if (!Utilities.ATLEAST_Q) {
            return () -> { };
        }
        int cookie = Random.Default.nextInt();
        Trace.beginAsyncSection(sectionName, cookie);
        return () -> Trace.endAsyncSection(sectionName, cookie);
    }

    /**
     * Returns a SafeCloseable to temporarily ignore blocking binder calls.
     */
    @SuppressWarnings("NewApi")
    @SuppressLint("NewApi")
    public SafeCloseable allowIpcs(String rpcName) {
        if (!Utilities.ATLEAST_Q) {
            return () -> { };
        }
        int cookie = Random.Default.nextInt();
        Trace.beginAsyncSection(rpcName, cookie);
        return () -> Trace.endAsyncSection(rpcName, cookie);
    }

    /**
     * Temporarily ignore blocking binder calls for the duration of this {@link Supplier}.
     *
     * Note, new features should be designed to not rely on mainThread RPCs.
     */
    @MainThread
    public static <T> T allowIpcs(String rpcName, Supplier<T> supplier) {
        try (SafeCloseable c = INSTANCE.allowIpcs(rpcName)) {
            return supplier.get();
        }
    }
}

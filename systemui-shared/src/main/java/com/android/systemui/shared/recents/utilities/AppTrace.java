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
package com.android.systemui.shared.recents.utilities;

import static android.os.Trace.TRACE_TAG_APP;

/**
 * Helper class for internal trace functions.
 */
public class AppTrace {

    /**
     * Begins a new async trace section with the given {@param key} and {@param cookie}.
     */
    public static void start(String key, int cookie) {
        android.os.Trace.asyncTraceBegin(TRACE_TAG_APP, key, cookie);
    }

    /**
     * Begins a new async trace section with the given {@param key}.
     */
    public static void start(String key) {
        android.os.Trace.asyncTraceBegin(TRACE_TAG_APP, key, 0);
    }

    /**
     * Ends an existing async trace section with the given {@param key}.
     */
    public static void end(String key) {
        android.os.Trace.asyncTraceEnd(TRACE_TAG_APP, key, 0);
    }

    /**
     * Ends an existing async trace section with the given {@param key} and {@param cookie}.
     */
    public static void end(String key, int cookie) {
        android.os.Trace.asyncTraceEnd(TRACE_TAG_APP, key, cookie);
    }

    /**
     * Begins a new trace section with the given {@param key}. Can be nested.
     */
    public static void beginSection(String key) {
        android.os.Trace.beginSection(key);
    }

    /**
     * Ends an existing trace section started in the last {@link #beginSection(String)}.
     */
    public static void endSection() {
        android.os.Trace.endSection();
    }

    /**
     * Traces a counter value.
     */
    public static void count(String name, int count) {
        android.os.Trace.traceCounter(TRACE_TAG_APP, name, count);
    }
}

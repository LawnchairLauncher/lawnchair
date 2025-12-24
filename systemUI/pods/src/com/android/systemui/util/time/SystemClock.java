/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.systemui.util.time;

import androidx.annotation.NonNull;

import java.time.Instant;

/**
 * Testable wrapper around {@link android.os.SystemClock}.
 *
 * Dagger can inject this wrapper into your classes. The implementation just proxies calls to the
 * real SystemClock.
 *
 * In tests, pass an instance of FakeSystemClock, which allows you to control the values returned by
 * the various getters below.
 */
public interface SystemClock {
    /** @see android.os.SystemClock#uptimeMillis() */
    long uptimeMillis();

    /** @see android.os.SystemClock#elapsedRealtime() */
    long elapsedRealtime();

    /** @see android.os.SystemClock#elapsedRealtimeNanos() */
    long elapsedRealtimeNanos();

    /** @see android.os.SystemClock#currentThreadTimeMillis() */
    long currentThreadTimeMillis();

    /** @see System#currentTimeMillis()  */
    long currentTimeMillis();

    /** @see System#currentTimeMillis()  */
    @NonNull
    default Instant currentTime() {
        return Instant.ofEpochMilli(currentTimeMillis());
    }
}

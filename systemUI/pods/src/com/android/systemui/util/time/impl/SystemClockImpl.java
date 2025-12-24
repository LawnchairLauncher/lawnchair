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

package com.android.systemui.util.time.impl;

import com.android.systemui.util.time.SystemClock;

import javax.inject.Inject;

/** Default implementation of {@link SystemClock}. */
public class SystemClockImpl implements SystemClock {
    @Inject
    public SystemClockImpl() {}

    @Override
    public long uptimeMillis() {
        return android.os.SystemClock.uptimeMillis();
    }

    @Override
    public long elapsedRealtime() {
        return android.os.SystemClock.elapsedRealtime();
    }

    @Override
    public long elapsedRealtimeNanos() {
        return android.os.SystemClock.elapsedRealtimeNanos();
    }

    @Override
    public long currentThreadTimeMillis() {
        return android.os.SystemClock.currentThreadTimeMillis();
    }

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}

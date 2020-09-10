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

package com.android.launcher3.shadows;

import static com.android.launcher3.util.Executors.createAndStartNewLooper;

import static org.robolectric.shadow.api.Shadow.directlyOn;
import static org.robolectric.util.ReflectionHelpers.setField;

import android.os.Handler;

import com.android.launcher3.util.LooperExecutor;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

/**
 * Shadow for {@link LooperExecutor} to provide reset functionality for static executors.
 */
@Implements(value = LooperExecutor.class, isInAndroidSdk = false)
public class ShadowLooperExecutor {

    @RealObject private LooperExecutor mRealExecutor;

    @Implementation
    protected Handler getHandler() {
        Handler handler = directlyOn(mRealExecutor, LooperExecutor.class, "getHandler");
        Thread thread = handler.getLooper().getThread();
        if (!thread.isAlive()) {
            // Robolectric destroys all loopers at the end of every test. Since Launcher maintains
            // some static threads, they need to be reinitialized in case they were destroyed.
            setField(mRealExecutor, "mHandler",
                    new Handler(createAndStartNewLooper(thread.getName())));
        }
        return directlyOn(mRealExecutor, LooperExecutor.class, "getHandler");
    }
}

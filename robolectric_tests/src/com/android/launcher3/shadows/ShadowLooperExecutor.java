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

import static org.robolectric.shadow.api.Shadow.invokeConstructor;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;
import static org.robolectric.util.ReflectionHelpers.setField;

import android.os.Handler;
import android.os.Looper;

import com.android.launcher3.util.Executors;
import com.android.launcher3.util.LooperExecutor;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Shadow for {@link LooperExecutor} to provide reset functionality for static executors.
 */
@Implements(value = LooperExecutor.class, isInAndroidSdk = false)
public class ShadowLooperExecutor {

    // Keep reference to all created Loopers so they can be torn down after test
    private static Set<LooperExecutor> executors =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    @RealObject private LooperExecutor realExecutor;

    @Implementation
    protected void __constructor__(Looper looper) {
        invokeConstructor(LooperExecutor.class, realExecutor, from(Looper.class, looper));
        executors.add(realExecutor);
    }

    /**
     * Re-initializes any executor which may have been reset when a test finished
     */
    public static void reinitializeStaticExecutors() {
        for (LooperExecutor executor : new ArrayList<>(executors)) {
            setField(executor, "mHandler",
                    new Handler(createAndStartNewLooper(executor.getThread().getName())));
        }
    }
}

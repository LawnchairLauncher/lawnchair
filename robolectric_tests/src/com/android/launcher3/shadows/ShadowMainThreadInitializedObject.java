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

import static org.robolectric.shadow.api.Shadow.invokeConstructor;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.MainThreadInitializedObject.ObjectProvider;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Shadow for {@link MainThreadInitializedObject} to provide reset functionality for static sObjects
 */
@Implements(value = MainThreadInitializedObject.class, isInAndroidSdk = false)
public class ShadowMainThreadInitializedObject {

    // Keep reference to all created MainThreadInitializedObject so they can be cleared after test
    private static Set<MainThreadInitializedObject> sObjects =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    @RealObject private MainThreadInitializedObject mRealObject;

    @Implementation
    protected void __constructor__(ObjectProvider provider) {
        invokeConstructor(MainThreadInitializedObject.class, mRealObject,
                from(ObjectProvider.class, provider));
        sObjects.add(mRealObject);
    }

    /**
     * Resets all the initialized sObjects to be null
     */
    public static void resetInitializedObjects() {
        for (MainThreadInitializedObject object : new ArrayList<>(sObjects)) {
            object.initializeForTesting(null);
        }
    }
}

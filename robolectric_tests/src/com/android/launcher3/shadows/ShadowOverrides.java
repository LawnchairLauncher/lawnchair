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

package com.android.launcher3.shadows;

import android.content.Context;

import com.android.launcher3.util.MainThreadInitializedObject.ObjectProvider;
import com.android.launcher3.util.ResourceBasedOverride;
import com.android.launcher3.util.ResourceBasedOverride.Overrides;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.util.HashMap;
import java.util.Map;

/**
 * Shadow for {@link Overrides} to provide custom overrides for test
 */
@Implements(value = Overrides.class, isInAndroidSdk = false)
public class ShadowOverrides {

    private static Map<Class, ObjectProvider> sProviderMap = new HashMap<>();

    @Implementation
    public static <T extends ResourceBasedOverride> T getObject(
            Class<T> clazz, Context context, int resId) {
        ObjectProvider<T> provider = sProviderMap.get(clazz);
        if (provider != null) {
            return provider.get(context);
        }
        return Shadow.directlyOn(Overrides.class, "getObject",
                ClassParameter.from(Class.class, clazz),
                ClassParameter.from(Context.class, context),
                ClassParameter.from(int.class, resId));
    }

    public static <T> void setProvider(Class<T> clazz, ObjectProvider<T> provider) {
        sProviderMap.put(clazz, provider);
    }

    public static void clearProvider() {
        sProviderMap.clear();
    }
}

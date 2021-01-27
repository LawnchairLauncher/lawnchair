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

import static org.robolectric.shadow.api.Shadow.invokeConstructor;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

import android.view.View;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;

/**
 * Shadow for SurfaceTransactionApplier to override default functionality
 */
@Implements(className = "com.android.quickstep.util.SurfaceTransactionApplier",
        isInAndroidSdk = false)
public class ShadowSurfaceTransactionApplier {

    @RealObject
    private Object mRealObject;

    @Implementation
    protected void __constructor__(View view) {
        invokeConstructor(mRealObject.getClass(), mRealObject, from(View.class, null));
    }
}

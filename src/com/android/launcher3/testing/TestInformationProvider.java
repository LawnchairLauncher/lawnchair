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

package com.android.launcher3.testing;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.Utilities;
import com.android.launcher3.util.ContentProviderProxy;

public class TestInformationProvider extends ContentProviderProxy {

    private static final String TAG = "TestInformationProvider";

    @Nullable
    @Override
    public ProxyProvider getProxy(@NonNull Context context) {
        if (Utilities.isRunningInTestHarness()) {
            return new ProxyProvider() {
                @Nullable
                @Override
                public Bundle call(@NonNull String method, @Nullable String arg,
                        @Nullable Bundle extras) {
                    TestInformationHandler handler = TestInformationHandler.newInstance(context);
                    handler.init(context);

                    Bundle response = handler.call(method, arg, extras);
                    if (response == null) {
                        Log.e(TAG, "Couldn't handle method: " + method + "; current handler="
                                + handler.getClass().getSimpleName());
                    }
                    return response;
                }
            };
        }
        return null;
    }
}

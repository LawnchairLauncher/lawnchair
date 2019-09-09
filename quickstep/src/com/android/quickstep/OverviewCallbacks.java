/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.quickstep;

import android.content.Context;

import com.android.launcher3.R;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.ResourceBasedOverride;

/**
 * Callbacks related to overview/quicksteps.
 */
public class OverviewCallbacks implements ResourceBasedOverride {

    private static OverviewCallbacks sInstance;

    public static OverviewCallbacks get(Context context) {
        Preconditions.assertUIThread();
        if (sInstance == null) {
            sInstance = Overrides.getObject(OverviewCallbacks.class,
                    context.getApplicationContext(), R.string.overview_callbacks_class);
        }
        return sInstance;
    }

    public void onInitOverviewTransition() { }

    public void closeAllWindows() { }
}

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

package com.android.launcher3;

import android.content.Context;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.graphics.IconShape;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.util.ResourceBasedOverride;

/**
 * Utility class to handle one time initializations of the main process
 */
public class MainProcessInitializer implements ResourceBasedOverride {

    public static void initialize(Context context) {
        Overrides.getObject(
                MainProcessInitializer.class, context, R.string.main_process_initializer_class)
                .init(context);
    }

    protected void init(Context context) {
        FileLog.setDir(context.getApplicationContext().getFilesDir());
        FeatureFlags.initialize(context);
        SessionCommitReceiver.applyDefaultUserPrefs(context);
        IconShape.init(context);
    }
}

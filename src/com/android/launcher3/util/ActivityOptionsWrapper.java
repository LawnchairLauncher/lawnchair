/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.util;


import android.app.ActivityOptions;
import android.os.Bundle;

/**
 * A wrapper around {@link ActivityOptions} to allow custom functionality in launcher
 */
public class ActivityOptionsWrapper {

    public final ActivityOptions options;
    // Called when the app launch animation is complete
    public final RunnableList onEndCallback;

    public ActivityOptionsWrapper(ActivityOptions options, RunnableList onEndCallback) {
        this.options = options;
        this.onEndCallback = onEndCallback;
    }

    /**
     * @see {@link ActivityOptions#toBundle()}
     */
    public Bundle toBundle() {
        return options.toBundle();
    }
}

/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.uioverrides.flags;

import androidx.annotation.NonNull;

import com.android.launcher3.config.FeatureFlags.BooleanFlag;
import com.android.launcher3.config.FeatureFlags.FlagState;

class DebugFlag extends BooleanFlag {

    public final String key;
    public final String description;

    @NonNull
    public final FlagState defaultValue;

    DebugFlag(String key, String description, FlagState defaultValue, boolean currentValue) {
        super(currentValue);
        this.key = key;
        this.defaultValue = defaultValue;
        this.description = description;
    }

    @Override
    public String toString() {
        return key + ": defaultValue=" + defaultValue + ", mCurrentValue=" + get();
    }
}

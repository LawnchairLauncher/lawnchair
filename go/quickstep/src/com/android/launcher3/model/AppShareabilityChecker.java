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

package com.android.launcher3.model;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Interface for checking apps' shareability. Implementations need to be able to determine whether
 * apps are shareable given their package names.
 */
public interface AppShareabilityChecker {
    /**
     * Checks the shareability of the provided apps. Once the check is complete, updates the
     * provided manager with the results and calls the (optionally) provided callback.
     * @param packageNames The apps to check
     * @param shareMgr The manager to receive the results
     * @param callback Optional callback to be invoked when the check is finished
     */
    void checkApps(List<String> packageNames, AppShareabilityManager shareMgr,
            @Nullable Consumer<Boolean> callback);
}

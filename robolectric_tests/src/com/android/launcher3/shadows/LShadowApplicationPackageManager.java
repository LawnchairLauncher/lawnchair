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

import android.os.Process;
import android.os.UserHandle;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowApplicationPackageManager;

/**
 * Shadow for {@link ShadowApplicationPackageManager} which create mock predictors
 */
@Implements(className = "android.app.ApplicationPackageManager")
public class LShadowApplicationPackageManager extends ShadowApplicationPackageManager {

    @Implementation
    public CharSequence getUserBadgedLabel(CharSequence label, UserHandle user) {
        return Process.myUserHandle().equals(user) ? label : "Work " + label;
    }
}

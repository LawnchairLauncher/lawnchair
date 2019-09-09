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

package com.android.systemui.shared.system;

import android.app.AppGlobals;
import android.app.admin.DevicePolicyManager;

/**
 * Wrapper for {@link DevicePolicyManager}.
 */
public class DevicePolicyManagerWrapper {
    private static final DevicePolicyManagerWrapper sInstance = new DevicePolicyManagerWrapper();

    private static final DevicePolicyManager sDevicePolicyManager =
            AppGlobals.getInitialApplication().getSystemService(DevicePolicyManager.class);

    private DevicePolicyManagerWrapper() { }

    public static DevicePolicyManagerWrapper getInstance() {
        return sInstance;
    }

    /**
     * Returns whether the given package is allowed to run in Lock Task mode.
     */
    public boolean isLockTaskPermitted(String pkg) {
        return sDevicePolicyManager.isLockTaskPermitted(pkg);
    }
}

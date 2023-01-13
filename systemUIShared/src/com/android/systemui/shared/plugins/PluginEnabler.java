/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.shared.plugins;

import android.annotation.IntDef;
import android.content.ComponentName;

/**
 * Enables and disables plugins.
 */
public interface PluginEnabler {

    int ENABLED = 0;
    int DISABLED_MANUALLY = 1;
    int DISABLED_INVALID_VERSION = 2;
    int DISABLED_FROM_EXPLICIT_CRASH = 3;
    int DISABLED_FROM_SYSTEM_CRASH = 4;

    @IntDef({ENABLED, DISABLED_MANUALLY, DISABLED_INVALID_VERSION, DISABLED_FROM_EXPLICIT_CRASH,
            DISABLED_FROM_SYSTEM_CRASH})
    @interface DisableReason {
    }

    /** Enables plugin via the PackageManager. */
    void setEnabled(ComponentName component);

    /** Disables a plugin via the PackageManager and records the reason for disabling. */
    void setDisabled(ComponentName component, @DisableReason int reason);

    /** Returns true if the plugin is enabled in the PackageManager. */
    boolean isEnabled(ComponentName component);

    /**
     * Returns the reason that a plugin is disabled, (if it is).
     *
     * It should return {@link #ENABLED} if the plugin is turned on.
     * It should return {@link #DISABLED_MANUALLY} if the plugin is off but the reason is unknown.
     */
    @DisableReason
    int getDisableReason(ComponentName componentName);
}

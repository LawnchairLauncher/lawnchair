/*
 * Copyright (C) 2024 The Android Open Source Project
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
import android.os.SystemProperties;

import com.android.internal.annotations.VisibleForTesting;
import com.android.window.flags.Flags;

// TODO(b/335401172): Explore unifying logic across core and shell
public class DesktopModeStatus {

    /**
     * Flag to indicate whether to restrict desktop mode to supported devices.
     */
    private static final boolean ENFORCE_DEVICE_RESTRICTIONS = SystemProperties.getBoolean(
            "persist.wm.debug.desktop_mode_enforce_device_restrictions", true);

    /**
     * Return {@code true} if desktop mode should be restricted to supported devices.
     */
    @VisibleForTesting
    public static boolean enforceDeviceRestrictions() {
        return ENFORCE_DEVICE_RESTRICTIONS;
    }

    /**
     * Return {@code true} if the current device supports desktop mode.
     */
    @VisibleForTesting
    public static boolean isDesktopModeSupported(Context context) {
        return context.getResources().getBoolean(
                com.android.internal.R.bool.config_isDesktopModeSupported);
    }

    /**
     * Return {@code true} if desktop mode can be entered on the current device.
     */
    public static boolean canEnterDesktopMode(Context context) {
        return Flags.enableDesktopWindowingMode()
                && (!enforceDeviceRestrictions() || isDesktopModeSupported(context));
    }
}

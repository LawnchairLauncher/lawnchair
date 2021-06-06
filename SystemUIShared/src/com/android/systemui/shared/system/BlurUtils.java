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
 * limitations under the License
 */

package com.android.systemui.shared.system;

import android.app.ActivityManager;
import android.os.SystemProperties;

public abstract class BlurUtils {

    private static boolean mBlurSupportedSysProp = SystemProperties
            .getBoolean("ro.surface_flinger.supports_background_blur", false);
    private static boolean mBlurDisabledSysProp = SystemProperties
            .getBoolean("persist.sys.sf.disable_blurs", false);

    /**
     * If this device can render blurs.
     *
     * @return {@code true} when supported.
     */
    public static boolean supportsBlursOnWindows() {
        return mBlurSupportedSysProp && !mBlurDisabledSysProp && ActivityManager.isHighEndGfx();
    }
}

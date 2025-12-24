/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.shared.pip

import android.app.AppGlobals
import android.content.pm.PackageManager
import android.window.DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_PIP
import com.android.wm.shell.Flags

class PipFlags {
    companion object {
        /**
         * @return {@code true} if PiP2 implementation should be used.
         *
         * Note: if PiP on Desktop Windowing is enabled, override the PiP2 gantry flag to be ON.
         * Note: For form factors other than phone, such as TV, separate flag needs to be ON.
         */
        @JvmStatic
        val isPip2ExperimentEnabled: Boolean by lazy {
            val isTv = AppGlobals.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_LEANBACK, 0)
            (Flags.enablePip2() || ENABLE_DESKTOP_WINDOWING_PIP.isTrue) &&
                    (!isTv || Flags.enablePip2OnTv())
        }

        @JvmStatic
        val isPipUmoExperienceEnabled: Boolean by lazy {
            Flags.enablePipUmoExperience()
        }
    }
}

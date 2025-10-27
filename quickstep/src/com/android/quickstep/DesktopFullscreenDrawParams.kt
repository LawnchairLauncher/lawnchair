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

package com.android.quickstep

import android.content.Context
import com.android.wm.shell.R

// DesktopTaskView thumbnail's corner radius is independent of fullscreenProgress.
open class DesktopFullscreenDrawParams
@JvmOverloads
constructor(context: Context, cornerRadiusProvider: (Context) -> Float = ::computeCornerRadius) :
    FullscreenDrawParams(context, cornerRadiusProvider, cornerRadiusProvider) {
    companion object {
        // computeCornerRadius is used as cornerRadiusProvider, so
        // QuickStepContract::getWindowCornerRadius can be mocked properly.
        private fun computeCornerRadius(context: Context): Float =
            context.resources.getDimension(R.dimen.desktop_windowing_freeform_rounded_corner_radius)
    }
}

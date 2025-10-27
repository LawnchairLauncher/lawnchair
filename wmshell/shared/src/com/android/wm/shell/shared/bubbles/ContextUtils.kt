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

package com.android.wm.shell.shared.bubbles

import android.content.Context
import android.view.View
import android.view.WindowManagerPolicyConstants
import com.android.internal.R

/** Simplifies accessing context fields. */
object ContextUtils {

    /** Gets navigation mode. */
    @JvmStatic
    val Context.navigationMode: Int
        get() = resources.getInteger(R.integer.config_navBarInteractionMode)

    /** Returns whether the navigation mode is gestures. */
    @JvmStatic
    val Context.isGestureNavigationMode: Boolean
        get() = navigationMode == WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL

    /** Returns whether layout direction is rtl. */
    @JvmStatic
    val Context.isRtl: Boolean
        get() = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
}

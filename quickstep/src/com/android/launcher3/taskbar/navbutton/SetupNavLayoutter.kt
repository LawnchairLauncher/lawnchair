/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.launcher3.taskbar.navbutton

import android.content.res.Resources
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.android.launcher3.DeviceProfile

class SetupNavLayoutter(
        resources: Resources,
        navButtonContainer: LinearLayout,
        endContextualContainer: ViewGroup,
        startContextualContainer: ViewGroup
) : AbstractNavButtonLayoutter(
        resources,
        navButtonContainer,
        endContextualContainer,
        startContextualContainer
) {

    override fun layoutButtons(dp: DeviceProfile, isContextualButtonShowing: Boolean) {
        // Since setup wizard only has back button enabled, it looks strange to be
        // end-aligned, so start-align instead.
        val navButtonsLayoutParams = navButtonContainer.layoutParams as FrameLayout.LayoutParams
        navButtonsLayoutParams.apply {
            marginStart = navButtonsLayoutParams.marginEnd
            marginEnd = 0
            gravity = Gravity.START
        }
        navButtonContainer.requestLayout()
    }
}
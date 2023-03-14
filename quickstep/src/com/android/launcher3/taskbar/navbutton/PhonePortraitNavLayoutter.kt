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
import com.android.launcher3.R
import com.android.launcher3.taskbar.TaskbarManager
import com.android.launcher3.util.DimensionUtils

class PhonePortraitNavLayoutter(resources: Resources, navBarContainer: LinearLayout,
                                endContextualContainer: ViewGroup,
                                startContextualContainer: ViewGroup) :
        AbstractNavButtonLayoutter(resources, navBarContainer, endContextualContainer,
                startContextualContainer) {

    override fun layoutButtons(dp: DeviceProfile, isContextualButtonShowing: Boolean) {
        // TODO(b/230395757): Polish pending, this is just to make it usable
        val navContainerParams = navButtonContainer.layoutParams as FrameLayout.LayoutParams
        val taskbarDimensions = DimensionUtils.getTaskbarPhoneDimensions(dp, resources,
                TaskbarManager.isPhoneMode(dp))
        val endStartMargins = resources.getDimensionPixelSize(R.dimen.taskbar_nav_buttons_size)
        navContainerParams.width = taskbarDimensions.x
        navContainerParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        navContainerParams.gravity = Gravity.CENTER_VERTICAL

        // Ensure order of buttons is correct
        navButtonContainer.removeAllViews()
        navButtonContainer.orientation = LinearLayout.HORIZONTAL
        navContainerParams.topMargin = 0
        navContainerParams.bottomMargin = 0
        navContainerParams.marginEnd = endStartMargins
        navContainerParams.marginStart = endStartMargins
        // Swap recents and back button in case we were landscape prior to this
        navButtonContainer.addView(backButton)
        navButtonContainer.addView(homeButton)
        navButtonContainer.addView(recentsButton)

        navButtonContainer.layoutParams = navContainerParams

        // Add the spaces in between the nav buttons
        val spaceInBetween =
                resources.getDimensionPixelSize(R.dimen.taskbar_button_space_inbetween_phone)
        for (i in 0 until navButtonContainer.childCount) {
            val navButton = navButtonContainer.getChildAt(i)
            val buttonLayoutParams = navButton.layoutParams as LinearLayout.LayoutParams
            buttonLayoutParams.weight = 1f
            when (i) {
                0 -> {
                    // First button
                    buttonLayoutParams.marginEnd = spaceInBetween / 2
                }
                navButtonContainer.childCount - 1 -> {
                    // Last button
                    buttonLayoutParams.marginStart = spaceInBetween / 2
                }
                else -> {
                    // other buttons
                    buttonLayoutParams.marginStart = spaceInBetween / 2
                    buttonLayoutParams.marginEnd = spaceInBetween / 2
                }
            }
        }
    }
}
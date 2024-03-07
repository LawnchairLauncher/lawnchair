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
import android.widget.ImageView
import android.widget.LinearLayout
import com.android.launcher3.DeviceProfile
import com.android.launcher3.R
import com.android.launcher3.taskbar.TaskbarManager
import com.android.launcher3.util.DimensionUtils
import com.android.systemui.shared.rotation.RotationButton

class PhonePortraitNavLayoutter(
        resources: Resources,
        navBarContainer: LinearLayout,
        endContextualContainer: ViewGroup,
        startContextualContainer: ViewGroup,
        imeSwitcher: ImageView?,
        rotationButton: RotationButton?,
        a11yButton: ImageView?,
) :
    AbstractNavButtonLayoutter(
            resources,
            navBarContainer,
            endContextualContainer,
            startContextualContainer,
            imeSwitcher,
            rotationButton,
            a11yButton
    ) {

    override fun layoutButtons(dp: DeviceProfile, isA11yButtonPersistent: Boolean) {
        // TODO(b/230395757): Polish pending, this is just to make it usable
        val taskbarDimensions =
            DimensionUtils.getTaskbarPhoneDimensions(dp, resources,
                    TaskbarManager.isPhoneMode(dp))
        val endStartMargins = resources.getDimensionPixelSize(R.dimen.taskbar_nav_buttons_size)

        // Ensure order of buttons is correct
        navButtonContainer.removeAllViews()
        navButtonContainer.orientation = LinearLayout.HORIZONTAL

        val navContainerParams = FrameLayout.LayoutParams(
                taskbarDimensions.x, ViewGroup.LayoutParams.MATCH_PARENT)
        navContainerParams.apply {
            topMargin = 0
            bottomMargin = 0
            marginEnd = endStartMargins
            marginStart = endStartMargins
        }

        // Swap recents and back button in case we were landscape prior to this
        navButtonContainer.addView(backButton)
        navButtonContainer.addView(homeButton)
        navButtonContainer.addView(recentsButton)

        navButtonContainer.layoutParams = navContainerParams
        navButtonContainer.gravity = Gravity.CENTER

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

        endContextualContainer.removeAllViews()
        startContextualContainer.removeAllViews()

        val contextualMargin = resources.getDimensionPixelSize(
                R.dimen.taskbar_contextual_button_padding)
        repositionContextualContainer(endContextualContainer, contextualMargin, Gravity.END)

        if (imeSwitcher != null) {
            endContextualContainer.addView(imeSwitcher)
            imeSwitcher.layoutParams = getParamsToCenterView()
        }
        if (a11yButton != null) {
            endContextualContainer.addView(a11yButton)
        }
        if (rotationButton != null) {
            endContextualContainer.addView(rotationButton.currentView)
            rotationButton.currentView.layoutParams = getParamsToCenterView()
        }
    }
}

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
import androidx.core.view.children
import com.android.launcher3.DeviceProfile
import com.android.launcher3.R
import com.android.launcher3.taskbar.TaskbarManager
import com.android.launcher3.util.DimensionUtils
import com.android.systemui.shared.rotation.RotationButton

open class PhoneLandscapeNavLayoutter(
        resources: Resources,
        navBarContainer: LinearLayout,
        endContextualContainer: ViewGroup,
        startContextualContainer: ViewGroup,
        imeSwitcher: ImageView?,
        rotationButton: RotationButton?,
        a11yButton: ImageView,
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

    override fun layoutButtons(dp: DeviceProfile, isContextualButtonShowing: Boolean) {
        // TODO(b/230395757): Polish pending, this is just to make it usable
        val endStartMargins = resources.getDimensionPixelSize(R.dimen.taskbar_nav_buttons_size)
        val taskbarDimensions = DimensionUtils.getTaskbarPhoneDimensions(dp, resources,
                TaskbarManager.isPhoneMode(dp))
        navButtonContainer.removeAllViews()
        navButtonContainer.orientation = LinearLayout.VERTICAL

        val navContainerParams = FrameLayout.LayoutParams(
                taskbarDimensions.x, ViewGroup.LayoutParams.MATCH_PARENT)
        navContainerParams.apply {
            topMargin = endStartMargins
            bottomMargin = endStartMargins
            marginEnd = 0
            marginStart = 0
        }

        navButtonContainer.layoutParams = navContainerParams
        navButtonContainer.gravity = Gravity.CENTER

        addThreeButtons()

        // Add the spaces in between the nav buttons
        val spaceInBetween: Int =
            resources.getDimensionPixelSize(R.dimen.taskbar_button_space_inbetween_phone)
        navButtonContainer.children.forEachIndexed { i, navButton ->
            val buttonLayoutParams = navButton.layoutParams as LinearLayout.LayoutParams
            buttonLayoutParams.weight = 1f
            when (i) {
                0 -> {
                    buttonLayoutParams.bottomMargin = spaceInBetween / 2
                }
                navButtonContainer.childCount - 1 -> {
                    buttonLayoutParams.topMargin = spaceInBetween / 2
                }
                else -> {
                    buttonLayoutParams.bottomMargin = spaceInBetween / 2
                    buttonLayoutParams.topMargin = spaceInBetween / 2
                }
            }
        }

        endContextualContainer.removeAllViews()
        startContextualContainer.removeAllViews()

        val startContextualContainerParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        startContextualContainerParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        startContextualContainer.layoutParams = startContextualContainerParams

        if (imeSwitcher != null) {
            startContextualContainer.addView(imeSwitcher)
            imeSwitcher.layoutParams = getParamsToCenterView()
        }
        startContextualContainer.addView(a11yButton)
        if (rotationButton != null) {
            startContextualContainer.addView(rotationButton.currentView)
            rotationButton.currentView.layoutParams = getParamsToCenterView()
        }
    }

    open fun addThreeButtons() {
        // Swap recents and back button
        navButtonContainer.addView(recentsButton)
        navButtonContainer.addView(homeButton)
        navButtonContainer.addView(backButton)
    }
}

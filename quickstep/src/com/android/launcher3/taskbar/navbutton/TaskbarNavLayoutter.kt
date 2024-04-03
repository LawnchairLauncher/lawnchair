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
import com.android.systemui.shared.rotation.RotationButton

/**
 * Layoutter for rendering task bar in large screen, both in 3-button and gesture nav mode.
 */
class TaskbarNavLayoutter(
        resources: Resources,
        navBarContainer: LinearLayout,
        endContextualContainer: ViewGroup,
        startContextualContainer: ViewGroup,
        imeSwitcher: ImageView?,
        rotationButton: RotationButton?,
        a11yButton: ImageView?
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
        // Add spacing after the end of the last nav button
        var navMarginEnd = resources.getDimension(dp.inv.inlineNavButtonsEndSpacing).toInt()
        val contextualWidth = endContextualContainer.width
        // If contextual buttons are showing, we check if the end margin is enough for the
        // contextual button to be showing - if not, move the nav buttons over a smidge
        if (isA11yButtonPersistent && navMarginEnd < contextualWidth) {
            // Additional spacing, eat up half of space between last icon and nav button
            navMarginEnd += resources.getDimensionPixelSize(R.dimen.taskbar_hotseat_nav_spacing) / 2
        }

        val navButtonParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        navButtonParams.apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            marginEnd = navMarginEnd
        }
        navButtonContainer.orientation = LinearLayout.HORIZONTAL
        navButtonContainer.layoutParams = navButtonParams

        // Add the spaces in between the nav buttons
        val spaceInBetween = resources.getDimensionPixelSize(R.dimen.taskbar_button_space_inbetween)
        for (i in 0 until navButtonContainer.childCount) {
            val navButton = navButtonContainer.getChildAt(i)
            val buttonLayoutParams = navButton.layoutParams as LinearLayout.LayoutParams
            buttonLayoutParams.weight = 0f
            when (i) {
                0 -> {
                    buttonLayoutParams.marginEnd = spaceInBetween / 2
                }
                navButtonContainer.childCount - 1 -> {
                    buttonLayoutParams.marginStart = spaceInBetween / 2
                }
                else -> {
                    buttonLayoutParams.marginStart = spaceInBetween / 2
                    buttonLayoutParams.marginEnd = spaceInBetween / 2
                }
            }
        }

        endContextualContainer.removeAllViews()
        startContextualContainer.removeAllViews()

        if (!dp.isGestureMode) {
            val contextualMargin = resources.getDimensionPixelSize(
                    R.dimen.taskbar_contextual_button_padding)
            repositionContextualContainer(endContextualContainer, 0, Gravity.END)
            repositionContextualContainer(startContextualContainer, contextualMargin, Gravity.START)

            if (imeSwitcher != null) {
                startContextualContainer.addView(imeSwitcher)
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
}

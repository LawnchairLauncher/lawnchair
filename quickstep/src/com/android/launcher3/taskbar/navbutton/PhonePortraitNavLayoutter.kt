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
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import com.android.launcher3.R
import com.android.launcher3.taskbar.TaskbarActivityContext

class PhonePortraitNavLayoutter(
    resources: Resources,
    navBarContainer: LinearLayout,
    endContextualContainer: ViewGroup,
    startContextualContainer: ViewGroup,
    imeSwitcher: ImageView?,
    a11yButton: ImageView?,
    space: Space?
) :
    AbstractNavButtonLayoutter(
        resources,
        navBarContainer,
        endContextualContainer,
        startContextualContainer,
        imeSwitcher,
        a11yButton,
        space
    ) {

    override fun layoutButtons(context: TaskbarActivityContext, isA11yButtonPersistent: Boolean) {
        val totalWidth = context.deviceProfile.widthPx
        val homeButtonWidth =
            resources.getDimensionPixelSize(R.dimen.taskbar_phone_home_button_size)
        val roundedCornerContentMargin =
            resources.getDimensionPixelSize(R.dimen.taskbar_phone_rounded_corner_content_margin)
        val contentPadding = resources.getDimensionPixelSize(R.dimen.taskbar_phone_content_padding)
        val contentWidth = totalWidth - roundedCornerContentMargin * 2 - contentPadding * 2

        // left:back:space(reserved for home):overview:right = 0.25:0.5:1:0.5:0.25
        val contextualButtonWidth = contentWidth / (0.25f + 0.5f + 1f + 0.5f + 0.25f) * 0.25f
        val sideButtonWidth = contextualButtonWidth * 2
        val navButtonContainerWidth = contentWidth - contextualButtonWidth * 2

        val navContainerParams =
            FrameLayout.LayoutParams(
                navButtonContainerWidth.toInt(),
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        navContainerParams.apply {
            topMargin = 0
            bottomMargin = 0
            marginEnd =
                (contextualButtonWidth + contentPadding + roundedCornerContentMargin).toInt()
            marginStart =
                (contextualButtonWidth + contentPadding + roundedCornerContentMargin).toInt()
        }

        // Ensure order of buttons is correct
        navButtonContainer.removeAllViews()
        navButtonContainer.orientation = LinearLayout.HORIZONTAL

        navButtonContainer.addView(backButton)
        navButtonContainer.addView(homeButton)
        navButtonContainer.addView(recentsButton)

        navButtonContainer.layoutParams = navContainerParams
        navButtonContainer.gravity = Gravity.CENTER

        // Add the spaces in between the nav buttons
        val spaceInBetween =
            (navButtonContainerWidth - homeButtonWidth - sideButtonWidth * 2) / 2.0f
        for (i in 0 until navButtonContainer.childCount) {
            val navButton = navButtonContainer.getChildAt(i)
            val buttonLayoutParams = navButton.layoutParams as LinearLayout.LayoutParams
            val margin = (spaceInBetween / 2).toInt()
            when (i) {
                0 -> {
                    // First button
                    buttonLayoutParams.marginEnd = margin
                    buttonLayoutParams.width = sideButtonWidth.toInt()
                }
                navButtonContainer.childCount - 1 -> {
                    // Last button
                    buttonLayoutParams.marginStart = margin
                    buttonLayoutParams.width = sideButtonWidth.toInt()
                }
                else -> {
                    // other buttons
                    buttonLayoutParams.marginStart = margin
                    buttonLayoutParams.marginEnd = margin
                    buttonLayoutParams.width = homeButtonWidth
                }
            }
        }

        endContextualContainer.removeAllViews()
        startContextualContainer.removeAllViews()

        repositionContextualContainer(
            startContextualContainer,
            contextualButtonWidth.toInt(),
            roundedCornerContentMargin + contentPadding,
            0,
            Gravity.START
        )
        repositionContextualContainer(
            endContextualContainer,
            contextualButtonWidth.toInt(),
            0,
            roundedCornerContentMargin + contentPadding,
            Gravity.END
        )

        startContextualContainer.addView(space, MATCH_PARENT, MATCH_PARENT)
        if (imeSwitcher != null) {
            endContextualContainer.addView(imeSwitcher)
            imeSwitcher.layoutParams = getParamsToCenterView()
        }
        if (a11yButton != null) {
            endContextualContainer.addView(a11yButton)
            a11yButton.layoutParams = getParamsToCenterView()
        }
    }
}

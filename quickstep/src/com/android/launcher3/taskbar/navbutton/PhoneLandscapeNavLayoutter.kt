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

open class PhoneLandscapeNavLayoutter(
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
        val totalHeight = context.deviceProfile.heightPx
        val homeButtonHeight =
            resources.getDimensionPixelSize(R.dimen.taskbar_phone_home_button_size)
        val roundedCornerContentMargin =
            resources.getDimensionPixelSize(R.dimen.taskbar_phone_rounded_corner_content_margin)
        val contentPadding = resources.getDimensionPixelSize(R.dimen.taskbar_phone_content_padding)
        val contentWidth = totalHeight - roundedCornerContentMargin * 2 - contentPadding * 2

        // left:back:space(reserved for home):overview:right = 0.25:0.5:1:0.5:0.25
        val contextualButtonHeight = contentWidth / (0.25f + 0.5f + 1f + 0.5f + 0.25f) * 0.25f
        val sideButtonHeight = contextualButtonHeight * 2
        val navButtonContainerHeight = contentWidth - contextualButtonHeight * 2

        val navContainerParams =
            FrameLayout.LayoutParams(MATCH_PARENT, navButtonContainerHeight.toInt())
        navContainerParams.apply {
            topMargin =
                (contextualButtonHeight + contentPadding + roundedCornerContentMargin).toInt()
            bottomMargin =
                (contextualButtonHeight + contentPadding + roundedCornerContentMargin).toInt()
            marginEnd = 0
            marginStart = 0
        }

        // Ensure order of buttons is correct
        navButtonContainer.removeAllViews()
        navButtonContainer.orientation = LinearLayout.VERTICAL

        addThreeButtons()

        navButtonContainer.layoutParams = navContainerParams
        navButtonContainer.gravity = Gravity.CENTER

        // Add the spaces in between the nav buttons
        val spaceInBetween =
            (navButtonContainerHeight - homeButtonHeight - sideButtonHeight * 2) / 2.0f
        for (i in 0 until navButtonContainer.childCount) {
            val navButton = navButtonContainer.getChildAt(i)
            val buttonLayoutParams = navButton.layoutParams as LinearLayout.LayoutParams
            val margin = (spaceInBetween / 2).toInt()
            when (i) {
                0 -> {
                    // First button
                    buttonLayoutParams.bottomMargin = margin
                    buttonLayoutParams.height = sideButtonHeight.toInt()
                }
                navButtonContainer.childCount - 1 -> {
                    // Last button
                    buttonLayoutParams.topMargin = margin
                    buttonLayoutParams.height = sideButtonHeight.toInt()
                }
                else -> {
                    // other buttons
                    buttonLayoutParams.topMargin = margin
                    buttonLayoutParams.bottomMargin = margin
                    buttonLayoutParams.height = homeButtonHeight
                }
            }
        }

        repositionContextualButtons(contextualButtonHeight.toInt())
    }

    open fun addThreeButtons() {
        // Swap recents and back button
        navButtonContainer.addView(recentsButton)
        navButtonContainer.addView(homeButton)
        navButtonContainer.addView(backButton)
    }

    open fun repositionContextualButtons(buttonSize: Int) {
        endContextualContainer.removeAllViews()
        startContextualContainer.removeAllViews()

        val roundedCornerContentMargin =
            resources.getDimensionPixelSize(R.dimen.taskbar_phone_rounded_corner_content_margin)
        val contentPadding = resources.getDimensionPixelSize(R.dimen.taskbar_phone_content_padding)
        repositionContextualContainer(
            startContextualContainer,
            buttonSize,
            roundedCornerContentMargin + contentPadding,
            0,
            Gravity.TOP
        )
        repositionContextualContainer(
            endContextualContainer,
            buttonSize,
            0,
            roundedCornerContentMargin + contentPadding,
            Gravity.BOTTOM
        )

        if (imeSwitcher != null) {
            startContextualContainer.addView(imeSwitcher)
            imeSwitcher.layoutParams = getParamsToCenterView()
        }
        if (a11yButton != null) {
            startContextualContainer.addView(a11yButton)
            a11yButton.layoutParams = getParamsToCenterView()
        }
        endContextualContainer.addView(space, MATCH_PARENT, MATCH_PARENT)
    }

    override fun repositionContextualContainer(
        contextualContainer: ViewGroup,
        buttonSize: Int,
        barAxisMarginTop: Int,
        barAxisMarginBottom: Int,
        gravity: Int
    ) {
        val contextualContainerParams = FrameLayout.LayoutParams(MATCH_PARENT, buttonSize)
        contextualContainerParams.apply {
            marginStart = 0
            marginEnd = 0
            topMargin = barAxisMarginTop
            bottomMargin = barAxisMarginBottom
        }
        contextualContainerParams.gravity = gravity or Gravity.CENTER_HORIZONTAL
        contextualContainer.layoutParams = contextualContainerParams
    }
}

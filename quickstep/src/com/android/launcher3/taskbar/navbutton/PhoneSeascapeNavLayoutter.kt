/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import com.android.launcher3.R

class PhoneSeascapeNavLayoutter(
    resources: Resources,
    navBarContainer: LinearLayout,
    endContextualContainer: ViewGroup,
    startContextualContainer: ViewGroup,
    imeSwitcher: ImageView?,
    a11yButton: ImageView?,
    space: Space?
) :
    PhoneLandscapeNavLayoutter(
        resources,
        navBarContainer,
        endContextualContainer,
        startContextualContainer,
        imeSwitcher,
        a11yButton,
        space
    ) {

    override fun addThreeButtons() {
        // Flip ordering of back and recents buttons
        navButtonContainer.addView(backButton)
        navButtonContainer.addView(homeButton)
        navButtonContainer.addView(recentsButton)
    }

    override fun repositionContextualButtons(buttonSize: Int) {
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

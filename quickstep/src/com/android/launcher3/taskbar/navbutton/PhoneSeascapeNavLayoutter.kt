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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.android.systemui.shared.rotation.RotationButton

class PhoneSeascapeNavLayoutter(
        resources: Resources,
        navBarContainer: LinearLayout,
        endContextualContainer: ViewGroup,
        startContextualContainer: ViewGroup,
        imeSwitcher: ImageView?,
        rotationButton: RotationButton?,
        a11yButton: ImageView
) :
        PhoneLandscapeNavLayoutter(
                resources,
                navBarContainer,
                endContextualContainer,
                startContextualContainer,
                imeSwitcher,
                rotationButton,
                a11yButton
        ) {

    override fun addThreeButtons() {
        // Flip ordering of back and recents buttons
        navButtonContainer.addView(backButton)
        navButtonContainer.addView(homeButton)
        navButtonContainer.addView(recentsButton)
    }

    override fun repositionContextualButtons() {
        endContextualContainer.removeAllViews()
        startContextualContainer.removeAllViews()

        val endContextualContainerParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        endContextualContainerParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        endContextualContainer.layoutParams = endContextualContainerParams

        if (imeSwitcher != null) {
            endContextualContainer.addView(imeSwitcher)
            imeSwitcher.layoutParams = getParamsToCenterView()
        }
        endContextualContainer.addView(a11yButton)
        if (rotationButton != null) {
            endContextualContainer.addView(rotationButton.currentView)
            rotationButton.currentView.layoutParams = getParamsToCenterView()
        }
    }
}

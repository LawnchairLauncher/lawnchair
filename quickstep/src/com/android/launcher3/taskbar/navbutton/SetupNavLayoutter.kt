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

class SetupNavLayoutter(
        resources: Resources,
        navButtonContainer: LinearLayout,
        endContextualContainer: ViewGroup,
        startContextualContainer: ViewGroup,
        imeSwitcher: ImageView?,
        rotationButton: RotationButton?,
        a11yButton: ImageView?
) :
    AbstractNavButtonLayoutter(
            resources,
            navButtonContainer,
            endContextualContainer,
            startContextualContainer,
            imeSwitcher,
            rotationButton,
            a11yButton
    ) {

    override fun layoutButtons(dp: DeviceProfile, isA11yButtonPersistent: Boolean) {
        // Since setup wizard only has back button enabled, it looks strange to be
        // end-aligned, so start-align instead.
        val navButtonsLayoutParams = navButtonContainer.layoutParams as FrameLayout.LayoutParams
        navButtonsLayoutParams.apply {
            marginStart = navButtonsLayoutParams.marginEnd
            marginEnd = 0
            gravity = Gravity.START
        }
        navButtonContainer.requestLayout()

        endContextualContainer.removeAllViews()
        startContextualContainer.removeAllViews()

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

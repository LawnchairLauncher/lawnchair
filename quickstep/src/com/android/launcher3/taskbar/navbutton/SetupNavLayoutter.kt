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
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import com.android.launcher3.DeviceProfile
import com.android.launcher3.R
import com.android.launcher3.taskbar.TaskbarActivityContext

const val SQUARE_ASPECT_RATIO_BOTTOM_BOUND = 0.95
const val SQUARE_ASPECT_RATIO_UPPER_BOUND = 1.05

class SetupNavLayoutter(
    resources: Resources,
    navButtonsView: NearestTouchFrame,
    navButtonContainer: LinearLayout,
    endContextualContainer: ViewGroup,
    startContextualContainer: ViewGroup,
    imeSwitcher: ImageView?,
    a11yButton: ImageView?,
    space: Space?
) :
    AbstractNavButtonLayoutter(
        resources,
        navButtonContainer,
        endContextualContainer,
        startContextualContainer,
        imeSwitcher,
        a11yButton,
        space
    ) {
    private val mNavButtonsView = navButtonsView

    override fun layoutButtons(context: TaskbarActivityContext, isA11yButtonPersistent: Boolean) {
        // Since setup wizard only has back button enabled, it looks strange to be
        // end-aligned, so start-align instead.
        val navButtonsLayoutParams = navButtonContainer.layoutParams as FrameLayout.LayoutParams
        val navButtonsViewLayoutParams = mNavButtonsView.layoutParams as FrameLayout.LayoutParams
        val deviceProfile: DeviceProfile = context.deviceProfile

        navButtonsLayoutParams.marginEnd = 0
        navButtonsLayoutParams.gravity = Gravity.START
        context.setTaskbarWindowSize(context.setupWindowSize)

        // If SUW is on a large screen device that is landscape (or has a square aspect
        // ratio) the back button has to be placed accordingly
        if (
            deviceProfile.isTablet && deviceProfile.isLandscape ||
                (deviceProfile.aspectRatio > SQUARE_ASPECT_RATIO_BOTTOM_BOUND &&
                    deviceProfile.aspectRatio < SQUARE_ASPECT_RATIO_UPPER_BOUND)
        ) {
            navButtonsLayoutParams.marginStart =
                resources.getDimensionPixelSize(R.dimen.taskbar_back_button_suw_start_margin)
            navButtonsViewLayoutParams.bottomMargin =
                resources.getDimensionPixelSize(R.dimen.taskbar_back_button_suw_bottom_margin)
            navButtonsLayoutParams.height =
                resources.getDimensionPixelSize(R.dimen.taskbar_back_button_suw_height)
        } else {
            adjustForSetupInPhoneMode(
                navButtonsLayoutParams,
                navButtonsViewLayoutParams,
                deviceProfile
            )
        }
        mNavButtonsView.layoutParams = navButtonsViewLayoutParams
        navButtonContainer.layoutParams = navButtonsLayoutParams

        endContextualContainer.removeAllViews()
        startContextualContainer.removeAllViews()

        val contextualMargin =
            resources.getDimensionPixelSize(R.dimen.taskbar_contextual_button_padding)
        repositionContextualContainer(endContextualContainer, WRAP_CONTENT, 0, 0, Gravity.END)
        repositionContextualContainer(
            startContextualContainer,
            WRAP_CONTENT,
            contextualMargin,
            contextualMargin,
            Gravity.START
        )

        if (imeSwitcher != null) {
            startContextualContainer.addView(imeSwitcher)
            imeSwitcher.layoutParams = getParamsToCenterView()
        }
        if (a11yButton != null) {
            endContextualContainer.addView(a11yButton)
            a11yButton.layoutParams = getParamsToCenterView()
        }
    }
}

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
import android.graphics.Color
import android.graphics.drawable.PaintDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.android.launcher3.DeviceProfile
import com.android.launcher3.taskbar.navbutton.LayoutResourceHelper.*

class KidsNavLayoutter(
    resources: Resources,
    navBarContainer: LinearLayout,
    endContextualContainer: ViewGroup,
    startContextualContainer: ViewGroup
) :
    AbstractNavButtonLayoutter(
        resources,
        navBarContainer,
        endContextualContainer,
        startContextualContainer
    ) {

    override fun layoutButtons(dp: DeviceProfile, isContextualButtonShowing: Boolean) {
        val iconSize: Int = resources.getDimensionPixelSize(DIMEN_TASKBAR_ICON_SIZE_KIDS)
        val buttonWidth: Int = resources.getDimensionPixelSize(DIMEN_TASKBAR_NAV_BUTTONS_WIDTH_KIDS)
        val buttonHeight: Int =
            resources.getDimensionPixelSize(DIMEN_TASKBAR_NAV_BUTTONS_HEIGHT_KIDS)
        val buttonRadius: Int =
            resources.getDimensionPixelSize(DIMEN_TASKBAR_NAV_BUTTONS_CORNER_RADIUS_KIDS)
        val paddingLeft = (buttonWidth - iconSize) / 2
        val paddingTop = (buttonHeight - iconSize) / 2

        // Update icons
        backButton!!.setImageDrawable(backButton.context.getDrawable(DRAWABLE_SYSBAR_BACK_KIDS))
        backButton.scaleType = ImageView.ScaleType.FIT_CENTER
        backButton.setPadding(paddingLeft, paddingTop, paddingLeft, paddingTop)
        homeButton!!.setImageDrawable(homeButton.context.getDrawable(DRAWABLE_SYSBAR_HOME_KIDS))
        homeButton.scaleType = ImageView.ScaleType.FIT_CENTER
        homeButton.setPadding(paddingLeft, paddingTop, paddingLeft, paddingTop)

        // Home button layout
        val homeLayoutparams = LinearLayout.LayoutParams(buttonWidth, buttonHeight)
        val homeButtonLeftMargin: Int =
            resources.getDimensionPixelSize(DIMEN_TASKBAR_HOME_BUTTON_LEFT_MARGIN_KIDS)
        homeLayoutparams.setMargins(homeButtonLeftMargin, 0, 0, 0)
        homeButton.layoutParams = homeLayoutparams

        // Back button layout
        val backLayoutParams = LinearLayout.LayoutParams(buttonWidth, buttonHeight)
        val backButtonLeftMargin: Int =
            resources.getDimensionPixelSize(DIMEN_TASKBAR_BACK_BUTTON_LEFT_MARGIN_KIDS)
        backLayoutParams.setMargins(backButtonLeftMargin, 0, 0, 0)
        backButton.layoutParams = backLayoutParams

        // Button backgrounds
        val whiteWith10PctAlpha = Color.argb(0.1f, 1f, 1f, 1f)
        val buttonBackground = PaintDrawable(whiteWith10PctAlpha)
        buttonBackground.setCornerRadius(buttonRadius.toFloat())
        homeButton.background = buttonBackground
        backButton.background = buttonBackground

        // Update alignment within taskbar
        val navButtonsLayoutParams = navButtonContainer.layoutParams as FrameLayout.LayoutParams
        navButtonsLayoutParams.apply {
            marginStart = navButtonsLayoutParams.marginEnd / 2
            marginEnd = navButtonsLayoutParams.marginStart
            gravity = Gravity.CENTER
        }
        navButtonContainer.requestLayout()

        homeButton.onLongClickListener = null
    }
}

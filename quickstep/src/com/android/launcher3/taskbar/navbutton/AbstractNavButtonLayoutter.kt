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
import android.graphics.drawable.RotateDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.taskbar.navbutton.NavButtonLayoutFactory.NavButtonLayoutter
import com.android.systemui.shared.rotation.RotationButton

/**
 * Meant to be a simple container for data subclasses will need
 *
 * Assumes that the 3 navigation buttons (back/home/recents) have already been added to
 * [navButtonContainer]
 *
 * @property navButtonContainer ViewGroup that holds the 3 navigation buttons.
 * @property endContextualContainer ViewGroup that holds the end contextual button (ex, IME
 *   dismiss).
 * @property startContextualContainer ViewGroup that holds the start contextual button (ex, A11y).
 */
abstract class AbstractNavButtonLayoutter(
        val resources: Resources,
        val navButtonContainer: LinearLayout,
        protected val endContextualContainer: ViewGroup,
        protected val startContextualContainer: ViewGroup,
        protected val imeSwitcher: ImageView?,
        protected val rotationButton: RotationButton?,
        protected val a11yButton: ImageView?
) : NavButtonLayoutter {
    protected val homeButton: ImageView? = navButtonContainer.findViewById(R.id.home)
    protected val recentsButton: ImageView? = navButtonContainer.findViewById(R.id.recent_apps)
    protected val backButton: ImageView? = navButtonContainer.findViewById(R.id.back)

    init {
        // setup back button drawable
        if (backButton != null) {
            val rotateDrawable = RotateDrawable()
            rotateDrawable.drawable = backButton.context?.getDrawable(R.drawable.ic_sysbar_back)
            rotateDrawable.fromDegrees = 0f
            rotateDrawable.toDegrees = if (Utilities.isRtl(backButton.resources)) 90f else -90f
            backButton.setImageDrawable(rotateDrawable)
        }
    }

    fun getParamsToCenterView(): FrameLayout.LayoutParams {
        val params = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.gravity = Gravity.CENTER
        return params;
    }

    open fun repositionContextualContainer(contextualContainer: ViewGroup, barAxisMargin: Int,
                                           gravity: Int) {
        val contextualContainerParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        contextualContainerParams.apply {
            marginStart = barAxisMargin
            marginEnd = barAxisMargin
            topMargin = 0
            bottomMargin = 0
        }
        contextualContainerParams.gravity = gravity or Gravity.CENTER_VERTICAL
        contextualContainer.layoutParams = contextualContainerParams
    }
}

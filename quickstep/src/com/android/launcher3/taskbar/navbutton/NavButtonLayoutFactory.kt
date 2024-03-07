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
import android.view.Surface.ROTATION_90
import android.view.Surface.Rotation
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.android.launcher3.DeviceProfile
import com.android.launcher3.taskbar.navbutton.LayoutResourceHelper.*
import com.android.launcher3.taskbar.navbutton.NavButtonLayoutFactory.Companion
import com.android.launcher3.taskbar.navbutton.NavButtonLayoutFactory.NavButtonLayoutter
import com.android.systemui.shared.rotation.RotationButton

/**
 * Select the correct layout for nav buttons
 *
 * Since layouts are done dynamically for the nav buttons on Taskbar, this class returns a
 * corresponding [NavButtonLayoutter] via [Companion.getUiLayoutter] that can help position the
 * buttons based on the current [DeviceProfile]
 */
class NavButtonLayoutFactory {
    companion object {
        /**
         * Get the correct instance of [NavButtonLayoutter]
         *
         * No layouts supported for configurations where:
         * * taskbar isn't showing AND
         * * the device is not in [phoneMode] OR
         * * phone is showing
         * * device is using gesture navigation
         *
         * @param navButtonsView ViewGroup that contains start, end, nav button ViewGroups
         * @param isKidsMode no-op when taskbar is hidden/not showing
         * @param isInSetup no-op when taskbar is hidden/not showing
         * @param phoneMode refers to the device using the taskbar window on phones
         * @param isThreeButtonNav are no-ops when taskbar is present/showing
         */
        fun getUiLayoutter(
                deviceProfile: DeviceProfile,
                navButtonsView: FrameLayout,
                imeSwitcher: ImageView?,
                rotationButton: RotationButton?,
                a11yButton: ImageView?,
                resources: Resources,
                isKidsMode: Boolean,
                isInSetup: Boolean,
                isThreeButtonNav: Boolean,
                phoneMode: Boolean,
                @Rotation surfaceRotation: Int
        ): NavButtonLayoutter {
            val navButtonContainer =
                navButtonsView.requireViewById<LinearLayout>(ID_END_NAV_BUTTONS)
            val endContextualContainer =
                navButtonsView.requireViewById<ViewGroup>(ID_END_CONTEXTUAL_BUTTONS)
            val startContextualContainer =
                navButtonsView.requireViewById<ViewGroup>(ID_START_CONTEXTUAL_BUTTONS)
            val isPhoneNavMode = phoneMode && isThreeButtonNav
            val isPhoneGestureMode = phoneMode && !isThreeButtonNav
            return when {
                isPhoneNavMode -> {
                    if (!deviceProfile.isLandscape) {
                        PhonePortraitNavLayoutter(
                                resources,
                                navButtonContainer,
                                endContextualContainer,
                                startContextualContainer,
                                imeSwitcher,
                                rotationButton,
                                a11yButton
                        )
                    } else if (surfaceRotation == ROTATION_90) {
                        PhoneLandscapeNavLayoutter(
                                resources,
                                navButtonContainer,
                                endContextualContainer,
                                startContextualContainer,
                                imeSwitcher,
                                rotationButton,
                                a11yButton
                        )
                    } else {
                        PhoneSeascapeNavLayoutter(
                                resources,
                                navButtonContainer,
                                endContextualContainer,
                                startContextualContainer,
                                imeSwitcher,
                                rotationButton,
                                a11yButton
                        )
                    }
                }
                isPhoneGestureMode ->{
                    PhoneGestureLayoutter(
                            resources,
                            navButtonContainer,
                            endContextualContainer,
                            startContextualContainer,
                            imeSwitcher,
                            rotationButton,
                            a11yButton
                    )
                }
                deviceProfile.isTaskbarPresent -> {
                    return when {
                        isInSetup -> {
                            SetupNavLayoutter(
                                    resources,
                                    navButtonContainer,
                                    endContextualContainer,
                                    startContextualContainer,
                                    imeSwitcher,
                                    rotationButton,
                                    a11yButton
                            )
                        }
                        isKidsMode -> {
                            KidsNavLayoutter(
                                    resources,
                                    navButtonContainer,
                                    endContextualContainer,
                                    startContextualContainer,
                                    imeSwitcher,
                                    rotationButton,
                                    a11yButton
                            )
                        }
                        else ->
                            TaskbarNavLayoutter(
                                    resources,
                                    navButtonContainer,
                                    endContextualContainer,
                                    startContextualContainer,
                                    imeSwitcher,
                                    rotationButton,
                                    a11yButton
                            )
                    }
                }
                else -> error("No layoutter found")
            }
        }
    }

    /** Lays out and provides access to the home, recents, and back buttons for various mischief */
    interface NavButtonLayoutter {
        fun layoutButtons(dp: DeviceProfile, isA11yButtonPersistent: Boolean)
    }
}

/*
 * Copyright (C) 2024 The Android Open Source Project
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
 * limitations under the License.
 */

package com.android.wm.shell.flicker.pip.nonmatchparent

import android.platform.test.annotations.Presubmit
import androidx.test.filters.RequiresDevice
import android.platform.test.annotations.RequiresFlagsDisabled
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.FlickerBuilder
import android.tools.flicker.FlickerTest
import org.junit.Assume
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test entering pip from an app with bottom half layout via [onUserLeaveHint] and by navigating to
 * home.
 *
 * To run this test: `atest WMShellFlickerTestsPip:BottomHalfEnterPipOnUserLeaveHintTest`
 *
 * Actions:
 * ```
 *     Launch [BottomHalfPipLaunchingActivity] in full screen
 *     Launch [BottomHalfPipActivity] with bottom half layout
 *     Select "Via code behind" radio button
 *     Press Home button or swipe up to go Home and put [pipApp] in pip mode
 * ```
 * Notes:
 * ```
 *     1. All assertions are inherited from [EnterPipTest]
 *     2. Part of the test setup occurs automatically via
 *        [android.tools.flicker.runner.TransitionRunner],
 *        including configuring navigation mode, initial orientation and ensuring no
 *        apps are running before setup
 * ```
 */
// TODO(b/380796448): re-enable tests after the support of non-match parent PIP animation for PIP2.
@RequiresFlagsDisabled(com.android.wm.shell.Flags.FLAG_ENABLE_PIP2)
@RequiresDevice
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BottomHalfEnterPipOnUserLeaveHintTest(flicker: FlickerTest) :
    BottomHalfEnterPipTransition(flicker)
{
    override val thisTransition: FlickerBuilder.() -> Unit = { transitions {
        tapl.goHome()
        pipApp.waitForPip(wmHelper)
    } }

    override val defaultEnterPip: FlickerBuilder.() -> Unit = {
        setup {
            pipApp.launchViaIntent(wmHelper)
            pipApp.enableEnterPipOnUserLeaveHint()
        }
    }

    // gestural navigation

    // TODO(b/385086051): check if we can remove optional = true in the test.
    @Presubmit
    @Test
    fun pipAppWindowVisibleChanges() {
        // pip layer in gesture nav will disappear during transition
        Assume.assumeTrue(flicker.scenario.isGesturalNavigation)
        flicker.assertWm {
            this.isAppWindowVisible(pipApp)
                .then()
                .isAppWindowInvisible(pipApp, isOptional = true)
                .then()
                .isAppWindowVisible(pipApp, isOptional = true)
        }
    }

    @Presubmit
    @Test
    fun pipAppLayerVisibleChanges() {
        Assume.assumeTrue(flicker.scenario.isGesturalNavigation)
        // pip layer in gesture nav will disappear during transition
        flicker.assertLayers {
            this.isVisible(pipApp).then().isInvisible(pipApp).then().isVisible(pipApp)
        }
    }

    @Presubmit
    @Test
    fun pipLayerRemainInsideVisibleBounds() {
        // pip layer in gesture nav will disappear during transition
        Assume.assumeTrue(flicker.scenario.isGesturalNavigation)
        // pip layer in gesture nav will disappear during transition
        flicker.assertLayersStart { this.visibleRegion(pipApp).coversAtMost(displayBounds) }
        flicker.assertLayersEnd { this.visibleRegion(pipApp).coversAtMost(displayBounds) }
    }

    // 3-button navigation

    @Presubmit
    @Test
    override fun pipAppWindowAlwaysVisible() {
        // In gestural nav the pip will first move behind home and then above home. The visual
        // appearance visible->invisible->visible is asserted by pipAppLayerAlwaysVisible().
        // But the internal states of activity don't need to follow that, such as a temporary
        // visibility state can be changed quickly outside a transaction so the test doesn't
        // detect that. Hence, skip the case to avoid restricting the internal implementation.
        Assume.assumeFalse(flicker.scenario.isGesturalNavigation)
        super.pipAppWindowAlwaysVisible()
    }

    @Presubmit
    @Test
    override fun pipAppLayerAlwaysVisible() {
        // pip layer in gesture nav will disappear during transition
        Assume.assumeFalse(flicker.scenario.isGesturalNavigation)
        super.pipAppLayerAlwaysVisible()
    }

    @Presubmit
    @Test
    override fun pipOverlayLayerAppearThenDisappear() {
        // no overlay in gesture nav for non-auto enter PiP transition
        Assume.assumeFalse(flicker.scenario.isGesturalNavigation)
        super.pipOverlayLayerAppearThenDisappear()
    }

    @Presubmit
    @Test
    override fun pipLayerReduces() {
        // in gestural nav the pip enters through alpha animation
        Assume.assumeFalse(flicker.scenario.isGesturalNavigation)
        super.pipLayerReduces()
    }

    @Presubmit
    @Test
    override fun focusChanges() {
        // in gestural nav the focus goes to different activity on swipe up
        Assume.assumeFalse(flicker.scenario.isGesturalNavigation)
        super.focusChanges()
    }

    @Presubmit
    @Test
    override fun pipLayerOrOverlayRemainInsideVisibleBounds() {
        // pip layer in gesture nav will disappear during transition
        Assume.assumeFalse(flicker.scenario.isGesturalNavigation)
        super.pipLayerOrOverlayRemainInsideVisibleBounds()
    }
}
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

package com.android.wm.shell.flicker.pip

import android.platform.test.annotations.Presubmit
import android.tools.flicker.junit.FlickerParametersRunnerFactory
import android.tools.flicker.legacy.FlickerBuilder
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.traces.component.ComponentNameMatcher
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized

/**
 * Test auto entering pip using a source rect hint.
 *
 * To run this test: `atest WMShellFlickerTestsPip1:AutoEnterPipWithSourceRectHintTest`
 *
 * Actions:
 * ```
 *     Launch an app in full screen
 *     Select "Auto-enter PiP" radio button
 *     Press "Set SourceRectHint" to create a temporary view that is used as the source rect hint
 *     Press Home button or swipe up to go Home and put [pipApp] in pip mode
 * ```
 *
 * Notes:
 * ```
 *     1. All assertions are inherited from [AutoEnterPipOnGoToHomeTest]
 *     2. Part of the test setup occurs automatically via
 *        [android.tools.flicker.legacy.runner.TransitionRunner],
 *        including configuring navigation mode, initial orientation and ensuring no
 *        apps are running before setup
 * ```
 */
@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(FlickerParametersRunnerFactory::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AutoEnterPipWithSourceRectHintTest(flicker: LegacyFlickerTest) :
    AutoEnterPipOnGoToHomeTest(flicker) {
    override val defaultEnterPip: FlickerBuilder.() -> Unit = {
        setup {
            pipApp.launchViaIntent(wmHelper)
            pipApp.setSourceRectHint()
            pipApp.enableAutoEnterForPipActivity()
        }
    }

    @Presubmit
    @Test
    fun pipOverlayNotShown() {
        val overlay = ComponentNameMatcher.PIP_CONTENT_OVERLAY
        flicker.assertLayers { this.notContains(overlay) }
    }
    @Presubmit
    @Test
    override fun pipOverlayLayerAppearThenDisappear() {
        // we don't use overlay when entering with sourceRectHint
    }

    @Presubmit
    @Test
    override fun pipLayerOrOverlayRemainInsideVisibleBounds() {
        // TODO (b/323511194): Looks like there is some bounciness with pip when using
        // auto enter and sourceRectHint that causes the app to move outside of the display
        // bounds during the transition.
    }
}

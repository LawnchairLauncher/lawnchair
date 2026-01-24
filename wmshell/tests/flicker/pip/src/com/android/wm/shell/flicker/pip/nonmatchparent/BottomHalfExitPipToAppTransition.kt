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
import android.tools.flicker.legacy.LegacyFlickerTest
import android.tools.traces.component.ComponentNameMatcher
import com.android.server.wm.flicker.helpers.BottomHalfPipAppHelper
import com.android.server.wm.flicker.helpers.PipAppHelper
import com.android.wm.shell.flicker.pip.common.ExitPipToAppTransition
import org.junit.Test

/**
 * Base test class to verify PIP exit animation with an activity layout to the bottom half of
 * the container.
 */
abstract class BottomHalfExitPipToAppTransition(flicker: LegacyFlickerTest) :
    ExitPipToAppTransition(flicker) {

    override val pipApp: PipAppHelper = BottomHalfPipAppHelper(instrumentation)

    @Presubmit
    @Test
    override fun showBothAppLayersThenHidePip() {
        // Disabled since the BottomHalfPipActivity just covers half of the simple activity.
    }

    @Presubmit
    @Test
    override fun showBothAppWindowsThenHidePip() {
        // Disabled since the BottomHalfPipActivity just covers half of the simple activity.
    }

    @Presubmit
    @Test
    override fun pipAppCoversFullScreenAtEnd() {
        // Disabled since the BottomHalfPipActivity just covers half of the simple activity.
    }

    /**
     * Checks that the [testApp] and [pipApp] are always visible since the [pipApp] only covers
     * half of screen.
     */
    @Presubmit
    @Test
    fun showBothAppLayersDuringPipTransition() {
        flicker.assertLayers {
            isVisible(testApp)
                .isVisible(pipApp.or(ComponentNameMatcher.TRANSITION_SNAPSHOT))
        }
    }

    /**
     * Checks that the [testApp] and [pipApp] are always visible since the [pipApp] only covers
     * half of screen.
     */
    @Presubmit
    @Test
    fun showBothAppWindowsDuringPipTransition() {
        flicker.assertWm {
            isAppWindowVisible(testApp)
                .isAppWindowOnTop(pipApp)
                .isAppWindowVisible(pipApp)
        }
    }

    /**
     * Verify that the [testApp] and [pipApp] covers the entire screen at the end of PIP exit
     * animation since the [pipApp] will use a bottom half layout.
     */
    @Presubmit
    @Test
    fun testPlusPipAppCoversWindowFrameAtEnd() {
        flicker.assertLayersEnd {
            val pipRegion = visibleRegion(pipApp).region
            visibleRegion(testApp).plus(pipRegion).coversExactly(displayBounds)
        }
    }
}
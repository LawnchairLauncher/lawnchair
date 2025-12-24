/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.flicker.bubbles.testcase

import android.tools.Rotation
import android.tools.traces.component.IComponentNameMatcher
import com.android.wm.shell.flicker.bubbles.BubbleFlickerTestBase.FlickerProperties.tapl
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerSubjects
import com.android.wm.shell.flicker.utils.splitAppLayerBoundsSnapToDivider
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.Assume.assumeThat
import org.junit.Test

/**
 * Verifies an app enters the secondary split-screen area.
 */
interface SecondarySplitEnterTestCases : BubbleFlickerSubjects {

    /**
     * The component that was in the secondary split before the transition began.
     *
     * Override this in your test if a secondary app exists at the start.
     * It is null by default, and tests that rely on it will be skipped.
     */
    val previousSecondaryApp: IComponentNameMatcher?
        get() = null

    /**
     * Verifies that if a secondary app existed before the transition, it becomes
     * invisible after the new app replaces it.
     *
     * This test is skipped if [previousSecondaryApp] is null.
     */
    @Test
    fun previousSecondaryAppBecomesInvisible() {
        assumeThat(
            "Skipping test: No previous secondary app was defined to be replaced.",
            previousSecondaryApp,
            notNullValue()
        )

        val component = previousSecondaryApp!!
        layersTraceSubject
            .splitAppLayerBoundsSnapToDivider(
                component,
                landscapePosLeft = !tapl.isTablet,
                portraitPosTop = true,
                rotation = Rotation.ROTATION_0,
            )
            .then()
            .isVisible(component)
            .then()
            .isInvisible(component)
    }

    /**
     * Verifies the test app's layer is visible and correctly positioned as the secondary
     * split-screen app at the end of the transition.
     *
     * It checks that the app's bounds snap to the split-screen divider in the
     * expected position by considering the portrait and landscape orientations.
     */
    @Test
    fun secondaryAppBoundsIsVisibleAtEnd() {
        layerTraceEntrySubjectAtEnd.splitAppLayerBoundsSnapToDivider(
            component = testApp,
            landscapePosLeft = !tapl.isTablet,
            portraitPosTop = true,
            rotation = Rotation.ROTATION_0,
        )
    }
}
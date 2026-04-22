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

import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.component.ComponentNameMatcher.Companion.LAUNCHER
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerSubjects
import org.junit.Test

/**
 * The test cases to verify [testApp] goes to expanded bubble state, which verifies [testApp]
 * replaces [LAUNCHER] to be top and visible and has rounded corner at the end of transition.
 */
interface BubbleAppBecomesExpandedTestCases : BubbleFlickerSubjects {

    /**
     * Verifies the focus changed from launcher to bubble app.
     */
    @Test
    fun focusChanges() {
        eventLogSubject.focusChanges(LAUNCHER.toWindowName(), testApp.toWindowName())
    }

    /**
     * Verifies the bubble app replaces launcher to be the top window.
     */
    @Test
    fun appWindowReplacesLauncherAsTopWindow() {
        wmTraceSubject
            .isAppWindowOnTop(LAUNCHER)
            .then()
            .isAppWindowOnTop(
                ComponentNameMatcher.SNAPSHOT
                    .or(ComponentNameMatcher.SPLASH_SCREEN),
                isOptional = true,
            )
            .then()
            .isAppWindowOnTop(testApp)
            .forAllEntries()
    }

    /**
     * Verifies the bubble app is the top window at the end of transition.
     */
    @Test
    fun appWindowAsTopWindowAtEnd() {
        wmStateSubjectAtEnd.isAppWindowOnTop(testApp)
    }

    /**
     * Verifies the bubble app becomes the top window.
     */
    @Test
    fun appWindowBecomesTopWindow() {
        wmTraceSubject
            .skipUntilFirstAssertion()
            .isAppWindowNotOnTop(testApp)
            .then()
            .isAppWindowOnTop(testApp)
            .forAllEntries()
    }

    /**
     * Verifies the bubble app window becomes visible.
     */
    @Test
    fun appWindowBecomesVisible() {
        wmTraceSubject
            .skipUntilFirstAssertion()
            .isAppWindowInvisible(testApp)
            .then()
            .isAppWindowVisible(testApp)
            .forAllEntries()
    }

    /**
     * Verifies the bubble app layer becomes visible.
     */
    @Test
    fun appLayerBecomesVisible() {
        layersTraceSubject
            .isInvisible(testApp)
            .then()
            .isVisible(testApp)
            .forAllEntries()
    }

    /**
     * Verifies the bubble app window is visible at the end of transition.
     */
    @Test
    fun appWindowIsVisibleAtEnd() {
        wmStateSubjectAtEnd.isAppWindowVisible(testApp)
    }

    /**
     * Verifies the bubble app layer is visible at the end of transition.
     */
    @Test
    fun appLayerIsVisibleAtEnd() {
        layerTraceEntrySubjectAtEnd.isVisible(testApp)
    }

    /**
     * Verifies the bubble app layer has rounded corners at the end of transition.
     */
    @Test
    fun appLayerHasRoundedCorner() {
        layerTraceEntrySubjectAtEnd.hasRoundedCorners(testApp)
    }
}
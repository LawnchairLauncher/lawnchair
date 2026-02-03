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

import android.tools.traces.component.ComponentNameMatcher.Companion.LAUNCHER
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerSubjects
import org.junit.Test

/**
 * The test cases to verify [testApp] becomes invisible and [LAUNCHER] replaces [testApp] to be top
 * focused because the bubble app goes to collapsed or dismissed state.
 */
interface BubbleAppBecomesNotExpandedTestCases : BubbleFlickerSubjects {

    /**
     * Verifies bubble app window becomes invisible.
     */
    @Test
    fun appWindowBecomesInvisible() {
        wmTraceSubject
            .isAppWindowVisible(testApp)
            .then()
            .isAppWindowInvisible(testApp)
            .forAllEntries()
    }

    /**
     * Verifies bubble app layer becomes invisible.
     */
    @Test
    fun appLayerBecomesInvisible() {
        layersTraceSubject
            .isVisible(testApp)
            .then()
            .isInvisible(testApp)
            .forAllEntries()
    }

    /**
     * Verifies the focus changed from launcher to bubble app.
     */
    @Test
    fun focusChanges() {
        eventLogSubject.focusChanges(testApp.toWindowName(), LAUNCHER.toWindowName())
    }

    /**
     * Verifies the bubble app replaces launcher to be the top window.
     */
    @Test
    fun launcherWindowReplacesTestAppAsTopWindow() {
        wmTraceSubject
            .isAppWindowOnTop(testApp)
            .then()
            .isAppWindowOnTop(LAUNCHER)
            .forAllEntries()
    }

    /**
     * Verifies [LAUNCHER] is the top window at the end of transition.
     */
    @Test
    fun launcherWindowAsTopWindowAtEnd() {
        wmStateSubjectAtEnd.isAppWindowOnTop(LAUNCHER)
    }

    /**
     * Verifies the [LAUNCHER] becomes the top window.
     */
    @Test
    fun launcherWindowBecomesTopWindow() {
        wmTraceSubject
            .isAppWindowNotOnTop(LAUNCHER)
            .then()
            .isAppWindowOnTop(LAUNCHER)
            .forAllEntries()
    }
}
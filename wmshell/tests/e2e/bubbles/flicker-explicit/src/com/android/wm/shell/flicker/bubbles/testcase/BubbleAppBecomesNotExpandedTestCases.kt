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
import android.tools.traces.component.IComponentNameMatcher
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerSubjects
import org.junit.Test

/**
 * The test cases to verify [testApp] becomes invisible and [previousApp] replaces [testApp] to be
 * top focused because the bubble app goes to collapsed or dismissed state.
 */
interface BubbleAppBecomesNotExpandedTestCases : BubbleFlickerSubjects {

    val previousApp: IComponentNameMatcher
        get() = LAUNCHER

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
     * Verifies the [testApp] window has rounded corner at the start of the transition.
     */
    @Test
    fun appWindowHasRoundedCornerAtStart() {
        layerTraceEntrySubjectAtStart.hasRoundedCorners(testApp)
    }

    /**
     * Verifies bubble app window is invisible at the end of the transition.
     */
    @Test
    fun appWindowIsInvisibleAtEnd() {
        wmStateSubjectAtEnd.isAppWindowInvisible(testApp)
    }

    /**
     * Verifies bubble app layer is invisible at the end of the transition.
     */
    @Test
    fun appLayerIsInvisibleAtEnd() {
        // TestApp may be gone if it's in dismissed state.
        layerTraceEntrySubjectAtEnd.isInvisible(testApp)
    }

    /**
     * Verifies the focus changed from bubble app to [previousApp].
     */
    @Test
    fun focusChanges() {
        eventLogSubject.focusChanges(testApp.toWindowName(), previousApp.toWindowName())
    }

    /**
     * Verifies the bubble app replaces [previousApp] to be the top window.
     */
    @Test
    fun previousAppWindowReplacesTestAppAsTopWindow() {
        wmTraceSubject
            .isAppWindowOnTop(testApp)
            .then()
            .isAppWindowOnTop(previousApp)
            .forAllEntries()
    }

    /**
     * Verifies [previousApp] is the top window at the end of transition.
     */
    @Test
    fun previousWindowAsTopWindowAtEnd() {
        wmStateSubjectAtEnd.isAppWindowOnTop(previousApp)
    }

    /**
     * Verifies the [previousApp] becomes the top window.
     */
    @Test
    fun previousAppWindowBecomesTopWindow() {
        wmTraceSubject
            .isAppWindowNotOnTop(previousApp)
            .then()
            .isAppWindowOnTop(previousApp)
            .forAllEntries()
    }
}
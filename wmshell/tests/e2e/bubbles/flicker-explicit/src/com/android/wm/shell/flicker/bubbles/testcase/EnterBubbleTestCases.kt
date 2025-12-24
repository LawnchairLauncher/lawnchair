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

import android.tools.traces.component.ComponentNameMatcher.Companion.BUBBLE
import org.junit.Test

/**
 * The set to verify launching bubble to the expanded state, which verifies
 * - [BubbleAppBecomesExpandedTestCases]
 * - [LauncherAlwaysVisibleTestCases]
 * - Bubble becomes visible
 */
interface EnterBubbleTestCases : BubbleAppBecomesExpandedTestCases, LauncherAlwaysVisibleTestCases {

// region Bubble related tests

    /**
     * Verifies the bubble window is visible at the end of transition.
     */
    @Test
    fun bubbleWindowIsVisibleAtEnd() {
        wmStateSubjectAtEnd.isNonAppWindowVisible(BUBBLE)
    }

    /**
     * Verifies the bubble layer is visible at the end of transition.
     */
    @Test
    fun bubbleLayerIsVisibleAtEnd() {
        layerTraceEntrySubjectAtEnd.isVisible(BUBBLE)
    }

    /**
     * Verifies the bubble window becomes visible.
     */
    @Test
    fun bubbleWindowBecomesVisible() {
        wmTraceSubject
            // Bubble app window may not have been added to WM hierarchy at the start of the
            // transition.
            .isNonAppWindowInvisible(BUBBLE)
            .then()
            .isAboveAppWindowVisible(BUBBLE)
            .forAllEntries()
    }

    /**
     * Verifies the bubble layer becomes visible.
     */
    @Test
    fun bubbleLayerBecomesVisible() {
        layersTraceSubject
            // Bubble may not appear at the start of the transition.
            .isInvisible(BUBBLE)
            .then()
            .isVisible(BUBBLE)
            .forAllEntries()
    }

// endregion
}

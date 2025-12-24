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
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerSubjects
import org.junit.Test

/**
 * Verifies the exit transition of a bubble.
 *
 * Verifies a bubble's window and layer correctly transition from a visible to an invisible state
 * at the end of a trace.
 */
interface BubbleExitTestCases : BubbleFlickerSubjects {

    /**
     * Verifies [BUBBLE] window is gone at the end of the transition.
     */
    @Test
    fun bubbleWindowIsGoneAtEnd() {
        wmStateSubjectAtEnd.notContains(BUBBLE)
    }

    /**
     * Verifies [BUBBLE] layer is gone at the end of the transition.
     */
    @Test
    fun bubbleLayerIsGoneAtEnd() {
        layerTraceEntrySubjectAtEnd.notContains(BUBBLE)
    }

    /**
     * Verifies [BUBBLE] window was visible then disappears.
     */
    @Test
    fun bubbleWindowWasVisibleThenDisappear() {
        wmTraceSubject
            .isAboveAppWindowVisible(BUBBLE)
            .then()
            // Use #isNonAppWindowInvisible here because the BUBBLE window may have been removed
            // from WM hierarchy.
            .isNonAppWindowInvisible(BUBBLE)
            .forAllEntries()
    }

    /**
     * Verifies [BUBBLE] layer was visible then disappears.
     */
    @Test
    fun bubbleLayerWasVisibleThenDisappear() {
        layersTraceSubject
            .isVisible(BUBBLE)
            .then()
            .isInvisible(BUBBLE)
            .forAllEntries()
    }
}
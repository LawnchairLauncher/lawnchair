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
import android.tools.traces.component.ComponentNameMatcher.Companion.TASK_BAR
import org.junit.Test

/**
 * Test cases for bubble expanding via dragging the [testApp] icon from task bar to bubble bar
 * location.
 */
interface EnterBubbleViaDragToBubbleBarTestCases : BubbleAppBecomesExpandedTestCases {
    @Test
    override fun focusChanges() {
        eventLogSubject.focusChanges(
            LAUNCHER.toWindowName(),
            // Tap on the task bar.
            TASK_BAR.toWindowName(),
            // Drag an icon from task bar to the bubble bar location.
            LAUNCHER.toWindowName(),
            // The bubble app launches.
            testApp.toWindowName(),
        )
    }
}
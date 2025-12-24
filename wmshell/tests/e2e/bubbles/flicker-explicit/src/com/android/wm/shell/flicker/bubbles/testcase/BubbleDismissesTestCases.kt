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

import org.junit.Test

/**
 * Verifies a bubble and its associated app window are fully dismissed.
 *
 * This test case builds upon [BubbleExitTestCases] by adding a verification
 * to ensure the application window itself is also gone at the end of the transition.
 */
interface BubbleDismissesTestCases : BubbleExitTestCases {

    /**
     * Verifies bubble app window is gone at the end of the transition.
     */
    @Test
    fun appWindowIsGoneAtEnd() {
        wmStateSubjectAtEnd.notContains(testApp)
    }
}

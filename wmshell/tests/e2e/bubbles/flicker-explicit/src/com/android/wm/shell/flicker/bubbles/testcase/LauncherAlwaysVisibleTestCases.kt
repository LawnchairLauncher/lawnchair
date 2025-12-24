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
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerSubjects
import org.junit.Test

/**
 * Verifies [ComponentNameMatcher.LAUNCHER] is always visible.
 */
interface LauncherAlwaysVisibleTestCases : BubbleFlickerSubjects {
    /**
     * Verifies the launcher window is always visible.
     */
    @Test
    fun launcherWindowIsAlwaysVisible() {
        wmTraceSubject.isAppWindowVisible(ComponentNameMatcher.LAUNCHER).forAllEntries()
    }

    /**
     * Verifies the launcher layer is always visible.
     */
    @Test
    fun launcherLayerIsAlwaysVisible() {
        layersTraceSubject.isVisible(ComponentNameMatcher.LAUNCHER).forAllEntries()
    }
}
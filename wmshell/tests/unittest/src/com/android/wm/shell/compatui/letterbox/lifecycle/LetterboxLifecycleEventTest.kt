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

package com.android.wm.shell.compatui.letterbox.lifecycle

import android.testing.AndroidTestingRunner
import android.view.WindowManager
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleEventType.CLOSE
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleEventType.OPEN
import com.android.wm.shell.util.testLetterboxLifecycleEvent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [LetterboxLifecycleEvent].
 *
 * Build/Install/Run: atest WMShellUnitTests:LetterboxLifecycleEventTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class LetterboxLifecycleEventTest : ShellTestCase() {

    @Test
    fun `asLetterboxLifecycleEventType returns the right type for OPEN modes`() {
        testLetterboxLifecycleEvent {
            inputChange { mode = WindowManager.TRANSIT_OPEN }
            useChange { change -> assertEquals(OPEN, change.asLetterboxLifecycleEventType()) }
            inputChange { mode = WindowManager.TRANSIT_TO_FRONT }
            useChange { change -> assertEquals(OPEN, change.asLetterboxLifecycleEventType()) }
            inputChange { mode = WindowManager.TRANSIT_PREPARE_BACK_NAVIGATION }
            useChange { change -> assertEquals(OPEN, change.asLetterboxLifecycleEventType()) }
        }
    }

    @Test
    fun `asLetterboxLifecycleEventType returns the right type for CLOSE modes`() {
        testLetterboxLifecycleEvent {
            inputChange { mode = WindowManager.TRANSIT_CLOSE }
            useChange { change -> assertEquals(CLOSE, change.asLetterboxLifecycleEventType()) }
            inputChange { mode = WindowManager.TRANSIT_TO_BACK }
            useChange { change -> assertEquals(CLOSE, change.asLetterboxLifecycleEventType()) }
        }
    }

    @Test
    fun `isActivityChange returns true if activityTransitionInfo is present`() {
        testLetterboxLifecycleEvent {
            inputChange {}
            useChange { change -> assertFalse(change.isActivityChange()) }
            inputChange { activityTransitionInfo {} }
            useChange { change -> assertTrue(change.isActivityChange()) }
        }
    }

    @Test
    fun `isChangeForALeafTask returns true if the task is a leaf`() {
        testLetterboxLifecycleEvent {
            inputChange {}
            useChange { change -> assertFalse(change.isChangeForALeafTask()) }

            inputChange { runningTaskInfo {} }
            useChange { change -> assertFalse(change.isChangeForALeafTask()) }

            inputChange { runningTaskInfo { ti -> activityTransitionInfo {} } }
            useChange { change -> assertFalse(change.isChangeForALeafTask()) }

            inputChange { runningTaskInfo { ti -> ti.appCompatTaskInfo.setIsLeafTask(true) } }
            useChange { change -> assertTrue(change.isChangeForALeafTask()) }
        }
    }

    @Test
    fun `isChangeForALeafTask returns true if the Change has Activity target`() {
        testLetterboxLifecycleEvent {
            inputChange {}
            useChange { change -> assertFalse(change.isChangeForALeafTask()) }

            inputChange { activityTransitionInfo {} }
            useChange { change -> assertTrue(change.isChangeForALeafTask()) }
        }
    }

    @Test
    fun `isTranslucent returns the value from TaskInfo `() {
        testLetterboxLifecycleEvent {
            inputChange { runningTaskInfo { ti -> ti.isTopActivityTransparent = false } }
            useChange { change -> assertFalse(change.isTranslucent()) }

            inputChange { runningTaskInfo { ti -> ti.isTopActivityTransparent = true } }
            useChange { change -> assertTrue(change.isTranslucent()) }
        }
    }
}

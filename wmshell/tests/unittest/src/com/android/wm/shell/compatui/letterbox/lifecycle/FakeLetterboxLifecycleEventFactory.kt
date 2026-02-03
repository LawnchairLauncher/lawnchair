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

import android.graphics.Rect
import android.window.TransitionInfo.Change

/**
 * Fake [LetterboxLifecycleEventFactory] implementation.
 */
class FakeLetterboxLifecycleEventFactory(
    private val canHandleReturn: Boolean = true,
    private val eventToReturn: LetterboxLifecycleEvent? = null,
    private val eventToReturnFactory: (Change) -> LetterboxLifecycleEvent? = { eventToReturn }
) : LetterboxLifecycleEventFactory {

    companion object {
        @JvmStatic
        val FAKE_EVENT = LetterboxLifecycleEvent(taskBounds = Rect())
    }

    var canHandleInvokeTimes: Int = 0
    var lastCanHandleChange: Change? = null
    var createLifecycleEventInvokeTimes: Int = 0
    var lastCreateLifecycleEventChange: Change? = null

    override fun canHandle(change: Change): Boolean {
        canHandleInvokeTimes++
        lastCanHandleChange = change
        return canHandleReturn
    }

    override fun createLifecycleEvent(change: Change): LetterboxLifecycleEvent? {
        createLifecycleEventInvokeTimes++
        lastCreateLifecycleEventChange = change
        return eventToReturnFactory(change)
    }
}

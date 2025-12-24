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

import android.window.TransitionInfo.Change

/**
 * Abstracts the different way we can use to create a [LetterboxLifecycleEvent] from a
 * [TransitionInfo.Change].
 */
interface LetterboxLifecycleEventFactory {

    /**
     * @return [true] in case the specific implementation can handle the Change and return a
     *   [LetterboxLifecycleEvent] from it.
     */
    fun canHandle(change: Change): Boolean

    /**
     * If [#canHandle()] returns [true], this builds the [LetterboxLifecycleEvent] from the
     * [TransitionInfo.Change] in input. The [null] value represents a no-op and this should be the
     * value to return when [#canHandle()] returns [false].
     */
    fun createLifecycleEvent(change: Change): LetterboxLifecycleEvent?
}

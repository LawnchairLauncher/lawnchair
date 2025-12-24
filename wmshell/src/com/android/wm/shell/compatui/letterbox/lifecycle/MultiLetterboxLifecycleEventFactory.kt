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
 * [LetterboxLifecycleEventFactory] implementation which aggregates other implementation in a Chain
 * of Responsibility logic. The [candidates] are evaluated in order.
 */
class MultiLetterboxLifecycleEventFactory(
    private val candidates: List<LetterboxLifecycleEventFactory>
) : LetterboxLifecycleEventFactory {

    /** @return [true] in case any of the [candidates] can handle the [Change] in input. */
    override fun canHandle(change: Change): Boolean = candidates.any { it.canHandle(change) }

    /**
     * @return The [LetterboxLifecycleEvent] from the selected candidate which is the first in
     *   [candidates], if any, which [@canHandle] the [Change].
     */
    override fun createLifecycleEvent(change: Change): LetterboxLifecycleEvent? =
        candidates.firstOrNull { it.canHandle(change) }?.createLifecycleEvent(change)
}

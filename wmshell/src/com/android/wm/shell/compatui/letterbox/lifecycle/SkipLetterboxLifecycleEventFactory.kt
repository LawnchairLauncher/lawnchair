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
import com.android.wm.shell.dagger.WMSingleton
import javax.inject.Inject

/**
 * [LetterboxLifecycleEventFactory] implementation that ignore a Change if not related to
 * Letterboxing.
 */
@WMSingleton
class SkipLetterboxLifecycleEventFactory @Inject constructor() : LetterboxLifecycleEventFactory {

    // We also ignore closing Changes or changes related to Tasks without any activity.
    override fun canHandle(change: Change): Boolean = change.shouldSkipForLetterbox()

    // Although this LetterboxLifecycleEventFactory is able to handle the specific Change
    // it returns an empty LetterboxLifecycleEvent to basically ignore the Change.
    override fun createLifecycleEvent(change: Change): LetterboxLifecycleEvent? = null
}

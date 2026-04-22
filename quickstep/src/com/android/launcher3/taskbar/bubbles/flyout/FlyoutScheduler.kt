/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.taskbar.bubbles.flyout

import android.view.View

/** Interface for scheduling jobs by flyout. */
fun interface FlyoutScheduler {
    /** Runs the given [block] after layout. */
    fun runAfterLayout(block: () -> Unit)
}

/** A [FlyoutScheduler] that uses a Handler to schedule jobs. */
class HandlerScheduler(val view: View) : FlyoutScheduler {
    override fun runAfterLayout(block: () -> Unit) {
        view.post(block)
    }
}

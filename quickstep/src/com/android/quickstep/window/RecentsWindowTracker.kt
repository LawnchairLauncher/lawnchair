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

package com.android.quickstep.window

import com.android.app.displaylib.PerDisplayRepository
import com.android.launcher3.util.ContextTracker
import com.android.launcher3.util.DaggerSingletonObject
import com.android.quickstep.dagger.QuickstepBaseAppComponent

class RecentsWindowTracker : ContextTracker<RecentsWindowManager?>() {

    // Always return true since the recents window is meant to be agnostic of the launcher and
    // will not receive start callbacks.
    override fun isHomeStarted(context: RecentsWindowManager?) = true

    companion object {

        @JvmField
        val REPOSITORY_INSTANCE =
            DaggerSingletonObject<PerDisplayRepository<RecentsWindowTracker>>(
                QuickstepBaseAppComponent::getRecentsWindowTrackerRepository
            )
    }
}

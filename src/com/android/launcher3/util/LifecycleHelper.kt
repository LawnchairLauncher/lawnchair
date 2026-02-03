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

package com.android.launcher3.util

import android.app.Activity
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** Utility class for triggering various lifecycle events based on activity callbacks */
class LifecycleHelper(
    private val owner: SavedStateRegistryOwner,
    private val savedStateRegistryController: SavedStateRegistryController,
    private val lifecycleRegistry: LifecycleRegistry,
) : ActivityLifecycleCallbacksAdapter {

    override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
        savedStateRegistryController.performRestore(savedInstanceState)
    }

    override fun onActivityPostCreated(activity: Activity, savedInstanceState: Bundle?) {
        activity.window.decorView.setViewTreeLifecycleOwner(owner)
        activity.window.decorView.setViewTreeSavedStateRegistryOwner(owner)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onActivityPostStarted(activity: Activity) {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    override fun onActivityPostResumed(activity: Activity) {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onActivityPrePaused(activity: Activity) {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    override fun onActivityPreStopped(activity: Activity) {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onActivityPreDestroyed(activity: Activity) {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        savedStateRegistryController.performSave(bundle)
    }
}

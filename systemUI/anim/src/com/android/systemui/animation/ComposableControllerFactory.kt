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

package com.android.systemui.animation

import android.content.ComponentName
import android.util.Log
import com.android.systemui.animation.ActivityTransitionAnimator.Controller
import com.android.systemui.animation.ActivityTransitionAnimator.ControllerFactory
import kotlinx.coroutines.flow.MutableStateFlow

private const val TAG = "ComposableControllerFactory"

/**
 * [ControllerFactory] extension for Compose. Since composables are not guaranteed to be part of the
 * composition when [ControllerFactory.createController] is called, this class provides a way for
 * the composable to register itself at the time of composition, and deregister itself when
 * disposed.
 */
abstract class ComposableControllerFactory(
    cookie: ActivityTransitionAnimator.TransitionCookie,
    component: ComponentName?,
    launchCujType: Int? = null,
    returnCujType: Int? = null,
) : ControllerFactory(cookie, component, launchCujType, returnCujType) {
    /**
     * The object to be used to create [Controller]s, when its associate composable is in the
     * composition.
     */
    protected val expandable = MutableStateFlow<Expandable?>(null)

    /** To be called when the composable to be animated enters composition. */
    fun onCompose(expandable: Expandable) {
        if (TransitionAnimator.DEBUG) {
            Log.d(TAG, "Composable entered composition (expandable=$expandable")
        }
        this.expandable.value = expandable
    }

    /** To be called when the composable to be animated exits composition. */
    fun onDispose() {
        if (TransitionAnimator.DEBUG) {
            Log.d(TAG, "Composable left composition (expandable=${this.expandable.value}")
        }
        this.expandable.value = null
    }
}

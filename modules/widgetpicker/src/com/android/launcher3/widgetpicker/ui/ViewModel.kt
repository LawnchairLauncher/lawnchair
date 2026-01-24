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

package com.android.launcher3.widgetpicker.ui

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/**
 * An interface defining general structure of a view model in widget picker.
 */
// TODO(b/419844646): Once stable, introduce a reusable library.
interface ViewModel {
    /**
     * Implement this function to initialize the state of viewmodel by binding to the flows from the
     * interactors.
     *
     * Then when the UI calls [rememberViewModel], this callback function will be invoked.
     *
     * e.g. coroutineScope {
     *     launch { myInteractor.myData().collectLatest {..} }
     *     launch { myChildViewModel.onInit() }
     *
     *     awaitCancellation()
     * }
     */
    suspend fun onInit() {}
}

/**
 * Returns a remembered instance of the [ViewModel] of type [T].
 *
 * Also, initialize the view model.
 * @param key a unique key to remember the view model
 * @param animationDelay delay for animations to finish (if animations are enabled) before binding
 * data in view model for a smoother transition.
 */
@Composable
fun <T : ViewModel> rememberViewModel(
    key: Any = Unit,
    animationDelay: Long = 0L,
    factory: () -> T,
): T {
    val instance = remember(key) { factory() }
    // Here we initialize our view model using the launched effect so that it is tied to lifecycle
    // of the UI.
    LaunchedEffect(instance) {
        if (ValueAnimator.areAnimatorsEnabled() && animationDelay > 0) {
            delay(animationDelay)
        }
        instance.onInit()
    }

    return instance
}

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

package com.android.launcher3.model

import com.android.launcher3.dagger.LauncherAppSingleton
import java.util.function.Predicate
import javax.inject.Inject

/** Helper for the widgets model to load the filters that can be applied to available widgets. */
@LauncherAppSingleton
open class WidgetsFilterDataProvider @Inject constructor() {

    /** Filter that should be applied to the widget predictions */
    open val predictedWidgetsFilter: Predicate<WidgetItem>? = null

    /**
     * Filter that should be applied to the widgets list to see which widgets can be shown by
     * default.
     */
    open val defaultWidgetsFilter: Predicate<WidgetItem>? = null

    protected val listeners = mutableListOf<WidgetsFilterLoadedCallback>()

    /** Adds a callback for listening to filter changes */
    fun addFilterChangeCallback(callback: WidgetsFilterLoadedCallback) {
        listeners.add(callback)
    }

    /** Removes a previously added callback */
    fun removeFilterChangeCallback(callback: WidgetsFilterLoadedCallback) {
        listeners.remove(callback)
    }

    /** Interface for the model callback to be invoked when filters are loaded. */
    interface WidgetsFilterLoadedCallback {
        /** Method called back when widget filters are loaded */
        fun onWidgetsFilterLoaded()
    }
}

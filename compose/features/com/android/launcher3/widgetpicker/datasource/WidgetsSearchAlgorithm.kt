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

package com.android.launcher3.widgetpicker.datasource

import com.android.launcher3.widgetpicker.shared.model.WidgetApp

/**
 * An interface representing algorithm used to search widgets for widget picker.
 */
interface WidgetsSearchAlgorithm {
    /**
     * Perform any initializations here
     */
    suspend fun initialize()

    /**
     * Returns apps and its widgets that match the given input string.
     */
    suspend fun searchWidgets(query: String, corpus: List<WidgetApp>): List<WidgetApp>

    /**
     * Clear any service bindings or state here.
     */
    fun cleanup()
}

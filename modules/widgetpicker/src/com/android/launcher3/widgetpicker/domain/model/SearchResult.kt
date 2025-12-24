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

package com.android.launcher3.widgetpicker.domain.model

import com.android.launcher3.widgetpicker.shared.model.WidgetApp

/**
 * A domain layer object representing a specific search result (i.e. widget app).
 *
 * @param widgetApp an app hosting widgets whose name matched OR has widgets whose title /
 *   description matches the string that user searched.
 * @param resultLabel an optional unique string that differentiates [widgetApp].
 */
data class SearchResult(val widgetApp: WidgetApp, val resultLabel: String? = null)

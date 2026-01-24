package com.android.launcher3.widgetpicker.shared.model

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

/**
 * Represents an app hosting widgets that is shown in widget picker.
 *
 * @param id a unique identifier for the app
 * @param title a user readable title for the app; if null a placeholder text (e.g. "pending..")
 * is shown until the title is loaded.
 * @param widgets list of widgets available to picker from the app.
 */
data class WidgetApp(
    val id: WidgetAppId,
    val title: CharSequence?,
    val widgets: List<PickableWidget>,
)

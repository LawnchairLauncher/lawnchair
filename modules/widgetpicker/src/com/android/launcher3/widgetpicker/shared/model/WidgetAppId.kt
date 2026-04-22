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

package com.android.launcher3.widgetpicker.shared.model

import android.os.UserHandle

/**
 * A unique identifier to represent a specific app hosting widgets
 *
 * @property packageName package of the app hosting widgets; e.g. for widget such as
 *   "com.example.app/widget.MyWidgetReceiver", the app package should be "com.example.app".
 * @property userHandle the user to which this specific app instance belongs to; for example, work
 *   profile user will have a separate app instance from a personal app instance.
 * @property category a unique identifier representing a custom group that is not an app but is
 *   visually displayed as an app hosting certain types of widgets.
 */
data class WidgetAppId(val packageName: String, val userHandle: UserHandle, val category: Int?)

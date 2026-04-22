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

import android.content.ComponentName
import android.os.UserHandle

/**
 * Combination of properties that can be used to uniquely identify a widget in the widget picker.
 *
 * The information may be also used to combine with other data sources or other purposes such as
 * logging.
 *
 * @property componentName [ComponentName] of the widget's broadcast receiver e.g.
 *   "com.example.app/widget.MyWidgetReceiver".
 * @property userHandle the user to which this specific widget instance belongs to; for example, a
 *   work profile user will have a separate app instance holding a separate widget from a personal
 *   app instance.
 */
data class WidgetId(val componentName: ComponentName, val userHandle: UserHandle)

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

package com.android.wm.shell.shared.bubbles.logging

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Entry points for creating Bubbles. */
@Parcelize
enum class EntryPoint : Parcelable {
    TASKBAR_ICON_MENU,
    LAUNCHER_ICON_MENU,
    ALL_APPS_ICON_MENU,
    HOTSEAT_ICON_MENU,
    TASKBAR_ICON_DRAG,
    ALL_APPS_ICON_DRAG,
    NOTIFICATION,
    NOTIFICATION_BUBBLE_BUTTON,
}

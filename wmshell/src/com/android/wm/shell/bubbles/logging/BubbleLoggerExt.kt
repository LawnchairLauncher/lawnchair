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

@file:JvmName("BubbleLoggerExt")

package com.android.wm.shell.bubbles.logging

import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_BAR_CREATED_FROM_ALL_APPS_ICON_DRAG
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_BAR_CREATED_FROM_ALL_APPS_ICON_MENU
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_BAR_CREATED_FROM_HOTSEAT_ICON_MENU
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_BAR_CREATED_FROM_LAUNCHER_ICON_MENU
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_BAR_CREATED_FROM_NOTIF
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_BAR_CREATED_FROM_NOTIF_BUBBLE_BUTTON
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_BAR_CREATED_FROM_TASKBAR_ICON_MENU
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_BAR_CREATED_FROM_TASKBAR_ICON_DRAG
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_CREATED_FROM_ALL_APPS_ICON_MENU
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_CREATED_FROM_HOTSEAT_ICON_MENU
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_CREATED_FROM_LAUNCHER_ICON_MENU
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_CREATED_FROM_NOTIF
import com.android.wm.shell.bubbles.BubbleLogger.Event.BUBBLE_CREATED_FROM_NOTIF_BUBBLE_BUTTON
import com.android.wm.shell.shared.bubbles.logging.EntryPoint
import com.android.wm.shell.shared.bubbles.logging.EntryPoint.ALL_APPS_ICON_DRAG
import com.android.wm.shell.shared.bubbles.logging.EntryPoint.ALL_APPS_ICON_MENU
import com.android.wm.shell.shared.bubbles.logging.EntryPoint.HOTSEAT_ICON_MENU
import com.android.wm.shell.shared.bubbles.logging.EntryPoint.LAUNCHER_ICON_MENU
import com.android.wm.shell.shared.bubbles.logging.EntryPoint.NOTIFICATION
import com.android.wm.shell.shared.bubbles.logging.EntryPoint.NOTIFICATION_BUBBLE_BUTTON
import com.android.wm.shell.shared.bubbles.logging.EntryPoint.TASKBAR_ICON_DRAG
import com.android.wm.shell.shared.bubbles.logging.EntryPoint.TASKBAR_ICON_MENU

/** Converts the [EntryPoint] to a bubble bar UiEvent. */
fun EntryPoint.toBubbleBarUiEvent() = when (this) {
    TASKBAR_ICON_MENU -> BUBBLE_BAR_CREATED_FROM_TASKBAR_ICON_MENU
    LAUNCHER_ICON_MENU -> BUBBLE_BAR_CREATED_FROM_LAUNCHER_ICON_MENU
    ALL_APPS_ICON_MENU -> BUBBLE_BAR_CREATED_FROM_ALL_APPS_ICON_MENU
    HOTSEAT_ICON_MENU -> BUBBLE_BAR_CREATED_FROM_HOTSEAT_ICON_MENU
    TASKBAR_ICON_DRAG -> BUBBLE_BAR_CREATED_FROM_TASKBAR_ICON_DRAG
    ALL_APPS_ICON_DRAG -> BUBBLE_BAR_CREATED_FROM_ALL_APPS_ICON_DRAG
    NOTIFICATION -> BUBBLE_BAR_CREATED_FROM_NOTIF
    NOTIFICATION_BUBBLE_BUTTON -> BUBBLE_BAR_CREATED_FROM_NOTIF_BUBBLE_BUTTON
}

/** Converts the [EntryPoint] to a floating bubbles UiEvent. */
fun EntryPoint.toFloatingBubblesUiEvent() = when (this) {
    LAUNCHER_ICON_MENU -> BUBBLE_CREATED_FROM_LAUNCHER_ICON_MENU
    ALL_APPS_ICON_MENU -> BUBBLE_CREATED_FROM_ALL_APPS_ICON_MENU
    HOTSEAT_ICON_MENU -> BUBBLE_CREATED_FROM_HOTSEAT_ICON_MENU
    NOTIFICATION -> BUBBLE_CREATED_FROM_NOTIF
    NOTIFICATION_BUBBLE_BUTTON -> BUBBLE_CREATED_FROM_NOTIF_BUBBLE_BUTTON
    // the events below are only applicable to bubble bar
    ALL_APPS_ICON_DRAG,
    TASKBAR_ICON_DRAG,
    TASKBAR_ICON_MENU -> null
}

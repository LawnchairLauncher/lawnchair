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
package com.android.wm.shell.desktopmode.common

import android.graphics.Rect
import com.android.internal.jank.Cuj
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.InputMethod
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ResizeTrigger
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum
import com.android.wm.shell.desktopmode.common.ToggleTaskSizeInteraction.AmbiguousSource
import com.android.wm.shell.desktopmode.common.ToggleTaskSizeInteraction.Source

/** Represents a user interaction to toggle a desktop task's size from to maximize or vice versa. */
data class ToggleTaskSizeInteraction
@JvmOverloads
constructor(
    val direction: Direction,
    val source: Source,
    val inputMethod: InputMethod,
    val animationStartBounds: Rect? = null,
) {
    constructor(
        isMaximized: Boolean,
        source: Source,
        inputMethod: InputMethod,
    ) : this(
        direction = if (isMaximized) Direction.RESTORE else Direction.MAXIMIZE,
        source = source,
        inputMethod = inputMethod,
    )

    val jankTag: String? =
        when (source) {
            Source.HEADER_BUTTON_TO_MAXIMIZE -> "caption_bar_button"
            Source.HEADER_BUTTON_TO_RESTORE -> "caption_bar_button"
            Source.KEYBOARD_SHORTCUT -> null
            Source.HEADER_DRAG_TO_TOP -> null
            Source.MAXIMIZE_MENU_TO_MAXIMIZE -> "maximize_menu"
            Source.MAXIMIZE_MENU_TO_RESTORE -> "maximize_menu"
            Source.DOUBLE_TAP_TO_MAXIMIZE -> "double_tap"
            Source.DOUBLE_TAP_TO_RESTORE -> "double_tap"
        }
    val uiEvent: DesktopUiEventEnum? =
        when (source) {
            Source.HEADER_BUTTON_TO_MAXIMIZE ->
                DesktopUiEventEnum.DESKTOP_WINDOW_MAXIMIZE_BUTTON_TAP
            Source.HEADER_BUTTON_TO_RESTORE -> DesktopUiEventEnum.DESKTOP_WINDOW_RESTORE_BUTTON_TAP
            Source.KEYBOARD_SHORTCUT -> null
            Source.HEADER_DRAG_TO_TOP -> null
            Source.MAXIMIZE_MENU_TO_MAXIMIZE -> {
                DesktopUiEventEnum.DESKTOP_WINDOW_MAXIMIZE_BUTTON_MENU_TAP_TO_MAXIMIZE
            }
            Source.MAXIMIZE_MENU_TO_RESTORE -> {
                DesktopUiEventEnum.DESKTOP_WINDOW_MAXIMIZE_BUTTON_MENU_TAP_TO_RESTORE
            }
            Source.DOUBLE_TAP_TO_MAXIMIZE -> {
                DesktopUiEventEnum.DESKTOP_WINDOW_HEADER_DOUBLE_TAP_TO_MAXIMIZE
            }
            Source.DOUBLE_TAP_TO_RESTORE -> {
                DesktopUiEventEnum.DESKTOP_WINDOW_HEADER_DOUBLE_TAP_TO_RESTORE
            }
        }
    val resizeTrigger =
        when (source) {
            Source.HEADER_BUTTON_TO_MAXIMIZE -> ResizeTrigger.MAXIMIZE_BUTTON
            Source.HEADER_BUTTON_TO_RESTORE -> ResizeTrigger.MAXIMIZE_BUTTON
            Source.KEYBOARD_SHORTCUT -> ResizeTrigger.UNKNOWN_RESIZE_TRIGGER
            Source.HEADER_DRAG_TO_TOP -> ResizeTrigger.DRAG_TO_TOP_RESIZE_TRIGGER
            Source.MAXIMIZE_MENU_TO_MAXIMIZE -> ResizeTrigger.MAXIMIZE_MENU
            Source.MAXIMIZE_MENU_TO_RESTORE -> ResizeTrigger.MAXIMIZE_MENU
            Source.DOUBLE_TAP_TO_MAXIMIZE -> ResizeTrigger.DOUBLE_TAP_APP_HEADER
            Source.DOUBLE_TAP_TO_RESTORE -> ResizeTrigger.DOUBLE_TAP_APP_HEADER
        }
    val cujTracing: Int? =
        when (source) {
            Source.HEADER_BUTTON_TO_MAXIMIZE -> Cuj.CUJ_DESKTOP_MODE_MAXIMIZE_WINDOW
            Source.HEADER_BUTTON_TO_RESTORE -> Cuj.CUJ_DESKTOP_MODE_UNMAXIMIZE_WINDOW
            Source.KEYBOARD_SHORTCUT -> null
            Source.HEADER_DRAG_TO_TOP -> null
            Source.MAXIMIZE_MENU_TO_MAXIMIZE -> Cuj.CUJ_DESKTOP_MODE_MAXIMIZE_WINDOW
            Source.MAXIMIZE_MENU_TO_RESTORE -> Cuj.CUJ_DESKTOP_MODE_UNMAXIMIZE_WINDOW
            Source.DOUBLE_TAP_TO_MAXIMIZE -> Cuj.CUJ_DESKTOP_MODE_MAXIMIZE_WINDOW
            Source.DOUBLE_TAP_TO_RESTORE -> Cuj.CUJ_DESKTOP_MODE_UNMAXIMIZE_WINDOW
        }

    /** The direction to which the task is being resized. */
    enum class Direction {
        MAXIMIZE,
        RESTORE,
    }

    /** The user interaction source. */
    enum class Source {
        HEADER_BUTTON_TO_MAXIMIZE,
        HEADER_BUTTON_TO_RESTORE,
        KEYBOARD_SHORTCUT,
        HEADER_DRAG_TO_TOP,
        MAXIMIZE_MENU_TO_MAXIMIZE,
        MAXIMIZE_MENU_TO_RESTORE,
        DOUBLE_TAP_TO_MAXIMIZE,
        DOUBLE_TAP_TO_RESTORE,
    }

    /**
     * Temporary sources for interactions that should be broken into more specific sources, for
     * example, the header button click should use [Source.HEADER_BUTTON_TO_MAXIMIZE] and
     * [Source.HEADER_BUTTON_TO_RESTORE].
     *
     * TODO: b/341320112 - break these out into different [Source]s.
     */
    enum class AmbiguousSource {
        HEADER_BUTTON,
        MAXIMIZE_MENU,
        DOUBLE_TAP,
    }
}

/** Returns the non-ambiguous [Source] based on the maximized state of the task. */
fun AmbiguousSource.toSource(isMaximized: Boolean): Source {
    return when (this) {
        AmbiguousSource.HEADER_BUTTON ->
            if (isMaximized) {
                Source.HEADER_BUTTON_TO_RESTORE
            } else {
                Source.HEADER_BUTTON_TO_MAXIMIZE
            }
        AmbiguousSource.MAXIMIZE_MENU ->
            if (isMaximized) {
                Source.MAXIMIZE_MENU_TO_RESTORE
            } else {
                Source.MAXIMIZE_MENU_TO_MAXIMIZE
            }
        AmbiguousSource.DOUBLE_TAP ->
            if (isMaximized) {
                Source.DOUBLE_TAP_TO_RESTORE
            } else {
                Source.DOUBLE_TAP_TO_MAXIMIZE
            }
    }
}

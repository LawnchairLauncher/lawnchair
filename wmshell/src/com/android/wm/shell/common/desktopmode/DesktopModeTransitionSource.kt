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

package com.android.wm.shell.shared.desktopmode

import android.os.Parcel
import android.os.Parcelable

/** Transition source types for Desktop Mode. */
enum class DesktopModeTransitionSource : Parcelable {
    /** Transitions that originated as a consequence of task dragging. */
    TASK_DRAG,
    /** Transitions that originated from an app from Overview. */
    APP_FROM_OVERVIEW,
    /** Transitions that originated from app handle menu button */
    APP_HANDLE_MENU_BUTTON,
    /** Transitions that originated as a result of keyboard shortcuts. */
    KEYBOARD_SHORTCUT,
    /** Transitions with source unknown. */
    UNKNOWN;

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(name)
    }

    companion object {
        @JvmField
        val CREATOR =
            object : Parcelable.Creator<DesktopModeTransitionSource> {
                override fun createFromParcel(parcel: Parcel): DesktopModeTransitionSource {
                    return parcel.readString()?.let { valueOf(it) } ?: UNKNOWN
                }

                override fun newArray(size: Int) = arrayOfNulls<DesktopModeTransitionSource>(size)
            }
    }
}

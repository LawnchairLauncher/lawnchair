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

package com.android.wm.shell.shared.desktopmode

import android.os.Parcel
import android.os.Parcelable

/** Reason for moving a task to front in Desktop Mode. */
enum class DesktopTaskToFrontReason : Parcelable {
    UNKNOWN,
    TASKBAR_TAP,
    ALT_TAB,
    TASKBAR_MANAGE_WINDOW;

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(name)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<DesktopTaskToFrontReason> {
            override fun createFromParcel(parcel: Parcel): DesktopTaskToFrontReason {
                return parcel.readString()?.let { valueOf(it) } ?: UNKNOWN
            }

            override fun newArray(size: Int) = arrayOfNulls<DesktopTaskToFrontReason>(size)
        }
    }
}

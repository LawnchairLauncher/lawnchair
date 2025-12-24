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

package com.android.wm.shell.windowdecor.viewholder

import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable

/**
 * A simple data class to hold identifying information, including a bounding Rect and a display ID.
 *
 * @property rect The screen coordinate Rect associated with the handle.
 * @property displayId The ID of the display where the handle exists.
 */
data class AppHandleIdentifier(
    val rect: Rect,
    val displayId: Int,
    val taskId: Int,
    val windowingMode: AppHandleWindowingMode,
) : Parcelable {

    /** Indicates which type of windowing mode this AppHandle's window is in. */
    enum class AppHandleWindowingMode {
        APP_HANDLE_WINDOWING_MODE_SPLIT_SCREEN,
        APP_HANDLE_WINDOWING_MODE_BUBBLE,
        APP_HANDLE_WINDOWING_MODE_FULLSCREEN,
    }

    constructor(
        parcel: Parcel
    ) : this(
        parcel.readTypedObject(Rect.CREATOR) ?: Rect(),
        parcel.readInt(),
        parcel.readInt(),
        AppHandleWindowingMode.valueOf(
            parcel.readString() ?: "APP_HANDLE_WINDOWING_MODE_FULLSCREEN"
        ),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeTypedObject(rect, flags)
        parcel.writeInt(displayId)
        parcel.writeInt(taskId)
        parcel.writeString(windowingMode.name)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<AppHandleIdentifier> {
        override fun createFromParcel(parcel: Parcel): AppHandleIdentifier {
            return AppHandleIdentifier(parcel)
        }

        override fun newArray(size: Int): Array<AppHandleIdentifier?> {
            return arrayOfNulls(size)
        }
    }
}

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

package com.android.wm.shell.shared.bubbles

import android.graphics.drawable.Icon
import android.os.Parcel
import android.os.Parcelable

/** The contents of the flyout message to be passed to launcher for rendering in the bubble bar. */
class ParcelableFlyoutMessage(
    val icon: Icon?,
    val title: String?,
    val message: String?,
) : Parcelable {

    constructor(
        parcel: Parcel
    ) : this(
        icon = parcel.readParcelable(Icon::class.java.classLoader),
        title = parcel.readString(),
        message = parcel.readString(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(icon, flags)
        parcel.writeString(title)
        parcel.writeString(message)
    }

    override fun describeContents() = 0

    companion object {
        @JvmField
        val CREATOR =
            object : Parcelable.Creator<ParcelableFlyoutMessage> {
                override fun createFromParcel(parcel: Parcel) = ParcelableFlyoutMessage(parcel)

                override fun newArray(size: Int) = arrayOfNulls<ParcelableFlyoutMessage>(size)
            }
    }
}

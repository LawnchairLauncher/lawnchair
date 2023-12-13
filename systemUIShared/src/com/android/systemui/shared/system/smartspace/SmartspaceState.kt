/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.shared.system.smartspace

import android.graphics.Rect
import android.os.Parcel
import android.os.Parcelable

/**
 * Represents the state of a SmartSpace, including its location on screen and the index of the
 * currently selected page. This object contains all of the information needed to synchronize two
 * SmartSpace instances so that we can perform shared-element transitions between them.
 */
class SmartspaceState() : Parcelable {
    var boundsOnScreen: Rect = Rect()
    var selectedPage = 0
    var visibleOnScreen = false

    constructor(parcel: Parcel) : this() {
        this.boundsOnScreen = parcel.readParcelable(Rect::javaClass.javaClass.classLoader) ?: Rect()
        this.selectedPage = parcel.readInt()
        this.visibleOnScreen = parcel.readBoolean()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(boundsOnScreen, 0)
        dest.writeInt(selectedPage)
        dest.writeBoolean(visibleOnScreen)
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun toString(): String {
        return "boundsOnScreen: $boundsOnScreen, " +
                "selectedPage: $selectedPage, " +
                "visibleOnScreen: $visibleOnScreen"
    }

    companion object CREATOR : Parcelable.Creator<SmartspaceState> {
        override fun createFromParcel(parcel: Parcel): SmartspaceState {
            return SmartspaceState(parcel)
        }

        override fun newArray(size: Int): Array<SmartspaceState?> {
            return arrayOfNulls(size)
        }
    }
}
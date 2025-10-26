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

import android.annotation.IntDef
import android.os.Parcel
import android.os.Parcelable

/**
 * The location of the bubble bar.
 */
enum class BubbleBarLocation : Parcelable {
    /**
     * Place bubble bar at the default location for the chosen system language.
     * If an RTL language is used, it is on the left. Otherwise on the right.
     */
    DEFAULT,
    /** Default bubble bar location is overridden. Place bubble bar on the left. */
    LEFT,
    /** Default bubble bar location is overridden. Place bubble bar on the right. */
    RIGHT;

    /**
     * Returns whether bubble bar is pinned to the left edge or right edge.
     */
    fun isOnLeft(isRtl: Boolean): Boolean {
        if (this == DEFAULT) {
            return isRtl
        }
        return this == LEFT
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(name)
    }

    companion object {
        @JvmField
        val CREATOR = object : Parcelable.Creator<BubbleBarLocation> {
            override fun createFromParcel(parcel: Parcel): BubbleBarLocation {
                return parcel.readString()?.let { valueOf(it) } ?: DEFAULT
            }

            override fun newArray(size: Int) = arrayOfNulls<BubbleBarLocation>(size)
        }
    }

    /** Define set of constants that allow to determine why location changed. */
    @IntDef(
        UpdateSource.DRAG_BAR,
        UpdateSource.DRAG_BUBBLE,
        UpdateSource.DRAG_EXP_VIEW,
        UpdateSource.A11Y_ACTION_BAR,
        UpdateSource.A11Y_ACTION_BUBBLE,
        UpdateSource.A11Y_ACTION_EXP_VIEW,
        UpdateSource.APP_ICON_DRAG,
        UpdateSource.DRAG_TASK,
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class UpdateSource {
        companion object {
            /** Location changed from dragging the bar */
            const val DRAG_BAR = 1

            /** Location changed from dragging the bubble */
            const val DRAG_BUBBLE = 2

            /** Location changed from dragging the expanded view */
            const val DRAG_EXP_VIEW = 3

            /** Location changed via a11y action on the bar */
            const val A11Y_ACTION_BAR = 4

            /** Location changed via a11y action on the bubble */
            const val A11Y_ACTION_BUBBLE = 5

            /** Location changed via a11y action on the expanded view */
            const val A11Y_ACTION_EXP_VIEW = 6

            /** Location changed from dragging the application icon to the bubble bar */
            const val APP_ICON_DRAG = 7

            /** Location changed from dragging a running task to the bubble bar */
            const val DRAG_TASK = 8
        }
    }
}

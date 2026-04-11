/*
 * Copyright (C) 2025 Lawnchair
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

package app.lawnchair.widget

import android.os.Parcel
import android.os.Parcelable
import com.android.launcher3.LauncherSettings

/**
 * Represents a stack of widgets that can be displayed on top of each other.
 * Widgets in a stack share the same position on the home screen but may have
 * different native sizes; each widget is scaled to fit the stack bounds.
 */
data class WidgetStackInfo(
    /**
     * The ID of the stack (same as the first widget's ID in the stack)
     */
    val stackId: Long,

    /**
     * List of widget IDs in the stack, in order
     */
    val widgetIds: List<Int>,

    /**
     * Current index of the visible widget in the stack
     */
    var currentIndex: Int = 0,

    /**
     * Whether the stack should automatically rotate between widgets
     */
    var autoRotate: Boolean = false,

    /**
     * The container ID where this stack is located
     */
    val container: Int = LauncherSettings.Favorites.CONTAINER_DESKTOP,

    /**
     * The screen ID where this stack is located
     */
    val screenId: Int = 0,

    /**
     * The cell X position
     */
    val cellX: Int = 0,

    /**
     * The cell Y position
     */
    val cellY: Int = 0,

    /**
     * The span X (width in cells)
     */
    val spanX: Int = 2,

    /**
     * The span Y (height in cells)
     */
    val spanY: Int = 2,
) : Parcelable {

    constructor(parcel: Parcel) : this(
        stackId = parcel.readLong(),
        widgetIds = parcel.createIntArray()?.toList() ?: emptyList(),
        currentIndex = parcel.readInt(),
        autoRotate = parcel.readByte() != 0.toByte(),
        container = parcel.readInt(),
        screenId = parcel.readInt(),
        cellX = parcel.readInt(),
        cellY = parcel.readInt(),
        spanX = parcel.readInt(),
        spanY = parcel.readInt(),
    )

    /**
     * Returns the currently visible widget ID
     */
    fun getCurrentWidgetId(): Int? {
        return if (widgetIds.isNotEmpty() && currentIndex in widgetIds.indices) {
            widgetIds[currentIndex]
        } else {
            null
        }
    }

    /**
     * Advances to the next widget in the stack
     */
    fun advanceToNext(): Int? {
        if (widgetIds.isEmpty()) return null
        currentIndex = (currentIndex + 1) % widgetIds.size
        return getCurrentWidgetId()
    }

    /**
     * Returns the number of widgets in the stack
     */
    fun size(): Int = widgetIds.size

    /**
     * Checks if the stack is valid (has at least one widget)
     */
    fun isValid(): Boolean = widgetIds.isNotEmpty()

    /**
     * Creates a copy of this WidgetStackInfo with updated widget IDs.
     * This is a helper method for Java interop since Kotlin's copy() uses named parameters.
     */
    fun copyWithWidgetIds(newWidgetIds: List<Int>): WidgetStackInfo {
        val clampedIndex =
            currentIndex.coerceIn(0, newWidgetIds.lastIndex.coerceAtLeast(0))
        return copy(widgetIds = newWidgetIds, currentIndex = clampedIndex)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(stackId)
        parcel.writeIntArray(widgetIds.toIntArray())
        parcel.writeInt(currentIndex)
        parcel.writeByte((if (autoRotate) 1 else 0).toByte())
        parcel.writeInt(container)
        parcel.writeInt(screenId)
        parcel.writeInt(cellX)
        parcel.writeInt(cellY)
        parcel.writeInt(spanX)
        parcel.writeInt(spanY)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<WidgetStackInfo> {
        override fun createFromParcel(parcel: Parcel): WidgetStackInfo {
            return WidgetStackInfo(parcel)
        }

        override fun newArray(size: Int): Array<WidgetStackInfo?> {
            return arrayOfNulls(size)
        }
    }
}

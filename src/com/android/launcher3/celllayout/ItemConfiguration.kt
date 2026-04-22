/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.celllayout

import android.graphics.Rect
import android.util.ArrayMap
import android.view.View
import com.android.launcher3.util.CellAndSpan

/** Represents the solution to a reorder of items in the Workspace. */
class ItemConfiguration : CellAndSpan() {
    @JvmField val map = ArrayMap<View, CellAndSpan>()
    private val savedMap = ArrayMap<View, CellAndSpan>()

    @JvmField val sortedViews = ArrayList<View>()

    @JvmField var intersectingViews: ArrayList<View> = ArrayList()

    @JvmField var isSolution = false
    fun save() {
        // Copy current state into savedMap
        map.forEach { (k, v) -> savedMap[k]?.copyFrom(v) }
    }

    fun restore() {
        // Restore current state from savedMap
        savedMap.forEach { (k, v) -> map[k]?.copyFrom(v) }
    }

    fun add(v: View, cs: CellAndSpan) {
        map[v] = cs
        savedMap[v] = CellAndSpan()
        sortedViews.add(v)
    }

    fun area(): Int {
        return spanX * spanY
    }

    fun getBoundingRectForViews(views: ArrayList<View>, outRect: Rect) {
        views
            .mapNotNull { v -> map[v] }
            .forEachIndexed { i, c ->
                if (i == 0) outRect.set(c.cellX, c.cellY, c.cellX + c.spanX, c.cellY + c.spanY)
                else outRect.union(c.cellX, c.cellY, c.cellX + c.spanX, c.cellY + c.spanY)
            }
    }
}

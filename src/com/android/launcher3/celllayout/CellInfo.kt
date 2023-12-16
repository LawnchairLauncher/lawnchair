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

import android.view.View
import com.android.launcher3.celllayout.CellPosMapper.CellPos
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.CellAndSpan

// This class stores info for two purposes:
// 1. When dragging items (mDragInfo in Workspace), we store the View, its cellX & cellY,
//    its spanX, spanY, and the screen it is on
// 2. When long clicking on an empty cell in a CellLayout, we save information about the
//    cellX and cellY coordinates and which page was clicked. We then set this as a tag on
//    the CellLayout that was long clicked
class CellInfo(v: View?, info: ItemInfo, cellPos: CellPos) :
    CellAndSpan(cellPos.cellX, cellPos.cellY, info.spanX, info.spanY) {
    @JvmField val cell: View?
    @JvmField val screenId: Int
    @JvmField val container: Int

    init {
        cell = v
        screenId = cellPos.screenId
        container = info.container
    }

    override fun toString(): String {
        return "CellInfo(cell=$cell, screenId=$screenId, container=$container)"
    }
}

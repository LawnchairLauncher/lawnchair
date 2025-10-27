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

package com.android.launcher3.celllayout.integrationtest

import android.graphics.Point
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import com.android.launcher3.CellLayout
import com.android.launcher3.Utilities
import com.android.launcher3.Workspace
import com.android.launcher3.util.CellAndSpan
import com.android.launcher3.widget.LauncherAppWidgetHostView

object TestUtils {
    fun <T> searchChildren(viewGroup: ViewGroup, type: Class<T>): T? where T : View {
        for (i in 0..<viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (type.isInstance(child)) {
                return type.cast(child)
            }
            if (child is ViewGroup) {
                val result = searchChildren(child, type)
                if (result != null) {
                    return result
                }
            }
        }
        return null
    }

    fun getWidgetAtCell(
        workspace: Workspace<*>,
        cellX: Int,
        cellY: Int
    ): LauncherAppWidgetHostView {
        val view =
            (workspace.getPageAt(workspace.currentPage) as CellLayout).getChildAt(cellX, cellY)
        assert(view != null) { "There is no view at $cellX , $cellY" }
        assert(view is LauncherAppWidgetHostView) { "The view at $cellX , $cellY is not a widget" }
        return view as LauncherAppWidgetHostView
    }

    fun getCellTopLeftRelativeToWorkspace(
        workspace: Workspace<*>,
        cellAndSpan: CellAndSpan
    ): Point {
        val target = Rect()
        val cellLayout = workspace.getPageAt(workspace.currentPage) as CellLayout
        cellLayout.cellToRect(
            cellAndSpan.cellX,
            cellAndSpan.cellY,
            cellAndSpan.spanX,
            cellAndSpan.spanY,
            target
        )
        val point = floatArrayOf(target.left.toFloat(), target.top.toFloat())
        Utilities.getDescendantCoordRelativeToAncestor(cellLayout, workspace, point, false)
        return Point(point[0].toInt(), point[1].toInt())
    }
}

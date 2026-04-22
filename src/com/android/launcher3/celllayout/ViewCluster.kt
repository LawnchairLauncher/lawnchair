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
import android.view.View
import com.android.launcher3.CellLayout
import java.util.Collections

/**
 * This helper class defines a cluster of views. It helps with defining complex edges of the cluster
 * and determining how those edges interact with other views. The edges essentially define a
 * fine-grained boundary around the cluster of views -- like a more precise version of a bounding
 * box.
 */
class ViewCluster(
    private val mCellLayout: CellLayout,
    views: ArrayList<View>,
    val config: ItemConfiguration
) {

    @JvmField val views = ArrayList<View>(views)
    private val boundingRect = Rect()

    private val leftEdge = IntArray(mCellLayout.countY)
    private val rightEdge = IntArray(mCellLayout.countY)
    private val topEdge = IntArray(mCellLayout.countX)
    private val bottomEdge = IntArray(mCellLayout.countX)

    private var dirtyEdges = 0
    private var boundingRectDirty = false

    val comparator: PositionComparator = PositionComparator()

    init {
        resetEdges()
    }
    private fun resetEdges() {
        for (i in 0 until mCellLayout.countX) {
            topEdge[i] = -1
            bottomEdge[i] = -1
        }
        for (i in 0 until mCellLayout.countY) {
            leftEdge[i] = -1
            rightEdge[i] = -1
        }
        dirtyEdges = LEFT or TOP or RIGHT or BOTTOM
        boundingRectDirty = true
    }

    private fun computeEdge(which: Int) =
        views
            .mapNotNull { v -> config.map[v] }
            .forEach { cs ->
                val left = cs.cellX
                val right = cs.cellX + cs.spanX
                val top = cs.cellY
                val bottom = cs.cellY + cs.spanY
                when (which) {
                    LEFT ->
                        for (j in top until bottom) {
                            if (left < leftEdge[j] || leftEdge[j] < 0) {
                                leftEdge[j] = left
                            }
                        }
                    RIGHT ->
                        for (j in top until bottom) {
                            if (right > rightEdge[j]) {
                                rightEdge[j] = right
                            }
                        }
                    TOP ->
                        for (j in left until right) {
                            if (top < topEdge[j] || topEdge[j] < 0) {
                                topEdge[j] = top
                            }
                        }
                    BOTTOM ->
                        for (j in left until right) {
                            if (bottom > bottomEdge[j]) {
                                bottomEdge[j] = bottom
                            }
                        }
                }
            }

    fun isViewTouchingEdge(v: View?, whichEdge: Int): Boolean {
        val cs = config.map[v] ?: return false
        val left = cs.cellX
        val right = cs.cellX + cs.spanX
        val top = cs.cellY
        val bottom = cs.cellY + cs.spanY
        if ((dirtyEdges and whichEdge) == whichEdge) {
            computeEdge(whichEdge)
            dirtyEdges = dirtyEdges and whichEdge.inv()
        }
        return when (whichEdge) {
            // In this case if any of the values of leftEdge is equal to right, which is the
            // rightmost x value of the view, it means that the cluster is touching the view from
            // the left the same logic applies for the other sides.
            LEFT -> edgeContainsValue(top, bottom, leftEdge, right)
            RIGHT -> edgeContainsValue(top, bottom, rightEdge, left)
            TOP -> edgeContainsValue(left, right, topEdge, bottom)
            BOTTOM -> edgeContainsValue(left, right, bottomEdge, top)
            else -> false
        }
    }

    private fun edgeContainsValue(start: Int, end: Int, edge: IntArray, value: Int): Boolean {
        for (i in start until end) {
            if (edge[i] == value) {
                return true
            }
        }
        return false
    }

    fun shift(whichEdge: Int, delta: Int) {
        views
            .mapNotNull { v -> config.map[v] }
            .forEach { c ->
                when (whichEdge) {
                    LEFT -> c.cellX -= delta
                    RIGHT -> c.cellX += delta
                    TOP -> c.cellY -= delta
                    BOTTOM -> c.cellY += delta
                    else -> c.cellY += delta
                }
            }
        resetEdges()
    }

    fun addView(v: View) {
        views.add(v)
        resetEdges()
    }

    fun getBoundingRect(): Rect {
        if (boundingRectDirty) {
            config.getBoundingRectForViews(views, boundingRect)
        }
        return boundingRect
    }

    inner class PositionComparator : Comparator<View?> {
        var whichEdge = 0
        override fun compare(left: View?, right: View?): Int {
            val l = config.map[left]
            val r = config.map[right]
            if (l == null || r == null) throw NullPointerException()
            return when (whichEdge) {
                LEFT -> r.cellX + r.spanX - (l.cellX + l.spanX)
                RIGHT -> l.cellX - r.cellX
                TOP -> r.cellY + r.spanY - (l.cellY + l.spanY)
                BOTTOM -> l.cellY - r.cellY
                else -> l.cellY - r.cellY
            }
        }
    }

    fun sortConfigurationForEdgePush(edge: Int) {
        comparator.whichEdge = edge
        Collections.sort(config.sortedViews, comparator)
    }

    companion object {
        const val LEFT = 1 shl 0
        const val TOP = 1 shl 1
        const val RIGHT = 1 shl 2
        const val BOTTOM = 1 shl 3
    }
}

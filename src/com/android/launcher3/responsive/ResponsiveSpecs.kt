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

package com.android.launcher3.responsive

import android.util.Log

/**
 * Base class for responsive specs that holds a list of width and height specs.
 *
 * @param widthSpecs List of width responsive specifications
 * @param heightSpecs List of height responsive specifications
 */
abstract class ResponsiveSpecs<T : ResponsiveSpec>(
    val widthSpecs: List<T>,
    val heightSpecs: List<T>
) {

    init {
        check(widthSpecs.isNotEmpty() && heightSpecs.isNotEmpty()) {
            "${this::class.simpleName} is incomplete - " +
                "width list size = ${widthSpecs.size}; " +
                "height list size = ${heightSpecs.size}."
        }
    }

    /**
     * Get a [ResponsiveSpec] for width within the breakpoint.
     *
     * @param availableWidth The width breakpoint for the spec
     * @return A [ResponsiveSpec] for width.
     */
    fun getWidthSpec(availableWidth: Int): T {
        val spec = widthSpecs.firstOrNull { availableWidth <= it.maxAvailableSize }
        check(spec != null) { "No available width spec found within $availableWidth." }
        return spec
    }

    /**
     * Get a [ResponsiveSpec] for height within the breakpoint.
     *
     * @param availableHeight The height breakpoint for the spec
     * @return A [ResponsiveSpec] for height.
     */
    fun getHeightSpec(availableHeight: Int): T {
        val spec = heightSpecs.firstOrNull { availableHeight <= it.maxAvailableSize }
        check(spec != null) { "No available height spec found within $availableHeight." }
        return spec
    }
}

/**
 * Base class for a responsive specification that is used to calculate the paddings, gutter and cell
 * size.
 *
 * @param maxAvailableSize indicates the breakpoint to use this specification.
 * @param specType indicates whether the paddings and gutters will be applied vertically or
 *   horizontally.
 * @param startPadding padding used at the top or left (right in RTL) in the workspace folder.
 * @param endPadding padding used at the bottom or right (left in RTL) in the workspace folder.
 * @param gutter the space between the cells vertically or horizontally depending on the [specType].
 * @param cellSize height or width of the cell depending on the [specType].
 */
abstract class ResponsiveSpec(
    open val maxAvailableSize: Int,
    open val specType: SpecType,
    open val startPadding: SizeSpec,
    open val endPadding: SizeSpec,
    open val gutter: SizeSpec,
    open val cellSize: SizeSpec
) {
    open fun isValid(): Boolean {
        if (maxAvailableSize <= 0) {
            Log.e(LOG_TAG, "${this::class.simpleName}#isValid - maxAvailableSize <= 0")
            return false
        }

        // All specs need to be individually valid
        if (!allSpecsAreValid()) {
            Log.e(LOG_TAG, "${this::class.simpleName}#isValid - !allSpecsAreValid()")
            return false
        }

        return true
    }

    private fun allSpecsAreValid(): Boolean {
        return startPadding.isValid() &&
            endPadding.isValid() &&
            gutter.isValid() &&
            cellSize.isValid()
    }

    enum class SpecType {
        HEIGHT,
        WIDTH
    }

    companion object {
        private const val LOG_TAG = "ResponsiveSpec"
    }
}

/**
 * Calculated responsive specs contains the final paddings, gutter and cell size in pixels after
 * they are calculated from the available space, cells and workspace specs.
 */
sealed class CalculatedResponsiveSpec {
    var availableSpace: Int = 0
        private set

    var cells: Int = 0
        private set

    var startPaddingPx: Int = 0
        private set

    var endPaddingPx: Int = 0
        private set

    var gutterPx: Int = 0
        private set

    var cellSizePx: Int = 0
        private set

    var spec: ResponsiveSpec
        private set

    constructor(
        availableSpace: Int,
        cells: Int,
        spec: ResponsiveSpec,
        calculatedWorkspaceSpec: CalculatedWorkspaceSpec
    ) {
        this.availableSpace = availableSpace
        this.cells = cells
        this.spec = spec

        // Map if is fixedSize, ofAvailableSpace or matchWorkspace
        startPaddingPx =
            spec.startPadding.getCalculatedValue(
                availableSpace,
                calculatedWorkspaceSpec.startPaddingPx
            )
        endPaddingPx =
            spec.endPadding.getCalculatedValue(availableSpace, calculatedWorkspaceSpec.endPaddingPx)
        gutterPx = spec.gutter.getCalculatedValue(availableSpace, calculatedWorkspaceSpec.gutterPx)
        cellSizePx =
            spec.cellSize.getCalculatedValue(availableSpace, calculatedWorkspaceSpec.cellSizePx)

        updateRemainderSpaces(availableSpace, cells, spec)
    }

    constructor(availableSpace: Int, cells: Int, spec: ResponsiveSpec) {
        this.availableSpace = availableSpace
        this.cells = cells
        this.spec = spec

        // Map if is fixedSize or ofAvailableSpace
        startPaddingPx = spec.startPadding.getCalculatedValue(availableSpace)
        endPaddingPx = spec.endPadding.getCalculatedValue(availableSpace)
        gutterPx = spec.gutter.getCalculatedValue(availableSpace)
        cellSizePx = spec.cellSize.getCalculatedValue(availableSpace)

        updateRemainderSpaces(availableSpace, cells, spec)
    }

    private fun updateRemainderSpaces(availableSpace: Int, cells: Int, spec: ResponsiveSpec) {
        val gutters = cells - 1
        val usedSpace = startPaddingPx + endPaddingPx + (gutterPx * gutters) + (cellSizePx * cells)
        val remainderSpace = availableSpace - usedSpace

        startPaddingPx = spec.startPadding.getRemainderSpaceValue(remainderSpace, startPaddingPx)
        endPaddingPx = spec.endPadding.getRemainderSpaceValue(remainderSpace, endPaddingPx)
        gutterPx = spec.gutter.getRemainderSpaceValue(remainderSpace, gutterPx)
        cellSizePx = spec.cellSize.getRemainderSpaceValue(remainderSpace, cellSizePx)
    }

    override fun hashCode(): Int {
        var result = availableSpace.hashCode()
        result = 31 * result + cells.hashCode()
        result = 31 * result + startPaddingPx.hashCode()
        result = 31 * result + endPaddingPx.hashCode()
        result = 31 * result + gutterPx.hashCode()
        result = 31 * result + cellSizePx.hashCode()
        result = 31 * result + spec.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        return other is CalculatedResponsiveSpec &&
            availableSpace == other.availableSpace &&
            cells == other.cells &&
            startPaddingPx == other.startPaddingPx &&
            endPaddingPx == other.endPaddingPx &&
            gutterPx == other.gutterPx &&
            cellSizePx == other.cellSizePx &&
            spec == other.spec
    }

    override fun toString(): String {
        return "${this::class.simpleName}(" +
            "availableSpace=$availableSpace, cells=$cells, startPaddingPx=$startPaddingPx, " +
            "endPaddingPx=$endPaddingPx, gutterPx=$gutterPx, cellSizePx=$cellSizePx, " +
            "${spec::class.simpleName}.maxAvailableSize=${spec.maxAvailableSize}" +
            ")"
    }
}

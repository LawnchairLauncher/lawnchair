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

import android.content.res.TypedArray
import android.util.Log
import com.android.launcher3.R
import com.android.launcher3.responsive.ResponsiveSpec.Companion.ResponsiveSpecType

/**
 * Interface for responsive grid specs
 *
 * @property maxAvailableSize indicates the breakpoint to use this specification.
 * @property dimensionType indicates whether the paddings and gutters will be applied vertically or
 *   horizontally.
 * @property specType a [ResponsiveSpecType] that indicates the type of the spec.
 */
interface IResponsiveSpec {
    val maxAvailableSize: Int
    val dimensionType: ResponsiveSpec.DimensionType
    val specType: ResponsiveSpecType
}

/**
 * Class for a responsive specification that is used to calculate the paddings, gutter and cell
 * size.
 *
 * @param maxAvailableSize indicates the breakpoint to use this specification.
 * @param dimensionType indicates whether the paddings and gutters will be applied vertically or
 *   horizontally.
 * @param specType a [ResponsiveSpecType] that indicates the type of the spec.
 * @param startPadding padding used at the top or left (right in RTL) in the workspace folder.
 * @param endPadding padding used at the bottom or right (left in RTL) in the workspace folder.
 * @param gutter the space between the cells vertically or horizontally depending on the
 *   [dimensionType].
 * @param cellSize height or width of the cell depending on the [dimensionType].
 */
data class ResponsiveSpec(
    override val maxAvailableSize: Int,
    override val dimensionType: DimensionType,
    override val specType: ResponsiveSpecType,
    val startPadding: SizeSpec,
    val endPadding: SizeSpec,
    val gutter: SizeSpec,
    val cellSize: SizeSpec,
) : IResponsiveSpec {
    init {
        check(isValid()) { "Invalid ResponsiveSpec found. $this" }
    }

    constructor(
        responsiveSpecType: ResponsiveSpecType,
        attrs: TypedArray,
        specs: Map<String, SizeSpec>
    ) : this(
        maxAvailableSize =
            attrs.getDimensionPixelSize(R.styleable.ResponsiveSpec_maxAvailableSize, 0),
        dimensionType =
            DimensionType.entries[
                    attrs.getInt(
                        R.styleable.ResponsiveSpec_dimensionType,
                        DimensionType.HEIGHT.ordinal
                    )],
        specType = responsiveSpecType,
        startPadding = specs.getOrError(SizeSpec.XmlTags.START_PADDING),
        endPadding = specs.getOrError(SizeSpec.XmlTags.END_PADDING),
        gutter = specs.getOrError(SizeSpec.XmlTags.GUTTER),
        cellSize = specs.getOrError(SizeSpec.XmlTags.CELL_SIZE)
    )

    fun isValid(): Boolean {
        if (
            (specType == ResponsiveSpecType.Workspace) &&
                (startPadding.matchWorkspace ||
                    endPadding.matchWorkspace ||
                    gutter.matchWorkspace ||
                    cellSize.matchWorkspace)
        ) {
            logError("Workspace spec provided must not have any match workspace value.")
            return false
        }

        if (maxAvailableSize <= 0) {
            logError("The property maxAvailableSize must be higher than 0.")
            return false
        }

        // All specs need to be individually valid
        if (!allSpecsAreValid()) {
            logError("One or more specs are invalid!")
            return false
        }

        if (!isValidRemainderSpace()) {
            logError("The total Remainder Space used must be equal to 0 or 1.")
            return false
        }

        if (!isValidAvailableSpace()) {
            logError("The total Available Space used must be lower or equal to 100%.")
            return false
        }

        if (!isValidFixedSize()) {
            logError("The total Fixed Size used must be lower or equal to $maxAvailableSize.")
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

    private fun isValidRemainderSpace(): Boolean {
        val remainderSpaceUsed =
            startPadding.ofRemainderSpace +
                endPadding.ofRemainderSpace +
                gutter.ofRemainderSpace +
                cellSize.ofRemainderSpace
        return remainderSpaceUsed == 0f || remainderSpaceUsed == 1f
    }

    private fun isValidAvailableSpace(): Boolean {
        return startPadding.ofAvailableSpace +
            endPadding.ofAvailableSpace +
            gutter.ofAvailableSpace +
            cellSize.ofAvailableSpace < 1f
    }

    private fun isValidFixedSize(): Boolean {
        return startPadding.fixedSize +
            endPadding.fixedSize +
            gutter.fixedSize +
            cellSize.fixedSize <= maxAvailableSize
    }

    private fun logError(message: String) {
        Log.e(LOG_TAG, "$LOG_TAG#isValid - $message - $this")
    }

    enum class DimensionType {
        HEIGHT,
        WIDTH
    }

    companion object {
        private const val LOG_TAG = "ResponsiveSpec"

        enum class ResponsiveSpecType(val xmlTag: String) {
            AllApps("allAppsSpec"),
            Folder("folderSpec"),
            Workspace("workspaceSpec"),
            Hotseat("hotseatSpec"),
            Cell("cellSpec")
        }
    }
}

/**
 * Calculated responsive specs contains the final paddings, gutter and cell size in pixels after
 * they are calculated from the available space, cells and workspace specs.
 */
class CalculatedResponsiveSpec {
    var aspectRatio: Float = Float.NaN
        private set

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
        aspectRatio: Float,
        availableSpace: Int,
        cells: Int,
        spec: ResponsiveSpec,
        calculatedWorkspaceSpec: CalculatedResponsiveSpec
    ) {
        this.aspectRatio = aspectRatio
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

    constructor(aspectRatio: Float, availableSpace: Int, cells: Int, spec: ResponsiveSpec) {
        this.aspectRatio = aspectRatio
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

    fun isResponsiveSpecType(type: ResponsiveSpecType) = spec.specType == type

    private fun updateRemainderSpaces(availableSpace: Int, cells: Int, spec: ResponsiveSpec) {
        val gutters = cells - 1
        val usedSpace = startPaddingPx + endPaddingPx + (gutterPx * gutters) + (cellSizePx * cells)
        val remainderSpace = availableSpace - usedSpace

        startPaddingPx = spec.startPadding.getRemainderSpaceValue(remainderSpace, startPaddingPx)
        endPaddingPx = spec.endPadding.getRemainderSpaceValue(remainderSpace, endPaddingPx)
        gutterPx = spec.gutter.getRemainderSpaceValue(remainderSpace, gutterPx, gutters)
        cellSizePx = spec.cellSize.getRemainderSpaceValue(remainderSpace, cellSizePx, cells)
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
        return "Calculated${spec.specType}Spec(" +
            "availableSpace=$availableSpace, cells=$cells, startPaddingPx=$startPaddingPx, " +
            "endPaddingPx=$endPaddingPx, gutterPx=$gutterPx, cellSizePx=$cellSizePx, " +
            "aspectRatio=${aspectRatio}, " +
            "${spec.specType}Spec.maxAvailableSize=${spec.maxAvailableSize}" +
            ")"
    }
}

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
import com.android.launcher3.responsive.ResponsiveSpec.DimensionType
import com.android.launcher3.util.ResourceHelper

class ResponsiveCellSpecsProvider(groupOfSpecs: List<ResponsiveSpecGroup<CellSpec>>) {
    private val groupOfSpecs: List<ResponsiveSpecGroup<CellSpec>>

    init {
        this.groupOfSpecs =
            groupOfSpecs
                .onEach { group ->
                    check(group.widthSpecs.isEmpty() && group.heightSpecs.isNotEmpty()) {
                        "$LOG_TAG is invalid, only heightSpecs are allowed - " +
                            "width list size = ${group.widthSpecs.size}; " +
                            "height list size = ${group.heightSpecs.size}."
                    }
                }
                .sortedBy { it.aspectRatio }
    }

    fun getSpecsByAspectRatio(aspectRatio: Float): ResponsiveSpecGroup<CellSpec> {
        check(aspectRatio > 0f) { "Invalid aspect ratio! The value should be bigger than 0." }

        val specsGroup = groupOfSpecs.firstOrNull { aspectRatio <= it.aspectRatio }
        check(specsGroup != null) { "No available spec with aspectRatio within $aspectRatio." }

        return specsGroup
    }

    fun getCalculatedSpec(aspectRatio: Float, availableHeightSpace: Int): CalculatedCellSpec {
        val specsGroup = getSpecsByAspectRatio(aspectRatio)
        val spec = specsGroup.getSpec(DimensionType.HEIGHT, availableHeightSpace)
        return CalculatedCellSpec(availableHeightSpace, spec)
    }

    fun getCalculatedSpec(
        aspectRatio: Float,
        availableHeightSpace: Int,
        workspaceCellSpec: CalculatedCellSpec
    ): CalculatedCellSpec {
        val specsGroup = getSpecsByAspectRatio(aspectRatio)
        val spec = specsGroup.getSpec(DimensionType.HEIGHT, availableHeightSpace)
        return CalculatedCellSpec(availableHeightSpace, spec, workspaceCellSpec)
    }

    companion object {
        private const val LOG_TAG = "ResponsiveCellSpecsProvider"
        @JvmStatic
        fun create(resourceHelper: ResourceHelper): ResponsiveCellSpecsProvider {
            val parser = ResponsiveSpecsParser(resourceHelper)
            val specs = parser.parseXML(ResponsiveSpecType.Cell, ::CellSpec)
            return ResponsiveCellSpecsProvider(specs)
        }
    }
}

data class CellSpec(
    override val maxAvailableSize: Int,
    override val dimensionType: DimensionType,
    override val specType: ResponsiveSpecType,
    val iconSize: SizeSpec,
    val iconTextSize: SizeSpec,
    val iconDrawablePadding: SizeSpec
) : IResponsiveSpec {
    init {
        check(isValid()) { "Invalid CellSpec found." }
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
        iconSize = specs.getOrError(SizeSpec.XmlTags.ICON_SIZE),
        iconTextSize = specs.getOrError(SizeSpec.XmlTags.ICON_TEXT_SIZE),
        iconDrawablePadding = specs.getOrError(SizeSpec.XmlTags.ICON_DRAWABLE_PADDING)
    )

    fun isValid(): Boolean {
        if (maxAvailableSize <= 0) {
            logError("The property maxAvailableSize must be higher than 0.")
            return false
        }

        // All specs need to be individually valid
        if (!allSpecsAreValid()) {
            logError("Specs must be either Fixed Size or Match Workspace!")
            return false
        }

        if (!isValidFixedSize()) {
            logError("The total Fixed Size used must be lower or equal to $maxAvailableSize.")
            return false
        }

        return true
    }

    private fun isValidFixedSize(): Boolean {
        val totalSize = iconSize.fixedSize + iconTextSize.fixedSize + iconDrawablePadding.fixedSize
        return totalSize <= maxAvailableSize
    }

    private fun allSpecsAreValid(): Boolean {
        return (iconSize.fixedSize > 0f || iconSize.matchWorkspace) &&
            (iconTextSize.fixedSize >= 0f || iconTextSize.matchWorkspace) &&
            (iconDrawablePadding.fixedSize >= 0f || iconDrawablePadding.matchWorkspace)
    }

    private fun logError(message: String) {
        Log.e(LOG_TAG, "$LOG_TAG#isValid - $message - $this")
    }

    companion object {
        const val LOG_TAG = "CellSpec"
    }
}

data class CalculatedCellSpec(
    val availableSpace: Int,
    val spec: CellSpec,
    val iconSize: Int,
    val iconTextSize: Int,
    val iconDrawablePadding: Int
) {
    constructor(
        availableSpace: Int,
        spec: CellSpec
    ) : this(
        availableSpace = availableSpace,
        spec = spec,
        iconSize = spec.iconSize.getCalculatedValue(availableSpace),
        iconTextSize = spec.iconTextSize.getCalculatedValue(availableSpace),
        iconDrawablePadding = spec.iconDrawablePadding.getCalculatedValue(availableSpace)
    )

    constructor(
        availableSpace: Int,
        spec: CellSpec,
        workspaceCellSpec: CalculatedCellSpec
    ) : this(
        availableSpace = availableSpace,
        spec = spec,
        iconSize = getCalculatedValue(availableSpace, spec.iconSize, workspaceCellSpec.iconSize),
        iconTextSize =
            getCalculatedValue(availableSpace, spec.iconTextSize, workspaceCellSpec.iconTextSize),
        iconDrawablePadding =
            getCalculatedValue(
                availableSpace,
                spec.iconDrawablePadding,
                workspaceCellSpec.iconDrawablePadding
            )
    )

    companion object {
        private const val LOG_TAG = "CalculatedCellSpec"
        private fun getCalculatedValue(
            availableSpace: Int,
            spec: SizeSpec,
            workspaceValue: Int
        ): Int =
            if (spec.matchWorkspace) workspaceValue else spec.getCalculatedValue(availableSpace)
    }

    override fun toString(): String {
        return "$LOG_TAG(" +
            "availableSpace=$availableSpace, iconSize=$iconSize, " +
            "iconTextSize=$iconTextSize, iconDrawablePadding=$iconDrawablePadding, " +
            "${CellSpec.LOG_TAG}.maxAvailableSize=${spec.maxAvailableSize}" +
            ")"
    }
}

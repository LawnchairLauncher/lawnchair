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
import com.android.launcher3.R
import com.android.launcher3.responsive.ResponsiveSpec.SpecType
import com.android.launcher3.util.ResourceHelper

class FolderSpecs(widthSpecs: List<FolderSpec>, heightSpecs: List<FolderSpec>) :
    ResponsiveSpecs<FolderSpec>(widthSpecs, heightSpecs) {

    fun getCalculatedWidthSpec(
        columns: Int,
        availableWidth: Int,
        calculatedWorkspaceSpec: CalculatedWorkspaceSpec
    ): CalculatedFolderSpec {
        check(calculatedWorkspaceSpec.spec.specType == SpecType.WIDTH) {
            "Invalid specType for CalculatedWorkspaceSpec. " +
                "Expected: ${SpecType.WIDTH} - " +
                "Found: ${calculatedWorkspaceSpec.spec.specType}}"
        }

        val spec = getWidthSpec(availableWidth)
        return CalculatedFolderSpec(availableWidth, columns, spec, calculatedWorkspaceSpec)
    }

    fun getCalculatedHeightSpec(
        rows: Int,
        availableHeight: Int,
        calculatedWorkspaceSpec: CalculatedWorkspaceSpec
    ): CalculatedFolderSpec {
        check(calculatedWorkspaceSpec.spec.specType == SpecType.HEIGHT) {
            "Invalid specType for CalculatedWorkspaceSpec. " +
                "Expected: ${SpecType.HEIGHT} - " +
                "Found: ${calculatedWorkspaceSpec.spec.specType}}"
        }

        val spec = getHeightSpec(availableHeight)
        return CalculatedFolderSpec(availableHeight, rows, spec, calculatedWorkspaceSpec)
    }

    companion object {

        private const val XML_FOLDER_SPEC = "folderSpec"

        @JvmStatic
        fun create(resourceHelper: ResourceHelper): FolderSpecs {
            val parser = ResponsiveSpecsParser(resourceHelper)
            val specs = parser.parseXML(XML_FOLDER_SPEC, ::FolderSpec)
            val (widthSpecs, heightSpecs) = specs.partition { it.specType == SpecType.WIDTH }
            return FolderSpecs(widthSpecs, heightSpecs)
        }
    }
}

data class FolderSpec(
    override val maxAvailableSize: Int,
    override val specType: SpecType,
    override val startPadding: SizeSpec,
    override val endPadding: SizeSpec,
    override val gutter: SizeSpec,
    override val cellSize: SizeSpec
) : ResponsiveSpec(maxAvailableSize, specType, startPadding, endPadding, gutter, cellSize) {

    init {
        check(isValid()) { "Invalid FolderSpec found." }
    }

    constructor(
        attrs: TypedArray,
        specs: Map<String, SizeSpec>
    ) : this(
        maxAvailableSize =
            attrs.getDimensionPixelSize(R.styleable.ResponsiveSpec_maxAvailableSize, 0),
        specType =
            SpecType.values()[
                    attrs.getInt(R.styleable.ResponsiveSpec_specType, SpecType.HEIGHT.ordinal)],
        startPadding = specs.getOrError(SizeSpec.XmlTags.START_PADDING),
        endPadding = specs.getOrError(SizeSpec.XmlTags.END_PADDING),
        gutter = specs.getOrError(SizeSpec.XmlTags.GUTTER),
        cellSize = specs.getOrError(SizeSpec.XmlTags.CELL_SIZE)
    )
}

class CalculatedFolderSpec(
    availableSpace: Int,
    cells: Int,
    spec: FolderSpec,
    calculatedWorkspaceSpec: CalculatedWorkspaceSpec
) : CalculatedResponsiveSpec(availableSpace, cells, spec, calculatedWorkspaceSpec)

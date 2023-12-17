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
import com.android.launcher3.responsive.ResponsiveSpec.SpecType
import com.android.launcher3.util.ResourceHelper

private const val TAG = "WorkspaceSpecs"

class WorkspaceSpecs(widthSpecs: List<WorkspaceSpec>, heightSpecs: List<WorkspaceSpec>) :
    ResponsiveSpecs<WorkspaceSpec>(widthSpecs, heightSpecs) {

    fun getCalculatedWidthSpec(columns: Int, availableWidth: Int): CalculatedWorkspaceSpec {
        val spec = getWidthSpec(availableWidth)
        return CalculatedWorkspaceSpec(availableWidth, columns, spec)
    }

    fun getCalculatedHeightSpec(rows: Int, availableHeight: Int): CalculatedWorkspaceSpec {
        val spec = getHeightSpec(availableHeight)
        return CalculatedWorkspaceSpec(availableHeight, rows, spec)
    }

    companion object {
        private const val XML_WORKSPACE_SPEC = "workspaceSpec"

        @JvmStatic
        fun create(resourceHelper: ResourceHelper): WorkspaceSpecs {
            val parser = ResponsiveSpecsParser(resourceHelper)
            val specs = parser.parseXML(XML_WORKSPACE_SPEC, ::WorkspaceSpec)
            val (widthSpecs, heightSpecs) = specs.partition { it.specType == SpecType.WIDTH }
            return WorkspaceSpecs(widthSpecs, heightSpecs)
        }
    }
}

data class WorkspaceSpec(
    override val maxAvailableSize: Int,
    override val specType: SpecType,
    override val startPadding: SizeSpec,
    override val endPadding: SizeSpec,
    override val gutter: SizeSpec,
    override val cellSize: SizeSpec
) : ResponsiveSpec(maxAvailableSize, specType, startPadding, endPadding, gutter, cellSize) {

    init {
        check(isValid()) { "Invalid WorkspaceSpec found." }
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

    override fun isValid(): Boolean {
        // Workspace spec should not match workspace
        if (
            startPadding.matchWorkspace ||
                endPadding.matchWorkspace ||
                gutter.matchWorkspace ||
                cellSize.matchWorkspace
        ) {
            Log.e(TAG, "WorkspaceSpec#isValid - workspace shouldn't contain matchWorkspace!")
            return false
        }

        return super.isValid()
    }
}

class CalculatedWorkspaceSpec(availableSpace: Int, cells: Int, spec: WorkspaceSpec) :
    CalculatedResponsiveSpec(availableSpace, cells, spec)

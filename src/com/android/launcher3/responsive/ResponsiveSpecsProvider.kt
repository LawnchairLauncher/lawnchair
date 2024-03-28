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

import com.android.launcher3.responsive.ResponsiveSpec.Companion.ResponsiveSpecType
import com.android.launcher3.responsive.ResponsiveSpec.DimensionType
import com.android.launcher3.util.ResourceHelper

/**
 * A class to provide responsive grid specs for workspace, folder and all apps.
 *
 * This class is responsible for provide width and height [CalculatedResponsiveSpec] to be used for
 * the correct placement of the workspace, all apps and folders.
 *
 * @param type A [ResponsiveSpecType] to indicates the type of the spec.
 * @param groupOfSpecs Groups of responsive specifications
 */
class ResponsiveSpecsProvider(
    val type: ResponsiveSpecType,
    groupOfSpecs: List<ResponsiveSpecGroup<ResponsiveSpec>>
) {
    private val groupOfSpecs: List<ResponsiveSpecGroup<ResponsiveSpec>>

    init {
        this.groupOfSpecs =
            groupOfSpecs
                .onEach { group ->
                    check(group.widthSpecs.isNotEmpty() && group.heightSpecs.isNotEmpty()) {
                        "${this::class.simpleName} is incomplete - " +
                            "width list size = ${group.widthSpecs.size}; " +
                            "height list size = ${group.heightSpecs.size}."
                    }
                }
                .sortedBy { it.aspectRatio }
    }

    fun getSpecsByAspectRatio(aspectRatio: Float): ResponsiveSpecGroup<ResponsiveSpec> {
        check(aspectRatio > 0f) { "Invalid aspect ratio! The value should be bigger than 0." }

        val specsGroup = groupOfSpecs.firstOrNull { aspectRatio <= it.aspectRatio }
        checkNotNull(specsGroup) { "No available spec with aspectRatio within $aspectRatio." }

        return specsGroup
    }

    /**
     * Retrieves a responsive grid specification that matches the number of [numCells],
     * * [availableSpace] and [aspectRatio].
     *
     * @param aspectRatio the device width divided by device height (aspect ratio) to filter the
     *   specifications
     * @param dimensionType the grid axis of the spec width is x axis, height is y axis.
     * @param numCells number of rows/columns in the grid
     * @param availableSpace available width to filter the specifications
     * @return A [CalculatedResponsiveSpec] that matches the parameters provided.
     */
    fun getCalculatedSpec(
        aspectRatio: Float,
        dimensionType: DimensionType,
        numCells: Int,
        availableSpace: Int,
    ): CalculatedResponsiveSpec {
        val specsGroup = getSpecsByAspectRatio(aspectRatio)
        val spec = specsGroup.getSpec(dimensionType, availableSpace)
        return CalculatedResponsiveSpec(aspectRatio, availableSpace, numCells, spec)
    }

    /**
     * Retrieves a responsive grid specification that matches the number of [numCells],
     * * [availableSpace] and [aspectRatio]. This function uses a [CalculatedResponsiveSpec] to
     *   match workspace when its true.
     *
     * @param aspectRatio the device width divided by device height (aspect ratio) to filter the
     *   specifications
     * @param dimensionType the grid axis of the spec width is x axis, height is y axis.
     * @param numCells number of rows/columns in the grid
     * @param availableSpace available width to filter the specifications
     * @param calculatedWorkspaceSpec the calculated workspace specification to use its values as
     *   base when matchWorkspace is true.
     * @return A [CalculatedResponsiveSpec] that matches the parameters provided.
     */
    fun getCalculatedSpec(
        aspectRatio: Float,
        dimensionType: DimensionType,
        numCells: Int,
        availableSpace: Int,
        calculatedWorkspaceSpec: CalculatedResponsiveSpec
    ): CalculatedResponsiveSpec {
        check(calculatedWorkspaceSpec.spec.dimensionType == dimensionType) {
            "Invalid specType for CalculatedWorkspaceSpec. " +
                "Expected: $dimensionType - " +
                "Found: ${calculatedWorkspaceSpec.spec.dimensionType}}"
        }

        check(calculatedWorkspaceSpec.isResponsiveSpecType(ResponsiveSpecType.Workspace)) {
            "Invalid specType for CalculatedWorkspaceSpec. " +
                "Expected: ${ResponsiveSpecType.Workspace} - " +
                "Found: ${calculatedWorkspaceSpec.spec.specType}}"
        }

        val specsGroup = getSpecsByAspectRatio(aspectRatio)
        val spec = specsGroup.getSpec(dimensionType, availableSpace)
        return CalculatedResponsiveSpec(
            aspectRatio,
            availableSpace,
            numCells,
            spec,
            calculatedWorkspaceSpec
        )
    }

    companion object {
        @JvmStatic
        fun create(
            resourceHelper: ResourceHelper,
            type: ResponsiveSpecType
        ): ResponsiveSpecsProvider {
            val parser = ResponsiveSpecsParser(resourceHelper)
            val specs = parser.parseXML(type, ::ResponsiveSpec)
            return ResponsiveSpecsProvider(type, specs)
        }
    }
}

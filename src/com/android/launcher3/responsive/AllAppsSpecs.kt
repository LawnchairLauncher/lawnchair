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

import android.content.res.XmlResourceParser
import android.util.AttributeSet
import android.util.Log
import android.util.Xml
import com.android.launcher3.R
import com.android.launcher3.util.ResourceHelper
import com.android.launcher3.workspace.CalculatedWorkspaceSpec
import java.io.IOException
import kotlin.math.roundToInt
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

private const val LOG_TAG = "AllAppsSpecs"

class AllAppsSpecs(resourceHelper: ResourceHelper) {
    object XmlTags {
        const val ALL_APPS_SPECS = "allAppsSpecs"

        const val ALL_APPS_SPEC = "allAppsSpec"
        const val START_PADDING = "startPadding"
        const val END_PADDING = "endPadding"
        const val GUTTER = "gutter"
        const val CELL_SIZE = "cellSize"
    }

    val allAppsHeightSpecList = mutableListOf<AllAppsSpec>()
    val allAppsWidthSpecList = mutableListOf<AllAppsSpec>()

    // TODO(b/286538013) Remove this init after a more generic or reusable parser is created
    init {
        var parser: XmlResourceParser? = null
        try {
            parser = resourceHelper.getXml()
            val depth = parser.depth
            var type: Int
            while (
                (parser.next().also { type = it } != XmlPullParser.END_TAG ||
                    parser.depth > depth) && type != XmlPullParser.END_DOCUMENT
            ) {
                if (type == XmlPullParser.START_TAG && XmlTags.ALL_APPS_SPECS == parser.name) {
                    val displayDepth = parser.depth
                    while (
                        (parser.next().also { type = it } != XmlPullParser.END_TAG ||
                            parser.depth > displayDepth) && type != XmlPullParser.END_DOCUMENT
                    ) {
                        if (
                            type == XmlPullParser.START_TAG && XmlTags.ALL_APPS_SPEC == parser.name
                        ) {
                            val attrs =
                                resourceHelper.obtainStyledAttributes(
                                    Xml.asAttributeSet(parser),
                                    R.styleable.AllAppsSpec
                                )
                            val maxAvailableSize =
                                attrs.getDimensionPixelSize(
                                    R.styleable.AllAppsSpec_maxAvailableSize,
                                    0
                                )
                            val specType =
                                AllAppsSpec.SpecType.values()[
                                        attrs.getInt(
                                            R.styleable.AllAppsSpec_specType,
                                            AllAppsSpec.SpecType.HEIGHT.ordinal
                                        )]
                            attrs.recycle()

                            var startPadding: SizeSpec? = null
                            var endPadding: SizeSpec? = null
                            var gutter: SizeSpec? = null
                            var cellSize: SizeSpec? = null

                            val limitDepth = parser.depth
                            while (
                                (parser.next().also { type = it } != XmlPullParser.END_TAG ||
                                    parser.depth > limitDepth) && type != XmlPullParser.END_DOCUMENT
                            ) {
                                val attr: AttributeSet = Xml.asAttributeSet(parser)
                                if (type == XmlPullParser.START_TAG) {
                                    when (parser.name) {
                                        XmlTags.START_PADDING -> {
                                            startPadding = SizeSpec.create(resourceHelper, attr)
                                        }
                                        XmlTags.END_PADDING -> {
                                            endPadding = SizeSpec.create(resourceHelper, attr)
                                        }
                                        XmlTags.GUTTER -> {
                                            gutter = SizeSpec.create(resourceHelper, attr)
                                        }
                                        XmlTags.CELL_SIZE -> {
                                            cellSize = SizeSpec.create(resourceHelper, attr)
                                        }
                                    }
                                }
                            }

                            if (
                                startPadding == null ||
                                    endPadding == null ||
                                    gutter == null ||
                                    cellSize == null
                            ) {
                                throw IllegalStateException(
                                    "All attributes in AllAppsSpec must be defined"
                                )
                            }

                            val allAppsSpec =
                                AllAppsSpec(
                                    maxAvailableSize,
                                    specType,
                                    startPadding,
                                    endPadding,
                                    gutter,
                                    cellSize
                                )
                            if (allAppsSpec.isValid()) {
                                if (allAppsSpec.specType == AllAppsSpec.SpecType.HEIGHT)
                                    allAppsHeightSpecList.add(allAppsSpec)
                                else allAppsWidthSpecList.add(allAppsSpec)
                            } else {
                                throw IllegalStateException("Invalid AllAppsSpec found.")
                            }
                        }
                    }

                    if (allAppsWidthSpecList.isEmpty() || allAppsHeightSpecList.isEmpty()) {
                        throw IllegalStateException(
                            "AllAppsSpecs is incomplete - " +
                                "height list size = ${allAppsHeightSpecList.size}; " +
                                "width list size = ${allAppsWidthSpecList.size}."
                        )
                    }
                }
            }
        } catch (e: Exception) {
            when (e) {
                is IOException,
                is XmlPullParserException -> {
                    throw RuntimeException("Failure parsing all apps specs file.", e)
                }
                else -> throw e
            }
        } finally {
            parser?.close()
        }
    }

    /**
     * Returns the CalculatedAllAppsSpec for width, based on the available width, the AllAppsSpecs
     * and the CalculatedWorkspaceSpec.
     */
    fun getCalculatedWidthSpec(
        columns: Int,
        availableWidth: Int,
        calculatedWorkspaceSpec: CalculatedWorkspaceSpec
    ): CalculatedAllAppsSpec {
        val widthSpec = allAppsWidthSpecList.first { availableWidth <= it.maxAvailableSize }

        return CalculatedAllAppsSpec(availableWidth, columns, widthSpec, calculatedWorkspaceSpec)
    }

    /**
     * Returns the CalculatedAllAppsSpec for height, based on the available height, the AllAppsSpecs
     * and the CalculatedWorkspaceSpec.
     */
    fun getCalculatedHeightSpec(
        rows: Int,
        availableHeight: Int,
        calculatedWorkspaceSpec: CalculatedWorkspaceSpec
    ): CalculatedAllAppsSpec {
        val heightSpec = allAppsHeightSpecList.first { availableHeight <= it.maxAvailableSize }

        return CalculatedAllAppsSpec(availableHeight, rows, heightSpec, calculatedWorkspaceSpec)
    }
}

class CalculatedAllAppsSpec(
    val availableSpace: Int,
    val cells: Int,
    private val allAppsSpec: AllAppsSpec,
    calculatedWorkspaceSpec: CalculatedWorkspaceSpec
) {
    var startPaddingPx: Int = 0
        private set
    var endPaddingPx: Int = 0
        private set
    var gutterPx: Int = 0
        private set
    var cellSizePx: Int = 0
        private set
    init {
        // Copy values from workspace
        if (allAppsSpec.startPadding.matchWorkspace)
            startPaddingPx = calculatedWorkspaceSpec.startPaddingPx
        if (allAppsSpec.endPadding.matchWorkspace)
            endPaddingPx = calculatedWorkspaceSpec.endPaddingPx
        if (allAppsSpec.gutter.matchWorkspace) gutterPx = calculatedWorkspaceSpec.gutterPx
        if (allAppsSpec.cellSize.matchWorkspace) cellSizePx = calculatedWorkspaceSpec.cellSizePx

        // Calculate all fixed size first
        if (allAppsSpec.startPadding.fixedSize > 0)
            startPaddingPx = allAppsSpec.startPadding.fixedSize.roundToInt()
        if (allAppsSpec.endPadding.fixedSize > 0)
            endPaddingPx = allAppsSpec.endPadding.fixedSize.roundToInt()
        if (allAppsSpec.gutter.fixedSize > 0) gutterPx = allAppsSpec.gutter.fixedSize.roundToInt()
        if (allAppsSpec.cellSize.fixedSize > 0)
            cellSizePx = allAppsSpec.cellSize.fixedSize.roundToInt()

        // Calculate all available space next
        if (allAppsSpec.startPadding.ofAvailableSpace > 0)
            startPaddingPx =
                (allAppsSpec.startPadding.ofAvailableSpace * availableSpace).roundToInt()
        if (allAppsSpec.endPadding.ofAvailableSpace > 0)
            endPaddingPx = (allAppsSpec.endPadding.ofAvailableSpace * availableSpace).roundToInt()
        if (allAppsSpec.gutter.ofAvailableSpace > 0)
            gutterPx = (allAppsSpec.gutter.ofAvailableSpace * availableSpace).roundToInt()
        if (allAppsSpec.cellSize.ofAvailableSpace > 0)
            cellSizePx = (allAppsSpec.cellSize.ofAvailableSpace * availableSpace).roundToInt()

        // Calculate remainder space last
        val gutters = cells - 1
        val usedSpace = startPaddingPx + endPaddingPx + (gutterPx * gutters) + (cellSizePx * cells)
        val remainderSpace = availableSpace - usedSpace
        if (allAppsSpec.startPadding.ofRemainderSpace > 0)
            startPaddingPx =
                (allAppsSpec.startPadding.ofRemainderSpace * remainderSpace).roundToInt()
        if (allAppsSpec.endPadding.ofRemainderSpace > 0)
            endPaddingPx = (allAppsSpec.endPadding.ofRemainderSpace * remainderSpace).roundToInt()
        if (allAppsSpec.gutter.ofRemainderSpace > 0)
            gutterPx = (allAppsSpec.gutter.ofRemainderSpace * remainderSpace).roundToInt()
        if (allAppsSpec.cellSize.ofRemainderSpace > 0)
            cellSizePx = (allAppsSpec.cellSize.ofRemainderSpace * remainderSpace).roundToInt()
    }

    override fun toString(): String {
        return "CalculatedAllAppsSpec(availableSpace=$availableSpace, " +
            "cells=$cells, startPaddingPx=$startPaddingPx, endPaddingPx=$endPaddingPx, " +
            "gutterPx=$gutterPx, cellSizePx=$cellSizePx, " +
            "AllAppsSpec.maxAvailableSize=${allAppsSpec.maxAvailableSize})"
    }
}

data class AllAppsSpec(
    val maxAvailableSize: Int,
    val specType: SpecType,
    val startPadding: SizeSpec,
    val endPadding: SizeSpec,
    val gutter: SizeSpec,
    val cellSize: SizeSpec
) {

    enum class SpecType {
        HEIGHT,
        WIDTH
    }

    fun isValid(): Boolean {
        if (maxAvailableSize <= 0) {
            Log.e(LOG_TAG, "AllAppsSpec#isValid - maxAvailableSize <= 0")
            return false
        }

        // All specs need to be individually valid
        if (!allSpecsAreValid()) {
            Log.e(LOG_TAG, "AllAppsSpec#isValid - !allSpecsAreValid()")
            return false
        }

        return true
    }

    private fun allSpecsAreValid(): Boolean =
        startPadding.isValid() && endPadding.isValid() && gutter.isValid() && cellSize.isValid()
}

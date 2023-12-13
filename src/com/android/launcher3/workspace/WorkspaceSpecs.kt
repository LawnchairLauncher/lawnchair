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

package com.android.launcher3.workspace

import android.content.res.TypedArray
import android.content.res.XmlResourceParser
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.util.Xml
import com.android.launcher3.R
import com.android.launcher3.util.ResourceHelper
import java.io.IOException
import kotlin.math.roundToInt
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

private const val TAG = "WorkspaceSpecs"

class WorkspaceSpecs(resourceHelper: ResourceHelper) {
    object XmlTags {
        const val WORKSPACE_SPECS = "workspaceSpecs"

        const val WORKSPACE_SPEC = "workspaceSpec"
        const val START_PADDING = "startPadding"
        const val END_PADDING = "endPadding"
        const val GUTTER = "gutter"
        const val CELL_SIZE = "cellSize"
    }

    val workspaceHeightSpecList = mutableListOf<WorkspaceSpec>()
    val workspaceWidthSpecList = mutableListOf<WorkspaceSpec>()

    init {
        try {
            val parser: XmlResourceParser = resourceHelper.getXml()
            val depth = parser.depth
            var type: Int
            while (
                (parser.next().also { type = it } != XmlPullParser.END_TAG ||
                    parser.depth > depth) && type != XmlPullParser.END_DOCUMENT
            ) {
                if (type == XmlPullParser.START_TAG && XmlTags.WORKSPACE_SPECS == parser.name) {
                    val displayDepth = parser.depth
                    while (
                        (parser.next().also { type = it } != XmlPullParser.END_TAG ||
                            parser.depth > displayDepth) && type != XmlPullParser.END_DOCUMENT
                    ) {
                        if (
                            type == XmlPullParser.START_TAG && XmlTags.WORKSPACE_SPEC == parser.name
                        ) {
                            val attrs =
                                resourceHelper.obtainStyledAttributes(
                                    Xml.asAttributeSet(parser),
                                    R.styleable.WorkspaceSpec
                                )
                            val maxAvailableSize =
                                attrs.getDimensionPixelSize(
                                    R.styleable.WorkspaceSpec_maxAvailableSize,
                                    0
                                )
                            val specType =
                                WorkspaceSpec.SpecType.values()[
                                        attrs.getInt(
                                            R.styleable.WorkspaceSpec_specType,
                                            WorkspaceSpec.SpecType.HEIGHT.ordinal
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
                                            startPadding = SizeSpec(resourceHelper, attr)
                                        }
                                        XmlTags.END_PADDING -> {
                                            endPadding = SizeSpec(resourceHelper, attr)
                                        }
                                        XmlTags.GUTTER -> {
                                            gutter = SizeSpec(resourceHelper, attr)
                                        }
                                        XmlTags.CELL_SIZE -> {
                                            cellSize = SizeSpec(resourceHelper, attr)
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
                                    "All attributes in workspaceSpec must be defined"
                                )
                            }

                            val workspaceSpec =
                                WorkspaceSpec(
                                    maxAvailableSize,
                                    specType,
                                    startPadding,
                                    endPadding,
                                    gutter,
                                    cellSize
                                )
                            if (workspaceSpec.isValid()) {
                                if (workspaceSpec.specType == WorkspaceSpec.SpecType.HEIGHT)
                                    workspaceHeightSpecList.add(workspaceSpec)
                                else workspaceWidthSpecList.add(workspaceSpec)
                            } else {
                                throw IllegalStateException("Invalid workspaceSpec found.")
                            }
                        }
                    }

                    if (workspaceWidthSpecList.isEmpty() || workspaceHeightSpecList.isEmpty()) {
                        throw IllegalStateException(
                            "WorkspaceSpecs is incomplete - " +
                                "height list size = ${workspaceHeightSpecList.size}; " +
                                "width list size = ${workspaceWidthSpecList.size}."
                        )
                    }
                }
            }
            parser.close()
        } catch (e: Exception) {
            when (e) {
                is IOException,
                is XmlPullParserException -> {
                    throw RuntimeException("Failure parsing workspaces specs file.", e)
                }
                else -> throw e
            }
        }
    }

    /**
     * Returns the CalculatedWorkspaceSpec for width, based on the available width and the
     * WorkspaceSpecs.
     */
    fun getCalculatedWidthSpec(columns: Int, availableWidth: Int): CalculatedWorkspaceSpec {
        val widthSpec = workspaceWidthSpecList.first { availableWidth <= it.maxAvailableSize }

        return CalculatedWorkspaceSpec(availableWidth, columns, widthSpec)
    }

    /**
     * Returns the CalculatedWorkspaceSpec for height, based on the available height and the
     * WorkspaceSpecs.
     */
    fun getCalculatedHeightSpec(rows: Int, availableHeight: Int): CalculatedWorkspaceSpec {
        val heightSpec = workspaceHeightSpecList.first { availableHeight <= it.maxAvailableSize }

        return CalculatedWorkspaceSpec(availableHeight, rows, heightSpec)
    }
}

class CalculatedWorkspaceSpec(
    val availableSpace: Int,
    val cells: Int,
    val workspaceSpec: WorkspaceSpec
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
        // Calculate all fixed size first
        if (workspaceSpec.startPadding.fixedSize > 0)
            startPaddingPx = workspaceSpec.startPadding.fixedSize.roundToInt()
        if (workspaceSpec.endPadding.fixedSize > 0)
            endPaddingPx = workspaceSpec.endPadding.fixedSize.roundToInt()
        if (workspaceSpec.gutter.fixedSize > 0)
            gutterPx = workspaceSpec.gutter.fixedSize.roundToInt()
        if (workspaceSpec.cellSize.fixedSize > 0)
            cellSizePx = workspaceSpec.cellSize.fixedSize.roundToInt()

        // Calculate all available space next
        if (workspaceSpec.startPadding.ofAvailableSpace > 0)
            startPaddingPx =
                (workspaceSpec.startPadding.ofAvailableSpace * availableSpace).roundToInt()
        if (workspaceSpec.endPadding.ofAvailableSpace > 0)
            endPaddingPx = (workspaceSpec.endPadding.ofAvailableSpace * availableSpace).roundToInt()
        if (workspaceSpec.gutter.ofAvailableSpace > 0)
            gutterPx = (workspaceSpec.gutter.ofAvailableSpace * availableSpace).roundToInt()
        if (workspaceSpec.cellSize.ofAvailableSpace > 0)
            cellSizePx = (workspaceSpec.cellSize.ofAvailableSpace * availableSpace).roundToInt()

        // Calculate remainder space last
        val gutters = cells - 1
        val usedSpace = startPaddingPx + endPaddingPx + (gutterPx * gutters) + (cellSizePx * cells)
        val remainderSpace = availableSpace - usedSpace
        if (workspaceSpec.startPadding.ofRemainderSpace > 0)
            startPaddingPx =
                (workspaceSpec.startPadding.ofRemainderSpace * remainderSpace).roundToInt()
        if (workspaceSpec.endPadding.ofRemainderSpace > 0)
            endPaddingPx = (workspaceSpec.endPadding.ofRemainderSpace * remainderSpace).roundToInt()
        if (workspaceSpec.gutter.ofRemainderSpace > 0)
            gutterPx = (workspaceSpec.gutter.ofRemainderSpace * remainderSpace).roundToInt()
        if (workspaceSpec.cellSize.ofRemainderSpace > 0)
            cellSizePx = (workspaceSpec.cellSize.ofRemainderSpace * remainderSpace).roundToInt()
    }
}

data class WorkspaceSpec(
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
            Log.e(TAG, "WorkspaceSpec#isValid - maxAvailableSize <= 0")
            return false
        }

        // All specs need to be individually valid
        if (!allSpecsAreValid()) {
            Log.e(TAG, "WorkspaceSpec#isValid - !allSpecsAreValid()")
            return false
        }

        return true
    }

    private fun allSpecsAreValid(): Boolean =
        startPadding.isValid() && endPadding.isValid() && gutter.isValid() && cellSize.isValid()
}

class SizeSpec(resourceHelper: ResourceHelper, attrs: AttributeSet) {
    val fixedSize: Float
    val ofAvailableSpace: Float
    val ofRemainderSpace: Float

    init {
        val styledAttrs = resourceHelper.obtainStyledAttributes(attrs, R.styleable.SpecSize)

        fixedSize = getValue(styledAttrs, R.styleable.SpecSize_fixedSize)
        ofAvailableSpace = getValue(styledAttrs, R.styleable.SpecSize_ofAvailableSpace)
        ofRemainderSpace = getValue(styledAttrs, R.styleable.SpecSize_ofRemainderSpace)

        styledAttrs.recycle()
    }

    private fun getValue(a: TypedArray, index: Int): Float {
        if (a.getType(index) == TypedValue.TYPE_DIMENSION) {
            return a.getDimensionPixelSize(index, 0).toFloat()
        } else if (a.getType(index) == TypedValue.TYPE_FLOAT) {
            return a.getFloat(index, 0f)
        }
        return 0f
    }

    fun isValid(): Boolean {
        // All attributes are empty
        if (fixedSize < 0f && ofAvailableSpace <= 0f && ofRemainderSpace <= 0f) {
            Log.e(TAG, "SizeSpec#isValid - all attributes are empty")
            return false
        }

        // More than one attribute is filled
        val attrCount =
            (if (fixedSize > 0) 1 else 0) +
                (if (ofAvailableSpace > 0) 1 else 0) +
                (if (ofRemainderSpace > 0) 1 else 0)
        if (attrCount > 1) {
            Log.e(TAG, "SizeSpec#isValid - more than one attribute is filled")
            return false
        }

        // Values should be between 0 and 1
        if (ofAvailableSpace !in 0f..1f || ofRemainderSpace !in 0f..1f) {
            Log.e(TAG, "SizeSpec#isValid - values should be between 0 and 1")
            return false
        }

        return true
    }

    override fun toString(): String {
        return "SizeSpec(fixedSize=$fixedSize, ofAvailableSpace=$ofAvailableSpace, " +
            "ofRemainderSpace=$ofRemainderSpace)"
    }
}

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
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import com.android.launcher3.R
import com.android.launcher3.util.ResourceHelper
import kotlin.math.roundToInt

/**
 * [SizeSpec] is an attribute used to represent a property in the responsive grid specs.
 *
 * @param fixedSize a fixed size in dp to be used
 * @param ofAvailableSpace a percentage of the available space
 * @param ofRemainderSpace a percentage of the remaining space (available space minus used space)
 * @param matchWorkspace indicates whether the workspace value will be used or not.
 * @param maxSize restricts the maximum value allowed for the [SizeSpec].
 */
data class SizeSpec(
    val fixedSize: Float = 0f,
    val ofAvailableSpace: Float = 0f,
    val ofRemainderSpace: Float = 0f,
    val matchWorkspace: Boolean = false,
    val maxSize: Int = Int.MAX_VALUE
) {

    /** Retrieves the correct value for [SizeSpec]. */
    fun getCalculatedValue(availableSpace: Int, workspaceValue: Int = 0): Int {
        val calculatedValue =
            when {
                fixedSize > 0 -> fixedSize.roundToInt()
                matchWorkspace -> workspaceValue
                ofAvailableSpace > 0 -> (ofAvailableSpace * availableSpace).roundToInt()
                else -> 0
            }

        return calculatedValue.coerceAtMost(maxSize)
    }

    /**
     * Calculates the [SizeSpec] value when remainder space value is defined. If no remainderSpace
     * is 0, returns a default value.
     */
    fun getRemainderSpaceValue(remainderSpace: Int, defaultValue: Int): Int {
        val remainderSpaceValue =
            if (ofRemainderSpace > 0) {
                (ofRemainderSpace * remainderSpace).roundToInt()
            } else {
                defaultValue
            }

        return remainderSpaceValue.coerceAtMost(maxSize)
    }

    fun isValid(): Boolean {
        // All attributes are empty
        if (fixedSize < 0f && ofAvailableSpace <= 0f && ofRemainderSpace <= 0f && !matchWorkspace) {
            Log.e(TAG, "SizeSpec#isValid - all attributes are empty")
            return false
        }

        // More than one attribute is filled
        val attrCount =
            (if (fixedSize > 0) 1 else 0) +
                (if (ofAvailableSpace > 0) 1 else 0) +
                (if (ofRemainderSpace > 0) 1 else 0) +
                (if (matchWorkspace) 1 else 0)
        if (attrCount > 1) {
            Log.e(TAG, "SizeSpec#isValid - more than one attribute is filled")
            return false
        }

        // Values should be between 0 and 1
        if (ofAvailableSpace !in 0f..1f || ofRemainderSpace !in 0f..1f) {
            Log.e(TAG, "SizeSpec#isValid - values should be between 0 and 1")
            return false
        }

        // Invalid fixed or max size
        if (fixedSize < 0f || maxSize < 0f) {
            Log.e(TAG, "SizeSpec#isValid - values should be bigger or equal to zero.")
            return false
        }

        if (fixedSize > 0f && fixedSize > maxSize) {
            Log.e(TAG, "SizeSpec#isValid - fixed size should be smaller than the max size.")
            return false
        }

        return true
    }

    fun onlyFixedSize(): Boolean {
        if (ofAvailableSpace > 0 || ofRemainderSpace > 0 || matchWorkspace) {
            Log.e(TAG, "SizeSpec#onlyFixedSize - only fixed size allowed for this tag")
            return false
        }
        return true
    }

    object XmlTags {
        const val START_PADDING = "startPadding"
        const val END_PADDING = "endPadding"
        const val GUTTER = "gutter"
        const val CELL_SIZE = "cellSize"
        const val HOTSEAT_QSB_SPACE = "hotseatQsbSpace"
        const val EDGE_PADDING = "edgePadding"
        const val ICON_SIZE = "iconSize"
        const val ICON_TEXT_SIZE = "iconTextSize"
        const val ICON_DRAWABLE_PADDING = "iconDrawablePadding"
    }

    companion object {
        private const val TAG = "SizeSpec"

        private fun getValue(a: TypedArray, index: Int): Float {
            return when (a.getType(index)) {
                TypedValue.TYPE_DIMENSION -> a.getDimensionPixelSize(index, 0).toFloat()
                TypedValue.TYPE_FLOAT -> a.getFloat(index, 0f)
                else -> 0f
            }
        }

        fun create(resourceHelper: ResourceHelper, attrs: AttributeSet): SizeSpec {
            val styledAttrs = resourceHelper.obtainStyledAttributes(attrs, R.styleable.SizeSpec)

            val fixedSize = getValue(styledAttrs, R.styleable.SizeSpec_fixedSize)
            val ofAvailableSpace = getValue(styledAttrs, R.styleable.SizeSpec_ofAvailableSpace)
            val ofRemainderSpace = getValue(styledAttrs, R.styleable.SizeSpec_ofRemainderSpace)
            val matchWorkspace = styledAttrs.getBoolean(R.styleable.SizeSpec_matchWorkspace, false)
            val maxSize =
                styledAttrs.getDimensionPixelSize(R.styleable.SizeSpec_maxSize, Int.MAX_VALUE)

            styledAttrs.recycle()

            return SizeSpec(fixedSize, ofAvailableSpace, ofRemainderSpace, matchWorkspace, maxSize)
        }
    }
}

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

package com.android.wm.shell.common.pip

import android.content.Context
import android.content.res.Resources
import android.graphics.PointF
import android.util.Size
import com.android.wm.shell.R

class LegacySizeSpecSource(
        private val context: Context,
        private val pipDisplayLayoutState: PipDisplayLayoutState
) : SizeSpecSource {

    private var mDefaultMinSize = 0
    /** The absolute minimum an overridden size's edge can be */
    private var mOverridableMinSize = 0
    /** The preferred minimum (and default minimum) size specified by apps.  */
    private var mOverrideMinSize: Size? = null

    private var mDefaultSizePercent = 0f
    private var mMinimumSizePercent = 0f
    private var mMaxAspectRatioForMinSize = 0f
    private var mMinAspectRatioForMinSize = 0f

    init {
        reloadResources()
    }

    private fun reloadResources() {
        val res: Resources = context.getResources()

        mDefaultMinSize = res.getDimensionPixelSize(
                R.dimen.default_minimal_size_pip_resizable_task)
        mOverridableMinSize = res.getDimensionPixelSize(
                R.dimen.overridable_minimal_size_pip_resizable_task)

        mDefaultSizePercent = res.getFloat(R.dimen.config_pictureInPictureDefaultSizePercent)
        mMinimumSizePercent = res.getFraction(R.fraction.config_pipShortestEdgePercent, 1, 1)

        mMaxAspectRatioForMinSize = res.getFloat(
                R.dimen.config_pictureInPictureAspectRatioLimitForMinSize)
        mMinAspectRatioForMinSize = 1f / mMaxAspectRatioForMinSize
    }

    override fun onConfigurationChanged() {
        reloadResources()
    }

    override fun getMaxSize(aspectRatio: Float): Size {
        val insetBounds = pipDisplayLayoutState.insetBounds

        val shorterLength: Int = Math.min(getDisplayBounds().width(),
                getDisplayBounds().height())
        val totalHorizontalPadding: Int = (insetBounds.left +
                (getDisplayBounds().width() - insetBounds.right))
        val totalVerticalPadding: Int = (insetBounds.top +
                (getDisplayBounds().height() - insetBounds.bottom))

        return if (aspectRatio > 1f) {
            val maxWidth = Math.max(getDefaultSize(aspectRatio).width,
                    shorterLength - totalHorizontalPadding)
            val maxHeight = (maxWidth / aspectRatio).toInt()
            Size(maxWidth, maxHeight)
        } else {
            val maxHeight = Math.max(getDefaultSize(aspectRatio).height,
                    shorterLength - totalVerticalPadding)
            val maxWidth = (maxHeight * aspectRatio).toInt()
            Size(maxWidth, maxHeight)
        }
    }

    override fun getDefaultSize(aspectRatio: Float): Size {
        if (mOverrideMinSize != null) {
            return getMinSize(aspectRatio)
        }
        val smallestDisplaySize: Int = Math.min(getDisplayBounds().width(),
                getDisplayBounds().height())
        val minSize = Math.max(getMinEdgeSize().toFloat(),
                smallestDisplaySize * mDefaultSizePercent).toInt()
        val width: Int
        val height: Int
        if (aspectRatio <= mMinAspectRatioForMinSize ||
                aspectRatio > mMaxAspectRatioForMinSize) {
            // Beyond these points, we can just use the min size as the shorter edge
            if (aspectRatio <= 1) {
                // Portrait, width is the minimum size
                width = minSize
                height = Math.round(width / aspectRatio)
            } else {
                // Landscape, height is the minimum size
                height = minSize
                width = Math.round(height * aspectRatio)
            }
        } else {
            // Within these points, ensure that the bounds fit within the radius of the limits
            // at the points
            val widthAtMaxAspectRatioForMinSize: Float = mMaxAspectRatioForMinSize * minSize
            val radius = PointF.length(widthAtMaxAspectRatioForMinSize, minSize.toFloat())
            height = Math.round(Math.sqrt((radius * radius /
                    (aspectRatio * aspectRatio + 1)).toDouble())).toInt()
            width = Math.round(height * aspectRatio)
        }
        return Size(width, height)
    }

    override fun getMinSize(aspectRatio: Float): Size {
        if (mOverrideMinSize != null) {
            return adjustOverrideMinSizeToAspectRatio(aspectRatio)!!
        }
        val shorterLength: Int = Math.min(getDisplayBounds().width(),
                getDisplayBounds().height())
        val minWidth: Int
        val minHeight: Int
        if (aspectRatio > 1f) {
            minWidth = Math.min(getDefaultSize(aspectRatio).width.toFloat(),
                    shorterLength * mMinimumSizePercent).toInt()
            minHeight = (minWidth / aspectRatio).toInt()
        } else {
            minHeight = Math.min(getDefaultSize(aspectRatio).height.toFloat(),
                    shorterLength * mMinimumSizePercent).toInt()
            minWidth = (minHeight * aspectRatio).toInt()
        }
        return Size(minWidth, minHeight)
    }

    override fun getSizeForAspectRatio(size: Size, aspectRatio: Float): Size {
        val smallestSize = Math.min(size.width, size.height)
        val minSize = Math.max(getMinEdgeSize(), smallestSize)
        val width: Int
        val height: Int
        if (aspectRatio <= 1) {
            // Portrait, width is the minimum size.
            width = minSize
            height = Math.round(width / aspectRatio)
        } else {
            // Landscape, height is the minimum size
            height = minSize
            width = Math.round(height * aspectRatio)
        }
        return Size(width, height)
    }

    private fun getDisplayBounds() = pipDisplayLayoutState.displayBounds

    /** Sets the preferred size of PIP as specified by the activity in PIP mode.  */
    override fun setOverrideMinSize(overrideMinSize: Size?) {
        mOverrideMinSize = overrideMinSize
    }

    /** Returns the preferred minimal size specified by the activity in PIP.  */
    override fun getOverrideMinSize(): Size? {
        val overrideMinSize = mOverrideMinSize ?: return null
        return if (overrideMinSize.width < mOverridableMinSize ||
                overrideMinSize.height < mOverridableMinSize) {
            Size(mOverridableMinSize, mOverridableMinSize)
        } else {
            overrideMinSize
        }
    }

    private fun getMinEdgeSize(): Int {
        return if (mOverrideMinSize == null) mDefaultMinSize else getOverrideMinEdgeSize()
    }

    /**
     * Returns the adjusted overridden min size if it is set; otherwise, returns null.
     *
     *
     * Overridden min size needs to be adjusted in its own way while making sure that the target
     * aspect ratio is maintained
     *
     * @param aspectRatio target aspect ratio
     */
    private fun adjustOverrideMinSizeToAspectRatio(aspectRatio: Float): Size? {
        val size = getOverrideMinSize() ?: return null
        val sizeAspectRatio = size.width / size.height.toFloat()
        return if (sizeAspectRatio > aspectRatio) {
            // Size is wider, fix the width and increase the height
            Size(size.width, (size.width / aspectRatio).toInt())
        } else {
            // Size is taller, fix the height and adjust the width.
            Size((size.height * aspectRatio).toInt(), size.height)
        }
    }
}
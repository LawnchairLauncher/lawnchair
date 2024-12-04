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
import android.util.Size
import com.android.wm.shell.R
import java.io.PrintWriter

class PhoneSizeSpecSource(
        private val context: Context,
        private val pipDisplayLayoutState: PipDisplayLayoutState
) : SizeSpecSource {
    private var DEFAULT_OPTIMIZED_ASPECT_RATIO = 9f / 16

    private var mDefaultMinSize = 0
    /** The absolute minimum an overridden size's edge can be */
    private var mOverridableMinSize = 0
    /** The preferred minimum (and default minimum) size specified by apps.  */
    private var mOverrideMinSize: Size? = null


    /**
     * Default percentages for the PIP size logic.
     * 1. Determine max widths
     * Subtract width of system UI and default padding from the shortest edge of the device.
     * This is the max width.
     * 2. Calculate Default and Mins
     * Default is mSystemPreferredDefaultSizePercent of max-width/height.
     * Min is mSystemPreferredMinimumSizePercent of it.
     *
     * NOTE: Do not use this directly, use the mPreferredDefaultSizePercent getter instead.
     */
    private var mSystemPreferredDefaultSizePercent = 0.6f
    /** Minimum percentages for the PIP size logic. */
    private var mSystemPreferredMinimumSizePercent = 0.5f

    /** Threshold to categorize the Display as square, calculated as min(w, h) / max(w, h). */
    private var mSquareDisplayThresholdForSystemPreferredSize = 0.95f
    /**
     * Default percentages for the PIP size logic when the Display is square-ish.
     * This is used instead when the display is square-ish, like fold-ables when unfolded,
     * to make sure that default PiP does not cover the hinge (halfway of the display).
     * 1. Determine max widths
     * Subtract width of system UI and default padding from the shortest edge of the device.
     * This is the max width.
     * 2. Calculate Default and Mins
     * Default is mSystemPreferredDefaultSizePercent of max-width/height.
     * Min is mSystemPreferredMinimumSizePercent of it.
     *
     * NOTE: Do not use this directly, use the mPreferredDefaultSizePercent getter instead.
     */
    private var mSystemPreferredDefaultSizePercentForSquareDisplay = 0.5f
    /** Minimum percentages for the PIP size logic. */
    private var mSystemPreferredMinimumSizePercentForSquareDisplay = 0.4f

    private val mIsSquareDisplay
        get() = minOf(pipDisplayLayoutState.displayLayout.width(),
                        pipDisplayLayoutState.displayLayout.height()).toFloat() /
                maxOf(pipDisplayLayoutState.displayLayout.width(),
                        pipDisplayLayoutState.displayLayout.height()) >
                mSquareDisplayThresholdForSystemPreferredSize
    private val mPreferredDefaultSizePercent
        get() = if (mIsSquareDisplay) mSystemPreferredDefaultSizePercentForSquareDisplay else
            mSystemPreferredDefaultSizePercent

    private val mPreferredMinimumSizePercent
        get() = if (mIsSquareDisplay) mSystemPreferredMinimumSizePercentForSquareDisplay else
            mSystemPreferredMinimumSizePercent

    /** Aspect ratio that the PIP size spec logic optimizes for.  */
    private var mOptimizedAspectRatio = 0f

    init {
        reloadResources()
    }

    private fun reloadResources() {
        val res: Resources = context.resources

        mDefaultMinSize = res.getDimensionPixelSize(
                R.dimen.default_minimal_size_pip_resizable_task)
        mOverridableMinSize = res.getDimensionPixelSize(
                R.dimen.overridable_minimal_size_pip_resizable_task)

        mSystemPreferredDefaultSizePercent = res.getFloat(
                R.dimen.config_pipSystemPreferredDefaultSizePercent)
        mSystemPreferredMinimumSizePercent = res.getFloat(
                R.dimen.config_pipSystemPreferredMinimumSizePercent)

        mSquareDisplayThresholdForSystemPreferredSize = res.getFloat(
                R.dimen.config_pipSquareDisplayThresholdForSystemPreferredSize)
        mSystemPreferredDefaultSizePercentForSquareDisplay = res.getFloat(
                R.dimen.config_pipSystemPreferredDefaultSizePercentForSquareDisplay)
        mSystemPreferredMinimumSizePercentForSquareDisplay = res.getFloat(
                R.dimen.config_pipSystemPreferredMinimumSizePercentForSquareDisplay)

        val requestedOptAspRatio = res.getFloat(R.dimen.config_pipLargeScreenOptimizedAspectRatio)
        // make sure the optimized aspect ratio is valid with a default value to fall back to
        mOptimizedAspectRatio = if (requestedOptAspRatio > 1) {
            DEFAULT_OPTIMIZED_ASPECT_RATIO
        } else {
            requestedOptAspRatio
        }
    }

    override fun onConfigurationChanged() {
        reloadResources()
    }

    /**
     * Calculates the max size of PIP.
     *
     * Optimizes for 16:9 aspect ratios, making them take full length of shortest display edge.
     * As aspect ratio approaches values close to 1:1, the logic does not let PIP occupy the
     * whole screen. A linear function is used to calculate these sizes.
     *
     * @param aspectRatio aspect ratio of the PIP window
     * @return dimensions of the max size of the PIP
     */
    override fun getMaxSize(aspectRatio: Float): Size {
        val insetBounds = pipDisplayLayoutState.insetBounds
        val displayBounds = pipDisplayLayoutState.displayBounds

        val totalHorizontalPadding: Int = (insetBounds.left +
                (displayBounds.width() - insetBounds.right))
        val totalVerticalPadding: Int = (insetBounds.top +
                (displayBounds.height() - insetBounds.bottom))
        val shorterLength: Int = Math.min(displayBounds.width() - totalHorizontalPadding,
                displayBounds.height() - totalVerticalPadding)
        var maxWidth: Int
        val maxHeight: Int

        // use the optimized max sizing logic only within a certain aspect ratio range
        if (aspectRatio >= mOptimizedAspectRatio && aspectRatio <= 1 / mOptimizedAspectRatio) {
            // this formula and its derivation is explained in b/198643358#comment16
            maxWidth = Math.round(mOptimizedAspectRatio * shorterLength +
                    shorterLength * (aspectRatio - mOptimizedAspectRatio) / (1 + aspectRatio))
            // make sure the max width doesn't go beyond shorter screen length after rounding
            maxWidth = Math.min(maxWidth, shorterLength)
            maxHeight = Math.round(maxWidth / aspectRatio)
        } else {
            if (aspectRatio > 1f) {
                maxWidth = shorterLength
                maxHeight = Math.round(maxWidth / aspectRatio)
            } else {
                maxHeight = shorterLength
                maxWidth = Math.round(maxHeight * aspectRatio)
            }
        }
        return Size(maxWidth, maxHeight)
    }

    /**
     * Decreases the dimensions by a percentage relative to max size to get default size.
     *
     * @param aspectRatio aspect ratio of the PIP window
     * @return dimensions of the default size of the PIP
     */
    override fun getDefaultSize(aspectRatio: Float): Size {
        val minSize = getMinSize(aspectRatio)
        if (mOverrideMinSize != null) {
            return minSize
        }
        val maxSize = getMaxSize(aspectRatio)
        val defaultWidth = Math.max(Math.round(maxSize.width * mPreferredDefaultSizePercent),
                minSize.width)
        val defaultHeight = Math.round(defaultWidth / aspectRatio)
        return Size(defaultWidth, defaultHeight)
    }

    /**
     * Decreases the dimensions by a certain percentage relative to max size to get min size.
     *
     * @param aspectRatio aspect ratio of the PIP window
     * @return dimensions of the min size of the PIP
     */
    override fun getMinSize(aspectRatio: Float): Size {
        // if there is an overridden min size provided, return that
        if (mOverrideMinSize != null) {
            return adjustOverrideMinSizeToAspectRatio(aspectRatio)!!
        }
        val maxSize = getMaxSize(aspectRatio)
        var minWidth = Math.round(maxSize.width * mPreferredMinimumSizePercent)
        var minHeight = Math.round(maxSize.height * mPreferredMinimumSizePercent)

        // make sure the calculated min size is not smaller than the allowed default min size
        if (aspectRatio > 1f) {
            minHeight = Math.max(minHeight, mDefaultMinSize)
            minWidth = Math.round(minHeight * aspectRatio)
        } else {
            minWidth = Math.max(minWidth, mDefaultMinSize)
            minHeight = Math.round(minWidth / aspectRatio)
        }
        return Size(minWidth, minHeight)
    }

    /**
     * Returns the size for target aspect ratio making sure new size conforms with the rules.
     *
     *
     * Recalculates the dimensions such that the target aspect ratio is achieved, while
     * maintaining the same maximum size to current size ratio.
     *
     * @param size current size
     * @param aspectRatio target aspect ratio
     */
    override fun getSizeForAspectRatio(size: Size, aspectRatio: Float): Size {
        if (size == mOverrideMinSize) {
            return adjustOverrideMinSizeToAspectRatio(aspectRatio)!!
        }

        val currAspectRatio = size.width.toFloat() / size.height

        // getting the percentage of the max size that current size takes
        val currentMaxSize = getMaxSize(currAspectRatio)
        val currentPercent = size.width.toFloat() / currentMaxSize.width

        // getting the max size for the target aspect ratio
        val updatedMaxSize = getMaxSize(aspectRatio)
        var width = Math.round(updatedMaxSize.width * currentPercent)
        var height = Math.round(updatedMaxSize.height * currentPercent)

        // adjust the dimensions if below allowed min edge size
        val minEdgeSize =
                if (mOverrideMinSize == null) mDefaultMinSize else getOverrideMinEdgeSize()

        if (width < minEdgeSize && aspectRatio <= 1) {
            width = minEdgeSize
            height = Math.round(width / aspectRatio)
        } else if (height < minEdgeSize && aspectRatio > 1) {
            height = minEdgeSize
            width = Math.round(height * aspectRatio)
        }

        // reduce the dimensions of the updated size to the calculated percentage
        return Size(width, height)
    }

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

    override fun dump(pw: PrintWriter, prefix: String) {
        val innerPrefix = "$prefix  "
        pw.println(innerPrefix + "mOverrideMinSize=" + mOverrideMinSize)
        pw.println(innerPrefix + "mOverridableMinSize=" + mOverridableMinSize)
        pw.println(innerPrefix + "mDefaultMinSize=" + mDefaultMinSize)
        pw.println(innerPrefix + "mDefaultSizePercent=" + mPreferredDefaultSizePercent)
        pw.println(innerPrefix + "mMinimumSizePercent=" + mPreferredMinimumSizePercent)
        pw.println(innerPrefix + "mOptimizedAspectRatio=" + mOptimizedAspectRatio)
    }
}
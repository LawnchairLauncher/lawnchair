/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.wm.shell.common.split

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatTextView
import com.android.wm.shell.R
import com.android.wm.shell.common.pip.PipUtils.dpToPx
import com.android.wm.shell.shared.TypefaceUtils
import com.android.wm.shell.shared.TypefaceUtils.Companion.setTypeface
import androidx.core.graphics.toColorInt

/**
 * A small tooltip bubble that educates the user about split screen breakpoints.
 */
class DividerTooltip(context: Context, attrs: AttributeSet?) :
    AppCompatTextView(context, attrs) {
    /** The length of the split divider handle, along its long edge, in px.  */
    private val mDividerHandleLengthPx =
        resources.getDimensionPixelSize(R.dimen.split_divider_handle_width)
    private var mIsLeftRightSplit = false

    init {
        alpha = 0f
        setTextColor(TOOLTIP_TEXT_COLOR)
        setBackgroundColor(TOOLTIP_BG_COLOR)
        setTypeface(this, TOOLTIP_FONT)
    }

    /**
     * Called in DividerView.setup() to determine orientation. Expected to always be called on
     * divider initialization.
     */
    fun setIsLeftRightSplit(isLeftRightSplit: Boolean) {
        mIsLeftRightSplit = isLeftRightSplit
    }

    /** Converts dp to px.  */
    private fun dpToPx(dpValue: Int): Int {
        return dpToPx(dpValue.toFloat(), mContext.resources.displayMetrics)
    }

    /**
     * Resizes the tooltip to fit the current text, and adjusts margins to put it in the correct
     * place on the screen.
     */
    override fun onTextChanged(
        text: CharSequence,
        start: Int,
        lengthBefore: Int,
        lengthAfter: Int) {
        if (layoutParams == null) {
            return
        }

        // Get a Rect representing the raw size of the current text string.
        val textBounds = Rect()
        paint.getTextBounds(text.toString(), 0, text.length, textBounds)

        val lp = layoutParams as FrameLayout.LayoutParams
        lp.height = textBounds.height() + (dpToPx(TOOLTIP_PADDING_DP) * 2)
        lp.width = textBounds.width() + (dpToPx(TOOLTIP_PADDING_DP) * 2)
        if (mIsLeftRightSplit) {
            lp.bottomMargin = ((mDividerHandleLengthPx / 2) + (lp.height / 2)
                    + dpToPx(TOOLTIP_DISTANCE_FROM_HANDLE_DP))
            lp.rightMargin = 0
        } else {
            lp.bottomMargin = 0
            lp.rightMargin = ((mDividerHandleLengthPx / 2) + (lp.width / 2)
                    + dpToPx(TOOLTIP_DISTANCE_FROM_HANDLE_DP))
        }

        layoutParams = lp
    }

    companion object {
        private val TOOLTIP_TEXT_COLOR = "#4A3F08".toColorInt()
        private val TOOLTIP_BG_COLOR = "#F5E29D".toColorInt()
        private val TOOLTIP_FONT = TypefaceUtils.FontFamily.GSF_BODY_MEDIUM_EMPHASIZED

        /** The padding between the tooltip's text and its outer border, on all four sides, in dp.  */
        private const val TOOLTIP_PADDING_DP = 12

        /** The distance between the tooltip's border and the (full-sized) divider handle, in dp.  */
        private const val TOOLTIP_DISTANCE_FROM_HANDLE_DP = 16
    }
}

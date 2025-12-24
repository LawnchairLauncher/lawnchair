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
package com.android.wm.shell.bubbles.bar;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import com.android.wm.shell.R;

/**
 * A view that acts as a caption area for the {@link BubbleBarExpandedView}, containing the handle.
 */
public class BubbleBarCaptionView extends FrameLayout {

    public static final int CAPTION_ELEVATION = 1;

    private final View mBackgroundView;
    private int mBackgroundColor;

    private final BubbleBarHandleView mHandleView;

    public BubbleBarCaptionView(Context context) {
        this(context, null /* attrs */);
    }

    public BubbleBarCaptionView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0 /* defStyleAttr */);
    }

    public BubbleBarCaptionView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0 /* defStyleRes */);
    }

    public BubbleBarCaptionView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        // Add the background view.
        mBackgroundView = new View(context);
        addView(mBackgroundView, new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

        // Add the handle view.
        mHandleView = new BubbleBarHandleView(context);
        mHandleView.setId(R.id.bubble_bar_handle_view);
        final int handleWidth = getResources().getDimensionPixelSize(
                R.dimen.bubble_bar_expanded_view_handle_width);
        final LayoutParams handleLp = new LayoutParams(handleWidth, LayoutParams.MATCH_PARENT);
        handleLp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        addView(mHandleView, handleLp);

        // Set background color with an initial color based on the system theme after both views
        // have been added so that the caption and handle color can be updated if needed.
        final boolean isSystemDark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setBackgroundColor(isSystemDark ? Color.BLACK : Color.WHITE);

        // Elevation needs to be set to draw the handle and caption on top of task view.
        setElevation(CAPTION_ELEVATION);
    }

    public BubbleBarHandleView getHandleView() {
        return mHandleView;
    }

    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    /** Sets the background color of the caption bar. */
    public void setBackgroundColor(int color) {
        if (mBackgroundView != null && color != mBackgroundColor) {
            mBackgroundView.setBackgroundColor(color);
            mBackgroundColor = color;
            if (mHandleView != null) {
                boolean isRegionDark = Color.valueOf(color).luminance() < 0.5;
                // Only animate the color change if the view is already attached to a window.
                // During initial inflation (in the constructor), this will be false.
                mHandleView.updateHandleColor(isRegionDark, isAttachedToWindow() /* animated */);
            }
        }
    }
}

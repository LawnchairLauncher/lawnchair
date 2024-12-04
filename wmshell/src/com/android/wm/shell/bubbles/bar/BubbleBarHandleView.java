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
package com.android.wm.shell.bubbles.bar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.ColorInt;
import androidx.core.content.ContextCompat;

import com.android.wm.shell.R;

/**
 * Handle view to show at the top of a bubble bar expanded view.
 */
public class BubbleBarHandleView extends View {
    private static final long COLOR_CHANGE_DURATION = 120;
    // Path used to draw the dots
    private final Path mPath = new Path();

    private final @ColorInt int mHandleLightColor;
    private final @ColorInt int mHandleDarkColor;
    private @Nullable ObjectAnimator mColorChangeAnim;

    public BubbleBarHandleView(Context context) {
        this(context, null /* attrs */);
    }

    public BubbleBarHandleView(Context context, AttributeSet attrs) {
        this(context, attrs, 0 /* defStyleAttr */);
    }

    public BubbleBarHandleView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0 /* defStyleRes */);
    }

    public BubbleBarHandleView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        final int handleHeight = getResources().getDimensionPixelSize(
                R.dimen.bubble_bar_expanded_view_handle_height);
        mHandleLightColor = ContextCompat.getColor(getContext(),
                R.color.bubble_bar_expanded_view_handle_light);
        mHandleDarkColor = ContextCompat.getColor(getContext(),
                R.color.bubble_bar_expanded_view_handle_dark);

        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                final int handleCenterY = view.getHeight() / 2;
                final int handleTop = handleCenterY - handleHeight / 2;
                final int handleBottom = handleTop + handleHeight;
                final int radius = handleHeight / 2;
                RectF handle = new RectF(/* left = */ 0, handleTop, view.getWidth(), handleBottom);
                mPath.reset();
                mPath.addRoundRect(handle, radius, radius, Path.Direction.CW);
                outline.setPath(mPath);
            }
        });
    }

    /**
     * Updates the handle color.
     *
     * @param isRegionDark Whether the background behind the handle is dark, and thus the handle
     *                     should be light (and vice versa).
     * @param animated      Whether to animate the change, or apply it immediately.
     */
    public void updateHandleColor(boolean isRegionDark, boolean animated) {
        int newColor = isRegionDark ? mHandleLightColor : mHandleDarkColor;
        if (mColorChangeAnim != null) {
            mColorChangeAnim.cancel();
        }
        if (animated) {
            mColorChangeAnim = ObjectAnimator.ofArgb(this, "backgroundColor", newColor);
            mColorChangeAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mColorChangeAnim = null;
                }
            });
            mColorChangeAnim.setDuration(COLOR_CHANGE_DURATION);
            mColorChangeAnim.start();
        } else {
            setBackgroundColor(newColor);
        }
    }
}

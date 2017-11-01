/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.discovery;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;

import com.android.launcher3.R;

/**
 * A simple rating view that shows stars with a rating from 0-5.
 */
public class RatingView extends View {

    private static final float WIDTH_FACTOR = 0.9f;
    private static final int MAX_LEVEL = 10000;
    private static final int MAX_STARS = 5;

    private final Drawable mStarDrawable;
    private final int mColorGray;
    private final int mColorHighlight;

    private float rating;

    public RatingView(Context context) {
        this(context, null);
    }

    public RatingView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RatingView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mStarDrawable = getResources().getDrawable(R.drawable.ic_star_rating, null);
        mColorGray = 0x1E000000;
        mColorHighlight = 0x8A000000;
    }

    public void setRating(float rating) {
        this.rating = Math.min(Math.max(rating, 0), MAX_STARS);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawStars(canvas, MAX_STARS, mColorGray);
        drawStars(canvas, rating, mColorHighlight);
    }

    private void drawStars(Canvas canvas, float stars, int color) {
        int fullWidth = getLayoutParams().width;
        int cellWidth = fullWidth / MAX_STARS;
        int starWidth = (int) (cellWidth * WIDTH_FACTOR);
        int padding = cellWidth - starWidth;
        int fullStars = (int) stars;
        float partialStarFactor = stars - fullStars;

        for (int i = 0; i < fullStars; i++) {
            int x = i * cellWidth + padding;
            Drawable star = mStarDrawable.getConstantState().newDrawable().mutate();
            star.setTint(color);
            star.setBounds(x, padding, x + starWidth, padding + starWidth);
            star.draw(canvas);
        }
        if (partialStarFactor > 0f) {
            int x = fullStars * cellWidth + padding;
            ClipDrawable star = new ClipDrawable(mStarDrawable,
                    Gravity.LEFT, ClipDrawable.HORIZONTAL);
            star.setTint(color);
            star.setLevel((int) (MAX_LEVEL * partialStarFactor));
            star.setBounds(x, padding, x + starWidth, padding + starWidth);
            star.draw(canvas);
        }
    }
}

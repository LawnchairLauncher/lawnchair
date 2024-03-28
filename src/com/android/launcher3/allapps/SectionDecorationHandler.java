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
package com.android.launcher3.allapps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.android.launcher3.R;
import com.android.launcher3.util.Themes;

public class SectionDecorationHandler {

    protected final Path mTmpPath = new Path();
    protected final RectF mTmpRect = new RectF();

    protected final int mCornerGroupRadius;
    protected final int mCornerResultRadius;
    protected final RectF mBounds = new RectF();
    protected final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    protected final int mFocusAlpha = 255; // main focused item alpha
    protected int mFillColor; // grouping color
    protected int mFocusColor; // main focused item color
    protected float mFillSpacing;
    protected int mInlineRadius;
    protected Context mContext;
    protected float[] mCorners;
    protected int mFillAlpha;
    protected boolean mIsTopLeftRound;
    protected boolean mIsTopRightRound;
    protected boolean mIsBottomLeftRound;
    protected boolean mIsBottomRightRound;
    protected boolean mIsBottomRound;
    protected boolean mIsTopRound;

    public SectionDecorationHandler(Context context, int fillAlpha, boolean isTopLeftRound,
            boolean isTopRightRound, boolean isBottomLeftRound,
            boolean isBottomRightRound) {

        mContext = context;
        mFillAlpha = fillAlpha;
        mFocusColor = ContextCompat.getColor(context,
                R.color.material_color_surface_bright); // UX recommended
        mFillColor = ContextCompat.getColor(context,
                R.color.material_color_surface_container_high); // UX recommended

        mIsTopLeftRound = isTopLeftRound;
        mIsTopRightRound = isTopRightRound;
        mIsBottomLeftRound = isBottomLeftRound;
        mIsBottomRightRound = isBottomRightRound;
        mIsBottomRound = mIsBottomLeftRound && mIsBottomRightRound;
        mIsTopRound = mIsTopLeftRound && mIsTopRightRound;

        mCornerGroupRadius = context.getResources().getDimensionPixelSize(
                R.dimen.all_apps_recycler_view_decorator_group_radius);
        mCornerResultRadius = context.getResources().getDimensionPixelSize(
                R.dimen.all_apps_recycler_view_decorator_result_radius);

        mInlineRadius = 0;
        mFillSpacing = 0;
        initCorners();
    }

    protected void initCorners() {
        mCorners = new float[]{
                mIsTopLeftRound ? mCornerGroupRadius : 0,
                mIsTopLeftRound ? mCornerGroupRadius : 0, // Top left radius in px
                mIsTopRightRound ? mCornerGroupRadius : 0,
                mIsTopRightRound ? mCornerGroupRadius : 0, // Top right radius in px
                mIsBottomRightRound ? mCornerGroupRadius : 0,
                mIsBottomRightRound ? mCornerGroupRadius : 0, // Bottom right
                mIsBottomLeftRound ? mCornerGroupRadius : 0,
                mIsBottomLeftRound ? mCornerGroupRadius : 0 // Bottom left
        };
    }

    protected void setFillAlpha(int fillAlpha) {
        mFillAlpha = fillAlpha;
        mPaint.setAlpha(mFillAlpha);
    }

    protected void onFocusDraw(Canvas canvas, @Nullable View view) {
        if (view == null) {
            return;
        }
        mPaint.setColor(mFillColor);
        mPaint.setAlpha(mFillAlpha);
        int scaledHeight = (int) (view.getHeight() * view.getScaleY());
        mBounds.set(view.getLeft(), view.getY(), view.getRight(), view.getY() + scaledHeight);
        onDraw(canvas);
    }

    protected void onDraw(Canvas canvas) {
        mTmpPath.reset();
        mTmpRect.set(mBounds.left + mFillSpacing,
                mBounds.top + mFillSpacing,
                mBounds.right - mFillSpacing,
                mBounds.bottom - mFillSpacing);
        mTmpPath.addRoundRect(mTmpRect, mCorners, Path.Direction.CW);
        canvas.drawPath(mTmpPath, mPaint);
    }

    /** Sets the right background drawable to the view based on the give decoration info. */
    public void applyBackground(View view, Context context,
           @Nullable SectionDecorationInfo decorationInfo, boolean isHighlighted) {
        int inset = context.getResources().getDimensionPixelSize(
                R.dimen.all_apps_recycler_view_decorator_padding);
        float radiusBottom = (decorationInfo == null || decorationInfo.isBottomRound()) ?
                mCornerGroupRadius : mCornerResultRadius;
        float radiusTop =
                (decorationInfo == null || decorationInfo.isTopRound()) ?
                        mCornerGroupRadius : mCornerResultRadius;
        int color = isHighlighted ? mFocusColor : mFillColor;

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadii(new float[] {
                radiusTop, radiusTop, // top-left
                radiusTop, radiusTop, // top-right
                radiusBottom, radiusBottom, // bottom-right
                radiusBottom, radiusBottom // bottom-left
        });
        shape.setColor(color);

        // Setting the background resets the padding, so we cache it and reset it afterwards.
        int paddingLeft = view.getPaddingLeft();
        int paddingTop = view.getPaddingTop();
        int paddingRight = view.getPaddingRight();
        int paddingBottom = view.getPaddingBottom();

        view.setBackground(new InsetDrawable(shape, inset));

        view.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
    }

    /**
     * Section decorator that combines views and draws a single block decoration
     */
    public static class UnionDecorationHandler extends SectionDecorationHandler {

        private final int mPaddingLeft;
        private final int mPaddingRight;

        public UnionDecorationHandler(
                SectionDecorationHandler decorationHandler,
                int paddingLeft, int paddingRight) {
            super(decorationHandler.mContext, decorationHandler.mFillAlpha,
                    decorationHandler.mIsTopLeftRound, decorationHandler.mIsTopRightRound,
                    decorationHandler.mIsBottomLeftRound, decorationHandler.mIsBottomRightRound);
            mPaddingLeft = paddingLeft;
            mPaddingRight = paddingRight;
        }

        /**
         * Expands decoration bounds to include child {@link PrivateAppsSectionDecorator}
         */
        public void addChild(SectionDecorationHandler child, View view, boolean applyBackground) {
            int scaledHeight = (int) (view.getHeight() * view.getScaleY());
            mBounds.union(view.getLeft(), view.getY(),
                    view.getRight(), view.getY() + scaledHeight);
            if (applyBackground) {
                applyBackground(view, mContext, null, false);
            }
            mIsBottomRound |= child.mIsBottomRound;
            mIsBottomLeftRound |= child.mIsBottomLeftRound;
            mIsBottomRightRound |= child.mIsBottomRightRound;
            mIsTopRound |= child.mIsTopRound;
            mIsTopLeftRound |= child.mIsTopLeftRound;
            mIsTopRightRound |= child.mIsTopRightRound;
        }

        /**
         * Draws group decoration to canvas
         */
        public void onGroupDecorate(Canvas canvas) {
            initCorners();
            mBounds.left = mPaddingLeft;
            mBounds.right = canvas.getWidth() - mPaddingRight;
            mPaint.setColor(mFillColor);
            mPaint.setAlpha(mFillAlpha);
            onDraw(canvas);
        }
    }
}

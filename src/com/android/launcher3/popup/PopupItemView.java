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

package com.android.launcher3.popup;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.popup.PopupContainerWithArrow.RoundedCornerFlags;

import static com.android.launcher3.popup.PopupContainerWithArrow.ROUNDED_BOTTOM_CORNERS;
import static com.android.launcher3.popup.PopupContainerWithArrow.ROUNDED_TOP_CORNERS;

/**
 * An abstract {@link FrameLayout} that contains content for {@link PopupContainerWithArrow}.
 */
public abstract class PopupItemView extends FrameLayout {

    protected final Rect mPillRect;
    protected  @RoundedCornerFlags int mRoundedCorners;
    protected final boolean mIsRtl;
    protected View mIconView;

    private final Paint mBackgroundClipPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Matrix mMatrix = new Matrix();
    private Bitmap mRoundedCornerBitmap;

    public PopupItemView(Context context) {
        this(context, null, 0);
    }

    public PopupItemView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PopupItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mPillRect = new Rect();

        // Initialize corner clipping Bitmap and Paint.
        int radius = (int) getBackgroundRadius();
        mRoundedCornerBitmap = Bitmap.createBitmap(radius, radius, Bitmap.Config.ALPHA_8);
        Canvas canvas = new Canvas();
        canvas.setBitmap(mRoundedCornerBitmap);
        canvas.drawArc(0, 0, radius*2, radius*2, 180, 90, true, mBackgroundClipPaint);
        mBackgroundClipPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

        mIsRtl = Utilities.isRtl(getResources());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIconView = findViewById(R.id.popup_item_icon);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mPillRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mRoundedCorners == 0) {
            super.dispatchDraw(canvas);
            return;
        }

        int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        super.dispatchDraw(canvas);

        // Clip children to this item's rounded corners.
        int cornerWidth = mRoundedCornerBitmap.getWidth();
        int cornerHeight = mRoundedCornerBitmap.getHeight();
        int cornerCenterX = Math.round(cornerWidth / 2f);
        int cornerCenterY = Math.round(cornerHeight / 2f);
        if ((mRoundedCorners & ROUNDED_TOP_CORNERS) != 0) {
            // Clip top left corner.
            mMatrix.reset();
            canvas.drawBitmap(mRoundedCornerBitmap, mMatrix, mBackgroundClipPaint);
            // Clip top right corner.
            mMatrix.setRotate(90, cornerCenterX, cornerCenterY);
            mMatrix.postTranslate(canvas.getWidth() - cornerWidth, 0);
            canvas.drawBitmap(mRoundedCornerBitmap, mMatrix, mBackgroundClipPaint);
        }
        if ((mRoundedCorners & ROUNDED_BOTTOM_CORNERS) != 0) {
            // Clip bottom right corner.
            mMatrix.setRotate(180, cornerCenterX, cornerCenterY);
            mMatrix.postTranslate(canvas.getWidth() - cornerWidth, canvas.getHeight() - cornerHeight);
            canvas.drawBitmap(mRoundedCornerBitmap, mMatrix, mBackgroundClipPaint);
            // Clip bottom left corner.
            mMatrix.setRotate(270, cornerCenterX, cornerCenterY);
            mMatrix.postTranslate(0, canvas.getHeight() - cornerHeight);
            canvas.drawBitmap(mRoundedCornerBitmap, mMatrix, mBackgroundClipPaint);
        }

        canvas.restoreToCount(saveCount);
    }

    /**
     * Creates a round rect drawable (with the specified corners unrounded)
     * and sets it as this View's background.
     */
    public void setBackgroundWithCorners(int color, @RoundedCornerFlags int roundedCorners) {
        mRoundedCorners = roundedCorners;
        float rTop = (roundedCorners & ROUNDED_TOP_CORNERS) == 0 ? 0 : getBackgroundRadius();
        float rBot = (roundedCorners & ROUNDED_BOTTOM_CORNERS) == 0 ? 0 : getBackgroundRadius();
        float[] radii = new float[] {rTop, rTop, rTop, rTop, rBot, rBot, rBot, rBot};
        ShapeDrawable roundRectBackground = new ShapeDrawable(new RoundRectShape(radii, null, null));
        roundRectBackground.getPaint().setColor(color);
        setBackground(roundRectBackground);
    }

    protected float getBackgroundRadius() {
        return getResources().getDimensionPixelSize(R.dimen.bg_round_rect_radius);
    }
}

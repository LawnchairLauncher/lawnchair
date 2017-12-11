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

package ch.deletescape.lawnchair.popup;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.popup.theme.IPopupThemer;

/**
 * An abstract {@link FrameLayout} that supports animating an item's content
 * (e.g. icon and text) separate from the item's background.
 */
public abstract class PopupItemView extends FrameLayout
        implements ValueAnimator.AnimatorUpdateListener {
    public static final int CORNERS_TOP = 1;
    public static final int CORNERS_BOTTOM = 2;
    public static final int CORNERS_ALL = CORNERS_TOP | CORNERS_BOTTOM;

    protected static final Point sTempPoint = new Point();

    protected final Rect mPillRect;
    private float mOpenAnimationProgress;
    protected final boolean mIsRtl;
    protected View mIconView;

    private final Paint mBackgroundClipPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private final Matrix mMatrix = new Matrix();
    private Bitmap mRoundedCornerBitmap;

    protected IPopupThemer mTheme;
    protected int mCorners = CORNERS_ALL;

    public PopupItemView(Context context) {
        this(context, null, 0);
    }

    public PopupItemView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PopupItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mPillRect = new Rect();
        mTheme = Utilities.getThemer().popupTheme(context);

        // Initialize corner clipping Bitmap and Paint.
        int radius = (int) getBackgroundRadius();
        mRoundedCornerBitmap = Bitmap.createBitmap(radius, radius, Bitmap.Config.ALPHA_8);
        Canvas canvas = new Canvas();
        canvas.setBitmap(mRoundedCornerBitmap);
        canvas.drawArc(0, 0, radius * 2, radius * 2, 180, 90, true, mBackgroundClipPaint);
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
        int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        canvas.clipRect(mPillRect);
        super.dispatchDraw(canvas);

        int cornerWidth = mRoundedCornerBitmap.getWidth();
        int cornerHeight = mRoundedCornerBitmap.getHeight();
        if ((mCorners & CORNERS_TOP) != 0) {
            // Clip top left corner.
            mMatrix.reset();
            canvas.drawBitmap(mRoundedCornerBitmap, mMatrix, mBackgroundClipPaint);
            // Clip top right corner.
            mMatrix.setRotate(90, cornerWidth / 2, cornerHeight / 2);
            mMatrix.postTranslate(mPillRect.width() - cornerWidth, 0);
            canvas.drawBitmap(mRoundedCornerBitmap, mMatrix, mBackgroundClipPaint);
        }
        if ((mCorners & CORNERS_BOTTOM) != 0) {
            // Clip bottom right corner.
            mMatrix.setRotate(180, cornerWidth / 2, cornerHeight / 2);
            mMatrix.postTranslate(mPillRect.width() - cornerWidth, mPillRect.height() - cornerHeight);
            canvas.drawBitmap(mRoundedCornerBitmap, mMatrix, mBackgroundClipPaint);
            // Clip bottom left corner.
            mMatrix.setRotate(270, cornerWidth / 2, cornerHeight / 2);
            mMatrix.postTranslate(0, mPillRect.height() - cornerHeight);
            canvas.drawBitmap(mRoundedCornerBitmap, mMatrix, mBackgroundClipPaint);
        }

        canvas.restoreToCount(saveCount);
    }

    public void setBackgroundWithCorners(int i, int i2) {
        float f = 0.0f;
        mCorners = i2;
        float backgroundRadius = (i2 & 1) == 0 ? 0.0f : getBackgroundRadius();
        if ((i2 & 2) != 0) {
            f = getBackgroundRadius();
        }
        ShapeDrawable shapeDrawable = new ShapeDrawable(new RoundRectShape(new float[]{backgroundRadius, backgroundRadius, backgroundRadius, backgroundRadius, f, f, f, f}, null, null));
        shapeDrawable.getPaint().setColor(i);
        setBackground(shapeDrawable);
    }

    @Override
    public void onAnimationUpdate(ValueAnimator valueAnimator) {
        mOpenAnimationProgress = valueAnimator.getAnimatedFraction();
    }

    public boolean isOpenOrOpening() {
        return mOpenAnimationProgress > 0;
    }

    /**
     * Returns the position of the center of the icon relative to the container.
     */
    public Point getIconCenter() {
        sTempPoint.y = getMeasuredHeight() / 2;
        sTempPoint.x = getResources().getDimensionPixelSize(R.dimen.bg_popup_item_height) / 2;
        if (Utilities.isRtl(getResources())) {
            sTempPoint.x = getMeasuredWidth() - sTempPoint.x;
        }
        return sTempPoint;
    }

    protected float getBackgroundRadius() {
        return getResources().getDimensionPixelSize(mTheme.getBackgroundRadius());
    }

    protected PopupContainerWithArrow getContainer() {
        if (getParent() instanceof PopupContainerWithArrow) {
            return (PopupContainerWithArrow) getParent();
        } else {
            return (PopupContainerWithArrow) getParent().getParent().getParent();
        }
    }

    public abstract int getArrowColor(boolean isArrowAttachedToBottom);
}
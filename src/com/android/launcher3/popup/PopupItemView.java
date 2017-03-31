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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher3.LogAccelerateInterpolator;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.PillRevealOutlineProvider;

/**
 * An abstract {@link FrameLayout} that supports animating an item's content
 * (e.g. icon and text) separate from the item's background.
 */
public abstract class PopupItemView extends FrameLayout
        implements ValueAnimator.AnimatorUpdateListener {

    protected static final Point sTempPoint = new Point();

    protected final Rect mPillRect;
    private float mOpenAnimationProgress;

    protected View mIconView;

    private final Paint mBackgroundClipPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG |
            Paint.FILTER_BITMAP_FLAG);

    public PopupItemView(Context context) {
        this(context, null, 0);
    }

    public PopupItemView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PopupItemView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mPillRect = new Rect();
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

    protected void initializeBackgroundClipping(boolean force) {
        if (force || mBackgroundClipPaint.getShader() == null) {
            mBackgroundClipPaint.setXfermode(null);
            mBackgroundClipPaint.setShader(null);
            Bitmap backgroundBitmap = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(),
                    Bitmap.Config.ALPHA_8);
            Canvas canvas = new Canvas();
            canvas.setBitmap(backgroundBitmap);
            canvas.drawRoundRect(0, 0, getMeasuredWidth(), getMeasuredHeight(),
                    getBackgroundRadius(), getBackgroundRadius(), mBackgroundClipPaint);
            Shader backgroundClipShader = new BitmapShader(backgroundBitmap,
                    Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            mBackgroundClipPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            mBackgroundClipPaint.setShader(backgroundClipShader);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        initializeBackgroundClipping(false /* force */);
        int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null,
                Canvas.HAS_ALPHA_LAYER_SAVE_FLAG | Canvas.CLIP_TO_LAYER_SAVE_FLAG);
        super.dispatchDraw(canvas);
        canvas.drawPaint(mBackgroundClipPaint);
        canvas.restoreToCount(saveCount);
    }

    /**
     * Creates an animator to play when the shortcut container is being opened.
     */
    public Animator createOpenAnimation(boolean isContainerAboveIcon, boolean pivotLeft) {
        Point center = getIconCenter();
        ValueAnimator openAnimator =  new ZoomRevealOutlineProvider(center.x, center.y,
                mPillRect, this, mIconView, isContainerAboveIcon, pivotLeft)
                        .createRevealAnimator(this, false);
        mOpenAnimationProgress = 0f;
        openAnimator.addUpdateListener(this);
        return openAnimator;
    }

    @Override
    public void onAnimationUpdate(ValueAnimator valueAnimator) {
        mOpenAnimationProgress = valueAnimator.getAnimatedFraction();
    }

    public boolean isOpenOrOpening() {
        return mOpenAnimationProgress > 0;
    }

    /**
     * Creates an animator to play when the shortcut container is being closed.
     */
    public Animator createCloseAnimation(boolean isContainerAboveIcon, boolean pivotLeft,
            long duration) {
        Point center = getIconCenter();
        ValueAnimator closeAnimator = new ZoomRevealOutlineProvider(center.x, center.y,
                mPillRect, this, mIconView, isContainerAboveIcon, pivotLeft)
                        .createRevealAnimator(this, true);
        // Scale down the duration and interpolator according to the progress
        // that the open animation was at when the close started.
        closeAnimator.setDuration((long) (duration * mOpenAnimationProgress));
        closeAnimator.setInterpolator(new CloseInterpolator(mOpenAnimationProgress));
        closeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mOpenAnimationProgress = 0;
            }
        });
        return closeAnimator;
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
        return getResources().getDimensionPixelSize(R.dimen.bg_round_rect_radius);
    }

    /**
     * Extension of {@link PillRevealOutlineProvider} which scales the icon based on the height.
     */
    private static class ZoomRevealOutlineProvider extends PillRevealOutlineProvider {

        private final View mTranslateView;
        private final View mZoomView;

        private final float mFullHeight;
        private final float mTranslateYMultiplier;

        private final boolean mPivotLeft;
        private final float mTranslateX;

        public ZoomRevealOutlineProvider(int x, int y, Rect pillRect, PopupItemView translateView,
                View zoomView, boolean isContainerAboveIcon, boolean pivotLeft) {
            super(x, y, pillRect, translateView.getBackgroundRadius());
            mTranslateView = translateView;
            mZoomView = zoomView;
            mFullHeight = pillRect.height();

            mTranslateYMultiplier = isContainerAboveIcon ? 0.5f : -0.5f;

            mPivotLeft = pivotLeft;
            mTranslateX = pivotLeft ? pillRect.height() / 2 : pillRect.right - pillRect.height() / 2;
        }

        @Override
        public void setProgress(float progress) {
            super.setProgress(progress);

            if (mZoomView != null) {
                mZoomView.setScaleX(progress);
                mZoomView.setScaleY(progress);
            }

            float height = mOutline.height();
            mTranslateView.setTranslationY(mTranslateYMultiplier * (mFullHeight - height));

            float pivotX = mPivotLeft ? (mOutline.left + height / 2) : (mOutline.right - height / 2);
            mTranslateView.setTranslationX(mTranslateX - pivotX);
        }
    }

    /**
     * An interpolator that reverses the current open animation progress.
     */
    private static class CloseInterpolator extends LogAccelerateInterpolator {
        private float mStartProgress;
        private float mRemainingProgress;

        /**
         * @param openAnimationProgress The progress that the open interpolator ended at.
         */
        public CloseInterpolator(float openAnimationProgress) {
            super(100, 0);
            mStartProgress = 1f - openAnimationProgress;
            mRemainingProgress = openAnimationProgress;
        }

        @Override
        public float getInterpolation(float v) {
            return mStartProgress + super.getInterpolation(v) * mRemainingProgress;
        }
    }
}

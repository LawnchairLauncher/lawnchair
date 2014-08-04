package com.android.launcher3;

import android.animation.ObjectAnimator;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

class PreloadIconDrawable extends Drawable {

    private static final float ANIMATION_PROGRESS_STOPPED = -1.0f;
    private static final float ANIMATION_PROGRESS_STARTED = 0f;
    private static final float ANIMATION_PROGRESS_COMPLETED = 1.0f;

    private static final float MIN_SATUNATION = 0.2f;
    private static final float MIN_LIGHTNESS = 0.6f;

    private static final float ICON_SCALE_FACTOR = 0.5f;
    private static final int DEFAULT_COLOR = 0xFF009688;

    private static final Rect sTempRect = new Rect();

    private final RectF mIndicatorRect = new RectF();
    private boolean mIndicatorRectDirty;

    private final Paint mPaint;
    final Drawable mIcon;

    private Drawable mBgDrawable;
    private int mRingOutset;

    private int mIndicatorColor = 0;

    /**
     * Indicates the progress of the preloader [0-100]. If it goes above 100, only the icon
     * is shown with no progress bar.
     */
    private int mProgress = 0;

    private float mAnimationProgress = ANIMATION_PROGRESS_STOPPED;
    private ObjectAnimator mAnimator;

    public PreloadIconDrawable(Drawable icon, Theme theme) {
        mIcon = icon;

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);

        setBounds(icon.getBounds());
        applyTheme(theme);
        onLevelChange(0);
    }

    @Override
    public void applyTheme(Theme t) {
        TypedArray ta = t.obtainStyledAttributes(R.styleable.PreloadIconDrawable);
        mBgDrawable = ta.getDrawable(R.styleable.PreloadIconDrawable_background);
        mBgDrawable.setFilterBitmap(true);
        mPaint.setStrokeWidth(ta.getDimension(R.styleable.PreloadIconDrawable_indicatorSize, 0));
        mRingOutset = ta.getDimensionPixelSize(R.styleable.PreloadIconDrawable_ringOutset, 0);
        ta.recycle();
        onBoundsChange(getBounds());
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        mIcon.setBounds(bounds);
        if (mBgDrawable != null) {
            sTempRect.set(bounds);
            sTempRect.inset(-mRingOutset, -mRingOutset);
            mBgDrawable.setBounds(sTempRect);
        }
        mIndicatorRectDirty = true;
    }

    public int getOutset() {
        return mRingOutset;
    }

    /**
     * The size of the indicator is same as the content region of the {@link #mBgDrawable} minus
     * half the stroke size to accommodate the indicator.
     */
    private void initIndicatorRect() {
        Drawable d = mBgDrawable;
        Rect bounds = d.getBounds();

        d.getPadding(sTempRect);
        // Amount by which padding has to be scaled
        float paddingScaleX = ((float) bounds.width()) / d.getIntrinsicWidth();
        float paddingScaleY = ((float) bounds.height()) / d.getIntrinsicHeight();
        mIndicatorRect.set(
                bounds.left + sTempRect.left * paddingScaleX,
                bounds.top + sTempRect.top * paddingScaleY,
                bounds.right - sTempRect.right * paddingScaleX,
                bounds.bottom - sTempRect.bottom * paddingScaleY);

        float inset = mPaint.getStrokeWidth() / 2;
        mIndicatorRect.inset(inset, inset);
        mIndicatorRectDirty = false;
    }

    @Override
    public void draw(Canvas canvas) {
        final Rect r = new Rect(getBounds());
        if (canvas.getClipBounds(sTempRect) && !Rect.intersects(sTempRect, r)) {
            // The draw region has been clipped.
            return;
        }
        if (mIndicatorRectDirty) {
            initIndicatorRect();
        }
        final float iconScale;

        if ((mAnimationProgress >= ANIMATION_PROGRESS_STARTED)
                && (mAnimationProgress < ANIMATION_PROGRESS_COMPLETED)) {
            mPaint.setAlpha((int) ((1 - mAnimationProgress) * 255));
            mBgDrawable.setAlpha(mPaint.getAlpha());
            mBgDrawable.draw(canvas);
            canvas.drawOval(mIndicatorRect, mPaint);

            iconScale = ICON_SCALE_FACTOR + (1 - ICON_SCALE_FACTOR) * mAnimationProgress;
        } else if (mAnimationProgress == ANIMATION_PROGRESS_STOPPED) {
            mPaint.setAlpha(255);
            iconScale = ICON_SCALE_FACTOR;
            mBgDrawable.setAlpha(255);
            mBgDrawable.draw(canvas);

            if (mProgress >= 100) {
                canvas.drawOval(mIndicatorRect, mPaint);
            } else if (mProgress > 0) {
                canvas.drawArc(mIndicatorRect, -90, mProgress * 3.6f, false, mPaint);
            }
        } else {
            iconScale = 1;
        }

        canvas.save();
        canvas.scale(iconScale, iconScale, r.exactCenterX(), r.exactCenterY());
        mIcon.draw(canvas);
        canvas.restore();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void setAlpha(int alpha) {
        mIcon.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mIcon.setColorFilter(cf);
    }

    @Override
    protected boolean onLevelChange(int level) {
        mProgress = level;

        // Stop Animation
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
        mAnimationProgress = ANIMATION_PROGRESS_STOPPED;
        if (level > 0) {
            // Set the paint color only when the level changes, so that the dominant color
            // is only calculated when needed.
            mPaint.setColor(getIndicatorColor());
        }
        if (mIcon instanceof FastBitmapDrawable) {
            ((FastBitmapDrawable) mIcon).setGhostModeEnabled(level <= 0);
        }

        invalidateSelf();
        return true;
    }

    /**
     * Runs the finish animation if it is has not been run after last level change.
     */
    public void maybePerformFinishedAnimation() {
        if (mAnimationProgress > ANIMATION_PROGRESS_STOPPED) {
            return;
        }
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        setAnimationProgress(ANIMATION_PROGRESS_STARTED);
        mAnimator = ObjectAnimator.ofFloat(this, "animationProgress",
                ANIMATION_PROGRESS_STARTED, ANIMATION_PROGRESS_COMPLETED);
        mAnimator.start();
    }

    public void setAnimationProgress(float progress) {
        if (progress != mAnimationProgress) {
            mAnimationProgress = progress;
            invalidateSelf();
        }
    }

    public float getAnimationProgress() {
        return mAnimationProgress;
    }

    @Override
    public int getIntrinsicHeight() {
        return mIcon.getIntrinsicHeight();
    }

    @Override
    public int getIntrinsicWidth() {
        return mIcon.getIntrinsicWidth();
    }

    private int getIndicatorColor() {
        if (mIndicatorColor != 0) {
            return mIndicatorColor;
        }
        if (!(mIcon instanceof FastBitmapDrawable)) {
            mIndicatorColor = DEFAULT_COLOR;
            return mIndicatorColor;
        }
        mIndicatorColor = Utilities.findDominantColorByHue(
                ((FastBitmapDrawable) mIcon).getBitmap(), 20);

        // Make sure that the dominant color has enough saturation to be visible properly.
        float[] hsv = new float[3];
        Color.colorToHSV(mIndicatorColor, hsv);
        if (hsv[1] < MIN_SATUNATION) {
            mIndicatorColor = DEFAULT_COLOR;
            return mIndicatorColor;
        }
        hsv[2] = Math.max(MIN_LIGHTNESS, hsv[2]);
        mIndicatorColor = Color.HSVToColor(hsv);
        return mIndicatorColor;
    }
}

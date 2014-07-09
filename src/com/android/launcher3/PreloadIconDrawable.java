package com.android.launcher3;

import android.animation.ObjectAnimator;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

class PreloadIconDrawable extends Drawable {
    private static final float ANIMATION_PROGRESS_STOPPED = -1.0f;
    private static final float ANIMATION_PROGRESS_STARTED = 0f;
    private static final float ANIMATION_PROGRESS_COMPLETED = 1.0f;

    private static final float ICON_SCALE_FACTOR = 0.6f;

    private static Bitmap sProgressBg, sProgressFill;

    private final Rect mCanvasClipRect = new Rect();
    private final RectF mRect = new RectF();
    private final Path mProgressPath = new Path();
    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    final Drawable mIcon;

    /**
     * Indicates the progress of the preloader [0-100]. If it goes above 100, only the icon
     * is shown with no progress bar.
     */
    private int mProgress = 0;
    private boolean mPathChanged;

    private float mAnimationProgress = ANIMATION_PROGRESS_STOPPED;
    private ObjectAnimator mAnimator;

    public PreloadIconDrawable(Drawable icon, Resources res) {
        mIcon = icon;

        setBounds(icon.getBounds());
        mPathChanged = false;

        if (sProgressBg == null) {
            sProgressBg = BitmapFactory.decodeResource(res, R.drawable.bg_preloader);
        }
        if (sProgressFill == null) {
            sProgressFill = BitmapFactory.decodeResource(res, R.drawable.bg_preloader_progress);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        final Rect r = getBounds();
        if (canvas.getClipBounds(mCanvasClipRect) && !Rect.intersects(mCanvasClipRect, r)) {
            // The draw region has been clipped.
            return;
        }
        final float iconScale;

        if ((mAnimationProgress >= ANIMATION_PROGRESS_STARTED)
                && (mAnimationProgress < ANIMATION_PROGRESS_COMPLETED)) {
            mPaint.setAlpha((int) ((1 - mAnimationProgress) * 255));
            canvas.drawBitmap(sProgressBg, null, r, mPaint);
            canvas.drawBitmap(sProgressFill, null, r, mPaint);
            iconScale = ICON_SCALE_FACTOR + (1 - ICON_SCALE_FACTOR) * mAnimationProgress;

        } else if (mAnimationProgress == ANIMATION_PROGRESS_STOPPED) {
            mPaint.setAlpha(255);
            iconScale = ICON_SCALE_FACTOR;
            canvas.drawBitmap(sProgressBg, null, r, mPaint);

            if (mProgress >= 100) {
                canvas.drawBitmap(sProgressFill, null, r, mPaint);
            } else if (mProgress > 0) {
                if (mPathChanged) {
                    mProgressPath.reset();
                    mProgressPath.moveTo(r.exactCenterX(), r.centerY());

                    mRect.set(r);
                    mProgressPath.arcTo(mRect, -90, mProgress * 3.6f);
                    mProgressPath.close();
                    mPathChanged = false;
                }

                canvas.save();
                canvas.clipPath(mProgressPath);
                canvas.drawBitmap(sProgressFill, null, r, mPaint);
                canvas.restore();
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
    protected void onBoundsChange(Rect bounds) {
        mIcon.setBounds(bounds);
        mPathChanged = true;
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
        mPathChanged = true;

        // Stop Animation
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
        mAnimationProgress = ANIMATION_PROGRESS_STOPPED;

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
}

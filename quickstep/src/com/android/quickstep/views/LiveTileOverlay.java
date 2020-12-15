package com.android.quickstep.views;

import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;
import static com.android.launcher3.anim.Interpolators.LINEAR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.FloatProperty;
import android.view.ViewOverlay;

import com.android.launcher3.anim.Interpolators;
import com.android.quickstep.util.RecentsOrientedState.SurfaceRotation;

public class LiveTileOverlay extends Drawable {

    private static final long ICON_ANIM_DURATION = 120;

    private static final FloatProperty<LiveTileOverlay> PROGRESS =
            new FloatProperty<LiveTileOverlay>("progress") {
                @Override
                public void setValue(LiveTileOverlay liveTileOverlay, float progress) {
                    liveTileOverlay.setIconAnimationProgress(progress);
                }

                @Override
                public Float get(LiveTileOverlay liveTileOverlay) {
                    return liveTileOverlay.mIconAnimationProgress;
                }
            };

    public static final LiveTileOverlay INSTANCE = new LiveTileOverlay();

    private final Paint mPaint = new Paint();
    private final RectF mCurrentRect = new RectF();
    private final Rect mBoundsRect = new Rect();

    private @SurfaceRotation int mRotation = ROTATION_0;

    private float mCornerRadius;
    private Drawable mIcon;
    private Animator mIconAnimator;

    private float mIconAnimationProgress = 0f;
    private boolean mIsAttached;

    private LiveTileOverlay() {
        mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    public void update(RectF currentRect, float cornerRadius) {
        invalidateSelf();

        mCurrentRect.set(currentRect);
        mCornerRadius = cornerRadius;

        mCurrentRect.roundOut(mBoundsRect);
        setBounds(mBoundsRect);
        invalidateSelf();
    }

    public void update(float left, float top, float right, float bottom) {
        mCurrentRect.set(left, top, right, bottom);
    }

    public void setRotation(@SurfaceRotation int rotation) {
        mRotation = rotation;
    }

    public void setIcon(Drawable icon) {
        mIcon = icon;
    }

    public void startIconAnimation() {
        if (mIconAnimator != null) {
            mIconAnimator.cancel();
        }
        // This animator must match the icon part of {@link TaskView#FOCUS_TRANSITION} animation.
        mIconAnimator = ObjectAnimator.ofFloat(this, PROGRESS, 1);
        mIconAnimator.setDuration(ICON_ANIM_DURATION).setInterpolator(LINEAR);
        mIconAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mIconAnimator = null;
            }
        });
        mIconAnimator.start();
    }

    public float cancelIconAnimation() {
        if (mIconAnimator != null) {
            mIconAnimator.cancel();
        }
        return mIconAnimationProgress;
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawRoundRect(mCurrentRect, mCornerRadius, mCornerRadius, mPaint);
        if (mIcon != null && mIconAnimationProgress > 0f) {
            canvas.save();
            float scale = Interpolators.clampToProgress(FAST_OUT_SLOW_IN, 0f,
                    1f).getInterpolation(mIconAnimationProgress);

            int iconRadius = mIcon.getBounds().width() / 2;
            float dx = 0;
            float dy = 0;

            switch (mRotation) {
                case ROTATION_0:
                    dx = mCurrentRect.centerX() - iconRadius * scale;
                    dy = mCurrentRect.top - iconRadius * scale;
                    break;
                case ROTATION_90:
                    dx = mCurrentRect.right - iconRadius * scale;
                    dy = mCurrentRect.centerY() - iconRadius * scale;
                    break;
                case ROTATION_270:
                    dx = mCurrentRect.left - iconRadius * scale;
                    dy = mCurrentRect.centerY() - iconRadius * scale;
                    break;
                case ROTATION_180:
                    dx = mCurrentRect.centerX() - iconRadius * scale;
                    dy = mCurrentRect.bottom - iconRadius * scale;
                    break;
            }

            int rotationDegrees = mRotation * 90;
            if (mRotation == ROTATION_90 || mRotation == ROTATION_270) {
                canvas.rotate(rotationDegrees, dx + iconRadius, dy + iconRadius);
            }
            canvas.translate(dx, dy);
            canvas.scale(scale, scale);
            mIcon.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    public void setAlpha(int i) { }

    @Override
    public void setColorFilter(ColorFilter colorFilter) { }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    public boolean attach(ViewOverlay overlay) {
        if (overlay != null && !mIsAttached) {
            overlay.add(this);
            mIsAttached = true;
            return true;
        }

        return false;
    }

    public void detach(ViewOverlay overlay) {
        if (overlay != null) {
            overlay.remove(this);
            mIsAttached = false;
        }
    }

    private void setIconAnimationProgress(float progress) {
        mIconAnimationProgress = progress;
        invalidateSelf();
    }
}

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


package com.android.launcher3.graphics;

import static com.android.launcher3.graphics.IconShape.DEFAULT_PATH_SIZE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Rect;
import android.util.Property;
import android.util.SparseArray;

import com.android.launcher3.FastBitmapDrawable;
import com.android.launcher3.ItemInfoWithIcon;
import com.android.launcher3.anim.Interpolators;

import java.lang.ref.WeakReference;

/**
 * Extension of {@link FastBitmapDrawable} which shows a progress bar around the icon.
 */
public class PreloadIconDrawable extends FastBitmapDrawable {

    private static final Property<PreloadIconDrawable, Float> INTERNAL_STATE =
            new Property<PreloadIconDrawable, Float>(Float.TYPE, "internalStateProgress") {
                @Override
                public Float get(PreloadIconDrawable object) {
                    return object.mInternalStateProgress;
                }

                @Override
                public void set(PreloadIconDrawable object, Float value) {
                    object.setInternalProgress(value);
                }
            };

    private static final float PROGRESS_WIDTH = 7;
    private static final float PROGRESS_GAP = 2;
    private static final int MAX_PAINT_ALPHA = 255;

    private static final long DURATION_SCALE = 500;

    // The smaller the number, the faster the animation would be.
    // Duration = COMPLETE_ANIM_FRACTION * DURATION_SCALE
    private static final float COMPLETE_ANIM_FRACTION = 0.3f;

    private static final int COLOR_TRACK = 0x77EEEEEE;
    private static final int COLOR_SHADOW = 0x55000000;

    private static final float SMALL_SCALE = 0.6f;

    private static final SparseArray<WeakReference<Bitmap>> sShadowCache = new SparseArray<>();

    private final Matrix mTmpMatrix = new Matrix();
    private final PathMeasure mPathMeasure = new PathMeasure();

    private final ItemInfoWithIcon mItem;

    // Path in [0, 100] bounds.
    private final Path mProgressPath;

    private final Path mScaledTrackPath;
    private final Path mScaledProgressPath;
    private final Paint mProgressPaint;

    private Bitmap mShadowBitmap;
    private final int mIndicatorColor;

    private int mTrackAlpha;
    private float mTrackLength;
    private float mIconScale;

    private boolean mRanFinishAnimation;

    // Progress of the internal state. [0, 1] indicates the fraction of completed progress,
    // [1, (1 + COMPLETE_ANIM_FRACTION)] indicates the progress of zoom animation.
    private float mInternalStateProgress;

    private ObjectAnimator mCurrentAnim;

    /**
     * @param progressPath fixed path in the bounds [0, 0, 100, 100] representing a progress bar.
     */
    public PreloadIconDrawable(ItemInfoWithIcon info, Path progressPath, Context context) {
        super(info);
        mItem = info;
        mProgressPath = progressPath;
        mScaledTrackPath = new Path();
        mScaledProgressPath = new Path();

        mProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        mProgressPaint.setStyle(Paint.Style.STROKE);
        mProgressPaint.setStrokeCap(Paint.Cap.ROUND);
        mIndicatorColor = IconPalette.getPreloadProgressColor(context, mIconColor);

        setInternalProgress(0);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        mTmpMatrix.setScale(
                (bounds.width() - 2 * PROGRESS_WIDTH - 2 * PROGRESS_GAP) / DEFAULT_PATH_SIZE,
                (bounds.height() - 2 * PROGRESS_WIDTH - 2 * PROGRESS_GAP) / DEFAULT_PATH_SIZE);
        mTmpMatrix.postTranslate(
                bounds.left + PROGRESS_WIDTH + PROGRESS_GAP,
                bounds.top + PROGRESS_WIDTH + PROGRESS_GAP);

        mProgressPath.transform(mTmpMatrix, mScaledTrackPath);
        float scale = bounds.width() / DEFAULT_PATH_SIZE;
        mProgressPaint.setStrokeWidth(PROGRESS_WIDTH * scale);

        mShadowBitmap = getShadowBitmap(bounds.width(), bounds.height(),
                (PROGRESS_GAP ) * scale);
        mPathMeasure.setPath(mScaledTrackPath, true);
        mTrackLength = mPathMeasure.getLength();

        setInternalProgress(mInternalStateProgress);
    }

    private Bitmap getShadowBitmap(int width, int height, float shadowRadius) {
        int key = (width << 16) | height;
        WeakReference<Bitmap> shadowRef = sShadowCache.get(key);
        Bitmap shadow = shadowRef != null ? shadowRef.get() : null;
        if (shadow != null) {
            return shadow;
        }
        shadow = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(shadow);
        mProgressPaint.setShadowLayer(shadowRadius, 0, 0, COLOR_SHADOW);
        mProgressPaint.setColor(COLOR_TRACK);
        mProgressPaint.setAlpha(MAX_PAINT_ALPHA);
        c.drawPath(mScaledTrackPath, mProgressPaint);
        mProgressPaint.clearShadowLayer();
        c.setBitmap(null);

        sShadowCache.put(key, new WeakReference<>(shadow));
        return shadow;
    }

    @Override
    public void drawInternal(Canvas canvas, Rect bounds) {
        if (mRanFinishAnimation) {
            super.drawInternal(canvas, bounds);
            return;
        }

        // Draw track.
        mProgressPaint.setColor(mIndicatorColor);
        mProgressPaint.setAlpha(mTrackAlpha);
        if (mShadowBitmap != null) {
            canvas.drawBitmap(mShadowBitmap, bounds.left, bounds.top, mProgressPaint);
        }
        canvas.drawPath(mScaledProgressPath, mProgressPaint);

        int saveCount = canvas.save();
        canvas.scale(mIconScale, mIconScale, bounds.exactCenterX(), bounds.exactCenterY());
        super.drawInternal(canvas, bounds);
        canvas.restoreToCount(saveCount);
    }

    /**
     * Updates the install progress based on the level
     */
    @Override
    protected boolean onLevelChange(int level) {
        // Run the animation if we have already been bound.
        updateInternalState(level * 0.01f,  getBounds().width() > 0, false);
        return true;
    }

    /**
     * Runs the finish animation if it is has not been run after last call to
     * {@link #onLevelChange}
     */
    public void maybePerformFinishedAnimation() {
        // If the drawable was recently initialized, skip the progress animation.
        if (mInternalStateProgress == 0) {
            mInternalStateProgress = 1;
        }
        updateInternalState(1 + COMPLETE_ANIM_FRACTION, true, true);
    }

    public boolean hasNotCompleted() {
        return !mRanFinishAnimation;
    }

    private void updateInternalState(float finalProgress, boolean shouldAnimate, boolean isFinish) {
        if (mCurrentAnim != null) {
            mCurrentAnim.cancel();
            mCurrentAnim = null;
        }

        if (Float.compare(finalProgress, mInternalStateProgress) == 0) {
            return;
        }
        if (finalProgress < mInternalStateProgress) {
            shouldAnimate = false;
        }
        if (!shouldAnimate || mRanFinishAnimation) {
            setInternalProgress(finalProgress);
        } else {
            mCurrentAnim = ObjectAnimator.ofFloat(this, INTERNAL_STATE, finalProgress);
            mCurrentAnim.setDuration(
                    (long) ((finalProgress - mInternalStateProgress) * DURATION_SCALE));
            mCurrentAnim.setInterpolator(Interpolators.LINEAR);
            if (isFinish) {
                mCurrentAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mRanFinishAnimation = true;
                    }
                });
            }
            mCurrentAnim.start();
        }
    }

    /**
     * Sets the internal progress and updates the UI accordingly
     *   for progress <= 0:
     *     - icon in the small scale and disabled state
     *     - progress track is visible
     *     - progress bar is not visible
     *   for 0 < progress < 1
     *     - icon in the small scale and disabled state
     *     - progress track is visible
     *     - progress bar is visible with dominant color. Progress bar is drawn as a fraction of
     *       {@link #mScaledTrackPath}.
     *       @see PathMeasure#getSegment(float, float, Path, boolean)
     *   for 1 <= progress < (1 + COMPLETE_ANIM_FRACTION)
     *     - we calculate fraction of progress in the above range
     *     - progress track is drawn with alpha based on fraction
     *     - progress bar is drawn at 100% with alpha based on fraction
     *     - icon is scaled up based on fraction and is drawn in enabled state
     *   for progress >= (1 + COMPLETE_ANIM_FRACTION)
     *     - only icon is drawn in normal state
     */
    private void setInternalProgress(float progress) {
        mInternalStateProgress = progress;
        if (progress <= 0) {
            mIconScale = SMALL_SCALE;
            mScaledTrackPath.reset();
            mTrackAlpha = MAX_PAINT_ALPHA;
            setIsDisabled(true);
        }

        if (progress < 1 && progress > 0) {
            mPathMeasure.getSegment(0, progress * mTrackLength, mScaledProgressPath, true);
            mIconScale = SMALL_SCALE;
            mTrackAlpha = MAX_PAINT_ALPHA;
            setIsDisabled(true);
        } else if (progress >= 1) {
            setIsDisabled(mItem.isDisabled());
            mScaledTrackPath.set(mScaledProgressPath);
            float fraction = (progress - 1) / COMPLETE_ANIM_FRACTION;

            if (fraction >= 1) {
                // Animation has completed
                mIconScale = 1;
                mTrackAlpha = 0;
            } else {
                mTrackAlpha = Math.round((1 - fraction) * MAX_PAINT_ALPHA);
                mIconScale = SMALL_SCALE + (1 - SMALL_SCALE) * fraction;
            }
        }
        invalidateSelf();
    }
}

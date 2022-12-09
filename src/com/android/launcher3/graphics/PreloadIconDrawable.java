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

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.GraphicsUtils;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.util.Themes;

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

    private static final int DEFAULT_PATH_SIZE = 100;
    private static final int MAX_PAINT_ALPHA = 255;
    private static final int TRACK_ALPHA = (int) (0.27f * MAX_PAINT_ALPHA);
    private static final int DISABLED_ICON_ALPHA = (int) (0.6f * MAX_PAINT_ALPHA);

    private static final long DURATION_SCALE = 500;

    // The smaller the number, the faster the animation would be.
    // Duration = COMPLETE_ANIM_FRACTION * DURATION_SCALE
    private static final float COMPLETE_ANIM_FRACTION = 0.3f;

    private static final float SMALL_SCALE = 0.7f;
    private static final float PROGRESS_STROKE_SCALE = 0.075f;

    private static final int PRELOAD_ACCENT_COLOR_INDEX = 0;
    private static final int PRELOAD_BACKGROUND_COLOR_INDEX = 1;

    private final Matrix mTmpMatrix = new Matrix();
    private final PathMeasure mPathMeasure = new PathMeasure();

    private final ItemInfoWithIcon mItem;

    // Path in [0, 100] bounds.
    private final Path mShapePath;

    private final Path mScaledTrackPath;
    private final Path mScaledProgressPath;
    private final Paint mProgressPaint;

    private final int mIndicatorColor;
    private final int mSystemAccentColor;
    private final int mSystemBackgroundColor;
    private final boolean mIsDarkMode;

    private int mTrackAlpha;
    private float mTrackLength;
    private float mIconScale;

    private boolean mRanFinishAnimation;

    // Progress of the internal state. [0, 1] indicates the fraction of completed progress,
    // [1, (1 + COMPLETE_ANIM_FRACTION)] indicates the progress of zoom animation.
    private float mInternalStateProgress;

    private ObjectAnimator mCurrentAnim;

    private boolean mIsStartable;

    public PreloadIconDrawable(ItemInfoWithIcon info, Context context) {
        this(
                info,
                IconPalette.getPreloadProgressColor(context, info.bitmap.color),
                getPreloadColors(context),
                Utilities.isDarkTheme(context));
    }

    public PreloadIconDrawable(
            ItemInfoWithIcon info,
            int indicatorColor,
            int[] preloadColors,
            boolean isDarkMode) {
        super(info.bitmap);
        mItem = info;
        mShapePath = GraphicsUtils.getShapePath(DEFAULT_PATH_SIZE);
        mScaledTrackPath = new Path();
        mScaledProgressPath = new Path();

        mProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        mProgressPaint.setStrokeCap(Paint.Cap.ROUND);
        mIndicatorColor = indicatorColor;

        mSystemAccentColor = preloadColors[PRELOAD_ACCENT_COLOR_INDEX];
        mSystemBackgroundColor = preloadColors[PRELOAD_BACKGROUND_COLOR_INDEX];
        mIsDarkMode = isDarkMode;

        setLevel(info.getProgressLevel());
        setIsStartable(info.isAppStartable());
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);

        float progressWidth = PROGRESS_STROKE_SCALE * bounds.width();
        mTmpMatrix.setScale(
                (bounds.width() - 2 * progressWidth) / DEFAULT_PATH_SIZE,
                (bounds.height() - 2 * progressWidth) / DEFAULT_PATH_SIZE);
        mTmpMatrix.postTranslate(bounds.left + progressWidth, bounds.top + progressWidth);

        mShapePath.transform(mTmpMatrix, mScaledTrackPath);
        mProgressPaint.setStrokeWidth(progressWidth);

        mPathMeasure.setPath(mScaledTrackPath, true);
        mTrackLength = mPathMeasure.getLength();

        setInternalProgress(mInternalStateProgress);
    }

    @Override
    public void drawInternal(Canvas canvas, Rect bounds) {
        if (mRanFinishAnimation) {
            super.drawInternal(canvas, bounds);
            return;
        }

        // Draw background.
        mProgressPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mProgressPaint.setColor(mSystemBackgroundColor);
        canvas.drawPath(mScaledTrackPath, mProgressPaint);

        // Draw track and progress.
        mProgressPaint.setStyle(Paint.Style.STROKE);
        mProgressPaint.setColor(mIsStartable ? mIndicatorColor : mSystemAccentColor);
        mProgressPaint.setAlpha(TRACK_ALPHA);
        canvas.drawPath(mScaledTrackPath, mProgressPaint);
        mProgressPaint.setAlpha(mTrackAlpha);
        canvas.drawPath(mScaledProgressPath, mProgressPaint);

        int saveCount = canvas.save();
        canvas.scale(mIconScale, mIconScale, bounds.exactCenterX(), bounds.exactCenterY());
        super.drawInternal(canvas, bounds);
        canvas.restoreToCount(saveCount);
    }

    @Override
    protected void updateFilter() {
        setAlpha(mIsDisabled ? DISABLED_ICON_ALPHA : MAX_PAINT_ALPHA);
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

    /** Sets whether this icon should display the startable app UI. */
    public void setIsStartable(boolean isStartable) {
        if (mIsStartable != isStartable) {
            mIsStartable = isStartable;
            setIsDisabled(!isStartable);
        }
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
        }

        if (progress < 1 && progress > 0) {
            mPathMeasure.getSegment(0, progress * mTrackLength, mScaledProgressPath, true);
            mIconScale = SMALL_SCALE;
            mTrackAlpha = MAX_PAINT_ALPHA;
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

    private static int[] getPreloadColors(Context context) {
        int[] preloadColors = new int[2];

        preloadColors[PRELOAD_ACCENT_COLOR_INDEX] = Themes.getAttrColor(context,
                R.attr.preloadIconAccentColor);
        preloadColors[PRELOAD_BACKGROUND_COLOR_INDEX] = Themes.getAttrColor(context,
                R.attr.preloadIconBackgroundColor);

        return preloadColors;
    }

    /**
     * Returns a FastBitmapDrawable with the icon.
     */
    public static PreloadIconDrawable newPendingIcon(Context context, ItemInfoWithIcon info) {
        return new PreloadIconDrawable(info, context);
    }

    @Override
    public FastBitmapConstantState newConstantState() {
        return new PreloadIconConstantState(
                mBitmap,
                mIconColor,
                mItem,
                mIndicatorColor,
                new int[] {mSystemAccentColor, mSystemBackgroundColor},
                mIsDarkMode);
    }

    protected static class PreloadIconConstantState extends FastBitmapConstantState {

        protected final ItemInfoWithIcon mInfo;
        protected final int mIndicatorColor;
        protected final int[] mPreloadColors;
        protected final boolean mIsDarkMode;
        protected final int mLevel;

        public PreloadIconConstantState(
                Bitmap bitmap,
                int iconColor,
                ItemInfoWithIcon info,
                int indicatorColor,
                int[] preloadColors,
                boolean isDarkMode) {
            super(bitmap, iconColor);
            mInfo = info;
            mIndicatorColor = indicatorColor;
            mPreloadColors = preloadColors;
            mIsDarkMode = isDarkMode;
            mLevel = info.getProgressLevel();
        }

        @Override
        public PreloadIconDrawable createDrawable() {
            return new PreloadIconDrawable(
                    mInfo,
                    mIndicatorColor,
                    mPreloadColors,
                    mIsDarkMode);
        }
    }
}

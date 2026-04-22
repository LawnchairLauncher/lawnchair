/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.startingsurface;

import static android.os.Trace.TRACE_TAG_WINDOW_MANAGER;

import android.animation.AnimatorListenerAdapter;
import android.annotation.ColorInt;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Trace;
import android.util.Log;
import android.util.PathParser;
import android.window.SplashScreenView;

import com.android.internal.R;

import java.io.Closeable;
import java.util.function.LongConsumer;

/**
 * Creating a lightweight Drawable object used for splash screen.
 *
 * @hide
 */
public class SplashscreenIconDrawableFactory {

    private static final String TAG = StartingWindowController.TAG;

    /**
     * @return An array containing the foreground drawable at index 0 and if needed a background
     * drawable at index 1.
     */
    static Drawable[] makeIconDrawable(@ColorInt int backgroundColor, @ColorInt int themeColor,
            @NonNull Drawable foregroundDrawable, int srcIconSize, int iconSize,
            boolean loadInDetail, Handler preDrawHandler) {
        Drawable foreground;
        Drawable background = null;
        boolean drawBackground =
                backgroundColor != Color.TRANSPARENT && backgroundColor != themeColor;

        if (foregroundDrawable instanceof Animatable) {
            foreground = new AnimatableIconAnimateListener(foregroundDrawable);
        } else if (foregroundDrawable instanceof AdaptiveIconDrawable) {
            // If the icon is Adaptive, we already use the icon background.
            drawBackground = false;
            foreground = new ImmobileIconDrawable(foregroundDrawable,
                    srcIconSize, iconSize, loadInDetail, preDrawHandler);
        } else {
            // Adaptive icon don't handle transparency so we draw the background of the adaptive
            // icon with the same color as the window background color instead of using two layers
            foreground = new ImmobileIconDrawable(
                    new AdaptiveForegroundDrawable(foregroundDrawable),
                    srcIconSize, iconSize, loadInDetail, preDrawHandler);
        }

        if (drawBackground) {
            background = new MaskBackgroundDrawable(backgroundColor);
        }

        return new Drawable[]{foreground, background};
    }

    static Drawable[] makeLegacyIconDrawable(@NonNull Drawable iconDrawable, int srcIconSize,
            int iconSize, boolean loadInDetail, Handler preDrawHandler) {
        return new Drawable[]{new ImmobileIconDrawable(iconDrawable, srcIconSize, iconSize,
                loadInDetail, preDrawHandler)};
    }

    /**
     * Drawable pre-drawing the scaled icon in a separate thread to increase the speed of the
     * final drawing.
     */
    private static class ImmobileIconDrawable extends Drawable implements Closeable {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG
                | Paint.FILTER_BITMAP_FLAG);
        private final Matrix mMatrix = new Matrix();
        private Bitmap mIconBitmap;

        ImmobileIconDrawable(Drawable drawable, int srcIconSize, int iconSize, boolean loadInDetail,
                Handler preDrawHandler) {
            // This icon has lower density, don't scale it.
            if (loadInDetail) {
                preDrawHandler.post(() -> preDrawIcon(drawable, iconSize));
            } else {
                final float scale = (float) iconSize / srcIconSize;
                mMatrix.setScale(scale, scale);
                preDrawHandler.post(() -> preDrawIcon(drawable, srcIconSize));
            }
        }

        private void preDrawIcon(Drawable drawable, int size) {
            synchronized (mPaint) {
                Trace.traceBegin(TRACE_TAG_WINDOW_MANAGER, "preDrawIcon");
                mIconBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                final Canvas canvas = new Canvas(mIconBitmap);
                drawable.setBounds(0, 0, size, size);
                drawable.draw(canvas);
                Trace.traceEnd(TRACE_TAG_WINDOW_MANAGER);
            }
        }

        @Override
        public void draw(Canvas canvas) {
            synchronized (mPaint) {
                if (mIconBitmap != null) {
                    canvas.drawBitmap(mIconBitmap, mMatrix, mPaint);
                } else {
                    // this shouldn't happen, but if it really happen, invalidate self to wait
                    // for bitmap to be ready.
                    invalidateSelf();
                }
            }
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return 1;
        }

        @Override
        public void close() {
            synchronized (mPaint) {
                if (mIconBitmap != null) {
                    mIconBitmap.recycle();
                    mIconBitmap = null;
                }
            }
        }
    }

    /**
     * Base class the draw a background clipped by the system mask.
     */
    public static class MaskBackgroundDrawable extends Drawable {
        private static final float MASK_SIZE = AdaptiveIconDrawable.MASK_SIZE;
        private static final float EXTRA_INSET_PERCENTAGE = 1 / 4f;
        static final float DEFAULT_VIEW_PORT_SCALE = 1f / (1 + 2 * EXTRA_INSET_PERCENTAGE);
        /**
         * Clip path defined in R.string.config_icon_mask.
         */
        private static Path sMask;
        private final Path mMaskScaleOnly;
        private final Matrix mMaskMatrix;

        @Nullable
        private final Paint mBackgroundPaint;

        public MaskBackgroundDrawable(@ColorInt int backgroundColor) {
            final Resources r = Resources.getSystem();
            sMask = PathParser.createPathFromPathData(r.getString(R.string.config_icon_mask));
            Path mask = new Path(sMask);
            mMaskScaleOnly = new Path(mask);
            mMaskMatrix = new Matrix();
            if (backgroundColor != Color.TRANSPARENT) {
                mBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG
                        | Paint.FILTER_BITMAP_FLAG);
                mBackgroundPaint.setColor(backgroundColor);
                mBackgroundPaint.setStyle(Paint.Style.FILL);
            } else {
                mBackgroundPaint = null;
            }
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            if (bounds.isEmpty()) {
                return;
            }
            updateLayerBounds(bounds);
        }

        protected void updateLayerBounds(Rect bounds) {
            // reset everything that depends on the view bounds
            mMaskMatrix.setScale(bounds.width() / MASK_SIZE, bounds.height() / MASK_SIZE);
            sMask.transform(mMaskMatrix, mMaskScaleOnly);
        }

        @Override
        public void draw(Canvas canvas) {
            canvas.clipPath(mMaskScaleOnly);
            if (mBackgroundPaint != null) {
                canvas.drawPath(mMaskScaleOnly, mBackgroundPaint);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            if (mBackgroundPaint != null) {
                mBackgroundPaint.setAlpha(alpha);
            }
        }

        @Override
        public int getOpacity() {
            return PixelFormat.RGBA_8888;
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }
    }

    private static class AdaptiveForegroundDrawable extends MaskBackgroundDrawable {

        @NonNull
        protected final Drawable mForegroundDrawable;
        private final Rect mTmpOutRect = new Rect();

        AdaptiveForegroundDrawable(@NonNull Drawable foregroundDrawable) {
            super(Color.TRANSPARENT);
            mForegroundDrawable = foregroundDrawable;
        }

        @Override
        protected void updateLayerBounds(Rect bounds) {
            super.updateLayerBounds(bounds);
            int cX = bounds.width() / 2;
            int cY = bounds.height() / 2;

            int insetWidth = (int) (bounds.width() / (DEFAULT_VIEW_PORT_SCALE * 2));
            int insetHeight = (int) (bounds.height() / (DEFAULT_VIEW_PORT_SCALE * 2));
            final Rect outRect = mTmpOutRect;
            outRect.set(cX - insetWidth, cY - insetHeight, cX + insetWidth, cY + insetHeight);
            if (mForegroundDrawable != null) {
                mForegroundDrawable.setBounds(outRect);
            }
            invalidateSelf();
        }

        @Override
        public void draw(Canvas canvas) {
            super.draw(canvas);
            mForegroundDrawable.draw(canvas);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            mForegroundDrawable.setColorFilter(colorFilter);
        }
    }

    /**
     * A lightweight AdaptiveIconDrawable which support foreground to be Animatable, and keep this
     * drawable masked by config_icon_mask.
     */
    public static class AnimatableIconAnimateListener extends AdaptiveForegroundDrawable
            implements SplashScreenView.IconAnimateListener {
        private final Animatable mAnimatableIcon;
        private boolean mAnimationTriggered;
        private AnimatorListenerAdapter mJankMonitoringListener;
        private boolean mRunning;
        private LongConsumer mStartListener;

        AnimatableIconAnimateListener(@NonNull Drawable foregroundDrawable) {
            super(foregroundDrawable);
            Callback callback = new Callback() {
                @Override
                public void invalidateDrawable(@NonNull Drawable who) {
                    invalidateSelf();
                }

                @Override
                public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what,
                        long when) {
                    scheduleSelf(what, when);
                }

                @Override
                public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
                    unscheduleSelf(what);
                }
            };
            mForegroundDrawable.setCallback(callback);
            mAnimatableIcon = (Animatable) mForegroundDrawable;
        }

        @Override
        public void setAnimationJankMonitoring(AnimatorListenerAdapter listener) {
            mJankMonitoringListener = listener;
        }

        @Override
        public void prepareAnimate(LongConsumer startListener) {
            stopAnimation();
            mStartListener = startListener;
        }

        private void startAnimation() {
            if (mJankMonitoringListener != null) {
                mJankMonitoringListener.onAnimationStart(null);
            }
            try {
                mAnimatableIcon.start();
            } catch (Exception ex) {
                Log.e(TAG, "Error while running the splash screen animated icon", ex);
                mRunning = false;
                if (mJankMonitoringListener != null) {
                    mJankMonitoringListener.onAnimationCancel(null);
                }
                if (mStartListener != null) {
                    mStartListener.accept(0);
                }
                return;
            }
            long animDuration = 0;
            if (mAnimatableIcon instanceof AnimatedVectorDrawable
                    && ((AnimatedVectorDrawable) mAnimatableIcon).getTotalDuration() > 0) {
                animDuration = ((AnimatedVectorDrawable) mAnimatableIcon).getTotalDuration();
            } else if (mAnimatableIcon instanceof AnimationDrawable
                    && ((AnimationDrawable) mAnimatableIcon).getTotalDuration() > 0) {
                animDuration = ((AnimationDrawable) mAnimatableIcon).getTotalDuration();
            }
            mRunning = true;
            if (mStartListener != null) {
                mStartListener.accept(animDuration);
            }
        }

        private void onAnimationEnd() {
            mAnimatableIcon.stop();
            if (mJankMonitoringListener != null) {
                mJankMonitoringListener.onAnimationEnd(null);
            }
            mStartListener = null;
            mRunning = false;
        }

        @Override
        public void stopAnimation() {
            if (mRunning) {
                onAnimationEnd();
                mJankMonitoringListener = null;
            }
        }

        private void ensureAnimationStarted() {
            if (mAnimationTriggered) {
                return;
            }
            if (!mRunning) {
                startAnimation();
            }
            mAnimationTriggered = true;
        }

        @Override
        public void draw(Canvas canvas) {
            ensureAnimationStarted();
            super.draw(canvas);
        }
    }
}

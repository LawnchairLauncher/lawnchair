/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.quickstep.views;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import static com.android.app.animation.Interpolators.ACCELERATE;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.Utilities.mapToRange;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.util.PathParser;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.window.ScreenCaptureInternal;

import androidx.core.content.ContextCompat;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.CancellableTask;

/**
 * A custom ViewGroup that displays a clipped screenshot of the device wallpaper, overlaid with a
 * color scrim.
 *
 * Manages the task of capturing the screenshot and animating the children.
 */
public class WallpaperScreenshotClipView extends FrameLayout {

    private static final String TAG = "WSCV";

    // Arbitrarily large number to avoid rounding issues when tracking progress.
    public static final int CLIP_ANIM_DURATION = 10000;

    private static final float MAX_SCALE_MULTIPLIER = 1.50f;
    private static final int COLOR_ALPHA_DURATION_MS = 25;
    private static final float CLIP_TRANSLATION_MULTIPLIER = 0.55f;
    private static final float INITIAL_ARROW_SCALE = 2.4100475221f;

    private ImageView mWallpaperView;
    private View mColorOverlay;
    public boolean mHasValidScreenshot = false;
    public boolean mForceFallbackAnimation = false;

    private final Path mOriginalPath;
    private Path mCurrentClipPath;
    private final RectF mBounds = new RectF();
    private final RectF mOriginalBounds = new RectF();
    private final Matrix mScaleMatrix = new Matrix();
    private final Matrix mInvertScaleMatrix = new Matrix();

    private float mInitialArrowPositionY;
    // Keeps the arrow centered in the middle
    private float mClipTranslationY;

    // Briefly moves the arrow upwards in the first part of the animation
    private float mClipTranslateYUpwards;
    private final float mMinClipTranslateY;
    private final float mMaxClipTranslateY;
    private final float mMaxArrowScale;
    private final int mWindowWidth;
    private final int mWindowHeight;

    private CancellableTask<ScreenCaptureInternal.ScreenshotHardwareBuffer> mCaptureTask;

    public WallpaperScreenshotClipView(Context context) {
        this(context, null);
    }

    public WallpaperScreenshotClipView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WallpaperScreenshotClipView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public WallpaperScreenshotClipView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        Resources r = getResources();

        mWallpaperView = new ImageView(context);
        mWallpaperView.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));

        mColorOverlay = new View(context);
        mColorOverlay.setLayoutParams(new LayoutParams(MATCH_PARENT, MATCH_PARENT));
        mColorOverlay.setBackgroundColor(context.getColor(R.color.materialColorSecondaryContainer));

        addView(mWallpaperView);
        addView(mColorOverlay);

        mOriginalPath = new Path(PathParser
                .createPathFromPathData(r.getString(R.string.all_set_arrow_path)));
        resetAndSetMatrixScale(INITIAL_ARROW_SCALE);
        mOriginalPath.transform(mScaleMatrix);
        mOriginalPath.computeBounds(mOriginalBounds);

        mCurrentClipPath = new Path(mOriginalPath);
        setClipPath(mCurrentClipPath);

        Rect windowBounds = ContextCompat.getSystemService(context, WindowManager.class)
                .getCurrentWindowMetrics().getBounds();

        mWindowHeight = windowBounds.height();
        mWindowWidth = windowBounds.width();
        mInitialArrowPositionY = mWindowHeight
                - mOriginalBounds.height()
                - r.getDimensionPixelSize(R.dimen.allset_hint_margin_bottom)
                - Utilities.calculateTextHeight(
                        r.getDimensionPixelSize(R.dimen.allset_page_swipe_up_text_size_expressive))
                - r.getDimensionPixelSize(R.dimen.allset_arrow_margin_bottom);


        getViewTreeObserver().addOnWindowAttachListener(
                new ViewTreeObserver.OnWindowAttachListener() {
                    @Override
                    public void onWindowAttached() {
                    }

                    @Override
                    public void onWindowDetached() {
                        if (mCaptureTask != null) {
                            mCaptureTask.cancel();
                            mCaptureTask = null;
                        }
                        getViewTreeObserver().removeOnWindowAttachListener(this);
                    }
                });

        mMinClipTranslateY = Math.max(mOriginalBounds.height(), mOriginalBounds.width());
        mMaxClipTranslateY = Math.max(mWindowHeight, mWindowWidth);

        // Create a large max scale so that the shape can fill the entire screen.
        mMaxArrowScale = Math.max(mWindowHeight, mWindowWidth) * MAX_SCALE_MULTIPLIER
                / Math.min(mOriginalBounds.width(), mOriginalBounds.height());
    }

    private void resetAndSetMatrixScale(float scale) {
        mScaleMatrix.reset();
        mScaleMatrix.setScale(scale, scale);
    }

    private void setClipPath(Path clipPath) {
        mCurrentClipPath = clipPath;
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int count = canvas.save();
        if (mCurrentClipPath != null) {
            mCurrentClipPath.computeBounds(mBounds);
        }

        float transX = (getWidth() - mBounds.width()) / 2;
        float transY = mInitialArrowPositionY
                + mClipTranslationY
                + mClipTranslateYUpwards
                - ((mBounds.height() - mOriginalBounds.height()) / 2);
        if (mCurrentClipPath != null) {
            mCurrentClipPath.offset(transX, transY);
            canvas.clipPath(mCurrentClipPath);
        }
        super.dispatchDraw(canvas);
        canvas.restoreToCount(count);
        if (mCurrentClipPath != null) {
            mCurrentClipPath.offset(-transX, -transY);
        }
    }

    /**
     * Sets the {@code translationY} of the clipped views.
     */
    public void setClipTranslationY(float translationY, float progress) {
        mClipTranslationY = translationY * CLIP_TRANSLATION_MULTIPLIER;
        mClipTranslateYUpwards = -Math.min(mMinClipTranslateY,
                mapToRange(progress, 0, 1, 0, mMaxClipTranslateY, LINEAR));
        invalidate();
    }

    /**
     * Adds clip animation to {@code animatorSet}.
     */
    public void addClipAnimation(AnimatorSet animatorSet) {
        ValueAnimator scale = ValueAnimator.ofFloat(0, 1f);
        scale.setDuration(CLIP_ANIM_DURATION);
        scale.setInterpolator(ACCELERATE);
        scale.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                // undo any previous scale
                mCurrentClipPath.transform(mInvertScaleMatrix);

                // set new scale
                float scale = 1 + (valueAnimator.getAnimatedFraction() * mMaxArrowScale);
                resetAndSetMatrixScale(scale);
                mCurrentClipPath.transform(mScaleMatrix);
                setClipPath(mCurrentClipPath);

                // prepare inversion for next update
                mInvertScaleMatrix.reset();
                mScaleMatrix.invert(mInvertScaleMatrix);

            }
        });
        animatorSet.play(scale);

        ValueAnimator color = ValueAnimator.ofFloat(0, 1f);
        color.setDuration(CLIP_ANIM_DURATION);
        color.setInterpolator(LINEAR);
        color.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                if (!mHasValidScreenshot || mForceFallbackAnimation) {
                    // Do nothing. The entire window will fade out instead.
                } else {
                    float interpProgress = mapToRange(valueAnimator.getAnimatedFraction(), 0, 1f, 0,
                            1f, LINEAR);
                    float maxAlpha = 1f * CLIP_ANIM_DURATION / COLOR_ALPHA_DURATION_MS;
                    float alpha = Math.min(1f,
                            mapToRange(interpProgress, 0, 1, 0, maxAlpha, LINEAR));
                    mColorOverlay.setAlpha(1f - alpha);
                }
            }
        });
        animatorSet.play(color);
    }

    /**
     * Checks if the SurfaceControl is invalid (or null) and releases it if so.
     *
     * @return true if the control was invalid, false otherwise.
     */
    private boolean releaseIfInvalid(SurfaceControl control) {
        if (control == null) {
            return true;
        }
        if (!control.isValid()) {
            control.release();
            return true;
        }
        return false;
    }

    /**
     * Captures a screenshot of the wallpaper.
     */
    public void tryCaptureWallpaperScreenshot(Window window, int displayId, View rootView,
            int wallpaperBlurRadius, Runnable onEndRunnable) {
        Log.d(TAG, "tryCaptureWallpaperScreenshot() called");

        if (mHasValidScreenshot) {
            Log.d(TAG, "setupWallpaperScreenshot return: already have a screenshot");
            onEndRunnable.run();
            return;
        }
        if (WallpaperManager.getInstance(mContext).getWallpaperInfo() != null) {
            // Play fallback animation for live wallpapers.
            Log.d(TAG, "setupWallpaperScreenshot return: wallpaperInfo is null");
            onEndRunnable.run();
            return;
        }
        WindowManagerGlobal windowManagerGlobal = WindowManagerGlobal.getInstance();
        if (windowManagerGlobal == null) {
            Log.d(TAG, "setupWallpaperScreenshot return: windowManagerGlobal is null");
            onEndRunnable.run();
            return;
        }
        SurfaceControl rootSurfaceControl = rootView.getViewRootImpl().getSurfaceControl();
        if (rootSurfaceControl == null) { // We do not release if invalid.
            Log.d(TAG, "setupWallpaperScreenshot return: rootSurfaceControl is null");
            onEndRunnable.run();
            return;
        }
        SurfaceControl wallpaperMirror = windowManagerGlobal.mirrorWallpaperSurface(
                displayId);
        if (releaseIfInvalid(wallpaperMirror)) {
            Log.d(TAG, "setupWallpaperScreenshot return: wallpaperMirror=" + wallpaperMirror);
            onEndRunnable.run();
            return;
        }

        // It's important to set blur to 0 before trying to mirror the surface.
        window.setBackgroundBlurRadius(0);
        SurfaceControl allSetMirror = SurfaceControl.mirrorSurface(rootSurfaceControl);

        if (releaseIfInvalid(allSetMirror)) {
            Log.d(TAG, "setupWallpaperScreenshot return: allSetMirror=" + allSetMirror);
            onEndRunnable.run();
            wallpaperMirror.release();
            return;
        }

        SurfaceControl rootControl = new SurfaceControl.Builder()
                .setName("Wallpaper Screenshot Clip View")
                .build();
        SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()
                .reparent(wallpaperMirror, rootControl)
                .setLayer(wallpaperMirror, -1)
                .reparent(allSetMirror, rootControl)
                .setLayer(allSetMirror, 0);
        Rect captureBounds = new Rect();
        captureBounds.set(0, 0, mWindowWidth, mWindowHeight);
        mCaptureTask = new CancellableTask<>(
                () -> {
                    transaction.apply(true);
                    return ScreenCaptureInternal.captureLayers(rootControl, captureBounds, 1);
                },
                MAIN_EXECUTOR,
                (ScreenCaptureInternal.ScreenshotHardwareBuffer buffer) -> {
                    window.setBackgroundBlurRadius(wallpaperBlurRadius);
                    if (buffer != null) {
                        mWallpaperView.setImageBitmap(buffer.asBitmap());
                        mHasValidScreenshot = true;
                    }
                    Log.d(TAG, "capture callback: mHasValidScreenshot=" + mHasValidScreenshot);
                },
                () -> {
                    onEndRunnable.run();
                    mCaptureTask = null;
                    rootControl.release();
                    allSetMirror.release();
                    wallpaperMirror.release();
                });
        UI_HELPER_EXECUTOR.execute(mCaptureTask);
    }

    /**
     * @param forceFallbackAnimation True if we should play the fallback animation.
     */
    public void setForceFallbackAnimation(boolean forceFallbackAnimation) {
        mForceFallbackAnimation = forceFallbackAnimation;
    }
}

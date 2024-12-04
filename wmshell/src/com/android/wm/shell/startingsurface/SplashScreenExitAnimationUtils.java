/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.view.Choreographer.CALLBACK_COMMIT;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.IntDef;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.MathUtils;
import android.util.Slog;
import android.view.Choreographer;
import android.view.SurfaceControl;
import android.view.SyncRtSurfaceTransactionApplier;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.window.SplashScreenView;

import com.android.wm.shell.animation.Interpolators;
import com.android.wm.shell.common.TransactionPool;

/**
 * Utilities for creating the splash screen window animations.
 * @hide
 */
public class SplashScreenExitAnimationUtils {
    private static final boolean DEBUG_EXIT_ANIMATION = false;
    private static final boolean DEBUG_EXIT_ANIMATION_BLEND = false;
    private static final boolean DEBUG_EXIT_FADE_ANIMATION = false;
    private static final String TAG = "SplashScreenExitAnimationUtils";

    private static final Interpolator ICON_INTERPOLATOR = new PathInterpolator(0.15f, 0f, 1f, 1f);
    private static final Interpolator MASK_RADIUS_INTERPOLATOR =
            new PathInterpolator(0f, 0f, 0.4f, 1f);
    private static final Interpolator SHIFT_UP_INTERPOLATOR = new PathInterpolator(0f, 0f, 0f, 1f);

    /**
     * This splash screen exit animation type uses a radial vanish to hide
     * the starting window and slides up the main window content.
     * @hide
     */
    public static final int TYPE_RADIAL_VANISH_SLIDE_UP = 0;

    /**
     * This splash screen exit animation type fades out the starting window
     * to reveal the main window content.
     * @hide
     */
    public static final int TYPE_FADE_OUT = 1;

    /** @hide */
    @IntDef(prefix = { "TYPE_" }, value = {
            TYPE_RADIAL_VANISH_SLIDE_UP,
            TYPE_FADE_OUT,
    })
    public @interface ExitAnimationType {}

    /**
     * Creates and starts the animator to fade out the icon, reveal the app, and shift up main
     * window with rounded corner radius.
     */
    static void startAnimations(@ExitAnimationType int animationType,
            ViewGroup splashScreenView, SurfaceControl firstWindowSurface,
            int mainWindowShiftLength, TransactionPool transactionPool, Rect firstWindowFrame,
            int animationDuration, int iconFadeOutDuration, float iconStartAlpha,
            float brandingStartAlpha, int appRevealDelay, int appRevealDuration,
            Animator.AnimatorListener animatorListener, float roundedCornerRadius) {
        ValueAnimator animator;
        if (animationType == TYPE_FADE_OUT) {
            animator = createFadeOutAnimation(splashScreenView, animationDuration,
                    iconFadeOutDuration, iconStartAlpha, brandingStartAlpha, appRevealDelay,
                    appRevealDuration, animatorListener);
        } else {
            animator = createRadialVanishSlideUpAnimator(splashScreenView,
                    firstWindowSurface, mainWindowShiftLength, transactionPool, firstWindowFrame,
                    animationDuration, iconFadeOutDuration, iconStartAlpha, brandingStartAlpha,
                    appRevealDelay, appRevealDuration, animatorListener, roundedCornerRadius);
        }
        animator.start();
    }

    /**
     * Creates and starts the animator to fade out the icon, reveal the app, and shift up main
     * window.
     * @hide
     */
    public static void startAnimations(ViewGroup splashScreenView,
            SurfaceControl firstWindowSurface, int mainWindowShiftLength,
            TransactionPool transactionPool, Rect firstWindowFrame, int animationDuration,
            int iconFadeOutDuration, float iconStartAlpha, float brandingStartAlpha,
            int appRevealDelay, int appRevealDuration, Animator.AnimatorListener animatorListener) {
        // Start the default 'reveal' animation.
        startAnimations(TYPE_RADIAL_VANISH_SLIDE_UP, splashScreenView,
                firstWindowSurface, mainWindowShiftLength, transactionPool, firstWindowFrame,
                animationDuration, iconFadeOutDuration, iconStartAlpha, brandingStartAlpha,
                appRevealDelay, appRevealDuration, animatorListener, 0f /* roundedCornerRadius */);
    }

    /**
     * Creates the animator to fade out the icon, reveal the app, and shift up main window.
     * @hide
     */
    private static ValueAnimator createRadialVanishSlideUpAnimator(ViewGroup splashScreenView,
            SurfaceControl firstWindowSurface, int mMainWindowShiftLength,
            TransactionPool transactionPool, Rect firstWindowFrame, int animationDuration,
            int iconFadeOutDuration, float iconStartAlpha, float brandingStartAlpha,
            int appRevealDelay, int appRevealDuration, Animator.AnimatorListener animatorListener,
            float roundedCornerRadius) {
        // reveal app
        final float transparentRatio = 0.8f;
        final int globalHeight = splashScreenView.getHeight();
        final int verticalCircleCenter = 0;
        final int finalVerticalLength = globalHeight - verticalCircleCenter;
        final int halfWidth = splashScreenView.getWidth() / 2;
        final int endRadius = (int) (0.5 + (1f / transparentRatio * (int)
                Math.sqrt(finalVerticalLength * finalVerticalLength + halfWidth * halfWidth)));
        final int[] colors = {Color.WHITE, Color.WHITE, Color.TRANSPARENT};
        final float[] stops = {0f, transparentRatio, 1f};

        RadialVanishAnimation radialVanishAnimation = new RadialVanishAnimation(splashScreenView);
        radialVanishAnimation.setCircleCenter(halfWidth, verticalCircleCenter);
        radialVanishAnimation.setRadius(0 /* initRadius */, endRadius);
        radialVanishAnimation.setRadialPaintParam(colors, stops);

        View occludeHoleView = null;
        ShiftUpAnimation shiftUpAnimation = null;
        if (firstWindowSurface != null && firstWindowSurface.isValid()) {
            // shift up main window
            occludeHoleView = new View(splashScreenView.getContext());
            if (DEBUG_EXIT_ANIMATION_BLEND) {
                occludeHoleView.setBackgroundColor(Color.BLUE);
            } else if (splashScreenView instanceof SplashScreenView) {
                occludeHoleView.setBackgroundColor(
                        ((SplashScreenView) splashScreenView).getInitBackgroundColor());
            } else {
                occludeHoleView.setBackgroundColor(
                        isDarkTheme(splashScreenView.getContext()) ? Color.BLACK : Color.WHITE);
            }
            final ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, mMainWindowShiftLength);
            splashScreenView.addView(occludeHoleView, params);

            shiftUpAnimation = new ShiftUpAnimation(0, -mMainWindowShiftLength, occludeHoleView,
                    firstWindowSurface, splashScreenView, transactionPool, firstWindowFrame,
                    mMainWindowShiftLength, roundedCornerRadius);
        }

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(animationDuration);
        animator.setInterpolator(Interpolators.LINEAR);
        if (animatorListener != null) {
            animator.addListener(animatorListener);
        }
        View finalOccludeHoleView = occludeHoleView;
        ShiftUpAnimation finalShiftUpAnimation = shiftUpAnimation;
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (finalShiftUpAnimation != null) {
                    finalShiftUpAnimation.finish();
                }
                splashScreenView.removeView(radialVanishAnimation);
                splashScreenView.removeView(finalOccludeHoleView);
            }
        });
        animator.addUpdateListener(animation -> {
            float linearProgress = (float) animation.getAnimatedValue();

            // Fade out progress
            final float iconProgress =
                    ICON_INTERPOLATOR.getInterpolation(getProgress(
                            linearProgress, 0 /* delay */, iconFadeOutDuration, animationDuration));
            View iconView = null;
            View brandingView = null;
            if (splashScreenView instanceof SplashScreenView) {
                iconView = ((SplashScreenView) splashScreenView).getIconView();
                brandingView = ((SplashScreenView) splashScreenView).getBrandingView();
            }
            if (iconView != null) {
                iconView.setAlpha(iconStartAlpha * (1 - iconProgress));
            }
            if (brandingView != null) {
                brandingView.setAlpha(brandingStartAlpha * (1 - iconProgress));
            }

            final float revealLinearProgress = getProgress(linearProgress, appRevealDelay,
                    appRevealDuration, animationDuration);

            radialVanishAnimation.onAnimationProgress(revealLinearProgress);

            if (finalShiftUpAnimation != null) {
                finalShiftUpAnimation.onAnimationProgress(revealLinearProgress);
            }
        });
        return animator;
    }

    private static float getProgress(float linearProgress, long delay, long duration,
                                     int animationDuration) {
        return MathUtils.constrain(
                (linearProgress * (animationDuration) - delay) / duration,
                0.0f,
                1.0f
        );
    }

    private static boolean isDarkTheme(Context context) {
        Configuration configuration = context.getResources().getConfiguration();
        int nightMode = configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private static ValueAnimator createFadeOutAnimation(ViewGroup splashScreenView,
            int animationDuration, int iconFadeOutDuration, float iconStartAlpha,
            float brandingStartAlpha, int appRevealDelay, int appRevealDuration,
            Animator.AnimatorListener animatorListener) {

        if (DEBUG_EXIT_FADE_ANIMATION) {
            splashScreenView.setBackgroundColor(Color.BLUE);
        }

        final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(animationDuration);
        animator.setInterpolator(Interpolators.LINEAR);
        animator.addUpdateListener(animation -> {

            float linearProgress = (float) animation.getAnimatedValue();

            // Icon fade out progress (always starts immediately)
            final float iconFadeProgress = ICON_INTERPOLATOR.getInterpolation(getProgress(
                            linearProgress, 0 /* delay */, iconFadeOutDuration, animationDuration));
            View iconView = null;
            View brandingView = null;

            if (splashScreenView instanceof SplashScreenView) {
                iconView = ((SplashScreenView) splashScreenView).getIconView();
                brandingView = ((SplashScreenView) splashScreenView).getBrandingView();
            }
            if (iconView != null) {
                iconView.setAlpha(iconStartAlpha * (1f - iconFadeProgress));
            }
            if (brandingView != null) {
                brandingView.setAlpha(brandingStartAlpha * (1f - iconFadeProgress));
            }

            // Splash screen fade out progress (possibly delayed)
            final float splashFadeProgress = Interpolators.ALPHA_OUT.getInterpolation(
                    getProgress(linearProgress, appRevealDelay,
                    appRevealDuration, animationDuration));

            splashScreenView.setAlpha(1f - splashFadeProgress);

            if (DEBUG_EXIT_FADE_ANIMATION) {
                Slog.d(TAG, "progress -> animation: " + linearProgress
                        + "\t icon alpha: " + ((iconView != null) ? iconView.getAlpha() : "n/a")
                        + "\t splash alpha: " + splashScreenView.getAlpha()
                );
            }
        });
        if (animatorListener != null) {
            animator.addListener(animatorListener);
        }
        return animator;
    }

    /**
     * View which creates a circular reveal of the underlying view.
     * @hide
     */
    @SuppressLint("ViewConstructor")
    public static class RadialVanishAnimation extends View {
        private final ViewGroup mView;
        private int mInitRadius;
        private int mFinishRadius;

        private final Point mCircleCenter = new Point();
        private final Matrix mVanishMatrix = new Matrix();
        private final Paint mVanishPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public RadialVanishAnimation(ViewGroup target) {
            super(target.getContext());
            mView = target;
            mView.addView(this);
            if (getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) getLayoutParams()).setMargins(0, 0, 0, 0);
            }
            mVanishPaint.setAlpha(0);
        }

        void onAnimationProgress(float linearProgress) {
            if (mVanishPaint.getShader() == null) {
                return;
            }

            final float radiusProgress = MASK_RADIUS_INTERPOLATOR.getInterpolation(linearProgress);
            final float alphaProgress = Interpolators.ALPHA_OUT.getInterpolation(linearProgress);
            final float scale = mInitRadius + (mFinishRadius - mInitRadius) * radiusProgress;

            mVanishMatrix.setScale(scale, scale);
            mVanishMatrix.postTranslate(mCircleCenter.x, mCircleCenter.y);
            mVanishPaint.getShader().setLocalMatrix(mVanishMatrix);
            mVanishPaint.setAlpha(Math.round(0xFF * alphaProgress));

            postInvalidate();
        }

        void setRadius(int initRadius, int finishRadius) {
            if (DEBUG_EXIT_ANIMATION) {
                Slog.v(TAG, "RadialVanishAnimation setRadius init: " + initRadius
                        + " final " + finishRadius);
            }
            mInitRadius = initRadius;
            mFinishRadius = finishRadius;
        }

        void setCircleCenter(int x, int y) {
            if (DEBUG_EXIT_ANIMATION) {
                Slog.v(TAG, "RadialVanishAnimation setCircleCenter x: " + x + " y " + y);
            }
            mCircleCenter.set(x, y);
        }

        void setRadialPaintParam(int[] colors, float[] stops) {
            // setup gradient shader
            final RadialGradient rShader =
                    new RadialGradient(0, 0, 1, colors, stops, Shader.TileMode.CLAMP);
            mVanishPaint.setShader(rShader);
            if (!DEBUG_EXIT_ANIMATION_BLEND) {
                // We blend the reveal gradient with the splash screen using DST_OUT so that the
                // splash screen is fully visible when radius = 0 (or gradient opacity is 0) and
                // fully invisible when radius = finishRadius AND gradient opacity is 1.
                mVanishPaint.setBlendMode(BlendMode.DST_OUT);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawRect(0, 0, mView.getWidth(), mView.getHeight(), mVanishPaint);
        }
    }

    /**
     * Shifts up the main window.
     * @hide
     */
    public static final class ShiftUpAnimation {
        private final float mFromYDelta;
        private final float mToYDelta;
        private final View mOccludeHoleView;
        private final SyncRtSurfaceTransactionApplier mApplier;
        private final Matrix mTmpTransform = new Matrix();
        private final SurfaceControl mFirstWindowSurface;
        private final ViewGroup mSplashScreenView;
        private final TransactionPool mTransactionPool;
        private final Rect mFirstWindowFrame;
        private final int mMainWindowShiftLength;

        public ShiftUpAnimation(float fromYDelta, float toYDelta, View occludeHoleView,
                                SurfaceControl firstWindowSurface, ViewGroup splashScreenView,
                                TransactionPool transactionPool, Rect firstWindowFrame,
                                int mainWindowShiftLength, float roundedCornerRadius) {
            mFromYDelta = fromYDelta - Math.max(firstWindowFrame.top, roundedCornerRadius);
            mToYDelta = toYDelta;
            mOccludeHoleView = occludeHoleView;
            mApplier = new SyncRtSurfaceTransactionApplier(occludeHoleView);
            mFirstWindowSurface = firstWindowSurface;
            mSplashScreenView = splashScreenView;
            mTransactionPool = transactionPool;
            mFirstWindowFrame = firstWindowFrame;
            mMainWindowShiftLength = mainWindowShiftLength;
        }

        void onAnimationProgress(float linearProgress) {
            if (mFirstWindowSurface == null || !mFirstWindowSurface.isValid()
                    || !mSplashScreenView.isAttachedToWindow()) {
                return;
            }

            final float progress = SHIFT_UP_INTERPOLATOR.getInterpolation(linearProgress);
            final float dy = mFromYDelta + (mToYDelta - mFromYDelta) * progress;

            mOccludeHoleView.setTranslationY(dy);
            mTmpTransform.setTranslate(0 /* dx */, dy);

            // set the vsyncId to ensure the transaction doesn't get applied too early.
            final SurfaceControl.Transaction tx = mTransactionPool.acquire();
            tx.setFrameTimelineVsync(Choreographer.getSfInstance().getVsyncId());
            mTmpTransform.postTranslate(mFirstWindowFrame.left,
                    mFirstWindowFrame.top + mMainWindowShiftLength);

            SyncRtSurfaceTransactionApplier.SurfaceParams
                    params = new SyncRtSurfaceTransactionApplier.SurfaceParams
                    .Builder(mFirstWindowSurface)
                    .withMatrix(mTmpTransform)
                    .withMergeTransaction(tx)
                    .build();
            mApplier.scheduleApply(params);

            mTransactionPool.release(tx);
        }

        void finish() {
            if (mFirstWindowSurface == null || !mFirstWindowSurface.isValid()) {
                return;
            }
            final SurfaceControl.Transaction tx = mTransactionPool.acquire();
            if (mSplashScreenView.isAttachedToWindow()) {
                tx.setFrameTimelineVsync(Choreographer.getSfInstance().getVsyncId());

                SyncRtSurfaceTransactionApplier.SurfaceParams
                        params = new SyncRtSurfaceTransactionApplier.SurfaceParams
                        .Builder(mFirstWindowSurface)
                        .withWindowCrop(null)
                        .withMergeTransaction(tx)
                        .build();
                mApplier.scheduleApply(params);
            } else {
                tx.setWindowCrop(mFirstWindowSurface, null);
                tx.apply();
            }
            mTransactionPool.release(tx);

            Choreographer.getSfInstance().postCallback(CALLBACK_COMMIT,
                    mFirstWindowSurface::release, null);
        }
    }
}

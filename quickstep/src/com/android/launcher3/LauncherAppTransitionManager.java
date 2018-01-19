/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.launcher3;

import static com.android.systemui.shared.recents.utilities.Utilities.getNextFrameNumber;
import static com.android.systemui.shared.recents.utilities.Utilities.getSurface;
import static com.android.systemui.shared.recents.utilities.Utilities.postAtFrontOfQueueAsynchronously;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.ImageView;

import com.android.launcher3.InsettableFrameLayout.LayoutParams;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.systemui.shared.system.ActivityOptionsCompat;
import com.android.systemui.shared.system.RemoteAnimationAdapterCompat;
import com.android.systemui.shared.system.RemoteAnimationRunnerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.TransactionCompat;

/**
 * Manages the opening app animations from Launcher.
 */
public class LauncherAppTransitionManager {

    private static final int REFRESH_RATE_MS = 16;

    private final DragLayer mDragLayer;
    private final Launcher mLauncher;
    private final DeviceProfile mDeviceProfile;

    private final float mDragLayerTransY;

    private ImageView mFloatingView;

    public LauncherAppTransitionManager(Launcher launcher) {
        mLauncher = launcher;
        mDragLayer = launcher.getDragLayer();
        mDeviceProfile = launcher.getDeviceProfile();

        mDragLayerTransY =
                launcher.getResources().getDimensionPixelSize(R.dimen.drag_layer_trans_y);
    }

    public Bundle getActivityLauncherOptions(View v) {
        RemoteAnimationRunnerCompat runner = new RemoteAnimationRunnerCompat() {
            @Override
            public void onAnimationStart(RemoteAnimationTargetCompat[] targets,
                    Runnable finishedCallback) {
                // Post at front of queue ignoring sync barriers to make sure it gets processed
                // before the next frame.
                postAtFrontOfQueueAsynchronously(v.getHandler(), () -> {
                    AnimatorSet both = new AnimatorSet();
                    both.play(getLauncherAnimators(v));
                    both.play(getAppWindowAnimators(v, targets));
                    both.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            // Reset launcher to normal state
                            v.setVisibility(View.VISIBLE);
                            ((ViewGroup) mDragLayer.getParent()).removeView(mFloatingView);

                            mDragLayer.setAlpha(1f);
                            mDragLayer.setTranslationY(0f);

                            View appsView = mLauncher.getAppsView();
                            appsView.setAlpha(1f);
                            appsView.setTranslationY(0f);

                            finishedCallback.run();
                        }
                    });
                    both.start();
                    // Because t=0 has the app icon in its original spot, we can skip the first
                    // frame and have the same movement one frame earlier.
                    both.setCurrentPlayTime(REFRESH_RATE_MS);
                });
            }

            @Override
            public void onAnimationCancelled() {
            }
        };

        return ActivityOptionsCompat.makeRemoteAnimation(
                new RemoteAnimationAdapterCompat(runner, 500, 380)).toBundle();
    }

    private AnimatorSet getLauncherAnimators(View v) {
        AnimatorSet launcherAnimators = new AnimatorSet();
        launcherAnimators.play(getHideLauncherAnimator());
        launcherAnimators.play(getAppIconAnimator(v));
        return launcherAnimators;
    }

    private AnimatorSet getHideLauncherAnimator() {
        AnimatorSet hideLauncher = new AnimatorSet();

        // Animate the background content so that it moves downwards and fades out.
        if (mLauncher.isInState(LauncherState.ALL_APPS)) {
            View appsView = mLauncher.getAppsView();
            ObjectAnimator alpha = ObjectAnimator.ofFloat(appsView, View.ALPHA, 1f, 0f);
            alpha.setDuration(217);
            alpha.setInterpolator(Interpolators.LINEAR);
            ObjectAnimator transY = ObjectAnimator.ofFloat(appsView, View.TRANSLATION_Y, 0,
                    mDragLayerTransY);
            transY.setInterpolator(Interpolators.AGGRESSIVE_EASE);
            transY.setDuration(350);

            hideLauncher.play(alpha);
            hideLauncher.play(transY);
        } else {
            ObjectAnimator dragLayerAlpha = ObjectAnimator.ofFloat(mDragLayer, View.ALPHA, 1f, 0f);
            dragLayerAlpha.setDuration(217);
            dragLayerAlpha.setInterpolator(Interpolators.LINEAR);
            ObjectAnimator dragLayerTransY = ObjectAnimator.ofFloat(mDragLayer, View.TRANSLATION_Y,
                    0, mDragLayerTransY);
            dragLayerTransY.setInterpolator(Interpolators.AGGRESSIVE_EASE);
            dragLayerTransY.setDuration(350);

            hideLauncher.play(dragLayerAlpha);
            hideLauncher.play(dragLayerTransY);
        }
        return hideLauncher;
    }

    private AnimatorSet getAppIconAnimator(View v) {
        // Create a copy of the app icon
        mFloatingView = new ImageView(mLauncher);
        Bitmap iconBitmap = ((FastBitmapDrawable) ((BubbleTextView) v).getIcon()).getBitmap();
        mFloatingView.setImageDrawable(new FastBitmapDrawable(iconBitmap));

        // Position the copy of the app icon exactly on top of the original
        Rect rect = new Rect();
        mDragLayer.getDescendantRectRelativeToSelf(v, rect);
        int viewLocationLeft = rect.left;
        int viewLocationTop = rect.top;

        ((BubbleTextView) v).getIconBounds(rect);
        LayoutParams lp = new LayoutParams(rect.width(), rect.height());
        lp.ignoreInsets = true;
        lp.leftMargin = viewLocationLeft + rect.left;
        lp.topMargin = viewLocationTop + rect.top;
        mFloatingView.setLayoutParams(lp);

        // Swap the two views in place.
        ((ViewGroup) mDragLayer.getParent()).addView(mFloatingView);
        v.setVisibility(View.INVISIBLE);

        AnimatorSet appIconAnimatorSet = new AnimatorSet();
        // Animate the app icon to the center
        float centerX = mDeviceProfile.widthPx / 2;
        float centerY = mDeviceProfile.heightPx / 2;
        float dX = centerX - lp.leftMargin - (lp.width / 2);
        float dY = centerY - lp.topMargin - (lp.height / 2);
        ObjectAnimator x = ObjectAnimator.ofFloat(mFloatingView, View.TRANSLATION_X, 0f, dX);
        ObjectAnimator y = ObjectAnimator.ofFloat(mFloatingView, View.TRANSLATION_Y, 0f, dY);

        // Adjust the duration to change the "curve" of the app icon to the center.
        boolean isBelowCenterY = lp.topMargin < centerY;
        x.setDuration(isBelowCenterY ? 500 : 233);
        y.setDuration(isBelowCenterY ? 233 : 500);
        appIconAnimatorSet.play(x);
        appIconAnimatorSet.play(y);

        // Scale the app icon to take up the entire screen. This simplifies the math when
        // animating the app window position / scale.
        float maxScaleX = mDeviceProfile.widthPx / (float) rect.width();
        float maxScaleY = mDeviceProfile.heightPx / (float) rect.height();
        float scale = Math.max(maxScaleX, maxScaleY);
        ObjectAnimator sX = ObjectAnimator.ofFloat(mFloatingView, View.SCALE_X, 1f, scale);
        ObjectAnimator sY = ObjectAnimator.ofFloat(mFloatingView, View.SCALE_Y, 1f, scale);
        sX.setDuration(500);
        sY.setDuration(500);
        appIconAnimatorSet.play(sX);
        appIconAnimatorSet.play(sY);

        // Fade out the app icon.
        ObjectAnimator alpha = ObjectAnimator.ofFloat(mFloatingView, View.ALPHA, 1f, 0f);
        alpha.setStartDelay(17);
        alpha.setDuration(33);
        appIconAnimatorSet.play(alpha);

        for (Animator a : appIconAnimatorSet.getChildAnimations()) {
            a.setInterpolator(Interpolators.AGGRESSIVE_EASE);
        }
        return appIconAnimatorSet;
    }

    private ValueAnimator getAppWindowAnimators(View v, RemoteAnimationTargetCompat[] targets) {
        Rect iconBounds = new Rect();
        ((BubbleTextView) v).getIconBounds(iconBounds);
        int[] floatingViewBounds = new int[2];

        Rect crop = new Rect();
        Matrix matrix = new Matrix();

        ValueAnimator appAnimator = ValueAnimator.ofFloat(0, 1);
        appAnimator.setDuration(500);
        appAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            boolean isFirstFrame = true;

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final float percent = animation.getAnimatedFraction();
                final float easePercent = Interpolators.AGGRESSIVE_EASE.getInterpolation(percent);

                // Calculate app icon size.
                float iconWidth = iconBounds.width() * mFloatingView.getScaleX();
                float iconHeight = iconBounds.height() * mFloatingView.getScaleY();

                // Scale the app window to match the icon size.
                float scaleX = iconWidth / mDeviceProfile.widthPx;
                float scaleY = iconHeight / mDeviceProfile.heightPx;
                float scale = Math.min(1f, Math.min(scaleX, scaleY));
                matrix.setScale(scale, scale);

                // Position the scaled window on top of the icon
                int deviceWidth = mDeviceProfile.widthPx;
                int deviceHeight = mDeviceProfile.heightPx;
                float scaledWindowWidth = deviceWidth * scale;
                float scaledWindowHeight = deviceHeight * scale;

                float offsetX = (scaledWindowWidth - iconWidth) / 2;
                float offsetY = (scaledWindowHeight - iconHeight) / 2;
                mFloatingView.getLocationInWindow(floatingViewBounds);
                float transX0 = floatingViewBounds[0] - offsetX;
                float transY0 = floatingViewBounds[1] - offsetY;
                matrix.postTranslate(transX0, transY0);

                // Fade in the app window.
                float alphaDelay = 0;
                float alphaDuration = 50;
                float alpha = getValue(1f, 0f, alphaDelay, alphaDuration,
                        appAnimator.getDuration() * percent, Interpolators.AGGRESSIVE_EASE);

                // Animate the window crop so that it starts off as a square, and then reveals
                // horizontally.
                float cropHeight = deviceHeight * easePercent + deviceWidth * (1 - easePercent);
                float initialTop = (deviceHeight - deviceWidth) / 2f;
                crop.left = 0;
                crop.top = (int) (initialTop * (1 - easePercent));
                crop.right = deviceWidth;
                crop.bottom = (int) (crop.top + cropHeight);

                TransactionCompat t = new TransactionCompat();
                for (RemoteAnimationTargetCompat target : targets) {
                    if (target.mode == RemoteAnimationTargetCompat.MODE_OPENING) {
                        t.setAlpha(target.leash, alpha);
                        t.setMatrix(target.leash, matrix);
                        t.setWindowCrop(target.leash, crop);
                        Surface surface = getSurface(mFloatingView);
                        t.deferTransactionUntil(target.leash, surface, getNextFrameNumber(surface));
                    }
                    if (isFirstFrame) {
                        t.show(target.leash);
                    }
                }
                t.apply();

                matrix.reset();
                isFirstFrame = false;
            }

            /**
             * Helper method that allows us to get interpolated values for embedded
             * animations with a delay and/or different duration.
             */
            private float getValue(float start, float end, float delay, float duration,
                                   float currentPlayTime, Interpolator i) {
                float time = Math.max(0, currentPlayTime - delay);
                float newPercent = Math.min(1f, time / duration);
                newPercent = i.getInterpolation(newPercent);
                return start * newPercent + end * (1 - newPercent);
            }
        });
        return appAnimator;
    }
}

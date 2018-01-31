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

import static com.android.launcher3.allapps.AllAppsTransitionController.ALL_APPS_PROGRESS;
import static com.android.systemui.shared.recents.utilities.Utilities.getNextFrameNumber;
import static com.android.systemui.shared.recents.utilities.Utilities.getSurface;
import static com.android.systemui.shared.recents.utilities.Utilities.postAtFrontOfQueueAsynchronously;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.ImageView;

import com.android.launcher3.InsettableFrameLayout.LayoutParams;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.systemui.shared.system.ActivityCompat;
import com.android.systemui.shared.system.ActivityOptionsCompat;
import com.android.systemui.shared.system.RemoteAnimationAdapterCompat;
import com.android.systemui.shared.system.RemoteAnimationDefinitionCompat;
import com.android.systemui.shared.system.RemoteAnimationRunnerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.TransactionCompat;
import com.android.systemui.shared.system.WindowManagerWrapper;

/**
 * Manages the opening and closing app transitions from Launcher.
 */
public class LauncherAppTransitionManagerImpl extends LauncherAppTransitionManager {

    private static final String TAG = "LauncherTransition";
    private static final int REFRESH_RATE_MS = 16;

    private static final String CONTROL_REMOTE_APP_TRANSITION_PERMISSION =
            "android.permission.CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS";

    private static final int LAUNCHER_RESUME_START_DELAY = 150;
    private static final int CLOSING_TRANSITION_DURATION_MS = 350;

    // Progress = 0: All apps is fully pulled up, Progress = 1: All apps is fully pulled down.
    private static final float ALL_APPS_PROGRESS_START = 1.3059858f;
    private static final float ALL_APPS_PROGRESS_SLIDE_END = 0.99581414f;

    private final DragLayer mDragLayer;
    private final Launcher mLauncher;
    private final DeviceProfile mDeviceProfile;

    private final float mContentTransY;
    private final float mWorkspaceTransY;

    private ImageView mFloatingView;
    private boolean mIsRtl;

    private Animator mCurrentAnimator;

    public LauncherAppTransitionManagerImpl(Context context) {
        mLauncher = Launcher.getLauncher(context);
        mDragLayer = mLauncher.getDragLayer();
        mDeviceProfile = mLauncher.getDeviceProfile();

        mIsRtl = Utilities.isRtl(mLauncher.getResources());

        Resources res = mLauncher.getResources();
        mContentTransY = res.getDimensionPixelSize(R.dimen.content_trans_y);
        mWorkspaceTransY = res.getDimensionPixelSize(R.dimen.workspace_trans_y);
    }

    private void setCurrentAnimator(Animator animator) {
        if (mCurrentAnimator != null && mCurrentAnimator.isRunning()) {
            mCurrentAnimator.cancel();
        }
        mCurrentAnimator = animator;
    }

    /**
     * @return A Bundle with remote animations that controls how the window of the opening
     *         targets are displayed.
     */
    @Override
    public Bundle getActivityLaunchOptions(Launcher launcher, View v) {
        if (hasControlRemoteAppTransitionPermission()) {
            try {
                RemoteAnimationRunnerCompat runner = new LauncherAnimationRunner(mLauncher) {
                    @Override
                    public void onAnimationStart(RemoteAnimationTargetCompat[] targets,
                                                 Runnable finishedCallback) {
                        // Post at front of queue ignoring sync barriers to make sure it gets
                        // processed before the next frame.
                        postAtFrontOfQueueAsynchronously(v.getHandler(), () -> {
                            mAnimator = new AnimatorSet();
                            setCurrentAnimator(mAnimator);
                            mAnimator.play(getLauncherAnimators(v));
                            mAnimator.play(getWindowAnimators(v, targets));
                            mAnimator.addListener(new AnimatorListenerAdapter() {
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
                            mAnimator.start();
                            // Because t=0 has the app icon in its original spot, we can skip the
                            // first frame and have the same movement one frame earlier.
                            mAnimator.setCurrentPlayTime(REFRESH_RATE_MS);
                        });
                    }
                };

                return ActivityOptionsCompat.makeRemoteAnimation(
                        new RemoteAnimationAdapterCompat(runner, 500, 380)).toBundle();
            } catch (NoClassDefFoundError e) {
                // Gracefully fall back to default launch options if the user's platform doesn't
                // have the latest changes.
            }
        }
        return getDefaultActivityLaunchOptions(launcher, v);
    }

    /**
     * @return Animators that control the movements of the Launcher and icon of the opening target.
     */
    private AnimatorSet getLauncherAnimators(View v) {
        AnimatorSet launcherAnimators = new AnimatorSet();
        launcherAnimators.play(getLauncherContentAnimator(false /* show */));
        launcherAnimators.play(getIconAnimator(v));
        return launcherAnimators;
    }

    /**
     * Content is everything on screen except the background and the floating view (if any).
     *
     * @param show If true: Animate the content so that it moves upwards and fades in.
     *             Else: Animate the content so that it moves downwards and fades out.
     */
    private AnimatorSet getLauncherContentAnimator(boolean show) {
        AnimatorSet launcherAnimator = new AnimatorSet();

        float[] alphas = show
                ? new float[] {0, 1}
                : new float[] {1, 0};
        float[] trans = show
                ? new float[] {mContentTransY, 0,}
                : new float[] {0, mContentTransY};

        if (mLauncher.isInState(LauncherState.ALL_APPS) && !mDeviceProfile.isVerticalBarLayout()) {
            // All Apps in portrait mode is full screen, so we only animate AllAppsContainerView.
            View appsView = mLauncher.getAppsView();
            appsView.setAlpha(alphas[0]);
            appsView.setTranslationY(trans[0]);

            ObjectAnimator alpha = ObjectAnimator.ofFloat(appsView, View.ALPHA, alphas);
            alpha.setDuration(217);
            alpha.setInterpolator(Interpolators.LINEAR);
            ObjectAnimator transY = ObjectAnimator.ofFloat(appsView, View.TRANSLATION_Y, trans);
            transY.setInterpolator(Interpolators.AGGRESSIVE_EASE);
            transY.setDuration(350);

            launcherAnimator.play(alpha);
            launcherAnimator.play(transY);
        } else {
            mDragLayer.setAlpha(alphas[0]);
            mDragLayer.setTranslationY(trans[0]);

            ObjectAnimator dragLayerAlpha = ObjectAnimator.ofFloat(mDragLayer, View.ALPHA, alphas);
            dragLayerAlpha.setDuration(217);
            dragLayerAlpha.setInterpolator(Interpolators.LINEAR);
            ObjectAnimator dragLayerTransY = ObjectAnimator.ofFloat(mDragLayer, View.TRANSLATION_Y,
                    trans);
            dragLayerTransY.setInterpolator(Interpolators.AGGRESSIVE_EASE);
            dragLayerTransY.setDuration(350);

            launcherAnimator.play(dragLayerAlpha);
            launcherAnimator.play(dragLayerTransY);
        }
        return launcherAnimator;
    }

    /**
     * @return Animator that controls the icon used to launch the target.
     */
    private AnimatorSet getIconAnimator(View v) {
        boolean isBubbleTextView = v instanceof BubbleTextView;
        mFloatingView = new ImageView(mLauncher);
        if (isBubbleTextView) {
            // Create a copy of the app icon
            Bitmap iconBitmap = ((FastBitmapDrawable) ((BubbleTextView) v).getIcon()).getBitmap();
            mFloatingView.setImageDrawable(new FastBitmapDrawable(iconBitmap));
        }

        // Position the floating view exactly on top of the original
        Rect rect = new Rect();
        mDragLayer.getDescendantRectRelativeToSelf(v, rect);
        int viewLocationStart = mIsRtl
                ? mDeviceProfile.widthPx - rect.right
                : rect.left;
        int viewLocationTop = rect.top;

        if (isBubbleTextView) {
            ((BubbleTextView) v).getIconBounds(rect);
        }
        LayoutParams lp = new LayoutParams(rect.width(), rect.height());
        lp.ignoreInsets = true;
        lp.setMarginStart(viewLocationStart + rect.left);
        lp.topMargin = viewLocationTop + rect.top;
        mFloatingView.setLayoutParams(lp);

        // Swap the two views in place.
        ((ViewGroup) mDragLayer.getParent()).addView(mFloatingView);
        v.setVisibility(View.INVISIBLE);

        AnimatorSet appIconAnimatorSet = new AnimatorSet();
        // Animate the app icon to the center
        float centerX = mDeviceProfile.widthPx / 2;
        float centerY = mDeviceProfile.heightPx / 2;

        float xPosition = mIsRtl
                ? mDeviceProfile.widthPx - lp.getMarginStart() - rect.width()
                : lp.getMarginStart();
        float dX = centerX - xPosition - (lp.width / 2);
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

    /**
     * @return Animator that controls the window of the opening targets.
     */
    private ValueAnimator getWindowAnimators(View v, RemoteAnimationTargetCompat[] targets) {
        Rect bounds = new Rect();
        if (v instanceof BubbleTextView) {
            ((BubbleTextView) v).getIconBounds(bounds);
        } else {
            mDragLayer.getDescendantRectRelativeToSelf(v, bounds);
        }
        int[] floatingViewBounds = new int[2];

        Rect crop = new Rect();
        Matrix matrix = new Matrix();

        ValueAnimator appAnimator = ValueAnimator.ofFloat(0, 1);
        appAnimator.setDuration(500);
        appAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            boolean isFirstFrame = true;

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final Surface surface = getSurface(mFloatingView);
                final long frameNumber = surface != null ? getNextFrameNumber(surface) : -1;
                if (frameNumber == -1) {
                    // Booo, not cool! Our surface got destroyed, so no reason to animate anything.
                    Log.w(TAG, "Failed to animate, surface got destroyed.");
                    return;
                }
                final float percent = animation.getAnimatedFraction();
                final float easePercent = Interpolators.AGGRESSIVE_EASE.getInterpolation(percent);

                // Calculate app icon size.
                float iconWidth = bounds.width() * mFloatingView.getScaleX();
                float iconHeight = bounds.height() * mFloatingView.getScaleY();

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
                float alpha = getValue(0f, 1f, alphaDelay, alphaDuration,
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

                        // TODO: This isn't correct at the beginning of the animation, but better
                        // than nothing.
                        matrix.postTranslate(target.position.x, target.position.y);
                        t.setMatrix(target.leash, matrix);
                        t.setWindowCrop(target.leash, crop);
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
        });
        return appAnimator;
    }

    /**
     * Registers remote animations used when closing apps to home screen.
     */
    @Override
    public void registerRemoteAnimations() {
        if (hasControlRemoteAppTransitionPermission()) {
            try {
                RemoteAnimationDefinitionCompat definition = new RemoteAnimationDefinitionCompat();
                definition.addRemoteAnimation(WindowManagerWrapper.TRANSIT_WALLPAPER_OPEN,
                        new RemoteAnimationAdapterCompat(getWallpaperOpenRunner(),
                                CLOSING_TRANSITION_DURATION_MS, 0 /* statusBarTransitionDelay */));

//      TODO: App controlled transition for unlock to home TRANSIT_KEYGUARD_GOING_AWAY_ON_WALLPAPER

                new ActivityCompat(mLauncher).registerRemoteAnimations(definition);
            } catch (NoClassDefFoundError e) {
                // Gracefully fall back if the user's platform doesn't have the latest changes
            }
        }
    }

    /**
     * @return Runner that plays when user goes to Launcher
     *         ie. pressing home, swiping up from nav bar.
     */
    private RemoteAnimationRunnerCompat getWallpaperOpenRunner() {
        return new LauncherAnimationRunner(mLauncher) {
            @Override
            public void onAnimationStart(RemoteAnimationTargetCompat[] targets,
                                         Runnable finishedCallback) {
                Handler handler = mLauncher.getWindow().getDecorView().getHandler();
                postAtFrontOfQueueAsynchronously(handler, () -> {
                    if (Utilities.getPrefs(mLauncher).getBoolean("pref_use_screenshot_animation",
                            true) && mLauncher.isInState(LauncherState.OVERVIEW)) {
                        // We use a separate transition for Overview mode.
                        setCurrentAnimator(null);
                        finishedCallback.run();
                        return;
                    }

                    mAnimator = new AnimatorSet();
                    setCurrentAnimator(mAnimator);
                    mAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            finishedCallback.run();
                        }
                    });
                    mAnimator.play(getClosingWindowAnimators(targets));
                    mAnimator.play(getLauncherResumeAnimation());
                    mAnimator.start();
                });
            }
        };
    }

    /**
     * Animator that controls the transformations of the windows the targets that are closing.
     */
    private Animator getClosingWindowAnimators(RemoteAnimationTargetCompat[] targets) {
        Matrix matrix = new Matrix();
        float height = mLauncher.getDeviceProfile().heightPx;
        float width = mLauncher.getDeviceProfile().widthPx;
        float endX = Utilities.isRtl(mLauncher.getResources()) ? -width : width;

        ValueAnimator closingAnimator = ValueAnimator.ofFloat(0, 1);
        closingAnimator.setDuration(CLOSING_TRANSITION_DURATION_MS);

        closingAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            boolean isFirstFrame = true;

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final float percent = animation.getAnimatedFraction();
                float currentPlayTime = percent * closingAnimator.getDuration();

                float scale = getValue(1f, 0.8f, 0, 267, currentPlayTime,
                        Interpolators.AGGRESSIVE_EASE);
                matrix.setScale(scale, scale);

                float dX = getValue(0, endX, 0, 350, currentPlayTime,
                        Interpolators.AGGRESSIVE_EASE_IN_OUT);
                float dY = (height - height * scale) / 2f;

                TransactionCompat t = new TransactionCompat();
                for (RemoteAnimationTargetCompat app : targets) {
                    if (app.mode == RemoteAnimationTargetCompat.MODE_CLOSING) {
                        t.setAlpha(app.leash, 1f - percent);
                        matrix.postTranslate(dX, dY);
                        matrix.postTranslate(app.position.x, app.position.y);
                        t.setMatrix(app.leash, matrix);
                    }
                    // TODO: Layer should be set only once, but there is possibly a race condition
                    // where WindowManager is also calling setLayer.
                    int layer = app.mode == RemoteAnimationTargetCompat.MODE_CLOSING
                            ? Integer.MAX_VALUE
                            : app.prefixOrderIndex;
                    t.setLayer(app.leash, layer);
                    if (isFirstFrame) {
                        t.show(app.leash);
                    }
                }
                t.apply();

                matrix.reset();
                isFirstFrame = false;
            }
        });
        return closingAnimator;
    }

    /**
     * @return Animator that modifies Launcher as a result from {@link #getWallpaperOpenRunner}.
     */
    private AnimatorSet getLauncherResumeAnimation() {
        if (mLauncher.isInState(LauncherState.ALL_APPS)
                || mLauncher.getDeviceProfile().isVerticalBarLayout()) {
            AnimatorSet contentAnimator = getLauncherContentAnimator(true /* show */);
            contentAnimator.setStartDelay(LAUNCHER_RESUME_START_DELAY);
            return contentAnimator;
        } else {
            AnimatorSet workspaceAnimator = new AnimatorSet();
            mLauncher.getWorkspace().setTranslationY(mWorkspaceTransY);
            mLauncher.getWorkspace().setAlpha(0f);
            workspaceAnimator.play(ObjectAnimator.ofFloat(mLauncher.getWorkspace(),
                    View.TRANSLATION_Y, mWorkspaceTransY, 0));
            workspaceAnimator.play(ObjectAnimator.ofFloat(mLauncher.getWorkspace(), View.ALPHA,
                    0, 1f));
            workspaceAnimator.setStartDelay(LAUNCHER_RESUME_START_DELAY);
            workspaceAnimator.setDuration(333);
            workspaceAnimator.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);

            // Animate the shelf in two parts: slide in, and overeshoot.
            AllAppsTransitionController allAppsController = mLauncher.getAllAppsController();
            // The shelf will start offscreen
            final float startY = ALL_APPS_PROGRESS_START;
            // And will end slightly pulled up, so that there is something to overshoot back to 1f.
            final float slideEnd = ALL_APPS_PROGRESS_SLIDE_END;

            allAppsController.setProgress(startY);

            Animator allAppsSlideIn =
                    ObjectAnimator.ofFloat(allAppsController, ALL_APPS_PROGRESS, startY, slideEnd);
            allAppsSlideIn.setStartDelay(LAUNCHER_RESUME_START_DELAY);
            allAppsSlideIn.setDuration(317);
            allAppsSlideIn.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);

            Animator allAppsOvershoot =
                    ObjectAnimator.ofFloat(allAppsController, ALL_APPS_PROGRESS, slideEnd, 1f);
            allAppsOvershoot.setDuration(153);
            allAppsOvershoot.setInterpolator(Interpolators.OVERSHOOT_0);

            AnimatorSet resumeLauncherAnimation = new AnimatorSet();
            resumeLauncherAnimation.play(workspaceAnimator);
            resumeLauncherAnimation.playSequentially(allAppsSlideIn, allAppsOvershoot);
            return resumeLauncherAnimation;
        }
    }

    private boolean hasControlRemoteAppTransitionPermission() {
        return mLauncher.checkSelfPermission(CONTROL_REMOTE_APP_TRANSITION_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Helper method that allows us to get interpolated values for embedded
     * animations with a delay and/or different duration.
     */
    private static float getValue(float start, float end, float delay, float duration,
            float currentPlayTime, Interpolator i) {
        float time = Math.max(0, currentPlayTime - delay);
        float newPercent = Math.min(1f, time / duration);
        newPercent = i.getInterpolation(newPercent);
        return end * newPercent + start * (1 - newPercent);
    }
}

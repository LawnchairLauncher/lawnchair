/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.launcher3.statehandlers;

import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_DEPTH;
import static com.android.launcher3.states.StateAnimationConfig.SKIP_DEPTH_CONTROLLER;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.os.IBinder;
import android.util.FloatProperty;
import android.view.View;
import android.view.ViewTreeObserver;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.systemui.shared.system.BlurUtils;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SurfaceControlCompat;
import com.android.systemui.shared.system.TransactionCompat;
import com.android.systemui.shared.system.WallpaperManagerCompat;

/**
 * Controls blur and wallpaper zoom, for the Launcher surface only.
 */
public class DepthController implements StateHandler<LauncherState>,
        BaseActivity.MultiWindowModeChangedListener {

    public static final FloatProperty<DepthController> DEPTH =
            new FloatProperty<DepthController>("depth") {
                @Override
                public void setValue(DepthController depthController, float depth) {
                    depthController.setDepth(depth);
                }

                @Override
                public Float get(DepthController depthController) {
                    return depthController.mDepth;
                }
            };

    /**
     * A property that updates the background blur within a given range of values (ie. even if the
     * animator goes beyond 0..1, the interpolated value will still be bounded).
     */
    public static class ClampedDepthProperty extends FloatProperty<DepthController> {
        private final float mMinValue;
        private final float mMaxValue;

        public ClampedDepthProperty(float minValue, float maxValue) {
            super("depthClamped");
            mMinValue = minValue;
            mMaxValue = maxValue;
        }

        @Override
        public void setValue(DepthController depthController, float depth) {
            depthController.setDepth(Utilities.boundToRange(depth, mMinValue, mMaxValue));
        }

        @Override
        public Float get(DepthController depthController) {
            return depthController.mDepth;
        }
    }

    private final ViewTreeObserver.OnDrawListener mOnDrawListener =
            new ViewTreeObserver.OnDrawListener() {
                @Override
                public void onDraw() {
                    View view = mLauncher.getDragLayer();
                    setSurface(new SurfaceControlCompat(view));
                    view.post(() -> view.getViewTreeObserver().removeOnDrawListener(this));
                }
            };

    private final Launcher mLauncher;
    /**
     * Blur radius when completely zoomed out, in pixels.
     */
    private int mMaxBlurRadius;
    private WallpaperManagerCompat mWallpaperManager;
    private SurfaceControlCompat mSurface;
    /**
     * Ratio from 0 to 1, where 0 is fully zoomed out, and 1 is zoomed in.
     * @see android.service.wallpaper.WallpaperService.Engine#onZoomChanged(float)
     */
    private float mDepth;

    // Workaround for animating the depth when multiwindow mode changes.
    private boolean mIgnoreStateChangesDuringMultiWindowAnimation = false;

    private View.OnAttachStateChangeListener mOnAttachListener;

    public DepthController(Launcher l) {
        mLauncher = l;
    }

    private void ensureDependencies() {
        if (mWallpaperManager == null) {
            mMaxBlurRadius = mLauncher.getResources().getInteger(R.integer.max_depth_blur_radius);
            mWallpaperManager = new WallpaperManagerCompat(mLauncher);
        }
        if (mLauncher.getRootView() != null && mOnAttachListener == null) {
            mOnAttachListener = new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View view) {
                    // To handle the case where window token is invalid during last setDepth call.
                    IBinder windowToken = mLauncher.getRootView().getWindowToken();
                    if (windowToken != null) {
                        mWallpaperManager.setWallpaperZoomOut(windowToken, mDepth);
                    }
                }

                @Override
                public void onViewDetachedFromWindow(View view) {
                }
            };
            mLauncher.getRootView().addOnAttachStateChangeListener(mOnAttachListener);
        }
    }

    /**
     * Sets if the underlying activity is started or not
     */
    public void setActivityStarted(boolean isStarted) {
        if (isStarted) {
            mLauncher.getDragLayer().getViewTreeObserver().addOnDrawListener(mOnDrawListener);
        } else {
            mLauncher.getDragLayer().getViewTreeObserver().removeOnDrawListener(mOnDrawListener);
            setSurface(null);
        }
    }

    /**
     * Sets the specified app target surface to apply the blur to.
     */
    public void setSurfaceToApp(RemoteAnimationTargetCompat target) {
        if (target != null) {
            setSurface(target.leash);
        } else {
            setActivityStarted(mLauncher.isStarted());
        }
    }

    private void setSurface(SurfaceControlCompat surface) {
        if (mSurface != surface) {
            mSurface = surface;
            if (surface != null) {
                setDepth(mDepth);
            } else {
                // If there is no surface, then reset the ratio
                setDepth(0f);
            }
        }
    }

    @Override
    public void setState(LauncherState toState) {
        if (mSurface == null || mIgnoreStateChangesDuringMultiWindowAnimation) {
            return;
        }

        float toDepth = toState.getDepth(mLauncher);
        if (Float.compare(mDepth, toDepth) != 0) {
            setDepth(toDepth);
        }
    }

    @Override
    public void setStateWithAnimation(LauncherState toState, StateAnimationConfig config,
            PendingAnimation animation) {
        if (mSurface == null
                || config.onlyPlayAtomicComponent()
                || config.hasAnimationFlag(SKIP_DEPTH_CONTROLLER)
                || mIgnoreStateChangesDuringMultiWindowAnimation) {
            return;
        }

        float toDepth = toState.getDepth(mLauncher);
        if (Float.compare(mDepth, toDepth) != 0) {
            animation.setFloat(this, DEPTH, toDepth, config.getInterpolator(ANIM_DEPTH, LINEAR));
        }
    }

    private void setDepth(float depth) {
        depth = Utilities.boundToRange(depth, 0, 1);
        // Round out the depth to dedupe frequent, non-perceptable updates
        int depthI = (int) (depth * 256);
        float depthF = depthI / 256f;
        if (Float.compare(mDepth, depthF) == 0) {
            return;
        }

        boolean supportsBlur = BlurUtils.supportsBlursOnWindows();
        if (supportsBlur && (mSurface == null || !mSurface.isValid())) {
            return;
        }
        mDepth = depthF;
        ensureDependencies();
        IBinder windowToken = mLauncher.getRootView().getWindowToken();
        if (windowToken != null) {
            mWallpaperManager.setWallpaperZoomOut(windowToken, mDepth);
        }

        if (supportsBlur) {
            final int blur;
            if (mLauncher.isInState(LauncherState.ALL_APPS) && mDepth == 1) {
                // All apps has a solid background. We don't need to draw blurs after it's fully
                // visible. This will take us out of GPU composition, saving battery and increasing
                // performance.
                blur = 0;
            } else {
                blur = (int) (mDepth * mMaxBlurRadius);
            }
            new TransactionCompat()
                    .setBackgroundBlurRadius(mSurface, blur)
                    .apply();
        }
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        mIgnoreStateChangesDuringMultiWindowAnimation = true;

        ObjectAnimator mwAnimation = ObjectAnimator.ofFloat(this, DEPTH,
                mLauncher.getStateManager().getState().getDepth(mLauncher, isInMultiWindowMode))
                .setDuration(300);
        mwAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mIgnoreStateChangesDuringMultiWindowAnimation = false;
            }
        });
        mwAnimation.setAutoCancel(true);
        mwAnimation.start();
    }
}

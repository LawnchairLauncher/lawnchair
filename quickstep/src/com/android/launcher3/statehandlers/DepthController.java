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
import android.os.SystemProperties;
import android.util.FloatProperty;
import android.view.CrossWindowBlurListeners;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewRootImpl;
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
import com.android.systemui.shared.system.WallpaperManagerCompat;

import java.util.function.Consumer;

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
                    ViewRootImpl viewRootImpl = view.getViewRootImpl();
                    setSurface(viewRootImpl != null ? viewRootImpl.getSurfaceControl() : null);
                    view.post(() -> view.getViewTreeObserver().removeOnDrawListener(this));
                }
            };

    private final Consumer<Boolean> mCrossWindowBlurListener = new Consumer<Boolean>() {
        @Override
        public void accept(Boolean enabled) {
            mCrossWindowBlursEnabled = enabled;
            dispatchTransactionSurface(mDepth);
        }
    };

    private final Launcher mLauncher;
    /**
     * Blur radius when completely zoomed out, in pixels.
     */
    private int mMaxBlurRadius;
    private boolean mCrossWindowBlursEnabled;
    private WallpaperManagerCompat mWallpaperManager;
    private SurfaceControl mSurface;
    /**
     * Ratio from 0 to 1, where 0 is fully zoomed out, and 1 is zoomed in.
     * @see android.service.wallpaper.WallpaperService.Engine#onZoomChanged(float)
     */
    private float mDepth;
    /**
     * If we're launching and app and should not be blurring the screen for performance reasons.
     */
    private boolean mBlurDisabledForAppLaunch;

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
                    CrossWindowBlurListeners.getInstance().addListener(mLauncher.getMainExecutor(),
                            mCrossWindowBlurListener);
                }

                @Override
                public void onViewDetachedFromWindow(View view) {
                    CrossWindowBlurListeners.getInstance().removeListener(mCrossWindowBlurListener);
                }
            };
            mLauncher.getRootView().addOnAttachStateChangeListener(mOnAttachListener);
            if (mLauncher.getRootView().isAttachedToWindow()) {
                CrossWindowBlurListeners.getInstance().addListener(mLauncher.getMainExecutor(),
                        mCrossWindowBlurListener);
            }
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
    public void setSurface(SurfaceControl surface) {
        // Set launcher as the SurfaceControl when we don't need an external target anymore.
        if (surface == null) {
            ViewRootImpl viewRootImpl = mLauncher.getDragLayer().getViewRootImpl();
            surface = viewRootImpl != null ? viewRootImpl.getSurfaceControl() : null;
        }

        if (mSurface != surface) {
            mSurface = surface;
            if (surface != null) {
                dispatchTransactionSurface(mDepth);
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
        } else if (toState == LauncherState.OVERVIEW) {
            dispatchTransactionSurface(mDepth);
        }
    }

    @Override
    public void setStateWithAnimation(LauncherState toState, StateAnimationConfig config,
            PendingAnimation animation) {
        if (config.hasAnimationFlag(SKIP_DEPTH_CONTROLLER)
                || mIgnoreStateChangesDuringMultiWindowAnimation) {
            return;
        }

        float toDepth = toState.getDepth(mLauncher);
        if (Float.compare(mDepth, toDepth) != 0) {
            animation.setFloat(this, DEPTH, toDepth, config.getInterpolator(ANIM_DEPTH, LINEAR));
        }
    }

    /**
     * If we're launching an app from the home screen.
     */
    public void setIsInLaunchTransition(boolean inLaunchTransition) {
        boolean blurEnabled = SystemProperties.getBoolean("ro.launcher.blur.appLaunch", true);
        mBlurDisabledForAppLaunch = inLaunchTransition && !blurEnabled;
        if (!inLaunchTransition) {
            // Reset depth at the end of the launch animation, so the wallpaper won't be
            // zoomed out if an app crashes.
            setDepth(0f);
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
        if (dispatchTransactionSurface(depthF)) {
            mDepth = depthF;
        }
    }

    private boolean dispatchTransactionSurface(float depth) {
        boolean supportsBlur = BlurUtils.supportsBlursOnWindows();
        if (supportsBlur && (mSurface == null || !mSurface.isValid())) {
            return false;
        }
        ensureDependencies();
        IBinder windowToken = mLauncher.getRootView().getWindowToken();
        if (windowToken != null) {
            mWallpaperManager.setWallpaperZoomOut(windowToken, depth);
        }

        if (supportsBlur) {
            // We cannot mark the window as opaque in overview because there will be an app window
            // below the launcher layer, and we need to draw it -- without blurs.
            boolean isOverview = mLauncher.isInState(LauncherState.OVERVIEW);
            boolean opaque = mLauncher.getScrimView().isFullyOpaque() && !isOverview;

            int blur = opaque || isOverview || !mCrossWindowBlursEnabled
                    || mBlurDisabledForAppLaunch ? 0 : (int) (depth * mMaxBlurRadius);
            new SurfaceControl.Transaction()
                    .setBackgroundBlurRadius(mSurface, blur)
                    .setOpaque(mSurface, opaque)
                    .apply();
        }
        return true;
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

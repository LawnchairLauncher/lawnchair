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
import android.view.AttachedSurfaceControl;
import android.view.CrossWindowBlurListeners;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewRootImpl;
import android.view.ViewTreeObserver;

import androidx.datastore.preferences.core.Preferences;

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
import com.patrykmichalik.opto.domain.Preference;
import com.patrykmichalik.opto.core.PreferenceExtensionsKt;

import app.lawnchair.LawnchairApp;
import app.lawnchair.preferences.BasePreferenceManager;
import app.lawnchair.preferences.PreferenceManager;
import app.lawnchair.preferences2.PreferenceManager2;

import java.io.PrintWriter;
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
                    boolean applied = setSurface(
                            viewRootImpl != null ? viewRootImpl.getSurfaceControl() : null);
                    if (!applied) {
                        dispatchTransactionSurface(mDepth);
                    }
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

    private final Runnable mOpaquenessListener = new Runnable() {
        @Override
        public void run() {
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
     * How visible the -1 overlay is, from 0 to 1.
     */
    private float mOverlayScrollProgress;
    /**
     * Ratio from 0 to 1, where 0 is fully zoomed out, and 1 is zoomed in.
     * @see android.service.wallpaper.WallpaperService.Engine#onZoomChanged(float)
     */
    private float mDepth;
    /**
     * Last blur value, in pixels, that was applied.
     * For debugging purposes.
     */
    private int mCurrentBlur;
    /**
     * If we're launching and app and should not be blurring the screen for performance reasons.
     */
    private boolean mBlurDisabledForAppLaunch;
    /**
     * If we requested early wake-up offsets to SurfaceFlinger.
     */
    private boolean mInEarlyWakeUp;

    // Workaround for animating the depth when multiwindow mode changes.
    private boolean mIgnoreStateChangesDuringMultiWindowAnimation = false;

    // Hints that there is potentially content behind Launcher and that we shouldn't optimize by
    // marking the launcher surface as opaque.  Only used in certain Launcher states.
    private boolean mHasContentBehindLauncher;

    private View.OnAttachStateChangeListener mOnAttachListener;

    private BasePreferenceManager.FloatPref mDrawerOpacity;
    private final boolean mEnableDepth;

    public DepthController(Launcher l) {
        mLauncher = l;
        // TODO: No need to use app context here after merging Android 13 branch.
        // https://cs.android.com/android/platform/superproject/+/master:packages/apps/Launcher3/quickstep/src/com/android/launcher3/uioverrides/QuickstepLauncher.java;drc=eef0b1640bbc8c26c824836271a71b929726e895;l=175
        Preference<Boolean, Boolean, ?> depthPref = PreferenceManager2.getInstance(LawnchairApp.getInstance()).getWallpaperDepthEffect();
        mEnableDepth = PreferenceExtensionsKt.firstBlocking(depthPref);
    }

    private void ensureDependencies() {
        if (mWallpaperManager == null) {
            mMaxBlurRadius = mLauncher.getResources().getInteger(R.integer.max_depth_blur_radius);
            mWallpaperManager = new WallpaperManagerCompat(mLauncher);
        }

        if (Utilities.ATLEAST_R && mLauncher.getRootView() != null && mOnAttachListener == null) {
            mOnAttachListener = new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View view) {
                    // To handle the case where window token is invalid during last setDepth call.
                    IBinder windowToken = mLauncher.getRootView().getWindowToken();
                    if (windowToken != null) {
                        mWallpaperManager.setWallpaperZoomOut(windowToken, mEnableDepth ? mDepth : 1f);
                    }
                    onAttached();
                }

                @Override
                public void onViewDetachedFromWindow(View view) {
                    if (Utilities.ATLEAST_S) {
                        CrossWindowBlurListeners.getInstance().removeListener(mCrossWindowBlurListener);
                    }
                    mLauncher.getScrimView().removeOpaquenessListener(mOpaquenessListener);
                }
            };
            mLauncher.getRootView().addOnAttachStateChangeListener(mOnAttachListener);
            if (mLauncher.getRootView().isAttachedToWindow()) {
                onAttached();
            }
        }
        if (mDrawerOpacity == null) {
            mDrawerOpacity = PreferenceManager.getInstance(mLauncher).getDrawerOpacity();
        }
    }

    private void onAttached() {
        if (Utilities.ATLEAST_S) {
            CrossWindowBlurListeners.getInstance().addListener(mLauncher.getMainExecutor(),
                    mCrossWindowBlurListener);
        }
        mLauncher.getScrimView().addOpaquenessListener(mOpaquenessListener);
    }

    public void setHasContentBehindLauncher(boolean hasContentBehindLauncher) {
        mHasContentBehindLauncher = hasContentBehindLauncher;
    }

    /**
     * Sets if the underlying activity is started or not
     */
    public void setActivityStarted(boolean isStarted) {
        if (!Utilities.ATLEAST_R) return;
        if (isStarted) {
            mLauncher.getDragLayer().getViewTreeObserver().addOnDrawListener(mOnDrawListener);
        } else {
            mLauncher.getDragLayer().getViewTreeObserver().removeOnDrawListener(mOnDrawListener);
            setSurface(null);
        }
    }

    /**
     * Sets the specified app target surface to apply the blur to.
     * @return true when surface was valid and transaction was dispatched.
     */
    public boolean setSurface(SurfaceControl surface) {
        // Set launcher as the SurfaceControl when we don't need an external target anymore.
        if (surface == null) {
            ViewRootImpl viewRootImpl = mLauncher.getDragLayer().getViewRootImpl();
            surface = viewRootImpl != null ? viewRootImpl.getSurfaceControl() : null;
        }
        if (mSurface != surface) {
            mSurface = surface;
            if (surface != null) {
                dispatchTransactionSurface(mDepth);
                return true;
            }
        }
        return false;
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
        } else if (toState == LauncherState.BACKGROUND_APP) {
            mLauncher.getDragLayer().getViewTreeObserver().addOnDrawListener(mOnDrawListener);
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

    public void reapplyDepth() {
        LauncherState toState = mLauncher.getStateManager().getState();
        float toDepth = toState.getDepth(mLauncher);
        setDepth(toDepth, true);
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
        setDepth(depth, false);
    }

    private void setDepth(float depth, boolean force) {
        if (!Utilities.ATLEAST_R) return;
        depth = Utilities.boundToRange(depth, 0, 1);
        // Round out the depth to dedupe frequent, non-perceptable updates
        int depthI = (int) (depth * 256);
        float depthF = depthI / 256f;
        if (Float.compare(mDepth, depthF) == 0 && !force) {
            return;
        }
        dispatchTransactionSurface(depthF);
        mDepth = depthF;
    }

    public void onOverlayScrollChanged(float progress) {
        if (!Utilities.ATLEAST_R) return;
        // Round out the progress to dedupe frequent, non-perceptable updates
        int progressI = (int) (progress * 256);
        float progressF = Utilities.boundToRange(progressI / 256f, 0f, 1f);
        if (Float.compare(mOverlayScrollProgress, progressF) == 0) {
            return;
        }
        mOverlayScrollProgress = progressF;
        dispatchTransactionSurface(mDepth);
    }

    private boolean dispatchTransactionSurface(float depth) {
        boolean supportsBlur = BlurUtils.supportsBlursOnWindows();
        if (supportsBlur && (mSurface == null || !mSurface.isValid())) {
            return false;
        }
        ensureDependencies();
        depth = Math.max(depth, mOverlayScrollProgress);
        IBinder windowToken = mLauncher.getRootView().getWindowToken();
        if (windowToken != null) {
            mWallpaperManager.setWallpaperZoomOut(windowToken, mEnableDepth ? depth : 1f);
        }

        if (supportsBlur) {
            boolean hasOpaqueBg = mLauncher.getScrimView().isFullyOpaque();
            boolean isSurfaceOpaque = !mHasContentBehindLauncher && hasOpaqueBg;

            mCurrentBlur = !mCrossWindowBlursEnabled || mBlurDisabledForAppLaunch || hasOpaqueBg
                    ? 0 : (int) (depth * mMaxBlurRadius);
            SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()
                    .setBackgroundBlurRadius(mSurface, mCurrentBlur)
                    .setOpaque(mSurface, isSurfaceOpaque);

            // Set early wake-up flags when we know we're executing an expensive operation, this way
            // SurfaceFlinger will adjust its internal offsets to avoid jank.
            boolean wantsEarlyWakeUp = depth > 0 && depth < 1;
            if (wantsEarlyWakeUp && !mInEarlyWakeUp) {
                transaction.setEarlyWakeupStart();
                mInEarlyWakeUp = true;
            } else if (!wantsEarlyWakeUp && mInEarlyWakeUp) {
                transaction.setEarlyWakeupEnd();
                mInEarlyWakeUp = false;
            }

            AttachedSurfaceControl rootSurfaceControl =
                    mLauncher.getRootView().getRootSurfaceControl();
            if (rootSurfaceControl != null) {
                rootSurfaceControl.applyTransactionOnDraw(transaction);
            }
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

    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + this.getClass().getSimpleName());
        writer.println(prefix + "\tmMaxBlurRadius=" + mMaxBlurRadius);
        writer.println(prefix + "\tmCrossWindowBlursEnabled=" + mCrossWindowBlursEnabled);
        writer.println(prefix + "\tmSurface=" + mSurface);
        writer.println(prefix + "\tmOverlayScrollProgress=" + mOverlayScrollProgress);
        writer.println(prefix + "\tmDepth=" + mDepth);
        writer.println(prefix + "\tmCurrentBlur=" + mCurrentBlur);
        writer.println(prefix + "\tmBlurDisabledForAppLaunch=" + mBlurDisabledForAppLaunch);
        writer.println(prefix + "\tmInEarlyWakeUp=" + mInEarlyWakeUp);
        writer.println(prefix + "\tmIgnoreStateChangesDuringMultiWindowAnimation="
                + mIgnoreStateChangesDuringMultiWindowAnimation);
    }
}

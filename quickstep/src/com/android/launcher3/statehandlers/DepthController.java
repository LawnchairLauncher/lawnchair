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

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_DEPTH;
import static com.android.launcher3.states.StateAnimationConfig.SKIP_DEPTH_CONTROLLER;
import static com.android.launcher3.util.MultiPropertyFactory.MULTI_PROPERTY_VALUE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.CrossWindowBlurListeners;
import android.view.View;
import android.view.ViewRootImpl;
import android.view.ViewTreeObserver;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.quickstep.util.BaseDepthController;
import com.patrykmichalik.opto.core.PreferenceExtensionsKt;

import java.io.PrintWriter;
import java.util.function.Consumer;

import app.lawnchair.compat.LawnchairQuickstepCompat;
import app.lawnchair.preferences2.PreferenceManager2;

/**
 * Controls blur and wallpaper zoom, for the Launcher surface only.
 */
public class DepthController extends BaseDepthController implements StateHandler<LauncherState>,
        BaseActivity.MultiWindowModeChangedListener {

    private final ViewTreeObserver.OnDrawListener mOnDrawListener = this::onLauncherDraw;

    private final Consumer<Boolean> mCrossWindowBlurListener = this::setCrossWindowBlursEnabled;

    private final Runnable mOpaquenessListener = this::applyDepthAndBlur;

    // Workaround for animating the depth when multiwindow mode changes.
    private boolean mIgnoreStateChangesDuringMultiWindowAnimation = false;

    private View.OnAttachStateChangeListener mOnAttachListener;

    private final boolean mEnableDepth;

    public DepthController(Launcher l) {
        super(l);
        var pref = PreferenceManager2.getInstance(l).getWallpaperDepthEffect();
        mEnableDepth = PreferenceExtensionsKt.firstBlocking(pref);
    }

    private void onLauncherDraw() {
        View view = mLauncher.getDragLayer();
        ViewRootImpl viewRootImpl = view.getViewRootImpl();
        if (Utilities.ATLEAST_Q) {
            setSurface(viewRootImpl != null ? viewRootImpl.getSurfaceControl() : null);
        }
        view.post(() -> view.getViewTreeObserver().removeOnDrawListener(mOnDrawListener));
    }

    private void ensureDependencies() {
        if (mLauncher.getRootView() != null && mOnAttachListener == null) {
            View rootView = mLauncher.getRootView();
            mOnAttachListener = new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View view) {
                    if (LawnchairQuickstepCompat.ATLEAST_S) {
                        CrossWindowBlurListeners.getInstance().addListener(mLauncher.getMainExecutor(),
                                mCrossWindowBlurListener);
                    }

                    mLauncher.getScrimView().addOpaquenessListener(mOpaquenessListener);

                    // To handle the case where window token is invalid during last setDepth call.
                    applyDepthAndBlur();
                }

                @Override
                public void onViewDetachedFromWindow(View view) {
                    removeSecondaryListeners();
                }
            };
            rootView.addOnAttachStateChangeListener(mOnAttachListener);
            if (rootView.isAttachedToWindow()) {
                mOnAttachListener.onViewAttachedToWindow(rootView);
            }
        }
    }

    /**
     * Cleans up after this controller so it can be garbage collected without
     * leaving traces.
     */
    public void dispose() {
        removeSecondaryListeners();

        if (mLauncher.getRootView() != null && mOnAttachListener != null) {
            mLauncher.getRootView().removeOnAttachStateChangeListener(mOnAttachListener);
            mOnAttachListener = null;
        }
    }

    private void removeSecondaryListeners() {
        if (mCrossWindowBlurListener != null && LawnchairQuickstepCompat.ATLEAST_S) {
            CrossWindowBlurListeners.getInstance().removeListener(mCrossWindowBlurListener);
        }
        if (mOpaquenessListener != null) {
            mLauncher.getScrimView().removeOpaquenessListener(mOpaquenessListener);
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

    @Override
    public void setState(LauncherState toState) {
        if (mIgnoreStateChangesDuringMultiWindowAnimation) {
            return;
        }

        stateDepth.setValue(toState.getDepth(mLauncher));
        if (toState == LauncherState.BACKGROUND_APP) {
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
        animation.setFloat(stateDepth, MULTI_PROPERTY_VALUE, toDepth,
                config.getInterpolator(ANIM_DEPTH, LINEAR));
    }

    @Override
    public void applyDepthAndBlur() {
        try {
            if (LawnchairQuickstepCompat.ATLEAST_R && mEnableDepth) {
                ensureDependencies();
                super.applyDepthAndBlur();
            }
        } catch (Throwable t) {
            // Ignore
        }
    }

    @Override
    protected void onInvalidSurface() {
        // Lets wait for surface to become valid again
        mLauncher.getDragLayer().getViewTreeObserver().addOnDrawListener(mOnDrawListener);
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        mIgnoreStateChangesDuringMultiWindowAnimation = true;

        ObjectAnimator mwAnimation = ObjectAnimator.ofFloat(stateDepth, MULTI_PROPERTY_VALUE,
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
        writer.println(prefix + "DepthController");
        writer.println(prefix + "\tmMaxBlurRadius=" + mMaxBlurRadius);
        writer.println(prefix + "\tmCrossWindowBlursEnabled=" + mCrossWindowBlursEnabled);
        writer.println(prefix + "\tmSurface=" + mSurface);
        writer.println(prefix + "\tmStateDepth=" + stateDepth.getValue());
        writer.println(prefix + "\tmWidgetDepth=" + widgetDepth.getValue());
        writer.println(prefix + "\tmCurrentBlur=" + mCurrentBlur);
        writer.println(prefix + "\tmInEarlyWakeUp=" + mInEarlyWakeUp);
        writer.println(prefix + "\tmIgnoreStateChangesDuringMultiWindowAnimation="
                + mIgnoreStateChangesDuringMultiWindowAnimation);
        writer.println(prefix + "\tmPauseBlurs=" + mPauseBlurs);
    }
}
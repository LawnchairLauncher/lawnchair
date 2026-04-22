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

package com.android.launcher3.allapps;

import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.util.OnboardingPrefs.HOME_BOUNCE_SEEN;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.os.Handler;
import android.os.UserManager;
import android.view.MotionEvent;
import android.view.View;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.statemanager.StateManager.StateListener;
import com.android.launcher3.util.OnboardingPrefs;

/**
 * Abstract base class of floating view responsible for showing discovery bounce animation
 */
public class DiscoveryBounce extends AbstractFloatingView {

    private static final long DELAY_MS = 450;

    private final Launcher mLauncher;
    private final Animator mDiscoBounceAnimation;

    private final StateListener<LauncherState> mStateListener = new StateListener<LauncherState>() {
        @Override
        public void onStateTransitionStart(LauncherState toState) {
            handleClose(false);
        }

        @Override
        public void onStateTransitionComplete(LauncherState finalState) {}
    };

    public DiscoveryBounce(Launcher launcher) {
        super(launcher, null);
        mLauncher = launcher;

        mDiscoBounceAnimation =
                AnimatorInflater.loadAnimator(launcher, R.animator.discovery_bounce);
        mDiscoBounceAnimation.setTarget(new VerticalProgressWrapper(
                launcher.getHotseat(), mLauncher.getDragLayer().getHeight()));
        mDiscoBounceAnimation.addListener(AnimatorListeners.forEndCallback(this::handleClose));
        launcher.getStateManager().addStateListener(mStateListener);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mDiscoBounceAnimation.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mDiscoBounceAnimation.isRunning()) {
            mDiscoBounceAnimation.end();
        }
    }

    @Override
    public boolean canHandleBack() {
        // Since DiscoveryBounce doesn't handle back, onBackInvoked() won't be called and we should
        // close it without animation.
        close(false);
        return false;
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        handleClose(false);
        return false;
    }

    @Override
    protected void handleClose(boolean animate) {
        if (mIsOpen) {
            mIsOpen = false;
            mLauncher.getDragLayer().removeView(this);
            // Reset the translation to what ever it was previously.
            mLauncher.getHotseat().setTranslationY(mLauncher.getStateManager().getState()
                    .getHotseatScaleAndTranslation(mLauncher).translationY);
            mLauncher.getStateManager().removeStateListener(mStateListener);
        }
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_DISCOVERY_BOUNCE) != 0;
    }

    private void show() {
        mIsOpen = true;
        mLauncher.getDragLayer().addView(this);
        // TODO: add WW log for discovery bounce tip show event.
    }

    public static void showForHomeIfNeeded(Launcher launcher) {
        showForHomeIfNeeded(launcher, true);
    }

    private static void showForHomeIfNeeded(Launcher launcher, boolean withDelay) {
        if (!launcher.isInState(NORMAL)
                || HOME_BOUNCE_SEEN.get(launcher)
                || AbstractFloatingView.getTopOpenView(launcher) != null
                || launcher.getSystemService(UserManager.class).isDemoUser()
                || Utilities.isRunningInTestHarness()) {
            return;
        }

        if (withDelay) {
            new Handler().postDelayed(() -> showForHomeIfNeeded(launcher, false), DELAY_MS);
            return;
        }
        OnboardingPrefs.HOME_BOUNCE_COUNT.increment(launcher);
        new DiscoveryBounce(launcher).show();
    }

    /**
     * A wrapper around hotseat animator allowing a fixed shift in the value.
     */
    public static class VerticalProgressWrapper {

        private final View mView;
        private final float mLimit;

        private VerticalProgressWrapper(View view, float limit) {
            mView = view;
            mLimit = limit;
        }

        public float getProgress() {
            return 1 + mView.getTranslationY() / mLimit;
        }

        public void setProgress(float progress) {
            mView.setTranslationY(mLimit * (progress - 1));
        }
    }
}

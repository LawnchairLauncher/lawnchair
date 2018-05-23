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
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType.HOTSEAT;
import static com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType.PREDICTION;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.app.ActivityManager;
import android.os.Handler;
import android.view.MotionEvent;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.states.InternalStateHandler;

/**
 * Abstract base class of floating view responsible for showing discovery bounce animation
 */
public class DiscoveryBounce extends AbstractFloatingView {

    private static final long DELAY_MS = 450;

    public static final String HOME_BOUNCE_SEEN = "launcher.apps_view_shown";
    public static final String SHELF_BOUNCE_SEEN = "launcher.shelf_bounce_seen";

    private final Launcher mLauncher;
    private final Animator mDiscoBounceAnimation;

    public DiscoveryBounce(Launcher launcher, float delta) {
        super(launcher, null);
        mLauncher = launcher;
        AllAppsTransitionController controller = mLauncher.getAllAppsController();

        mDiscoBounceAnimation =
                AnimatorInflater.loadAnimator(launcher, R.animator.discovery_bounce);
        mDiscoBounceAnimation.setTarget(new VerticalProgressWrapper(controller, delta));
        mDiscoBounceAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                handleClose(false);
            }
        });
        mDiscoBounceAnimation.addListener(controller.getProgressAnimatorListener());
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
    public boolean onBackPressed() {
        super.onBackPressed();
        // Go back to the previous state (from a user's perspective this floating view isn't
        // something to go back from).
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
            // Reset the all-apps progress to what ever it was previously.
            mLauncher.getAllAppsController().setProgress(mLauncher.getStateManager()
                    .getState().getVerticalProgress(mLauncher));
        }
    }

    @Override
    public void logActionCommand(int command) {
        // Since this is on-boarding popup, it is not a user controlled action.
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_DISCOVERY_BOUNCE) != 0;
    }

    private void show(int containerType) {
        mIsOpen = true;
        mLauncher.getDragLayer().addView(this);
        mLauncher.getUserEventDispatcher().logActionBounceTip(containerType);
    }

    public static void showForHomeIfNeeded(Launcher launcher) {
        showForHomeIfNeeded(launcher, true);
    }

    private static void showForHomeIfNeeded(Launcher launcher, boolean withDelay) {
        if (!launcher.isInState(NORMAL)
                || launcher.getSharedPrefs().getBoolean(HOME_BOUNCE_SEEN, false)
                || AbstractFloatingView.getTopOpenView(launcher) != null
                || UserManagerCompat.getInstance(launcher).isDemoUser()
                || ActivityManager.isRunningInTestHarness()) {
            return;
        }

        if (withDelay) {
            new Handler().postDelayed(() -> showForHomeIfNeeded(launcher, false), DELAY_MS);
            return;
        }

        new DiscoveryBounce(launcher, 0).show(HOTSEAT);
    }

    public static void showForOverviewIfNeeded(Launcher launcher) {
        showForOverviewIfNeeded(launcher, true);
    }

    private static void showForOverviewIfNeeded(Launcher launcher, boolean withDelay) {
        if (!launcher.isInState(OVERVIEW)
                || !launcher.hasBeenResumed()
                || launcher.isForceInvisible()
                || launcher.getDeviceProfile().isVerticalBarLayout()
                || launcher.getSharedPrefs().getBoolean(SHELF_BOUNCE_SEEN, false)
                || UserManagerCompat.getInstance(launcher).isDemoUser()
                || ActivityManager.isRunningInTestHarness()) {
            return;
        }

        if (withDelay) {
            new Handler().postDelayed(() -> showForOverviewIfNeeded(launcher, false), DELAY_MS);
            return;
        } else if (InternalStateHandler.hasPending()
                || AbstractFloatingView.getTopOpenView(launcher) != null) {
            // TODO: Move these checks to the top and call this method after invalidate handler.
            return;
        }

        new DiscoveryBounce(launcher, (1 - OVERVIEW.getVerticalProgress(launcher)))
                .show(PREDICTION);
    }

    /**
     * A wrapper around {@link AllAppsTransitionController} allowing a fixed shift in the value.
     */
    public static class VerticalProgressWrapper {

        private final float mDelta;
        private final AllAppsTransitionController mController;

        private VerticalProgressWrapper(AllAppsTransitionController controller, float delta) {
            mController = controller;
            mDelta = delta;
        }

        public float getProgress() {
            return mController.getProgress() + mDelta;
        }

        public void setProgress(float progress) {
            mController.setProgress(progress - mDelta);
        }
    }
}

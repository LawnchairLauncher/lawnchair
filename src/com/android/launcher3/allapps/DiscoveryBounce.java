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

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.app.ActivityManager;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.animation.PathInterpolator;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.states.InternalStateHandler;

/**
 * Abstract base class of floating view responsible for showing discovery bounce animation
 */
public class DiscoveryBounce extends AbstractFloatingView {

    private static final long DELAY_MS = 200;

    public static final String HOME_BOUNCE_SEEN = "launcher.apps_view_shown";
    public static final String SHELF_BOUNCE_SEEN = "launcher.shelf_bounce_seen";

    private final Launcher mLauncher;
    private final Animator mDiscoBounceAnimation;

    public DiscoveryBounce(Launcher launcher, Animator animator) {
        super(launcher, null);
        mLauncher = launcher;

        mDiscoBounceAnimation = animator;
        AllAppsTransitionController controller = mLauncher.getAllAppsController();
        mDiscoBounceAnimation.setTarget(controller);
        mDiscoBounceAnimation.addListener(controller.getProgressAnimatorListener());

        mDiscoBounceAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                handleClose(false);
            }
        });
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
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        handleClose(false);
        return false;
    }

    @Override
    protected void handleClose(boolean animate) {
        if (mIsOpen) {
            mIsOpen = false;
            mLauncher.getDragLayer().removeView(this);
        }
    }

    @Override
    public void logActionCommand(int command) {
        // Since this is on-boarding popup, it is not a user controlled action.
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_ON_BOARD_POPUP) != 0;
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

        DiscoveryBounce view = new DiscoveryBounce(launcher,
                AnimatorInflater.loadAnimator(launcher, R.animator.discovery_bounce));
        view.mIsOpen = true;
        launcher.getDragLayer().addView(view);
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

        float verticalProgress = OVERVIEW.getVerticalProgress(launcher);

        TimeInterpolator pathInterpolator = new PathInterpolator(0.35f, 0, 0.5f, 1);
        Keyframe keyframe3 = Keyframe.ofFloat(0.423f, verticalProgress - (1 - 0.9438f));
        keyframe3.setInterpolator(pathInterpolator);
        Keyframe keyframe4 = Keyframe.ofFloat(0.654f, verticalProgress);
        keyframe4.setInterpolator(pathInterpolator);

        PropertyValuesHolder propertyValuesHolder = PropertyValuesHolder.ofKeyframe("progress",
                Keyframe.ofFloat(0, verticalProgress),
                Keyframe.ofFloat(0.346f, verticalProgress), keyframe3, keyframe4,
                Keyframe.ofFloat(1f, verticalProgress));
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(null,
                new PropertyValuesHolder[]{propertyValuesHolder});
        animator.setDuration(2166);
        animator.setRepeatCount(5);

        DiscoveryBounce view = new DiscoveryBounce(launcher, animator);
        view.mIsOpen = true;
        launcher.getDragLayer().addView(view);
    }
}

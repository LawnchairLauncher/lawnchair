/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.view.MotionEvent;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.compat.UserManagerCompat;

/**
 * Floating view responsible for showing discovery bounce animation
 */
public class DiscoveryBounce extends AbstractFloatingView {

    public static final String APPS_VIEW_SHOWN = "launcher.apps_view_shown";

    private final Launcher mLauncher;
    private final Animator mDiscoBounceAnimation;

    public DiscoveryBounce(Launcher launcher) {
        super(launcher, null);
        mLauncher = launcher;

        mDiscoBounceAnimation = AnimatorInflater.loadAnimator(mLauncher,
                R.animator.discovery_bounce);
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

    public static void showIfNeeded(Launcher launcher) {
        if (!launcher.isInState(NORMAL)
                || launcher.getSharedPrefs().getBoolean(APPS_VIEW_SHOWN, false)
                || AbstractFloatingView.getTopOpenView(launcher) != null
                || UserManagerCompat.getInstance(launcher).isDemoUser()) {
            return;
        }

        DiscoveryBounce view = new DiscoveryBounce(launcher);
        view.mIsOpen = true;
        launcher.getDragLayer().addView(view);
    }
}

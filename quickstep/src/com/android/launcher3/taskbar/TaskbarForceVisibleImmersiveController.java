/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.launcher3.taskbar;

import static android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS;
import static android.view.accessibility.AccessibilityManager.FLAG_CONTENT_ICONS;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS;
import static android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK;

import static com.android.launcher3.taskbar.NavbarButtonsViewController.ALPHA_INDEX_IMMERSIVE_MODE;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_IMMERSIVE_MODE;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.TouchController;

/**
 * Controller for taskbar when force visible in immersive mode is set.
 */
public class TaskbarForceVisibleImmersiveController implements TouchController {
    private static final int NAV_BAR_ICONS_DIM_ANIMATION_START_DELAY_MS = 4500;
    private static final int NAV_BAR_ICONS_DIM_ANIMATION_DURATION_MS = 500;
    private static final int NAV_BAR_ICONS_UNDIM_ANIMATION_DURATION_MS = 250;
    private static final float NAV_BAR_ICONS_DIM_PCT = 0.15f;
    private static final float NAV_BAR_ICONS_UNDIM_PCT = 1f;

    private final TaskbarActivityContext mContext;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mDimmingRunnable = this::dimIcons;
    private final Runnable mUndimmingRunnable = this::undimIcons;
    private final AnimatedFloat mIconAlphaForDimming = new AnimatedFloat(
            this::updateIconDimmingAlpha);
    private final View.AccessibilityDelegate mKidsModeAccessibilityDelegate =
            new View.AccessibilityDelegate() {
                @Override
                public boolean performAccessibilityAction(View host, int action, Bundle args) {
                    if (action == ACTION_ACCESSIBILITY_FOCUS || action == ACTION_CLICK) {
                        // Animate undimming of icons on an a11y event, followed by starting the
                        // dimming animation (after its timeout has expired). Both can be called in
                        // succession, as the playing of the two animations in a row is managed by
                        // mHandler's message queue.
                        startIconUndimming();
                        startIconDimming();
                    }
                    return super.performAccessibilityAction(host, action, args);
                }
            };

    // Initialized in init.
    private TaskbarControllers mControllers;
    private boolean mIsImmersiveMode;

    public TaskbarForceVisibleImmersiveController(TaskbarActivityContext context) {
        mContext = context;
    }

    /**
     * Initialize controllers.
     */
    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
    }

    /** Update values tracked via sysui flags. */
    public void updateSysuiFlags(int sysuiFlags) {
        mIsImmersiveMode = (sysuiFlags & SYSUI_STATE_IMMERSIVE_MODE) != 0;
        if (mContext.isNavBarForceVisible()) {
            if (mIsImmersiveMode) {
                startIconDimming();
            } else {
                startIconUndimming();
            }
            mControllers.navbarButtonsViewController.setHomeButtonAccessibilityDelegate(
                    mKidsModeAccessibilityDelegate);
            mControllers.navbarButtonsViewController.setBackButtonAccessibilityDelegate(
                    mKidsModeAccessibilityDelegate);
        } else {
            mControllers.navbarButtonsViewController.setHomeButtonAccessibilityDelegate(null);
            mControllers.navbarButtonsViewController.setBackButtonAccessibilityDelegate(null);
        }
    }

    /** Clean up animations. */
    public void onDestroy() {
        startIconUndimming();
        mControllers.navbarButtonsViewController.setHomeButtonAccessibilityDelegate(null);
        mControllers.navbarButtonsViewController.setBackButtonAccessibilityDelegate(null);
    }

    private void startIconUndimming() {
        mHandler.removeCallbacks(mDimmingRunnable);
        mHandler.removeCallbacks(mUndimmingRunnable);
        mHandler.post(mUndimmingRunnable);
    }

    private void undimIcons() {
        mIconAlphaForDimming.animateToValue(NAV_BAR_ICONS_UNDIM_PCT).setDuration(
                NAV_BAR_ICONS_UNDIM_ANIMATION_DURATION_MS).start();
    }

    private void startIconDimming() {
        mHandler.removeCallbacks(mDimmingRunnable);
        int accessibilityDimmingTimeout = AccessibilityManagerCompat.getRecommendedTimeoutMillis(
                mContext, NAV_BAR_ICONS_DIM_ANIMATION_START_DELAY_MS,
                (FLAG_CONTENT_ICONS | FLAG_CONTENT_CONTROLS));
        mHandler.postDelayed(mDimmingRunnable, accessibilityDimmingTimeout);
    }

    private void dimIcons() {
        mIconAlphaForDimming.animateToValue(NAV_BAR_ICONS_DIM_PCT).setDuration(
                NAV_BAR_ICONS_DIM_ANIMATION_DURATION_MS).start();
    }

    /**
     * Returns whether the taskbar is always visible in immersive mode.
     */
    private boolean isNavbarShownInImmersiveMode() {
        return mIsImmersiveMode && mContext.isNavBarForceVisible();
    }

    private void updateIconDimmingAlpha() {
        if (mControllers == null || mControllers.navbarButtonsViewController == null) {
            return;
        }

        MultiPropertyFactory<View> ba =
                mControllers.navbarButtonsViewController.getBackButtonAlpha();
        if (ba != null) {
            ba.get(ALPHA_INDEX_IMMERSIVE_MODE).setValue(mIconAlphaForDimming.value);
        }
        MultiPropertyFactory<View> ha =
                mControllers.navbarButtonsViewController.getHomeButtonAlpha();
        if (ba != null) {
            ha.get(ALPHA_INDEX_IMMERSIVE_MODE).setValue(mIconAlphaForDimming.value);
        }
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (!isNavbarShownInImmersiveMode()
                || mControllers.taskbarStashController.supportsManualStashing()) {
            return false;
        }
        return onControllerTouchEvent(ev);
    }

    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startIconUndimming();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                startIconDimming();
                break;
        }
        return false;
    }
}

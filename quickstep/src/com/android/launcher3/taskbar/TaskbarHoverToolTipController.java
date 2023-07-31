/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.view.MotionEvent.ACTION_HOVER_ENTER;
import static android.view.MotionEvent.ACTION_HOVER_EXIT;
import static android.view.View.ALPHA;
import static android.view.View.SCALE_Y;
import static android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT;

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.AbstractFloatingView.TYPE_ALL_EXCEPT_ON_BOARD_POPUP;
import static com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_HOVERING_ICONS;
import static com.android.launcher3.views.ArrowTipView.TEXT_ALPHA;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.VisibleForTesting;

import com.android.app.animation.Interpolators;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.views.ArrowTipView;

/**
 * Controls showing a tooltip in the taskbar above each icon when it is hovered.
 */
public class TaskbarHoverToolTipController implements View.OnHoverListener {

    @VisibleForTesting protected static final int HOVER_TOOL_TIP_REVEAL_START_DELAY = 400;
    private static final int HOVER_TOOL_TIP_REVEAL_DURATION = 300;
    private static final int HOVER_TOOL_TIP_EXIT_DURATION = 150;

    private final Handler mHoverToolTipHandler = new Handler(Looper.getMainLooper());
    private final Runnable mRevealHoverToolTipRunnable = this::revealHoverToolTip;
    private final Runnable mHideHoverToolTipRunnable = this::hideHoverToolTip;

    private final TaskbarActivityContext mActivity;
    private final TaskbarView mTaskbarView;
    private final View mHoverView;
    private final ArrowTipView mHoverToolTipView;
    private final String mToolTipText;

    public TaskbarHoverToolTipController(TaskbarActivityContext activity, TaskbarView taskbarView,
            View hoverView) {
        mActivity = activity;
        mTaskbarView = taskbarView;
        mHoverView = hoverView;

        if (mHoverView instanceof BubbleTextView) {
            mToolTipText = ((BubbleTextView) mHoverView).getText().toString();
        } else if (mHoverView instanceof FolderIcon
                && ((FolderIcon) mHoverView).mInfo.title != null) {
            mToolTipText = ((FolderIcon) mHoverView).mInfo.title.toString();
        } else {
            mToolTipText = null;
        }

        ContextThemeWrapper arrowContextWrapper = new ContextThemeWrapper(mActivity,
                R.style.ArrowTipTaskbarStyle);
        mHoverToolTipView = new ArrowTipView(arrowContextWrapper, /* isPointingUp = */ false,
                R.layout.arrow_toast);

        AnimatorSet hoverCloseAnimator = new AnimatorSet();
        ObjectAnimator textCloseAnimator = ObjectAnimator.ofInt(mHoverToolTipView, TEXT_ALPHA, 0);
        textCloseAnimator.setInterpolator(Interpolators.clampToProgress(LINEAR, 0, 0.33f));
        ObjectAnimator alphaCloseAnimator = ObjectAnimator.ofFloat(mHoverToolTipView, ALPHA, 0);
        alphaCloseAnimator.setInterpolator(Interpolators.clampToProgress(LINEAR, 0.33f, 0.66f));
        ObjectAnimator scaleCloseAnimator = ObjectAnimator.ofFloat(mHoverToolTipView, SCALE_Y, 0);
        scaleCloseAnimator.setInterpolator(Interpolators.STANDARD);
        hoverCloseAnimator.playTogether(
                textCloseAnimator,
                alphaCloseAnimator,
                scaleCloseAnimator);
        hoverCloseAnimator.setStartDelay(0);
        hoverCloseAnimator.setDuration(HOVER_TOOL_TIP_EXIT_DURATION);
        mHoverToolTipView.setCustomCloseAnimation(hoverCloseAnimator);

        AnimatorSet hoverOpenAnimator = new AnimatorSet();
        ObjectAnimator textOpenAnimator = ObjectAnimator.ofInt(mHoverToolTipView, TEXT_ALPHA, 255);
        textOpenAnimator.setInterpolator(Interpolators.clampToProgress(LINEAR, 0.33f, 1f));
        ObjectAnimator scaleOpenAnimator = ObjectAnimator.ofFloat(mHoverToolTipView, SCALE_Y, 1f);
        scaleOpenAnimator.setInterpolator(Interpolators.EMPHASIZED);
        ObjectAnimator alphaOpenAnimator = ObjectAnimator.ofFloat(mHoverToolTipView, ALPHA, 1f);
        alphaOpenAnimator.setInterpolator(Interpolators.clampToProgress(LINEAR, 0.1f, 0.33f));
        hoverOpenAnimator.playTogether(
                scaleOpenAnimator,
                textOpenAnimator,
                alphaOpenAnimator);
        hoverOpenAnimator.setStartDelay(HOVER_TOOL_TIP_REVEAL_START_DELAY);
        hoverOpenAnimator.setDuration(HOVER_TOOL_TIP_REVEAL_DURATION);
        mHoverToolTipView.setCustomOpenAnimation(hoverOpenAnimator);

        mHoverToolTipView.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    mHoverToolTipView.setPivotY(bottom);
                    mHoverToolTipView.setY(mTaskbarView.getTop() - (bottom - top));
                });
        mHoverToolTipView.setScaleY(0f);
        mHoverToolTipView.setAlpha(0f);
    }

    @Override
    public boolean onHover(View v, MotionEvent event) {
        boolean isAnyOtherFloatingViewOpen =
                AbstractFloatingView.hasOpenView(mActivity, TYPE_ALL_EXCEPT_ON_BOARD_POPUP);
        if (isAnyOtherFloatingViewOpen) {
            mHoverToolTipHandler.removeCallbacksAndMessages(null);
        }
        // If hover leaves a taskbar icon animate the tooltip closed.
        if (event.getAction() == ACTION_HOVER_EXIT) {
            startHideHoverToolTip();
            mActivity.setAutohideSuspendFlag(FLAG_AUTOHIDE_SUSPEND_HOVERING_ICONS, false);
            return true;
        } else if (!isAnyOtherFloatingViewOpen && event.getAction() == ACTION_HOVER_ENTER) {
            // If hovering above a taskbar icon starts, animate the tooltip open. Do not
            // reveal if any floating views such as folders or edu pop-ups are open.
            startRevealHoverToolTip();
            mActivity.setAutohideSuspendFlag(FLAG_AUTOHIDE_SUSPEND_HOVERING_ICONS, true);
            return true;
        }
        return false;
    }

    private void startRevealHoverToolTip() {
        mHoverToolTipHandler.postDelayed(mRevealHoverToolTipRunnable,
                HOVER_TOOL_TIP_REVEAL_START_DELAY);
    }

    private void revealHoverToolTip() {
        if (mHoverView == null || mToolTipText == null) {
            return;
        }
        if (mHoverView instanceof FolderIcon && !((FolderIcon) mHoverView).getIconVisible()) {
            return;
        }
        mActivity.setTaskbarWindowFullscreen(true);
        Rect iconViewBounds = Utilities.getViewBounds(mHoverView);
        mHoverToolTipView.showAtLocation(mToolTipText, iconViewBounds.centerX(),
                mTaskbarView.getTop(), /* shouldAutoClose= */ false);
    }

    private void startHideHoverToolTip() {
        mHoverToolTipHandler.removeCallbacks(mRevealHoverToolTipRunnable);
        int accessibilityHideTimeout = AccessibilityManagerCompat.getRecommendedTimeoutMillis(
                mActivity, /* originalTimeout= */ 0, FLAG_CONTENT_TEXT);
        mHoverToolTipHandler.postDelayed(mHideHoverToolTipRunnable, accessibilityHideTimeout);
    }

    private void hideHoverToolTip() {
        mHoverToolTipView.close(/* animate = */ true);
    }
}

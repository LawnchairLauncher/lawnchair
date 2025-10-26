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

import static com.android.launcher3.AbstractFloatingView.TYPE_ACTION_POPUP;
import static com.android.launcher3.AbstractFloatingView.TYPE_FOLDER;
import static com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_HOVERING_ICONS;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Rect;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.views.ArrowTipView;

/**
 * Controls showing a tooltip in the taskbar above each icon when it is hovered.
 */
public class TaskbarHoverToolTipController implements View.OnHoverListener {
    // Short duration to reveal tooltip, as it is positioned in the x/y via a post() call in
    // parallel with the open animation. An instant animation could show in the wrong location.
    private static final int HOVER_TOOL_TIP_REVEAL_DURATION = 15;

    private final TaskbarActivityContext mActivity;
    private final TaskbarView mTaskbarView;
    private final View mHoverView;
    private final ArrowTipView mHoverToolTipView;
    private final String mToolTipText;
    private final int mYOffset;

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
        } else if (mHoverView instanceof AppPairIcon) {
            mToolTipText = ((AppPairIcon) mHoverView).getTitleTextView().getText().toString();
        } else {
            mToolTipText = null;
        }

        ContextThemeWrapper arrowContextWrapper = new ContextThemeWrapper(mActivity,
                R.style.ArrowTipTaskbarStyle);
        mHoverToolTipView = new ArrowTipView(arrowContextWrapper, /* isPointingUp = */ false,
                R.layout.arrow_toast);
        int verticalPadding = arrowContextWrapper.getResources().getDimensionPixelSize(
                R.dimen.taskbar_tooltip_vertical_padding);
        int horizontalPadding = arrowContextWrapper.getResources().getDimensionPixelSize(
                R.dimen.taskbar_tooltip_horizontal_padding);
        mHoverToolTipView.findViewById(R.id.text).setPadding(horizontalPadding, verticalPadding,
                horizontalPadding, verticalPadding);
        mHoverToolTipView.setAlpha(0);
        mYOffset = arrowContextWrapper.getResources().getDimensionPixelSize(
                R.dimen.taskbar_tooltip_y_offset);

        AnimatorSet hoverOpenAnimator = new AnimatorSet();
        ObjectAnimator alphaOpenAnimator = ObjectAnimator.ofFloat(mHoverToolTipView, ALPHA, 0f, 1f);
        hoverOpenAnimator.play(alphaOpenAnimator);
        hoverOpenAnimator.setDuration(HOVER_TOOL_TIP_REVEAL_DURATION);
        mHoverToolTipView.setCustomOpenAnimation(hoverOpenAnimator);

        mHoverToolTipView.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    mHoverToolTipView.setPivotY(bottom);
                    mHoverToolTipView.setY(mTaskbarView.getTop() - mYOffset - (bottom - top));
                });
    }

    @Override
    public boolean onHover(View v, MotionEvent event) {
        // If hover leaves a taskbar icon animate the tooltip closed.
        if (event.getAction() == ACTION_HOVER_EXIT) {
            mHoverToolTipView.close(/* animate= */ false);
            mActivity.setAutohideSuspendFlag(FLAG_AUTOHIDE_SUSPEND_HOVERING_ICONS, false);
        } else if (event.getAction() == ACTION_HOVER_ENTER) {
            maybeRevealHoverToolTip();
            mActivity.setAutohideSuspendFlag(FLAG_AUTOHIDE_SUSPEND_HOVERING_ICONS, true);
        }
        return false;
    }

    private void maybeRevealHoverToolTip() {
        if (mHoverView == null || mToolTipText == null) {
            return;
        }
        // Do not show tooltip if taskbar icons are transitioning to hotseat.
        if (mActivity.isIconAlignedWithHotseat()) {
            return;
        }
        if (mHoverView instanceof FolderIcon && !((FolderIcon) mHoverView).getIconVisible()) {
            return;
        }
        // Do not reveal if floating views such as folders or app pop-ups are open,
        // as these views will overlap and not look great.
        if (AbstractFloatingView.hasOpenView(mActivity, TYPE_FOLDER | TYPE_ACTION_POPUP)) {
            return;
        }

        Rect iconViewBounds = Utilities.getViewBounds(mHoverView);
        mHoverToolTipView.showAtLocation(mToolTipText, iconViewBounds.centerX(),
                mTaskbarView.getTop() - mYOffset, /* shouldAutoClose= */ false);
    }
}

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

package com.android.quickstep.views;

import static com.android.quickstep.views.TaskThumbnailView.DIM_ALPHA;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.TaskSystemShortcut;
import com.android.quickstep.TaskUtils;

/**
 * Contains options for a recent task when long-pressing its icon.
 */
public class TaskMenuView extends AbstractFloatingView {

    private static final Rect sTempRect = new Rect();

    /** Note that these will be shown in order from top to bottom, if available for the task. */
    public static final TaskSystemShortcut[] MENU_OPTIONS = new TaskSystemShortcut[] {
            new TaskSystemShortcut.AppInfo(),
            new TaskSystemShortcut.SplitScreen(),
            new TaskSystemShortcut.Pin(),
            new TaskSystemShortcut.Install(),
    };

    private static final int REVEAL_OPEN_DURATION = 150;
    private static final int REVEAL_CLOSE_DURATION = 100;

    private BaseDraggingActivity mActivity;
    private TextView mTaskIconAndName;
    private AnimatorSet mOpenCloseAnimator;
    private TaskView mTaskView;
    private LinearLayout mOptionLayout;

    public TaskMenuView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskMenuView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mActivity = BaseDraggingActivity.fromContext(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTaskIconAndName = findViewById(R.id.task_icon_and_name);
        mOptionLayout = findViewById(R.id.menu_option_layout);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            BaseDragLayer dl = mActivity.getDragLayer();
            if (!dl.isEventOverView(this, ev)) {
                // TODO: log this once we have a new container type for it?
                close(true);
                return true;
            }
        }
        return false;
    }

    @Override
    protected void handleClose(boolean animate) {
        if (animate) {
            animateClose();
        } else {
            closeComplete();
        }
    }

    @Override
    public void logActionCommand(int command) {
        // TODO
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_TASK_MENU) != 0;
    }

    public static boolean showForTask(TaskView taskView) {
        BaseDraggingActivity activity = BaseDraggingActivity.fromContext(taskView.getContext());
        final TaskMenuView taskMenuView = (TaskMenuView) activity.getLayoutInflater().inflate(
                        R.layout.task_menu, activity.getDragLayer(), false);
        return taskMenuView.populateAndShowForTask(taskView);
    }

    private boolean populateAndShowForTask(TaskView taskView) {
        if (isAttachedToWindow()) {
            return false;
        }
        mActivity.getDragLayer().addView(this);
        mTaskView = taskView;
        addMenuOptions(mTaskView);
        orientAroundTaskView(mTaskView);
        post(this::animateOpen);
        return true;
    }

    private void addMenuOptions(TaskView taskView) {
        Drawable icon = taskView.getTask().icon.getConstantState().newDrawable();
        int iconSize = getResources().getDimensionPixelSize(R.dimen.task_thumbnail_icon_size);
        icon.setBounds(0, 0, iconSize, iconSize);
        mTaskIconAndName.setCompoundDrawables(null, icon, null, null);
        mTaskIconAndName.setText(TaskUtils.getTitle(getContext(), taskView.getTask()));
        mTaskIconAndName.setOnClickListener(v -> close(true));

        // Move the icon and text up half an icon size to lay over the TaskView
        LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams) mTaskIconAndName.getLayoutParams();
        params.topMargin = (int) -getResources().getDimension(R.dimen.task_thumbnail_top_margin);
        mTaskIconAndName.setLayoutParams(params);

        for (TaskSystemShortcut menuOption : MENU_OPTIONS) {
            OnClickListener onClickListener = menuOption.getOnClickListener(mActivity, taskView);
            if (onClickListener != null) {
                addMenuOption(menuOption, onClickListener);
            }
        }
    }

    private void addMenuOption(TaskSystemShortcut menuOption, OnClickListener onClickListener) {
        ViewGroup menuOptionView = (ViewGroup) mActivity.getLayoutInflater().inflate(
                R.layout.task_view_menu_option, this, false);
        menuOptionView.findViewById(R.id.icon).setBackgroundResource(menuOption.iconResId);
        ((TextView) menuOptionView.findViewById(R.id.text)).setText(menuOption.labelResId);
        menuOptionView.setOnClickListener(onClickListener);
        mOptionLayout.addView(menuOptionView);
    }

    private void orientAroundTaskView(TaskView taskView) {
        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        mActivity.getDragLayer().getDescendantRectRelativeToSelf(taskView, sTempRect);
        Rect insets = mActivity.getDragLayer().getInsets();
        BaseDragLayer.LayoutParams params = (BaseDragLayer.LayoutParams) getLayoutParams();
        params.width = sTempRect.width();
        params.gravity = Gravity.LEFT;
        setLayoutParams(params);
        setX(sTempRect.left - insets.left);
        setY(sTempRect.top + getResources().getDimension(R.dimen.task_thumbnail_top_margin)
                - insets.top);
    }

    private void animateOpen() {
        animateOpenOrClosed(false);
        mIsOpen = true;
    }

    private void animateClose() {
        animateOpenOrClosed(true);
    }

    private void animateOpenOrClosed(boolean closing) {
        if (mOpenCloseAnimator != null && mOpenCloseAnimator.isRunning()) {
            return;
        }
        mOpenCloseAnimator = LauncherAnimUtils.createAnimatorSet();

        final Animator revealAnimator = createOpenCloseOutlineProvider()
                .createRevealAnimator(this, closing);
        revealAnimator.setInterpolator(Interpolators.DEACCEL);
        mOpenCloseAnimator.play(revealAnimator);
        mOpenCloseAnimator.play(ObjectAnimator.ofFloat(mTaskView.getThumbnail(), DIM_ALPHA,
                closing ? 0 : TaskView.MAX_PAGE_SCRIM_ALPHA));
        mOpenCloseAnimator.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                setVisibility(VISIBLE);
            }

            @Override
            public void onAnimationSuccess(Animator animator) {
                if (closing) {
                    closeComplete();
                }
            }
        });
        mOpenCloseAnimator.play(ObjectAnimator.ofFloat(this, ALPHA, closing ? 0 : 1));
        mOpenCloseAnimator.setDuration(closing ? REVEAL_CLOSE_DURATION: REVEAL_OPEN_DURATION);
        mOpenCloseAnimator.start();
    }

    private void closeComplete() {
        mIsOpen = false;
        mActivity.getDragLayer().removeView(this);
    }

    private RoundedRectRevealOutlineProvider createOpenCloseOutlineProvider() {
        float radius = getResources().getDimension(R.dimen.task_corner_radius);
        Rect fromRect = new Rect(0, 0, getWidth(), 0);
        Rect toRect = new Rect(0, 0, getWidth(), getHeight());
        return new RoundedRectRevealOutlineProvider(radius, radius, fromRect, toRect);
    }
}

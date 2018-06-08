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

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;
import com.android.launcher3.shortcuts.DeepShortcutView;
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

    private static final long OPEN_CLOSE_DURATION = 220;

    private BaseDraggingActivity mActivity;
    private TextView mTaskIconAndName;
    private AnimatorSet mOpenCloseAnimator;
    private TaskView mTaskView;

    public TaskMenuView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskMenuView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mActivity = BaseDraggingActivity.fromContext(context);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                float r = getResources().getDimensionPixelSize(R.dimen.task_menu_background_radius);
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTaskIconAndName = findViewById(R.id.task_icon_and_name);
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

        for (TaskSystemShortcut menuOption : MENU_OPTIONS) {
            OnClickListener onClickListener = menuOption.getOnClickListener(mActivity, taskView);
            if (onClickListener != null) {
                addMenuOption(menuOption, onClickListener);
            }
        }
    }

    private void addMenuOption(TaskSystemShortcut menuOption, OnClickListener onClickListener) {
        DeepShortcutView menuOptionView = (DeepShortcutView) mActivity.getLayoutInflater().inflate(
                R.layout.system_shortcut, this, false);
        menuOptionView.getIconView().setBackgroundResource(menuOption.iconResId);
        menuOptionView.getBubbleText().setText(menuOption.labelResId);
        menuOptionView.setOnClickListener(onClickListener);
        addView(menuOptionView);
    }

    private void orientAroundTaskView(TaskView taskView) {
        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        mActivity.getDragLayer().getDescendantRectRelativeToSelf(taskView, sTempRect);
        Rect insets = mActivity.getDragLayer().getInsets();
        int x = sTempRect.left + (sTempRect.width() - getMeasuredWidth()) / 2 - insets.left;
        setX(Utilities.isRtl(getResources()) ? -x : x);
        setY(sTempRect.top - mTaskIconAndName.getPaddingTop() - insets.top);
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
        mOpenCloseAnimator.play(createOpenCloseOutlineProvider()
                .createRevealAnimator(this, closing));
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
        mOpenCloseAnimator.setDuration(OPEN_CLOSE_DURATION);
        mOpenCloseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mOpenCloseAnimator.start();
    }

    private void closeComplete() {
        mIsOpen = false;
        mActivity.getDragLayer().removeView(this);
    }

    private RoundedRectRevealOutlineProvider createOpenCloseOutlineProvider() {
        int iconSize = getResources().getDimensionPixelSize(R.dimen.task_thumbnail_icon_size);
        float fromRadius = iconSize / 2;
        float toRadius = getResources().getDimensionPixelSize(
                R.dimen.task_menu_background_radius);
        Point iconCenter = new Point(getWidth() / 2, mTaskIconAndName.getPaddingTop() + iconSize / 2);
        Rect fromRect = new Rect(iconCenter.x, iconCenter.y, iconCenter.x, iconCenter.y);
        Rect toRect = new Rect(0, 0, getWidth(), getHeight());
        return new RoundedRectRevealOutlineProvider(fromRadius, toRadius, fromRect, toRect) {
            @Override
            public boolean shouldRemoveElevationDuringAnimation() {
                return true;
            }
        };
    }
}

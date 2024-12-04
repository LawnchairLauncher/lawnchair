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

package com.android.wm.shell.compatui;

import static android.provider.Settings.Secure.LAUNCHER_TASKBAR_EDUCATION_SHOWING;
import static android.window.TaskConstants.TASK_CHILD_LAYER_COMPAT_UI;

import android.annotation.Nullable;
import android.app.TaskInfo;
import android.content.Context;
import android.graphics.Rect;
import android.provider.Settings;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import com.android.internal.annotations.VisibleForTesting;
import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.transition.Transitions;

import java.util.function.Consumer;

/**
 * Window manager for the Restart Dialog.
 *
 * TODO(b/263484314): Create abstraction of RestartDialogWindowManager and LetterboxEduWindowManager
 */
class RestartDialogWindowManager extends CompatUIWindowManagerAbstract {

    private final DialogAnimationController<RestartDialogLayout> mAnimationController;

    private final Transitions mTransitions;

    // Remember the last reported state in case visibility changes due to keyguard or IME updates.
    private boolean mRequestRestartDialog;

    private final Consumer<Pair<TaskInfo, ShellTaskOrganizer.TaskListener>> mOnDismissCallback;

    private final Consumer<Pair<TaskInfo, ShellTaskOrganizer.TaskListener>> mOnRestartCallback;

    private final CompatUIConfiguration mCompatUIConfiguration;

    /**
     * The vertical margin between the dialog container and the task stable bounds (excluding
     * insets).
     */
    private final int mDialogVerticalMargin;

    @Nullable
    @VisibleForTesting
    RestartDialogLayout mLayout;

    RestartDialogWindowManager(Context context, TaskInfo taskInfo,
            SyncTransactionQueue syncQueue, ShellTaskOrganizer.TaskListener taskListener,
            DisplayLayout displayLayout, Transitions transitions,
            Consumer<Pair<TaskInfo, ShellTaskOrganizer.TaskListener>> onRestartCallback,
            Consumer<Pair<TaskInfo, ShellTaskOrganizer.TaskListener>> onDismissCallback,
            CompatUIConfiguration compatUIConfiguration) {
        this(context, taskInfo, syncQueue, taskListener, displayLayout, transitions,
                onRestartCallback, onDismissCallback,
                new DialogAnimationController<>(context, "RestartDialogWindowManager"),
                compatUIConfiguration);
    }

    @VisibleForTesting
    RestartDialogWindowManager(Context context, TaskInfo taskInfo,
            SyncTransactionQueue syncQueue, ShellTaskOrganizer.TaskListener taskListener,
            DisplayLayout displayLayout, Transitions transitions,
            Consumer<Pair<TaskInfo, ShellTaskOrganizer.TaskListener>> onRestartCallback,
            Consumer<Pair<TaskInfo, ShellTaskOrganizer.TaskListener>> onDismissCallback,
            DialogAnimationController<RestartDialogLayout> animationController,
            CompatUIConfiguration compatUIConfiguration) {
        super(context, taskInfo, syncQueue, taskListener, displayLayout);
        mTransitions = transitions;
        mOnDismissCallback = onDismissCallback;
        mOnRestartCallback = onRestartCallback;
        mAnimationController = animationController;
        mDialogVerticalMargin = (int) mContext.getResources().getDimension(
                R.dimen.letterbox_restart_dialog_margin);
        mCompatUIConfiguration = compatUIConfiguration;
    }

    @Override
    protected int getZOrder() {
        return TASK_CHILD_LAYER_COMPAT_UI + 2;
    }

    @Override
    @Nullable
    protected  View getLayout() {
        return mLayout;
    }

    @Override
    protected void removeLayout() {
        mLayout = null;
    }

    @Override
    protected boolean eligibleToShowLayout() {
        // We don't show this dialog if the user has explicitly selected so clicking on a checkbox.
        return mRequestRestartDialog && !isTaskbarEduShowing() && (mLayout != null
                || mCompatUIConfiguration.shouldShowRestartDialogAgain(getLastTaskInfo()));
    }

    @Override
    protected View createLayout() {
        mLayout = inflateLayout();
        updateDialogMargins();

        // startEnterAnimation will be called immediately if shell-transitions are disabled.
        mTransitions.runOnIdle(this::startEnterAnimation);

        return mLayout;
    }

    void setRequestRestartDialog(boolean enabled) {
        mRequestRestartDialog = enabled;
    }

    private void updateDialogMargins() {
        if (mLayout == null) {
            return;
        }
        final View dialogContainer = mLayout.getDialogContainerView();
        ViewGroup.MarginLayoutParams marginParams =
                (ViewGroup.MarginLayoutParams) dialogContainer.getLayoutParams();

        final Rect taskBounds = getTaskBounds();
        final Rect taskStableBounds = getTaskStableBounds();
        // only update margins based on taskbar insets
        marginParams.topMargin = mDialogVerticalMargin;
        marginParams.bottomMargin = taskBounds.bottom - taskStableBounds.bottom
                + mDialogVerticalMargin;
        dialogContainer.setLayoutParams(marginParams);
    }

    private RestartDialogLayout inflateLayout() {
        return (RestartDialogLayout) LayoutInflater.from(mContext).inflate(
                R.layout.letterbox_restart_dialog_layout, null);
    }

    private void startEnterAnimation() {
        if (mLayout == null) {
            // Dialog has already been released.
            return;
        }
        mAnimationController.startEnterAnimation(mLayout, /* endCallback= */
                this::onDialogEnterAnimationEnded);
    }

    private void onDialogEnterAnimationEnded() {
        if (mLayout == null) {
            // Dialog has already been released.
            return;
        }
        final TaskInfo lastTaskInfo = getLastTaskInfo();
        mLayout.setDismissOnClickListener(this::onDismiss);
        mLayout.setRestartOnClickListener(dontShowAgain -> {
            if (mLayout != null) {
                mLayout.setDismissOnClickListener(null);
                mAnimationController.startExitAnimation(mLayout, () -> {
                    release();
                });
            }
            if (dontShowAgain) {
                mCompatUIConfiguration.setDontShowRestartDialogAgain(lastTaskInfo);
            }
            mOnRestartCallback.accept(Pair.create(lastTaskInfo, getTaskListener()));
        });
        // Focus on the dialog title for accessibility.
        mLayout.getDialogTitle().sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }

    private void onDismiss() {
        if (mLayout == null) {
            return;
        }

        mLayout.setDismissOnClickListener(null);
        mAnimationController.startExitAnimation(mLayout, () -> {
            release();
            mOnDismissCallback.accept(Pair.create(getLastTaskInfo(), getTaskListener()));
        });
    }

    @Override
    public void release() {
        mAnimationController.cancelAnimation();
        super.release();
    }

    @Override
    protected void onParentBoundsChanged() {
        if (mLayout == null) {
            return;
        }
        // Both the layout dimensions and dialog margins depend on the parent bounds.
        WindowManager.LayoutParams windowLayoutParams = getWindowLayoutParams();
        mLayout.setLayoutParams(windowLayoutParams);
        updateDialogMargins();
        relayout(windowLayoutParams);
    }

    @Override
    protected void updateSurfacePosition() {
        // Nothing to do, since the position of the surface is fixed to the top left corner (0,0)
        // of the task (parent surface), which is the default position of a surface.
    }

    @Override
    protected WindowManager.LayoutParams getWindowLayoutParams() {
        final Rect taskBounds = getTaskBounds();
        return getWindowLayoutParams(/* width= */ taskBounds.width(), /* height= */
                taskBounds.height());
    }

    @VisibleForTesting
    boolean isTaskbarEduShowing() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                LAUNCHER_TASKBAR_EDUCATION_SHOWING, /* def= */ 0) == 1;
    }
}

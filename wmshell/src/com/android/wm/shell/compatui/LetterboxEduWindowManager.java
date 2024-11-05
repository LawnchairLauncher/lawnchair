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
import android.view.ViewGroup.MarginLayoutParams;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import com.android.internal.annotations.VisibleForTesting;
import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.DockStateReader;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.transition.Transitions;

import java.util.function.Consumer;

/**
 * Window manager for the Letterbox Education.
 */
class LetterboxEduWindowManager extends CompatUIWindowManagerAbstract {

    private final DialogAnimationController<LetterboxEduDialogLayout> mAnimationController;

    private final Transitions mTransitions;

    /**
     * The id of the current user, to associate with a boolean in {@link
     * #HAS_SEEN_LETTERBOX_EDUCATION_PREF_NAME}, indicating whether that user has already seen the
     * Letterbox Education dialog.
     */
    private final int mUserId;

    private final Consumer<Pair<TaskInfo, ShellTaskOrganizer.TaskListener>> mOnDismissCallback;

    private final CompatUIConfiguration mCompatUIConfiguration;

    // Remember the last reported state in case visibility changes due to keyguard or IME updates.
    private boolean mEligibleForLetterboxEducation;

    @Nullable
    @VisibleForTesting
    LetterboxEduDialogLayout mLayout;

    /**
     * The vertical margin between the dialog container and the task stable bounds (excluding
     * insets).
     */
    private final int mDialogVerticalMargin;

    private final DockStateReader mDockStateReader;

    LetterboxEduWindowManager(Context context, TaskInfo taskInfo,
            SyncTransactionQueue syncQueue, ShellTaskOrganizer.TaskListener taskListener,
            DisplayLayout displayLayout, Transitions transitions,
            Consumer<Pair<TaskInfo, ShellTaskOrganizer.TaskListener>> onDismissCallback,
            DockStateReader dockStateReader, CompatUIConfiguration compatUIConfiguration) {
        this(context, taskInfo, syncQueue, taskListener, displayLayout, transitions,
                onDismissCallback,
                new DialogAnimationController<>(context, /* tag */ "LetterboxEduWindowManager"),
                dockStateReader, compatUIConfiguration);
    }

    @VisibleForTesting
    LetterboxEduWindowManager(Context context, TaskInfo taskInfo,
            SyncTransactionQueue syncQueue, ShellTaskOrganizer.TaskListener taskListener,
            DisplayLayout displayLayout, Transitions transitions,
            Consumer<Pair<TaskInfo, ShellTaskOrganizer.TaskListener>> onDismissCallback,
            DialogAnimationController<LetterboxEduDialogLayout> animationController,
            DockStateReader dockStateReader, CompatUIConfiguration compatUIConfiguration) {
        super(context, taskInfo, syncQueue, taskListener, displayLayout);
        mTransitions = transitions;
        mOnDismissCallback = onDismissCallback;
        mAnimationController = animationController;
        mUserId = taskInfo.userId;
        mDialogVerticalMargin = (int) mContext.getResources().getDimension(
                R.dimen.letterbox_education_dialog_margin);
        mDockStateReader = dockStateReader;
        mCompatUIConfiguration = compatUIConfiguration;
        mEligibleForLetterboxEducation =
                taskInfo.appCompatTaskInfo.topActivityEligibleForLetterboxEducation;
    }

    @Override
    protected int getZOrder() {
        return TASK_CHILD_LAYER_COMPAT_UI + 2;
    }

    @Override
    protected @Nullable View getLayout() {
        return mLayout;
    }

    @Override
    protected void removeLayout() {
        mLayout = null;
    }

    @Override
    protected boolean eligibleToShowLayout() {
        // - The letterbox education should not be visible if the device is docked.
        // - If taskbar education is showing, the letterbox education shouldn't be shown for the
        //   given task until the taskbar education is dismissed and the compat info changes (then
        //   the controller will create a new instance of this class since this one isn't eligible).
        // - If the layout isn't null then it was previously showing, and we shouldn't check if the
        //   user has seen the letterbox education before.
        return mEligibleForLetterboxEducation && !isTaskbarEduShowing() && (mLayout != null
                || !mCompatUIConfiguration.getHasSeenLetterboxEducation(mUserId))
                && !mDockStateReader.isDocked();
    }

    @Override
    protected View createLayout() {
        mLayout = inflateLayout();
        updateDialogMargins();

        // startEnterAnimation will be called immediately if shell-transitions are disabled.
        mTransitions.runOnIdle(this::startEnterAnimation);
        return mLayout;
    }

    private void updateDialogMargins() {
        if (mLayout == null) {
            return;
        }
        final View dialogContainer = mLayout.getDialogContainerView();
        MarginLayoutParams marginParams = (MarginLayoutParams) dialogContainer.getLayoutParams();

        final Rect taskBounds = getTaskBounds();
        final Rect taskStableBounds = getTaskStableBounds();
        marginParams.topMargin = taskStableBounds.top - taskBounds.top + mDialogVerticalMargin;
        marginParams.bottomMargin =
                taskBounds.bottom - taskStableBounds.bottom + mDialogVerticalMargin;
        dialogContainer.setLayoutParams(marginParams);
    }

    private LetterboxEduDialogLayout inflateLayout() {
        return (LetterboxEduDialogLayout) LayoutInflater.from(mContext).inflate(
                R.layout.letterbox_education_dialog_layout, null);
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
        mLayout.setDismissOnClickListener(this::onDismiss);
        // Focus on the dialog title for accessibility.
        mLayout.getDialogTitle().sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
    }

    private void onDismiss() {
        if (mLayout == null) {
            return;
        }
        mCompatUIConfiguration.setSeenLetterboxEducation(mUserId);
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
    public boolean updateCompatInfo(TaskInfo taskInfo, ShellTaskOrganizer.TaskListener taskListener,
            boolean canShow) {
        mEligibleForLetterboxEducation =
                taskInfo.appCompatTaskInfo.topActivityEligibleForLetterboxEducation;

        return super.updateCompatInfo(taskInfo, taskListener, canShow);
    }

    @Override
    boolean needsToBeRecreated(TaskInfo taskInfo, ShellTaskOrganizer.TaskListener taskListener) {
        return super.needsToBeRecreated(taskInfo, taskListener)
                && !mCompatUIConfiguration.getHasSeenLetterboxEducation(mUserId);
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

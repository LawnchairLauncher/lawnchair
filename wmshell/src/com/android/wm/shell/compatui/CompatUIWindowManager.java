/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_CONTROL_DISMISSED;
import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_CONTROL_HIDDEN;
import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_CONTROL_TREATMENT_APPLIED;
import static android.app.CameraCompatTaskInfo.CAMERA_COMPAT_CONTROL_TREATMENT_SUGGESTED;
import static android.view.WindowManager.LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP;
import static android.window.TaskConstants.TASK_CHILD_LAYER_COMPAT_UI;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.CameraCompatTaskInfo.CameraCompatControlState;
import android.app.TaskInfo;
import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;

import com.android.internal.annotations.VisibleForTesting;
import com.android.window.flags.Flags;
import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.compatui.CompatUIController.CompatUICallback;
import com.android.wm.shell.compatui.CompatUIController.CompatUIHintsState;

import java.util.function.Consumer;

/**
 * Window manager for the Size Compat restart button and Camera Compat control.
 */
class CompatUIWindowManager extends CompatUIWindowManagerAbstract {

    private final CompatUICallback mCallback;

    private final CompatUIConfiguration mCompatUIConfiguration;

    private final Consumer<Pair<TaskInfo, ShellTaskOrganizer.TaskListener>> mOnRestartButtonClicked;

    // Remember the last reported states in case visibility changes due to keyguard or IME updates.
    @VisibleForTesting
    boolean mHasSizeCompat;

    @VisibleForTesting
    @CameraCompatControlState
    int mCameraCompatControlState = CAMERA_COMPAT_CONTROL_HIDDEN;

    @VisibleForTesting
    CompatUIHintsState mCompatUIHintsState;

    @Nullable
    @VisibleForTesting
    CompatUILayout mLayout;

    private final float mHideScmTolerance;

    CompatUIWindowManager(Context context, TaskInfo taskInfo,
            SyncTransactionQueue syncQueue, CompatUICallback callback,
            ShellTaskOrganizer.TaskListener taskListener, DisplayLayout displayLayout,
            CompatUIHintsState compatUIHintsState, CompatUIConfiguration compatUIConfiguration,
            Consumer<Pair<TaskInfo, ShellTaskOrganizer.TaskListener>> onRestartButtonClicked) {
        super(context, taskInfo, syncQueue, taskListener, displayLayout);
        mCallback = callback;
        mHasSizeCompat = taskInfo.appCompatTaskInfo.topActivityInSizeCompat;
        if (Flags.enableDesktopWindowingMode() && Flags.enableWindowingDynamicInitialBounds()) {
            // Don't show the SCM button for freeform tasks
            mHasSizeCompat &= !taskInfo.isFreeform();
        }
        mCameraCompatControlState =
                taskInfo.appCompatTaskInfo.cameraCompatTaskInfo.cameraCompatControlState;
        mCompatUIHintsState = compatUIHintsState;
        mCompatUIConfiguration = compatUIConfiguration;
        mOnRestartButtonClicked = onRestartButtonClicked;
        mHideScmTolerance = mCompatUIConfiguration.getHideSizeCompatRestartButtonTolerance();
    }

    @Override
    protected int getZOrder() {
        return TASK_CHILD_LAYER_COMPAT_UI + 1;
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
        return (mHasSizeCompat && shouldShowSizeCompatRestartButton(getLastTaskInfo()))
                || shouldShowCameraControl();
    }

    @Override
    protected View createLayout() {
        mLayout = inflateLayout();
        mLayout.inject(this);

        updateVisibilityOfViews();

        if (mHasSizeCompat) {
            mCallback.onSizeCompatRestartButtonAppeared(mTaskId);
        }

        return mLayout;
    }

    @VisibleForTesting
    CompatUILayout inflateLayout() {
        return (CompatUILayout) LayoutInflater.from(mContext).inflate(R.layout.compat_ui_layout,
                null);
    }

    @Override
    public boolean updateCompatInfo(TaskInfo taskInfo, ShellTaskOrganizer.TaskListener taskListener,
            boolean canShow) {
        final boolean prevHasSizeCompat = mHasSizeCompat;
        final int prevCameraCompatControlState = mCameraCompatControlState;
        mHasSizeCompat = taskInfo.appCompatTaskInfo.topActivityInSizeCompat;
        if (Flags.enableDesktopWindowingMode() && Flags.enableWindowingDynamicInitialBounds()) {
            // Don't show the SCM button for freeform tasks
            mHasSizeCompat &= !taskInfo.isFreeform();
        }
        mCameraCompatControlState =
                taskInfo.appCompatTaskInfo.cameraCompatTaskInfo.cameraCompatControlState;

        if (!super.updateCompatInfo(taskInfo, taskListener, canShow)) {
            return false;
        }

        if (prevHasSizeCompat != mHasSizeCompat
                || prevCameraCompatControlState != mCameraCompatControlState) {
            updateVisibilityOfViews();
        }

        return true;
    }

    /** Called when the restart button is clicked. */
    void onRestartButtonClicked() {
        mOnRestartButtonClicked.accept(Pair.create(getLastTaskInfo(), getTaskListener()));
    }

    /** Called when the camera treatment button is clicked. */
    void onCameraTreatmentButtonClicked() {
        if (!shouldShowCameraControl()) {
            Log.w(getTag(), "Camera compat shouldn't receive clicks in the hidden state.");
            return;
        }
        // When a camera control is shown, only two states are allowed: "treament applied" and
        // "treatment suggested". Clicks on the conrol's treatment button toggle between these
        // two states.
        mCameraCompatControlState =
                mCameraCompatControlState == CAMERA_COMPAT_CONTROL_TREATMENT_SUGGESTED
                        ? CAMERA_COMPAT_CONTROL_TREATMENT_APPLIED
                        : CAMERA_COMPAT_CONTROL_TREATMENT_SUGGESTED;
        mCallback.onCameraControlStateUpdated(mTaskId, mCameraCompatControlState);
        mLayout.updateCameraTreatmentButton(mCameraCompatControlState);
    }

    /** Called when the camera dismiss button is clicked. */
    void onCameraDismissButtonClicked() {
        if (!shouldShowCameraControl()) {
            Log.w(getTag(), "Camera compat shouldn't receive clicks in the hidden state.");
            return;
        }
        mCameraCompatControlState = CAMERA_COMPAT_CONTROL_DISMISSED;
        mCallback.onCameraControlStateUpdated(mTaskId, CAMERA_COMPAT_CONTROL_DISMISSED);
        mLayout.setCameraControlVisibility(/* show= */ false);
    }

    /** Called when the restart button is long clicked. */
    void onRestartButtonLongClicked() {
        if (mLayout == null) {
            return;
        }
        mLayout.setSizeCompatHintVisibility(/* show= */ true);
    }

    /** Called when either dismiss or treatment camera buttons is long clicked. */
    void onCameraButtonLongClicked() {
        if (mLayout == null) {
            return;
        }
        mLayout.setCameraCompatHintVisibility(/* show= */ true);
    }

    @Override
    @VisibleForTesting
    public void updateSurfacePosition() {
        if (mLayout == null) {
            return;
        }
        // Position of the button in the container coordinate.
        final Rect taskBounds = getTaskBounds();
        final Rect taskStableBounds = getTaskStableBounds();
        final int positionX = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                ? taskStableBounds.left - taskBounds.left
                : taskStableBounds.right - taskBounds.left - mLayout.getMeasuredWidth();
        final int positionY = taskStableBounds.bottom - taskBounds.top
                - mLayout.getMeasuredHeight();
        updateSurfacePosition(positionX, positionY);
    }

    @VisibleForTesting
    boolean shouldShowSizeCompatRestartButton(@NonNull TaskInfo taskInfo) {
        // Always show button if display is phone sized.
        if (!Flags.allowHideScmButton() || taskInfo.configuration.smallestScreenWidthDp
                < LARGE_SCREEN_SMALLEST_SCREEN_WIDTH_DP) {
            return true;
        }

        final int letterboxWidth = taskInfo.appCompatTaskInfo.topActivityLetterboxWidth;
        final int letterboxHeight = taskInfo.appCompatTaskInfo.topActivityLetterboxHeight;
        final Rect stableBounds = getTaskStableBounds();
        final int appWidth = stableBounds.width();
        final int appHeight = stableBounds.height();
        // App is floating, should always show restart button.
        if (appWidth > letterboxWidth && appHeight > letterboxHeight) {
            return true;
        }
        // If app fills the width of the display, don't show restart button (for landscape apps)
        // if device has a custom tolerance value.
        if (mHideScmTolerance != mCompatUIConfiguration.getDefaultHideRestartButtonTolerance()
                && appWidth == letterboxWidth)  {
            return false;
        }

        final int letterboxArea = letterboxWidth * letterboxHeight;
        final int taskArea = appWidth * appHeight;
        if (letterboxArea == 0 || taskArea == 0) {
            return false;
        }
        final float percentageAreaOfLetterboxInTask = (float) letterboxArea / taskArea * 100;
        return percentageAreaOfLetterboxInTask < mHideScmTolerance;
    }

    private void updateVisibilityOfViews() {
        if (mLayout == null) {
            return;
        }
        // Size Compat mode restart button.
        mLayout.setRestartButtonVisibility(mHasSizeCompat);
        // Only show by default for the first time.
        if (mHasSizeCompat && !mCompatUIHintsState.mHasShownSizeCompatHint) {
            mLayout.setSizeCompatHintVisibility(/* show= */ true);
            mCompatUIHintsState.mHasShownSizeCompatHint = true;
        }

        // Camera control for stretched issues.
        mLayout.setCameraControlVisibility(shouldShowCameraControl());
        // Only show by default for the first time.
        if (shouldShowCameraControl() && !mCompatUIHintsState.mHasShownCameraCompatHint) {
            mLayout.setCameraCompatHintVisibility(/* show= */ true);
            mCompatUIHintsState.mHasShownCameraCompatHint = true;
        }
        if (shouldShowCameraControl()) {
            mLayout.updateCameraTreatmentButton(mCameraCompatControlState);
        }
    }

    private boolean shouldShowCameraControl() {
        return mCameraCompatControlState != CAMERA_COMPAT_CONTROL_HIDDEN
                && mCameraCompatControlState != CAMERA_COMPAT_CONTROL_DISMISSED;
    }
}

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

import static android.window.TaskConstants.TASK_CHILD_LAYER_COMPAT_UI;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppCompatTaskInfo;
import android.app.TaskInfo;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

import com.android.internal.annotations.VisibleForTesting;
import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.compatui.CompatUIController.CompatUIHintsState;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Window manager for the user aspect ratio settings button which allows users to go to
 * app settings and change apps aspect ratio.
 */
class UserAspectRatioSettingsWindowManager extends CompatUIWindowManagerAbstract {

    private static final long SHOW_USER_ASPECT_RATIO_BUTTON_DELAY_MS = 500L;

    private long mNextButtonHideTimeMs = -1L;

    private final BiConsumer<TaskInfo, ShellTaskOrganizer.TaskListener> mOnButtonClicked;

    private final Function<Integer, Integer> mDisappearTimeSupplier;

    private final ShellExecutor mShellExecutor;

    @NonNull
    private final Supplier<Boolean> mUserAspectRatioButtonShownChecker;

    @NonNull
    private final Consumer<Boolean> mUserAspectRatioButtonStateConsumer;

    @VisibleForTesting
    @NonNull
    final CompatUIHintsState mCompatUIHintsState;

    @Nullable
    private UserAspectRatioSettingsLayout mLayout;

    // Remember the last reported states in case visibility changes due to keyguard or IME updates.
    @VisibleForTesting
    boolean mHasUserAspectRatioSettingsButton;

    UserAspectRatioSettingsWindowManager(@NonNull Context context, @NonNull TaskInfo taskInfo,
            @NonNull SyncTransactionQueue syncQueue,
            @Nullable ShellTaskOrganizer.TaskListener taskListener,
            @NonNull DisplayLayout displayLayout, @NonNull CompatUIHintsState compatUIHintsState,
            @NonNull BiConsumer<TaskInfo, ShellTaskOrganizer.TaskListener> onButtonClicked,
            @NonNull ShellExecutor shellExecutor,
            @NonNull Function<Integer, Integer> disappearTimeSupplier,
            @NonNull Supplier<Boolean> userAspectRatioButtonStateChecker,
            @NonNull Consumer<Boolean> userAspectRatioButtonShownConsumer) {
        super(context, taskInfo, syncQueue, taskListener, displayLayout);
        mShellExecutor = shellExecutor;
        mUserAspectRatioButtonShownChecker = userAspectRatioButtonStateChecker;
        mUserAspectRatioButtonStateConsumer = userAspectRatioButtonShownConsumer;
        mHasUserAspectRatioSettingsButton = shouldShowUserAspectRatioSettingsButton(
                taskInfo.appCompatTaskInfo, taskInfo.baseIntent);
        mCompatUIHintsState = compatUIHintsState;
        mOnButtonClicked = onButtonClicked;
        mDisappearTimeSupplier = disappearTimeSupplier;
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
        return mHasUserAspectRatioSettingsButton;
    }

    @Override
    protected View createLayout() {
        mLayout = inflateLayout();
        mLayout.inject(this);

        updateVisibilityOfViews();

        return mLayout;
    }

    @VisibleForTesting
    UserAspectRatioSettingsLayout inflateLayout() {
        return (UserAspectRatioSettingsLayout) LayoutInflater.from(mContext).inflate(
                R.layout.user_aspect_ratio_settings_layout, null);
    }

    @Override
    public boolean updateCompatInfo(@NonNull TaskInfo taskInfo,
            @NonNull ShellTaskOrganizer.TaskListener taskListener, boolean canShow) {
        final boolean prevHasUserAspectRatioSettingsButton = mHasUserAspectRatioSettingsButton;
        mHasUserAspectRatioSettingsButton = shouldShowUserAspectRatioSettingsButton(
                taskInfo.appCompatTaskInfo, taskInfo.baseIntent);

        if (!super.updateCompatInfo(taskInfo, taskListener, canShow)) {
            return false;
        }

        if (prevHasUserAspectRatioSettingsButton != mHasUserAspectRatioSettingsButton) {
            updateVisibilityOfViews();
        }
        return true;
    }

    /** Called when the user aspect ratio settings button is clicked. */
    void onUserAspectRatioSettingsButtonClicked() {
        mOnButtonClicked.accept(getLastTaskInfo(), getTaskListener());
    }

    /** Called when the user aspect ratio settings button is long clicked. */
    void onUserAspectRatioSettingsButtonLongClicked() {
        if (mLayout == null) {
            return;
        }
        mLayout.setUserAspectRatioSettingsHintVisibility(/* show= */ true);
        final long disappearTimeMs = getDisappearTimeMs();
        mNextButtonHideTimeMs = updateHideTime(disappearTimeMs);
        mShellExecutor.executeDelayed(this::hideUserAspectRatioButton, disappearTimeMs);
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
    void updateVisibilityOfViews() {
        if (mHasUserAspectRatioSettingsButton) {
            mShellExecutor.executeDelayed(this::showUserAspectRatioButton,
                    SHOW_USER_ASPECT_RATIO_BUTTON_DELAY_MS);
            final long disappearTimeMs = getDisappearTimeMs();
            mNextButtonHideTimeMs = updateHideTime(disappearTimeMs);
            mShellExecutor.executeDelayed(this::hideUserAspectRatioButton, disappearTimeMs);
        } else {
            mShellExecutor.removeCallbacks(this::showUserAspectRatioButton);
            mShellExecutor.execute(this::hideUserAspectRatioButton);
        }
    }

    @VisibleForTesting
    boolean isShowingButton() {
        return (mUserAspectRatioButtonShownChecker.get()
                && !isHideDelayReached(mNextButtonHideTimeMs));
    }

    private void showUserAspectRatioButton() {
        if (mLayout == null) {
            return;
        }
        mLayout.setUserAspectRatioButtonVisibility(true);
        mUserAspectRatioButtonStateConsumer.accept(true);
        // Only show by default for the first time.
        if (!mCompatUIHintsState.mHasShownUserAspectRatioSettingsButtonHint) {
            mLayout.setUserAspectRatioSettingsHintVisibility(/* show= */ true);
            mCompatUIHintsState.mHasShownUserAspectRatioSettingsButtonHint = true;
        }
    }

    private void hideUserAspectRatioButton() {
        if (mLayout == null || !isHideDelayReached(mNextButtonHideTimeMs)) {
            return;
        }
        mLayout.setUserAspectRatioButtonVisibility(false);
    }

    private boolean isHideDelayReached(long nextHideTime) {
        return SystemClock.uptimeMillis() >= nextHideTime;
    }

    private long updateHideTime(long hideDelay) {
        return SystemClock.uptimeMillis() + hideDelay;
    }

    private boolean shouldShowUserAspectRatioSettingsButton(@NonNull AppCompatTaskInfo taskInfo,
            @NonNull Intent intent) {
        final Rect stableBounds = getTaskStableBounds();
        final int letterboxHeight = taskInfo.topActivityLetterboxHeight;
        final int letterboxWidth = taskInfo.topActivityLetterboxWidth;
        // App is not visibly letterboxed if it covers status bar/bottom insets or matches the
        // stable bounds, so don't show the button
        if (stableBounds.height() <= letterboxHeight && stableBounds.width() <= letterboxWidth
                && !taskInfo.isUserFullscreenOverrideEnabled) {
            return false;
        }

        return taskInfo.topActivityEligibleForUserAspectRatioButton
                && (taskInfo.topActivityBoundsLetterboxed
                    || taskInfo.isUserFullscreenOverrideEnabled)
                && !taskInfo.isSystemFullscreenOverrideEnabled
                && Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
                && (!mUserAspectRatioButtonShownChecker.get() || isShowingButton());
    }

    private long getDisappearTimeMs() {
        return mDisappearTimeSupplier.apply(AccessibilityManager.FLAG_CONTENT_CONTROLS);
    }
}

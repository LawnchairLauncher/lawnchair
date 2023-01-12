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

package com.android.quickstep.util;

import static com.android.launcher3.config.FeatureFlags.ENABLE_SPLIT_FROM_FULLSCREEN_WITH_KEYBOARD_SHORTCUTS;
import static com.android.launcher3.config.FeatureFlags.ENABLE_SPLIT_FROM_WORKSPACE_TO_WORKSPACE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.quickstep.views.FloatingTaskView;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;

/** Handles when the stage split lands on the home screen. */
public class SplitToWorkspaceController {

    private final Launcher mLauncher;
    private final DeviceProfile mDP;
    private final SplitSelectStateController mController;

    private final int mHalfDividerSize;

    public SplitToWorkspaceController(Launcher launcher, SplitSelectStateController controller) {
        mLauncher = launcher;
        mDP = mLauncher.getDeviceProfile();
        mController = controller;

        mHalfDividerSize = mLauncher.getResources().getDimensionPixelSize(
                R.dimen.multi_window_task_divider_size) / 2;
    }

    /**
     * Handles second app selection from stage split. If the item can't be opened in split or
     * it's not in stage split state, we pass it onto Launcher's default item click handler.
     */
    public boolean handleSecondAppSelectionForSplit(View view) {
        if ((!ENABLE_SPLIT_FROM_FULLSCREEN_WITH_KEYBOARD_SHORTCUTS.get()
                && !ENABLE_SPLIT_FROM_WORKSPACE_TO_WORKSPACE.get())
                || !mController.isSplitSelectActive()) {
            return false;
        }
        Object tag = view.getTag();
        Intent intent;
        BitmapInfo bitmapInfo;
        if (tag instanceof WorkspaceItemInfo) {
            final WorkspaceItemInfo workspaceItemInfo = (WorkspaceItemInfo) tag;
            intent = workspaceItemInfo.intent;
            bitmapInfo = workspaceItemInfo.bitmap;
        } else if (tag instanceof com.android.launcher3.model.data.AppInfo) {
            final com.android.launcher3.model.data.AppInfo appInfo =
                    (com.android.launcher3.model.data.AppInfo) tag;
            intent = appInfo.intent;
            bitmapInfo = appInfo.bitmap;
        } else {
            return false;
        }

        mController.setSecondTask(intent);

        boolean isTablet = mLauncher.getDeviceProfile().isTablet;
        SplitAnimationTimings timings = AnimUtils.getDeviceSplitToConfirmTimings(isTablet);
        PendingAnimation pendingAnimation = new PendingAnimation(timings.getDuration());

        Rect firstTaskStartingBounds = new Rect();
        Rect firstTaskEndingBounds = new Rect();
        RectF secondTaskStartingBounds = new RectF();
        Rect secondTaskEndingBounds = new Rect();

        RecentsView recentsView = mLauncher.getOverviewPanel();
        recentsView.getPagedOrientationHandler().getFinalSplitPlaceholderBounds(mHalfDividerSize,
                mDP, mController.getActiveSplitStagePosition(), firstTaskEndingBounds,
                secondTaskEndingBounds);

        FloatingTaskView firstFloatingTaskView = mController.getFirstFloatingTaskView();
        firstFloatingTaskView.getBoundsOnScreen(firstTaskStartingBounds);
        firstFloatingTaskView.addConfirmAnimation(pendingAnimation,
                new RectF(firstTaskStartingBounds), firstTaskEndingBounds,
                false /* fadeWithThumbnail */, true /* isStagedTask */);

        FloatingTaskView secondFloatingTaskView = FloatingTaskView.getFloatingTaskView(mLauncher,
                view, null /* thumbnail */, bitmapInfo.newIcon(mLauncher),
                secondTaskStartingBounds);
        secondFloatingTaskView.setAlpha(1);
        secondFloatingTaskView.addConfirmAnimation(pendingAnimation, secondTaskStartingBounds,
                secondTaskEndingBounds, true /* fadeWithThumbnail */, false /* isStagedTask */);

        pendingAnimation.addListener(new AnimatorListenerAdapter() {
            private boolean mIsCancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                mIsCancelled = true;
                cleanUp();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!mIsCancelled) {
                    mController.launchSplitTasks(aBoolean -> cleanUp());
                    InteractionJankMonitorWrapper.end(
                            InteractionJankMonitorWrapper.CUJ_SPLIT_SCREEN_ENTER);
                }
            }

            private void cleanUp() {
                mLauncher.getDragLayer().removeView(firstFloatingTaskView);
                mLauncher.getDragLayer().removeView(secondFloatingTaskView);
                mController.resetState();
            }
        });
        pendingAnimation.buildAnim().start();
        return true;
    }
}

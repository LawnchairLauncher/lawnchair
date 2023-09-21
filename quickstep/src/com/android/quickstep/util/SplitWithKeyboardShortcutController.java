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
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_KEYBOARD_SHORTCUT_SPLIT_LEFT_TOP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_KEYBOARD_SHORTCUT_SPLIT_RIGHT_BOTTOM;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ActivityManager;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.os.UserHandle;
import android.view.View;

import androidx.annotation.BinderThread;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.quickstep.OverviewCommandHelper;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.RecentsAnimationCallbacks;
import com.android.quickstep.RecentsAnimationController;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.RecentsAnimationTargets;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.views.FloatingTaskView;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.ActivityManagerWrapper;

/** Transitions app from fullscreen to stage split when triggered from keyboard shortcuts. */
public class SplitWithKeyboardShortcutController {

    private final QuickstepLauncher mLauncher;
    private final SplitSelectStateController mController;
    private final OverviewComponentObserver mOverviewComponentObserver;

    private final int mSplitPlaceholderSize;
    private final int mSplitPlaceholderInset;

    public SplitWithKeyboardShortcutController(QuickstepLauncher launcher,
            SplitSelectStateController controller) {
        mLauncher = launcher;
        mController = controller;
        RecentsAnimationDeviceState deviceState = new RecentsAnimationDeviceState(
                launcher.getApplicationContext());
        mOverviewComponentObserver = new OverviewComponentObserver(launcher.getApplicationContext(),
                deviceState);

        mSplitPlaceholderSize = mLauncher.getResources().getDimensionPixelSize(
                R.dimen.split_placeholder_size);
        mSplitPlaceholderInset = mLauncher.getResources().getDimensionPixelSize(
                R.dimen.split_placeholder_inset);
    }

    @BinderThread
    public void enterStageSplit(boolean leftOrTop) {
        if (!ENABLE_SPLIT_FROM_FULLSCREEN_WITH_KEYBOARD_SHORTCUTS.get()) {
            return;
        }
        RecentsAnimationCallbacks callbacks = new RecentsAnimationCallbacks(
                SystemUiProxy.INSTANCE.get(mLauncher.getApplicationContext()),
                false /* allowMinimizeSplitScreen */);
        SplitWithKeyboardShortcutRecentsAnimationListener listener =
                new SplitWithKeyboardShortcutRecentsAnimationListener(leftOrTop);

        MAIN_EXECUTOR.execute(() -> {
            callbacks.addListener(listener);
            UI_HELPER_EXECUTOR.execute(
                    // Transition from fullscreen app to enter stage split in launcher with
                    // recents animation.
                    () -> ActivityManagerWrapper.getInstance().startRecentsActivity(
                            mOverviewComponentObserver.getOverviewIntent(),
                            SystemClock.uptimeMillis(), callbacks, null, null));
        });
    }

    public void onDestroy() {
        mOverviewComponentObserver.onDestroy();
    }

    private class SplitWithKeyboardShortcutRecentsAnimationListener implements
            RecentsAnimationCallbacks.RecentsAnimationListener {

        private final boolean mLeftOrTop;
        private final Rect mTempRect = new Rect();

        private SplitWithKeyboardShortcutRecentsAnimationListener(boolean leftOrTop) {
            mLeftOrTop = leftOrTop;
        }

        @Override
        public void onRecentsAnimationStart(RecentsAnimationController controller,
                RecentsAnimationTargets targets) {
            ActivityManager.RunningTaskInfo runningTaskInfo =
                    ActivityManagerWrapper.getInstance().getRunningTask();
            mController.setInitialTaskSelect(runningTaskInfo,
                    mLeftOrTop ? STAGE_POSITION_TOP_OR_LEFT : STAGE_POSITION_BOTTOM_OR_RIGHT,
                    null /* itemInfo */,
                    mLeftOrTop ? LAUNCHER_KEYBOARD_SHORTCUT_SPLIT_LEFT_TOP
                            : LAUNCHER_KEYBOARD_SHORTCUT_SPLIT_RIGHT_BOTTOM);

            RecentsView recentsView = mLauncher.getOverviewPanel();
            recentsView.getPagedOrientationHandler().getInitialSplitPlaceholderBounds(
                    mSplitPlaceholderSize, mSplitPlaceholderInset, mLauncher.getDeviceProfile(),
                    mController.getActiveSplitStagePosition(), mTempRect);

            PendingAnimation anim = new PendingAnimation(
                    SplitAnimationTimings.TABLET_HOME_TO_SPLIT.getDuration());
            RectF startingTaskRect = new RectF();
            final FloatingTaskView floatingTaskView = FloatingTaskView.getFloatingTaskView(
                    mLauncher, mLauncher.getDragLayer(),
                    controller.screenshotTask(runningTaskInfo.taskId).thumbnail,
                    null /* icon */, startingTaskRect);
            RecentsModel.INSTANCE.get(mLauncher.getApplicationContext())
                    .getIconCache()
                    .updateIconInBackground(
                            Task.from(new Task.TaskKey(runningTaskInfo), runningTaskInfo,
                                    false /* isLocked */),
                            (task) -> {
                                if (task.thumbnail != null) {
                                    floatingTaskView.setIcon(task.thumbnail.thumbnail);
                                }
                            });
            floatingTaskView.setAlpha(1);
            floatingTaskView.addStagingAnimation(anim, startingTaskRect, mTempRect,
                    false /* fadeWithThumbnail */, true /* isStagedTask */);
            mController.setFirstFloatingTaskView(floatingTaskView);

            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    controller.finish(true /* toRecents */, null /* onFinishComplete */,
                            false /* sendUserLeaveHint */);
                }
            });
            anim.buildAnim().start();
        }
    };
}

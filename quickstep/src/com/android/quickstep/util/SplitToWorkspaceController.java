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

import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.window.flags.Flags.enableDesktopWindowingMode;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.view.View;

import com.android.internal.jank.Cuj;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.quickstep.views.FloatingTaskView;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;

/** Handles when the stage split lands on the home screen. */
public class SplitToWorkspaceController {

    private final Launcher mLauncher;
    private final SplitSelectStateController mController;

    private final int mHalfDividerSize;
    private final IconCache mIconCache;

    public SplitToWorkspaceController(Launcher launcher, SplitSelectStateController controller) {
        mLauncher = launcher;
        mController = controller;
        mIconCache = LauncherAppState.getInstanceNoCreate().getIconCache();
        mHalfDividerSize = mLauncher.getResources().getDimensionPixelSize(
                R.dimen.multi_window_task_divider_size) / 2;
    }

    /**
     * Handles widget selection from staged split.
     * @param view Original widget view
     * @param pendingIntent Provided by widget via InteractionHandler
     * @return {@code true} if we can attempt launch the widget into split, {@code false} otherwise
     *         to allow launcher to handle the click
     */
    public boolean handleSecondWidgetSelectionForSplit(View view, PendingIntent pendingIntent,
            Intent remoteResponseIntent) {
        if (shouldIgnoreSecondSplitLaunch()) {
            return false;
        }

        int width = view.getWidth();
        int height = view.getHeight();
        MODEL_EXECUTOR.execute(() -> {
            PackageItemInfo infoInOut = new PackageItemInfo(pendingIntent.getCreatorPackage(),
                    pendingIntent.getCreatorUserHandle());
            mIconCache.getTitleAndIconForApp(infoInOut, false);
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            view.post(() -> {
                mController.setSecondWidget(pendingIntent, remoteResponseIntent);
                // Convert original widgetView into bitmap to use for animation
                Canvas canvas = new Canvas(bitmap);
                view.draw(canvas);
                startWorkspaceAnimation(view, bitmap,
                        new BitmapDrawable(mLauncher.getResources(), infoInOut.bitmap.icon));
            });
        });

        return true;
    }

    /**
     * Handles second app selection from stage split. If the item can't be opened in split or
     * it's not in stage split state, we pass it onto Launcher's default item click handler.
     */
    public boolean handleSecondAppSelectionForSplit(View view) {
        if (shouldIgnoreSecondSplitLaunch()) {
            return false;
        }
        Object tag = view.getTag();
        Intent intent;
        UserHandle user;
        BitmapInfo bitmapInfo;
        if (tag instanceof WorkspaceItemInfo) {
            final WorkspaceItemInfo workspaceItemInfo = (WorkspaceItemInfo) tag;
            intent = workspaceItemInfo.intent;
            user = workspaceItemInfo.user;
            bitmapInfo = workspaceItemInfo.bitmap;
        } else if (tag instanceof com.android.launcher3.model.data.AppInfo) {
            final com.android.launcher3.model.data.AppInfo appInfo =
                    (com.android.launcher3.model.data.AppInfo) tag;
            intent = appInfo.intent;
            user = appInfo.user;
            bitmapInfo = appInfo.bitmap;
        } else if (tag instanceof AppPairInfo) {
            // Prompt the user to select something else by wiggling the instructions view
            mController.getSplitInstructionsView().goBoing();
            return true;
        } else {
            // Use Launcher's default click handler
            return false;
        }

        mController.setSecondTask(intent, user, (ItemInfo) tag);

        startWorkspaceAnimation(view, null /*bitmap*/, bitmapInfo.newIcon(mLauncher));
        return true;
    }

    private void startWorkspaceAnimation(@NonNull View view, @Nullable Bitmap bitmap,
            @Nullable Drawable icon) {
        DeviceProfile dp = mLauncher.getDeviceProfile();
        boolean isTablet = dp.isTablet;
        SplitAnimationTimings timings = AnimUtils.getDeviceSplitToConfirmTimings(isTablet);
        PendingAnimation pendingAnimation = new PendingAnimation(timings.getDuration());

        Rect firstTaskStartingBounds = new Rect();
        Rect firstTaskEndingBounds = new Rect();
        RectF secondTaskStartingBounds = new RectF();
        Rect secondTaskEndingBounds = new Rect();

        RecentsView recentsView = mLauncher.getOverviewPanel();
        recentsView.getPagedOrientationHandler().getFinalSplitPlaceholderBounds(mHalfDividerSize,
                dp, mController.getActiveSplitStagePosition(), firstTaskEndingBounds,
                secondTaskEndingBounds);

        FloatingTaskView firstFloatingTaskView = mController.getFirstFloatingTaskView();
        firstFloatingTaskView.getBoundsOnScreen(firstTaskStartingBounds);
        firstFloatingTaskView.addConfirmAnimation(pendingAnimation,
                new RectF(firstTaskStartingBounds), firstTaskEndingBounds,
                false /* fadeWithThumbnail */, true /* isStagedTask */);

        FloatingTaskView secondFloatingTaskView = FloatingTaskView.getFloatingTaskView(mLauncher,
                view, bitmap, icon, secondTaskStartingBounds);
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
                    InteractionJankMonitorWrapper.end(Cuj.CUJ_SPLIT_SCREEN_ENTER);
                }
            }

            private void cleanUp() {
                mLauncher.getDragLayer().removeView(firstFloatingTaskView);
                mLauncher.getDragLayer().removeView(secondFloatingTaskView);
                mController.getSplitAnimationController().removeSplitInstructionsView(mLauncher);
                mController.resetState();
            }
        });
        pendingAnimation.buildAnim().start();
    }

    private boolean shouldIgnoreSecondSplitLaunch() {
        return (!FeatureFlags.enableSplitContextually()
                && !enableDesktopWindowingMode())
                || !mController.isSplitSelectActive();
    }
}

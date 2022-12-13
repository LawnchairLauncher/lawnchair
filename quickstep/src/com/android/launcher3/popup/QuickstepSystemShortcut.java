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
package com.android.launcher3.popup;

import static com.android.launcher3.config.FeatureFlags.ENABLE_SPLIT_FROM_WORKSPACE_TO_WORKSPACE;
import static com.android.launcher3.util.SplitConfigurationOptions.getLogEventForPosition;
import static com.android.quickstep.util.SplitAnimationTimings.TABLET_HOME_TO_SPLIT;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.R;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.quickstep.util.SplitSelectStateController;
import com.android.quickstep.views.FloatingTaskView;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.shared.recents.model.Task;

import java.util.function.Consumer;

public interface QuickstepSystemShortcut {

    String TAG = QuickstepSystemShortcut.class.getSimpleName();

    static SystemShortcut.Factory<QuickstepLauncher> getSplitSelectShortcutByPosition(
            SplitPositionOption position) {
        return (activity, itemInfo, originalView) ->
                new QuickstepSystemShortcut.SplitSelectSystemShortcut(activity, itemInfo,
                        originalView, position);
    }

    class SplitSelectSystemShortcut extends SystemShortcut<QuickstepLauncher> {

        private final int mSplitPlaceholderSize;
        private final int mSplitPlaceholderInset;

        private final Rect mTempRect = new Rect();
        private final SplitPositionOption mPosition;

        public SplitSelectSystemShortcut(QuickstepLauncher launcher, ItemInfo itemInfo,
                View originalView, SplitPositionOption position) {
            super(position.iconResId, position.textResId, launcher, itemInfo, originalView);

            mPosition = position;

            mSplitPlaceholderSize = launcher.getResources().getDimensionPixelSize(
                    R.dimen.split_placeholder_size);
            mSplitPlaceholderInset = launcher.getResources().getDimensionPixelSize(
                    R.dimen.split_placeholder_inset);
        }

        @Override
        public void onClick(View view) {
            // Initiate splitscreen from the Home screen or Home All Apps
            Bitmap bitmap;
            Intent intent;
            if (mItemInfo instanceof WorkspaceItemInfo) {
                final WorkspaceItemInfo workspaceItemInfo = (WorkspaceItemInfo) mItemInfo;
                bitmap = workspaceItemInfo.bitmap.icon;
                intent = workspaceItemInfo.intent;
            } else if (mItemInfo instanceof com.android.launcher3.model.data.AppInfo) {
                final com.android.launcher3.model.data.AppInfo appInfo =
                        (com.android.launcher3.model.data.AppInfo) mItemInfo;
                bitmap = appInfo.bitmap.icon;
                intent = appInfo.intent;
            } else {
                Log.e(TAG, "unknown item type");
                return;
            }

            StatsLogManager.EventEnum splitEvent = getLogEventForPosition(mPosition.stagePosition);
            RecentsView recentsView = mTarget.getOverviewPanel();
            // Check if there is already an instance of this app running, if so, initiate the split
            // using that.
            recentsView.findLastActiveTaskAndDoSplitOperation(
                    intent.getComponent(),
                    (Consumer<Task>) foundTask -> {
                        SplitSelectSource source = new SplitSelectSource(mOriginalView,
                                new BitmapDrawable(bitmap), intent, mPosition, mItemInfo,
                                splitEvent, foundTask);
                        if (ENABLE_SPLIT_FROM_WORKSPACE_TO_WORKSPACE.get()) {
                            startSplitToHome(source);
                        } else {
                            recentsView.initiateSplitSelect(source);
                        }
                    }
            );
        }

        private void startSplitToHome(SplitSelectSource source) {
            AbstractFloatingView.closeAllOpenViews(mTarget);

            SplitSelectStateController controller = mTarget.getSplitSelectStateController();
            controller.setInitialTaskSelect(source.intent, source.position.stagePosition,
                    source.itemInfo, source.splitEvent, source.alreadyRunningTask);

            RecentsView recentsView = mTarget.getOverviewPanel();
            recentsView.getPagedOrientationHandler().getInitialSplitPlaceholderBounds(
                    mSplitPlaceholderSize, mSplitPlaceholderInset, mTarget.getDeviceProfile(),
                    controller.getActiveSplitStagePosition(), mTempRect);

            PendingAnimation anim = new PendingAnimation(TABLET_HOME_TO_SPLIT.getDuration());
            RectF startingTaskRect = new RectF();
            final FloatingTaskView floatingTaskView = FloatingTaskView.getFloatingTaskView(mTarget,
                    source.view, null /* thumbnail */, source.drawable, startingTaskRect);
            floatingTaskView.setAlpha(1);
            floatingTaskView.addStagingAnimation(anim, startingTaskRect, mTempRect,
                    false /* fadeWithThumbnail */, true /* isStagedTask */);
            controller.setFirstFloatingTaskView(floatingTaskView);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationCancel(Animator animation) {
                    mTarget.getDragLayer().removeView(floatingTaskView);
                    controller.resetState();
                }
            });
            anim.buildAnim().start();
        }
    }

    class SplitSelectSource {

        public final View view;
        public final Drawable drawable;
        public final Intent intent;
        public final SplitPositionOption position;
        public final ItemInfo itemInfo;
        public final StatsLogManager.EventEnum splitEvent;
        @Nullable
        public final Task alreadyRunningTask;

        public SplitSelectSource(View view, Drawable drawable, Intent intent,
                SplitPositionOption position, ItemInfo itemInfo,
                StatsLogManager.EventEnum splitEvent, @Nullable Task foundTask) {
            this.view = view;
            this.drawable = drawable;
            this.intent = intent;
            this.position = position;
            this.itemInfo = itemInfo;
            this.splitEvent = splitEvent;
            this.alreadyRunningTask = foundTask;
        }
    }
}

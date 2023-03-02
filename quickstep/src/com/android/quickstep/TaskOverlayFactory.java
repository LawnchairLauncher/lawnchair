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

package com.android.quickstep;

import static android.view.Surface.ROTATION_0;

import static com.android.quickstep.views.OverviewActionsView.DISABLED_NO_THUMBNAIL;
import static com.android.quickstep.views.OverviewActionsView.DISABLED_ROTATED;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.util.ResourceBasedOverride;
import com.android.launcher3.views.ActivityContext;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.quickstep.views.OverviewActionsView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskThumbnailView;
import com.android.quickstep.views.TaskView;
import com.android.quickstep.views.TaskView.TaskIdAttributeContainer;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory class to create and add an overlays on the TaskView
 */
public class TaskOverlayFactory implements ResourceBasedOverride {

    public static List<SystemShortcut> getEnabledShortcuts(TaskView taskView,
            TaskIdAttributeContainer taskContainer) {
        final ArrayList<SystemShortcut> shortcuts = new ArrayList<>();
        final BaseDraggingActivity activity = BaseActivity.fromContext(taskView.getContext());
        boolean hasMultipleTasks = taskView.getTaskIds()[1] != -1;
        for (TaskShortcutFactory menuOption : MENU_OPTIONS) {
            if (hasMultipleTasks && !menuOption.showForSplitscreen()) {
                continue;
            }

            List<SystemShortcut> menuShortcuts = menuOption.getShortcuts(activity, taskContainer);
            if (menuShortcuts == null) {
                continue;
            }
            shortcuts.addAll(menuShortcuts);
        }
        RecentsOrientedState orientedState = taskView.getRecentsView().getPagedViewOrientedState();
        boolean canLauncherRotate = orientedState.isRecentsActivityRotationAllowed();
        boolean isInLandscape = orientedState.getTouchRotation() != ROTATION_0;
        boolean isTablet = activity.getDeviceProfile().isTablet;

        boolean isGridOnlyOverview = isTablet && FeatureFlags.ENABLE_GRID_ONLY_OVERVIEW.get();
        // Add overview actions to the menu when in in-place rotate landscape mode, or in
        // grid-only overview.
        if ((!canLauncherRotate && isInLandscape) || isGridOnlyOverview) {
            // Add screenshot action to task menu.
            List<SystemShortcut> screenshotShortcuts = TaskShortcutFactory.SCREENSHOT
                    .getShortcuts(activity, taskContainer);
            if (screenshotShortcuts != null) {
                shortcuts.addAll(screenshotShortcuts);
            }

            // Add modal action only if display orientation is the same as the device orientation,
            // or in grid-only overview.
            if (orientedState.getDisplayRotation() == ROTATION_0 || isGridOnlyOverview) {
                List<SystemShortcut> modalShortcuts = TaskShortcutFactory.MODAL
                        .getShortcuts(activity, taskContainer);
                if (modalShortcuts != null) {
                    shortcuts.addAll(modalShortcuts);
                }
            }
        }
        return shortcuts;
    }

    public TaskOverlay createOverlay(TaskThumbnailView thumbnailView) {
        return new TaskOverlay(thumbnailView);
    }

    /**
     * Subclasses can attach any system listeners in this method, must be paired with
     * {@link #removeListeners()}
     */
    public void initListeners() {
    }

    /**
     * Subclasses should remove any system listeners in this method, must be paired with
     * {@link #initListeners()}
     */
    public void removeListeners() {
    }

    /** Note that these will be shown in order from top to bottom, if available for the task. */
    private static final TaskShortcutFactory[] MENU_OPTIONS = new TaskShortcutFactory[]{
            TaskShortcutFactory.APP_INFO,
            TaskShortcutFactory.SPLIT_SELECT,
            TaskShortcutFactory.PIN,
            TaskShortcutFactory.INSTALL,
            TaskShortcutFactory.FREE_FORM,
            TaskShortcutFactory.WELLBEING
    };

    /**
     * Overlay on each task handling Overview Action Buttons.
     */
    public static class TaskOverlay<T extends OverviewActionsView> {

        protected final Context mApplicationContext;
        protected final TaskThumbnailView mThumbnailView;

        private T mActionsView;
        protected ImageActionsApi mImageApi;

        protected TaskOverlay(TaskThumbnailView taskThumbnailView) {
            mApplicationContext = taskThumbnailView.getContext().getApplicationContext();
            mThumbnailView = taskThumbnailView;
            mImageApi = new ImageActionsApi(
                    mApplicationContext, mThumbnailView::getThumbnail);
        }

        protected T getActionsView() {
            if (mActionsView == null) {
                mActionsView = BaseActivity.fromContext(mThumbnailView.getContext()).findViewById(
                        R.id.overview_actions_view);
            }
            return mActionsView;
        }

        /**
         * Called when the current task is interactive for the user
         */
        public void initOverlay(Task task, ThumbnailData thumbnail, Matrix matrix,
                boolean rotated) {
            getActionsView().updateDisabledFlags(DISABLED_NO_THUMBNAIL, thumbnail == null);

            if (thumbnail != null) {
                getActionsView().updateDisabledFlags(DISABLED_ROTATED, rotated);
                boolean isAllowedByPolicy = mThumbnailView.isRealSnapshot();
                getActionsView().setCallbacks(new OverlayUICallbacksImpl(isAllowedByPolicy, task));
            }
        }

        /**
         * End rendering live tile in Overview.
         *
         * @param callback callback to run, after switching to screenshot
         */
        public void endLiveTileMode(@NonNull Runnable callback) {
            RecentsView recentsView = mThumbnailView.getTaskView().getRecentsView();
            recentsView.switchToScreenshot(
                    () -> recentsView.finishRecentsAnimation(true /* toRecents */,
                            false /* shouldPip */, callback));
        }

        /**
         * Called to save screenshot of the task thumbnail.
         */
        @SuppressLint("NewApi")
        protected void saveScreenshot(Task task) {
            if (mThumbnailView.isRealSnapshot()) {
                mImageApi.saveScreenshot(mThumbnailView.getThumbnail(),
                        getTaskSnapshotBounds(), getTaskSnapshotInsets(), task.key);
            } else {
                showBlockedByPolicyMessage();
            }
        }

        private void enterSplitSelect() {
            RecentsView overviewPanel = mThumbnailView.getTaskView().getRecentsView();
            overviewPanel.initiateSplitSelect(mThumbnailView.getTaskView());
        }

        /**
         * Called when the overlay is no longer used.
         */
        public void reset() {
        }

        /**
         * Called when the system wants to reset the modal visuals.
         */
        public void resetModalVisuals() {
        }

        /**
         * Gets the modal state system shortcut.
         */
        public SystemShortcut getModalStateSystemShortcut(WorkspaceItemInfo itemInfo,
                View original) {
            return null;
        }

        /**
         * Sets full screen progress to the task overlay.
         */
        public void setFullscreenProgress(float progress) {
        }

        /**
         * Gets the system shortcut for the screenshot that will be added to the task menu.
         */
        public SystemShortcut getScreenshotShortcut(BaseDraggingActivity activity,
                ItemInfo iteminfo, View originalView) {
            return new ScreenshotSystemShortcut(activity, iteminfo, originalView);
        }

        /**
         * Gets the task snapshot as it is displayed on the screen.
         *
         * @return the bounds of the snapshot in screen coordinates.
         */
        public Rect getTaskSnapshotBounds() {
            int[] location = new int[2];
            mThumbnailView.getLocationOnScreen(location);

            return new Rect(location[0], location[1], mThumbnailView.getWidth() + location[0],
                    mThumbnailView.getHeight() + location[1]);
        }

        /**
         * Gets the insets that the snapshot is drawn with.
         *
         * @return the insets in screen coordinates.
         */
        @RequiresApi(api = Build.VERSION_CODES.Q)
        public Insets getTaskSnapshotInsets() {
            return mThumbnailView.getScaledInsets();
        }

        /**
         * Called when the device rotated.
         */
        public void updateOrientationState(RecentsOrientedState state) {
        }

        protected void showBlockedByPolicyMessage() {
            ActivityContext activityContext = ActivityContext.lookupContext(
                    mThumbnailView.getContext());
            String message = activityContext.getStringCache() != null
                    ? activityContext.getStringCache().disabledByAdminMessage
                    : mThumbnailView.getContext().getString(R.string.blocked_by_policy);
            Toast.makeText(
                    mThumbnailView.getContext(),
                    message,
                    Toast.LENGTH_LONG).show();
        }

        /** Called when the snapshot has updated its full screen drawing parameters. */
        public void setFullscreenParams(TaskView.FullscreenDrawParams fullscreenParams) {
        }

        private class ScreenshotSystemShortcut extends SystemShortcut {

            private final BaseDraggingActivity mActivity;

            ScreenshotSystemShortcut(BaseDraggingActivity activity, ItemInfo itemInfo,
                    View originalView) {
                super(R.drawable.ic_screenshot, R.string.action_screenshot, activity, itemInfo,
                        originalView);
                mActivity = activity;
            }

            @Override
            public void onClick(View view) {
                saveScreenshot(mThumbnailView.getTaskView().getTask());
                dismissTaskMenuView(mActivity);
            }
        }

        protected class OverlayUICallbacksImpl implements OverlayUICallbacks {
            protected final boolean mIsAllowedByPolicy;
            protected final Task mTask;

            public OverlayUICallbacksImpl(boolean isAllowedByPolicy, Task task) {
                mIsAllowedByPolicy = isAllowedByPolicy;
                mTask = task;
            }

            @SuppressLint("NewApi")
            public void onScreenshot() {
                endLiveTileMode(() -> saveScreenshot(mTask));
            }

            public void onSplit() {
                endLiveTileMode(TaskOverlay.this::enterSplitSelect);
            }
        }
    }

    /**
     * Callbacks the Ui can generate. This is the only way for a Ui to call methods on the
     * controller.
     */
    public interface OverlayUICallbacks {
        /** User has indicated they want to screenshot the current task. */
        void onScreenshot();

        /** User wants to start split screen with current app. */
        void onSplit();
    }
}

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

import static com.android.quickstep.views.OverviewActionsView.DISABLED_NO_THUMBNAIL;
import static com.android.quickstep.views.OverviewActionsView.DISABLED_ROTATED;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.util.ResourceBasedOverride;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.Snackbar;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.quickstep.views.DesktopTaskView;
import com.android.quickstep.views.GroupedTaskView;
import com.android.quickstep.views.OverviewActionsView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.quickstep.views.TaskThumbnailViewDeprecated;
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
        final RecentsViewContainer container =
                RecentsViewContainer.containerFromContext(taskView.getContext());
        for (TaskShortcutFactory menuOption : MENU_OPTIONS) {
            if (taskView instanceof GroupedTaskView && !menuOption.showForGroupedTask()) {
                continue;
            }
            if (taskView instanceof DesktopTaskView && !menuOption.showForDesktopTask()) {
                continue;
            }

            List<SystemShortcut> menuShortcuts = menuOption.getShortcuts(container, taskContainer);
            if (menuShortcuts == null) {
                continue;
            }
            shortcuts.addAll(menuShortcuts);
        }
        return shortcuts;
    }

    public TaskOverlay createOverlay(TaskThumbnailViewDeprecated thumbnailView) {
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

    /**
     * Clears any active state outside of the TaskOverlay lifecycle which might have built
     * up over time
     */
    public void clearAllActiveState() { }

    /** Note that these will be shown in order from top to bottom, if available for the task. */
    private static final TaskShortcutFactory[] MENU_OPTIONS = new TaskShortcutFactory[]{
            TaskShortcutFactory.APP_INFO,
            TaskShortcutFactory.SPLIT_SELECT,
            TaskShortcutFactory.PIN,
            TaskShortcutFactory.INSTALL,
            TaskShortcutFactory.FREE_FORM,
            DesktopSystemShortcut.Companion.createFactory(),
            TaskShortcutFactory.WELLBEING,
            TaskShortcutFactory.SAVE_APP_PAIR,
            TaskShortcutFactory.SCREENSHOT,
            TaskShortcutFactory.MODAL
    };

    /**
     * Overlay on each task handling Overview Action Buttons.
     */
    public static class TaskOverlay<T extends OverviewActionsView> {

        protected final Context mApplicationContext;
        protected final TaskThumbnailViewDeprecated mThumbnailView;

        private T mActionsView;
        protected ImageActionsApi mImageApi;

        protected TaskOverlay(TaskThumbnailViewDeprecated taskThumbnailViewDeprecated) {
            mApplicationContext = taskThumbnailViewDeprecated.getContext().getApplicationContext();
            mThumbnailView = taskThumbnailViewDeprecated;
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

        public TaskThumbnailViewDeprecated getThumbnailView() {
            return mThumbnailView;
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
            // Task has already been dismissed
            if (recentsView == null) return;
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

        protected void enterSplitSelect() {
            RecentsView overviewPanel = mThumbnailView.getTaskView().getRecentsView();
            // Task has already been dismissed
            if (overviewPanel == null) return;
            overviewPanel.initiateSplitSelect(mThumbnailView.getTaskView());
        }

        protected void saveAppPair() {
            GroupedTaskView taskView = (GroupedTaskView) mThumbnailView.getTaskView();
            taskView.getRecentsView().getSplitSelectController().getAppPairsController()
                    .saveAppPair(taskView);
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
        public SystemShortcut getScreenshotShortcut(RecentsViewContainer container,
                ItemInfo iteminfo, View originalView) {
            return new ScreenshotSystemShortcut(container, iteminfo, originalView);
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

            Snackbar.show(BaseActivity.fromContext(mThumbnailView.getContext()), message, null);
        }

        /** Called when the snapshot has updated its full screen drawing parameters. */
        public void setFullscreenParams(TaskView.FullscreenDrawParams fullscreenParams) {
        }

        private class ScreenshotSystemShortcut extends SystemShortcut {

            private final RecentsViewContainer mContainer;

            ScreenshotSystemShortcut(RecentsViewContainer container, ItemInfo itemInfo,
                    View originalView) {
                super(R.drawable.ic_screenshot, R.string.action_screenshot, container, itemInfo,
                        originalView);
                mContainer = container;
            }

            @Override
            public void onClick(View view) {
                saveScreenshot(mThumbnailView.getTaskView().getTask());
                dismissTaskMenuView();
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

            public void onSaveAppPair() {
                endLiveTileMode(TaskOverlay.this::saveAppPair);
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

        /** User wants to save an app pair with current group of apps. */
        void onSaveAppPair();
    }
}

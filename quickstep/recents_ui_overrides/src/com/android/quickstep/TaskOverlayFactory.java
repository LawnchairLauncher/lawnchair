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

import androidx.annotation.RequiresApi;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.R;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.util.ResourceBasedOverride;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.quickstep.views.OverviewActionsView;
import com.android.quickstep.views.TaskThumbnailView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory class to create and add an overlays on the TaskView
 */
public class TaskOverlayFactory implements ResourceBasedOverride {

    public static List<SystemShortcut> getEnabledShortcuts(TaskView taskView) {
        final ArrayList<SystemShortcut> shortcuts = new ArrayList<>();
        final BaseDraggingActivity activity = BaseActivity.fromContext(taskView.getContext());
        for (TaskShortcutFactory menuOption : MENU_OPTIONS) {
            SystemShortcut shortcut = menuOption.getShortcut(activity, taskView);
            if (shortcut != null) {
                shortcuts.add(shortcut);
            }
        }
        RecentsOrientedState orientedState = taskView.getRecentsView().getPagedViewOrientedState();
        boolean canLauncherRotate = orientedState.canRecentsActivityRotate();
        boolean isInLandscape = orientedState.getTouchRotation() != ROTATION_0;

        // Add overview actions to the menu when in in-place rotate landscape mode.
        if (!canLauncherRotate && isInLandscape) {
            // Add screenshot action to task menu.
            SystemShortcut screenshotShortcut = TaskShortcutFactory.SCREENSHOT
                    .getShortcut(activity, taskView);
            if (screenshotShortcut != null) {
                shortcuts.add(screenshotShortcut);
            }

            // Add modal action only if display orientation is the same as the device orientation.
            if (orientedState.getDisplayRotation() == ROTATION_0) {
                SystemShortcut modalShortcut = TaskShortcutFactory.MODAL
                        .getShortcut(activity, taskView);
                if (modalShortcut != null) {
                    shortcuts.add(modalShortcut);
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
    public void initListeners() { }

    /**
     * Subclasses should remove any system listeners in this method, must be paired with
     * {@link #initListeners()}
     */
    public void removeListeners() { }

    /** Note that these will be shown in order from top to bottom, if available for the task. */
    private static final TaskShortcutFactory[] MENU_OPTIONS = new TaskShortcutFactory[]{
            TaskShortcutFactory.APP_INFO,
            TaskShortcutFactory.SPLIT_SCREEN,
            TaskShortcutFactory.PIN,
            TaskShortcutFactory.INSTALL,
            TaskShortcutFactory.FREE_FORM,
            TaskShortcutFactory.WELLBEING
    };

    /**
     * Overlay on each task handling Overview Action Buttons.
     */
    public static class TaskOverlay<T extends OverviewActionsView> {

        private final Context mApplicationContext;
        protected final TaskThumbnailView mThumbnailView;

        private T mActionsView;
        private ImageActionsApi mImageApi;
        private boolean mIsAllowedByPolicy;

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
            final boolean isAllowedByPolicy = mThumbnailView.isRealSnapshot();

            if (thumbnail != null) {
                getActionsView().updateDisabledFlags(DISABLED_ROTATED, rotated);

                getActionsView().setCallbacks(new OverlayUICallbacks() {
                    @Override
                    public void onShare() {
                        if (isAllowedByPolicy) {
                            mImageApi.startShareActivity();
                        } else {
                            showBlockedByPolicyMessage();
                        }
                    }

                    @SuppressLint("NewApi")
                    @Override
                    public void onScreenshot() {
                        saveScreenshot(task);
                    }
                });
            }
        }

        /**
         * Called to save screenshot of the task thumbnail.
         */
        @SuppressLint("NewApi")
        private void saveScreenshot(Task task) {
            if (mThumbnailView.isRealSnapshot()) {
                mImageApi.saveScreenshot(mThumbnailView.getThumbnail(),
                        getTaskSnapshotBounds(), getTaskSnapshotInsets(), task.key);
            } else {
                showBlockedByPolicyMessage();
            }
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
        public SystemShortcut getModalStateSystemShortcut(WorkspaceItemInfo itemInfo) {
            return null;
        }

        /**
         * Gets the system shortcut for the screenshot that will be added to the task menu.
         */
        public SystemShortcut getScreenshotShortcut(BaseDraggingActivity activity,
                ItemInfo iteminfo) {
            return new ScreenshotSystemShortcut(activity, iteminfo);
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

        private void showBlockedByPolicyMessage() {
            Toast.makeText(
                    mThumbnailView.getContext(),
                    R.string.blocked_by_policy,
                    Toast.LENGTH_LONG).show();
        }

        private class ScreenshotSystemShortcut extends SystemShortcut {

            private final BaseDraggingActivity mActivity;

            ScreenshotSystemShortcut(BaseDraggingActivity activity, ItemInfo itemInfo) {
                super(R.drawable.ic_screenshot, R.string.action_screenshot, activity, itemInfo);
                mActivity = activity;
            }

            @Override
            public void onClick(View view) {
                saveScreenshot(mThumbnailView.getTaskView().getTask());
                dismissTaskMenuView(mActivity);
            }
        }
    }

    /**
     * Callbacks the Ui can generate. This is the only way for a Ui to call methods on the
     * controller.
     */
    public interface OverlayUICallbacks {
        /** User has indicated they want to share the current task. */
        void onShare();

        /** User has indicated they want to screenshot the current task. */
        void onScreenshot();
    }
}

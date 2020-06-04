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

import static com.android.launcher3.util.MainThreadInitializedObject.forOverride;

import android.content.Context;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.widget.Toast;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.R;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.ResourceBasedOverride;
import com.android.quickstep.views.OverviewActionsView;
import com.android.quickstep.views.TaskThumbnailView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.plugins.OverscrollPlugin;
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
        return shortcuts;
    }

    public static final MainThreadInitializedObject<TaskOverlayFactory> INSTANCE =
            forOverride(TaskOverlayFactory.class, R.string.task_overlay_factory_class);

    /**
     * @return a launcher-provided OverscrollPlugin if available, otherwise null
     */
    public OverscrollPlugin getLocalOverscrollPlugin() {
        return null;
    }

    public TaskOverlay createOverlay(TaskThumbnailView thumbnailView) {
        return new TaskOverlay(thumbnailView);
    }

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

        protected TaskOverlay(TaskThumbnailView taskThumbnailView) {
            mApplicationContext = taskThumbnailView.getContext().getApplicationContext();
            mThumbnailView = taskThumbnailView;
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
        public void initOverlay(Task task, ThumbnailData thumbnail, Matrix matrix) {
            ImageActionsApi imageApi = new ImageActionsApi(
                    mApplicationContext, mThumbnailView::getThumbnail);
            final boolean isAllowedByPolicy = thumbnail.isRealSnapshot;

            getActionsView().setCallbacks(new OverlayUICallbacks() {
                @Override
                public void onShare() {
                    if (isAllowedByPolicy) {
                        imageApi.startShareActivity();
                    } else {
                        showBlockedByPolicyMessage();
                    }
                }

                @Override
                public void onScreenshot() {
                    if (isAllowedByPolicy) {
                        imageApi.saveScreenshot(mThumbnailView.getThumbnail(),
                                getTaskSnapshotBounds(), getTaskSnapshotInsets(), task.key);
                    } else {
                        showBlockedByPolicyMessage();
                    }
                }
            });
        }


        /**
         * Called when the overlay is no longer used.
         */
        public void reset() {
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
        public Insets getTaskSnapshotInsets() {
            // TODO: return the real insets
            return Insets.of(0, 0, 0, 0);
        }

        private void showBlockedByPolicyMessage() {
            Toast.makeText(
                    mThumbnailView.getContext(),
                    R.string.blocked_by_policy,
                    Toast.LENGTH_LONG).show();
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

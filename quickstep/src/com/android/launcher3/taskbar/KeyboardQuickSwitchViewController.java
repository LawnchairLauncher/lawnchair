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
package com.android.launcher3.taskbar;

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.animation.Animator;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayDragLayer;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.util.GroupTask;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import java.io.PrintWriter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Handles initialization of the {@link KeyboardQuickSwitchView} and supplies it with the list of
 * tasks.
 */
public class KeyboardQuickSwitchViewController {

    @NonNull private final ViewCallbacks mViewCallbacks = new ViewCallbacks();
    @NonNull private final TaskbarControllers mControllers;
    @NonNull private final TaskbarOverlayContext mOverlayContext;
    @NonNull private final KeyboardQuickSwitchView mKeyboardQuickSwitchView;
    @NonNull private final KeyboardQuickSwitchController.ControllerCallbacks mControllerCallbacks;

    @Nullable private Animator mCloseAnimation;

    private int mCurrentFocusIndex = -1;

    private boolean mOnDesktop;

    protected KeyboardQuickSwitchViewController(
            @NonNull TaskbarControllers controllers,
            @NonNull TaskbarOverlayContext overlayContext,
            @NonNull KeyboardQuickSwitchView keyboardQuickSwitchView,
            @NonNull KeyboardQuickSwitchController.ControllerCallbacks controllerCallbacks) {
        mControllers = controllers;
        mOverlayContext = overlayContext;
        mKeyboardQuickSwitchView = keyboardQuickSwitchView;
        mControllerCallbacks = controllerCallbacks;
    }

    protected int getCurrentFocusedIndex() {
        return mCurrentFocusIndex;
    }

    protected void openQuickSwitchView(
            @NonNull List<GroupTask> tasks,
            int numHiddenTasks,
            boolean updateTasks,
            int currentFocusIndexOverride,
            boolean onDesktop) {
        TaskbarOverlayDragLayer dragLayer = mOverlayContext.getDragLayer();
        dragLayer.addView(mKeyboardQuickSwitchView);
        dragLayer.runOnClickOnce(v -> closeQuickSwitchView(true));
        mOnDesktop = onDesktop;

        mKeyboardQuickSwitchView.applyLoadPlan(
                mOverlayContext,
                tasks,
                numHiddenTasks,
                updateTasks,
                currentFocusIndexOverride,
                mViewCallbacks);
    }

    protected void closeQuickSwitchView(boolean animate) {
        if (mCloseAnimation != null) {
            if (animate) {
                // Let currently-running animation finish.
                return;
            } else {
                mCloseAnimation.cancel();
            }
        }
        if (!animate) {
            mCloseAnimation = null;
            onCloseComplete();
            return;
        }
        mCloseAnimation = mKeyboardQuickSwitchView.getCloseAnimation();

        mCloseAnimation.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                mCloseAnimation = null;
                onCloseComplete();
            }
        });
        mCloseAnimation.start();
    }

    /**
     * Launched the currently-focused task.
     *
     * Returns index -1 iff the RecentsView shouldn't be opened.
     *
     * If the index is not -1, then the {@link com.android.quickstep.views.TaskView} at the returned
     * index will be focused.
     */
    protected int launchFocusedTask() {
        // Launch the second-most recent task if the user quick switches too quickly, if possible.
        return launchTaskAt(mCurrentFocusIndex == -1
                ? (mControllerCallbacks.getTaskCount() > 1 ? 1 : 0) : mCurrentFocusIndex);
    }

    private int launchTaskAt(int index) {
        if (mCloseAnimation != null) {
            // Ignore taps on task views and alt key unpresses while the close animation is running.
            return -1;
        }
        // Even with a valid index, this can be null if the user tries to quick switch before the
        // views have been added in the KeyboardQuickSwitchView.
        View taskView = mKeyboardQuickSwitchView.getTaskAt(index);
        GroupTask task = mControllerCallbacks.getTaskAt(index);
        if (task == null) {
            return Math.max(0, index);
        } else if (mOnDesktop) {
            UI_HELPER_EXECUTOR.execute(() ->
                    SystemUiProxy.INSTANCE.get(mKeyboardQuickSwitchView.getContext())
                            .showDesktopApp(task.task1.key.id));
        } else if (task.task2 == null) {
            UI_HELPER_EXECUTOR.execute(() ->
                    ActivityManagerWrapper.getInstance().startActivityFromRecents(
                            task.task1.key,
                            mControllers.taskbarActivityContext.getActivityLaunchOptions(
                                    taskView == null ? mKeyboardQuickSwitchView : taskView, null)
                                    .options));
        } else {
            mControllers.uiController.launchSplitTasks(
                    taskView == null ? mKeyboardQuickSwitchView : taskView, task);
        }
        return -1;
    }

    private void onCloseComplete() {
        mOverlayContext.getDragLayer().removeView(mKeyboardQuickSwitchView);
        mControllerCallbacks.onCloseComplete();
    }

    protected void onDestroy() {
        closeQuickSwitchView(false);
    }

    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "KeyboardQuickSwitchViewController:");

        pw.println(prefix + "\thasFocus=" + mKeyboardQuickSwitchView.hasFocus());
        pw.println(prefix + "\tcloseAnimationRunning=" + (mCloseAnimation != null));
        pw.println(prefix + "\tmCurrentFocusIndex=" + mCurrentFocusIndex);
    }

    class ViewCallbacks {

        boolean onKeyUp(int keyCode, KeyEvent event, boolean isRTL, boolean allowTraversal) {
            if (keyCode != KeyEvent.KEYCODE_TAB
                    && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT
                    && keyCode != KeyEvent.KEYCODE_DPAD_LEFT
                    && keyCode != KeyEvent.KEYCODE_GRAVE
                    && keyCode != KeyEvent.KEYCODE_ESCAPE) {
                return false;
            }
            if (keyCode == KeyEvent.KEYCODE_GRAVE || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                closeQuickSwitchView(true);
                return true;
            }
            if (!allowTraversal) {
                return false;
            }
            boolean traverseBackwards = (keyCode == KeyEvent.KEYCODE_TAB && event.isShiftPressed())
                    || (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && isRTL)
                    || (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && !isRTL);
            int taskCount = mControllerCallbacks.getTaskCount();
            int toIndex = mCurrentFocusIndex == -1
                    // Focus the second-most recent app if possible
                    ? (taskCount > 1 ? 1 : 0)
                    : (traverseBackwards
                            // focus a more recent task or loop back to the opposite end
                            ? Math.max(0, mCurrentFocusIndex == 0
                                    ? taskCount - 1 : mCurrentFocusIndex - 1)
                            // focus a less recent app or loop back to the opposite end
                            : ((mCurrentFocusIndex + 1) % taskCount));

            if (mCurrentFocusIndex == toIndex) {
                return true;
            }
            mKeyboardQuickSwitchView.animateFocusMove(mCurrentFocusIndex, toIndex);

            return true;
        }

        void updateCurrentFocusIndex(int index) {
            mCurrentFocusIndex = index;
        }

        void launchTappedTask(int index) {
            KeyboardQuickSwitchViewController.this.launchTaskAt(index);
            closeQuickSwitchView(true);
        }

        void updateThumbnailInBackground(Task task, Consumer<ThumbnailData> callback) {
            mControllerCallbacks.updateThumbnailInBackground(task, callback);
        }

        void updateIconInBackground(Task task, Consumer<Task> callback) {
            mControllerCallbacks.updateIconInBackground(task, callback);
        }
    }
}

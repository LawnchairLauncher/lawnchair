/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static com.android.systemui.shared.system.WindowManagerWrapper.ITYPE_BOTTOM_TAPPABLE_ELEMENT;
import static com.android.systemui.shared.system.WindowManagerWrapper.ITYPE_EXTRA_NAVIGATION_BAR;

import android.animation.Animator;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.QuickstepAppTransitionManagerImpl;
import com.android.launcher3.R;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.quickstep.AnimatedFloat;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.WindowManagerWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Interfaces with Launcher/WindowManager/SystemUI to determine what to show in TaskbarView.
 */
public class TaskbarController {

    private static final String WINDOW_TITLE = "Taskbar";

    private final TaskbarContainerView mTaskbarContainerView;
    private final TaskbarView mTaskbarView;
    private final BaseQuickstepLauncher mLauncher;
    private final WindowManager mWindowManager;
    // Layout width and height of the Taskbar in the default state.
    private final Point mTaskbarSize;
    private final TaskbarStateHandler mTaskbarStateHandler;
    private final TaskbarVisibilityController mTaskbarVisibilityController;
    private final TaskbarHotseatController mHotseatController;
    private final TaskbarRecentsController mRecentsController;
    private final TaskbarDragController mDragController;

    // Initialized in init().
    private WindowManager.LayoutParams mWindowLayoutParams;

    // Contains all loaded Tasks, not yet deduped from Hotseat items.
    private List<Task> mLatestLoadedRecentTasks;
    // Contains all loaded Hotseat items.
    private ItemInfo[] mLatestLoadedHotseatItems;

    public TaskbarController(BaseQuickstepLauncher launcher,
            TaskbarContainerView taskbarContainerView) {
        mLauncher = launcher;
        mTaskbarContainerView = taskbarContainerView;
        mTaskbarContainerView.construct(createTaskbarContainerViewCallbacks());
        mTaskbarView = mTaskbarContainerView.findViewById(R.id.taskbar_view);
        mTaskbarView.construct(createTaskbarViewCallbacks());
        mWindowManager = mLauncher.getWindowManager();
        mTaskbarSize = new Point(MATCH_PARENT, mLauncher.getDeviceProfile().taskbarSize);
        mTaskbarStateHandler = mLauncher.getTaskbarStateHandler();
        mTaskbarVisibilityController = new TaskbarVisibilityController(mLauncher,
                createTaskbarVisibilityControllerCallbacks());
        mHotseatController = new TaskbarHotseatController(mLauncher,
                createTaskbarHotseatControllerCallbacks());
        mRecentsController = new TaskbarRecentsController(mLauncher,
                createTaskbarRecentsControllerCallbacks());
        mDragController = new TaskbarDragController(mLauncher);
    }

    private TaskbarVisibilityControllerCallbacks createTaskbarVisibilityControllerCallbacks() {
        return new TaskbarVisibilityControllerCallbacks() {
            @Override
            public void updateTaskbarBackgroundAlpha(float alpha) {
                mTaskbarView.setBackgroundAlpha(alpha);
            }

            @Override
            public void updateTaskbarVisibilityAlpha(float alpha) {
                mTaskbarContainerView.setAlpha(alpha);
            }
        };
    }

    private TaskbarContainerViewCallbacks createTaskbarContainerViewCallbacks() {
        return new TaskbarContainerViewCallbacks() {
            @Override
            public void onViewRemoved() {
                if (mTaskbarContainerView.getChildCount() == 1) {
                    // Only TaskbarView remains.
                    setTaskbarWindowFullscreen(false);
                }
            }
        };
    }

    private TaskbarViewCallbacks createTaskbarViewCallbacks() {
        return new TaskbarViewCallbacks() {
            @Override
            public View.OnClickListener getItemOnClickListener() {
                return view -> {
                    Object tag = view.getTag();
                    if (tag instanceof Task) {
                        Task task = (Task) tag;
                        ActivityManagerWrapper.getInstance().startActivityFromRecents(task.key,
                                ActivityOptions.makeBasic());
                    } else if (tag instanceof FolderInfo) {
                        FolderIcon folderIcon = (FolderIcon) view;
                        Folder folder = folderIcon.getFolder();

                        setTaskbarWindowFullscreen(true);

                        mTaskbarContainerView.post(() -> {
                            folder.animateOpen();

                            folder.iterateOverItems((itemInfo, itemView) -> {
                                itemView.setOnClickListener(getItemOnClickListener());
                                itemView.setOnLongClickListener(getItemOnLongClickListener());
                                // To play haptic when dragging, like other Taskbar items do.
                                itemView.setHapticFeedbackEnabled(true);
                                return false;
                            });
                        });
                    } else {
                        ItemClickHandler.INSTANCE.onClick(view);
                    }

                    AbstractFloatingView.closeAllOpenViews(
                            mTaskbarContainerView.getTaskbarActivityContext());
                };
            }

            @Override
            public View.OnLongClickListener getItemOnLongClickListener() {
                return mDragController::startDragOnLongClick;
            }
        };
    }

    private TaskbarHotseatControllerCallbacks createTaskbarHotseatControllerCallbacks() {
        return new TaskbarHotseatControllerCallbacks() {
            @Override
            public void updateHotseatItems(ItemInfo[] hotseatItemInfos) {
                mTaskbarView.updateHotseatItems(hotseatItemInfos);
                mLatestLoadedHotseatItems = hotseatItemInfos;
                dedupeAndUpdateRecentItems();
            }
        };
    }

    private TaskbarRecentsControllerCallbacks createTaskbarRecentsControllerCallbacks() {
        return new TaskbarRecentsControllerCallbacks() {
            @Override
            public void updateRecentItems(ArrayList<Task> recentTasks) {
                mLatestLoadedRecentTasks = recentTasks;
                dedupeAndUpdateRecentItems();
            }

            @Override
            public void updateRecentTaskAtIndex(int taskIndex, Task task) {
                mTaskbarView.updateRecentTaskAtIndex(taskIndex, task);
            }
        };
    }

    /**
     * Initializes the Taskbar, including adding it to the screen.
     */
    public void init() {
        mTaskbarView.init(mHotseatController.getNumHotseatIcons(),
                mRecentsController.getNumRecentIcons());
        mTaskbarContainerView.init(mTaskbarView);
        addToWindowManager();
        mTaskbarStateHandler.setTaskbarCallbacks(createTaskbarStateHandlerCallbacks());
        mTaskbarVisibilityController.init();
        mHotseatController.init();
        mRecentsController.init();
    }

    private TaskbarStateHandlerCallbacks createTaskbarStateHandlerCallbacks() {
        return new TaskbarStateHandlerCallbacks() {
            @Override
            public AnimatedFloat getAlphaTarget() {
                return mTaskbarVisibilityController.getTaskbarVisibilityForLauncherState();
            }
        };
    }

    /**
     * Removes the Taskbar from the screen, and removes any obsolete listeners etc.
     */
    public void cleanup() {
        mTaskbarView.cleanup();
        mTaskbarContainerView.cleanup();
        removeFromWindowManager();
        mTaskbarStateHandler.setTaskbarCallbacks(null);
        mTaskbarVisibilityController.cleanup();
        mHotseatController.cleanup();
        mRecentsController.cleanup();
    }

    private void removeFromWindowManager() {
        mWindowManager.removeViewImmediate(mTaskbarContainerView);
    }

    private void addToWindowManager() {
        final int gravity = Gravity.BOTTOM;

        mWindowLayoutParams = new WindowManager.LayoutParams(
                mTaskbarSize.x,
                mTaskbarSize.y,
                TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        mWindowLayoutParams.setTitle(WINDOW_TITLE);
        mWindowLayoutParams.packageName = mLauncher.getPackageName();
        mWindowLayoutParams.gravity = gravity;
        mWindowLayoutParams.setFitInsetsTypes(0);
        mWindowLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        mWindowLayoutParams.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        mWindowLayoutParams.setSystemApplicationOverlay(true);

        WindowManagerWrapper wmWrapper = WindowManagerWrapper.getInstance();
        wmWrapper.setProvidesInsetsTypes(
                mWindowLayoutParams,
                new int[] { ITYPE_EXTRA_NAVIGATION_BAR, ITYPE_BOTTOM_TAPPABLE_ELEMENT }
        );

        TaskbarContainerView.LayoutParams taskbarLayoutParams =
                new TaskbarContainerView.LayoutParams(mTaskbarSize.x, mTaskbarSize.y);
        taskbarLayoutParams.gravity = gravity;
        mTaskbarView.setLayoutParams(taskbarLayoutParams);

        mWindowManager.addView(mTaskbarContainerView, mWindowLayoutParams);
    }

    /**
     * Should be called from onResume() and onPause(), and animates the Taskbar accordingly.
     */
    public void onLauncherResumedOrPaused(boolean isResumed) {
        long duration = QuickstepAppTransitionManagerImpl.CONTENT_ALPHA_DURATION;
        final Animator anim;
        if (isResumed) {
            anim = createAnimToLauncher(null, duration);
        } else {
            anim = createAnimToApp(duration);
        }
        anim.start();
    }

    /**
     * Create Taskbar animation when going from an app to Launcher.
     * @param toState If known, the state we will end up in when reaching Launcher.
     */
    public Animator createAnimToLauncher(@Nullable LauncherState toState, long duration) {
        PendingAnimation anim = new PendingAnimation(duration);
        anim.add(mTaskbarVisibilityController.createAnimToBackgroundAlpha(0, duration));
        if (toState != null) {
            mTaskbarStateHandler.setStateWithAnimation(toState, new StateAnimationConfig(), anim);
        }
        return anim.buildAnim();
    }

    private Animator createAnimToApp(long duration) {
        return mTaskbarVisibilityController.createAnimToBackgroundAlpha(1, duration);
    }

    /**
     * Should be called when the IME visibility changes, so we can hide/show Taskbar accordingly.
     */
    public void setIsImeVisible(boolean isImeVisible) {
        mTaskbarVisibilityController.animateToVisibilityForIme(isImeVisible ? 0 : 1);
    }

    /**
     * Should be called when one or more items in the Hotseat have changed.
     */
    public void onHotseatUpdated() {
        mHotseatController.onHotseatUpdated();
    }

    /**
     * @param ev MotionEvent in screen coordinates.
     * @return Whether any Taskbar item could handle the given MotionEvent if given the chance.
     */
    public boolean isEventOverAnyTaskbarItem(MotionEvent ev) {
        return mTaskbarView.isEventOverAnyItem(ev);
    }

    public boolean isDraggingItem() {
        return mTaskbarView.isDraggingItem();
    }

    private void dedupeAndUpdateRecentItems() {
        if (mLatestLoadedRecentTasks == null || mLatestLoadedHotseatItems == null) {
            return;
        }

        final int numRecentIcons = mRecentsController.getNumRecentIcons();

        // From most recent to least recently opened.
        List<Task> dedupedTasksInDescendingOrder = new ArrayList<>();
        for (int i = mLatestLoadedRecentTasks.size() - 1; i >= 0; i--) {
            Task task = mLatestLoadedRecentTasks.get(i);
            boolean isTaskInHotseat = false;
            for (ItemInfo hotseatItem : mLatestLoadedHotseatItems) {
                if (hotseatItem == null) {
                    continue;
                }
                ComponentName hotseatActivity = hotseatItem.getTargetComponent();
                if (hotseatActivity != null && task.key.sourceComponent.getPackageName()
                        .equals(hotseatActivity.getPackageName())) {
                    isTaskInHotseat = true;
                    break;
                }
            }
            if (!isTaskInHotseat) {
                dedupedTasksInDescendingOrder.add(task);
                if (dedupedTasksInDescendingOrder.size() == numRecentIcons) {
                    break;
                }
            }
        }

        // TaskbarView expects an array of all the recent tasks to show, in the order to show them.
        // So we create an array of the proper size, then fill it in such that the most recent items
        // are at the end. If there aren't enough elements to fill the array, leave them null.
        Task[] tasksArray = new Task[numRecentIcons];
        for (int i = 0; i < tasksArray.length; i++) {
            Task task = i >= dedupedTasksInDescendingOrder.size()
                    ? null
                    : dedupedTasksInDescendingOrder.get(i);
            tasksArray[tasksArray.length - 1 - i] = task;
        }

        mTaskbarView.updateRecentTasks(tasksArray);
        mRecentsController.loadIconsForTasks(tasksArray);
    }

    /**
     * @return Whether the given View is in the same window as Taskbar.
     */
    public boolean isViewInTaskbar(View v) {
        return mTaskbarContainerView.isAttachedToWindow()
                && mTaskbarContainerView.getWindowId().equals(v.getWindowId());
    }

    /**
     * Updates the TaskbarContainer to MATCH_PARENT vs original Taskbar size.
     */
    private void setTaskbarWindowFullscreen(boolean fullscreen) {
        if (fullscreen) {
            mWindowLayoutParams.width = MATCH_PARENT;
            mWindowLayoutParams.height = MATCH_PARENT;
        } else {
            mWindowLayoutParams.width = mTaskbarSize.x;
            mWindowLayoutParams.height = mTaskbarSize.y;
        }
        mWindowManager.updateViewLayout(mTaskbarContainerView, mWindowLayoutParams);
    }

    /**
     * Contains methods that TaskbarStateHandler can call to interface with TaskbarController.
     */
    protected interface TaskbarStateHandlerCallbacks {
        AnimatedFloat getAlphaTarget();
    }

    /**
     * Contains methods that TaskbarVisibilityController can call to interface with
     * TaskbarController.
     */
    protected interface TaskbarVisibilityControllerCallbacks {
        void updateTaskbarBackgroundAlpha(float alpha);
        void updateTaskbarVisibilityAlpha(float alpha);
    }

    /**
     * Contains methods that TaskbarContainerView can call to interface with TaskbarController.
     */
    protected interface TaskbarContainerViewCallbacks {
        void onViewRemoved();
    }

    /**
     * Contains methods that TaskbarView can call to interface with TaskbarController.
     */
    protected interface TaskbarViewCallbacks {
        View.OnClickListener getItemOnClickListener();
        View.OnLongClickListener getItemOnLongClickListener();
    }

    /**
     * Contains methods that TaskbarHotseatController can call to interface with TaskbarController.
     */
    protected interface TaskbarHotseatControllerCallbacks {
        void updateHotseatItems(ItemInfo[] hotseatItemInfos);
    }

    /**
     * Contains methods that TaskbarRecentsController can call to interface with TaskbarController.
     */
    protected interface TaskbarRecentsControllerCallbacks {
        void updateRecentItems(ArrayList<Task> recentTasks);
        void updateRecentTaskAtIndex(int taskIndex, Task task);
    }
}

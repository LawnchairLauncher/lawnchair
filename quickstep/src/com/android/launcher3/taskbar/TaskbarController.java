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
import android.animation.AnimatorListenerAdapter;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherState;
import com.android.launcher3.QuickstepTransitionManager;
import com.android.launcher3.R;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.views.ActivityContext;
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
    private final TaskbarView mTaskbarViewInApp;
    private final TaskbarView mTaskbarViewOnHome;
    private final BaseQuickstepLauncher mLauncher;
    private final WindowManager mWindowManager;
    // Layout width and height of the Taskbar in the default state.
    private final Point mTaskbarSize;
    private final TaskbarStateHandler mTaskbarStateHandler;
    private final TaskbarAnimationController mTaskbarAnimationController;
    private final TaskbarHotseatController mHotseatController;
    private final TaskbarRecentsController mRecentsController;
    private final TaskbarDragController mDragController;

    // Initialized in init().
    private WindowManager.LayoutParams mWindowLayoutParams;

    // Contains all loaded Tasks, not yet deduped from Hotseat items.
    private List<Task> mLatestLoadedRecentTasks;
    // Contains all loaded Hotseat items.
    private ItemInfo[] mLatestLoadedHotseatItems;

    private @Nullable Animator mAnimator;
    private boolean mIsAnimatingToLauncher;
    private boolean mIsAnimatingToApp;

    public TaskbarController(BaseQuickstepLauncher launcher,
            TaskbarContainerView taskbarContainerView, TaskbarView taskbarViewOnHome) {
        mLauncher = launcher;
        mTaskbarContainerView = taskbarContainerView;
        mTaskbarContainerView.construct(createTaskbarContainerViewCallbacks());
        mTaskbarViewInApp = mTaskbarContainerView.findViewById(R.id.taskbar_view);
        mTaskbarViewInApp.construct(createTaskbarViewCallbacks());
        mTaskbarViewOnHome = taskbarViewOnHome;
        mTaskbarViewOnHome.construct(createTaskbarViewCallbacks());
        mWindowManager = mLauncher.getWindowManager();
        mTaskbarSize = new Point(MATCH_PARENT, mLauncher.getDeviceProfile().taskbarSize);
        mTaskbarStateHandler = mLauncher.getTaskbarStateHandler();
        mTaskbarAnimationController = new TaskbarAnimationController(mLauncher,
                createTaskbarAnimationControllerCallbacks());
        mHotseatController = new TaskbarHotseatController(mLauncher,
                createTaskbarHotseatControllerCallbacks());
        mRecentsController = new TaskbarRecentsController(mLauncher,
                createTaskbarRecentsControllerCallbacks());
        mDragController = new TaskbarDragController(mLauncher);
    }

    private TaskbarAnimationControllerCallbacks createTaskbarAnimationControllerCallbacks() {
        return new TaskbarAnimationControllerCallbacks() {
            @Override
            public void updateTaskbarBackgroundAlpha(float alpha) {
                mTaskbarViewInApp.setBackgroundAlpha(alpha);
            }

            @Override
            public void updateTaskbarVisibilityAlpha(float alpha) {
                mTaskbarContainerView.setAlpha(alpha);
                mTaskbarViewOnHome.setAlpha(alpha);
            }

            @Override
            public void updateTaskbarScale(float scale) {
                mTaskbarViewInApp.setScaleX(scale);
                mTaskbarViewInApp.setScaleY(scale);
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
                return mDragController::startSystemDragOnLongClick;
            }

            @Override
            public int getEmptyHotseatViewVisibility(TaskbarView taskbarView) {
                // When on the home screen, we want the empty hotseat views to take up their full
                // space so that the others line up with the home screen hotseat.
                boolean isOnHomeScreen = taskbarView == mTaskbarViewOnHome
                        || mLauncher.hasBeenResumed() || mIsAnimatingToLauncher;
                return isOnHomeScreen ? View.INVISIBLE : View.GONE;
            }

            @Override
            public float getNonIconScale(TaskbarView taskbarView) {
                return taskbarView == mTaskbarViewOnHome ? getTaskbarScaleOnHome() : 1f;
            }

            @Override
            public void onItemPositionsChanged(TaskbarView taskbarView) {
                if (taskbarView == mTaskbarViewOnHome) {
                    alignRealHotseatWithTaskbar();
                }
            }
        };
    }

    private TaskbarHotseatControllerCallbacks createTaskbarHotseatControllerCallbacks() {
        return new TaskbarHotseatControllerCallbacks() {
            @Override
            public void updateHotseatItems(ItemInfo[] hotseatItemInfos) {
                mTaskbarViewInApp.updateHotseatItems(hotseatItemInfos);
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
                mTaskbarViewInApp.updateRecentTaskAtIndex(taskIndex, task);
                mTaskbarViewOnHome.updateRecentTaskAtIndex(taskIndex, task);
            }
        };
    }

    /**
     * Initializes the Taskbar, including adding it to the screen.
     */
    public void init() {
        mTaskbarViewInApp.init(mHotseatController.getNumHotseatIcons(),
                mRecentsController.getNumRecentIcons());
        mTaskbarViewOnHome.init(mHotseatController.getNumHotseatIcons(),
                mRecentsController.getNumRecentIcons());
        mTaskbarContainerView.init(mTaskbarViewInApp);
        addToWindowManager();
        mTaskbarStateHandler.setTaskbarCallbacks(createTaskbarStateHandlerCallbacks());
        mTaskbarAnimationController.init();
        mHotseatController.init();
        mRecentsController.init();

        updateWhichTaskbarViewIsVisible();
    }

    private TaskbarStateHandlerCallbacks createTaskbarStateHandlerCallbacks() {
        return new TaskbarStateHandlerCallbacks() {
            @Override
            public AnimatedFloat getAlphaTarget() {
                return mTaskbarAnimationController.getTaskbarVisibilityForLauncherState();
            }

            @Override
            public AnimatedFloat getScaleTarget() {
                return mTaskbarAnimationController.getTaskbarScaleForLauncherState();
            }
        };
    }

    /**
     * Removes the Taskbar from the screen, and removes any obsolete listeners etc.
     */
    public void cleanup() {
        if (mAnimator != null) {
            // End this first, in case it relies on properties that are about to be cleaned up.
            mAnimator.end();
        }

        mTaskbarViewInApp.cleanup();
        mTaskbarViewOnHome.cleanup();
        mTaskbarContainerView.cleanup();
        removeFromWindowManager();
        mTaskbarStateHandler.setTaskbarCallbacks(null);
        mTaskbarAnimationController.cleanup();
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
        mTaskbarViewInApp.setLayoutParams(taskbarLayoutParams);

        mWindowManager.addView(mTaskbarContainerView, mWindowLayoutParams);
    }

    /**
     * Should be called from onResume() and onPause(), and animates the Taskbar accordingly.
     */
    public void onLauncherResumedOrPaused(boolean isResumed) {
        long duration = QuickstepTransitionManager.CONTENT_ALPHA_DURATION;
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        if (isResumed) {
            mAnimator = createAnimToLauncher(null, duration);
        } else {
            mAnimator = createAnimToApp(duration);
        }
        mAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimator = null;
            }
        });
        mAnimator.start();
    }

    /**
     * Create Taskbar animation when going from an app to Launcher.
     * @param toState If known, the state we will end up in when reaching Launcher.
     */
    public Animator createAnimToLauncher(@Nullable LauncherState toState, long duration) {
        PendingAnimation anim = new PendingAnimation(duration);
        anim.add(mTaskbarAnimationController.createAnimToBackgroundAlpha(0, duration));
        if (toState != null) {
            mTaskbarStateHandler.setStateWithAnimation(toState, new StateAnimationConfig(), anim);
        }

        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mIsAnimatingToLauncher = true;
                mTaskbarViewInApp.updateHotseatItemsVisibility();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mIsAnimatingToLauncher = false;
                updateWhichTaskbarViewIsVisible();
            }
        });

        return anim.buildAnim();
    }

    private Animator createAnimToApp(long duration) {
        PendingAnimation anim = new PendingAnimation(duration);
        anim.add(mTaskbarAnimationController.createAnimToBackgroundAlpha(1, duration));
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mIsAnimatingToApp = true;
                mTaskbarViewInApp.updateHotseatItemsVisibility();
                updateWhichTaskbarViewIsVisible();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mIsAnimatingToApp = false;
            }
        });
        return anim.buildAnim();
    }

    /**
     * Should be called when the IME visibility changes, so we can hide/show Taskbar accordingly.
     */
    public void setIsImeVisible(boolean isImeVisible) {
        mTaskbarAnimationController.animateToVisibilityForIme(isImeVisible ? 0 : 1);
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
        return mTaskbarViewInApp.isEventOverAnyItem(ev);
    }

    public boolean isDraggingItem() {
        return mTaskbarViewInApp.isDraggingItem() || mTaskbarViewOnHome.isDraggingItem();
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

        mTaskbarViewInApp.updateRecentTasks(tasksArray);
        mTaskbarViewOnHome.updateRecentTasks(tasksArray);
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
     * Pads the Hotseat to line up exactly with Taskbar's copy of the Hotseat.
     */
    public void alignRealHotseatWithTaskbar() {
        Rect hotseatBounds = new Rect();
        DeviceProfile grid = mLauncher.getDeviceProfile();
        int hotseatHeight = grid.workspacePadding.bottom + grid.taskbarSize;
        int hotseatTopDiff = hotseatHeight - grid.taskbarSize;

        mTaskbarViewOnHome.getHotseatBounds().roundOut(hotseatBounds);
        mLauncher.getHotseat().setPadding(hotseatBounds.left,
                hotseatBounds.top + hotseatTopDiff,
                mTaskbarViewOnHome.getWidth() - hotseatBounds.right,
                mTaskbarViewOnHome.getHeight() - hotseatBounds.bottom);
    }

    private void updateWhichTaskbarViewIsVisible() {
        boolean isInApp = !mLauncher.hasBeenResumed() || mIsAnimatingToLauncher
                || mIsAnimatingToApp;
        if (isInApp) {
            mTaskbarViewInApp.setVisibility(View.VISIBLE);
            mTaskbarViewOnHome.setVisibility(View.INVISIBLE);
            mLauncher.getHotseat().setIconsAlpha(0);
        } else {
            mTaskbarViewInApp.setVisibility(View.INVISIBLE);
            mTaskbarViewOnHome.setVisibility(View.VISIBLE);
            mLauncher.getHotseat().setIconsAlpha(1);
        }
    }

    /**
     * Returns the ratio of the taskbar icon size on home vs in an app.
     */
    public float getTaskbarScaleOnHome() {
        DeviceProfile inAppDp = mTaskbarContainerView.getTaskbarActivityContext()
                .getDeviceProfile();
        DeviceProfile onHomeDp = ActivityContext.lookupContext(mTaskbarViewOnHome.getContext())
                .getDeviceProfile();
        return (float) onHomeDp.cellWidthPx / inAppDp.cellWidthPx;
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
        AnimatedFloat getScaleTarget();
    }

    /**
     * Contains methods that TaskbarAnimationController can call to interface with
     * TaskbarController.
     */
    protected interface TaskbarAnimationControllerCallbacks {
        void updateTaskbarBackgroundAlpha(float alpha);
        void updateTaskbarVisibilityAlpha(float alpha);
        void updateTaskbarScale(float scale);
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
        int getEmptyHotseatViewVisibility(TaskbarView taskbarView);
        /** Returns how much to scale non-icon elements such as spacing and dividers. */
        float getNonIconScale(TaskbarView taskbarView);
        void onItemPositionsChanged(TaskbarView taskbarView);
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

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

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;

import static com.android.launcher3.util.OverviewReleaseFlags.enableGridOnlyOverview;
import static com.android.launcher3.Flags.enableRefactorTaskThumbnail;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_CLOSE_APP_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_FREE_FORM_TAP;
import static com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT;

import android.app.ActivityOptions;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManagerGlobal;
import android.window.SplashScreen;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.logging.StatsLogManager.LauncherEvent;
import com.android.launcher3.model.WellbeingModel;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.popup.SystemShortcut.AppInfo;
import com.android.launcher3.util.InstantAppResolver;
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.launcher3.views.ActivityContext;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;
import com.android.quickstep.views.GroupedTaskView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.quickstep.views.TaskContainer;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecCompat;
import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecsFuture;
import com.android.systemui.shared.recents.view.RecentsTransition;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Represents a system shortcut that can be shown for a recent task. Appears as a single entry in
 * the dropdown menu that shows up when you tap an app icon in Overview.
 */
public interface TaskShortcutFactory {
    @Nullable
    default List<SystemShortcut> getShortcuts(RecentsViewContainer container,
            TaskContainer taskContainer) {
        return null;
    }

    /**
     * Returns {@code true} if it should be shown for grouped task; {@code false} otherwise.
     */
    default boolean showForGroupedTask() {
        return false;
    }

    /**
     * Returns {@code true} if it should be shown for desktop task; {@code false} otherwise.
     */
    default boolean showForDesktopTask() {
        return false;
    }

    /** @return a singleton list if the provided shortcut is non-null, null otherwise */
    @Nullable
    default List<SystemShortcut> createSingletonShortcutList(@Nullable SystemShortcut shortcut) {
        if (shortcut != null) {
            return Collections.singletonList(shortcut);
        }
        return null;
    }

    TaskShortcutFactory APP_INFO = new TaskShortcutFactory() {
        @Override
        public List<SystemShortcut> getShortcuts(RecentsViewContainer container,
                TaskContainer taskContainer) {
            TaskView taskView = taskContainer.getTaskView();
            int actionId = taskContainer.getStagePosition() == STAGE_POSITION_BOTTOM_OR_RIGHT
                    ? R.id.action_app_info_bottom_right
                    : R.id.action_app_info_top_left;

            AppInfo.SplitAccessibilityInfo accessibilityInfo =
                    new AppInfo.SplitAccessibilityInfo(taskView.containsMultipleTasks(),
                            TaskUtils.getTitle(taskView.getContext(), taskContainer.getTask()),
                            actionId
                    );
            return Collections.singletonList(new AppInfo(container, taskContainer.getItemInfo(),
                    taskView, accessibilityInfo));
        }

        @Override
        public boolean showForGroupedTask() {
            return true;
        }
    };

    class SplitSelectSystemShortcut extends SystemShortcut {
        private final TaskContainer mTaskContainer;
        private final SplitPositionOption mSplitPositionOption;

        public SplitSelectSystemShortcut(RecentsViewContainer container,
                TaskContainer taskContainer, TaskView taskView,
                SplitPositionOption option) {
            super(option.iconResId, option.textResId, container, taskContainer.getItemInfo(),
                    taskView);
            mTaskContainer = taskContainer;
            mSplitPositionOption = option;
        }

        @Override
        public void onClick(View view) {
            RecentsView recentsView = mTaskContainer.getTaskView().getRecentsView();
            if (recentsView != null) {
                recentsView.initiateSplitSelect(
                        mTaskContainer,
                        mSplitPositionOption.stagePosition,
                        SplitConfigurationOptions.getLogEventForPosition(
                                mSplitPositionOption.stagePosition));
            }
        }
    }

    /**
     * A menu item, "Save app pair", that allows the user to preserve the current app combination as
     * one persistent icon on the Home screen, allowing for quick split screen launching.
     */
    class SaveAppPairSystemShortcut extends SystemShortcut<RecentsViewContainer> {
        private final GroupedTaskView mTaskView;

        public SaveAppPairSystemShortcut(RecentsViewContainer container, GroupedTaskView taskView,
            int iconResId) {
            super(iconResId, R.string.save_app_pair, container, taskView.getItemInfo(), taskView);
            mTaskView = taskView;
        }

        @Override
        public void onClick(View view) {
            dismissTaskMenuView();
            ((RecentsView) mTarget.getOverviewPanel())
                    .getSplitSelectController().getAppPairsController().saveAppPair(mTaskView);
        }
    }

    class FreeformSystemShortcut extends SystemShortcut<RecentsViewContainer> {
        private static final String TAG = "FreeformSystemShortcut";

        private Handler mHandler;

        private final RecentsView mRecentsView;
        private final TaskContainer mTaskContainer;
        private final TaskView mTaskView;
        private final LauncherEvent mLauncherEvent;

        public FreeformSystemShortcut(int iconRes, int textRes, RecentsViewContainer container,
                TaskContainer taskContainer, LauncherEvent launcherEvent) {
            super(iconRes, textRes, container, taskContainer.getItemInfo(),
                    taskContainer.getTaskView());
            mLauncherEvent = launcherEvent;
            mHandler = new Handler(Looper.getMainLooper());
            mTaskView = taskContainer.getTaskView();
            mRecentsView = container.getOverviewPanel();
            mTaskContainer = taskContainer;
        }

        @Override
        public void onClick(View view) {
            dismissTaskMenuView();
            RecentsView rv = mTarget.getOverviewPanel();
            rv.switchToScreenshot(() -> {
                rv.finishRecentsAnimation(true /* toRecents */, false /* shouldPip */, () -> {
                    mTarget.returnToHomescreen();
                    rv.getHandler().post(this::startActivity);
                });
            });
        }

        private void startActivity() {
            final ActivityOptions options = makeLaunchOptions(mTarget);
            if (options == null) {
                return;
            }
            final Task.TaskKey taskKey = mTaskContainer.getTask().key;
            final int taskId = taskKey.id;
            options.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON);
            if (ActivityManagerWrapper.getInstance().startActivityFromRecents(taskId,
                    options)) {
                final Runnable animStartedListener = () -> {
                    // Hide the task view and wait for the window to be resized
                    // TODO: Consider animating in launcher and do an in-place start activity
                    //       afterwards
                    mRecentsView.setIgnoreResetTask(taskId);
                    mTaskView.setAlpha(0f);
                };

                final int[] position = new int[2];
                View snapShotView = mTaskContainer.getSnapshotView();
                snapShotView.getLocationOnScreen(position);
                final int width = (int) (snapShotView.getWidth() * mTaskView.getScaleX());
                final int height = (int) (snapShotView.getHeight() * mTaskView.getScaleY());
                final Rect taskBounds = new Rect(position[0], position[1],
                        position[0] + width, position[1] + height);

                // Take the thumbnail of the task without a scrim and apply it back after
                Bitmap thumbnail;
                if (enableRefactorTaskThumbnail()) {
                    thumbnail = mTaskContainer.getThumbnail();
                } else {
                    float alpha = mTaskContainer.getThumbnailViewDeprecated().getDimAlpha();
                    mTaskContainer.getThumbnailViewDeprecated().setDimAlpha(0);
                    thumbnail = RecentsTransition.drawViewIntoHardwareBitmap(
                            taskBounds.width(), taskBounds.height(), snapShotView, 1f, Color.BLACK);
                    mTaskContainer.getThumbnailViewDeprecated().setDimAlpha(alpha);
                }

                AppTransitionAnimationSpecsFuture future =
                        new AppTransitionAnimationSpecsFuture(mHandler) {
                            @Override
                            public List<AppTransitionAnimationSpecCompat> composeSpecs() {
                                return Collections.singletonList(
                                        new AppTransitionAnimationSpecCompat(
                                                taskId, thumbnail, taskBounds));
                            }
                        };
                overridePendingAppTransitionMultiThumbFuture(
                        future, animStartedListener, mHandler, true /* scaleUp */,
                        taskKey.displayId);
                mTarget.getStatsLogManager().logger().withItemInfo(mTaskContainer.getItemInfo())
                            .log(mLauncherEvent);
            }
        }

        /**
         * Overrides a pending app transition.
         */
        private void overridePendingAppTransitionMultiThumbFuture(
                AppTransitionAnimationSpecsFuture animationSpecFuture, Runnable animStartedCallback,
                Handler animStartedCallbackHandler, boolean scaleUp, int displayId) {
            try {
                WindowManagerGlobal.getWindowManagerService()
                        .overridePendingAppTransitionMultiThumbFuture(
                                animationSpecFuture.getFuture(),
                                RecentsTransition.wrapStartedListener(animStartedCallbackHandler,
                                        animStartedCallback), scaleUp, displayId);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to override pending app transition (multi-thumbnail future): ",
                        e);
            }
        }

        private ActivityOptions makeLaunchOptions(RecentsViewContainer container) {
            ActivityOptions activityOptions = ActivityOptions.makeBasic();
            activityOptions.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);
            // Arbitrary bounds only because freeform is in dev mode right now
            final View decorView = container.getWindow().getDecorView();
            final WindowInsets insets = decorView.getRootWindowInsets();
            final Rect r = new Rect(0, 0, decorView.getWidth() / 2, decorView.getHeight() / 2);
            r.offsetTo(insets.getSystemWindowInsetLeft() + 50,
                    insets.getSystemWindowInsetTop() + 50);
            activityOptions.setLaunchBounds(r);
            return activityOptions;
        }
    }

    class RemoveTaskSystemShortcut extends SystemShortcut {
        private final TaskContainer mTaskContainer;

        public RemoveTaskSystemShortcut(int iconResId, int textResId,
                RecentsViewContainer container, TaskContainer taskContainer) {
            super(iconResId, textResId, container, taskContainer.getTaskView().getFirstItemInfo(),
                    taskContainer.getTaskView());
            mTaskContainer = taskContainer;
        }

        @Override
        public void onClick(View view) {
            TaskView taskView = mTaskContainer.getTaskView();
            RecentsView<?, ?> recentsView = taskView.getRecentsView();
            if (recentsView != null) {
                dismissTaskMenuView();
                recentsView.dismissTaskView(taskView, true, true);
                mTarget.getStatsLogManager().logger().withItemInfo(mTaskContainer.getItemInfo())
                        .log(LAUNCHER_SYSTEM_SHORTCUT_CLOSE_APP_TAP);
            }
        }
    }

    /**
     * Does NOT add split options in the following scenarios:
     * * 1. Taskbar is not present AND aren't at least 2 tasks in overview to show split options for
     * * 2. Split isn't supported by the task itself (non resizable activity)
     * * 3. We aren't currently in multi-window
     * * 4. The taskView to show split options for is the focused task AND we haven't started
     * * scrolling in overview (if we haven't scrolled, there's a split overview action button so
     * * we don't need this menu option)
     */
    TaskShortcutFactory SPLIT_SELECT = new TaskShortcutFactory() {
        @Override
        public List<SystemShortcut> getShortcuts(RecentsViewContainer container,
                TaskContainer taskContainer) {
            DeviceProfile deviceProfile = container.getDeviceProfile();
            final Task task = taskContainer.getTask();
            final int intentFlags = task.key.baseIntent.getFlags();
            final TaskView taskView = taskContainer.getTaskView();
            final RecentsView recentsView = taskView.getRecentsView();
            final RecentsPagedOrientationHandler orientationHandler =
                    recentsView.getPagedOrientationHandler();

            boolean notEnoughTasksToSplit =
                    !deviceProfile.isTaskbarPresent && recentsView.getTaskViewCount() < 2;
            boolean isTaskSplitNotSupported = !task.isDockable ||
                    (intentFlags & FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS) != 0;
            boolean hideForExistingMultiWindow = container.getDeviceProfile().getDeviceProperties().isMultiWindowMode();

            if (notEnoughTasksToSplit || isTaskSplitNotSupported || hideForExistingMultiWindow) {
                return null;
            }

            return orientationHandler.getSplitPositionOptions(deviceProfile)
                    .stream()
                    .map((Function<SplitPositionOption, SystemShortcut>) option ->
                            new SplitSelectSystemShortcut(container, taskContainer, taskView,
                                    option))
                    .collect(Collectors.toList());
        }
    };

    TaskShortcutFactory SAVE_APP_PAIR = new TaskShortcutFactory() {
        @Nullable
        @Override
        public List<SystemShortcut> getShortcuts(RecentsViewContainer container,
                TaskContainer taskContainer) {
            DeviceProfile deviceProfile = container.getDeviceProfile();
            final TaskView taskView = taskContainer.getTaskView();
            final RecentsView recentsView = taskView.getRecentsView();
            boolean isLargeTile = deviceProfile.getDeviceProperties().isTablet() && taskView.isLargeTile();
            boolean isInExpectedScrollPosition =
                    recentsView.isTaskInExpectedScrollPosition(taskView);
            boolean shouldShowActionsButtonInstead =
                    isLargeTile && isInExpectedScrollPosition;

            // No "save app pair" menu item if:
            // - we are in 3p launcher
            // - the Overview Actions Button should be visible
            // - the task view is not a valid save-able split pair
            if (!OverviewComponentObserver.INSTANCE.get(container.asContext())
                    .isHomeAndOverviewSame()
                    || shouldShowActionsButtonInstead
                    || !recentsView.getSplitSelectController().getAppPairsController()
                            .canSaveAppPair(taskView)) {
                return null;
            }

            int iconResId = deviceProfile.isLeftRightSplit
                    ? R.drawable.ic_save_app_pair_left_right
                    : R.drawable.ic_save_app_pair_up_down;

            return Collections.singletonList(
                    new SaveAppPairSystemShortcut(container,
                            (GroupedTaskView) taskView, iconResId));
        }

        @Override
        public boolean showForGroupedTask() {
            return true;
        }
    };

    TaskShortcutFactory FREE_FORM = new TaskShortcutFactory() {
        @Override
        public List<SystemShortcut> getShortcuts(RecentsViewContainer container,
                TaskContainer taskContainer) {
            final Task task = taskContainer.getTask();
            if (!task.isDockable) {
                return null;
            }
            if (!isAvailable(container)) {
                return null;
            }

            return Collections.singletonList(new FreeformSystemShortcut(
                    R.drawable.ic_caption_desktop_button_foreground,
                    R.string.recent_task_option_freeform, container, taskContainer,
                    LAUNCHER_SYSTEM_SHORTCUT_FREE_FORM_TAP));
        }

        private boolean isAvailable(RecentsViewContainer container) {
            return Settings.Global.getInt(
                    container.asContext().getContentResolver(),
                    Settings.Global.DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT, 0) != 0
                    && !DesktopModeStatus.canEnterDesktopMode(container.asContext());
        }
    };

    TaskShortcutFactory PIN = new TaskShortcutFactory() {
        @Override
        public List<SystemShortcut> getShortcuts(RecentsViewContainer container,
                TaskContainer taskContainer) {
            if (!SystemUiProxy.INSTANCE.get(container.asContext()).isActive()) {
                return null;
            }
            if (!ActivityManagerWrapper.getInstance().isScreenPinningEnabled()) {
                return null;
            }
            if (ActivityManagerWrapper.getInstance().isLockToAppActive()) {
                // We shouldn't be able to pin while an app is locked.
                return null;
            }
            return Collections.singletonList(new PinSystemShortcut(container, taskContainer));
        }
    };

    class PinSystemShortcut extends SystemShortcut<RecentsViewContainer> {

        private static final String TAG = "PinSystemShortcut";

        private final TaskContainer mTaskContainer;

        public PinSystemShortcut(RecentsViewContainer target,
                TaskContainer taskContainer) {
            super(R.drawable.ic_pin, R.string.recent_task_option_pin, target,
                    taskContainer.getItemInfo(), taskContainer.getTaskView());
            mTaskContainer = taskContainer;
        }

        @Override
        public void onClick(View view) {
            if (mTaskContainer.getTaskView().launchAsStaticTile() != null) {
                SystemUiProxy.INSTANCE.get(mTarget.asContext()).startScreenPinning(
                        mTaskContainer.getTask().key.id);
            }
            dismissTaskMenuView();
            mTarget.getStatsLogManager().logger().withItemInfo(mTaskContainer.getItemInfo())
                        .log(LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_PIN_TAP);
        }
    }

    TaskShortcutFactory INSTALL = new TaskShortcutFactory() {
        @Override
        public List<SystemShortcut> getShortcuts(RecentsViewContainer container,
                TaskContainer taskContainer) {
            Task t = taskContainer.getTask();
            return InstantAppResolver.newInstance(container.asContext()).isInstantApp(
                    t.getTopComponent().getPackageName(), t.getKey().userId)
                    ? Collections.singletonList(new SystemShortcut.Install(container,
                    taskContainer.getItemInfo(), taskContainer.getTaskView()))
                    : null;
        }
    };

    TaskShortcutFactory WELLBEING = new TaskShortcutFactory() {
        @Override
        public List<SystemShortcut> getShortcuts(RecentsViewContainer container,
                TaskContainer taskContainer) {
            SystemShortcut<ActivityContext> wellbeingShortcut =
                    WellbeingModel.SHORTCUT_FACTORY.getShortcut(container,
                            taskContainer.getItemInfo(), taskContainer.getTaskView());
            return createSingletonShortcutList(wellbeingShortcut);
        }
    };

    TaskShortcutFactory SCREENSHOT = new TaskShortcutFactory() {
        @Override
        public List<SystemShortcut> getShortcuts(RecentsViewContainer container,
                TaskContainer taskContainer) {
            if (!taskContainer.getOverlay().isRealSnapshot()) {
                return null;
            }

            SystemShortcut screenshotShortcut = taskContainer.getOverlay().getScreenshotShortcut(
                    container, taskContainer.getItemInfo(), taskContainer.getTaskView());
            return createSingletonShortcutList(screenshotShortcut);
        }

        @Override
        public boolean showForDesktopTask() {
            return true;
        }
    };

    TaskShortcutFactory MODAL = new TaskShortcutFactory() {
        @Override
        public List<SystemShortcut> getShortcuts(RecentsViewContainer container,
                TaskContainer taskContainer) {
            if (!taskContainer.getOverlay().isRealSnapshot()) {
                return null;
            }

            // Modal only works with grid size tiles with enableGridOnlyOverview enabled on
            // tablets / foldables. With enableGridOnlyOverview off, for large tiles it works,
            // but the tile needs to be in the center of Recents / Overview.
            boolean isTablet = container.getDeviceProfile().getDeviceProperties().isTablet();
            RecentsView recentsView = container.getOverviewPanel();
            boolean isLargeTileInCenterOfOverview = taskContainer.getTaskView().isLargeTile()
                    && recentsView.isFocusedTaskInExpectedScrollPosition();
            if (isTablet
                    && !isLargeTileInCenterOfOverview
                    && !enableGridOnlyOverview()) {
                return null;
            }

            boolean isFakeLandscape = !taskContainer.getTaskView().getPagedOrientationHandler()
                    .isLayoutNaturalToLauncher();
            if (isFakeLandscape) {
                return null;
            }

            if (taskContainer.getOverlay().isThumbnailRotationDifferentFromTask()) {
                return null;
            }

            SystemShortcut modalStateSystemShortcut =
                    taskContainer.getOverlay().getModalStateSystemShortcut(
                            taskContainer.getItemInfo(), taskContainer.getTaskView());
            return createSingletonShortcutList(modalStateSystemShortcut);
        }
    };

    TaskShortcutFactory REMOVE_TASK = new TaskShortcutFactory() {
        @Override
        public List<SystemShortcut> getShortcuts(RecentsViewContainer container,
                TaskContainer taskContainer) {
            return Collections.singletonList(new RemoveTaskSystemShortcut(
                    R.drawable.ic_remove_task_option,
                    R.string.recent_task_option_remove_task, container, taskContainer));
        }

        @Override
        public boolean showForGroupedTask() {
            return true;
        }

        @Override
        public boolean showForDesktopTask() {
            return true;
        }
    };
}

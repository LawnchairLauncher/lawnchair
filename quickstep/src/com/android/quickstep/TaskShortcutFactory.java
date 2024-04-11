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

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_FREE_FORM_TAP;
import static com.android.window.flags.Flags.enableDesktopWindowingMode;

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
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.StatsLogManager.LauncherEvent;
import com.android.launcher3.model.WellbeingModel;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.popup.SystemShortcut.AppInfo;
import com.android.launcher3.util.InstantAppResolver;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.launcher3.views.ActivityContext;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;
import com.android.quickstep.views.GroupedTaskView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.quickstep.views.TaskThumbnailView;
import com.android.quickstep.views.TaskView;
import com.android.quickstep.views.TaskView.TaskIdAttributeContainer;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecCompat;
import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecsFuture;
import com.android.systemui.shared.recents.view.RecentsTransition;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import java.util.Arrays;
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
            TaskIdAttributeContainer taskContainer) {
        return null;
    }

    default boolean showForSplitscreen() {
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
                TaskIdAttributeContainer taskContainer) {
            TaskView taskView = taskContainer.getTaskView();
            AppInfo.SplitAccessibilityInfo accessibilityInfo =
                    new AppInfo.SplitAccessibilityInfo(taskView.containsMultipleTasks(),
                            TaskUtils.getTitle(taskView.getContext(), taskContainer.getTask()),
                            taskContainer.getA11yNodeId()
                    );
            return Collections.singletonList(new AppInfo(container, taskContainer.getItemInfo(),
                    taskView, accessibilityInfo));
        }

        @Override
        public boolean showForSplitscreen() {
            return true;
        }
    };

    class SplitSelectSystemShortcut extends SystemShortcut {
        private final TaskView mTaskView;
        private final SplitPositionOption mSplitPositionOption;

        public SplitSelectSystemShortcut(RecentsViewContainer container, TaskView taskView,
                SplitPositionOption option) {
            super(option.iconResId, option.textResId, container, taskView.getItemInfo(), taskView);
            mTaskView = taskView;
            mSplitPositionOption = option;
        }

        @Override
        public void onClick(View view) {
            mTaskView.initiateSplitSelect(mSplitPositionOption);
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
                super(iconResId, R.string.save_app_pair, container,
                    taskView.getItemInfo(), taskView);
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
        private final TaskThumbnailView mThumbnailView;
        private final TaskView mTaskView;
        private final LauncherEvent mLauncherEvent;

        public FreeformSystemShortcut(int iconRes, int textRes, RecentsViewContainer container,
                TaskIdAttributeContainer taskContainer, LauncherEvent launcherEvent) {
            super(iconRes, textRes, container, taskContainer.getItemInfo(),
                    taskContainer.getTaskView());
            mLauncherEvent = launcherEvent;
            mHandler = new Handler(Looper.getMainLooper());
            mTaskView = taskContainer.getTaskView();
            mRecentsView = container.getOverviewPanel();
            mThumbnailView = taskContainer.getThumbnailView();
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
            final Task.TaskKey taskKey = mTaskView.getTask().key;
            final int taskId = taskKey.id;
            final ActivityOptions options = makeLaunchOptions(mTarget);
            if (options != null) {
                options.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON);
            }
            if (options != null
                    && ActivityManagerWrapper.getInstance().startActivityFromRecents(taskId,
                    options)) {
                final Runnable animStartedListener = () -> {
                    // Hide the task view and wait for the window to be resized
                    // TODO: Consider animating in launcher and do an in-place start activity
                    //       afterwards
                    mRecentsView.setIgnoreResetTask(taskId);
                    mTaskView.setAlpha(0f);
                };

                final int[] position = new int[2];
                mThumbnailView.getLocationOnScreen(position);
                final int width = (int) (mThumbnailView.getWidth() * mTaskView.getScaleX());
                final int height = (int) (mThumbnailView.getHeight() * mTaskView.getScaleY());
                final Rect taskBounds = new Rect(position[0], position[1],
                        position[0] + width, position[1] + height);

                // Take the thumbnail of the task without a scrim and apply it back after
                float alpha = mThumbnailView.getDimAlpha();
                mThumbnailView.setDimAlpha(0);
                Bitmap thumbnail = RecentsTransition.drawViewIntoHardwareBitmap(
                        taskBounds.width(), taskBounds.height(), mThumbnailView, 1f,
                        Color.BLACK);
                mThumbnailView.setDimAlpha(alpha);

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
                mTarget.getStatsLogManager().logger().withItemInfo(mTaskView.getItemInfo())
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
                TaskIdAttributeContainer taskContainer) {
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
            boolean hideForExistingMultiWindow = container.getDeviceProfile().isMultiWindowMode;
            boolean isFocusedTask = deviceProfile.isTablet && taskView.isFocusedTask();
            boolean isTaskInExpectedScrollPosition =
                    recentsView.isTaskInExpectedScrollPosition(recentsView.indexOfChild(taskView));

            if (notEnoughTasksToSplit || isTaskSplitNotSupported || hideForExistingMultiWindow
                    || (isFocusedTask && isTaskInExpectedScrollPosition)) {
                return null;
            }

            return orientationHandler.getSplitPositionOptions(deviceProfile)
                    .stream()
                    .map((Function<SplitPositionOption, SystemShortcut>) option ->
                            new SplitSelectSystemShortcut(container, taskView, option))
                    .collect(Collectors.toList());
        }
    };

    TaskShortcutFactory SAVE_APP_PAIR = new TaskShortcutFactory() {
        @Nullable
        @Override
        public List<SystemShortcut> getShortcuts(RecentsViewContainer container,
                TaskIdAttributeContainer taskContainer) {
            DeviceProfile deviceProfile = container.getDeviceProfile();
            final TaskView taskView = taskContainer.getTaskView();
            final RecentsView recentsView = taskView.getRecentsView();
            boolean isLargeTileFocusedTask = deviceProfile.isTablet && taskView.isFocusedTask();
            boolean isInExpectedScrollPosition =
                    recentsView.isTaskInExpectedScrollPosition(recentsView.indexOfChild(taskView));
            boolean shouldShowActionsButtonInstead =
                    isLargeTileFocusedTask && isInExpectedScrollPosition;
            boolean hasUnpinnableApp = Arrays.stream(taskView.getTaskIdAttributeContainers())
                    .anyMatch(att -> att != null && att.getItemInfo() != null
                            && ((att.getItemInfo().runtimeStatusFlags
                                & ItemInfoWithIcon.FLAG_NOT_PINNABLE) != 0));

            // No "save app pair" menu item if:
            // - app pairs feature is not enabled
            // - we are in 3p launcher
            // - the task in question is a single task
            // - at least one app in app pair is unpinnable
            // - the Overview Actions Button should be visible
            // - the task is not a GroupedTaskView
            if (!FeatureFlags.enableAppPairs()
                    || !recentsView.supportsAppPairs()
                    || !taskView.containsMultipleTasks()
                    || hasUnpinnableApp
                    || shouldShowActionsButtonInstead
                    || !(taskView instanceof GroupedTaskView)) {
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
        public boolean showForSplitscreen() {
            return true;
        }
    };

    TaskShortcutFactory FREE_FORM = new TaskShortcutFactory() {
        @Override
        public List<SystemShortcut> getShortcuts(RecentsViewContainer container,
                TaskIdAttributeContainer taskContainer) {
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
                    && !enableDesktopWindowingMode();
        }
    };

    TaskShortcutFactory PIN = new TaskShortcutFactory() {
        @Override
        public List<SystemShortcut> getShortcuts(RecentsViewContainer container,
                TaskIdAttributeContainer taskContainer) {
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

        private final TaskView mTaskView;

        public PinSystemShortcut(RecentsViewContainer target,
                TaskIdAttributeContainer taskContainer) {
            super(R.drawable.ic_pin, R.string.recent_task_option_pin, target,
                    taskContainer.getItemInfo(), taskContainer.getTaskView());
            mTaskView = taskContainer.getTaskView();
        }

        @Override
        public void onClick(View view) {
            if (mTaskView.launchTaskAnimated() != null) {
                SystemUiProxy.INSTANCE.get(mTarget.asContext()).startScreenPinning(
                        mTaskView.getTask().key.id);
            }
            dismissTaskMenuView();
            mTarget.getStatsLogManager().logger().withItemInfo(mTaskView.getItemInfo())
                    .log(LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_PIN_TAP);
        }
    }

    TaskShortcutFactory INSTALL = new TaskShortcutFactory() {
        @Override
        public List<SystemShortcut> getShortcuts(RecentsViewContainer container,
                TaskIdAttributeContainer taskContainer) {
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
                TaskIdAttributeContainer taskContainer) {
            SystemShortcut<ActivityContext> wellbeingShortcut =
                    WellbeingModel.SHORTCUT_FACTORY.getShortcut(container,
                            taskContainer.getItemInfo(), taskContainer.getTaskView());
            return createSingletonShortcutList(wellbeingShortcut);
        }
    };

    TaskShortcutFactory SCREENSHOT = new TaskShortcutFactory() {
        @Override
        public List<SystemShortcut> getShortcuts(RecentsViewContainer container,
                TaskIdAttributeContainer taskContainer) {
            SystemShortcut screenshotShortcut =
                    taskContainer.getThumbnailView().getTaskOverlay()
                            .getScreenshotShortcut(container, taskContainer.getItemInfo(),
                                    taskContainer.getTaskView());
            return createSingletonShortcutList(screenshotShortcut);
        }
    };

    TaskShortcutFactory MODAL = new TaskShortcutFactory() {
        @Override
        public List<SystemShortcut> getShortcuts(RecentsViewContainer container,
                TaskIdAttributeContainer taskContainer) {
            SystemShortcut modalStateSystemShortcut =
                    taskContainer.getThumbnailView().getTaskOverlay()
                            .getModalStateSystemShortcut(
                                    taskContainer.getItemInfo(), taskContainer.getTaskView());
            return createSingletonShortcutList(modalStateSystemShortcut);
        }
    };
}

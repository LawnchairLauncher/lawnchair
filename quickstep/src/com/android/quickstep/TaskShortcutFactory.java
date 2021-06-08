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

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.launcher3.config.FeatureFlags.ENABLE_OVERVIEW_SELECTIONS;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_FREE_FORM_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_SPLIT_SCREEN_TAP;

import android.app.Activity;
import android.app.ActivityOptions;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.window.SplashScreen;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.logging.StatsLogManager.LauncherEvent;
import com.android.launcher3.model.WellbeingModel;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.popup.SystemShortcut.AppInfo;
import com.android.launcher3.util.InstantAppResolver;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskThumbnailView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecCompat;
import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecsFuture;
import com.android.systemui.shared.recents.view.RecentsTransition;
import com.android.systemui.shared.system.ActivityCompat;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.ActivityOptionsCompat;
import com.android.systemui.shared.system.WindowManagerWrapper;

import java.util.Collections;
import java.util.List;

/**
 * Represents a system shortcut that can be shown for a recent task.
 */
public interface TaskShortcutFactory {
    SystemShortcut getShortcut(BaseDraggingActivity activity, TaskView view);

    TaskShortcutFactory APP_INFO = (activity, view) -> new AppInfo(activity, view.getItemInfo());

    abstract class MultiWindowFactory implements TaskShortcutFactory {

        private final int mIconRes;
        private final int mTextRes;
        private final LauncherEvent mLauncherEvent;

        MultiWindowFactory(int iconRes, int textRes, LauncherEvent launcherEvent) {
            mIconRes = iconRes;
            mTextRes = textRes;
            mLauncherEvent = launcherEvent;
        }

        protected abstract boolean isAvailable(BaseDraggingActivity activity, int displayId);
        protected abstract ActivityOptions makeLaunchOptions(Activity activity);
        protected abstract boolean onActivityStarted(BaseDraggingActivity activity);

        @Override
        public SystemShortcut getShortcut(BaseDraggingActivity activity, TaskView taskView) {
            final Task task  = taskView.getTask();
            if (!task.isDockable) {
                return null;
            }
            if (!isAvailable(activity, task.key.displayId)) {
                return null;
            }
            return new MultiWindowSystemShortcut(mIconRes, mTextRes, activity, taskView, this,
                    mLauncherEvent);
        }
    }

    class SplitSelectSystemShortcut extends SystemShortcut {
        private final TaskView mTaskView;
        private SplitPositionOption mSplitPositionOption;
        public SplitSelectSystemShortcut(BaseDraggingActivity target, TaskView taskView,
                SplitPositionOption option) {
            super(option.mIconResId, option.mTextResId, target, taskView.getItemInfo());
            mTaskView = taskView;
            mSplitPositionOption = option;
            setEnabled(taskView.getRecentsView().getTaskViewCount() > 1);
        }

        @Override
        public void onClick(View view) {
            mTaskView.initiateSplitSelect(mSplitPositionOption);
        }
    }

    class MultiWindowSystemShortcut extends SystemShortcut {

        private Handler mHandler;

        private final RecentsView mRecentsView;
        private final TaskThumbnailView mThumbnailView;
        private final TaskView mTaskView;
        private final MultiWindowFactory mFactory;
        private final LauncherEvent mLauncherEvent;

        public MultiWindowSystemShortcut(int iconRes, int textRes, BaseDraggingActivity activity,
                TaskView taskView, MultiWindowFactory factory, LauncherEvent launcherEvent) {
            super(iconRes, textRes, activity, taskView.getItemInfo());
            mLauncherEvent = launcherEvent;
            mHandler = new Handler(Looper.getMainLooper());
            mTaskView = taskView;
            mRecentsView = activity.getOverviewPanel();
            mThumbnailView = taskView.getThumbnail();
            mFactory = factory;
        }

        @Override
        public void onClick(View view) {
            Task.TaskKey taskKey = mTaskView.getTask().key;
            final int taskId = taskKey.id;

            final View.OnLayoutChangeListener onLayoutChangeListener =
                    new View.OnLayoutChangeListener() {
                        @Override
                        public void onLayoutChange(View v, int l, int t, int r, int b,
                                int oldL, int oldT, int oldR, int oldB) {
                            mTaskView.getRootView().removeOnLayoutChangeListener(this);
                            mRecentsView.clearIgnoreResetTask(taskId);

                            // Start animating in the side pages once launcher has been resized
                            mRecentsView.dismissTask(mTaskView, false, false);
                        }
                    };

            final DeviceProfile.OnDeviceProfileChangeListener onDeviceProfileChangeListener =
                    new DeviceProfile.OnDeviceProfileChangeListener() {
                        @Override
                        public void onDeviceProfileChanged(DeviceProfile dp) {
                            mTarget.removeOnDeviceProfileChangeListener(this);
                            if (dp.isMultiWindowMode) {
                                mTaskView.getRootView().addOnLayoutChangeListener(
                                        onLayoutChangeListener);
                            }
                        }
                    };

            dismissTaskMenuView(mTarget);

            ActivityOptions options = mFactory.makeLaunchOptions(mTarget);
            if (options != null) {
                options.setSplashscreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON);
            }
            if (options != null
                    && ActivityManagerWrapper.getInstance().startActivityFromRecents(taskId,
                            options)) {
                if (!mFactory.onActivityStarted(mTarget)) {
                    return;
                }
                // Add a device profile change listener to kick off animating the side tasks
                // once we enter multiwindow mode and relayout
                mTarget.addOnDeviceProfileChangeListener(onDeviceProfileChangeListener);

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
                        return Collections.singletonList(new AppTransitionAnimationSpecCompat(
                                taskId, thumbnail, taskBounds));
                    }
                };
                WindowManagerWrapper.getInstance().overridePendingAppTransitionMultiThumbFuture(
                        future, animStartedListener, mHandler, true /* scaleUp */,
                        taskKey.displayId);
                mTarget.getStatsLogManager().logger().withItemInfo(mTaskView.getItemInfo())
                        .log(mLauncherEvent);
            }
        }
    }

    TaskShortcutFactory SPLIT_SCREEN = new MultiWindowFactory(R.drawable.ic_split_screen,
            R.string.recent_task_option_split_screen, LAUNCHER_SYSTEM_SHORTCUT_SPLIT_SCREEN_TAP) {

        @Override
        protected boolean isAvailable(BaseDraggingActivity activity, int displayId) {
            // Don't show menu-item if already in multi-window and the task is from
            // the secondary display.
            // TODO(b/118266305): Temporarily disable splitscreen for secondary display while new
            // implementation is enabled
            return !activity.getDeviceProfile().isMultiWindowMode
                    && (displayId == -1 || displayId == DEFAULT_DISPLAY);
        }

        @Override
        public SystemShortcut getShortcut(BaseDraggingActivity activity, TaskView taskView) {
            SystemShortcut shortcut = super.getShortcut(activity, taskView);
            if (shortcut != null && FeatureFlags.ENABLE_SPLIT_SELECT.get()) {
                // Disable if there's only one recent app for split screen
                shortcut.setEnabled(taskView.getRecentsView().getTaskViewCount() > 1);
            }
            return shortcut;
        }

        @Override
        protected ActivityOptions makeLaunchOptions(Activity activity) {
            final ActivityCompat act = new ActivityCompat(activity);
            final int navBarPosition = WindowManagerWrapper.getInstance().getNavBarPosition(
                    act.getDisplayId());
            if (navBarPosition == WindowManagerWrapper.NAV_BAR_POS_INVALID) {
                return null;
            }
            boolean dockTopOrLeft = navBarPosition != WindowManagerWrapper.NAV_BAR_POS_LEFT;
            return ActivityOptionsCompat.makeSplitScreenOptions(dockTopOrLeft);
        }

        @Override
        protected boolean onActivityStarted(BaseDraggingActivity activity) {
            return true;
        }
    };

    TaskShortcutFactory FREE_FORM = new MultiWindowFactory(R.drawable.ic_split_screen,
            R.string.recent_task_option_freeform, LAUNCHER_SYSTEM_SHORTCUT_FREE_FORM_TAP) {

        @Override
        protected boolean isAvailable(BaseDraggingActivity activity, int displayId) {
            return ActivityManagerWrapper.getInstance().supportsFreeformMultiWindow(activity);
        }

        @Override
        protected ActivityOptions makeLaunchOptions(Activity activity) {
            ActivityOptions activityOptions = ActivityOptionsCompat.makeFreeformOptions();
            // Arbitrary bounds only because freeform is in dev mode right now
            Rect r = new Rect(50, 50, 200, 200);
            activityOptions.setLaunchBounds(r);
            return activityOptions;
        }

        @Override
        protected boolean onActivityStarted(BaseDraggingActivity activity) {
            activity.returnToHomescreen();
            return true;
        }
    };

    TaskShortcutFactory PIN = (activity, tv) -> {
        if (!SystemUiProxy.INSTANCE.get(activity).isActive()) {
            return null;
        }
        if (!ActivityManagerWrapper.getInstance().isScreenPinningEnabled()) {
            return null;
        }
        if (ActivityManagerWrapper.getInstance().isLockToAppActive()) {
            // We shouldn't be able to pin while an app is locked.
            return null;
        }
        return new PinSystemShortcut(activity, tv);
    };

    class PinSystemShortcut extends SystemShortcut {

        private static final String TAG = "PinSystemShortcut";

        private final TaskView mTaskView;

        public PinSystemShortcut(BaseDraggingActivity target, TaskView tv) {
            super(R.drawable.ic_pin, R.string.recent_task_option_pin, target, tv.getItemInfo());
            mTaskView = tv;
        }

        @Override
        public void onClick(View view) {
            if (mTaskView.launchTaskAnimated() != null) {
                SystemUiProxy.INSTANCE.get(mTarget).startScreenPinning(mTaskView.getTask().key.id);
            }
            dismissTaskMenuView(mTarget);
            mTarget.getStatsLogManager().logger().withItemInfo(mTaskView.getItemInfo())
                    .log(LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_PIN_TAP);
        }
    }

    TaskShortcutFactory INSTALL = (activity, view) ->
            InstantAppResolver.newInstance(activity).isInstantApp(activity,
                 view.getTask().getTopComponent().getPackageName())
                    ? new SystemShortcut.Install(activity, view.getItemInfo()) : null;

    TaskShortcutFactory WELLBEING = (activity, view) ->
            WellbeingModel.SHORTCUT_FACTORY.getShortcut(activity, view.getItemInfo());

    TaskShortcutFactory SCREENSHOT = (activity, tv) -> tv.getThumbnail().getTaskOverlay()
            .getScreenshotShortcut(activity, tv.getItemInfo());

    TaskShortcutFactory MODAL = (activity, tv) -> {
        if (ENABLE_OVERVIEW_SELECTIONS.get()) {
            return tv.getThumbnail().getTaskOverlay().getModalStateSystemShortcut(tv.getItemInfo());
        }
        return null;
    };
}

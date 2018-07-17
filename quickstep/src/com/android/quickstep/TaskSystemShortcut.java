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

import static com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch.TAP;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.View;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.util.InstantAppResolver;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskThumbnailView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecCompat;
import com.android.systemui.shared.recents.view.AppTransitionAnimationSpecsFuture;
import com.android.systemui.shared.recents.view.RecentsTransition;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.ActivityOptionsCompat;
import com.android.systemui.shared.system.WindowManagerWrapper;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Represents a system shortcut that can be shown for a recent task.
 */
public class TaskSystemShortcut<T extends SystemShortcut> extends SystemShortcut {

    private static final String TAG = "TaskSystemShortcut";

    protected T mSystemShortcut;

    protected TaskSystemShortcut(T systemShortcut) {
        super(systemShortcut.iconResId, systemShortcut.labelResId);
        mSystemShortcut = systemShortcut;
    }

    protected TaskSystemShortcut(int iconResId, int labelResId) {
        super(iconResId, labelResId);
    }

    @Override
    public View.OnClickListener getOnClickListener(
            BaseDraggingActivity activity, ItemInfo itemInfo) {
        return null;
    }

    public View.OnClickListener getOnClickListener(BaseDraggingActivity activity, TaskView view) {
        Task task = view.getTask();

        ShortcutInfo dummyInfo = new ShortcutInfo();
        dummyInfo.intent = new Intent();
        ComponentName component = task.getTopComponent();
        dummyInfo.intent.setComponent(component);
        dummyInfo.user = UserHandle.of(task.key.userId);
        dummyInfo.title = TaskUtils.getTitle(activity, task);

        return getOnClickListenerForTask(activity, task, dummyInfo);
    }

    protected View.OnClickListener getOnClickListenerForTask(
            BaseDraggingActivity activity, Task task, ItemInfo dummyInfo) {
        return mSystemShortcut.getOnClickListener(activity, dummyInfo);
    }

    public static class AppInfo extends TaskSystemShortcut<SystemShortcut.AppInfo> {
        public AppInfo() {
            super(new SystemShortcut.AppInfo());
        }
    }

    public static class SplitScreen extends TaskSystemShortcut {

        private Handler mHandler;

        public SplitScreen() {
            super(R.drawable.ic_split_screen, R.string.recent_task_option_split_screen);
            mHandler = new Handler(Looper.getMainLooper());
        }

        @Override
        public View.OnClickListener getOnClickListener(
                BaseDraggingActivity activity, TaskView taskView) {
            if (activity.getDeviceProfile().isMultiWindowMode) {
                return null;
            }
            final Task task  = taskView.getTask();
            final int taskId = task.key.id;
            if (!task.isDockable) {
                return null;
            }
            final RecentsView recentsView = activity.getOverviewPanel();

            final TaskThumbnailView thumbnailView = taskView.getThumbnail();
            return (v -> {
                final View.OnLayoutChangeListener onLayoutChangeListener =
                        new View.OnLayoutChangeListener() {
                            @Override
                            public void onLayoutChange(View v, int l, int t, int r, int b,
                                    int oldL, int oldT, int oldR, int oldB) {
                                taskView.getRootView().removeOnLayoutChangeListener(this);
                                recentsView.removeIgnoreResetTask(taskView);

                                // Start animating in the side pages once launcher has been resized
                                recentsView.dismissTask(taskView, false, false);
                            }
                        };

                final DeviceProfile.OnDeviceProfileChangeListener onDeviceProfileChangeListener =
                        new DeviceProfile.OnDeviceProfileChangeListener() {
                            @Override
                            public void onDeviceProfileChanged(DeviceProfile dp) {
                                activity.removeOnDeviceProfileChangeListener(this);
                                if (dp.isMultiWindowMode) {
                                    taskView.getRootView().addOnLayoutChangeListener(
                                            onLayoutChangeListener);
                                }
                            }
                        };

                dismissTaskMenuView(activity);

                final int navBarPosition = WindowManagerWrapper.getInstance().getNavBarPosition();
                if (navBarPosition == WindowManagerWrapper.NAV_BAR_POS_INVALID) {
                    return;
                }
                boolean dockTopOrLeft = navBarPosition != WindowManagerWrapper.NAV_BAR_POS_LEFT;
                if (ActivityManagerWrapper.getInstance().startActivityFromRecents(taskId,
                        ActivityOptionsCompat.makeSplitScreenOptions(dockTopOrLeft))) {
                    ISystemUiProxy sysUiProxy = RecentsModel.getInstance(activity).getSystemUiProxy();
                    try {
                        sysUiProxy.onSplitScreenInvoked();
                    } catch (RemoteException e) {
                        Log.w(TAG, "Failed to notify SysUI of split screen: ", e);
                        return;
                    }
                    activity.getUserEventDispatcher().logActionOnControl(TAP,
                            LauncherLogProto.ControlType.SPLIT_SCREEN_TARGET);
                    // Add a device profile change listener to kick off animating the side tasks
                    // once we enter multiwindow mode and relayout
                    activity.addOnDeviceProfileChangeListener(onDeviceProfileChangeListener);

                    final Runnable animStartedListener = () -> {
                        // Hide the task view and wait for the window to be resized
                        // TODO: Consider animating in launcher and do an in-place start activity
                        //       afterwards
                        recentsView.addIgnoreResetTask(taskView);
                        taskView.setAlpha(0f);
                    };

                    final int[] position = new int[2];
                    thumbnailView.getLocationOnScreen(position);
                    final int width = (int) (thumbnailView.getWidth() * taskView.getScaleX());
                    final int height = (int) (thumbnailView.getHeight() * taskView.getScaleY());
                    final Rect taskBounds = new Rect(position[0], position[1],
                            position[0] + width, position[1] + height);

                    // Take the thumbnail of the task without a scrim and apply it back after
                    float alpha = thumbnailView.getDimAlpha();
                    thumbnailView.setDimAlpha(0);
                    Bitmap thumbnail = RecentsTransition.drawViewIntoHardwareBitmap(
                            taskBounds.width(), taskBounds.height(), thumbnailView, 1f,
                            Color.BLACK);
                    thumbnailView.setDimAlpha(alpha);

                    AppTransitionAnimationSpecsFuture future =
                            new AppTransitionAnimationSpecsFuture(mHandler) {
                        @Override
                        public List<AppTransitionAnimationSpecCompat> composeSpecs() {
                            return Collections.singletonList(new AppTransitionAnimationSpecCompat(
                                    taskId, thumbnail, taskBounds));
                        }
                    };
                    WindowManagerWrapper.getInstance().overridePendingAppTransitionMultiThumbFuture(
                            future, animStartedListener, mHandler, true /* scaleUp */);
                }
            });
        }
    }

    public static class Pin extends TaskSystemShortcut {

        private static final String TAG = Pin.class.getSimpleName();

        private Handler mHandler;

        public Pin() {
            super(R.drawable.ic_pin, R.string.recent_task_option_pin);
            mHandler = new Handler(Looper.getMainLooper());
        }

        @Override
        public View.OnClickListener getOnClickListener(
                BaseDraggingActivity activity, TaskView taskView) {
            ISystemUiProxy sysUiProxy = RecentsModel.getInstance(activity).getSystemUiProxy();
            if (sysUiProxy == null) {
                return null;
            }
            if (!ActivityManagerWrapper.getInstance().isScreenPinningEnabled()) {
                return null;
            }
            if (ActivityManagerWrapper.getInstance().isLockToAppActive()) {
                // We shouldn't be able to pin while an app is locked.
                return null;
            }
            return view -> {
                Consumer<Boolean> resultCallback = success -> {
                    if (success) {
                        try {
                            sysUiProxy.startScreenPinning(taskView.getTask().key.id);
                        } catch (RemoteException e) {
                            Log.w(TAG, "Failed to start screen pinning: ", e);
                        }
                    } else {
                        taskView.notifyTaskLaunchFailed(TAG);
                    }
                };
                taskView.launchTask(true, resultCallback, mHandler);
                dismissTaskMenuView(activity);
            };
        }
    }

    public static class Install extends TaskSystemShortcut<SystemShortcut.Install> {
        public Install() {
            super(new SystemShortcut.Install());
        }

        @Override
        protected View.OnClickListener getOnClickListenerForTask(
                BaseDraggingActivity activity, Task task, ItemInfo itemInfo) {
            if (InstantAppResolver.newInstance(activity).isInstantApp(activity,
                        task.getTopComponent().getPackageName())) {
                return mSystemShortcut.createOnClickListener(activity, itemInfo);
            }
            return null;
        }
    }

    private static void dismissTaskMenuView(BaseDraggingActivity activity) {
        AbstractFloatingView.closeOpenViews(activity, true,
                AbstractFloatingView.TYPE_ALL & ~AbstractFloatingView.TYPE_REBIND_SAFE);
    }
}

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

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;
import android.view.View;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.util.InstantAppResolver;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.ActivityOptionsCompat;

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
    public View.OnClickListener getOnClickListener(Launcher launcher, ItemInfo itemInfo) {
        return null;
    }

    public View.OnClickListener getOnClickListener(final Launcher launcher, final TaskView view) {
        Task task = view.getTask();

        ShortcutInfo dummyInfo = new ShortcutInfo();
        dummyInfo.intent = new Intent();
        ComponentName component = task.getTopComponent();
        dummyInfo.intent.setComponent(component);
        dummyInfo.user = UserHandle.of(task.key.userId);
        dummyInfo.title = TaskUtils.getTitle(launcher, task);

        return getOnClickListenerForTask(launcher, task, dummyInfo);
    }

    protected View.OnClickListener getOnClickListenerForTask(final Launcher launcher,
            final Task task, final ItemInfo dummyInfo) {
        return mSystemShortcut.getOnClickListener(launcher, dummyInfo);
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
        public View.OnClickListener getOnClickListener(Launcher launcher, TaskView taskView) {
            if (launcher.getDeviceProfile().inMultiWindowMode()) {
                return null;
            }
            final Task task  = taskView.getTask();
            if (!task.isDockable) {
                return null;
            }
            return (v -> {
                final ActivityOptions options = ActivityOptionsCompat.makeSplitScreenOptions(true);
                final Consumer<Boolean> resultCallback = success -> {
                    if (success) {
                        launcher.<RecentsView>getOverviewPanel().removeView(taskView);
                    }
                };
                ActivityManagerWrapper.getInstance().startActivityFromRecentsAsync(
                        task.key, options, resultCallback, mHandler);

                // TODO improve transition; see:
                // DockedFirstAnimationFrameEvent
                // RecentsTransitionHelper#composeDockAnimationSpec
                // WindowManagerWrapper#overridePendingAppTransitionMultiThumbFuture
            });
        }
    }

    public static class Pin extends TaskSystemShortcut {

        private Handler mHandler;

        public Pin() {
            super(R.drawable.ic_pin, R.string.recent_task_option_pin);
            mHandler = new Handler(Looper.getMainLooper());
        }

        @Override
        public View.OnClickListener getOnClickListener(Launcher launcher, TaskView taskView) {
            ISystemUiProxy sysUiProxy = RecentsModel.getInstance(launcher).getSystemUiProxy();
            if (sysUiProxy == null) {
                return null;
            }
            if (!ActivityManagerWrapper.getInstance().isLockToAppEnabled()) {
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
                    }
                };
                taskView.launchTask(true, resultCallback, mHandler);
            };
        }
    }

    public static class Install extends TaskSystemShortcut<SystemShortcut.Install> {
        public Install() {
            super(new SystemShortcut.Install());
        }

        @Override
        protected View.OnClickListener getOnClickListenerForTask(Launcher launcher, Task task,
                ItemInfo itemInfo) {
            if (InstantAppResolver.newInstance(launcher).isInstantApp(launcher,
                        task.getTopComponent().getPackageName())) {
                return mSystemShortcut.createOnClickListener(launcher, itemInfo);
            }
            return null;
        }
    }
}

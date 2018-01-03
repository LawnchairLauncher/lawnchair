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

import android.content.ComponentName;
import android.content.Intent;
import android.os.UserHandle;
import android.view.View;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.util.InstantAppResolver;
import com.android.systemui.shared.recents.model.Task;

/**
 * Represents a system shortcut that can be shown for a recent task.
 */
public class TaskSystemShortcut<T extends SystemShortcut> extends SystemShortcut {

    protected T mSystemShortcut;

    protected TaskSystemShortcut(T systemShortcut) {
        super(systemShortcut.iconResId, systemShortcut.labelResId);
        mSystemShortcut = systemShortcut;
    }

    @Override
    public View.OnClickListener getOnClickListener(Launcher launcher, ItemInfo itemInfo) {
        return null;
    }

    public View.OnClickListener getOnClickListener(final Launcher launcher, final Task task) {
        ShortcutInfo dummyInfo = new ShortcutInfo();
        dummyInfo.intent = new Intent();
        ComponentName component = task.getTopComponent();
        dummyInfo.intent.setComponent(component);
        dummyInfo.user = UserHandle.getUserHandleForUid(task.key.userId);
        dummyInfo.title = TaskUtils.getTitle(launcher, task);

        return getOnClickListenerForTask(launcher, task, dummyInfo);
    }

    protected View.OnClickListener getOnClickListenerForTask(final Launcher launcher,
            final Task task, final ItemInfo dummyInfo) {
        return mSystemShortcut.getOnClickListener(launcher, dummyInfo);
    }


    public static class Widgets extends TaskSystemShortcut<SystemShortcut.Widgets> {
        public Widgets() {
            super(new SystemShortcut.Widgets());
        }
    }

    public static class AppInfo extends TaskSystemShortcut<SystemShortcut.AppInfo> {
        public AppInfo() {
            super(new SystemShortcut.AppInfo());
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

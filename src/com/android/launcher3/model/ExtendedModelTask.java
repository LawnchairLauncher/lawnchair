/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.model;

import android.os.UserHandle;

import com.android.launcher3.LauncherModel.BaseModelUpdateTask;
import com.android.launcher3.LauncherModel.CallbackTask;
import com.android.launcher3.LauncherModel.Callbacks;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.MultiHashMap;

import java.util.ArrayList;

/**
 * Extension of {@link BaseModelUpdateTask} with some utility methods
 */
public abstract class ExtendedModelTask extends BaseModelUpdateTask {

    public void bindUpdatedShortcuts(
            ArrayList<ShortcutInfo> updatedShortcuts, UserHandle user) {
        bindUpdatedShortcuts(updatedShortcuts, new ArrayList<ShortcutInfo>(), user);
    }

    public void bindUpdatedShortcuts(
            final ArrayList<ShortcutInfo> updatedShortcuts,
            final ArrayList<ShortcutInfo> removedShortcuts,
            final UserHandle user) {
        if (!updatedShortcuts.isEmpty() || !removedShortcuts.isEmpty()) {
            scheduleCallbackTask(new CallbackTask() {
                @Override
                public void execute(Callbacks callbacks) {
                    callbacks.bindShortcutsChanged(updatedShortcuts, removedShortcuts, user);
                }
            });
        }
    }

    public void bindDeepShortcuts(BgDataModel dataModel) {
        final MultiHashMap<ComponentKey, String> shortcutMapCopy = dataModel.deepShortcutMap.clone();
        scheduleCallbackTask(new CallbackTask() {
            @Override
            public void execute(Callbacks callbacks) {
                callbacks.bindDeepShortcutMap(shortcutMapCopy);
            }
        });
    }
}

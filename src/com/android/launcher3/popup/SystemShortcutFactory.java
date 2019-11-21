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
package com.android.launcher3.popup;

import static com.android.launcher3.util.MainThreadInitializedObject.forOverride;

import androidx.annotation.NonNull;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.ResourceBasedOverride;

import java.util.ArrayList;
import java.util.List;

public class SystemShortcutFactory implements ResourceBasedOverride {

    public static final MainThreadInitializedObject<SystemShortcutFactory> INSTANCE =
            forOverride(SystemShortcutFactory.class, R.string.system_shortcut_factory_class);

    /** Note that these are in order of priority. */
    private final SystemShortcut[] mAllShortcuts;

    @SuppressWarnings("unused")
    public SystemShortcutFactory() {
        this(new SystemShortcut.AppInfo(),
                new SystemShortcut.Widgets(),
                new SystemShortcut.Install(),
                new SystemShortcut.DismissPrediction());
    }

    protected SystemShortcutFactory(SystemShortcut... shortcuts) {
        mAllShortcuts = shortcuts;
    }

    public @NonNull List<SystemShortcut> getEnabledShortcuts(Launcher launcher, ItemInfo info) {
        List<SystemShortcut> systemShortcuts = new ArrayList<>();
        for (SystemShortcut systemShortcut : mAllShortcuts) {
            if (systemShortcut.getOnClickListener(launcher, info) != null) {
                systemShortcuts.add(systemShortcut);
            }
        }

        return systemShortcuts;
    }
}

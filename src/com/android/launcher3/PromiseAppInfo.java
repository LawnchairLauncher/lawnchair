/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.android.launcher3.compat.PackageInstallerCompat;
import com.android.launcher3.util.PackageManagerHelper;

public class PromiseAppInfo extends AppInfo {

    public int level = 0;

    public PromiseAppInfo(@NonNull PackageInstallerCompat.PackageInstallInfo installInfo) {
        componentName = installInfo.componentName;
        intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(componentName)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    @Override
    public ShortcutInfo makeShortcut() {
        ShortcutInfo shortcut = new ShortcutInfo(this);
        shortcut.setInstallProgress(level);
        // We need to update the component name when the apk is installed
        shortcut.status |= ShortcutInfo.FLAG_AUTOINSTALL_ICON;
        // Since the user is manually placing it on homescreen, it should not be auto-removed later
        shortcut.status |= ShortcutInfo.FLAG_RESTORE_STARTED;
        return shortcut;
    }

    public Intent getMarketIntent(Context context) {
        return new PackageManagerHelper(context).getMarketIntent(componentName.getPackageName());
    }
}

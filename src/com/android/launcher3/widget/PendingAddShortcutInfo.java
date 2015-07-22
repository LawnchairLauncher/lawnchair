/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.launcher3.widget;

import android.content.ComponentName;
import android.content.pm.ActivityInfo;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.PendingAddItemInfo;

/**
 * Meta data used for late binding of the short cuts.
 *
 * @see {@link PendingAddItemInfo}
 */
public class PendingAddShortcutInfo extends PendingAddItemInfo {

    ActivityInfo activityInfo;

    public PendingAddShortcutInfo(ActivityInfo activityInfo) {
        this.activityInfo = activityInfo;
        componentName = new ComponentName(activityInfo.packageName, activityInfo.name);
        itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
    }

    @Override
    public String toString() {
        return String.format("PendingAddShortcutInfo package=%s, name=%s",
                activityInfo.packageName, activityInfo.name);
    }
}

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

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_TRAY;

import android.content.Context;

import com.android.launcher3.PendingAddItemInfo;
import com.android.launcher3.pm.ShortcutConfigActivityInfo;

/**
 * Meta data used for late binding of the short cuts.
 *
 * @see {@link PendingAddItemInfo}
 */
public class PendingAddShortcutInfo extends PendingAddItemInfo {

    // TODO: Make it @NonNull
    protected ShortcutConfigActivityInfo mActivityInfo;

    public PendingAddShortcutInfo(ShortcutConfigActivityInfo activityInfo) {
        this.mActivityInfo = activityInfo;
        componentName = activityInfo.getComponent();
        user = activityInfo.getUser();
        itemType = activityInfo.getItemType();
        this.container = CONTAINER_WIDGETS_TRAY;
    }

    public PendingAddShortcutInfo(PendingAddShortcutInfo info) {
        super(info);
        mActivityInfo = info.mActivityInfo;
    }

    public PendingAddShortcutInfo() { }

    /**
     * Returns the info used for creating the shortcut
     */
    public ShortcutConfigActivityInfo getActivityInfo(Context context) {
        return mActivityInfo;
    }
}

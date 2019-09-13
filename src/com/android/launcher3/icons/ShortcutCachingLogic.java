/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.launcher3.icons;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.icons.cache.CachingLogic;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutKey;

/**
 * Caching logic for shortcuts.
 */
public class ShortcutCachingLogic implements CachingLogic<ShortcutInfo> {

    @Override
    public ComponentName getComponent(ShortcutInfo info) {
        return ShortcutKey.fromInfo(info).componentName;
    }

    @Override
    public UserHandle getUser(ShortcutInfo info) {
        return info.getUserHandle();
    }

    @Override
    public CharSequence getLabel(ShortcutInfo info) {
        return info.getShortLabel();
    }

    @Override
    public void loadIcon(Context context, ShortcutInfo info, BitmapInfo target) {
        LauncherIcons li = LauncherIcons.obtain(context);
        Drawable unbadgedDrawable = DeepShortcutManager.getInstance(context)
                .getShortcutIconDrawable(info, LauncherAppState.getIDP(context).fillResIconDpi);
        if (unbadgedDrawable != null) {
            target.icon = li.createScaledBitmapWithoutShadow(unbadgedDrawable, 0);
        }
        li.recycle();
    }

    @Override
    public long getLastUpdatedTime(ShortcutInfo shortcutInfo, PackageInfo info) {
        if (shortcutInfo == null) return info.lastUpdateTime;
        return Math.max(shortcutInfo.getLastChangedTimestamp(), info.lastUpdateTime);
    }
}

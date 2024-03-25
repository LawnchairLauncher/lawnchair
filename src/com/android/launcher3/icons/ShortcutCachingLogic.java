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

import static com.android.launcher3.BuildConfig.WIDGETS_ENABLED;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInfo;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.icons.BaseIconFactory.IconOptions;
import com.android.launcher3.icons.cache.CachingLogic;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.Themes;

/**
 * Caching logic for shortcuts.
 */
public class ShortcutCachingLogic implements CachingLogic<ShortcutInfo> {

    private static final String TAG = "ShortcutCachingLogic";

    @Override
    @NonNull
    public ComponentName getComponent(@NonNull ShortcutInfo info) {
        return ShortcutKey.fromInfo(info).componentName;
    }

    @NonNull
    @Override
    public UserHandle getUser(@NonNull ShortcutInfo info) {
        return info.getUserHandle();
    }

    @NonNull
    @Override
    public CharSequence getLabel(@NonNull ShortcutInfo info) {
        return info.getShortLabel();
    }

    @Override
    @NonNull
    public CharSequence getDescription(@NonNull ShortcutInfo object,
            @NonNull CharSequence fallback) {
        CharSequence label = object.getLongLabel();
        return TextUtils.isEmpty(label) ? fallback : label;
    }

    @NonNull
    @Override
    public BitmapInfo loadIcon(@NonNull Context context, @NonNull ShortcutInfo info) {
        try (LauncherIcons li = LauncherIcons.obtain(context)) {
            Drawable unbadgedDrawable = ShortcutCachingLogic.getIcon(
                    context, info, LauncherAppState.getIDP(context).fillResIconDpi);
            if (unbadgedDrawable == null) return BitmapInfo.LOW_RES_INFO;
            return li.createBadgedIconBitmap(unbadgedDrawable,
                    new IconOptions().setExtractedColor(Themes.getColorAccent(context)));
        }
    }

    @Override
    public long getLastUpdatedTime(@Nullable ShortcutInfo shortcutInfo,
            @NonNull PackageInfo info) {
        if (shortcutInfo == null) {
            return info.lastUpdateTime;
        }
        return Math.max(shortcutInfo.getLastChangedTimestamp(), info.lastUpdateTime);
    }

    @Override
    public boolean addToMemCache() {
        return false;
    }

    /**
     * Similar to {@link LauncherApps#getShortcutIconDrawable(ShortcutInfo, int)} with additional
     * Launcher specific checks
     */
    public static Drawable getIcon(Context context, ShortcutInfo shortcutInfo, int density) {
        if (!WIDGETS_ENABLED) {
            return null;
        }
        try {
            return context.getSystemService(LauncherApps.class)
                    .getShortcutIconDrawable(shortcutInfo, density);
        } catch (SecurityException | IllegalStateException | NullPointerException e) {
            Log.e(TAG, "Failed to get shortcut icon", e);
            return null;
        }
    }
}

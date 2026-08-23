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
package com.android.launcher3.model;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.BadParcelableException;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.Utilities;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.IntSet;

import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Predicate;

/**
 * Utils class for {@link com.android.launcher3.LauncherModel}.
 */
public class ModelUtils {

    private static final String TAG = "ModelUtils";

    /**
     * Returns a filter for items on hotseat or current screens
     */
    public static Predicate<ItemInfo> currentScreenContentFilter(IntSet currentScreenIds) {
        return item -> item.container == CONTAINER_HOTSEAT
                || (item.container == CONTAINER_DESKTOP
                        && currentScreenIds.contains(item.screenId));
    }

    /**
     * Returns a filter for widget items
     */
    public static final Predicate<ItemInfo> WIDGET_FILTER = item ->
            item.itemType == ITEM_TYPE_APPWIDGET || item.itemType == ITEM_TYPE_CUSTOM_APPWIDGET;

    /**
     * Creates a workspace item from the result of an ACTION_CREATE_SHORTCUT activity.
     * <p> 
     * LC-Note: We still use this to support legacy shortcut for non-root.
     */
    @SuppressWarnings("deprecation")
    public static WorkspaceItemInfo fromLegacyShortcutIntent(Context context, Intent data) {
        if (data == null) {
            return null;
        }
        Object shortcutIntent;
        Object iconResource;
        Object icon;
        String label;
        try {
            shortcutIntent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
            iconResource = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE);
            icon = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);
            label = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        } catch (BadParcelableException | ClassCastException e) {
            Log.e(TAG, "Unable to read legacy shortcut intent", e);
            return null;
        }

        if ((shortcutIntent != null && !(shortcutIntent instanceof Intent))
                || (iconResource != null
                        && !(iconResource instanceof Intent.ShortcutIconResource))
                || (icon != null && !(icon instanceof Bitmap))) {
            Log.e(TAG, "Invalid install shortcut intent");
            return null;
        }

        Intent launchIntent = (Intent) shortcutIntent;
        if (launchIntent == null || label == null) {
            Log.e(TAG, "Invalid install shortcut intent");
            return null;
        }

        BitmapInfo iconInfo = null;
        try (LauncherIcons li = LauncherIcons.obtain(context)) {
            Bitmap bitmap = (Bitmap) icon;
            if (bitmap != null) {
                iconInfo = li.createIconBitmap(bitmap);
            } else {
                Intent.ShortcutIconResource resource =
                        (Intent.ShortcutIconResource) iconResource;
                if (resource != null) {
                    iconInfo = li.createIconBitmap(resource);
                }
            }
        }

        if (iconInfo == null) {
            Log.e(TAG, "Invalid icon by the app");
            return null;
        }

        String launchPackage = launchIntent.getComponent() == null
                ? launchIntent.getPackage() : launchIntent.getComponent().getPackageName();
        if (!TextUtils.isEmpty(launchPackage)) {
            IconCache iconCache = LauncherAppState.getInstance(context).getIconCache();
            BitmapInfo baseIconInfo = iconInfo;
            try {
                iconInfo = MODEL_EXECUTOR.submit(() -> baseIconInfo.withBadgeInfo(
                        iconCache.getShortcutInfoBadge(launchPackage, Process.myUserHandle())))
                        .get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Failed to load legacy shortcut badge", e);
            }
        }

        WorkspaceItemInfo info = new WorkspaceItemInfo();
        info.itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
        info.user = Process.myUserHandle();
        info.bitmap = iconInfo;
        info.contentDescription = info.title = Utilities.trim(label);
        info.intent = launchIntent;
        return info;
    }
}

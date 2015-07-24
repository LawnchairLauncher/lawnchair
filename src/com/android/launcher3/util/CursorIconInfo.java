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

package com.android.launcher3.util;

import android.content.Context;
import android.content.Intent.ShortcutIconResource;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.text.TextUtils;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;

/**
 * Utility class to load icon from a cursor.
 */
public class CursorIconInfo {
    public final int iconTypeIndex;
    public final int iconPackageIndex;
    public final int iconResourceIndex;
    public final int iconIndex;

    public CursorIconInfo(Cursor c) {
        iconTypeIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON_TYPE);
        iconIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON);
        iconPackageIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON_PACKAGE);
        iconResourceIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON_RESOURCE);
    }

    public Bitmap loadIcon(Cursor c, ShortcutInfo info, Context context) {
        Bitmap icon = null;
        int iconType = c.getInt(iconTypeIndex);
        switch (iconType) {
        case LauncherSettings.Favorites.ICON_TYPE_RESOURCE:
            String packageName = c.getString(iconPackageIndex);
            String resourceName = c.getString(iconResourceIndex);
            if (!TextUtils.isEmpty(packageName) || !TextUtils.isEmpty(resourceName)) {
                info.iconResource = new ShortcutIconResource();
                info.iconResource.packageName = packageName;
                info.iconResource.resourceName = resourceName;
                icon = Utilities.createIconBitmap(packageName, resourceName, context);
            }
            if (icon == null) {
                // Failed to load from resource, try loading from DB.
                icon = Utilities.createIconBitmap(c, iconIndex, context);
            }
            break;
        case LauncherSettings.Favorites.ICON_TYPE_BITMAP:
            icon = Utilities.createIconBitmap(c, iconIndex, context);
            info.customIcon = icon != null;
            break;
        }
        return icon;
    }
}

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

package ch.deletescape.lawnchair.util;

import android.content.Context;
import android.content.Intent.ShortcutIconResource;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.text.TextUtils;

import ch.deletescape.lawnchair.LauncherSettings;
import ch.deletescape.lawnchair.ShortcutInfo;
import ch.deletescape.lawnchair.Utilities;

/**
 * Utility class to load icon from a cursor.
 */
public class CursorIconInfo {
    public final int iconPackageIndex;
    public final int iconResourceIndex;
    public final int iconIndex;
    public final int customIconIndex;

    public final int titleIndex;

    private final Context mContext;

    public CursorIconInfo(Context context, Cursor c) {
        mContext = context;

        iconIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON);
        customIconIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CUSTOM_ICON);
        iconPackageIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON_PACKAGE);
        iconResourceIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON_RESOURCE);

        titleIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.TITLE);
    }

    /**
     * Loads the icon from the cursor and updates the {@param info} if the icon is an app resource.
     */
    public Bitmap loadIcon(Cursor c, ShortcutInfo info) {
        Bitmap icon = null;
        String packageName = c.getString(iconPackageIndex);
        String resourceName = c.getString(iconResourceIndex);
        if (!TextUtils.isEmpty(packageName) || !TextUtils.isEmpty(resourceName)) {
            info.iconResource = new ShortcutIconResource();
            info.iconResource.packageName = packageName;
            info.iconResource.resourceName = resourceName;
            icon = Utilities.createIconBitmap(packageName, resourceName, mContext);
        }
        if (icon == null) {
            // Failed to load from resource, try loading from DB.
            icon = loadIcon(c);
        }
        return icon;
    }

    /**
     * Loads the fixed bitmap from the icon if available.
     */
    public Bitmap loadIcon(Cursor c) {
        return Utilities.createIconBitmap(c, iconIndex, mContext);
    }

    public Bitmap loadCustomIcon(Cursor c) {
        return Utilities.createIconBitmap(c, customIconIndex, mContext);
    }

    /**
     * Returns the title or empty string
     */
    public String getTitle(Cursor c) {
        String title = c.getString(titleIndex);
        return TextUtils.isEmpty(title) ? "" : Utilities.trim(c.getString(titleIndex));
    }
}

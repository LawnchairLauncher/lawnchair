/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.model.data;

import static android.graphics.BitmapFactory.decodeByteArray;

import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherSettings;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.LauncherIcons;

/**
 * Class representing one request for an icon to be queried in a sql database.
 *
 * @param <T> ItemInfoWithIcon subclass whose title and icon can be loaded and filled by an sql
 *           query.
 */
public class IconRequestInfo<T extends ItemInfoWithIcon> {

    private static final String TAG = "IconRequestInfo";

    @NonNull public final T itemInfo;
    @Nullable public final LauncherActivityInfo launcherActivityInfo;
    @Nullable public final String packageName;
    @Nullable public final String resourceName;
    @Nullable public final byte[] iconBlob;
    public final boolean useLowResIcon;

    public IconRequestInfo(
            @NonNull T itemInfo,
            @Nullable LauncherActivityInfo launcherActivityInfo,
            boolean useLowResIcon) {
        this(
                itemInfo,
                launcherActivityInfo,
                /* packageName= */ null,
                /* resourceName= */ null,
                /* iconBlob= */ null,
                useLowResIcon);
    }

    public IconRequestInfo(
            @NonNull T itemInfo,
            @Nullable LauncherActivityInfo launcherActivityInfo,
            @Nullable String packageName,
            @Nullable String resourceName,
            @Nullable byte[] iconBlob,
            boolean useLowResIcon) {
        this.itemInfo = itemInfo;
        this.launcherActivityInfo = launcherActivityInfo;
        this.packageName = packageName;
        this.resourceName = resourceName;
        this.iconBlob = iconBlob;
        this.useLowResIcon = useLowResIcon;
    }

    /** Loads  */
    public boolean loadWorkspaceIcon(Context context) {
        if (!(itemInfo instanceof WorkspaceItemInfo)) {
            throw new IllegalStateException(
                    "loadWorkspaceIcon should only be use for a WorkspaceItemInfos: " + itemInfo);
        }

        try (LauncherIcons li = LauncherIcons.obtain(context)) {
            WorkspaceItemInfo info = (WorkspaceItemInfo) itemInfo;
            if (itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT) {
                if (!TextUtils.isEmpty(packageName) || !TextUtils.isEmpty(resourceName)) {
                    info.iconResource = new Intent.ShortcutIconResource();
                    info.iconResource.packageName = packageName;
                    info.iconResource.resourceName = resourceName;
                    BitmapInfo iconInfo = li.createIconBitmap(info.iconResource);
                    if (iconInfo != null) {
                        info.bitmap = iconInfo;
                        return true;
                    }
                }
            }

            // Failed to load from resource, try loading from DB.
            try {
                if (iconBlob == null) {
                    return false;
                }
                info.bitmap = li.createIconBitmap(decodeByteArray(
                        iconBlob, 0, iconBlob.length));
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to decode byte array for info " + info, e);
                return false;
            }
        }
    }
}

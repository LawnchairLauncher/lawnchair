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

package com.android.launcher3.discovery;

import android.content.ComponentName;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.android.launcher3.AppInfo;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.ShortcutInfo;

public class AppDiscoveryAppInfo extends AppInfo {

    public final boolean showAsDiscoveryItem;
    public final boolean isInstantApp;
    public final boolean isRecent;
    public final float rating;
    public final long reviewCount;
    public final @NonNull String publisher;
    public final @NonNull Intent installIntent;
    public final @NonNull Intent launchIntent;
    public final @Nullable String priceFormatted;

    public AppDiscoveryAppInfo(AppDiscoveryItem item) {
        this.intent = item.isInstantApp ? item.launchIntent : item.installIntent;
        this.title = item.title;
        this.iconBitmap = item.bitmap;
        this.isDisabled = ShortcutInfo.DEFAULT;
        this.usingLowResIcon = false;
        this.isInstantApp = item.isInstantApp;
        this.isRecent = item.isRecent;
        this.rating = item.starRating;
        this.showAsDiscoveryItem = true;
        this.publisher = item.publisher != null ? item.publisher : "";
        this.priceFormatted = item.price;
        this.componentName = new ComponentName(item.packageName, "");
        this.installIntent = item.installIntent;
        this.launchIntent = item.launchIntent;
        this.reviewCount = item.reviewCount;
        this.itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
    }

    @Override
    public ShortcutInfo makeShortcut() {
        if (!isDragAndDropSupported()) {
            throw new RuntimeException("DnD is currently not supported for discovered store apps");
        }
        return super.makeShortcut();
    }

    public boolean isDragAndDropSupported() {
        return isInstantApp;
    }

}

/*
 * Copyright (C) 2011 The Android Open Source Project
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

package ch.deletescape.lawnchair;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

import ch.deletescape.lawnchair.compat.LauncherAppsCompat;

public class InfoDropTarget extends UninstallDropTarget {

    private static final String TAG = "InfoDropTarget";

    public InfoDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InfoDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // Get the hover color
        mHoverColor = Utilities.getColorAccent(getContext());

        setDrawable(R.drawable.ic_info_no_shadow);
    }

    @Override
    void completeDrop(DragObject d) {
        DropTargetResultCallback callback = d.dragSource instanceof DropTargetResultCallback
                ? (DropTargetResultCallback) d.dragSource : null;
        startDetailsActivityForInfo(d.dragInfo, mLauncher, callback);
    }

    public static boolean startDetailsActivityForInfo(ItemInfo itemInfo, Launcher launcher, DropTargetResultCallback dropTargetResultCallback) {
        return startDetailsActivityForInfo(itemInfo, launcher, dropTargetResultCallback, null, null);
    }

    public static boolean startDetailsActivityForInfo(ItemInfo itemInfo, Launcher launcher, DropTargetResultCallback dropTargetResultCallback, Rect rect, Bundle bundle) {
        ComponentName componentName;
        boolean z;
        if (itemInfo instanceof AppInfo) {
            componentName = ((AppInfo) itemInfo).componentName;
        } else if (itemInfo instanceof ShortcutInfo) {
            componentName = ((ShortcutInfo) itemInfo).intent.getComponent();
        } else if (itemInfo instanceof PendingAddItemInfo) {
            componentName = ((PendingAddItemInfo) itemInfo).componentName;
        } else if (itemInfo instanceof LauncherAppWidgetInfo) {
            componentName = ((LauncherAppWidgetInfo) itemInfo).providerName;
        } else {
            componentName = null;
        }
        if (componentName != null) {
            try {
                LauncherAppsCompat.getInstance(launcher).showAppDetailsForProfile(componentName, itemInfo.user/*, rect, bundle*/);
                z = true;
            } catch (Throwable e) {
                Toast.makeText(launcher, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
                Log.e("InfoDropTarget", "Unable to launch settings", e);
                z = false;
            }
        } else {
            z = false;
        }
        if (dropTargetResultCallback != null) {
            UninstallDropTarget.sendUninstallResult(launcher, z, componentName, itemInfo.user, dropTargetResultCallback);
        }
        return z;
    }


    @Override
    protected boolean supportsDrop(DragSource source, ItemInfo info) {
        return source.supportsAppInfoDropTarget() && supportsDrop(info);
    }

    public static boolean supportsDrop(ItemInfo info) {
        // Only show the App Info drop target if developer settings are enabled.
        ContentResolver resolver = LauncherAppState.getInstance().getContext().getContentResolver();
        boolean developmentSettingsEnabled = Settings.Global.getInt(resolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;
        return developmentSettingsEnabled
                && (info instanceof AppInfo || info instanceof ShortcutInfo || info instanceof PendingAddItemInfo
                || info instanceof LauncherAppWidgetInfo)
                && info.itemType != LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
    }
}

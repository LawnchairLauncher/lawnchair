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

package com.android.launcher3;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

import com.android.launcher3.compat.LauncherAppsCompat;

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

        setDrawable(R.drawable.ic_info_launcher);
    }

    @Override
    void completeDrop(DragObject d) {
        DropTargetResultCallback callback = d.dragSource instanceof DropTargetResultCallback
                ? (DropTargetResultCallback) d.dragSource : null;
        startDetailsActivityForInfo(d.dragInfo, mLauncher, callback);
    }

    /**
     * @return Whether the activity was started.
     */
    public static boolean startDetailsActivityForInfo(
            ItemInfo info, Launcher launcher, DropTargetResultCallback callback) {
        boolean result = false;
        ComponentName componentName = null;
        if (info instanceof AppInfo) {
            componentName = ((AppInfo) info).componentName;
        } else if (info instanceof ShortcutInfo) {
            componentName = ((ShortcutInfo) info).intent.getComponent();
        } else if (info instanceof PendingAddItemInfo) {
            componentName = ((PendingAddItemInfo) info).componentName;
        } else if (info instanceof LauncherAppWidgetInfo) {
            componentName = ((LauncherAppWidgetInfo) info).providerName;
        }
        if (componentName != null) {
            try {
                LauncherAppsCompat.getInstance(launcher)
                        .showAppDetailsForProfile(componentName, info.user);
                result = true;
            } catch (SecurityException | ActivityNotFoundException e) {
                Toast.makeText(launcher, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Unable to launch settings", e);
            }
        }

        if (callback != null) {
            sendUninstallResult(launcher, result, componentName, info.user, callback);
        }
        return result;
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
                && (info instanceof AppInfo || info instanceof ShortcutInfo
                || info instanceof PendingAddItemInfo || info instanceof LauncherAppWidgetInfo)
                && info.itemType != LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
    }
}

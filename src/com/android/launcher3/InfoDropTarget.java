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
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

import com.android.launcher3.compat.LauncherAppsCompat;

import ch.deletescape.lawnchair.colors.ColorEngine;

public class InfoDropTarget extends UninstallDropTarget implements ColorEngine.OnAccentChangeListener {

    private static final String TAG = "InfoDropTarget";

    public InfoDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InfoDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void setupUi() {
        setDrawable(R.drawable.ic_info_shadow);
    }

    @Override
    public void completeDrop(DragObject d) {
        DropTargetResultCallback callback = d.dragSource instanceof DropTargetResultCallback
                ? (DropTargetResultCallback) d.dragSource : null;
        startDetailsActivityForInfo(d.dragInfo, mLauncher, callback);
    }

    /**
     * @return Whether the activity was started.
     */
    public static boolean startDetailsActivityForInfo(
            ItemInfo info, Launcher launcher, DropTargetResultCallback callback) {
        return startDetailsActivityForInfo(info, launcher, callback, null, null);
    }

    public static boolean startDetailsActivityForInfo(ItemInfo info, Launcher launcher,
            DropTargetResultCallback callback, Rect sourceBounds, Bundle opts) {
        if (info instanceof PromiseAppInfo) {
            PromiseAppInfo promiseAppInfo = (PromiseAppInfo) info;
            launcher.startActivity(promiseAppInfo.getMarketIntent());
            return true;
        }
        boolean result = false;
        ComponentName componentName = null;
        if (info instanceof AppInfo) {
            componentName = ((AppInfo) info).componentName;
        } else if (info instanceof ShortcutInfo) {
            componentName = info.getTargetComponent();
        } else if (info instanceof PendingAddItemInfo) {
            componentName = ((PendingAddItemInfo) info).componentName;
        } else if (info instanceof LauncherAppWidgetInfo) {
            componentName = ((LauncherAppWidgetInfo) info).providerName;
        }
        if (componentName != null) {
            try {
                LauncherAppsCompat.getInstance(launcher)
                        .showAppDetailsForProfile(componentName, info.user, sourceBounds, opts);
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
        return source.supportsAppInfoDropTarget() && supportsDrop(getContext(), info);
    }

    public static boolean supportsDrop(Context context, ItemInfo info) {
        // Only show the App Info drop target if developer settings are enabled.
        boolean developmentSettingsEnabled = Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1;
        if (!developmentSettingsEnabled) {
            return false;
        }
        return info.itemType != LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT &&
                (info instanceof AppInfo ||
                (info instanceof ShortcutInfo && !((ShortcutInfo) info).isPromise()) ||
                (info instanceof LauncherAppWidgetInfo &&
                        ((LauncherAppWidgetInfo) info).restoreStatus == 0) ||
                info instanceof PendingAddItemInfo);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Register accent change listener
        ColorEngine.Companion.getInstance(getContext()).addAccentChangeListener(this);
    }

    @Override
    public void onAccentChange(int color) {
        mHoverColor = color;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // Remove accent change listener
        ColorEngine.Companion.getInstance(getContext()).removeAccentChangeListener(this);
    }
}

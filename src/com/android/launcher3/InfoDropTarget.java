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

import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.util.Themes;

public class InfoDropTarget extends UninstallDropTarget {

    private static final String TAG = "InfoDropTarget";

    public InfoDropTarget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InfoDropTarget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void setupUi() {
        // Get the hover color
        mHoverColor = Themes.getColorAccent(getContext());
        setDrawable(R.drawable.ic_info_shadow);
    }

    @Override
    protected ComponentName performDropAction(ItemInfo item) {
        return performDropAction(mLauncher, item, null, null);
    }

    /**
     * @return Whether the activity was started.
     */
    public static boolean startDetailsActivityForInfo(
            ItemInfo info, Launcher launcher, Rect sourceBounds, Bundle opts) {
        return performDropAction(launcher, info, sourceBounds, opts) != null;
    }

    /**
     * Performs the drop action and returns the target component for the dragObject or null if
     * the action was not performed.
     */
    private static ComponentName performDropAction(Context context, ItemInfo info,
            Rect sourceBounds, Bundle opts) {
        if (info instanceof PromiseAppInfo) {
            PromiseAppInfo promiseAppInfo = (PromiseAppInfo) info;
            context.startActivity(promiseAppInfo.getMarketIntent(context));
            return null;
        }
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
                LauncherAppsCompat.getInstance(context)
                        .showAppDetailsForProfile(componentName, info.user, sourceBounds, opts);
                return componentName;
            } catch (SecurityException | ActivityNotFoundException e) {
                Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Unable to launch settings", e);
            }
        }
        return null;
    }

    @Override
    public int getAccessibilityAction() {
        return LauncherAccessibilityDelegate.INFO;
    }

    @Override
    protected boolean supportsDrop(ItemInfo info) {
        // Only show the App Info drop target if developer settings are enabled.
        boolean developmentSettingsEnabled = Settings.Global.getInt(
                getContext().getContentResolver(),
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
}

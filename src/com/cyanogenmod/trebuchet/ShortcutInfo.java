/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.cyanogenmod.trebuchet;

import java.util.ArrayList;
import java.util.Arrays;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Log;

/**
 * Represents a launchable icon on the workspaces and in folders.
 */
class ShortcutInfo extends ItemInfo {

    /**
     * The intent used to start the application.
     */
    Intent intent;

    /**
     * Indicates whether the title comes from an application's resource (if false)
     * or from a custom title (if true.)
     */
    boolean customTitle;

    /**
     * Indicates whether the icon comes from an application's resource (if false)
     * or from a custom Bitmap (if true.)
     */
    boolean customIcon;

    /**
     * Indicates whether we're using the default fallback icon instead of something from the
     * app.
     */
    boolean usingFallbackIcon;

    /**
     * If isShortcut=true and customIcon=false, this contains a reference to the
     * shortcut icon as an application's resource.
     */
    Intent.ShortcutIconResource iconResource;

    /**
     * The application icon.
     */
    private Bitmap mIcon;

    /**
     * Title change listener
     */
    private ShortcutListener mListener;

    ShortcutInfo() {
        itemType = LauncherSettings.BaseLauncherColumns.ITEM_TYPE_SHORTCUT;
    }
    
    public ShortcutInfo(ShortcutInfo info) {
        super(info);
        title = info.title.toString();
        intent = new Intent(info.intent);
        if (info.iconResource != null) {
            iconResource = new Intent.ShortcutIconResource();
            iconResource.packageName = info.iconResource.packageName;
            iconResource.resourceName = info.iconResource.resourceName;
        }
        mIcon = info.mIcon; // TODO: should make a copy here.  maybe we don't need this ctor at all
        customIcon = info.customIcon;
    }

    /** TODO: Remove this.  It's only called by ApplicationInfo.makeShortcut. */
    public ShortcutInfo(ApplicationInfo info) {
        super(info);
        title = info.title.toString();
        intent = new Intent(info.intent);
        customIcon = false;
    }

    public void setIcon(Bitmap b) {
        mIcon = b;
    }

    public Bitmap getIcon(IconCache iconCache) {
        if (mIcon == null) {
            updateIcon(iconCache);
        }
        return mIcon;
    }

    /** Returns the package name that the shortcut's intent will resolve to, or an empty string if
     *  none exists. */
    String getPackageName() {
        return super.getPackageName(intent);
    }

    public void updateIcon(IconCache iconCache) {
        mIcon = iconCache.getIcon(intent);
        usingFallbackIcon = iconCache.isDefaultIcon(mIcon);
    }

    /**
     * Creates the application intent based on a component name and various launch flags.
     * Sets {@link #itemType} to {@link LauncherSettings.BaseLauncherColumns#ITEM_TYPE_APPLICATION}.
     *
     * @param className the class name of the component representing the intent
     * @param launchFlags the launch flags
     */
    final void setActivity(ComponentName className, int launchFlags) {
        intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(className);
        intent.setFlags(launchFlags);
        itemType = LauncherSettings.BaseLauncherColumns.ITEM_TYPE_APPLICATION;
    }

    public void setTitle(CharSequence title) {
        this.title = title;
        this.customTitle = true;
        if (mListener != null) {
            mListener.onTitleChanged(title);
        }
    }

    void setListener(ShortcutListener listener) {
        mListener = listener;
    }

    @Override
    void onAddToDatabase(ContentValues values) {
        super.onAddToDatabase(values);

        String titleStr = title != null && (customTitle || itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT) ?
                title.toString() : null;
        values.put(LauncherSettings.BaseLauncherColumns.TITLE, titleStr);

        String uri = intent != null ? intent.toUri(0) : null;
        values.put(LauncherSettings.BaseLauncherColumns.INTENT, uri);

        if (customIcon) {
            values.put(LauncherSettings.BaseLauncherColumns.ICON_TYPE,
                    LauncherSettings.BaseLauncherColumns.ICON_TYPE_BITMAP);
            writeBitmap(values, mIcon);
        } else {
            if (!usingFallbackIcon) {
                writeBitmap(values, mIcon);
            }
            values.put(LauncherSettings.BaseLauncherColumns.ICON_TYPE,
                    LauncherSettings.BaseLauncherColumns.ICON_TYPE_RESOURCE);
            if (iconResource != null) {
                values.put(LauncherSettings.BaseLauncherColumns.ICON_PACKAGE,
                        iconResource.packageName);
                values.put(LauncherSettings.BaseLauncherColumns.ICON_RESOURCE,
                        iconResource.resourceName);
            }
        }
    }

    @Override
    public String toString() {
        return "ShortcutInfo(title=" + (title != null ? title.toString() : "unknown ") + "intent=" + intent + "id=" + this.id
                + " type=" + this.itemType + " container=" + this.container + " screen=" + screen
                + " cellX=" + cellX + " cellY=" + cellY + " spanX=" + spanX + " spanY=" + spanY
                + " dropPos=" + Arrays.toString(dropPos) + ")";
    }

    public static void dumpShortcutInfoList(String tag, String label,
            ArrayList<ShortcutInfo> list) {
        Log.d(tag, label + " size=" + list.size());
        for (ShortcutInfo info: list) {
            Log.d(tag, "   title=\"" + info.title + " icon=" + info.mIcon
                    + " customIcon=" + info.customIcon);
        }
    }

    interface ShortcutListener {
        public void onTitleChanged(CharSequence title);
    }
}


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

package com.android.launcher3;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Log;

import com.android.launcher3.compat.UserHandleCompat;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Represents a launchable icon on the workspaces and in folders.
 */
public class ShortcutInfo extends ItemInfo {

    /** {@link #mState} meaning this package is not installed, and there is no other information. */
    public static final int PACKAGE_STATE_UNKNOWN = -2;

    /** {@link #mState} meaning this package is not installed, because installation failed. */
    public static final int PACKAGE_STATE_ERROR = -1;

    /** {@link #mState} meaning this package is installed.  This is the typical case. */
    public static final int PACKAGE_STATE_DEFAULT = 0;

    /** {@link #mState} meaning some external entity has promised to install this package. */
    public static final int PACKAGE_STATE_ENQUEUED = 1;

    /** {@link #mState} meaning but some external entity is downloading this package. */
    public static final int PACKAGE_STATE_DOWNLOADING = 2;

    /** {@link #mState} meaning some external entity is installing this package. */
    public static final int PACKAGE_STATE_INSTALLING = 3;

    /**
     * The intent used to start the application.
     */
    Intent intent;

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
     * Could be disabled, if the the app is installed but unavailable (eg. in safe mode or when
     * sd-card is not available).
     */
    boolean isDisabled = false;

    /**
     * The installation state of the package that this shortcut represents.
     */
    protected int mState;

    long firstInstallTime;
    int flags = 0;

    /**
     * If this shortcut is a placeholder, then intent will be a market intent for the package, and
     * this will hold the original intent from the database.  Otherwise, null.
     */
    Intent restoredIntent;

    /**
     * This is set once to indicate that it was a promise info at some point of its life.
     */
    boolean wasPromise = false;

    ShortcutInfo() {
        itemType = LauncherSettings.BaseLauncherColumns.ITEM_TYPE_SHORTCUT;
    }

    public Intent getIntent() {
        return intent;
    }

    protected Intent getRestoredIntent() {
        return restoredIntent;
    }

    /**
     * Overwrite placeholder data with restored data, or do nothing if this is not a placeholder.
     */
    public void restore() {
        if (restoredIntent != null) {
            intent = restoredIntent;
            restoredIntent = null;
            mState = PACKAGE_STATE_DEFAULT;
        }
    }

    ShortcutInfo(Intent intent, CharSequence title, CharSequence contentDescription,
            Bitmap icon, UserHandleCompat user) {
        this();
        this.intent = intent;
        this.title = title;
        this.contentDescription = contentDescription;
        mIcon = icon;
        this.user = user;
    }

    public ShortcutInfo(Context context, ShortcutInfo info) {
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
        flags = info.flags;
        firstInstallTime = info.firstInstallTime;
        user = info.user;
    }

    /** TODO: Remove this.  It's only called by ApplicationInfo.makeShortcut. */
    public ShortcutInfo(AppInfo info) {
        super(info);
        title = info.title.toString();
        intent = new Intent(info.intent);
        customIcon = false;
        flags = info.flags;
        firstInstallTime = info.firstInstallTime;
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

    public void updateIcon(IconCache iconCache) {
        mIcon = iconCache.getIcon(intent, user);
        usingFallbackIcon = iconCache.isDefaultIcon(mIcon, user);
    }

    @Override
    void onAddToDatabase(Context context, ContentValues values) {
        super.onAddToDatabase(context, values);

        String titleStr = title != null ? title.toString() : null;
        values.put(LauncherSettings.BaseLauncherColumns.TITLE, titleStr);

        String uri = restoredIntent != null ? restoredIntent.toUri(0)
                : (intent != null ? intent.toUri(0) : null);
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
        return "ShortcutInfo(title=" + title + "intent=" + intent + "id=" + this.id
                + " type=" + this.itemType + " container=" + this.container + " screen=" + screenId
                + " cellX=" + cellX + " cellY=" + cellY + " spanX=" + spanX + " spanY=" + spanY
                + " dropPos=" + Arrays.toString(dropPos) + " user=" + user + ")";
    }

    public static void dumpShortcutInfoList(String tag, String label,
            ArrayList<ShortcutInfo> list) {
        Log.d(tag, label + " size=" + list.size());
        for (ShortcutInfo info: list) {
            Log.d(tag, "   title=\"" + info.title + " icon=" + info.mIcon
                    + " customIcon=" + info.customIcon);
        }
    }

    public boolean isPromise() {
        return restoredIntent != null;
    }

    public boolean isPromiseFor(String pkgName) {
        return restoredIntent != null
                && pkgName != null
                && pkgName.equals(restoredIntent.getComponent().getPackageName());
    }

    public boolean isAbandoned() {
        return isPromise()
                && (mState == ShortcutInfo.PACKAGE_STATE_ERROR
                        || mState == ShortcutInfo.PACKAGE_STATE_UNKNOWN);
    }

    public int getState() {
        return mState;
    }

    public void setState(int state) {
        mState = state;
    }
}


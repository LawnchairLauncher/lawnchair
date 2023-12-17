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

package com.android.launcher3.model.data;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.pm.PackageInstallInfo;
import com.android.launcher3.util.PackageManagerHelper;

import app.lawnchair.preferences.PreferenceManager;

/**
 * Represents an ItemInfo which also holds an icon.
 */
public abstract class ItemInfoWithIcon extends ItemInfo {

    public static final String TAG = "ItemInfoDebug";

    /**
     * The bitmap for the application icon
     */
    public BitmapInfo bitmap = BitmapInfo.LOW_RES_INFO;

    /**
     * Indicates that the icon is disabled due to safe mode restrictions.
     */
    public static final int FLAG_DISABLED_SAFEMODE = 1 << 0;

    /**
     * Indicates that the icon is disabled as the app is not available.
     */
    public static final int FLAG_DISABLED_NOT_AVAILABLE = 1 << 1;

    /**
     * Indicates that the icon is disabled as the app is suspended
     */
    public static final int FLAG_DISABLED_SUSPENDED = 1 << 2;

    /**
     * Indicates that the icon is disabled as the user is in quiet mode.
     */
    public static final int FLAG_DISABLED_QUIET_USER = 1 << 3;

    /**
     * Indicates that the icon is disabled as the publisher has disabled the actual shortcut.
     */
    public static final int FLAG_DISABLED_BY_PUBLISHER = 1 << 4;

    /**
     * Indicates that the icon is disabled as the user partition is currently locked.
     */
    public static final int FLAG_DISABLED_LOCKED_USER = 1 << 5;

    /**
     * The item points to a system app.
     */
    public static final int FLAG_SYSTEM_YES = 1 << 6;

    /**
     * The item points to a non system app.
     */
    public static final int FLAG_SYSTEM_NO = 1 << 7;

    public static final int FLAG_SYSTEM_MASK = FLAG_SYSTEM_YES | FLAG_SYSTEM_NO;

    /**
     * Flag indicating that the icon is an {@link android.graphics.drawable.AdaptiveIconDrawable}
     * that can be optimized in various way.
     */
    public static final int FLAG_ADAPTIVE_ICON = 1 << 8;

    /**
     * Flag indicating that the icon is badged.
     */
    public static final int FLAG_ICON_BADGED = 1 << 9;

    /**
     * The icon is being installed. If {@link WorkspaceItemInfo#FLAG_RESTORED_ICON} or
     * {@link WorkspaceItemInfo#FLAG_AUTOINSTALL_ICON} is set, then the icon is either being
     * installed or is in a broken state.
     */
    public static final int FLAG_INSTALL_SESSION_ACTIVE = 1 << 10;

    /**
     * This icon is still being downloaded.
     */
    public static final int FLAG_INCREMENTAL_DOWNLOAD_ACTIVE = 1 << 11;

    public static final int FLAG_SHOW_DOWNLOAD_PROGRESS_MASK = FLAG_INSTALL_SESSION_ACTIVE
            | FLAG_INCREMENTAL_DOWNLOAD_ACTIVE;

    /**
     * Indicates that the icon is a disabled shortcut and application updates are required.
     */
    public static final int FLAG_DISABLED_VERSION_LOWER = 1 << 12;

    public static final int FLAG_DISABLED_MASK = FLAG_DISABLED_SAFEMODE
            | FLAG_DISABLED_NOT_AVAILABLE | FLAG_DISABLED_SUSPENDED
            | FLAG_DISABLED_QUIET_USER | FLAG_DISABLED_BY_PUBLISHER | FLAG_DISABLED_LOCKED_USER
            | FLAG_DISABLED_VERSION_LOWER;

    /**
     * Flag indicating this item can't be pinned to home screen.
     */
    public static final int FLAG_NOT_PINNABLE = 1 << 13;

    /**
     * Status associated with the system state of the underlying item. This is calculated every
     * time a new info is created and not persisted on the disk.
     */
    public int runtimeStatusFlags = 0;

    /**
     * The download progress of the package that this shortcut represents. For legacy apps, this
     * will always be the installation progress. For apps that support incremental downloads, this
     * will only match be the installation progress until the app is installed, then this will the
     * total download progress.
     */
    private int mProgressLevel = 100;

    protected ItemInfoWithIcon() { }

    protected ItemInfoWithIcon(ItemInfoWithIcon info) {
        super(info);
        bitmap = info.bitmap;
        mProgressLevel = info.mProgressLevel;
        runtimeStatusFlags = info.runtimeStatusFlags;
        user = info.user;
    }

    @Override
    public boolean isDisabled() {
        return (runtimeStatusFlags & FLAG_DISABLED_MASK) != 0;
    }

    /**
     * Indicates whether we're using a low res icon
     */
    public boolean usingLowResIcon() {
        return bitmap.isLowRes();
    }

    /**
     * Returns whether the app this shortcut represents is able to be started. For legacy apps,
     * this returns whether it is fully installed. For apps that support incremental downloads,
     * this returns whether the app is either fully downloaded or has installed and is downloading
     * incrementally.
     */
    public boolean isAppStartable() {
        return ((runtimeStatusFlags & FLAG_INSTALL_SESSION_ACTIVE) == 0)
                && (((runtimeStatusFlags & FLAG_INCREMENTAL_DOWNLOAD_ACTIVE) != 0)
                    || mProgressLevel == 100);
    }

    /**
     * Returns the download progress for the app this shortcut represents. If this app is not yet
     * installed or does not support incremental downloads, this will return the installation
     * progress.
     */
    public int getProgressLevel() {
        if ((runtimeStatusFlags & FLAG_SHOW_DOWNLOAD_PROGRESS_MASK) != 0) {
            return mProgressLevel;
        }
        return 100;
    }

    /**
     * Sets the download progress for the app this shortcut represents. If this app is not yet
     * installed or does not support incremental downloads, this will set
     * {@code FLAG_INSTALL_SESSION_ACTIVE}. If this app is downloading incrementally, this will
     * set {@code FLAG_INCREMENTAL_DOWNLOAD_ACTIVE}. Otherwise, this will remove both flags.
     */
    public void setProgressLevel(PackageInstallInfo installInfo) {
        setProgressLevel(installInfo.progress, installInfo.state);

        if (installInfo.state == PackageInstallInfo.STATUS_FAILED) {
            FileLog.d(TAG,
                    "Icon info: " + this + " marked broken with install info: " + installInfo,
                    new Exception());
        }
    }

    /**
     * Sets the download progress for the app this shortcut represents.
     */
    public void setProgressLevel(int progress, int status) {
        if (status == PackageInstallInfo.STATUS_INSTALLING) {
            mProgressLevel = progress;
            runtimeStatusFlags = progress < 100
                    ? runtimeStatusFlags | FLAG_INSTALL_SESSION_ACTIVE
                    : runtimeStatusFlags & ~FLAG_INSTALL_SESSION_ACTIVE;
        } else if (status == PackageInstallInfo.STATUS_INSTALLED_DOWNLOADING) {
            mProgressLevel = progress;
            runtimeStatusFlags = runtimeStatusFlags & ~FLAG_INSTALL_SESSION_ACTIVE;
            runtimeStatusFlags = progress < 100
                    ? runtimeStatusFlags | FLAG_INCREMENTAL_DOWNLOAD_ACTIVE
                    : runtimeStatusFlags & ~FLAG_INCREMENTAL_DOWNLOAD_ACTIVE;
        } else {
            mProgressLevel = status == PackageInstallInfo.STATUS_INSTALLED ? 100 : 0;
            runtimeStatusFlags &= ~FLAG_INSTALL_SESSION_ACTIVE;
            runtimeStatusFlags &= ~FLAG_INCREMENTAL_DOWNLOAD_ACTIVE;
        }
    }

    /** Creates an intent to that launches the app store at this app's page. */
    @Nullable
    public Intent getMarketIntent(Context context) {
        String targetPackage = getTargetPackage();

        return targetPackage != null
                ? new PackageManagerHelper(context).getMarketIntent(targetPackage)
                : null;
    }

    /**
     * @return a copy of this
     */
    public abstract ItemInfoWithIcon clone();


    /**
     * Returns a FastBitmapDrawable with the icon.
     */
    public FastBitmapDrawable newIcon(Context context) {
        return newIcon(context, PreferenceManager.getInstance(context).getThemedIcons().get());
    }

    /**
     * Returns a FastBitmapDrawable with the icon and context theme applied
     */
    public FastBitmapDrawable newIcon(Context context, boolean applyTheme) {
        FastBitmapDrawable drawable = applyTheme
                ? bitmap.newThemedIcon(context) : bitmap.newIcon(context);
        drawable.setIsDisabled(isDisabled());
        return drawable;
    }
}

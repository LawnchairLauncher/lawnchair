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

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.UserHandle;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import android.util.DisplayMetrics;
import android.util.Log;
import ch.deletescape.lawnchair.iconpack.IconPackManager;
import ch.deletescape.lawnchair.sesame.SesameShortcutInfo;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.util.ContentWriter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a launchable icon on the workspaces and in folders.
 */
public class ShortcutInfo extends ItemInfoWithIcon {

    public static final int DEFAULT = 0;

    /**
     * The shortcut was restored from a backup and it not ready to be used. This is automatically
     * set during backup/restore
     */
    public static final int FLAG_RESTORED_ICON = 1;

    /**
     * The icon was added as an auto-install app, and is not ready to be used. This flag can't
     * be present along with {@link #FLAG_RESTORED_ICON}, and is set during default layout
     * parsing.
     */
    public static final int FLAG_AUTOINSTALL_ICON = 2; //0B10;

    /**
     * The icon is being installed. If {@link #FLAG_RESTORED_ICON} or {@link #FLAG_AUTOINSTALL_ICON}
     * is set, then the icon is either being installed or is in a broken state.
     */
    public static final int FLAG_INSTALL_SESSION_ACTIVE = 4; // 0B100;

    /**
     * Indicates that the widget restore has started.
     */
    public static final int FLAG_RESTORE_STARTED = 8; //0B1000;

    /**
     * Web UI supported.
     */
    public static final int FLAG_SUPPORTS_WEB_UI = 16; //0B10000;

    /**
     * The intent used to start the application.
     */
    public Intent intent;

    /**
     * If isShortcut=true and customIcon=false, this contains a reference to the
     * shortcut icon as an application's resource.
     */
    public Intent.ShortcutIconResource iconResource;

    /**
     * A message to display when the user tries to start a disabled shortcut.
     * This is currently only used for deep shortcuts.
     */
    public CharSequence disabledMessage;

    public int status;

    /**
     * The installation progress [0-100] of the package that this shortcut represents.
     */
    private int mInstallProgress;

    public CharSequence customTitle;

    public Bitmap customIcon;

    public IconPackManager.CustomIconEntry customIconEntry;

    public String swipeUpAction;

    private boolean badgeVisible = true;

    public ShortcutInfoCompat shortcutInfo;

    public ShortcutInfo() {
        itemType = LauncherSettings.BaseLauncherColumns.ITEM_TYPE_SHORTCUT;
    }

    public ShortcutInfo(ShortcutInfo info) {
        super(info);
        title = info.title;
        intent = new Intent(info.intent);
        iconResource = info.iconResource;
        status = info.status;
        mInstallProgress = info.mInstallProgress;
    }

    /** TODO: Remove this.  It's only called by ApplicationInfo.makeShortcut. */
    public ShortcutInfo(AppInfo info) {
        super(info);
        title = Utilities.trim(info.title);
        intent = new Intent(info.intent);
    }

    public ShortcutInfo(String title, Intent intent, UserHandle user) {
        this.title = title;
        this.intent = intent;
        this.user = user;
    }

    /**
     * Creates a {@link ShortcutInfo} from a {@link ShortcutInfoCompat}.
     */
    @TargetApi(Build.VERSION_CODES.N)
    public ShortcutInfo(ShortcutInfoCompat shortcutInfo, Context context) {
        user = shortcutInfo.getUserHandle();
        itemType = LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
        updateFromDeepShortcutInfo(shortcutInfo, context);
    }

    @Override
    public void onAddToDatabase(ContentWriter writer) {
        super.onAddToDatabase(writer);
        writer.put(LauncherSettings.BaseLauncherColumns.TITLE, title)
                .put(LauncherSettings.BaseLauncherColumns.INTENT, getIntent())
                .put(LauncherSettings.Favorites.RESTORED, status);

        if (!usingLowResIcon) {
            writer.putIcon(iconBitmap, user);
        }
        if (iconResource != null) {
            writer.put(LauncherSettings.BaseLauncherColumns.ICON_PACKAGE, iconResource.packageName)
                    .put(LauncherSettings.BaseLauncherColumns.ICON_RESOURCE,
                            iconResource.resourceName);
        }
    }

    @Override
    public Intent getIntent() {
        return intent;
    }

    public boolean hasStatusFlag(int flag) {
        return (status & flag) != 0;
    }


    public final boolean isPromise() {
        return hasStatusFlag(FLAG_RESTORED_ICON | FLAG_AUTOINSTALL_ICON);
    }

    public boolean hasPromiseIconUi() {
        return isPromise() && !hasStatusFlag(FLAG_SUPPORTS_WEB_UI);
    }

    public int getInstallProgress() {
        return mInstallProgress;
    }

    public void setInstallProgress(int progress) {
        mInstallProgress = progress;
        status |= FLAG_INSTALL_SESSION_ACTIVE;
    }

    public void updateFromDeepShortcutInfo(ShortcutInfoCompat shortcutInfo, Context context) {
        this.shortcutInfo = shortcutInfo;

        // {@link ShortcutInfoCompat#getActivity} can change during an update. Recreate the intent
        intent = shortcutInfo.makeIntent();
        title = shortcutInfo.getShortLabel();

        CharSequence label = shortcutInfo.getLongLabel();
        if (TextUtils.isEmpty(label)) {
            label = shortcutInfo.getShortLabel();
        }
        contentDescription = UserManagerCompat.getInstance(context)
                .getBadgedLabelForUser(label, user);
        if (shortcutInfo.isEnabled()) {
            runtimeStatusFlags &= ~FLAG_DISABLED_BY_PUBLISHER;
        } else {
            runtimeStatusFlags |= FLAG_DISABLED_BY_PUBLISHER;
        }
        disabledMessage = shortcutInfo.getDisabledMessage();

        // Treat sesame shortcuts like normal shortcuts and not like deep shortcuts
        if (shortcutInfo instanceof SesameShortcutInfo) {
            itemType = LauncherSettings.BaseLauncherColumns.ITEM_TYPE_SHORTCUT;
        }
    }

    /** Returns the ShortcutInfo id associated with the deep shortcut. */
    public String getDeepShortcutId() {
        return itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT ?
                getIntent().getStringExtra(ShortcutInfoCompat.EXTRA_SHORTCUT_ID) : null;
    }

    @Override
    public ComponentName getTargetComponent() {
        ComponentName cn = super.getTargetComponent();
        if (cn == null && (itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT
                || hasStatusFlag(FLAG_SUPPORTS_WEB_UI))) {
            // Legacy shortcuts and promise icons with web UI may not have a componentName but just
            // a packageName. In that case create a dummy componentName instead of adding additional
            // check everywhere.
            String pkg = intent.getPackage();
            return pkg == null ? null : new ComponentName(pkg, IconCache.EMPTY_CLASS_NAME);
        }
        return cn;
    }

    @Override
    public boolean isBadgeVisible() {
        return badgeVisible;
    }

    private void updateDatabase(Context context, boolean updateIcon, boolean reload) {
        if (updateIcon)
            ModelWriter.modifyItemInDatabase(context, this, (String) customTitle, swipeUpAction
                    , badgeVisible, customIconEntry, customIcon, true, reload);
        else
            ModelWriter.modifyItemInDatabase(context, this, (String) customTitle, swipeUpAction
                    , badgeVisible, null, null, false, reload);
    }

    public void onLoadCustomizations(String titleAlias, String swipeUpAction, boolean badgeVisible,
            IconPackManager.CustomIconEntry customIcon, Bitmap icon) {
        customTitle = titleAlias;
        customIconEntry = customIcon;
        this.customIcon = icon;
        this.swipeUpAction = swipeUpAction;
        this.badgeVisible = badgeVisible;
    }

    public void setTitle(@NotNull Context context, @Nullable String title) {
        customTitle = title;
        updateDatabase(context, false, true);
    }

    public void setIconEntry(@NotNull Context context, @Nullable IconPackManager.CustomIconEntry iconEntry) {
        customIconEntry = iconEntry;
        updateDatabase(context, true, false);
    }

    public void setIcon(@NotNull Context context, @Nullable Bitmap icon) {
        customIcon = icon;
        updateDatabase(context, true, true);
    }

    public void setSwipeUpAction(@NonNull Context context, @Nullable String action) {
        swipeUpAction = action;
        updateDatabase(context, false, true);
    }

    public void setBadgeVisible(@NonNull Context context, @NonNull Boolean visible) {
        badgeVisible = visible;
        updateDatabase(context, false, true);
    }
}

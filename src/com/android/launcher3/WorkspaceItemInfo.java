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

import android.app.Person;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.uioverrides.UiFactory;
import com.android.launcher3.util.ContentWriter;

import java.util.Arrays;

/**
 * Represents a launchable icon on the workspaces and in folders.
 */
public class WorkspaceItemInfo extends ItemInfoWithIcon {

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
     *
     * OR this icon was added due to it being an active install session created by the user.
     */
    public static final int FLAG_AUTOINSTALL_ICON = 1 << 1;

    /**
     * The icon is being installed. If {@link #FLAG_RESTORED_ICON} or {@link #FLAG_AUTOINSTALL_ICON}
     * is set, then the icon is either being installed or is in a broken state.
     */
    public static final int FLAG_INSTALL_SESSION_ACTIVE = 1 << 2;

    /**
     * Indicates that the widget restore has started.
     */
    public static final int FLAG_RESTORE_STARTED = 1 << 3;

    /**
     * Web UI supported.
     */
    public static final int FLAG_SUPPORTS_WEB_UI = 1 << 4;

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
     * A set of person's Id associated with the WorkspaceItemInfo, this is only used if the item
     * represents a deep shortcut.
     */
    @NonNull private String[] personKeys = Utilities.EMPTY_STRING_ARRAY;

    /**
     * The installation progress [0-100] of the package that this shortcut represents.
     */
    private int mInstallProgress;


    public WorkspaceItemInfo() {
        itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
    }

    public WorkspaceItemInfo(WorkspaceItemInfo info) {
        super(info);
        title = info.title;
        intent = new Intent(info.intent);
        iconResource = info.iconResource;
        status = info.status;
        mInstallProgress = info.mInstallProgress;
        personKeys = info.personKeys.clone();
    }

    /** TODO: Remove this.  It's only called by ApplicationInfo.makeWorkspaceItem. */
    public WorkspaceItemInfo(AppInfo info) {
        super(info);
        title = Utilities.trim(info.title);
        intent = new Intent(info.intent);
    }

    /**
     * Creates a {@link WorkspaceItemInfo} from a {@link ShortcutInfo}.
     */
    public WorkspaceItemInfo(ShortcutInfo shortcutInfo, Context context) {
        user = shortcutInfo.getUserHandle();
        itemType = Favorites.ITEM_TYPE_DEEP_SHORTCUT;
        updateFromDeepShortcutInfo(shortcutInfo, context);
    }

    @Override
    public void onAddToDatabase(ContentWriter writer) {
        super.onAddToDatabase(writer);
        writer.put(Favorites.TITLE, title)
                .put(Favorites.INTENT, getIntent())
                .put(Favorites.RESTORED, status);

        if (!usingLowResIcon()) {
            writer.putIcon(iconBitmap, user);
        }
        if (iconResource != null) {
            writer.put(Favorites.ICON_PACKAGE, iconResource.packageName)
                    .put(Favorites.ICON_RESOURCE, iconResource.resourceName);
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

    public void updateFromDeepShortcutInfo(ShortcutInfo shortcutInfo, Context context) {
        // {@link ShortcutInfo#getActivity} can change during an update. Recreate the intent
        intent = ShortcutKey.makeIntent(shortcutInfo);
        title = shortcutInfo.getShortLabel();

        CharSequence label = shortcutInfo.getLongLabel();
        if (TextUtils.isEmpty(label)) {
            label = shortcutInfo.getShortLabel();
        }
        contentDescription = context.getPackageManager().getUserBadgedLabel(label, user);
        if (shortcutInfo.isEnabled()) {
            runtimeStatusFlags &= ~FLAG_DISABLED_BY_PUBLISHER;
        } else {
            runtimeStatusFlags |= FLAG_DISABLED_BY_PUBLISHER;
        }
        disabledMessage = shortcutInfo.getDisabledMessage();

        Person[] persons = UiFactory.getPersons(shortcutInfo);
        personKeys = persons.length == 0 ? Utilities.EMPTY_STRING_ARRAY
            : Arrays.stream(persons).map(Person::getKey).sorted().toArray(String[]::new);
    }

    /** Returns the WorkspaceItemInfo id associated with the deep shortcut. */
    public String getDeepShortcutId() {
        return itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT ?
                getIntent().getStringExtra(ShortcutKey.EXTRA_SHORTCUT_ID) : null;
    }

    @NonNull
    public String[] getPersonKeys() {
        return personKeys;
    }

    @Override
    public ComponentName getTargetComponent() {
        ComponentName cn = super.getTargetComponent();
        if (cn == null && (itemType == Favorites.ITEM_TYPE_SHORTCUT
                || hasStatusFlag(FLAG_SUPPORTS_WEB_UI | FLAG_AUTOINSTALL_ICON))) {
            // Legacy shortcuts and promise icons with web UI may not have a componentName but just
            // a packageName. In that case create a dummy componentName instead of adding additional
            // check everywhere.
            String pkg = intent.getPackage();
            return pkg == null ? null : new ComponentName(pkg, IconCache.EMPTY_CLASS_NAME);
        }
        return cn;
    }
}

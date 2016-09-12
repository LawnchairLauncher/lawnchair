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
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.compat.LauncherActivityInfoCompat;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;

/**
 * Represents a launchable icon on the workspaces and in folders.
 */
public class ShortcutInfo extends ItemInfo {

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
    public static final int FLAG_AUTOINTALL_ICON = 2; //0B10;

    /**
     * The icon is being installed. If {@link #FLAG_RESTORED_ICON} or {@link #FLAG_AUTOINTALL_ICON}
     * is set, then the icon is either being installed or is in a broken state.
     */
    public static final int FLAG_INSTALL_SESSION_ACTIVE = 4; // 0B100;

    /**
     * Indicates that the widget restore has started.
     */
    public static final int FLAG_RESTORE_STARTED = 8; //0B1000;

    /**
     * Indicates if it represents a common type mentioned in {@link CommonAppTypeParser}.
     * Upto 15 different types supported.
     */
    public static final int FLAG_RESTORED_APP_TYPE = 0B0011110000;

    /**
     * The intent used to start the application.
     */
    public Intent intent;

    /**
     * Indicates whether we're using the default fallback icon instead of something from the
     * app.
     */
    boolean usingFallbackIcon;

    /**
     * Indicates whether we're using a low res icon
     */
    boolean usingLowResIcon;

    /**
     * If isShortcut=true and customIcon=false, this contains a reference to the
     * shortcut icon as an application's resource.
     */
    public Intent.ShortcutIconResource iconResource;

    /**
     * The application icon.
     */
    private Bitmap mIcon;

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
     * Could be disabled, if the the app is installed but unavailable (eg. in safe mode or when
     * sd-card is not available).
     */
    int isDisabled = DEFAULT;

    /**
     * A message to display when the user tries to start a disabled shortcut.
     * This is currently only used for deep shortcuts.
     */
    CharSequence disabledMessage;

    int status;

    /**
     * The installation progress [0-100] of the package that this shortcut represents.
     */
    private int mInstallProgress;

    /**
     * TODO move this to {@link #status}
     */
    int flags = 0;

    /**
     * If this shortcut is a placeholder, then intent will be a market intent for the package, and
     * this will hold the original intent from the database.  Otherwise, null.
     * Refer {@link #FLAG_RESTORED_ICON}, {@link #FLAG_AUTOINTALL_ICON}
     */
    Intent promisedIntent;

    public ShortcutInfo() {
        itemType = LauncherSettings.BaseLauncherColumns.ITEM_TYPE_SHORTCUT;
    }

    @Override
    public Intent getIntent() {
        return intent;
    }

    /** Returns {@link #promisedIntent}, or {@link #intent} if promisedIntent is null. */
    public Intent getPromisedIntent() {
        return promisedIntent != null ? promisedIntent : intent;
    }

    ShortcutInfo(Intent intent, CharSequence title, CharSequence contentDescription,
            Bitmap icon, UserHandleCompat user) {
        this();
        this.intent = intent;
        this.title = Utilities.trim(title);
        this.contentDescription = contentDescription;
        mIcon = icon;
        this.user = user;
    }

    public ShortcutInfo(ShortcutInfo info) {
        super(info);
        title = info.title;
        intent = new Intent(info.intent);
        iconResource = info.iconResource;
        mIcon = info.mIcon; // TODO: should make a copy here.  maybe we don't need this ctor at all
        flags = info.flags;
        status = info.status;
        mInstallProgress = info.mInstallProgress;
        isDisabled = info.isDisabled;
        usingFallbackIcon = info.usingFallbackIcon;
    }

    /** TODO: Remove this.  It's only called by ApplicationInfo.makeShortcut. */
    public ShortcutInfo(AppInfo info) {
        super(info);
        title = Utilities.trim(info.title);
        intent = new Intent(info.intent);
        flags = info.flags;
        isDisabled = info.isDisabled;
    }

    public ShortcutInfo(LauncherActivityInfoCompat info, Context context) {
        user = info.getUser();
        title = Utilities.trim(info.getLabel());
        contentDescription = UserManagerCompat.getInstance(context)
                .getBadgedLabelForUser(info.getLabel(), info.getUser());
        intent = AppInfo.makeLaunchIntent(context, info, info.getUser());
        itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
        flags = AppInfo.initFlags(info);
    }

    /**
     * Creates a {@link ShortcutInfo} from a {@link ShortcutInfoCompat}.
     */
    @TargetApi(Build.VERSION_CODES.N)
    public ShortcutInfo(ShortcutInfoCompat shortcutInfo, Context context) {
        user = shortcutInfo.getUserHandle();
        itemType = LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
        flags = 0;
        updateFromDeepShortcutInfo(shortcutInfo, context);
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

    public void updateIcon(IconCache iconCache, boolean useLowRes) {
        if (itemType == Favorites.ITEM_TYPE_APPLICATION) {
            iconCache.getTitleAndIcon(this, promisedIntent != null ? promisedIntent : intent, user,
                    useLowRes);
        }
    }

    public void updateIcon(IconCache iconCache) {
        updateIcon(iconCache, shouldUseLowResIcon());
    }

    @Override
    void onAddToDatabase(Context context, ContentValues values) {
        super.onAddToDatabase(context, values);

        String titleStr = title != null ? title.toString() : null;
        values.put(LauncherSettings.BaseLauncherColumns.TITLE, titleStr);

        String uri = promisedIntent != null ? promisedIntent.toUri(0)
                : (intent != null ? intent.toUri(0) : null);
        values.put(LauncherSettings.BaseLauncherColumns.INTENT, uri);
        values.put(LauncherSettings.Favorites.RESTORED, status);

        if (!usingFallbackIcon && !usingLowResIcon) {
            writeBitmap(values, mIcon);
        }
        if (iconResource != null) {
            values.put(LauncherSettings.BaseLauncherColumns.ICON_PACKAGE,
                    iconResource.packageName);
            values.put(LauncherSettings.BaseLauncherColumns.ICON_RESOURCE,
                    iconResource.resourceName);
        }
    }

    public ComponentName getTargetComponent() {
        return promisedIntent != null ? promisedIntent.getComponent() : intent.getComponent();
    }

    public boolean hasStatusFlag(int flag) {
        return (status & flag) != 0;
    }


    public final boolean isPromise() {
        return hasStatusFlag(FLAG_RESTORED_ICON | FLAG_AUTOINTALL_ICON);
    }

    public int getInstallProgress() {
        return mInstallProgress;
    }

    public void setInstallProgress(int progress) {
        mInstallProgress = progress;
        status |= FLAG_INSTALL_SESSION_ACTIVE;
    }

    public boolean shouldUseLowResIcon() {
        return usingLowResIcon && container >= 0 && rank >= FolderIcon.NUM_ITEMS_IN_PREVIEW;
    }

    public void updateFromDeepShortcutInfo(ShortcutInfoCompat shortcutInfo, Context context) {
        // {@link ShortcutInfoCompat#getActivity} can change during an update. Recreate the intent
        intent = shortcutInfo.makeIntent(context);
        title = shortcutInfo.getShortLabel();

        CharSequence label = shortcutInfo.getLongLabel();
        if (TextUtils.isEmpty(label)) {
            label = shortcutInfo.getShortLabel();
        }
        contentDescription = UserManagerCompat.getInstance(context)
                .getBadgedLabelForUser(label, user);
        if (shortcutInfo.isEnabled()) {
            isDisabled &= ~FLAG_DISABLED_BY_PUBLISHER;
        } else {
            isDisabled |= FLAG_DISABLED_BY_PUBLISHER;
        }
        disabledMessage = shortcutInfo.getDisabledMessage();

        // TODO: Use cache for this
        LauncherAppState launcherAppState = LauncherAppState.getInstance();
        Drawable unbadgedDrawable = launcherAppState.getShortcutManager()
                .getShortcutIconDrawable(shortcutInfo,
                        launcherAppState.getInvariantDeviceProfile().fillResIconDpi);

        IconCache cache = launcherAppState.getIconCache();
        Bitmap unbadgedBitmap = unbadgedDrawable == null
                ? cache.getDefaultIcon(UserHandleCompat.myUserHandle())
                : Utilities.createScaledBitmapWithoutShadow(unbadgedDrawable, context);
        setIcon(getBadgedIcon(unbadgedBitmap, shortcutInfo, cache, context));
    }

    protected Bitmap getBadgedIcon(Bitmap unbadgedBitmap, ShortcutInfoCompat shortcutInfo,
            IconCache cache, Context context) {
        unbadgedBitmap = Utilities.addShadowToIcon(unbadgedBitmap);
        // Get the app info for the source activity.
        AppInfo appInfo = new AppInfo();
        appInfo.user = user;
        appInfo.componentName = shortcutInfo.getActivity();
        try {
            cache.getTitleAndIcon(appInfo, shortcutInfo.getActivityInfo(context), false);
        } catch (NullPointerException e) {
            // This may happen when we fail to load the activity info. Worst case ignore badging.
            return Utilities.badgeIconForUser(unbadgedBitmap, user, context);
        }
        return Utilities.badgeWithBitmap(unbadgedBitmap, appInfo.iconBitmap, context);
    }

    /** Returns the ShortcutInfo id associated with the deep shortcut. */
    public String getDeepShortcutId() {
        return itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT ?
                getPromisedIntent().getStringExtra(ShortcutInfoCompat.EXTRA_SHORTCUT_ID) : null;
    }

    @Override
    public boolean isDisabled() {
        return isDisabled != 0;
    }
}

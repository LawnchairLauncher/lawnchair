/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.util;

import static com.android.launcher3.Utilities.ATLEAST_R;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_INSTALL_SESSION_ACTIVE;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.PendingAddItemInfo;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dagger.ApplicationContext;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.dagger.LauncherBaseAppComponent;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

/**
 * Utility methods using package manager
 */
@LauncherAppSingleton
public class PackageManagerHelper {

    private static final String TAG = "PackageManagerHelper";

    @NonNull
    public static DaggerSingletonObject<PackageManagerHelper> INSTANCE =
            new DaggerSingletonObject<>(LauncherBaseAppComponent::getPackageManagerHelper);

    @NonNull
    private final Context mContext;

    @NonNull
    private final PackageManager mPm;

    @NonNull
    private final LauncherApps mLauncherApps;

    @Inject
    public PackageManagerHelper(@ApplicationContext final Context context) {
        mContext = context;
        mPm = context.getPackageManager();
        mLauncherApps = Objects.requireNonNull(context.getSystemService(LauncherApps.class));
    }

    /**
     * Returns the installing app package for the given package
     */
    public String getAppInstallerPackage(@NonNull final String packageName) {
        try {
            if (ATLEAST_R) {
                return mPm.getInstallSourceInfo(packageName).getInstallingPackageName();
            } else {
                return null;
            }
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Failed to get installer package for app package:" + packageName, e);
            return null;
        }
    }

    /**
     * Returns the preferred launch activity intent for a given package.
     */
    @Nullable
    public Intent getAppLaunchIntent(@Nullable final String pkg, @NonNull final UserHandle user) {
        LauncherActivityInfo info = getAppLaunchInfo(pkg, user);
        return info != null ? AppInfo.makeLaunchIntent(info) : null;
    }

    /**
     * Returns the preferred launch activity for a given package.
     */
    @Nullable
    public LauncherActivityInfo getAppLaunchInfo(@Nullable final String pkg,
            @NonNull final UserHandle user) {
        List<LauncherActivityInfo> activities = mLauncherApps.getActivityList(pkg, user);
        return activities.isEmpty() ? null : activities.get(0);
    }

    /**
     * Creates a new market search intent.
     */
    public static Intent getMarketSearchIntent(Context context, String query) {
        try {
            Intent intent = Intent.parseUri(context.getString(R.string.market_search_intent), 0);
            if (!TextUtils.isEmpty(query)) {
                intent.setData(
                    intent.getData().buildUpon().appendQueryParameter("q", query).build());
            }
            return intent;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    // Lawnchair
    public static Intent getStyleWallpapersIntent(Context context) {
        return getStyleWallpapersAltIntent(context);
    }

    // Lawnchair
    public static Intent getStyleWallpapersAltIntent(Context context) {
        return new Intent(Intent.ACTION_SET_WALLPAPER).setComponent(
            new ComponentName(context.getString(R.string.wallpaper_picker_package_alt),
                "com.android.customization.picker.CustomizationPickerActivity"));
    }

    /**
     * Starts the details activity for {@code info}
     */
    public static void startDetailsActivityForInfo(Context context, ItemInfo info,
            Rect sourceBounds, Bundle opts) {
        if (info instanceof ItemInfoWithIcon appInfo
                && (appInfo.runtimeStatusFlags & FLAG_INSTALL_SESSION_ACTIVE) != 0) {
            context.startActivity(ApiWrapper.INSTANCE.get(context).getAppMarketActivityIntent(
                    appInfo.getTargetComponent().getPackageName(), Process.myUserHandle()), opts);
            return;
        }
        ComponentName componentName = null;
        if (info instanceof AppInfo) {
            componentName = ((AppInfo) info).componentName;
        } else if (info instanceof WorkspaceItemInfo) {
            componentName = info.getTargetComponent();
        } else if (info instanceof PendingAddItemInfo) {
            componentName = ((PendingAddItemInfo) info).componentName;
        } else if (info instanceof LauncherAppWidgetInfo) {
            componentName = ((LauncherAppWidgetInfo) info).providerName;
        }
        if (componentName != null) {
            try {
                context.getSystemService(LauncherApps.class).startAppDetailsActivity(componentName,
                        info.user, sourceBounds, opts);
            } catch (SecurityException | ActivityNotFoundException e) {
                Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Unable to launch settings", e);
            }
        }
    }

    /**
     * Returns true if the intent is a valid launch intent for a launcher activity of an app.
     * This is used to identify shortcuts which are different from the ones exposed by the
     * applications' manifest file.
     *
     * @param launchIntent The intent that will be launched when the shortcut is clicked.
     */
    public static boolean isLauncherAppTarget(Intent launchIntent) {
        if (launchIntent != null
                && Intent.ACTION_MAIN.equals(launchIntent.getAction())
                && launchIntent.getComponent() != null
                && launchIntent.getCategories() != null
                && launchIntent.getCategories().size() == 1
                && launchIntent.hasCategory(Intent.CATEGORY_LAUNCHER)
                && TextUtils.isEmpty(launchIntent.getDataString())) {
            // An app target can either have no extra or have ItemInfo.EXTRA_PROFILE.
            Bundle extras = launchIntent.getExtras();
            return extras == null || extras.keySet().isEmpty();
        }
        return false;
    }

    /**
     * Returns true if Launcher has the permission to access shortcuts.
     *
     * @see LauncherApps#hasShortcutHostPermission()
     */
    public static boolean hasShortcutsPermission(Context context) {
        try {
            return context.getSystemService(LauncherApps.class).hasShortcutHostPermission();
        } catch (SecurityException | IllegalStateException e) {
            Log.e(TAG, "Failed to make shortcut manager call", e);
        }
        return false;
    }

    /** Returns the incremental download progress for the given shortcut's app. */
    public static int getLoadingProgress(LauncherActivityInfo info) {
        if (Utilities.ATLEAST_S) {
            return (int) (100 * info.getLoadingProgress());
        } else {
            return 100;
        }
    }

    /**
     * Returns whether two apps should be considered the same for multi-instance purposes, which
     * requires additional checks to ensure they can be started as multiple instances.
     */
    public static boolean isSameAppForMultiInstance(@NonNull ItemInfo app1,
            @NonNull ItemInfo app2) {
        return app1.getTargetPackage().equals(app2.getTargetPackage())
                && app1.user.equals(app2.user);
    }
}

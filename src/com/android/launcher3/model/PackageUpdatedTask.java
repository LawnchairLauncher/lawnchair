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
package com.android.launcher3.model;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;

import com.android.launcher3.AllAppsList;
import com.android.launcher3.AppInfo;
import com.android.launcher3.IconCache;
import com.android.launcher3.InstallShortcutReceiver;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherModel.CallbackTask;
import com.android.launcher3.LauncherModel.Callbacks;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.SessionCommitReceiver;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.graphics.BitmapInfo;
import com.android.launcher3.graphics.LauncherIcons;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.util.FlagOp;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.LongArrayMap;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Handles updates due to changes in package manager (app installed/updated/removed)
 * or when a user availability changes.
 */
public class PackageUpdatedTask extends BaseModelUpdateTask {

    private static final boolean DEBUG = false;
    private static final String TAG = "PackageUpdatedTask";

    public static final int OP_NONE = 0;
    public static final int OP_ADD = 1;
    public static final int OP_UPDATE = 2;
    public static final int OP_REMOVE = 3; // uninstalled
    public static final int OP_UNAVAILABLE = 4; // external media unmounted
    public static final int OP_SUSPEND = 5; // package suspended
    public static final int OP_UNSUSPEND = 6; // package unsuspended
    public static final int OP_USER_AVAILABILITY_CHANGE = 7; // user available/unavailable

    private final int mOp;
    private final UserHandle mUser;
    private final String[] mPackages;

    public PackageUpdatedTask(int op, UserHandle user, String... packages) {
        mOp = op;
        mUser = user;
        mPackages = packages;
    }

    @Override
    public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList appsList) {
        final Context context = app.getContext();
        final IconCache iconCache = app.getIconCache();

        final String[] packages = mPackages;
        final int N = packages.length;
        FlagOp flagOp = FlagOp.NO_OP;
        final HashSet<String> packageSet = new HashSet<>(Arrays.asList(packages));
        ItemInfoMatcher matcher = ItemInfoMatcher.ofPackages(packageSet, mUser);
        switch (mOp) {
            case OP_ADD: {
                for (int i = 0; i < N; i++) {
                    if (DEBUG) Log.d(TAG, "mAllAppsList.addPackage " + packages[i]);
                    iconCache.updateIconsForPkg(packages[i], mUser);
                    if (FeatureFlags.LAUNCHER3_PROMISE_APPS_IN_ALL_APPS) {
                        appsList.removePackage(packages[i], Process.myUserHandle());
                    }
                    appsList.addPackage(context, packages[i], mUser);

                    // Automatically add homescreen icon for work profile apps for below O device.
                    if (!Utilities.ATLEAST_OREO && !Process.myUserHandle().equals(mUser)) {
                        SessionCommitReceiver.queueAppIconAddition(context, packages[i], mUser);
                    }
                }
                flagOp = FlagOp.removeFlag(ShortcutInfo.FLAG_DISABLED_NOT_AVAILABLE);
                break;
            }
            case OP_UPDATE:
                for (int i = 0; i < N; i++) {
                    if (DEBUG) Log.d(TAG, "mAllAppsList.updatePackage " + packages[i]);
                    iconCache.updateIconsForPkg(packages[i], mUser);
                    appsList.updatePackage(context, packages[i], mUser);
                    app.getWidgetCache().removePackage(packages[i], mUser);
                }
                // Since package was just updated, the target must be available now.
                flagOp = FlagOp.removeFlag(ShortcutInfo.FLAG_DISABLED_NOT_AVAILABLE);
                break;
            case OP_REMOVE: {
                for (int i = 0; i < N; i++) {
                    iconCache.removeIconsForPkg(packages[i], mUser);
                }
                // Fall through
            }
            case OP_UNAVAILABLE:
                for (int i = 0; i < N; i++) {
                    if (DEBUG) Log.d(TAG, "mAllAppsList.removePackage " + packages[i]);
                    appsList.removePackage(packages[i], mUser);
                    app.getWidgetCache().removePackage(packages[i], mUser);
                }
                flagOp = FlagOp.addFlag(ShortcutInfo.FLAG_DISABLED_NOT_AVAILABLE);
                break;
            case OP_SUSPEND:
            case OP_UNSUSPEND:
                flagOp = mOp == OP_SUSPEND ?
                        FlagOp.addFlag(ShortcutInfo.FLAG_DISABLED_SUSPENDED) :
                        FlagOp.removeFlag(ShortcutInfo.FLAG_DISABLED_SUSPENDED);
                if (DEBUG) Log.d(TAG, "mAllAppsList.(un)suspend " + N);
                appsList.updateDisabledFlags(matcher, flagOp);
                break;
            case OP_USER_AVAILABILITY_CHANGE:
                flagOp = UserManagerCompat.getInstance(context).isQuietModeEnabled(mUser)
                        ? FlagOp.addFlag(ShortcutInfo.FLAG_DISABLED_QUIET_USER)
                        : FlagOp.removeFlag(ShortcutInfo.FLAG_DISABLED_QUIET_USER);
                // We want to update all packages for this user.
                matcher = ItemInfoMatcher.ofUser(mUser);
                appsList.updateDisabledFlags(matcher, flagOp);
                break;
        }

        final ArrayList<AppInfo> addedOrModified = new ArrayList<>();
        addedOrModified.addAll(appsList.added);
        appsList.added.clear();
        addedOrModified.addAll(appsList.modified);
        appsList.modified.clear();

        final ArrayList<AppInfo> removedApps = new ArrayList<>(appsList.removed);
        appsList.removed.clear();

        final ArrayMap<ComponentName, AppInfo> addedOrUpdatedApps = new ArrayMap<>();
        if (!addedOrModified.isEmpty()) {
            scheduleCallbackTask((callbacks) -> callbacks.bindAppsAddedOrUpdated(addedOrModified));
            for (AppInfo ai : addedOrModified) {
                addedOrUpdatedApps.put(ai.componentName, ai);
            }
        }

        final LongArrayMap<Boolean> removedShortcuts = new LongArrayMap<>();

        // Update shortcut infos
        if (mOp == OP_ADD || flagOp != FlagOp.NO_OP) {
            final ArrayList<ShortcutInfo> updatedShortcuts = new ArrayList<>();
            final ArrayList<LauncherAppWidgetInfo> widgets = new ArrayList<>();

            // For system apps, package manager send OP_UPDATE when an app is enabled.
            final boolean isNewApkAvailable = mOp == OP_ADD || mOp == OP_UPDATE;
            synchronized (dataModel) {
                for (ItemInfo info : dataModel.itemsIdMap) {
                    if (info instanceof ShortcutInfo && mUser.equals(info.user)) {
                        ShortcutInfo si = (ShortcutInfo) info;
                        boolean infoUpdated = false;
                        boolean shortcutUpdated = false;

                        // Update shortcuts which use iconResource.
                        if ((si.iconResource != null)
                                && packageSet.contains(si.iconResource.packageName)) {
                            LauncherIcons li = LauncherIcons.obtain(context);
                            BitmapInfo iconInfo = li.createIconBitmap(si.iconResource);
                            li.recycle();
                            if (iconInfo != null) {
                                iconInfo.applyTo(si);
                                infoUpdated = true;
                            }
                        }

                        ComponentName cn = si.getTargetComponent();
                        if (cn != null && matcher.matches(si, cn)) {
                            AppInfo appInfo = addedOrUpdatedApps.get(cn);

                            if (si.hasStatusFlag(ShortcutInfo.FLAG_SUPPORTS_WEB_UI)) {
                                removedShortcuts.put(si.id, false);
                                if (mOp == OP_REMOVE) {
                                    continue;
                                }
                            }

                            if (si.isPromise() && isNewApkAvailable) {
                                boolean isTargetValid = true;
                                if (si.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                                    List<ShortcutInfoCompat> shortcut = DeepShortcutManager
                                            .getInstance(context).queryForPinnedShortcuts(
                                                    cn.getPackageName(),
                                                    Arrays.asList(si.getDeepShortcutId()), mUser);
                                    if (shortcut.isEmpty()) {
                                        isTargetValid = false;
                                    } else {
                                        si.updateFromDeepShortcutInfo(shortcut.get(0), context);
                                        infoUpdated = true;
                                    }
                                } else if (!cn.getClassName().equals(IconCache.EMPTY_CLASS_NAME)) {
                                    isTargetValid = LauncherAppsCompat.getInstance(context)
                                            .isActivityEnabledForProfile(cn, mUser);
                                }

                                if (si.hasStatusFlag(ShortcutInfo.FLAG_AUTOINSTALL_ICON)) {
                                    // Auto install icon
                                    if (!isTargetValid) {
                                        // Try to find the best match activity.
                                        Intent intent = new PackageManagerHelper(context)
                                                .getAppLaunchIntent(cn.getPackageName(), mUser);
                                        if (intent != null) {
                                            cn = intent.getComponent();
                                            appInfo = addedOrUpdatedApps.get(cn);
                                        }

                                        if (intent != null && appInfo != null) {
                                            si.intent = intent;
                                            si.status = ShortcutInfo.DEFAULT;
                                            infoUpdated = true;
                                        } else if (si.hasPromiseIconUi()) {
                                            removedShortcuts.put(si.id, true);
                                            continue;
                                        }
                                    }
                                } else if (!isTargetValid) {
                                    removedShortcuts.put(si.id, true);
                                    FileLog.e(TAG, "Restored shortcut no longer valid "
                                            + si.intent);
                                    continue;
                                } else {
                                    si.status = ShortcutInfo.DEFAULT;
                                    infoUpdated = true;
                                }
                            }

                            if (isNewApkAvailable &&
                                    si.itemType == Favorites.ITEM_TYPE_APPLICATION) {
                                iconCache.getTitleAndIcon(si, si.usingLowResIcon);
                                infoUpdated = true;
                            }

                            int oldRuntimeFlags = si.runtimeStatusFlags;
                            si.runtimeStatusFlags = flagOp.apply(si.runtimeStatusFlags);
                            if (si.runtimeStatusFlags != oldRuntimeFlags) {
                                shortcutUpdated = true;
                            }
                        }

                        if (infoUpdated || shortcutUpdated) {
                            updatedShortcuts.add(si);
                        }
                        if (infoUpdated) {
                            getModelWriter().updateItemInDatabase(si);
                        }
                    } else if (info instanceof LauncherAppWidgetInfo && isNewApkAvailable) {
                        LauncherAppWidgetInfo widgetInfo = (LauncherAppWidgetInfo) info;
                        if (mUser.equals(widgetInfo.user)
                                && widgetInfo.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY)
                                && packageSet.contains(widgetInfo.providerName.getPackageName())) {
                            widgetInfo.restoreStatus &=
                                    ~LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY &
                                            ~LauncherAppWidgetInfo.FLAG_RESTORE_STARTED;

                            // adding this flag ensures that launcher shows 'click to setup'
                            // if the widget has a config activity. In case there is no config
                            // activity, it will be marked as 'restored' during bind.
                            widgetInfo.restoreStatus |= LauncherAppWidgetInfo.FLAG_UI_NOT_READY;

                            widgets.add(widgetInfo);
                            getModelWriter().updateItemInDatabase(widgetInfo);
                        }
                    }
                }
            }

            bindUpdatedShortcuts(updatedShortcuts, mUser);
            if (!removedShortcuts.isEmpty()) {
                deleteAndBindComponentsRemoved(ItemInfoMatcher.ofItemIds(removedShortcuts, false));
            }

            if (!widgets.isEmpty()) {
                scheduleCallbackTask(new CallbackTask() {
                    @Override
                    public void execute(Callbacks callbacks) {
                        callbacks.bindWidgetsRestored(widgets);
                    }
                });
            }
        }

        final HashSet<String> removedPackages = new HashSet<>();
        final HashSet<ComponentName> removedComponents = new HashSet<>();
        if (mOp == OP_REMOVE) {
            // Mark all packages in the broadcast to be removed
            Collections.addAll(removedPackages, packages);

            // No need to update the removedComponents as
            // removedPackages is a super-set of removedComponents
        } else if (mOp == OP_UPDATE) {
            // Mark disabled packages in the broadcast to be removed
            final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(context);
            for (int i=0; i<N; i++) {
                if (!launcherApps.isPackageEnabledForProfile(packages[i], mUser)) {
                    removedPackages.add(packages[i]);
                }
            }

            // Update removedComponents as some components can get removed during package update
            for (AppInfo info : removedApps) {
                removedComponents.add(info.componentName);
            }
        }

        if (!removedPackages.isEmpty() || !removedComponents.isEmpty()) {
            ItemInfoMatcher removeMatch = ItemInfoMatcher.ofPackages(removedPackages, mUser)
                    .or(ItemInfoMatcher.ofComponents(removedComponents, mUser))
                    .and(ItemInfoMatcher.ofItemIds(removedShortcuts, true));
            deleteAndBindComponentsRemoved(removeMatch);

            // Remove any queued items from the install queue
            InstallShortcutReceiver.removeFromInstallQueue(context, removedPackages, mUser);
        }

        if (!removedApps.isEmpty()) {
            // Remove corresponding apps from All-Apps
            scheduleCallbackTask(new CallbackTask() {
                @Override
                public void execute(Callbacks callbacks) {
                    callbacks.bindAppInfosRemoved(removedApps);
                }
            });
        }

        if (Utilities.ATLEAST_OREO && mOp == OP_ADD) {
            // Load widgets for the new package. Changes due to app updates are handled through
            // AppWidgetHost events, this is just to initialize the long-press options.
            for (int i = 0; i < N; i++) {
                dataModel.widgetsModel.update(app, new PackageUserKey(packages[i], mUser));
            }
            bindUpdatedWidgets(dataModel);
        }
    }
}

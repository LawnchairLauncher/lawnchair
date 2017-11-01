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
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.os.UserHandle;
import android.util.Log;

import com.android.launcher3.AllAppsList;
import com.android.launcher3.AppInfo;
import com.android.launcher3.IconCache;
import com.android.launcher3.InstallShortcutReceiver;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherModel.CallbackTask;
import com.android.launcher3.LauncherModel.Callbacks;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.graphics.LauncherIcons;
import com.android.launcher3.util.FlagOp;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.ManagedProfileHeuristic;
import com.android.launcher3.util.PackageUserKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Handles updates due to changes in package manager (app installed/updated/removed)
 * or when a user availability changes.
 */
public class PackageUpdatedTask extends ExtendedModelTask {

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
                    appsList.addPackage(context, packages[i], mUser);
                }

                ManagedProfileHeuristic heuristic = ManagedProfileHeuristic.get(context, mUser);
                if (heuristic != null) {
                    heuristic.processPackageAdd(mPackages);
                }
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
                ManagedProfileHeuristic heuristic = ManagedProfileHeuristic.get(context, mUser);
                if (heuristic != null) {
                    heuristic.processPackageRemoved(mPackages);
                }
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

        ArrayList<AppInfo> added = null;
        ArrayList<AppInfo> modified = null;
        final ArrayList<AppInfo> removedApps = new ArrayList<AppInfo>();

        if (appsList.added.size() > 0) {
            added = new ArrayList<>(appsList.added);
            appsList.added.clear();
        }
        if (appsList.modified.size() > 0) {
            modified = new ArrayList<>(appsList.modified);
            appsList.modified.clear();
        }
        if (appsList.removed.size() > 0) {
            removedApps.addAll(appsList.removed);
            appsList.removed.clear();
        }

        final HashMap<ComponentName, AppInfo> addedOrUpdatedApps = new HashMap<>();

        if (added != null) {
            final ArrayList<AppInfo> addedApps = added;
            scheduleCallbackTask(new CallbackTask() {
                @Override
                public void execute(Callbacks callbacks) {
                    callbacks.bindAppsAdded(null, null, null, addedApps);
                }
            });
            for (AppInfo ai : added) {
                addedOrUpdatedApps.put(ai.componentName, ai);
            }
        }

        if (modified != null) {
            final ArrayList<AppInfo> modifiedFinal = modified;
            for (AppInfo ai : modified) {
                addedOrUpdatedApps.put(ai.componentName, ai);
            }
            scheduleCallbackTask(new CallbackTask() {
                @Override
                public void execute(Callbacks callbacks) {
                    callbacks.bindAppsUpdated(modifiedFinal);
                }
            });
        }

        // Update shortcut infos
        if (mOp == OP_ADD || flagOp != FlagOp.NO_OP) {
            final ArrayList<ShortcutInfo> updatedShortcuts = new ArrayList<>();
            final ArrayList<ShortcutInfo> removedShortcuts = new ArrayList<>();
            final ArrayList<LauncherAppWidgetInfo> widgets = new ArrayList<>();

            synchronized (dataModel) {
                for (ItemInfo info : dataModel.itemsIdMap) {
                    if (info instanceof ShortcutInfo && mUser.equals(info.user)) {
                        ShortcutInfo si = (ShortcutInfo) info;
                        boolean infoUpdated = false;
                        boolean shortcutUpdated = false;

                        // Update shortcuts which use iconResource.
                        if ((si.iconResource != null)
                                && packageSet.contains(si.iconResource.packageName)) {
                            Bitmap icon = LauncherIcons.createIconBitmap(si.iconResource, context);
                            if (icon != null) {
                                si.iconBitmap = icon;
                                infoUpdated = true;
                            }
                        }

                        ComponentName cn = si.getTargetComponent();
                        if (cn != null && matcher.matches(si, cn)) {
                            AppInfo appInfo = addedOrUpdatedApps.get(cn);

                            if (si.isPromise() && mOp == OP_ADD) {
                                if (si.hasStatusFlag(ShortcutInfo.FLAG_AUTOINTALL_ICON)) {
                                    // Auto install icon
                                    PackageManager pm = context.getPackageManager();
                                    ResolveInfo matched = pm.resolveActivity(
                                            new Intent(Intent.ACTION_MAIN)
                                                    .setComponent(cn).addCategory(Intent.CATEGORY_LAUNCHER),
                                            PackageManager.MATCH_DEFAULT_ONLY);
                                    if (matched == null) {
                                        // Try to find the best match activity.
                                        Intent intent = pm.getLaunchIntentForPackage(
                                                cn.getPackageName());
                                        if (intent != null) {
                                            cn = intent.getComponent();
                                            appInfo = addedOrUpdatedApps.get(cn);
                                        }

                                        if ((intent == null) || (appInfo == null)) {
                                            removedShortcuts.add(si);
                                            continue;
                                        }
                                        si.intent = intent;
                                    }
                                }

                                si.status = ShortcutInfo.DEFAULT;
                                infoUpdated = true;
                                if (si.itemType == Favorites.ITEM_TYPE_APPLICATION) {
                                    iconCache.getTitleAndIcon(si, si.usingLowResIcon);
                                }
                            }

                            if (appInfo != null && Intent.ACTION_MAIN.equals(si.intent.getAction())
                                    && si.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                                iconCache.getTitleAndIcon(si, si.usingLowResIcon);
                                infoUpdated = true;
                            }

                            int oldDisabledFlags = si.isDisabled;
                            si.isDisabled = flagOp.apply(si.isDisabled);
                            if (si.isDisabled != oldDisabledFlags) {
                                shortcutUpdated = true;
                            }
                        }

                        if (infoUpdated || shortcutUpdated) {
                            updatedShortcuts.add(si);
                        }
                        if (infoUpdated) {
                            getModelWriter().updateItemInDatabase(si);
                        }
                    } else if (info instanceof LauncherAppWidgetInfo && mOp == OP_ADD) {
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

            bindUpdatedShortcuts(updatedShortcuts, removedShortcuts, mUser);
            if (!removedShortcuts.isEmpty()) {
                getModelWriter().deleteItemsFromDatabase(removedShortcuts);
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
            getModelWriter().deleteItemsFromDatabase(
                    ItemInfoMatcher.ofPackages(removedPackages, mUser));
            getModelWriter().deleteItemsFromDatabase(
                    ItemInfoMatcher.ofComponents(removedComponents, mUser));

            // Remove any queued items from the install queue
            InstallShortcutReceiver.removeFromInstallQueue(context, removedPackages, mUser);

            // Call the components-removed callback
            scheduleCallbackTask(new CallbackTask() {
                @Override
                public void execute(Callbacks callbacks) {
                    callbacks.bindWorkspaceComponentsRemoved(
                            removedPackages, removedComponents, mUser);
                }
            });
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

        // Notify launcher of widget update. From marshmallow onwards we use AppWidgetHost to
        // get widget update signals.
        if (!Utilities.ATLEAST_MARSHMALLOW &&
                (mOp == OP_ADD || mOp == OP_REMOVE || mOp == OP_UPDATE)) {
            scheduleCallbackTask(new CallbackTask() {
                @Override
                public void execute(Callbacks callbacks) {
                    callbacks.notifyWidgetProvidersChanged();
                }
            });
        } else if (Utilities.isAtLeastO() && mOp == OP_ADD) {
            // Load widgets for the new package.
            for (int i = 0; i < N; i++) {
                LauncherModel model = app.getModel();
                model.refreshAndBindWidgetsAndShortcuts(
                        model.getCallback(), false /* bindFirst */,
                        new PackageUserKey(packages[i], mUser) /* packageUser */);
            }
        }
    }
}

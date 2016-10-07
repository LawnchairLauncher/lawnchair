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

import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Parcelable;
import android.os.Process;
import android.os.SystemClock;
import android.os.Trace;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.MutableInt;
import android.util.Pair;

import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.LauncherActivityInfoCompat;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.PackageInstallerCompat;
import com.android.launcher3.compat.PackageInstallerCompat.PackageInstallInfo;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.config.ProviderConfig;
import com.android.launcher3.dynamicui.ExtractionUtils;
import com.android.launcher3.folder.Folder;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.model.GridSizeMigrationTask;
import com.android.launcher3.model.WidgetsModel;
import com.android.launcher3.provider.ImportDataTask;
import com.android.launcher3.provider.LauncherDbUtils;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.CursorIconInfo;
import com.android.launcher3.util.FlagOp;
import com.android.launcher3.util.GridOccupancy;
import com.android.launcher3.util.LongArrayMap;
import com.android.launcher3.util.ManagedProfileHeuristic;
import com.android.launcher3.util.MultiHashMap;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.StringFilter;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.util.ViewOnDrawExecutor;

import java.lang.ref.WeakReference;
import java.net.URISyntaxException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Maintains in-memory state of the Launcher. It is expected that there should be only one
 * LauncherModel object held in a static. Also provide APIs for updating the database state
 * for the Launcher.
 */
public class LauncherModel extends BroadcastReceiver
        implements LauncherAppsCompat.OnAppsChangedCallbackCompat {
    static final boolean DEBUG_LOADERS = false;
    private static final boolean DEBUG_RECEIVER = false;

    static final String TAG = "Launcher.Model";

    private static final int ITEMS_CHUNK = 6; // batch size for the workspace icons
    private static final long INVALID_SCREEN_ID = -1L;

    @Thunk final LauncherAppState mApp;
    @Thunk final Object mLock = new Object();
    @Thunk DeferredHandler mHandler = new DeferredHandler();
    @Thunk LoaderTask mLoaderTask;
    @Thunk boolean mIsLoaderTaskRunning;
    @Thunk boolean mHasLoaderCompletedOnce;

    @Thunk static final HandlerThread sWorkerThread = new HandlerThread("launcher-loader");
    static {
        sWorkerThread.start();
    }
    @Thunk static final Handler sWorker = new Handler(sWorkerThread.getLooper());

    // We start off with everything not loaded.  After that, we assume that
    // our monitoring of the package manager provides all updates and we never
    // need to do a requery.  These are only ever touched from the loader thread.
    private boolean mWorkspaceLoaded;
    private boolean mAllAppsLoaded;
    private boolean mDeepShortcutsLoaded;

    /**
     * Set of runnables to be called on the background thread after the workspace binding
     * is complete.
     */
    static final ArrayList<Runnable> mBindCompleteRunnables = new ArrayList<Runnable>();

    @Thunk WeakReference<Callbacks> mCallbacks;

    // < only access in worker thread >
    private final AllAppsList mBgAllAppsList;
    // Entire list of widgets.
    private final WidgetsModel mBgWidgetsModel;

    // Maps all launcher activities to the id's of their shortcuts (if they have any).
    private final MultiHashMap<ComponentKey, String> mBgDeepShortcutMap = new MultiHashMap<>();

    private boolean mHasShortcutHostPermission;
    // Runnable to check if the shortcuts permission has changed.
    private final Runnable mShortcutPermissionCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (mDeepShortcutsLoaded) {
                boolean hasShortcutHostPermission = mDeepShortcutManager.hasHostPermission();
                if (hasShortcutHostPermission != mHasShortcutHostPermission) {
                    mApp.reloadWorkspace();
                }
            }
        }
    };

    // The lock that must be acquired before referencing any static bg data structures.  Unlike
    // other locks, this one can generally be held long-term because we never expect any of these
    // static data structures to be referenced outside of the worker thread except on the first
    // load after configuration change.
    static final Object sBgLock = new Object();

    // sBgItemsIdMap maps *all* the ItemInfos (shortcuts, folders, and widgets) created by
    // LauncherModel to their ids
    static final LongArrayMap<ItemInfo> sBgItemsIdMap = new LongArrayMap<>();

    // sBgWorkspaceItems is passed to bindItems, which expects a list of all folders and shortcuts
    //       created by LauncherModel that are directly on the home screen (however, no widgets or
    //       shortcuts within folders).
    static final ArrayList<ItemInfo> sBgWorkspaceItems = new ArrayList<ItemInfo>();

    // sBgAppWidgets is all LauncherAppWidgetInfo created by LauncherModel. Passed to bindAppWidget()
    static final ArrayList<LauncherAppWidgetInfo> sBgAppWidgets =
        new ArrayList<LauncherAppWidgetInfo>();

    // sBgFolders is all FolderInfos created by LauncherModel. Passed to bindFolders()
    static final LongArrayMap<FolderInfo> sBgFolders = new LongArrayMap<>();

    // sBgWorkspaceScreens is the ordered set of workspace screens.
    static final ArrayList<Long> sBgWorkspaceScreens = new ArrayList<Long>();

    // sBgPinnedShortcutCounts is the ComponentKey representing a pinned shortcut to the number of
    // times it is pinned.
    static final Map<ShortcutKey, MutableInt> sBgPinnedShortcutCounts = new HashMap<>();

    // sPendingPackages is a set of packages which could be on sdcard and are not available yet
    static final HashMap<UserHandleCompat, HashSet<String>> sPendingPackages =
            new HashMap<UserHandleCompat, HashSet<String>>();

    // </ only access in worker thread >

    private IconCache mIconCache;
    private DeepShortcutManager mDeepShortcutManager;

    private final LauncherAppsCompat mLauncherApps;
    private final UserManagerCompat mUserManager;

    public interface Callbacks {
        public boolean setLoadOnResume();
        public int getCurrentWorkspaceScreen();
        public void clearPendingBinds();
        public void startBinding();
        public void bindItems(ArrayList<ItemInfo> shortcuts, int start, int end,
                              boolean forceAnimateIcons);
        public void bindScreens(ArrayList<Long> orderedScreenIds);
        public void finishFirstPageBind(ViewOnDrawExecutor executor);
        public void finishBindingItems();
        public void bindAppWidget(LauncherAppWidgetInfo info);
        public void bindAllApplications(ArrayList<AppInfo> apps);
        public void bindAppsAdded(ArrayList<Long> newScreens,
                                  ArrayList<ItemInfo> addNotAnimated,
                                  ArrayList<ItemInfo> addAnimated,
                                  ArrayList<AppInfo> addedApps);
        public void bindAppsUpdated(ArrayList<AppInfo> apps);
        public void bindShortcutsChanged(ArrayList<ShortcutInfo> updated,
                ArrayList<ShortcutInfo> removed, UserHandleCompat user);
        public void bindWidgetsRestored(ArrayList<LauncherAppWidgetInfo> widgets);
        public void bindRestoreItemsChange(HashSet<ItemInfo> updates);
        public void bindWorkspaceComponentsRemoved(
                HashSet<String> packageNames, HashSet<ComponentName> components,
                UserHandleCompat user);
        public void bindAppInfosRemoved(ArrayList<AppInfo> appInfos);
        public void notifyWidgetProvidersChanged();
        public void bindWidgetsModel(WidgetsModel model);
        public void onPageBoundSynchronously(int page);
        public void executeOnNextDraw(ViewOnDrawExecutor executor);
        public void bindDeepShortcutMap(MultiHashMap<ComponentKey, String> deepShortcutMap);
    }

    public interface ItemInfoFilter {
        public boolean filterItem(ItemInfo parent, ItemInfo info, ComponentName cn);
    }

    LauncherModel(LauncherAppState app, IconCache iconCache, AppFilter appFilter,
            DeepShortcutManager deepShortcutManager) {
        Context context = app.getContext();
        mApp = app;
        mBgAllAppsList = new AllAppsList(iconCache, appFilter);
        mBgWidgetsModel = new WidgetsModel(context, iconCache, appFilter);
        mIconCache = iconCache;
        mDeepShortcutManager = deepShortcutManager;

        mLauncherApps = LauncherAppsCompat.getInstance(context);
        mUserManager = UserManagerCompat.getInstance(context);
    }

    /** Runs the specified runnable immediately if called from the main thread, otherwise it is
     * posted on the main thread handler. */
    private void runOnMainThread(Runnable r) {
        if (sWorkerThread.getThreadId() == Process.myTid()) {
            // If we are on the worker thread, post onto the main handler
            mHandler.post(r);
        } else {
            r.run();
        }
    }

    /** Runs the specified runnable immediately if called from the worker thread, otherwise it is
     * posted on the worker thread handler. */
    private static void runOnWorkerThread(Runnable r) {
        if (sWorkerThread.getThreadId() == Process.myTid()) {
            r.run();
        } else {
            // If we are not on the worker thread, then post to the worker handler
            sWorker.post(r);
        }
    }

    public void setPackageState(final PackageInstallInfo installInfo) {
        Runnable updateRunnable = new Runnable() {

            @Override
            public void run() {
                synchronized (sBgLock) {
                    final HashSet<ItemInfo> updates = new HashSet<>();

                    if (installInfo.state == PackageInstallerCompat.STATUS_INSTALLED) {
                        // Ignore install success events as they are handled by Package add events.
                        return;
                    }

                    for (ItemInfo info : sBgItemsIdMap) {
                        if (info instanceof ShortcutInfo) {
                            ShortcutInfo si = (ShortcutInfo) info;
                            ComponentName cn = si.getTargetComponent();
                            if (si.isPromise() && (cn != null)
                                    && installInfo.packageName.equals(cn.getPackageName())) {
                                si.setInstallProgress(installInfo.progress);

                                if (installInfo.state == PackageInstallerCompat.STATUS_FAILED) {
                                    // Mark this info as broken.
                                    si.status &= ~ShortcutInfo.FLAG_INSTALL_SESSION_ACTIVE;
                                }
                                updates.add(si);
                            }
                        }
                    }

                    for (LauncherAppWidgetInfo widget : sBgAppWidgets) {
                        if (widget.providerName.getPackageName().equals(installInfo.packageName)) {
                            widget.installProgress = installInfo.progress;
                            updates.add(widget);
                        }
                    }

                    if (!updates.isEmpty()) {
                        // Push changes to the callback.
                        Runnable r = new Runnable() {
                            public void run() {
                                Callbacks callbacks = getCallback();
                                if (callbacks != null) {
                                    callbacks.bindRestoreItemsChange(updates);
                                }
                            }
                        };
                        mHandler.post(r);
                    }
                }
            }
        };
        runOnWorkerThread(updateRunnable);
    }

    /**
     * Updates the icons and label of all pending icons for the provided package name.
     */
    public void updateSessionDisplayInfo(final String packageName) {
        Runnable updateRunnable = new Runnable() {

            @Override
            public void run() {
                synchronized (sBgLock) {
                    ArrayList<ShortcutInfo> updates = new ArrayList<>();
                    UserHandleCompat user = UserHandleCompat.myUserHandle();

                    for (ItemInfo info : sBgItemsIdMap) {
                        if (info instanceof ShortcutInfo) {
                            ShortcutInfo si = (ShortcutInfo) info;
                            ComponentName cn = si.getTargetComponent();
                            if (si.isPromise() && (cn != null)
                                    && packageName.equals(cn.getPackageName())) {
                                if (si.hasStatusFlag(ShortcutInfo.FLAG_AUTOINTALL_ICON)) {
                                    // For auto install apps update the icon as well as label.
                                    mIconCache.getTitleAndIcon(si,
                                            si.promisedIntent, user,
                                            si.shouldUseLowResIcon());
                                } else {
                                    // Only update the icon for restored apps.
                                    si.updateIcon(mIconCache);
                                }
                                updates.add(si);
                            }
                        }
                    }

                    bindUpdatedShortcuts(updates, user);
                }
            }
        };
        runOnWorkerThread(updateRunnable);
    }

    public void addAppsToAllApps(final Context ctx, final ArrayList<AppInfo> allAppsApps) {
        final Callbacks callbacks = getCallback();

        if (allAppsApps == null) {
            throw new RuntimeException("allAppsApps must not be null");
        }
        if (allAppsApps.isEmpty()) {
            return;
        }

        // Process the newly added applications and add them to the database first
        Runnable r = new Runnable() {
            public void run() {
                runOnMainThread(new Runnable() {
                    public void run() {
                        Callbacks cb = getCallback();
                        if (callbacks == cb && cb != null) {
                            callbacks.bindAppsAdded(null, null, null, allAppsApps);
                        }
                    }
                });
            }
        };
        runOnWorkerThread(r);
    }

    private static boolean findNextAvailableIconSpaceInScreen(ArrayList<ItemInfo> occupiedPos,
            int[] xy, int spanX, int spanY) {
        LauncherAppState app = LauncherAppState.getInstance();
        InvariantDeviceProfile profile = app.getInvariantDeviceProfile();

        GridOccupancy occupied = new GridOccupancy(profile.numColumns, profile.numRows);
        if (occupiedPos != null) {
            for (ItemInfo r : occupiedPos) {
                occupied.markCells(r, true);
            }
        }
        return occupied.findVacantCell(xy, spanX, spanY);
    }

    /**
     * Find a position on the screen for the given size or adds a new screen.
     * @return screenId and the coordinates for the item.
     */
    @Thunk Pair<Long, int[]> findSpaceForItem(
            Context context,
            ArrayList<Long> workspaceScreens,
            ArrayList<Long> addedWorkspaceScreensFinal,
            int spanX, int spanY) {
        LongSparseArray<ArrayList<ItemInfo>> screenItems = new LongSparseArray<>();

        // Use sBgItemsIdMap as all the items are already loaded.
        assertWorkspaceLoaded();
        synchronized (sBgLock) {
            for (ItemInfo info : sBgItemsIdMap) {
                if (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                    ArrayList<ItemInfo> items = screenItems.get(info.screenId);
                    if (items == null) {
                        items = new ArrayList<>();
                        screenItems.put(info.screenId, items);
                    }
                    items.add(info);
                }
            }
        }

        // Find appropriate space for the item.
        long screenId = 0;
        int[] cordinates = new int[2];
        boolean found = false;

        int screenCount = workspaceScreens.size();
        // First check the preferred screen.
        int preferredScreenIndex = workspaceScreens.isEmpty() ? 0 : 1;
        if (preferredScreenIndex < screenCount) {
            screenId = workspaceScreens.get(preferredScreenIndex);
            found = findNextAvailableIconSpaceInScreen(
                    screenItems.get(screenId), cordinates, spanX, spanY);
        }

        if (!found) {
            // Search on any of the screens starting from the first screen.
            for (int screen = 1; screen < screenCount; screen++) {
                screenId = workspaceScreens.get(screen);
                if (findNextAvailableIconSpaceInScreen(
                        screenItems.get(screenId), cordinates, spanX, spanY)) {
                    // We found a space for it
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            // Still no position found. Add a new screen to the end.
            screenId = LauncherSettings.Settings.call(context.getContentResolver(),
                    LauncherSettings.Settings.METHOD_NEW_SCREEN_ID)
                    .getLong(LauncherSettings.Settings.EXTRA_VALUE);

            // Save the screen id for binding in the workspace
            workspaceScreens.add(screenId);
            addedWorkspaceScreensFinal.add(screenId);

            // If we still can't find an empty space, then God help us all!!!
            if (!findNextAvailableIconSpaceInScreen(
                    screenItems.get(screenId), cordinates, spanX, spanY)) {
                throw new RuntimeException("Can't find space to add the item");
            }
        }
        return Pair.create(screenId, cordinates);
    }

    /**
     * Adds the provided items to the workspace.
     */
    public void addAndBindAddedWorkspaceItems(final Context context,
            final ArrayList<? extends ItemInfo> workspaceApps) {
        final Callbacks callbacks = getCallback();
        if (workspaceApps.isEmpty()) {
            return;
        }
        // Process the newly added applications and add them to the database first
        Runnable r = new Runnable() {
            public void run() {
                final ArrayList<ItemInfo> addedShortcutsFinal = new ArrayList<ItemInfo>();
                final ArrayList<Long> addedWorkspaceScreensFinal = new ArrayList<Long>();

                // Get the list of workspace screens.  We need to append to this list and
                // can not use sBgWorkspaceScreens because loadWorkspace() may not have been
                // called.
                ArrayList<Long> workspaceScreens = loadWorkspaceScreensDb(context);
                synchronized(sBgLock) {
                    for (ItemInfo item : workspaceApps) {
                        if (item instanceof ShortcutInfo) {
                            // Short-circuit this logic if the icon exists somewhere on the workspace
                            if (shortcutExists(context, item.getIntent(), item.user)) {
                                continue;
                            }
                        }

                        // Find appropriate space for the item.
                        Pair<Long, int[]> coords = findSpaceForItem(context,
                                workspaceScreens, addedWorkspaceScreensFinal, 1, 1);
                        long screenId = coords.first;
                        int[] cordinates = coords.second;

                        ItemInfo itemInfo;
                        if (item instanceof ShortcutInfo || item instanceof FolderInfo) {
                            itemInfo = item;
                        } else if (item instanceof AppInfo) {
                            itemInfo = ((AppInfo) item).makeShortcut();
                        } else {
                            throw new RuntimeException("Unexpected info type");
                        }

                        // Add the shortcut to the db
                        addItemToDatabase(context, itemInfo,
                                LauncherSettings.Favorites.CONTAINER_DESKTOP,
                                screenId, cordinates[0], cordinates[1]);
                        // Save the ShortcutInfo for binding in the workspace
                        addedShortcutsFinal.add(itemInfo);
                    }
                }

                // Update the workspace screens
                updateWorkspaceScreenOrder(context, workspaceScreens);

                if (!addedShortcutsFinal.isEmpty()) {
                    runOnMainThread(new Runnable() {
                        public void run() {
                            Callbacks cb = getCallback();
                            if (callbacks == cb && cb != null) {
                                final ArrayList<ItemInfo> addAnimated = new ArrayList<ItemInfo>();
                                final ArrayList<ItemInfo> addNotAnimated = new ArrayList<ItemInfo>();
                                if (!addedShortcutsFinal.isEmpty()) {
                                    ItemInfo info = addedShortcutsFinal.get(addedShortcutsFinal.size() - 1);
                                    long lastScreenId = info.screenId;
                                    for (ItemInfo i : addedShortcutsFinal) {
                                        if (i.screenId == lastScreenId) {
                                            addAnimated.add(i);
                                        } else {
                                            addNotAnimated.add(i);
                                        }
                                    }
                                }
                                callbacks.bindAppsAdded(addedWorkspaceScreensFinal,
                                        addNotAnimated, addAnimated, null);
                            }
                        }
                    });
                }
            }
        };
        runOnWorkerThread(r);
    }

    /**
     * Adds an item to the DB if it was not created previously, or move it to a new
     * <container, screen, cellX, cellY>
     */
    public static void addOrMoveItemInDatabase(Context context, ItemInfo item, long container,
            long screenId, int cellX, int cellY) {
        if (item.container == ItemInfo.NO_ID) {
            // From all apps
            addItemToDatabase(context, item, container, screenId, cellX, cellY);
        } else {
            // From somewhere else
            moveItemInDatabase(context, item, container, screenId, cellX, cellY);
        }
    }

    static void checkItemInfoLocked(
            final long itemId, final ItemInfo item, StackTraceElement[] stackTrace) {
        ItemInfo modelItem = sBgItemsIdMap.get(itemId);
        if (modelItem != null && item != modelItem) {
            // check all the data is consistent
            if (modelItem instanceof ShortcutInfo && item instanceof ShortcutInfo) {
                ShortcutInfo modelShortcut = (ShortcutInfo) modelItem;
                ShortcutInfo shortcut = (ShortcutInfo) item;
                if (modelShortcut.title.toString().equals(shortcut.title.toString()) &&
                        modelShortcut.intent.filterEquals(shortcut.intent) &&
                        modelShortcut.id == shortcut.id &&
                        modelShortcut.itemType == shortcut.itemType &&
                        modelShortcut.container == shortcut.container &&
                        modelShortcut.screenId == shortcut.screenId &&
                        modelShortcut.cellX == shortcut.cellX &&
                        modelShortcut.cellY == shortcut.cellY &&
                        modelShortcut.spanX == shortcut.spanX &&
                        modelShortcut.spanY == shortcut.spanY) {
                    // For all intents and purposes, this is the same object
                    return;
                }
            }

            // the modelItem needs to match up perfectly with item if our model is
            // to be consistent with the database-- for now, just require
            // modelItem == item or the equality check above
            String msg = "item: " + ((item != null) ? item.toString() : "null") +
                    "modelItem: " +
                    ((modelItem != null) ? modelItem.toString() : "null") +
                    "Error: ItemInfo passed to checkItemInfo doesn't match original";
            RuntimeException e = new RuntimeException(msg);
            if (stackTrace != null) {
                e.setStackTrace(stackTrace);
            }
            throw e;
        }
    }

    static void checkItemInfo(final ItemInfo item) {
        final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        final long itemId = item.id;
        Runnable r = new Runnable() {
            public void run() {
                synchronized (sBgLock) {
                    checkItemInfoLocked(itemId, item, stackTrace);
                }
            }
        };
        runOnWorkerThread(r);
    }

    static void updateItemInDatabaseHelper(Context context, final ContentValues values,
            final ItemInfo item, final String callingFunction) {
        final long itemId = item.id;
        final Uri uri = LauncherSettings.Favorites.getContentUri(itemId);
        final ContentResolver cr = context.getContentResolver();

        final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        Runnable r = new Runnable() {
            public void run() {
                cr.update(uri, values, null, null);
                updateItemArrays(item, itemId, stackTrace);
            }
        };
        runOnWorkerThread(r);
    }

    static void updateItemsInDatabaseHelper(Context context, final ArrayList<ContentValues> valuesList,
            final ArrayList<ItemInfo> items, final String callingFunction) {
        final ContentResolver cr = context.getContentResolver();

        final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        Runnable r = new Runnable() {
            public void run() {
                ArrayList<ContentProviderOperation> ops =
                        new ArrayList<ContentProviderOperation>();
                int count = items.size();
                for (int i = 0; i < count; i++) {
                    ItemInfo item = items.get(i);
                    final long itemId = item.id;
                    final Uri uri = LauncherSettings.Favorites.getContentUri(itemId);
                    ContentValues values = valuesList.get(i);

                    ops.add(ContentProviderOperation.newUpdate(uri).withValues(values).build());
                    updateItemArrays(item, itemId, stackTrace);

                }
                try {
                    cr.applyBatch(LauncherProvider.AUTHORITY, ops);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        runOnWorkerThread(r);
    }

    static void updateItemArrays(ItemInfo item, long itemId, StackTraceElement[] stackTrace) {
        // Lock on mBgLock *after* the db operation
        synchronized (sBgLock) {
            checkItemInfoLocked(itemId, item, stackTrace);

            if (item.container != LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                    item.container != LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                // Item is in a folder, make sure this folder exists
                if (!sBgFolders.containsKey(item.container)) {
                    // An items container is being set to a that of an item which is not in
                    // the list of Folders.
                    String msg = "item: " + item + " container being set to: " +
                            item.container + ", not in the list of folders";
                    Log.e(TAG, msg);
                }
            }

            // Items are added/removed from the corresponding FolderInfo elsewhere, such
            // as in Workspace.onDrop. Here, we just add/remove them from the list of items
            // that are on the desktop, as appropriate
            ItemInfo modelItem = sBgItemsIdMap.get(itemId);
            if (modelItem != null &&
                    (modelItem.container == LauncherSettings.Favorites.CONTAINER_DESKTOP ||
                     modelItem.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT)) {
                switch (modelItem.itemType) {
                    case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                    case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                    case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                    case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                        if (!sBgWorkspaceItems.contains(modelItem)) {
                            sBgWorkspaceItems.add(modelItem);
                        }
                        break;
                    default:
                        break;
                }
            } else {
                sBgWorkspaceItems.remove(modelItem);
            }
        }
    }

    /**
     * Move an item in the DB to a new <container, screen, cellX, cellY>
     */
    public static void moveItemInDatabase(Context context, final ItemInfo item, final long container,
            final long screenId, final int cellX, final int cellY) {
        item.container = container;
        item.cellX = cellX;
        item.cellY = cellY;

        // We store hotseat items in canonical form which is this orientation invariant position
        // in the hotseat
        if (context instanceof Launcher && screenId < 0 &&
                container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            item.screenId = Launcher.getLauncher(context).getHotseat()
                    .getOrderInHotseat(cellX, cellY);
        } else {
            item.screenId = screenId;
        }

        final ContentValues values = new ContentValues();
        values.put(LauncherSettings.Favorites.CONTAINER, item.container);
        values.put(LauncherSettings.Favorites.CELLX, item.cellX);
        values.put(LauncherSettings.Favorites.CELLY, item.cellY);
        values.put(LauncherSettings.Favorites.RANK, item.rank);
        values.put(LauncherSettings.Favorites.SCREEN, item.screenId);

        updateItemInDatabaseHelper(context, values, item, "moveItemInDatabase");
    }

    /**
     * Move items in the DB to a new <container, screen, cellX, cellY>. We assume that the
     * cellX, cellY have already been updated on the ItemInfos.
     */
    public static void moveItemsInDatabase(Context context, final ArrayList<ItemInfo> items,
            final long container, final int screen) {

        ArrayList<ContentValues> contentValues = new ArrayList<ContentValues>();
        int count = items.size();

        for (int i = 0; i < count; i++) {
            ItemInfo item = items.get(i);
            item.container = container;

            // We store hotseat items in canonical form which is this orientation invariant position
            // in the hotseat
            if (context instanceof Launcher && screen < 0 &&
                    container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                item.screenId = Launcher.getLauncher(context).getHotseat().getOrderInHotseat(item.cellX,
                        item.cellY);
            } else {
                item.screenId = screen;
            }

            final ContentValues values = new ContentValues();
            values.put(LauncherSettings.Favorites.CONTAINER, item.container);
            values.put(LauncherSettings.Favorites.CELLX, item.cellX);
            values.put(LauncherSettings.Favorites.CELLY, item.cellY);
            values.put(LauncherSettings.Favorites.RANK, item.rank);
            values.put(LauncherSettings.Favorites.SCREEN, item.screenId);

            contentValues.add(values);
        }
        updateItemsInDatabaseHelper(context, contentValues, items, "moveItemInDatabase");
    }

    /**
     * Move and/or resize item in the DB to a new <container, screen, cellX, cellY, spanX, spanY>
     */
    static void modifyItemInDatabase(Context context, final ItemInfo item, final long container,
            final long screenId, final int cellX, final int cellY, final int spanX, final int spanY) {
        item.container = container;
        item.cellX = cellX;
        item.cellY = cellY;
        item.spanX = spanX;
        item.spanY = spanY;

        // We store hotseat items in canonical form which is this orientation invariant position
        // in the hotseat
        if (context instanceof Launcher && screenId < 0 &&
                container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            item.screenId = Launcher.getLauncher(context).getHotseat()
                    .getOrderInHotseat(cellX, cellY);
        } else {
            item.screenId = screenId;
        }

        final ContentValues values = new ContentValues();
        values.put(LauncherSettings.Favorites.CONTAINER, item.container);
        values.put(LauncherSettings.Favorites.CELLX, item.cellX);
        values.put(LauncherSettings.Favorites.CELLY, item.cellY);
        values.put(LauncherSettings.Favorites.RANK, item.rank);
        values.put(LauncherSettings.Favorites.SPANX, item.spanX);
        values.put(LauncherSettings.Favorites.SPANY, item.spanY);
        values.put(LauncherSettings.Favorites.SCREEN, item.screenId);

        updateItemInDatabaseHelper(context, values, item, "modifyItemInDatabase");
    }

    /**
     * Update an item to the database in a specified container.
     */
    public static void updateItemInDatabase(Context context, final ItemInfo item) {
        final ContentValues values = new ContentValues();
        item.onAddToDatabase(context, values);
        updateItemInDatabaseHelper(context, values, item, "updateItemInDatabase");
    }

    private void assertWorkspaceLoaded() {
        if (ProviderConfig.IS_DOGFOOD_BUILD) {
            synchronized (mLock) {
                if (!mHasLoaderCompletedOnce ||
                        (mLoaderTask != null && mLoaderTask.mIsLoadingAndBindingWorkspace)) {
                    throw new RuntimeException("Trying to add shortcut while loader is running");
                }
            }
        }
    }

    /**
     * Returns true if the shortcuts already exists on the workspace. This must be called after
     * the workspace has been loaded. We identify a shortcut by its intent.
     */
    @Thunk boolean shortcutExists(Context context, Intent intent, UserHandleCompat user) {
        assertWorkspaceLoaded();
        final String intentWithPkg, intentWithoutPkg;
        if (intent.getComponent() != null) {
            // If component is not null, an intent with null package will produce
            // the same result and should also be a match.
            String packageName = intent.getComponent().getPackageName();
            if (intent.getPackage() != null) {
                intentWithPkg = intent.toUri(0);
                intentWithoutPkg = new Intent(intent).setPackage(null).toUri(0);
            } else {
                intentWithPkg = new Intent(intent).setPackage(packageName).toUri(0);
                intentWithoutPkg = intent.toUri(0);
            }
        } else {
            intentWithPkg = intent.toUri(0);
            intentWithoutPkg = intent.toUri(0);
        }

        synchronized (sBgLock) {
            for (ItemInfo item : sBgItemsIdMap) {
                if (item instanceof ShortcutInfo) {
                    ShortcutInfo info = (ShortcutInfo) item;
                    Intent targetIntent = info.promisedIntent == null
                            ? info.intent : info.promisedIntent;
                    if (targetIntent != null && info.user.equals(user)) {
                        Intent copyIntent = new Intent(targetIntent);
                        copyIntent.setSourceBounds(intent.getSourceBounds());
                        String s = copyIntent.toUri(0);
                        if (intentWithPkg.equals(s) || intentWithoutPkg.equals(s)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Add an item to the database in a specified container. Sets the container, screen, cellX and
     * cellY fields of the item. Also assigns an ID to the item.
     */
    public static void addItemToDatabase(Context context, final ItemInfo item, final long container,
            final long screenId, final int cellX, final int cellY) {
        item.container = container;
        item.cellX = cellX;
        item.cellY = cellY;
        // We store hotseat items in canonical form which is this orientation invariant position
        // in the hotseat
        if (context instanceof Launcher && screenId < 0 &&
                container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            item.screenId = Launcher.getLauncher(context).getHotseat()
                    .getOrderInHotseat(cellX, cellY);
        } else {
            item.screenId = screenId;
        }

        final ContentValues values = new ContentValues();
        final ContentResolver cr = context.getContentResolver();
        item.onAddToDatabase(context, values);

        item.id = LauncherSettings.Settings.call(cr, LauncherSettings.Settings.METHOD_NEW_ITEM_ID)
                .getLong(LauncherSettings.Settings.EXTRA_VALUE);

        values.put(LauncherSettings.Favorites._ID, item.id);

        final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        Runnable r = new Runnable() {
            public void run() {
                cr.insert(LauncherSettings.Favorites.CONTENT_URI, values);

                // Lock on mBgLock *after* the db operation
                synchronized (sBgLock) {
                    checkItemInfoLocked(item.id, item, stackTrace);
                    sBgItemsIdMap.put(item.id, item);
                    switch (item.itemType) {
                        case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                            sBgFolders.put(item.id, (FolderInfo) item);
                            // Fall through
                        case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                        case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                        case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                            if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP ||
                                    item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                                sBgWorkspaceItems.add(item);
                            } else {
                                if (!sBgFolders.containsKey(item.container)) {
                                    // Adding an item to a folder that doesn't exist.
                                    String msg = "adding item: " + item + " to a folder that " +
                                            " doesn't exist";
                                    Log.e(TAG, msg);
                                }
                            }
                            if (item.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                                incrementPinnedShortcutCount(
                                        ShortcutKey.fromShortcutInfo((ShortcutInfo) item),
                                        true /* shouldPin */);
                            }
                            break;
                        case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                            sBgAppWidgets.add((LauncherAppWidgetInfo) item);
                            break;
                    }
                }
            }
        };
        runOnWorkerThread(r);
    }

    private static ArrayList<ItemInfo> getItemsByPackageName(
            final String pn, final UserHandleCompat user) {
        ItemInfoFilter filter  = new ItemInfoFilter() {
            @Override
            public boolean filterItem(ItemInfo parent, ItemInfo info, ComponentName cn) {
                return cn.getPackageName().equals(pn) && info.user.equals(user);
            }
        };
        return filterItemInfos(sBgItemsIdMap, filter);
    }

    /**
     * Removes all the items from the database corresponding to the specified package.
     */
    static void deletePackageFromDatabase(Context context, final String pn,
            final UserHandleCompat user) {
        deleteItemsFromDatabase(context, getItemsByPackageName(pn, user));
    }

    /**
     * Removes the specified item from the database
     */
    public static void deleteItemFromDatabase(Context context, final ItemInfo item) {
        ArrayList<ItemInfo> items = new ArrayList<ItemInfo>();
        items.add(item);
        deleteItemsFromDatabase(context, items);
    }

    /**
     * Removes the specified items from the database
     */
    static void deleteItemsFromDatabase(Context context, final ArrayList<? extends ItemInfo> items) {
        final ContentResolver cr = context.getContentResolver();
        Runnable r = new Runnable() {
            public void run() {
                for (ItemInfo item : items) {
                    final Uri uri = LauncherSettings.Favorites.getContentUri(item.id);
                    cr.delete(uri, null, null);

                    // Lock on mBgLock *after* the db operation
                    synchronized (sBgLock) {
                        switch (item.itemType) {
                            case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                                sBgFolders.remove(item.id);
                                for (ItemInfo info: sBgItemsIdMap) {
                                    if (info.container == item.id) {
                                        // We are deleting a folder which still contains items that
                                        // think they are contained by that folder.
                                        String msg = "deleting a folder (" + item + ") which still " +
                                                "contains items (" + info + ")";
                                        Log.e(TAG, msg);
                                    }
                                }
                                sBgWorkspaceItems.remove(item);
                                break;
                            case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                                decrementPinnedShortcutCount(ShortcutKey.fromShortcutInfo(
                                        (ShortcutInfo) item));
                                // Fall through.
                            case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                            case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                                sBgWorkspaceItems.remove(item);
                                break;
                            case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                                sBgAppWidgets.remove((LauncherAppWidgetInfo) item);
                                break;
                        }
                        sBgItemsIdMap.remove(item.id);
                    }
                }
            }
        };
        runOnWorkerThread(r);
    }

    /**
     * Decrement the count for the given pinned shortcut, unpinning it if the count becomes 0.
     */
    private static void decrementPinnedShortcutCount(final ShortcutKey pinnedShortcut) {
        synchronized (sBgLock) {
            MutableInt count = sBgPinnedShortcutCounts.get(pinnedShortcut);
            if (count == null || --count.value == 0) {
                LauncherAppState.getInstance().getShortcutManager().unpinShortcut(pinnedShortcut);
            }
        }
    }

    /**
     * Increment the count for the given shortcut, pinning it if the count becomes 1.
     *
     * As an optimization, the caller can pass shouldPin == false to avoid
     * unnecessary RPC's if the shortcut is already pinned.
     */
    private static void incrementPinnedShortcutCount(ShortcutKey pinnedShortcut, boolean shouldPin) {
        synchronized (sBgLock) {
            MutableInt count = sBgPinnedShortcutCounts.get(pinnedShortcut);
            if (count == null) {
                count = new MutableInt(1);
                sBgPinnedShortcutCounts.put(pinnedShortcut, count);
            } else {
                count.value++;
            }
            if (shouldPin && count.value == 1) {
                LauncherAppState.getInstance().getShortcutManager().pinShortcut(pinnedShortcut);
            }
        }
    }

    /**
     * Update the order of the workspace screens in the database. The array list contains
     * a list of screen ids in the order that they should appear.
     */
    public void updateWorkspaceScreenOrder(Context context, final ArrayList<Long> screens) {
        final ArrayList<Long> screensCopy = new ArrayList<Long>(screens);
        final ContentResolver cr = context.getContentResolver();
        final Uri uri = LauncherSettings.WorkspaceScreens.CONTENT_URI;

        // Remove any negative screen ids -- these aren't persisted
        Iterator<Long> iter = screensCopy.iterator();
        while (iter.hasNext()) {
            long id = iter.next();
            if (id < 0) {
                iter.remove();
            }
        }

        Runnable r = new Runnable() {
            @Override
            public void run() {
                ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
                // Clear the table
                ops.add(ContentProviderOperation.newDelete(uri).build());
                int count = screensCopy.size();
                for (int i = 0; i < count; i++) {
                    ContentValues v = new ContentValues();
                    long screenId = screensCopy.get(i);
                    v.put(LauncherSettings.WorkspaceScreens._ID, screenId);
                    v.put(LauncherSettings.WorkspaceScreens.SCREEN_RANK, i);
                    ops.add(ContentProviderOperation.newInsert(uri).withValues(v).build());
                }

                try {
                    cr.applyBatch(LauncherProvider.AUTHORITY, ops);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }

                synchronized (sBgLock) {
                    sBgWorkspaceScreens.clear();
                    sBgWorkspaceScreens.addAll(screensCopy);
                }
            }
        };
        runOnWorkerThread(r);
    }

    /**
     * Remove the specified folder and all its contents from the database.
     */
    public static void deleteFolderAndContentsFromDatabase(Context context, final FolderInfo info) {
        final ContentResolver cr = context.getContentResolver();

        Runnable r = new Runnable() {
            public void run() {
                cr.delete(LauncherSettings.Favorites.getContentUri(info.id), null, null);
                // Lock on mBgLock *after* the db operation
                synchronized (sBgLock) {
                    sBgItemsIdMap.remove(info.id);
                    sBgFolders.remove(info.id);
                    sBgWorkspaceItems.remove(info);
                }

                cr.delete(LauncherSettings.Favorites.CONTENT_URI,
                        LauncherSettings.Favorites.CONTAINER + "=" + info.id, null);
                // Lock on mBgLock *after* the db operation
                synchronized (sBgLock) {
                    for (ItemInfo childInfo : info.contents) {
                        sBgItemsIdMap.remove(childInfo.id);
                    }
                }
            }
        };
        runOnWorkerThread(r);
    }

    /**
     * Set this as the current Launcher activity object for the loader.
     */
    public void initialize(Callbacks callbacks) {
        synchronized (mLock) {
            Preconditions.assertUIThread();
            // Remove any queued UI runnables
            mHandler.cancelAll();
            mCallbacks = new WeakReference<>(callbacks);
        }
    }

    @Override
    public void onPackageChanged(String packageName, UserHandleCompat user) {
        int op = PackageUpdatedTask.OP_UPDATE;
        enqueueItemUpdatedTask(new PackageUpdatedTask(op, new String[] { packageName },
                user));
    }

    @Override
    public void onPackageRemoved(String packageName, UserHandleCompat user) {
        int op = PackageUpdatedTask.OP_REMOVE;
        enqueueItemUpdatedTask(new PackageUpdatedTask(op, new String[] { packageName },
                user));
    }

    @Override
    public void onPackageAdded(String packageName, UserHandleCompat user) {
        int op = PackageUpdatedTask.OP_ADD;
        enqueueItemUpdatedTask(new PackageUpdatedTask(op, new String[] { packageName },
                user));
    }

    @Override
    public void onPackagesAvailable(String[] packageNames, UserHandleCompat user,
            boolean replacing) {
        enqueueItemUpdatedTask(
                new PackageUpdatedTask(PackageUpdatedTask.OP_UPDATE, packageNames, user));
    }

    @Override
    public void onPackagesUnavailable(String[] packageNames, UserHandleCompat user,
            boolean replacing) {
        if (!replacing) {
            enqueueItemUpdatedTask(new PackageUpdatedTask(
                    PackageUpdatedTask.OP_UNAVAILABLE, packageNames,
                    user));
        }
    }

    @Override
    public void onPackagesSuspended(String[] packageNames, UserHandleCompat user) {
        enqueueItemUpdatedTask(new PackageUpdatedTask(
                PackageUpdatedTask.OP_SUSPEND, packageNames,
                user));
    }

    @Override
    public void onPackagesUnsuspended(String[] packageNames, UserHandleCompat user) {
        enqueueItemUpdatedTask(new PackageUpdatedTask(
                PackageUpdatedTask.OP_UNSUSPEND, packageNames,
                user));
    }

    @Override
    public void onShortcutsChanged(String packageName, List<ShortcutInfoCompat> shortcuts,
            UserHandleCompat user) {
        enqueueItemUpdatedTask(new ShortcutsChangedTask(packageName, shortcuts, user, true));
    }

    public void updatePinnedShortcuts(String packageName, List<ShortcutInfoCompat> shortcuts,
            UserHandleCompat user) {
        enqueueItemUpdatedTask(new ShortcutsChangedTask(packageName, shortcuts, user, false));
    }

    /**
     * Call from the handler for ACTION_PACKAGE_ADDED, ACTION_PACKAGE_REMOVED and
     * ACTION_PACKAGE_CHANGED.
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (DEBUG_RECEIVER) Log.d(TAG, "onReceive intent=" + intent);

        final String action = intent.getAction();
        if (Intent.ACTION_LOCALE_CHANGED.equals(action)) {
            // If we have changed locale we need to clear out the labels in all apps/workspace.
            forceReload();
        } else if (Intent.ACTION_MANAGED_PROFILE_ADDED.equals(action)
                || Intent.ACTION_MANAGED_PROFILE_REMOVED.equals(action)) {
            UserManagerCompat.getInstance(context).enableAndResetCache();
            forceReload();
        } else if (Intent.ACTION_MANAGED_PROFILE_AVAILABLE.equals(action) ||
                Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE.equals(action) ||
                Intent.ACTION_MANAGED_PROFILE_UNLOCKED.equals(action)) {
            UserHandleCompat user = UserHandleCompat.fromIntent(intent);
            if (user != null) {
                if (Intent.ACTION_MANAGED_PROFILE_AVAILABLE.equals(action) ||
                        Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE.equals(action)) {
                    enqueueItemUpdatedTask(new PackageUpdatedTask(
                            PackageUpdatedTask.OP_USER_AVAILABILITY_CHANGE,
                            new String[0], user));
                }

                // ACTION_MANAGED_PROFILE_UNAVAILABLE sends the profile back to locked mode, so
                // we need to run the state change task again.
                if (Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE.equals(action) ||
                        Intent.ACTION_MANAGED_PROFILE_UNLOCKED.equals(action)) {
                    enqueueItemUpdatedTask(new UserLockStateChangedTask(user));
                }
            }
        } else if (Intent.ACTION_WALLPAPER_CHANGED.equals(action)) {
            ExtractionUtils.startColorExtractionServiceIfNecessary(context);
        }
    }

    void forceReload() {
        resetLoadedState(true, true);

        // Do this here because if the launcher activity is running it will be restarted.
        // If it's not running startLoaderFromBackground will merely tell it that it needs
        // to reload.
        startLoaderFromBackground();
    }

    public void resetLoadedState(boolean resetAllAppsLoaded, boolean resetWorkspaceLoaded) {
        synchronized (mLock) {
            // Stop any existing loaders first, so they don't set mAllAppsLoaded or
            // mWorkspaceLoaded to true later
            stopLoaderLocked();
            if (resetAllAppsLoaded) mAllAppsLoaded = false;
            if (resetWorkspaceLoaded) mWorkspaceLoaded = false;
            // Always reset deep shortcuts loaded.
            // TODO: why?
            mDeepShortcutsLoaded = false;
        }
    }

    /**
     * When the launcher is in the background, it's possible for it to miss paired
     * configuration changes.  So whenever we trigger the loader from the background
     * tell the launcher that it needs to re-run the loader when it comes back instead
     * of doing it now.
     */
    public void startLoaderFromBackground() {
        Callbacks callbacks = getCallback();
        if (callbacks != null) {
            // Only actually run the loader if they're not paused.
            if (!callbacks.setLoadOnResume()) {
                startLoader(callbacks.getCurrentWorkspaceScreen());
            }
        }
    }

    /**
     * If there is already a loader task running, tell it to stop.
     */
    private void stopLoaderLocked() {
        LoaderTask oldTask = mLoaderTask;
        if (oldTask != null) {
            oldTask.stopLocked();
        }
    }

    public boolean isCurrentCallbacks(Callbacks callbacks) {
        return (mCallbacks != null && mCallbacks.get() == callbacks);
    }

    /**
     * Starts the loader. Tries to bind {@params synchronousBindPage} synchronously if possible.
     * @return true if the page could be bound synchronously.
     */
    public boolean startLoader(int synchronousBindPage) {
        // Enable queue before starting loader. It will get disabled in Launcher#finishBindingItems
        InstallShortcutReceiver.enableInstallQueue();
        synchronized (mLock) {
            // Don't bother to start the thread if we know it's not going to do anything
            if (mCallbacks != null && mCallbacks.get() != null) {
                final Callbacks oldCallbacks = mCallbacks.get();
                // Clear any pending bind-runnables from the synchronized load process.
                runOnMainThread(new Runnable() {
                    public void run() {
                        oldCallbacks.clearPendingBinds();
                    }
                });

                // If there is already one running, tell it to stop.
                stopLoaderLocked();
                mLoaderTask = new LoaderTask(mApp.getContext(), synchronousBindPage);
                // TODO: mDeepShortcutsLoaded does not need to be true for synchronous bind.
                if (synchronousBindPage != PagedView.INVALID_RESTORE_PAGE && mAllAppsLoaded
                        && mWorkspaceLoaded && mDeepShortcutsLoaded && !mIsLoaderTaskRunning) {
                    mLoaderTask.runBindSynchronousPage(synchronousBindPage);
                    return true;
                } else {
                    sWorkerThread.setPriority(Thread.NORM_PRIORITY);
                    sWorker.post(mLoaderTask);
                }
            }
        }
        return false;
    }

    public void stopLoader() {
        synchronized (mLock) {
            if (mLoaderTask != null) {
                mLoaderTask.stopLocked();
            }
        }
    }

    /**
     * Loads the workspace screen ids in an ordered list.
     */
    public static ArrayList<Long> loadWorkspaceScreensDb(Context context) {
        final ContentResolver contentResolver = context.getContentResolver();
        final Uri screensUri = LauncherSettings.WorkspaceScreens.CONTENT_URI;

        // Get screens ordered by rank.
        return LauncherDbUtils.getScreenIdsFromCursor(contentResolver.query(
                screensUri, null, null, null, LauncherSettings.WorkspaceScreens.SCREEN_RANK));
    }

    /**
     * Runnable for the thread that loads the contents of the launcher:
     *   - workspace icons
     *   - widgets
     *   - all apps icons
     *   - deep shortcuts within apps
     */
    private class LoaderTask implements Runnable {
        private Context mContext;
        private int mPageToBindFirst;

        @Thunk boolean mIsLoadingAndBindingWorkspace;
        private boolean mStopped;
        @Thunk boolean mLoadAndBindStepFinished;

        LoaderTask(Context context, int pageToBindFirst) {
            mContext = context;
            mPageToBindFirst = pageToBindFirst;
        }

        private void loadAndBindWorkspace() {
            mIsLoadingAndBindingWorkspace = true;

            // Load the workspace
            if (DEBUG_LOADERS) {
                Log.d(TAG, "loadAndBindWorkspace mWorkspaceLoaded=" + mWorkspaceLoaded);
            }

            if (!mWorkspaceLoaded) {
                loadWorkspace();
                synchronized (LoaderTask.this) {
                    if (mStopped) {
                        return;
                    }
                    mWorkspaceLoaded = true;
                }
            }

            // Bind the workspace
            bindWorkspace(mPageToBindFirst);
        }

        private void waitForIdle() {
            // Wait until the either we're stopped or the other threads are done.
            // This way we don't start loading all apps until the workspace has settled
            // down.
            synchronized (LoaderTask.this) {
                final long workspaceWaitTime = DEBUG_LOADERS ? SystemClock.uptimeMillis() : 0;

                mHandler.postIdle(new Runnable() {
                        public void run() {
                            synchronized (LoaderTask.this) {
                                mLoadAndBindStepFinished = true;
                                if (DEBUG_LOADERS) {
                                    Log.d(TAG, "done with previous binding step");
                                }
                                LoaderTask.this.notify();
                            }
                        }
                    });

                while (!mStopped && !mLoadAndBindStepFinished) {
                    try {
                        // Just in case mFlushingWorkerThread changes but we aren't woken up,
                        // wait no longer than 1sec at a time
                        this.wait(1000);
                    } catch (InterruptedException ex) {
                        // Ignore
                    }
                }
                if (DEBUG_LOADERS) {
                    Log.d(TAG, "waited "
                            + (SystemClock.uptimeMillis()-workspaceWaitTime)
                            + "ms for previous step to finish binding");
                }
            }
        }

        void runBindSynchronousPage(int synchronousBindPage) {
            if (synchronousBindPage == PagedView.INVALID_RESTORE_PAGE) {
                // Ensure that we have a valid page index to load synchronously
                throw new RuntimeException("Should not call runBindSynchronousPage() without " +
                        "valid page index");
            }
            if (!mAllAppsLoaded || !mWorkspaceLoaded) {
                // Ensure that we don't try and bind a specified page when the pages have not been
                // loaded already (we should load everything asynchronously in that case)
                throw new RuntimeException("Expecting AllApps and Workspace to be loaded");
            }
            synchronized (mLock) {
                if (mIsLoaderTaskRunning) {
                    // Ensure that we are never running the background loading at this point since
                    // we also touch the background collections
                    throw new RuntimeException("Error! Background loading is already running");
                }
            }

            // XXX: Throw an exception if we are already loading (since we touch the worker thread
            //      data structures, we can't allow any other thread to touch that data, but because
            //      this call is synchronous, we can get away with not locking).

            // The LauncherModel is static in the LauncherAppState and mHandler may have queued
            // operations from the previous activity.  We need to ensure that all queued operations
            // are executed before any synchronous binding work is done.
            mHandler.flush();

            // Divide the set of loaded items into those that we are binding synchronously, and
            // everything else that is to be bound normally (asynchronously).
            bindWorkspace(synchronousBindPage);
            // XXX: For now, continue posting the binding of AllApps as there are other issues that
            //      arise from that.
            onlyBindAllApps();

            bindDeepShortcuts();
        }

        public void run() {
            synchronized (mLock) {
                if (mStopped) {
                    return;
                }
                mIsLoaderTaskRunning = true;
            }
            // Optimize for end-user experience: if the Launcher is up and // running with the
            // All Apps interface in the foreground, load All Apps first. Otherwise, load the
            // workspace first (default).
            keep_running: {
                if (DEBUG_LOADERS) Log.d(TAG, "step 1: loading workspace");
                loadAndBindWorkspace();

                if (mStopped) {
                    break keep_running;
                }

                waitForIdle();

                // second step
                if (DEBUG_LOADERS) Log.d(TAG, "step 2: loading all apps");
                loadAndBindAllApps();

                waitForIdle();

                // third step
                if (DEBUG_LOADERS) Log.d(TAG, "step 3: loading deep shortcuts");
                loadAndBindDeepShortcuts();
            }

            // Clear out this reference, otherwise we end up holding it until all of the
            // callback runnables are done.
            mContext = null;

            synchronized (mLock) {
                // If we are still the last one to be scheduled, remove ourselves.
                if (mLoaderTask == this) {
                    mLoaderTask = null;
                }
                mIsLoaderTaskRunning = false;
                mHasLoaderCompletedOnce = true;
            }
        }

        public void stopLocked() {
            synchronized (LoaderTask.this) {
                mStopped = true;
                this.notify();
            }
        }

        /**
         * Gets the callbacks object.  If we've been stopped, or if the launcher object
         * has somehow been garbage collected, return null instead.  Pass in the Callbacks
         * object that was around when the deferred message was scheduled, and if there's
         * a new Callbacks object around then also return null.  This will save us from
         * calling onto it with data that will be ignored.
         */
        Callbacks tryGetCallbacks(Callbacks oldCallbacks) {
            synchronized (mLock) {
                if (mStopped) {
                    return null;
                }

                if (mCallbacks == null) {
                    return null;
                }

                final Callbacks callbacks = mCallbacks.get();
                if (callbacks != oldCallbacks) {
                    return null;
                }
                if (callbacks == null) {
                    Log.w(TAG, "no mCallbacks");
                    return null;
                }

                return callbacks;
            }
        }

        // check & update map of what's occupied; used to discard overlapping/invalid items
        private boolean checkItemPlacement(LongArrayMap<GridOccupancy> occupied, ItemInfo item,
                   ArrayList<Long> workspaceScreens) {
            LauncherAppState app = LauncherAppState.getInstance();
            InvariantDeviceProfile profile = app.getInvariantDeviceProfile();

            long containerIndex = item.screenId;
            if (item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                // Return early if we detect that an item is under the hotseat button
                if (!FeatureFlags.NO_ALL_APPS_ICON &&
                        profile.isAllAppsButtonRank((int) item.screenId)) {
                    Log.e(TAG, "Error loading shortcut into hotseat " + item
                            + " into position (" + item.screenId + ":" + item.cellX + ","
                            + item.cellY + ") occupied by all apps");
                    return false;
                }

                final GridOccupancy hotseatOccupancy =
                        occupied.get((long) LauncherSettings.Favorites.CONTAINER_HOTSEAT);

                if (item.screenId >= profile.numHotseatIcons) {
                    Log.e(TAG, "Error loading shortcut " + item
                            + " into hotseat position " + item.screenId
                            + ", position out of bounds: (0 to " + (profile.numHotseatIcons - 1)
                            + ")");
                    return false;
                }

                if (hotseatOccupancy != null) {
                    if (hotseatOccupancy.cells[(int) item.screenId][0]) {
                        Log.e(TAG, "Error loading shortcut into hotseat " + item
                                + " into position (" + item.screenId + ":" + item.cellX + ","
                                + item.cellY + ") already occupied");
                            return false;
                    } else {
                        hotseatOccupancy.cells[(int) item.screenId][0] = true;
                        return true;
                    }
                } else {
                    final GridOccupancy occupancy = new GridOccupancy(profile.numHotseatIcons, 1);
                    occupancy.cells[(int) item.screenId][0] = true;
                    occupied.put((long) LauncherSettings.Favorites.CONTAINER_HOTSEAT, occupancy);
                    return true;
                }
            } else if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                if (!workspaceScreens.contains((Long) item.screenId)) {
                    // The item has an invalid screen id.
                    return false;
                }
            } else {
                // Skip further checking if it is not the hotseat or workspace container
                return true;
            }

            final int countX = profile.numColumns;
            final int countY = profile.numRows;
            if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                    item.cellX < 0 || item.cellY < 0 ||
                    item.cellX + item.spanX > countX || item.cellY + item.spanY > countY) {
                Log.e(TAG, "Error loading shortcut " + item
                        + " into cell (" + containerIndex + "-" + item.screenId + ":"
                        + item.cellX + "," + item.cellY
                        + ") out of screen bounds ( " + countX + "x" + countY + ")");
                return false;
            }

            if (!occupied.containsKey(item.screenId)) {
                GridOccupancy screen = new GridOccupancy(countX + 1, countY + 1);
                if (item.screenId == Workspace.FIRST_SCREEN_ID) {
                    // Mark the first row as occupied (if the feature is enabled)
                    // in order to account for the QSB.
                    screen.markCells(0, 0, countX + 1, 1, FeatureFlags.QSB_ON_FIRST_SCREEN);
                }
                occupied.put(item.screenId, screen);
            }
            final GridOccupancy occupancy = occupied.get(item.screenId);

            // Check if any workspace icons overlap with each other
            if (occupancy.isRegionVacant(item.cellX, item.cellY, item.spanX, item.spanY)) {
                occupancy.markCells(item, true);
                return true;
            } else {
                Log.e(TAG, "Error loading shortcut " + item
                        + " into cell (" + containerIndex + "-" + item.screenId + ":"
                        + item.cellX + "," + item.cellX + "," + item.spanX + "," + item.spanY
                        + ") already occupied");
                return false;
            }
        }

        /** Clears all the sBg data structures */
        private void clearSBgDataStructures() {
            synchronized (sBgLock) {
                sBgWorkspaceItems.clear();
                sBgAppWidgets.clear();
                sBgFolders.clear();
                sBgItemsIdMap.clear();
                sBgWorkspaceScreens.clear();
                sBgPinnedShortcutCounts.clear();
            }
        }

        private void loadWorkspace() {
            if (LauncherAppState.PROFILE_STARTUP) {
                Trace.beginSection("Loading Workspace");
            }
            final long t = DEBUG_LOADERS ? SystemClock.uptimeMillis() : 0;

            final Context context = mContext;
            final ContentResolver contentResolver = context.getContentResolver();
            final PackageManager manager = context.getPackageManager();
            final boolean isSafeMode = manager.isSafeMode();
            final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(context);
            final boolean isSdCardReady = Utilities.isBootCompleted();

            LauncherAppState app = LauncherAppState.getInstance();
            InvariantDeviceProfile profile = app.getInvariantDeviceProfile();
            int countX = profile.numColumns;
            int countY = profile.numRows;

            boolean clearDb = false;
            try {
                ImportDataTask.performImportIfPossible(context);
            } catch (Exception e) {
                // Migration failed. Clear workspace.
                clearDb = true;
            }

            if (!clearDb && GridSizeMigrationTask.ENABLED &&
                    !GridSizeMigrationTask.migrateGridIfNeeded(mContext)) {
                // Migration failed. Clear workspace.
                clearDb = true;
            }

            if (clearDb) {
                Log.d(TAG, "loadWorkspace: resetting launcher database");
                LauncherSettings.Settings.call(contentResolver,
                        LauncherSettings.Settings.METHOD_DELETE_DB);
            }

            Log.d(TAG, "loadWorkspace: loading default favorites");
            LauncherSettings.Settings.call(contentResolver,
                    LauncherSettings.Settings.METHOD_LOAD_DEFAULT_FAVORITES);

            synchronized (sBgLock) {
                clearSBgDataStructures();
                final HashMap<String, Integer> installingPkgs = PackageInstallerCompat
                        .getInstance(mContext).updateAndGetActiveSessionCache();
                sBgWorkspaceScreens.addAll(loadWorkspaceScreensDb(mContext));

                final ArrayList<Long> itemsToRemove = new ArrayList<>();
                final ArrayList<Long> restoredRows = new ArrayList<>();
                Map<ShortcutKey, ShortcutInfoCompat> shortcutKeyToPinnedShortcuts = new HashMap<>();
                final Uri contentUri = LauncherSettings.Favorites.CONTENT_URI;
                if (DEBUG_LOADERS) Log.d(TAG, "loading model from " + contentUri);
                final Cursor c = contentResolver.query(contentUri, null, null, null, null);

                // +1 for the hotseat (it can be larger than the workspace)
                // Load workspace in reverse order to ensure that latest items are loaded first (and
                // before any earlier duplicates)
                final LongArrayMap<GridOccupancy> occupied = new LongArrayMap<>();
                HashMap<ComponentKey, AppWidgetProviderInfo> widgetProvidersMap = null;

                try {
                    final int idIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites._ID);
                    final int intentIndex = c.getColumnIndexOrThrow
                            (LauncherSettings.Favorites.INTENT);
                    final int containerIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.CONTAINER);
                    final int itemTypeIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.ITEM_TYPE);
                    final int appWidgetIdIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.APPWIDGET_ID);
                    final int appWidgetProviderIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.APPWIDGET_PROVIDER);
                    final int screenIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.SCREEN);
                    final int cellXIndex = c.getColumnIndexOrThrow
                            (LauncherSettings.Favorites.CELLX);
                    final int cellYIndex = c.getColumnIndexOrThrow
                            (LauncherSettings.Favorites.CELLY);
                    final int spanXIndex = c.getColumnIndexOrThrow
                            (LauncherSettings.Favorites.SPANX);
                    final int spanYIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.SPANY);
                    final int rankIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.RANK);
                    final int restoredIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.RESTORED);
                    final int profileIdIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.PROFILE_ID);
                    final int optionsIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.OPTIONS);
                    final CursorIconInfo cursorIconInfo = new CursorIconInfo(mContext, c);

                    final LongSparseArray<UserHandleCompat> allUsers = new LongSparseArray<>();
                    final LongSparseArray<Boolean> quietMode = new LongSparseArray<>();
                    final LongSparseArray<Boolean> unlockedUsers = new LongSparseArray<>();
                    for (UserHandleCompat user : mUserManager.getUserProfiles()) {
                        long serialNo = mUserManager.getSerialNumberForUser(user);
                        allUsers.put(serialNo, user);
                        quietMode.put(serialNo, mUserManager.isQuietModeEnabled(user));

                        boolean userUnlocked = mUserManager.isUserUnlocked(user);

                        // We can only query for shortcuts when the user is unlocked.
                        if (userUnlocked) {
                            List<ShortcutInfoCompat> pinnedShortcuts =
                                    mDeepShortcutManager.queryForPinnedShortcuts(null, user);
                            if (mDeepShortcutManager.wasLastCallSuccess()) {
                                for (ShortcutInfoCompat shortcut : pinnedShortcuts) {
                                    shortcutKeyToPinnedShortcuts.put(ShortcutKey.fromInfo(shortcut),
                                            shortcut);
                                }
                            } else {
                                // Shortcut manager can fail due to some race condition when the
                                // lock state changes too frequently. For the purpose of the loading
                                // shortcuts, consider the user is still locked.
                                userUnlocked = false;
                            }
                        }
                        unlockedUsers.put(serialNo, userUnlocked);
                    }

                    ShortcutInfo info;
                    String intentDescription;
                    LauncherAppWidgetInfo appWidgetInfo;
                    int container;
                    long id;
                    long serialNumber;
                    Intent intent;
                    UserHandleCompat user;
                    String targetPackage;

                    while (!mStopped && c.moveToNext()) {
                        try {
                            int itemType = c.getInt(itemTypeIndex);
                            boolean restored = 0 != c.getInt(restoredIndex);
                            boolean allowMissingTarget = false;
                            container = c.getInt(containerIndex);

                            switch (itemType) {
                            case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                            case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                            case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT:
                                id = c.getLong(idIndex);
                                intentDescription = c.getString(intentIndex);
                                serialNumber = c.getInt(profileIdIndex);
                                user = allUsers.get(serialNumber);
                                int promiseType = c.getInt(restoredIndex);
                                int disabledState = 0;
                                boolean itemReplaced = false;
                                targetPackage = null;
                                if (user == null) {
                                    // User has been deleted remove the item.
                                    itemsToRemove.add(id);
                                    continue;
                                }
                                try {
                                    intent = Intent.parseUri(intentDescription, 0);
                                    ComponentName cn = intent.getComponent();
                                    if (cn != null && cn.getPackageName() != null) {
                                        boolean validPkg = launcherApps.isPackageEnabledForProfile(
                                                cn.getPackageName(), user);
                                        boolean validComponent = validPkg &&
                                                launcherApps.isActivityEnabledForProfile(cn, user);
                                        if (validPkg) {
                                            targetPackage = cn.getPackageName();
                                        }

                                        if (validComponent) {
                                            if (restored) {
                                                // no special handling necessary for this item
                                                restoredRows.add(id);
                                                restored = false;
                                            }
                                            if (quietMode.get(serialNumber)) {
                                                disabledState = ShortcutInfo.FLAG_DISABLED_QUIET_USER;
                                            }
                                        } else if (validPkg) {
                                            intent = null;
                                            if ((promiseType & ShortcutInfo.FLAG_AUTOINTALL_ICON) != 0) {
                                                // We allow auto install apps to have their intent
                                                // updated after an install.
                                                intent = manager.getLaunchIntentForPackage(
                                                        cn.getPackageName());
                                                if (intent != null) {
                                                    ContentValues values = new ContentValues();
                                                    values.put(LauncherSettings.Favorites.INTENT,
                                                            intent.toUri(0));
                                                    updateItem(id, values);
                                                }
                                            }

                                            if (intent == null) {
                                                // The app is installed but the component is no
                                                // longer available.
                                                FileLog.d(TAG, "Invalid component removed: " + cn);
                                                itemsToRemove.add(id);
                                                continue;
                                            } else {
                                                // no special handling necessary for this item
                                                restoredRows.add(id);
                                                restored = false;
                                            }
                                        } else if (restored) {
                                            // Package is not yet available but might be
                                            // installed later.
                                            FileLog.d(TAG, "package not yet restored: " + cn);

                                            if ((promiseType & ShortcutInfo.FLAG_RESTORE_STARTED) != 0) {
                                                // Restore has started once.
                                            } else if (installingPkgs.containsKey(cn.getPackageName())) {
                                                // App restore has started. Update the flag
                                                promiseType |= ShortcutInfo.FLAG_RESTORE_STARTED;
                                                ContentValues values = new ContentValues();
                                                values.put(LauncherSettings.Favorites.RESTORED,
                                                        promiseType);
                                                updateItem(id, values);
                                            } else if ((promiseType & ShortcutInfo.FLAG_RESTORED_APP_TYPE) != 0) {
                                                // This is a common app. Try to replace this.
                                                int appType = CommonAppTypeParser.decodeItemTypeFromFlag(promiseType);
                                                CommonAppTypeParser parser = new CommonAppTypeParser(id, appType, context);
                                                if (parser.findDefaultApp()) {
                                                    // Default app found. Replace it.
                                                    intent = parser.parsedIntent;
                                                    cn = intent.getComponent();
                                                    ContentValues values = parser.parsedValues;
                                                    values.put(LauncherSettings.Favorites.RESTORED, 0);
                                                    updateItem(id, values);
                                                    restored = false;
                                                    itemReplaced = true;

                                                } else {
                                                    FileLog.d(TAG, "Unrestored package removed: " + cn);
                                                    itemsToRemove.add(id);
                                                    continue;
                                                }
                                            } else {
                                                FileLog.d(TAG, "Unrestored package removed: " + cn);
                                                itemsToRemove.add(id);
                                                continue;
                                            }
                                        } else if (PackageManagerHelper.isAppOnSdcard(
                                                manager, cn.getPackageName())) {
                                            // Package is present but not available.
                                            allowMissingTarget = true;
                                            disabledState = ShortcutInfo.FLAG_DISABLED_NOT_AVAILABLE;
                                        } else if (!isSdCardReady) {
                                            // SdCard is not ready yet. Package might get available,
                                            // once it is ready.
                                            Log.d(TAG, "Invalid package: " + cn + " (check again later)");
                                            HashSet<String> pkgs = sPendingPackages.get(user);
                                            if (pkgs == null) {
                                                pkgs = new HashSet<String>();
                                                sPendingPackages.put(user, pkgs);
                                            }
                                            pkgs.add(cn.getPackageName());
                                            allowMissingTarget = true;
                                            // Add the icon on the workspace anyway.

                                        } else {
                                            // Do not wait for external media load anymore.
                                            // Log the invalid package, and remove it
                                            FileLog.d(TAG, "Invalid package removed: " + cn);
                                            itemsToRemove.add(id);
                                            continue;
                                        }
                                    } else if (cn == null) {
                                        // For shortcuts with no component, keep them as they are
                                        restoredRows.add(id);
                                        restored = false;
                                    }
                                } catch (URISyntaxException e) {
                                    FileLog.d(TAG, "Invalid uri: " + intentDescription);
                                    itemsToRemove.add(id);
                                    continue;
                                }

                                boolean useLowResIcon = container >= 0 &&
                                        c.getInt(rankIndex) >= FolderIcon.NUM_ITEMS_IN_PREVIEW;

                                if (itemReplaced) {
                                    if (user.equals(UserHandleCompat.myUserHandle())) {
                                        info = getAppShortcutInfo(intent, user, null,
                                                cursorIconInfo, false, useLowResIcon);
                                    } else {
                                        // Don't replace items for other profiles.
                                        itemsToRemove.add(id);
                                        continue;
                                    }
                                } else if (restored) {
                                    if (user.equals(UserHandleCompat.myUserHandle())) {
                                        info = getRestoredItemInfo(c, intent,
                                                promiseType, itemType, cursorIconInfo);
                                        intent = getRestoredItemIntent(c, context, intent);
                                    } else {
                                        // Don't restore items for other profiles.
                                        itemsToRemove.add(id);
                                        continue;
                                    }
                                } else if (itemType ==
                                        LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                                    info = getAppShortcutInfo(intent, user, c,
                                            cursorIconInfo, allowMissingTarget, useLowResIcon);
                                } else if (itemType ==
                                        LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {

                                    ShortcutKey key = ShortcutKey.fromIntent(intent, user);
                                    if (unlockedUsers.get(serialNumber)) {
                                        ShortcutInfoCompat pinnedShortcut =
                                                shortcutKeyToPinnedShortcuts.get(key);
                                        if (pinnedShortcut == null) {
                                            // The shortcut is no longer valid.
                                            itemsToRemove.add(id);
                                            continue;
                                        }
                                        info = new ShortcutInfo(pinnedShortcut, context);
                                        intent = info.intent;
                                    } else {
                                        // Create a shortcut info in disabled mode for now.
                                        info = new ShortcutInfo();
                                        info.user = user;
                                        info.itemType = itemType;
                                        loadInfoFromCursor(info, c, cursorIconInfo);

                                        info.isDisabled |= ShortcutInfo.FLAG_DISABLED_LOCKED_USER;
                                    }
                                    incrementPinnedShortcutCount(key, false /* shouldPin */);
                                } else { // item type == ITEM_TYPE_SHORTCUT
                                    info = getShortcutInfo(c, cursorIconInfo);

                                    // Shortcuts are only available on the primary profile
                                    if (PackageManagerHelper.isAppSuspended(manager, targetPackage)) {
                                        disabledState |= ShortcutInfo.FLAG_DISABLED_SUSPENDED;
                                    }

                                    // App shortcuts that used to be automatically added to Launcher
                                    // didn't always have the correct intent flags set, so do that
                                    // here
                                    if (intent.getAction() != null &&
                                        intent.getCategories() != null &&
                                        intent.getAction().equals(Intent.ACTION_MAIN) &&
                                        intent.getCategories().contains(Intent.CATEGORY_LAUNCHER)) {
                                        intent.addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK |
                                            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                                    }
                                }

                                if (info != null) {
                                    info.id = id;
                                    info.intent = intent;
                                    info.container = container;
                                    info.screenId = c.getInt(screenIndex);
                                    info.cellX = c.getInt(cellXIndex);
                                    info.cellY = c.getInt(cellYIndex);
                                    info.rank = c.getInt(rankIndex);
                                    info.spanX = 1;
                                    info.spanY = 1;
                                    info.intent.putExtra(ItemInfo.EXTRA_PROFILE, serialNumber);
                                    if (info.promisedIntent != null) {
                                        info.promisedIntent.putExtra(ItemInfo.EXTRA_PROFILE, serialNumber);
                                    }
                                    info.isDisabled |= disabledState;
                                    if (isSafeMode && !Utilities.isSystemApp(context, intent)) {
                                        info.isDisabled |= ShortcutInfo.FLAG_DISABLED_SAFEMODE;
                                    }

                                    // check & update map of what's occupied
                                    if (!checkItemPlacement(occupied, info, sBgWorkspaceScreens)) {
                                        itemsToRemove.add(id);
                                        break;
                                    }

                                    if (restored) {
                                        ComponentName cn = info.getTargetComponent();
                                        if (cn != null) {
                                            Integer progress = installingPkgs.get(cn.getPackageName());
                                            if (progress != null) {
                                                info.setInstallProgress(progress);
                                            } else {
                                                info.status &= ~ShortcutInfo.FLAG_INSTALL_SESSION_ACTIVE;
                                            }
                                        }
                                    }

                                    switch (container) {
                                    case LauncherSettings.Favorites.CONTAINER_DESKTOP:
                                    case LauncherSettings.Favorites.CONTAINER_HOTSEAT:
                                        sBgWorkspaceItems.add(info);
                                        break;
                                    default:
                                        // Item is in a user folder
                                        FolderInfo folderInfo =
                                                findOrMakeFolder(sBgFolders, container);
                                        folderInfo.add(info, false);
                                        break;
                                    }
                                    sBgItemsIdMap.put(info.id, info);
                                } else {
                                    throw new RuntimeException("Unexpected null ShortcutInfo");
                                }
                                break;

                            case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                                id = c.getLong(idIndex);
                                FolderInfo folderInfo = findOrMakeFolder(sBgFolders, id);

                                // Do not trim the folder label, as is was set by the user.
                                folderInfo.title = c.getString(cursorIconInfo.titleIndex);
                                folderInfo.id = id;
                                folderInfo.container = container;
                                folderInfo.screenId = c.getInt(screenIndex);
                                folderInfo.cellX = c.getInt(cellXIndex);
                                folderInfo.cellY = c.getInt(cellYIndex);
                                folderInfo.spanX = 1;
                                folderInfo.spanY = 1;
                                folderInfo.options = c.getInt(optionsIndex);

                                // check & update map of what's occupied
                                if (!checkItemPlacement(occupied, folderInfo, sBgWorkspaceScreens)) {
                                    itemsToRemove.add(id);
                                    break;
                                }

                                switch (container) {
                                    case LauncherSettings.Favorites.CONTAINER_DESKTOP:
                                    case LauncherSettings.Favorites.CONTAINER_HOTSEAT:
                                        sBgWorkspaceItems.add(folderInfo);
                                        break;
                                }

                                if (restored) {
                                    // no special handling required for restored folders
                                    restoredRows.add(id);
                                }

                                sBgItemsIdMap.put(folderInfo.id, folderInfo);
                                sBgFolders.put(folderInfo.id, folderInfo);
                                break;

                            case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                            case LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET:
                                // Read all Launcher-specific widget details
                                boolean customWidget = itemType ==
                                    LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET;

                                int appWidgetId = c.getInt(appWidgetIdIndex);
                                serialNumber = c.getLong(profileIdIndex);
                                String savedProvider = c.getString(appWidgetProviderIndex);
                                id = c.getLong(idIndex);
                                user = allUsers.get(serialNumber);
                                if (user == null) {
                                    itemsToRemove.add(id);
                                    continue;
                                }

                                final ComponentName component =
                                        ComponentName.unflattenFromString(savedProvider);

                                final int restoreStatus = c.getInt(restoredIndex);
                                final boolean isIdValid = (restoreStatus &
                                        LauncherAppWidgetInfo.FLAG_ID_NOT_VALID) == 0;
                                final boolean wasProviderReady = (restoreStatus &
                                        LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY) == 0;

                                if (widgetProvidersMap == null) {
                                    widgetProvidersMap = AppWidgetManagerCompat
                                            .getInstance(mContext).getAllProvidersMap();
                                }
                                final AppWidgetProviderInfo provider = widgetProvidersMap.get(
                                        new ComponentKey(
                                                ComponentName.unflattenFromString(savedProvider),
                                                user));

                                final boolean isProviderReady = isValidProvider(provider);
                                if (!isSafeMode && !customWidget &&
                                        wasProviderReady && !isProviderReady) {
                                    FileLog.d(TAG, "Deleting widget that isn't installed anymore: "
                                            + provider);
                                    itemsToRemove.add(id);
                                } else {
                                    if (isProviderReady) {
                                        appWidgetInfo = new LauncherAppWidgetInfo(appWidgetId,
                                                provider.provider);

                                        // The provider is available. So the widget is either
                                        // available or not available. We do not need to track
                                        // any future restore updates.
                                        int status = restoreStatus &
                                                ~LauncherAppWidgetInfo.FLAG_RESTORE_STARTED;
                                        if (!wasProviderReady) {
                                            // If provider was not previously ready, update the
                                            // status and UI flag.

                                            // Id would be valid only if the widget restore broadcast was received.
                                            if (isIdValid) {
                                                status |= LauncherAppWidgetInfo.FLAG_UI_NOT_READY;
                                            } else {
                                                status &= ~LauncherAppWidgetInfo
                                                        .FLAG_PROVIDER_NOT_READY;
                                            }
                                        }
                                        appWidgetInfo.restoreStatus = status;
                                    } else {
                                        Log.v(TAG, "Widget restore pending id=" + id
                                                + " appWidgetId=" + appWidgetId
                                                + " status =" + restoreStatus);
                                        appWidgetInfo = new LauncherAppWidgetInfo(appWidgetId,
                                                component);
                                        appWidgetInfo.restoreStatus = restoreStatus;
                                        Integer installProgress = installingPkgs.get(component.getPackageName());

                                        if ((restoreStatus & LauncherAppWidgetInfo.FLAG_RESTORE_STARTED) != 0) {
                                            // Restore has started once.
                                        } else if (installProgress != null) {
                                            // App restore has started. Update the flag
                                            appWidgetInfo.restoreStatus |=
                                                    LauncherAppWidgetInfo.FLAG_RESTORE_STARTED;
                                        } else if (!isSafeMode) {
                                            FileLog.d(TAG, "Unrestored widget removed: " + component);
                                            itemsToRemove.add(id);
                                            continue;
                                        }

                                        appWidgetInfo.installProgress =
                                                installProgress == null ? 0 : installProgress;
                                    }
                                    if (appWidgetInfo.hasRestoreFlag(
                                            LauncherAppWidgetInfo.FLAG_DIRECT_CONFIG)) {
                                        intentDescription = c.getString(intentIndex);
                                        if (!TextUtils.isEmpty(intentDescription)) {
                                            appWidgetInfo.bindOptions =
                                                    Intent.parseUri(intentDescription, 0);
                                        }
                                    }

                                    appWidgetInfo.id = id;
                                    appWidgetInfo.screenId = c.getInt(screenIndex);
                                    appWidgetInfo.cellX = c.getInt(cellXIndex);
                                    appWidgetInfo.cellY = c.getInt(cellYIndex);
                                    appWidgetInfo.spanX = c.getInt(spanXIndex);
                                    appWidgetInfo.spanY = c.getInt(spanYIndex);
                                    appWidgetInfo.user = user;

                                    if (container != LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                                        container != LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                                        Log.e(TAG, "Widget found where container != " +
                                                "CONTAINER_DESKTOP nor CONTAINER_HOTSEAT - ignoring!");
                                        itemsToRemove.add(id);
                                        continue;
                                    }

                                    appWidgetInfo.container = container;
                                    // check & update map of what's occupied
                                    if (!checkItemPlacement(occupied, appWidgetInfo, sBgWorkspaceScreens)) {
                                        itemsToRemove.add(id);
                                        break;
                                    }

                                    if (!customWidget) {
                                        String providerName =
                                                appWidgetInfo.providerName.flattenToString();
                                        if (!providerName.equals(savedProvider) ||
                                                (appWidgetInfo.restoreStatus != restoreStatus)) {
                                            ContentValues values = new ContentValues();
                                            values.put(
                                                    LauncherSettings.Favorites.APPWIDGET_PROVIDER,
                                                    providerName);
                                            values.put(LauncherSettings.Favorites.RESTORED,
                                                    appWidgetInfo.restoreStatus);
                                            updateItem(id, values);
                                        }
                                    }
                                    sBgItemsIdMap.put(appWidgetInfo.id, appWidgetInfo);
                                    sBgAppWidgets.add(appWidgetInfo);
                                }
                                break;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Desktop items loading interrupted", e);
                        }
                    }
                } finally {
                    Utilities.closeSilently(c);
                }

                // Break early if we've stopped loading
                if (mStopped) {
                    clearSBgDataStructures();
                    return;
                }

                if (itemsToRemove.size() > 0) {
                    // Remove dead items
                    contentResolver.delete(LauncherSettings.Favorites.CONTENT_URI,
                            Utilities.createDbSelectionQuery(
                                    LauncherSettings.Favorites._ID, itemsToRemove), null);
                    if (DEBUG_LOADERS) {
                        Log.d(TAG, "Removed = " + Utilities.createDbSelectionQuery(
                                LauncherSettings.Favorites._ID, itemsToRemove));
                    }

                    // Remove any empty folder
                    ArrayList<Long> deletedFolderIds = (ArrayList<Long>) LauncherSettings.Settings
                            .call(contentResolver,
                                    LauncherSettings.Settings.METHOD_DELETE_EMPTY_FOLDERS)
                            .getSerializable(LauncherSettings.Settings.EXTRA_VALUE);
                    for (long folderId : deletedFolderIds) {
                        sBgWorkspaceItems.remove(sBgFolders.get(folderId));
                        sBgFolders.remove(folderId);
                        sBgItemsIdMap.remove(folderId);
                    }
                }

                // Unpin shortcuts that don't exist on the workspace.
                for (ShortcutKey key : shortcutKeyToPinnedShortcuts.keySet()) {
                    MutableInt numTimesPinned = sBgPinnedShortcutCounts.get(key);
                    if (numTimesPinned == null || numTimesPinned.value == 0) {
                        // Shortcut is pinned but doesn't exist on the workspace; unpin it.
                        mDeepShortcutManager.unpinShortcut(key);
                    }
                }

                // Sort all the folder items and make sure the first 3 items are high resolution.
                for (FolderInfo folder : sBgFolders) {
                    Collections.sort(folder.contents, Folder.ITEM_POS_COMPARATOR);
                    int pos = 0;
                    for (ShortcutInfo info : folder.contents) {
                        if (info.usingLowResIcon) {
                            info.updateIcon(mIconCache, false);
                        }
                        pos ++;
                        if (pos >= FolderIcon.NUM_ITEMS_IN_PREVIEW) {
                            break;
                        }
                    }
                }

                if (restoredRows.size() > 0) {
                    // Update restored items that no longer require special handling
                    ContentValues values = new ContentValues();
                    values.put(LauncherSettings.Favorites.RESTORED, 0);
                    contentResolver.update(LauncherSettings.Favorites.CONTENT_URI, values,
                            Utilities.createDbSelectionQuery(
                                    LauncherSettings.Favorites._ID, restoredRows), null);
                }

                if (!isSdCardReady && !sPendingPackages.isEmpty()) {
                    context.registerReceiver(new AppsAvailabilityCheck(),
                            new IntentFilter(Intent.ACTION_BOOT_COMPLETED),
                            null, sWorker);
                }

                // Remove any empty screens
                ArrayList<Long> unusedScreens = new ArrayList<Long>(sBgWorkspaceScreens);
                for (ItemInfo item: sBgItemsIdMap) {
                    long screenId = item.screenId;
                    if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                            unusedScreens.contains(screenId)) {
                        unusedScreens.remove(screenId);
                    }
                }

                // If there are any empty screens remove them, and update.
                if (unusedScreens.size() != 0) {
                    sBgWorkspaceScreens.removeAll(unusedScreens);
                    updateWorkspaceScreenOrder(context, sBgWorkspaceScreens);
                }

                if (DEBUG_LOADERS) {
                    Log.d(TAG, "loaded workspace in " + (SystemClock.uptimeMillis()-t) + "ms");
                    Log.d(TAG, "workspace layout: ");
                    int nScreens = occupied.size();
                    for (int y = 0; y < countY; y++) {
                        String line = "";

                        for (int i = 0; i < nScreens; i++) {
                            long screenId = occupied.keyAt(i);
                            if (screenId > 0) {
                                line += " | ";
                            }
                        }
                        Log.d(TAG, "[ " + line + " ]");
                    }
                }
            }
            if (LauncherAppState.PROFILE_STARTUP) {
                Trace.endSection();
            }
        }

        /**
         * Partially updates the item without any notification. Must be called on the worker thread.
         */
        private void updateItem(long itemId, ContentValues update) {
            mContext.getContentResolver().update(
                    LauncherSettings.Favorites.CONTENT_URI,
                    update,
                    BaseColumns._ID + "= ?",
                    new String[]{Long.toString(itemId)});
        }

        /** Filters the set of items who are directly or indirectly (via another container) on the
         * specified screen. */
        private void filterCurrentWorkspaceItems(long currentScreenId,
                ArrayList<ItemInfo> allWorkspaceItems,
                ArrayList<ItemInfo> currentScreenItems,
                ArrayList<ItemInfo> otherScreenItems) {
            // Purge any null ItemInfos
            Iterator<ItemInfo> iter = allWorkspaceItems.iterator();
            while (iter.hasNext()) {
                ItemInfo i = iter.next();
                if (i == null) {
                    iter.remove();
                }
            }

            // Order the set of items by their containers first, this allows use to walk through the
            // list sequentially, build up a list of containers that are in the specified screen,
            // as well as all items in those containers.
            Set<Long> itemsOnScreen = new HashSet<Long>();
            Collections.sort(allWorkspaceItems, new Comparator<ItemInfo>() {
                @Override
                public int compare(ItemInfo lhs, ItemInfo rhs) {
                    return Utilities.longCompare(lhs.container, rhs.container);
                }
            });
            for (ItemInfo info : allWorkspaceItems) {
                if (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                    if (info.screenId == currentScreenId) {
                        currentScreenItems.add(info);
                        itemsOnScreen.add(info.id);
                    } else {
                        otherScreenItems.add(info);
                    }
                } else if (info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                    currentScreenItems.add(info);
                    itemsOnScreen.add(info.id);
                } else {
                    if (itemsOnScreen.contains(info.container)) {
                        currentScreenItems.add(info);
                        itemsOnScreen.add(info.id);
                    } else {
                        otherScreenItems.add(info);
                    }
                }
            }
        }

        /** Filters the set of widgets which are on the specified screen. */
        private void filterCurrentAppWidgets(long currentScreenId,
                ArrayList<LauncherAppWidgetInfo> appWidgets,
                ArrayList<LauncherAppWidgetInfo> currentScreenWidgets,
                ArrayList<LauncherAppWidgetInfo> otherScreenWidgets) {

            for (LauncherAppWidgetInfo widget : appWidgets) {
                if (widget == null) continue;
                if (widget.container == LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                        widget.screenId == currentScreenId) {
                    currentScreenWidgets.add(widget);
                } else {
                    otherScreenWidgets.add(widget);
                }
            }
        }

        /** Sorts the set of items by hotseat, workspace (spatially from top to bottom, left to
         * right) */
        private void sortWorkspaceItemsSpatially(ArrayList<ItemInfo> workspaceItems) {
            final LauncherAppState app = LauncherAppState.getInstance();
            final InvariantDeviceProfile profile = app.getInvariantDeviceProfile();
            final int screenCols = profile.numColumns;
            final int screenCellCount = profile.numColumns * profile.numRows;
            Collections.sort(workspaceItems, new Comparator<ItemInfo>() {
                @Override
                public int compare(ItemInfo lhs, ItemInfo rhs) {
                    if (lhs.container == rhs.container) {
                        // Within containers, order by their spatial position in that container
                        switch ((int) lhs.container) {
                            case LauncherSettings.Favorites.CONTAINER_DESKTOP: {
                                long lr = (lhs.screenId * screenCellCount +
                                        lhs.cellY * screenCols + lhs.cellX);
                                long rr = (rhs.screenId * screenCellCount +
                                        rhs.cellY * screenCols + rhs.cellX);
                                return Utilities.longCompare(lr, rr);
                            }
                            case LauncherSettings.Favorites.CONTAINER_HOTSEAT: {
                                // We currently use the screen id as the rank
                                return Utilities.longCompare(lhs.screenId, rhs.screenId);
                            }
                            default:
                                if (ProviderConfig.IS_DOGFOOD_BUILD) {
                                    throw new RuntimeException("Unexpected container type when " +
                                            "sorting workspace items.");
                                }
                                return 0;
                        }
                    } else {
                        // Between containers, order by hotseat, desktop
                        return Utilities.longCompare(lhs.container, rhs.container);
                    }
                }
            });
        }

        private void bindWorkspaceScreens(final Callbacks oldCallbacks,
                final ArrayList<Long> orderedScreens) {
            final Runnable r = new Runnable() {
                @Override
                public void run() {
                    Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                    if (callbacks != null) {
                        callbacks.bindScreens(orderedScreens);
                    }
                }
            };
            runOnMainThread(r);
        }

        private void bindWorkspaceItems(final Callbacks oldCallbacks,
                final ArrayList<ItemInfo> workspaceItems,
                final ArrayList<LauncherAppWidgetInfo> appWidgets,
                final Executor executor) {

            // Bind the workspace items
            int N = workspaceItems.size();
            for (int i = 0; i < N; i += ITEMS_CHUNK) {
                final int start = i;
                final int chunkSize = (i+ITEMS_CHUNK <= N) ? ITEMS_CHUNK : (N-i);
                final Runnable r = new Runnable() {
                    @Override
                    public void run() {
                        Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                        if (callbacks != null) {
                            callbacks.bindItems(workspaceItems, start, start+chunkSize,
                                    false);
                        }
                    }
                };
                executor.execute(r);
            }

            // Bind the widgets, one at a time
            N = appWidgets.size();
            for (int i = 0; i < N; i++) {
                final LauncherAppWidgetInfo widget = appWidgets.get(i);
                final Runnable r = new Runnable() {
                    public void run() {
                        Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                        if (callbacks != null) {
                            callbacks.bindAppWidget(widget);
                        }
                    }
                };
                executor.execute(r);
            }
        }

        /**
         * Binds all loaded data to actual views on the main thread.
         */
        private void bindWorkspace(int synchronizeBindPage) {
            final long t = SystemClock.uptimeMillis();
            Runnable r;

            // Don't use these two variables in any of the callback runnables.
            // Otherwise we hold a reference to them.
            final Callbacks oldCallbacks = mCallbacks.get();
            if (oldCallbacks == null) {
                // This launcher has exited and nobody bothered to tell us.  Just bail.
                Log.w(TAG, "LoaderTask running with no launcher");
                return;
            }

            // Save a copy of all the bg-thread collections
            ArrayList<ItemInfo> workspaceItems = new ArrayList<>();
            ArrayList<LauncherAppWidgetInfo> appWidgets = new ArrayList<>();
            ArrayList<Long> orderedScreenIds = new ArrayList<>();

            synchronized (sBgLock) {
                workspaceItems.addAll(sBgWorkspaceItems);
                appWidgets.addAll(sBgAppWidgets);
                orderedScreenIds.addAll(sBgWorkspaceScreens);
            }

            final int currentScreen;
            {
                int currScreen = synchronizeBindPage != PagedView.INVALID_RESTORE_PAGE
                        ? synchronizeBindPage : oldCallbacks.getCurrentWorkspaceScreen();
                if (currScreen >= orderedScreenIds.size()) {
                    // There may be no workspace screens (just hotseat items and an empty page).
                    currScreen = PagedView.INVALID_RESTORE_PAGE;
                }
                currentScreen = currScreen;
            }
            final boolean validFirstPage = currentScreen >= 0;
            final long currentScreenId =
                    validFirstPage ? orderedScreenIds.get(currentScreen) : INVALID_SCREEN_ID;

            // Separate the items that are on the current screen, and all the other remaining items
            ArrayList<ItemInfo> currentWorkspaceItems = new ArrayList<>();
            ArrayList<ItemInfo> otherWorkspaceItems = new ArrayList<>();
            ArrayList<LauncherAppWidgetInfo> currentAppWidgets = new ArrayList<>();
            ArrayList<LauncherAppWidgetInfo> otherAppWidgets = new ArrayList<>();

            filterCurrentWorkspaceItems(currentScreenId, workspaceItems, currentWorkspaceItems,
                    otherWorkspaceItems);
            filterCurrentAppWidgets(currentScreenId, appWidgets, currentAppWidgets,
                    otherAppWidgets);
            sortWorkspaceItemsSpatially(currentWorkspaceItems);
            sortWorkspaceItemsSpatially(otherWorkspaceItems);

            // Tell the workspace that we're about to start binding items
            r = new Runnable() {
                public void run() {
                    Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                    if (callbacks != null) {
                        callbacks.clearPendingBinds();
                        callbacks.startBinding();
                    }
                }
            };
            runOnMainThread(r);

            bindWorkspaceScreens(oldCallbacks, orderedScreenIds);

            Executor mainExecutor = new DeferredMainThreadExecutor();
            // Load items on the current page.
            bindWorkspaceItems(oldCallbacks, currentWorkspaceItems, currentAppWidgets, mainExecutor);

            // In case of validFirstPage, only bind the first screen, and defer binding the
            // remaining screens after first onDraw (and an optional the fade animation whichever
            // happens later).
            // This ensures that the first screen is immediately visible (eg. during rotation)
            // In case of !validFirstPage, bind all pages one after other.
            final Executor deferredExecutor =
                    validFirstPage ? new ViewOnDrawExecutor(mHandler) : mainExecutor;

            mainExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                    if (callbacks != null) {
                        callbacks.finishFirstPageBind(
                                validFirstPage ? (ViewOnDrawExecutor) deferredExecutor : null);
                    }
                }
            });

            bindWorkspaceItems(oldCallbacks, otherWorkspaceItems, otherAppWidgets, deferredExecutor);

            // Tell the workspace that we're done binding items
            r = new Runnable() {
                public void run() {
                    Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                    if (callbacks != null) {
                        callbacks.finishBindingItems();
                    }

                    mIsLoadingAndBindingWorkspace = false;

                    // Run all the bind complete runnables after workspace is bound.
                    if (!mBindCompleteRunnables.isEmpty()) {
                        synchronized (mBindCompleteRunnables) {
                            for (final Runnable r : mBindCompleteRunnables) {
                                runOnWorkerThread(r);
                            }
                            mBindCompleteRunnables.clear();
                        }
                    }

                    // If we're profiling, ensure this is the last thing in the queue.
                    if (DEBUG_LOADERS) {
                        Log.d(TAG, "bound workspace in "
                            + (SystemClock.uptimeMillis()-t) + "ms");
                    }

                }
            };
            deferredExecutor.execute(r);

            if (validFirstPage) {
                r = new Runnable() {
                    public void run() {
                        Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                        if (callbacks != null) {
                            // We are loading synchronously, which means, some of the pages will be
                            // bound after first draw. Inform the callbacks that page binding is
                            // not complete, and schedule the remaining pages.
                            if (currentScreen != PagedView.INVALID_RESTORE_PAGE) {
                                callbacks.onPageBoundSynchronously(currentScreen);
                            }
                            callbacks.executeOnNextDraw((ViewOnDrawExecutor) deferredExecutor);
                        }
                    }
                };
                runOnMainThread(r);
            }
        }

        private void loadAndBindAllApps() {
            if (DEBUG_LOADERS) {
                Log.d(TAG, "loadAndBindAllApps mAllAppsLoaded=" + mAllAppsLoaded);
            }
            if (!mAllAppsLoaded) {
                loadAllApps();
                synchronized (LoaderTask.this) {
                    if (mStopped) {
                        return;
                    }
                }
                updateIconCache();
                synchronized (LoaderTask.this) {
                    if (mStopped) {
                        return;
                    }
                    mAllAppsLoaded = true;
                }
            } else {
                onlyBindAllApps();
            }
        }

        private void updateIconCache() {
            // Ignore packages which have a promise icon.
            HashSet<String> packagesToIgnore = new HashSet<>();
            synchronized (sBgLock) {
                for (ItemInfo info : sBgItemsIdMap) {
                    if (info instanceof ShortcutInfo) {
                        ShortcutInfo si = (ShortcutInfo) info;
                        if (si.isPromise() && si.getTargetComponent() != null) {
                            packagesToIgnore.add(si.getTargetComponent().getPackageName());
                        }
                    } else if (info instanceof LauncherAppWidgetInfo) {
                        LauncherAppWidgetInfo lawi = (LauncherAppWidgetInfo) info;
                        if (lawi.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY)) {
                            packagesToIgnore.add(lawi.providerName.getPackageName());
                        }
                    }
                }
            }
            mIconCache.updateDbIcons(packagesToIgnore);
        }

        private void onlyBindAllApps() {
            final Callbacks oldCallbacks = mCallbacks.get();
            if (oldCallbacks == null) {
                // This launcher has exited and nobody bothered to tell us.  Just bail.
                Log.w(TAG, "LoaderTask running with no launcher (onlyBindAllApps)");
                return;
            }

            // shallow copy
            @SuppressWarnings("unchecked")
            final ArrayList<AppInfo> list
                    = (ArrayList<AppInfo>) mBgAllAppsList.data.clone();
            Runnable r = new Runnable() {
                public void run() {
                    final long t = SystemClock.uptimeMillis();
                    final Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                    if (callbacks != null) {
                        callbacks.bindAllApplications(list);
                    }
                    if (DEBUG_LOADERS) {
                        Log.d(TAG, "bound all " + list.size() + " apps from cache in "
                                + (SystemClock.uptimeMillis() - t) + "ms");
                    }
                }
            };
            runOnMainThread(r);
        }

        private void loadAllApps() {
            final long loadTime = DEBUG_LOADERS ? SystemClock.uptimeMillis() : 0;

            final Callbacks oldCallbacks = mCallbacks.get();
            if (oldCallbacks == null) {
                // This launcher has exited and nobody bothered to tell us.  Just bail.
                Log.w(TAG, "LoaderTask running with no launcher (loadAllApps)");
                return;
            }

            final List<UserHandleCompat> profiles = mUserManager.getUserProfiles();

            // Clear the list of apps
            mBgAllAppsList.clear();
            for (UserHandleCompat user : profiles) {
                // Query for the set of apps
                final long qiaTime = DEBUG_LOADERS ? SystemClock.uptimeMillis() : 0;
                final List<LauncherActivityInfoCompat> apps = mLauncherApps.getActivityList(null, user);
                if (DEBUG_LOADERS) {
                    Log.d(TAG, "getActivityList took "
                            + (SystemClock.uptimeMillis()-qiaTime) + "ms for user " + user);
                    Log.d(TAG, "getActivityList got " + apps.size() + " apps for user " + user);
                }
                // Fail if we don't have any apps
                // TODO: Fix this. Only fail for the current user.
                if (apps == null || apps.isEmpty()) {
                    return;
                }
                boolean quietMode = mUserManager.isQuietModeEnabled(user);
                // Create the ApplicationInfos
                for (int i = 0; i < apps.size(); i++) {
                    LauncherActivityInfoCompat app = apps.get(i);
                    // This builds the icon bitmaps.
                    mBgAllAppsList.add(new AppInfo(mContext, app, user, mIconCache, quietMode));
                }

                final ManagedProfileHeuristic heuristic = ManagedProfileHeuristic.get(mContext, user);
                if (heuristic != null) {
                    final Runnable r = new Runnable() {

                        @Override
                        public void run() {
                            heuristic.processUserApps(apps);
                        }
                    };
                    runOnMainThread(new Runnable() {

                        @Override
                        public void run() {
                            // Check isLoadingWorkspace on the UI thread, as it is updated on
                            // the UI thread.
                            if (mIsLoadingAndBindingWorkspace) {
                                synchronized (mBindCompleteRunnables) {
                                    mBindCompleteRunnables.add(r);
                                }
                            } else {
                                runOnWorkerThread(r);
                            }
                        }
                    });
                }
            }
            // Huh? Shouldn't this be inside the Runnable below?
            final ArrayList<AppInfo> added = mBgAllAppsList.added;
            mBgAllAppsList.added = new ArrayList<AppInfo>();

            // Post callback on main thread
            mHandler.post(new Runnable() {
                public void run() {

                    final long bindTime = SystemClock.uptimeMillis();
                    final Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                    if (callbacks != null) {
                        callbacks.bindAllApplications(added);
                        if (DEBUG_LOADERS) {
                            Log.d(TAG, "bound " + added.size() + " apps in "
                                    + (SystemClock.uptimeMillis() - bindTime) + "ms");
                        }
                    } else {
                        Log.i(TAG, "not binding apps: no Launcher activity");
                    }
                }
            });
            // Cleanup any data stored for a deleted user.
            ManagedProfileHeuristic.processAllUsers(profiles, mContext);
            if (DEBUG_LOADERS) {
                Log.d(TAG, "Icons processed in "
                        + (SystemClock.uptimeMillis() - loadTime) + "ms");
            }
        }

        private void loadAndBindDeepShortcuts() {
            if (DEBUG_LOADERS) {
                Log.d(TAG, "loadAndBindDeepShortcuts mDeepShortcutsLoaded=" + mDeepShortcutsLoaded);
            }
            if (!mDeepShortcutsLoaded) {
                mBgDeepShortcutMap.clear();
                mHasShortcutHostPermission = mDeepShortcutManager.hasHostPermission();
                if (mHasShortcutHostPermission) {
                    for (UserHandleCompat user : mUserManager.getUserProfiles()) {
                        if (mUserManager.isUserUnlocked(user)) {
                            List<ShortcutInfoCompat> shortcuts = mDeepShortcutManager
                                    .queryForAllShortcuts(user);
                            updateDeepShortcutMap(null, user, shortcuts);
                        }
                    }
                }
                synchronized (LoaderTask.this) {
                    if (mStopped) {
                        return;
                    }
                    mDeepShortcutsLoaded = true;
                }
            }
            bindDeepShortcuts();
        }

        public void dumpState() {
            synchronized (sBgLock) {
                Log.d(TAG, "mLoaderTask.mContext=" + mContext);
                Log.d(TAG, "mLoaderTask.mStopped=" + mStopped);
                Log.d(TAG, "mLoaderTask.mLoadAndBindStepFinished=" + mLoadAndBindStepFinished);
                Log.d(TAG, "mItems size=" + sBgWorkspaceItems.size());
            }
        }
    }

    /**
     * Clear all the shortcuts for the given package, and re-add the new shortcuts.
     */
    private void updateDeepShortcutMap(
            String packageName, UserHandleCompat user, List<ShortcutInfoCompat> shortcuts) {
        if (packageName != null) {
            Iterator<ComponentKey> keysIter = mBgDeepShortcutMap.keySet().iterator();
            while (keysIter.hasNext()) {
                ComponentKey next = keysIter.next();
                if (next.componentName.getPackageName().equals(packageName)
                        && next.user.equals(user)) {
                    keysIter.remove();
                }
            }
        }

        // Now add the new shortcuts to the map.
        for (ShortcutInfoCompat shortcut : shortcuts) {
            boolean shouldShowInContainer = shortcut.isEnabled()
                    && (shortcut.isDeclaredInManifest() || shortcut.isDynamic());
            if (shouldShowInContainer) {
                ComponentKey targetComponent
                        = new ComponentKey(shortcut.getActivity(), shortcut.getUserHandle());
                mBgDeepShortcutMap.addToList(targetComponent, shortcut.getId());
            }
        }
    }

    public void bindDeepShortcuts() {
        final MultiHashMap<ComponentKey, String> shortcutMapCopy = mBgDeepShortcutMap.clone();
        Runnable r = new Runnable() {
            @Override
            public void run() {
                Callbacks callbacks = getCallback();
                if (callbacks != null) {
                    callbacks.bindDeepShortcutMap(shortcutMapCopy);
                }
            }
        };
        runOnMainThread(r);
    }

    /**
     * Refreshes the cached shortcuts if the shortcut permission has changed.
     * Current implementation simply reloads the workspace, but it can be optimized to
     * use partial updates similar to {@link UserManagerCompat}
     */
    public void refreshShortcutsIfRequired() {
        if (Utilities.isNycMR1OrAbove()) {
            sWorker.removeCallbacks(mShortcutPermissionCheckRunnable);
            sWorker.post(mShortcutPermissionCheckRunnable);
        }
    }

    /**
     * Called when the icons for packages have been updated in the icon cache.
     */
    public void onPackageIconsUpdated(HashSet<String> updatedPackages, UserHandleCompat user) {
        final Callbacks callbacks = getCallback();
        final ArrayList<AppInfo> updatedApps = new ArrayList<>();
        final ArrayList<ShortcutInfo> updatedShortcuts = new ArrayList<>();

        // If any package icon has changed (app was updated while launcher was dead),
        // update the corresponding shortcuts.
        synchronized (sBgLock) {
            for (ItemInfo info : sBgItemsIdMap) {
                if (info instanceof ShortcutInfo && user.equals(info.user)
                        && info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                    ShortcutInfo si = (ShortcutInfo) info;
                    ComponentName cn = si.getTargetComponent();
                    if (cn != null && updatedPackages.contains(cn.getPackageName())) {
                        si.updateIcon(mIconCache);
                        updatedShortcuts.add(si);
                    }
                }
            }
            mBgAllAppsList.updateIconsAndLabels(updatedPackages, user, updatedApps);
        }

        bindUpdatedShortcuts(updatedShortcuts, user);

        if (!updatedApps.isEmpty()) {
            mHandler.post(new Runnable() {

                public void run() {
                    Callbacks cb = getCallback();
                    if (cb != null && callbacks == cb) {
                        cb.bindAppsUpdated(updatedApps);
                    }
                }
            });
        }
    }

    private void bindUpdatedShortcuts(
            ArrayList<ShortcutInfo> updatedShortcuts, UserHandleCompat user) {
        bindUpdatedShortcuts(updatedShortcuts, new ArrayList<ShortcutInfo>(), user);
    }

    private void bindUpdatedShortcuts(
            final ArrayList<ShortcutInfo> updatedShortcuts,
            final ArrayList<ShortcutInfo> removedShortcuts,
            final UserHandleCompat user) {
        if (!updatedShortcuts.isEmpty() || !removedShortcuts.isEmpty()) {
            final Callbacks callbacks = getCallback();
            mHandler.post(new Runnable() {

                public void run() {
                    Callbacks cb = getCallback();
                    if (cb != null && callbacks == cb) {
                        cb.bindShortcutsChanged(updatedShortcuts, removedShortcuts, user);
                    }
                }
            });
        }
    }

    void enqueueItemUpdatedTask(Runnable task) {
        sWorker.post(task);
    }

    @Thunk class AppsAvailabilityCheck extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (sBgLock) {
                final LauncherAppsCompat launcherApps = LauncherAppsCompat
                        .getInstance(mApp.getContext());
                final PackageManager manager = context.getPackageManager();
                final ArrayList<String> packagesRemoved = new ArrayList<String>();
                final ArrayList<String> packagesUnavailable = new ArrayList<String>();
                for (Entry<UserHandleCompat, HashSet<String>> entry : sPendingPackages.entrySet()) {
                    UserHandleCompat user = entry.getKey();
                    packagesRemoved.clear();
                    packagesUnavailable.clear();
                    for (String pkg : entry.getValue()) {
                        if (!launcherApps.isPackageEnabledForProfile(pkg, user)) {
                            if (PackageManagerHelper.isAppOnSdcard(manager, pkg)) {
                                packagesUnavailable.add(pkg);
                            } else {
                                packagesRemoved.add(pkg);
                            }
                        }
                    }
                    if (!packagesRemoved.isEmpty()) {
                        enqueueItemUpdatedTask(new PackageUpdatedTask(PackageUpdatedTask.OP_REMOVE,
                                packagesRemoved.toArray(new String[packagesRemoved.size()]), user));
                    }
                    if (!packagesUnavailable.isEmpty()) {
                        enqueueItemUpdatedTask(new PackageUpdatedTask(PackageUpdatedTask.OP_UNAVAILABLE,
                                packagesUnavailable.toArray(new String[packagesUnavailable.size()]), user));
                    }
                }
                sPendingPackages.clear();
            }
        }
    }

    private class PackageUpdatedTask implements Runnable {
        int mOp;
        String[] mPackages;
        UserHandleCompat mUser;

        public static final int OP_NONE = 0;
        public static final int OP_ADD = 1;
        public static final int OP_UPDATE = 2;
        public static final int OP_REMOVE = 3; // uninstlled
        public static final int OP_UNAVAILABLE = 4; // external media unmounted
        public static final int OP_SUSPEND = 5; // package suspended
        public static final int OP_UNSUSPEND = 6; // package unsuspended
        public static final int OP_USER_AVAILABILITY_CHANGE = 7; // user available/unavailable

        public PackageUpdatedTask(int op, String[] packages, UserHandleCompat user) {
            mOp = op;
            mPackages = packages;
            mUser = user;
        }

        public void run() {
            if (!mHasLoaderCompletedOnce) {
                // Loader has not yet run.
                return;
            }
            final Context context = mApp.getContext();

            final String[] packages = mPackages;
            final int N = packages.length;
            FlagOp flagOp = FlagOp.NO_OP;
            StringFilter pkgFilter = StringFilter.of(new HashSet<>(Arrays.asList(packages)));
            switch (mOp) {
                case OP_ADD: {
                    for (int i=0; i<N; i++) {
                        if (DEBUG_LOADERS) Log.d(TAG, "mAllAppsList.addPackage " + packages[i]);
                        mIconCache.updateIconsForPkg(packages[i], mUser);
                        mBgAllAppsList.addPackage(context, packages[i], mUser);
                    }

                    ManagedProfileHeuristic heuristic = ManagedProfileHeuristic.get(context, mUser);
                    if (heuristic != null) {
                        heuristic.processPackageAdd(mPackages);
                    }
                    break;
                }
                case OP_UPDATE:
                    for (int i=0; i<N; i++) {
                        if (DEBUG_LOADERS) Log.d(TAG, "mAllAppsList.updatePackage " + packages[i]);
                        mIconCache.updateIconsForPkg(packages[i], mUser);
                        mBgAllAppsList.updatePackage(context, packages[i], mUser);
                        mApp.getWidgetCache().removePackage(packages[i], mUser);
                    }
                    // Since package was just updated, the target must be available now.
                    flagOp = FlagOp.removeFlag(ShortcutInfo.FLAG_DISABLED_NOT_AVAILABLE);
                    break;
                case OP_REMOVE: {
                    ManagedProfileHeuristic heuristic = ManagedProfileHeuristic.get(context, mUser);
                    if (heuristic != null) {
                        heuristic.processPackageRemoved(mPackages);
                    }
                    for (int i=0; i<N; i++) {
                        if (DEBUG_LOADERS) Log.d(TAG, "mAllAppsList.removePackage " + packages[i]);
                        mIconCache.removeIconsForPkg(packages[i], mUser);
                    }
                    // Fall through
                }
                case OP_UNAVAILABLE:
                    for (int i=0; i<N; i++) {
                        if (DEBUG_LOADERS) Log.d(TAG, "mAllAppsList.removePackage " + packages[i]);
                        mBgAllAppsList.removePackage(packages[i], mUser);
                        mApp.getWidgetCache().removePackage(packages[i], mUser);
                    }
                    flagOp = FlagOp.addFlag(ShortcutInfo.FLAG_DISABLED_NOT_AVAILABLE);
                    break;
                case OP_SUSPEND:
                case OP_UNSUSPEND:
                    flagOp = mOp == OP_SUSPEND ?
                            FlagOp.addFlag(ShortcutInfo.FLAG_DISABLED_SUSPENDED) :
                                    FlagOp.removeFlag(ShortcutInfo.FLAG_DISABLED_SUSPENDED);
                    if (DEBUG_LOADERS) Log.d(TAG, "mAllAppsList.(un)suspend " + N);
                    mBgAllAppsList.updatePackageFlags(pkgFilter, mUser, flagOp);
                    break;
                case OP_USER_AVAILABILITY_CHANGE:
                    flagOp = UserManagerCompat.getInstance(context).isQuietModeEnabled(mUser)
                            ? FlagOp.addFlag(ShortcutInfo.FLAG_DISABLED_QUIET_USER)
                            : FlagOp.removeFlag(ShortcutInfo.FLAG_DISABLED_QUIET_USER);
                    // We want to update all packages for this user.
                    pkgFilter = StringFilter.matchesAll();
                    mBgAllAppsList.updatePackageFlags(pkgFilter, mUser, flagOp);
                    break;
            }

            ArrayList<AppInfo> added = null;
            ArrayList<AppInfo> modified = null;
            final ArrayList<AppInfo> removedApps = new ArrayList<AppInfo>();

            if (mBgAllAppsList.added.size() > 0) {
                added = new ArrayList<>(mBgAllAppsList.added);
                mBgAllAppsList.added.clear();
            }
            if (mBgAllAppsList.modified.size() > 0) {
                modified = new ArrayList<>(mBgAllAppsList.modified);
                mBgAllAppsList.modified.clear();
            }
            if (mBgAllAppsList.removed.size() > 0) {
                removedApps.addAll(mBgAllAppsList.removed);
                mBgAllAppsList.removed.clear();
            }

            final HashMap<ComponentName, AppInfo> addedOrUpdatedApps = new HashMap<>();

            if (added != null) {
                addAppsToAllApps(context, added);
                for (AppInfo ai : added) {
                    addedOrUpdatedApps.put(ai.componentName, ai);
                }
            }

            if (modified != null) {
                final Callbacks callbacks = getCallback();
                final ArrayList<AppInfo> modifiedFinal = modified;
                for (AppInfo ai : modified) {
                    addedOrUpdatedApps.put(ai.componentName, ai);
                }

                mHandler.post(new Runnable() {
                    public void run() {
                        Callbacks cb = getCallback();
                        if (callbacks == cb && cb != null) {
                            callbacks.bindAppsUpdated(modifiedFinal);
                        }
                    }
                });
            }

            // Update shortcut infos
            if (mOp == OP_ADD || flagOp != FlagOp.NO_OP) {
                final ArrayList<ShortcutInfo> updatedShortcuts = new ArrayList<ShortcutInfo>();
                final ArrayList<ShortcutInfo> removedShortcuts = new ArrayList<ShortcutInfo>();
                final ArrayList<LauncherAppWidgetInfo> widgets = new ArrayList<LauncherAppWidgetInfo>();

                synchronized (sBgLock) {
                    for (ItemInfo info : sBgItemsIdMap) {
                        if (info instanceof ShortcutInfo && mUser.equals(info.user)) {
                            ShortcutInfo si = (ShortcutInfo) info;
                            boolean infoUpdated = false;
                            boolean shortcutUpdated = false;

                            // Update shortcuts which use iconResource.
                            if ((si.iconResource != null)
                                    && pkgFilter.matches(si.iconResource.packageName)) {
                                Bitmap icon = Utilities.createIconBitmap(
                                        si.iconResource.packageName,
                                        si.iconResource.resourceName, context);
                                if (icon != null) {
                                    si.setIcon(icon);
                                    si.usingFallbackIcon = false;
                                    infoUpdated = true;
                                }
                            }

                            ComponentName cn = si.getTargetComponent();
                            if (cn != null && pkgFilter.matches(cn.getPackageName())) {
                                AppInfo appInfo = addedOrUpdatedApps.get(cn);

                                if (si.isPromise()) {
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
                                            si.promisedIntent = intent;
                                        }
                                    }

                                    // Restore the shortcut.
                                    if (appInfo != null) {
                                        si.flags = appInfo.flags;
                                    }

                                    si.intent = si.promisedIntent;
                                    si.promisedIntent = null;
                                    si.status = ShortcutInfo.DEFAULT;
                                    infoUpdated = true;
                                    si.updateIcon(mIconCache);
                                }

                                if (appInfo != null && Intent.ACTION_MAIN.equals(si.intent.getAction())
                                        && si.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                                    si.updateIcon(mIconCache);
                                    si.title = Utilities.trim(appInfo.title);
                                    si.contentDescription = appInfo.contentDescription;
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
                                updateItemInDatabase(context, si);
                            }
                        } else if (info instanceof LauncherAppWidgetInfo && mOp == OP_ADD) {
                            LauncherAppWidgetInfo widgetInfo = (LauncherAppWidgetInfo) info;
                            if (mUser.equals(widgetInfo.user)
                                    && widgetInfo.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY)
                                    && pkgFilter.matches(widgetInfo.providerName.getPackageName())) {
                                widgetInfo.restoreStatus &=
                                        ~LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY &
                                        ~LauncherAppWidgetInfo.FLAG_RESTORE_STARTED;

                                // adding this flag ensures that launcher shows 'click to setup'
                                // if the widget has a config activity. In case there is no config
                                // activity, it will be marked as 'restored' during bind.
                                widgetInfo.restoreStatus |= LauncherAppWidgetInfo.FLAG_UI_NOT_READY;

                                widgets.add(widgetInfo);
                                updateItemInDatabase(context, widgetInfo);
                            }
                        }
                    }
                }

                bindUpdatedShortcuts(updatedShortcuts, removedShortcuts, mUser);
                if (!removedShortcuts.isEmpty()) {
                    deleteItemsFromDatabase(context, removedShortcuts);
                }

                if (!widgets.isEmpty()) {
                    final Callbacks callbacks = getCallback();
                    mHandler.post(new Runnable() {
                        public void run() {
                            Callbacks cb = getCallback();
                            if (callbacks == cb && cb != null) {
                                callbacks.bindWidgetsRestored(widgets);
                            }
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
                for (int i=0; i<N; i++) {
                    if (isPackageDisabled(context, packages[i], mUser)) {
                        removedPackages.add(packages[i]);
                    }
                }

                // Update removedComponents as some components can get removed during package update
                for (AppInfo info : removedApps) {
                    removedComponents.add(info.componentName);
                }
            }

            if (!removedPackages.isEmpty() || !removedComponents.isEmpty()) {
                for (String pn : removedPackages) {
                    deletePackageFromDatabase(context, pn, mUser);
                }
                for (ComponentName cn : removedComponents) {
                    deleteItemsFromDatabase(context, getItemInfoForComponentName(cn, mUser));
                }

                // Remove any queued items from the install queue
                InstallShortcutReceiver.removeFromInstallQueue(context, removedPackages, mUser);

                // Call the components-removed callback
                final Callbacks callbacks = getCallback();
                mHandler.post(new Runnable() {
                    public void run() {
                        Callbacks cb = getCallback();
                        if (callbacks == cb && cb != null) {
                            callbacks.bindWorkspaceComponentsRemoved(
                                    removedPackages, removedComponents, mUser);
                        }
                    }
                });
            }

            if (!removedApps.isEmpty()) {
                // Remove corresponding apps from All-Apps
                final Callbacks callbacks = getCallback();
                mHandler.post(new Runnable() {
                    public void run() {
                        Callbacks cb = getCallback();
                        if (callbacks == cb && cb != null) {
                            callbacks.bindAppInfosRemoved(removedApps);
                        }
                    }
                });
            }

            // Notify launcher of widget update. From marshmallow onwards we use AppWidgetHost to
            // get widget update signals.
            if (!Utilities.ATLEAST_MARSHMALLOW &&
                    (mOp == OP_ADD || mOp == OP_REMOVE || mOp == OP_UPDATE)) {
                final Callbacks callbacks = getCallback();
                mHandler.post(new Runnable() {
                    public void run() {
                        Callbacks cb = getCallback();
                        if (callbacks == cb && cb != null) {
                            callbacks.notifyWidgetProvidersChanged();
                        }
                    }
                });
            }
        }
    }

    /**
     * Repopulates the shortcut info, possibly updating any icon already on the workspace.
     */
    public void updateShortcutInfo(final ShortcutInfoCompat fullDetail, final ShortcutInfo info) {
        enqueueItemUpdatedTask(new Runnable() {
            @Override
            public void run() {
                info.updateFromDeepShortcutInfo(
                        fullDetail, LauncherAppState.getInstance().getContext());
                ArrayList<ShortcutInfo> update = new ArrayList<ShortcutInfo>();
                update.add(info);
                bindUpdatedShortcuts(update, fullDetail.getUserHandle());
            }
        });
    }

    private class ShortcutsChangedTask implements Runnable {
        private final String mPackageName;
        private final List<ShortcutInfoCompat> mShortcuts;
        private final UserHandleCompat mUser;
        private final boolean mUpdateIdMap;

        public ShortcutsChangedTask(String packageName, List<ShortcutInfoCompat> shortcuts,
                UserHandleCompat user, boolean updateIdMap) {
            mPackageName = packageName;
            mShortcuts = shortcuts;
            mUser = user;
            mUpdateIdMap = updateIdMap;
        }

        @Override
        public void run() {
            mDeepShortcutManager.onShortcutsChanged(mShortcuts);

            // Find ShortcutInfo's that have changed on the workspace.
            final ArrayList<ShortcutInfo> removedShortcutInfos = new ArrayList<>();
            MultiHashMap<String, ShortcutInfo> idsToWorkspaceShortcutInfos = new MultiHashMap<>();
            for (ItemInfo itemInfo : sBgItemsIdMap) {
                if (itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
                    ShortcutInfo si = (ShortcutInfo) itemInfo;
                    if (si.getPromisedIntent().getPackage().equals(mPackageName)
                            && si.user.equals(mUser)) {
                        idsToWorkspaceShortcutInfos.addToList(si.getDeepShortcutId(), si);
                    }
                }
            }

            final Context context = LauncherAppState.getInstance().getContext();
            final ArrayList<ShortcutInfo> updatedShortcutInfos = new ArrayList<>();
            if (!idsToWorkspaceShortcutInfos.isEmpty()) {
                // Update the workspace to reflect the changes to updated shortcuts residing on it.
                List<ShortcutInfoCompat> shortcuts = mDeepShortcutManager.queryForFullDetails(
                        mPackageName, new ArrayList<>(idsToWorkspaceShortcutInfos.keySet()), mUser);
                for (ShortcutInfoCompat fullDetails : shortcuts) {
                    List<ShortcutInfo> shortcutInfos = idsToWorkspaceShortcutInfos
                            .remove(fullDetails.getId());
                    if (!fullDetails.isPinned()) {
                        // The shortcut was previously pinned but is no longer, so remove it from
                        // the workspace and our pinned shortcut counts.
                        // Note that we put this check here, after querying for full details,
                        // because there's a possible race condition between pinning and
                        // receiving this callback.
                        removedShortcutInfos.addAll(shortcutInfos);
                        continue;
                    }
                    for (ShortcutInfo shortcutInfo : shortcutInfos) {
                        shortcutInfo.updateFromDeepShortcutInfo(fullDetails, context);
                        updatedShortcutInfos.add(shortcutInfo);
                    }
                }
            }

            // If there are still entries in idsToWorkspaceShortcutInfos, that means that
            // the corresponding shortcuts weren't passed in onShortcutsChanged(). This
            // means they were cleared, so we remove and unpin them now.
            for (String id : idsToWorkspaceShortcutInfos.keySet()) {
                removedShortcutInfos.addAll(idsToWorkspaceShortcutInfos.get(id));
            }

            bindUpdatedShortcuts(updatedShortcutInfos, removedShortcutInfos, mUser);
            if (!removedShortcutInfos.isEmpty()) {
                deleteItemsFromDatabase(context, removedShortcutInfos);
            }

            if (mUpdateIdMap) {
                // Update the deep shortcut map if the list of ids has changed for an activity.
                updateDeepShortcutMap(mPackageName, mUser, mShortcuts);
                bindDeepShortcuts();
            }
        }
    }

    /**
     * Task to handle changing of lock state of the user
     */
    private class UserLockStateChangedTask implements Runnable {

        private final UserHandleCompat mUser;

        public UserLockStateChangedTask(UserHandleCompat user) {
            mUser = user;
        }

        @Override
        public void run() {
            boolean isUserUnlocked = mUserManager.isUserUnlocked(mUser);
            Context context = mApp.getContext();

            HashMap<ShortcutKey, ShortcutInfoCompat> pinnedShortcuts = new HashMap<>();
            if (isUserUnlocked) {
                List<ShortcutInfoCompat> shortcuts =
                        mDeepShortcutManager.queryForPinnedShortcuts(null, mUser);
                if (mDeepShortcutManager.wasLastCallSuccess()) {
                    for (ShortcutInfoCompat shortcut : shortcuts) {
                        pinnedShortcuts.put(ShortcutKey.fromInfo(shortcut), shortcut);
                    }
                } else {
                    // Shortcut manager can fail due to some race condition when the lock state
                    // changes too frequently. For the purpose of the update,
                    // consider it as still locked.
                    isUserUnlocked = false;
                }
            }

            // Update the workspace to reflect the changes to updated shortcuts residing on it.
            ArrayList<ShortcutInfo> updatedShortcutInfos = new ArrayList<>();
            ArrayList<ShortcutInfo> deletedShortcutInfos = new ArrayList<>();
            for (ItemInfo itemInfo : sBgItemsIdMap) {
                if (itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
                        && mUser.equals(itemInfo.user)) {
                    ShortcutInfo si = (ShortcutInfo) itemInfo;
                    if (isUserUnlocked) {
                        ShortcutInfoCompat shortcut =
                                pinnedShortcuts.get(ShortcutKey.fromShortcutInfo(si));
                        // We couldn't verify the shortcut during loader. If its no longer available
                        // (probably due to clear data), delete the workspace item as well
                        if (shortcut == null) {
                            deletedShortcutInfos.add(si);
                            continue;
                        }
                        si.isDisabled &= ~ShortcutInfo.FLAG_DISABLED_LOCKED_USER;
                        si.updateFromDeepShortcutInfo(shortcut, context);
                    } else {
                        si.isDisabled |= ShortcutInfo.FLAG_DISABLED_LOCKED_USER;
                    }
                    updatedShortcutInfos.add(si);
                }
            }
            bindUpdatedShortcuts(updatedShortcutInfos, deletedShortcutInfos, mUser);
            if (!deletedShortcutInfos.isEmpty()) {
                deleteItemsFromDatabase(context, deletedShortcutInfos);
            }

            // Remove shortcut id map for that user
            Iterator<ComponentKey> keysIter = mBgDeepShortcutMap.keySet().iterator();
            while (keysIter.hasNext()) {
                if (keysIter.next().user.equals(mUser)) {
                    keysIter.remove();
                }
            }

            if (isUserUnlocked) {
                updateDeepShortcutMap(null, mUser, mDeepShortcutManager.queryForAllShortcuts(mUser));
            }
            bindDeepShortcuts();
        }
    }

    private void bindWidgetsModel(final Callbacks callbacks, final WidgetsModel model) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Callbacks cb = getCallback();
                if (callbacks == cb && cb != null) {
                    callbacks.bindWidgetsModel(model);
                }
            }
        });
    }

    public void refreshAndBindWidgetsAndShortcuts(
            final Callbacks callbacks, final boolean bindFirst) {
        runOnWorkerThread(new Runnable() {
            @Override
            public void run() {
                if (bindFirst && !mBgWidgetsModel.isEmpty()) {
                    bindWidgetsModel(callbacks, mBgWidgetsModel.clone());
                }
                final WidgetsModel model = mBgWidgetsModel.updateAndClone(mApp.getContext());
                bindWidgetsModel(callbacks, model);
                // update the Widget entries inside DB on the worker thread.
                LauncherAppState.getInstance().getWidgetCache().removeObsoletePreviews(
                        model.getRawList());
            }
        });
    }

    @Thunk static boolean isPackageDisabled(Context context, String packageName,
            UserHandleCompat user) {
        final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(context);
        return !launcherApps.isPackageEnabledForProfile(packageName, user);
    }

    public static boolean isValidPackageActivity(Context context, ComponentName cn,
            UserHandleCompat user) {
        if (cn == null) {
            return false;
        }
        final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(context);
        if (!launcherApps.isPackageEnabledForProfile(cn.getPackageName(), user)) {
            return false;
        }
        return launcherApps.isActivityEnabledForProfile(cn, user);
    }

    public static boolean isValidPackage(Context context, String packageName,
            UserHandleCompat user) {
        if (packageName == null) {
            return false;
        }
        final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(context);
        return launcherApps.isPackageEnabledForProfile(packageName, user);
    }

    /**
     * Make an ShortcutInfo object for a restored application or shortcut item that points
     * to a package that is not yet installed on the system.
     */
    public ShortcutInfo getRestoredItemInfo(Cursor c, Intent intent,
            int promiseType, int itemType, CursorIconInfo iconInfo) {
        final ShortcutInfo info = new ShortcutInfo();
        info.user = UserHandleCompat.myUserHandle();

        Bitmap icon = iconInfo.loadIcon(c, info);
        // the fallback icon
        if (icon == null) {
            mIconCache.getTitleAndIcon(info, intent, info.user, false /* useLowResIcon */);
        } else {
            info.setIcon(icon);
        }

        if ((promiseType & ShortcutInfo.FLAG_RESTORED_ICON) != 0) {
            String title = iconInfo.getTitle(c);
            if (!TextUtils.isEmpty(title)) {
                info.title = Utilities.trim(title);
            }
        } else if  ((promiseType & ShortcutInfo.FLAG_AUTOINTALL_ICON) != 0) {
            if (TextUtils.isEmpty(info.title)) {
                info.title = iconInfo.getTitle(c);
            }
        } else {
            throw new InvalidParameterException("Invalid restoreType " + promiseType);
        }

        info.contentDescription = mUserManager.getBadgedLabelForUser(info.title, info.user);
        info.itemType = itemType;
        info.promisedIntent = intent;
        info.status = promiseType;
        return info;
    }

    /**
     * Make an Intent object for a restored application or shortcut item that points
     * to the market page for the item.
     */
    @Thunk Intent getRestoredItemIntent(Cursor c, Context context, Intent intent) {
        ComponentName componentName = intent.getComponent();
        return getMarketIntent(componentName.getPackageName());
    }

    static Intent getMarketIntent(String packageName) {
        return new Intent(Intent.ACTION_VIEW)
            .setData(new Uri.Builder()
                .scheme("market")
                .authority("details")
                .appendQueryParameter("id", packageName)
                .build());
    }

    /**
     * Make an ShortcutInfo object for a shortcut that is an application.
     *
     * If c is not null, then it will be used to fill in missing data like the title and icon.
     */
    public ShortcutInfo getAppShortcutInfo(Intent intent,
            UserHandleCompat user, Cursor c, CursorIconInfo iconInfo,
            boolean allowMissingTarget, boolean useLowResIcon) {
        if (user == null) {
            Log.d(TAG, "Null user found in getShortcutInfo");
            return null;
        }

        ComponentName componentName = intent.getComponent();
        if (componentName == null) {
            Log.d(TAG, "Missing component found in getShortcutInfo");
            return null;
        }

        Intent newIntent = new Intent(intent.getAction(), null);
        newIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        newIntent.setComponent(componentName);
        LauncherActivityInfoCompat lai = mLauncherApps.resolveActivity(newIntent, user);
        if ((lai == null) && !allowMissingTarget) {
            Log.d(TAG, "Missing activity found in getShortcutInfo: " + componentName);
            return null;
        }

        final ShortcutInfo info = new ShortcutInfo();
        mIconCache.getTitleAndIcon(info, componentName, lai, user, false, useLowResIcon);
        if (mIconCache.isDefaultIcon(info.getIcon(mIconCache), user) && c != null) {
            Bitmap icon = iconInfo.loadIcon(c);
            info.setIcon(icon == null ? mIconCache.getDefaultIcon(user) : icon);
        }

        if (lai != null && PackageManagerHelper.isAppSuspended(lai.getApplicationInfo())) {
            info.isDisabled = ShortcutInfo.FLAG_DISABLED_SUSPENDED;
        }

        // from the db
        if (TextUtils.isEmpty(info.title) && c != null) {
            info.title = iconInfo.getTitle(c);
        }

        // fall back to the class name of the activity
        if (info.title == null) {
            info.title = componentName.getClassName();
        }

        info.itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
        info.user = user;
        info.contentDescription = mUserManager.getBadgedLabelForUser(info.title, info.user);
        if (lai != null) {
            info.flags = AppInfo.initFlags(lai);
        }
        return info;
    }

    static ArrayList<ItemInfo> filterItemInfos(Iterable<ItemInfo> infos,
            ItemInfoFilter f) {
        HashSet<ItemInfo> filtered = new HashSet<ItemInfo>();
        for (ItemInfo i : infos) {
            if (i instanceof ShortcutInfo) {
                ShortcutInfo info = (ShortcutInfo) i;
                ComponentName cn = info.getTargetComponent();
                if (cn != null && f.filterItem(null, info, cn)) {
                    filtered.add(info);
                }
            } else if (i instanceof FolderInfo) {
                FolderInfo info = (FolderInfo) i;
                for (ShortcutInfo s : info.contents) {
                    ComponentName cn = s.getTargetComponent();
                    if (cn != null && f.filterItem(info, s, cn)) {
                        filtered.add(s);
                    }
                }
            } else if (i instanceof LauncherAppWidgetInfo) {
                LauncherAppWidgetInfo info = (LauncherAppWidgetInfo) i;
                ComponentName cn = info.providerName;
                if (cn != null && f.filterItem(null, info, cn)) {
                    filtered.add(info);
                }
            }
        }
        return new ArrayList<ItemInfo>(filtered);
    }

    @Thunk ArrayList<ItemInfo> getItemInfoForComponentName(final ComponentName cname,
            final UserHandleCompat user) {
        ItemInfoFilter filter  = new ItemInfoFilter() {
            @Override
            public boolean filterItem(ItemInfo parent, ItemInfo info, ComponentName cn) {
                if (info.user == null) {
                    return cn.equals(cname);
                } else {
                    return cn.equals(cname) && info.user.equals(user);
                }
            }
        };
        return filterItemInfos(sBgItemsIdMap, filter);
    }

    /**
     * Make an ShortcutInfo object for a shortcut that isn't an application.
     */
    @Thunk ShortcutInfo getShortcutInfo(Cursor c, CursorIconInfo iconInfo) {
        final ShortcutInfo info = new ShortcutInfo();
        // Non-app shortcuts are only supported for current user.
        info.user = UserHandleCompat.myUserHandle();
        info.itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;

        // TODO: If there's an explicit component and we can't install that, delete it.

        loadInfoFromCursor(info, c, iconInfo);
        return info;
    }

    /**
     * Make an ShortcutInfo object for a shortcut that isn't an application.
     */
    public void loadInfoFromCursor(ShortcutInfo info, Cursor c, CursorIconInfo iconInfo) {
        info.title = iconInfo.getTitle(c);
        Bitmap icon = iconInfo.loadIcon(c, info);
        // the fallback icon
        if (icon == null) {
            icon = mIconCache.getDefaultIcon(info.user);
            info.usingFallbackIcon = true;
        }
        info.setIcon(icon);
    }

    ShortcutInfo infoFromShortcutIntent(Context context, Intent data) {
        Intent intent = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT);
        String name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        Parcelable bitmap = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON);

        if (intent == null) {
            // If the intent is null, we can't construct a valid ShortcutInfo, so we return null
            Log.e(TAG, "Can't construct ShorcutInfo with null intent");
            return null;
        }

        Bitmap icon = null;
        boolean customIcon = false;
        ShortcutIconResource iconResource = null;

        if (bitmap instanceof Bitmap) {
            icon = Utilities.createIconBitmap((Bitmap) bitmap, context);
            customIcon = true;
        } else {
            Parcelable extra = data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE);
            if (extra instanceof ShortcutIconResource) {
                iconResource = (ShortcutIconResource) extra;
                icon = Utilities.createIconBitmap(iconResource.packageName,
                        iconResource.resourceName, context);
            }
        }

        final ShortcutInfo info = new ShortcutInfo();

        // Only support intents for current user for now. Intents sent from other
        // users wouldn't get here without intent forwarding anyway.
        info.user = UserHandleCompat.myUserHandle();
        if (icon == null) {
            icon = mIconCache.getDefaultIcon(info.user);
            info.usingFallbackIcon = true;
        }
        info.setIcon(icon);

        info.title = Utilities.trim(name);
        info.contentDescription = mUserManager.getBadgedLabelForUser(info.title, info.user);
        info.intent = intent;
        info.iconResource = iconResource;

        return info;
    }

    /**
     * Return an existing FolderInfo object if we have encountered this ID previously,
     * or make a new one.
     */
    @Thunk static FolderInfo findOrMakeFolder(LongArrayMap<FolderInfo> folders, long id) {
        // See if a placeholder was created for us already
        FolderInfo folderInfo = folders.get(id);
        if (folderInfo == null) {
            // No placeholder -- create a new instance
            folderInfo = new FolderInfo();
            folders.put(id, folderInfo);
        }
        return folderInfo;
    }


    static boolean isValidProvider(AppWidgetProviderInfo provider) {
        return (provider != null) && (provider.provider != null)
                && (provider.provider.getPackageName() != null);
    }

    public void dumpState() {
        Log.d(TAG, "mCallbacks=" + mCallbacks);
        AppInfo.dumpApplicationInfoList(TAG, "mAllAppsList.data", mBgAllAppsList.data);
        AppInfo.dumpApplicationInfoList(TAG, "mAllAppsList.added", mBgAllAppsList.added);
        AppInfo.dumpApplicationInfoList(TAG, "mAllAppsList.removed", mBgAllAppsList.removed);
        AppInfo.dumpApplicationInfoList(TAG, "mAllAppsList.modified", mBgAllAppsList.modified);
        if (mLoaderTask != null) {
            mLoaderTask.dumpState();
        } else {
            Log.d(TAG, "mLoaderTask=null");
        }
    }

    public Callbacks getCallback() {
        return mCallbacks != null ? mCallbacks.get() : null;
    }

    /**
     * @return {@link FolderInfo} if its already loaded.
     */
    public FolderInfo findFolderById(Long folderId) {
        synchronized (sBgLock) {
            return sBgFolders.get(folderId);
        }
    }

    @Thunk class DeferredMainThreadExecutor implements Executor {

        @Override
        public void execute(Runnable command) {
            runOnMainThread(command);
        }
    }

    /**
     * @return the looper for the worker thread which can be used to start background tasks.
     */
    public static Looper getWorkerLooper() {
        return sWorkerThread.getLooper();
    }
}

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

import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.LauncherApps.Callback;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Parcelable;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.LauncherActivityInfoCompat;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.PackageInstallerCompat;
import com.android.launcher3.compat.PackageInstallerCompat.PackageInstallInfo;
import com.android.launcher3.compat.UserHandleCompat;
import com.android.launcher3.compat.UserManagerCompat;

import java.lang.ref.WeakReference;
import java.net.URISyntaxException;
import java.security.InvalidParameterException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

/**
 * Maintains in-memory state of the Launcher. It is expected that there should be only one
 * LauncherModel object held in a static. Also provide APIs for updating the database state
 * for the Launcher.
 */
public class LauncherModel extends BroadcastReceiver
        implements LauncherAppsCompat.OnAppsChangedCallbackCompat {
    static final boolean DEBUG_LOADERS = false;
    private static final boolean DEBUG_RECEIVER = false;
    private static final boolean REMOVE_UNRESTORED_ICONS = true;
    private static final boolean ADD_MANAGED_PROFILE_SHORTCUTS = false;

    static final String TAG = "Launcher.Model";

    // true = use a "More Apps" folder for non-workspace apps on upgrade
    // false = strew non-workspace apps across the workspace on upgrade
    public static final boolean UPGRADE_USE_MORE_APPS_FOLDER = false;
    public static final int LOADER_FLAG_NONE = 0;
    public static final int LOADER_FLAG_CLEAR_WORKSPACE = 1 << 0;
    public static final int LOADER_FLAG_MIGRATE_SHORTCUTS = 1 << 1;

    private static final int ITEMS_CHUNK = 6; // batch size for the workspace icons
    private static final long INVALID_SCREEN_ID = -1L;

    private final boolean mAppsCanBeOnRemoveableStorage;
    private final boolean mOldContentProviderExists;

    private final LauncherAppState mApp;
    private final Object mLock = new Object();
    private DeferredHandler mHandler = new DeferredHandler();
    private LoaderTask mLoaderTask;
    private boolean mIsLoaderTaskRunning;
    private volatile boolean mFlushingWorkerThread;

    /**
     * Maintain a set of packages per user, for which we added a shortcut on the workspace.
     */
    private static final String INSTALLED_SHORTCUTS_SET_PREFIX = "installed_shortcuts_set_for_user_";

    // Specific runnable types that are run on the main thread deferred handler, this allows us to
    // clear all queued binding runnables when the Launcher activity is destroyed.
    private static final int MAIN_THREAD_NORMAL_RUNNABLE = 0;
    private static final int MAIN_THREAD_BINDING_RUNNABLE = 1;

    private static final String MIGRATE_AUTHORITY = "com.android.launcher2.settings";

    private static final HandlerThread sWorkerThread = new HandlerThread("launcher-loader");
    static {
        sWorkerThread.start();
    }
    private static final Handler sWorker = new Handler(sWorkerThread.getLooper());

    // We start off with everything not loaded.  After that, we assume that
    // our monitoring of the package manager provides all updates and we never
    // need to do a requery.  These are only ever touched from the loader thread.
    private boolean mWorkspaceLoaded;
    private boolean mAllAppsLoaded;

    // When we are loading pages synchronously, we can't just post the binding of items on the side
    // pages as this delays the rotation process.  Instead, we wait for a callback from the first
    // draw (in Workspace) to initiate the binding of the remaining side pages.  Any time we start
    // a normal load, we also clear this set of Runnables.
    static final ArrayList<Runnable> mDeferredBindRunnables = new ArrayList<Runnable>();

    private WeakReference<Callbacks> mCallbacks;

    // < only access in worker thread >
    AllAppsList mBgAllAppsList;

    // The lock that must be acquired before referencing any static bg data structures.  Unlike
    // other locks, this one can generally be held long-term because we never expect any of these
    // static data structures to be referenced outside of the worker thread except on the first
    // load after configuration change.
    static final Object sBgLock = new Object();

    // sBgItemsIdMap maps *all* the ItemInfos (shortcuts, folders, and widgets) created by
    // LauncherModel to their ids
    static final HashMap<Long, ItemInfo> sBgItemsIdMap = new HashMap<Long, ItemInfo>();

    // sBgWorkspaceItems is passed to bindItems, which expects a list of all folders and shortcuts
    //       created by LauncherModel that are directly on the home screen (however, no widgets or
    //       shortcuts within folders).
    static final ArrayList<ItemInfo> sBgWorkspaceItems = new ArrayList<ItemInfo>();

    // sBgAppWidgets is all LauncherAppWidgetInfo created by LauncherModel. Passed to bindAppWidget()
    static final ArrayList<LauncherAppWidgetInfo> sBgAppWidgets =
        new ArrayList<LauncherAppWidgetInfo>();

    // sBgFolders is all FolderInfos created by LauncherModel. Passed to bindFolders()
    static final HashMap<Long, FolderInfo> sBgFolders = new HashMap<Long, FolderInfo>();

    // sBgDbIconCache is the set of ItemInfos that need to have their icons updated in the database
    static final HashMap<Object, byte[]> sBgDbIconCache = new HashMap<Object, byte[]>();

    // sBgWorkspaceScreens is the ordered set of workspace screens.
    static final ArrayList<Long> sBgWorkspaceScreens = new ArrayList<Long>();

    // sPendingPackages is a set of packages which could be on sdcard and are not available yet
    static final HashMap<UserHandleCompat, HashSet<String>> sPendingPackages =
            new HashMap<UserHandleCompat, HashSet<String>>();

    // </ only access in worker thread >

    private IconCache mIconCache;

    protected int mPreviousConfigMcc;

    private final LauncherAppsCompat mLauncherApps;
    private final UserManagerCompat mUserManager;

    public interface Callbacks {
        public boolean setLoadOnResume();
        public int getCurrentWorkspaceScreen();
        public void startBinding();
        public void bindItems(ArrayList<ItemInfo> shortcuts, int start, int end,
                              boolean forceAnimateIcons);
        public void bindScreens(ArrayList<Long> orderedScreenIds);
        public void bindAddScreens(ArrayList<Long> orderedScreenIds);
        public void bindFolders(HashMap<Long,FolderInfo> folders);
        public void finishBindingItems(boolean upgradePath);
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
        public void updatePackageState(ArrayList<PackageInstallInfo> installInfo);
        public void updatePackageBadge(String packageName);
        public void bindComponentsRemoved(ArrayList<String> packageNames,
                        ArrayList<AppInfo> appInfos, UserHandleCompat user, int reason);
        public void bindPackagesUpdated(ArrayList<Object> widgetsAndShortcuts);
        public void bindSearchablesChanged();
        public boolean isAllAppsButtonRank(int rank);
        public void onPageBoundSynchronously(int page);
        public void dumpLogsToLocalData();
    }

    public interface ItemInfoFilter {
        public boolean filterItem(ItemInfo parent, ItemInfo info, ComponentName cn);
    }

    LauncherModel(LauncherAppState app, IconCache iconCache, AppFilter appFilter) {
        Context context = app.getContext();

        mAppsCanBeOnRemoveableStorage = Environment.isExternalStorageRemovable();
        String oldProvider = context.getString(R.string.old_launcher_provider_uri);
        // This may be the same as MIGRATE_AUTHORITY, or it may be replaced by a different
        // resource string.
        String redirectAuthority = Uri.parse(oldProvider).getAuthority();
        ProviderInfo providerInfo =
                context.getPackageManager().resolveContentProvider(MIGRATE_AUTHORITY, 0);
        ProviderInfo redirectProvider =
                context.getPackageManager().resolveContentProvider(redirectAuthority, 0);

        Log.d(TAG, "Old launcher provider: " + oldProvider);
        mOldContentProviderExists = (providerInfo != null) && (redirectProvider != null);

        if (mOldContentProviderExists) {
            Log.d(TAG, "Old launcher provider exists.");
        } else {
            Log.d(TAG, "Old launcher provider does not exist.");
        }

        mApp = app;
        mBgAllAppsList = new AllAppsList(iconCache, appFilter);
        mIconCache = iconCache;

        final Resources res = context.getResources();
        Configuration config = res.getConfiguration();
        mPreviousConfigMcc = config.mcc;
        mLauncherApps = LauncherAppsCompat.getInstance(context);
        mUserManager = UserManagerCompat.getInstance(context);
    }

    /** Runs the specified runnable immediately if called from the main thread, otherwise it is
     * posted on the main thread handler. */
    private void runOnMainThread(Runnable r) {
        runOnMainThread(r, 0);
    }
    private void runOnMainThread(Runnable r, int type) {
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

    boolean canMigrateFromOldLauncherDb(Launcher launcher) {
        return mOldContentProviderExists && !launcher.isLauncherPreinstalled() ;
    }

    static boolean findNextAvailableIconSpaceInScreen(ArrayList<ItemInfo> items, int[] xy,
                                 long screen) {
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        final int xCount = (int) grid.numColumns;
        final int yCount = (int) grid.numRows;
        boolean[][] occupied = new boolean[xCount][yCount];

        int cellX, cellY, spanX, spanY;
        for (int i = 0; i < items.size(); ++i) {
            final ItemInfo item = items.get(i);
            if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                if (item.screenId == screen) {
                    cellX = item.cellX;
                    cellY = item.cellY;
                    spanX = item.spanX;
                    spanY = item.spanY;
                    for (int x = cellX; 0 <= x && x < cellX + spanX && x < xCount; x++) {
                        for (int y = cellY; 0 <= y && y < cellY + spanY && y < yCount; y++) {
                            occupied[x][y] = true;
                        }
                    }
                }
            }
        }

        return CellLayout.findVacantCell(xy, 1, 1, xCount, yCount, occupied);
    }
    static Pair<Long, int[]> findNextAvailableIconSpace(Context context, String name,
                                                        Intent launchIntent,
                                                        int firstScreenIndex,
                                                        ArrayList<Long> workspaceScreens) {
        // Lock on the app so that we don't try and get the items while apps are being added
        LauncherAppState app = LauncherAppState.getInstance();
        LauncherModel model = app.getModel();
        boolean found = false;
        synchronized (app) {
            if (sWorkerThread.getThreadId() != Process.myTid()) {
                // Flush the LauncherModel worker thread, so that if we just did another
                // processInstallShortcut, we give it time for its shortcut to get added to the
                // database (getItemsInLocalCoordinates reads the database)
                model.flushWorkerThread();
            }
            final ArrayList<ItemInfo> items = LauncherModel.getItemsInLocalCoordinates(context);

            // Try adding to the workspace screens incrementally, starting at the default or center
            // screen and alternating between +1, -1, +2, -2, etc. (using ~ ceil(i/2f)*(-1)^(i-1))
            firstScreenIndex = Math.min(firstScreenIndex, workspaceScreens.size());
            int count = workspaceScreens.size();
            for (int screen = firstScreenIndex; screen < count && !found; screen++) {
                int[] tmpCoordinates = new int[2];
                if (findNextAvailableIconSpaceInScreen(items, tmpCoordinates,
                        workspaceScreens.get(screen))) {
                    // Update the Launcher db
                    return new Pair<Long, int[]>(workspaceScreens.get(screen), tmpCoordinates);
                }
            }
        }
        return null;
    }

    public void setPackageState(final ArrayList<PackageInstallInfo> installInfo) {
        // Process the updated package state
        Runnable r = new Runnable() {
            public void run() {
                Callbacks callbacks = getCallback();
                if (callbacks != null) {
                    callbacks.updatePackageState(installInfo);
                }
            }
        };
        mHandler.post(r);
    }

    public void updatePackageBadge(final String packageName) {
        // Process the updated package badge
        Runnable r = new Runnable() {
            public void run() {
                Callbacks callbacks = getCallback();
                if (callbacks != null) {
                    callbacks.updatePackageBadge(packageName);
                }
            }
        };
        mHandler.post(r);
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

    public void addAndBindAddedWorkspaceApps(final Context context,
            final ArrayList<ItemInfo> workspaceApps) {
        final Callbacks callbacks = getCallback();

        if (workspaceApps == null) {
            throw new RuntimeException("workspaceApps and allAppsApps must not be null");
        }
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
                ArrayList<Long> workspaceScreens = new ArrayList<Long>();
                TreeMap<Integer, Long> orderedScreens = loadWorkspaceScreensDb(context);
                for (Integer i : orderedScreens.keySet()) {
                    long screenId = orderedScreens.get(i);
                    workspaceScreens.add(screenId);
                }

                synchronized(sBgLock) {
                    Iterator<ItemInfo> iter = workspaceApps.iterator();
                    while (iter.hasNext()) {
                        ItemInfo a = iter.next();
                        final String name = a.title.toString();
                        final Intent launchIntent = a.getIntent();

                        // Short-circuit this logic if the icon exists somewhere on the workspace
                        if (shortcutExists(context, name, launchIntent, a.user)) {
                            continue;
                        }

                        // Add this icon to the db, creating a new page if necessary.  If there
                        // is only the empty page then we just add items to the first page.
                        // Otherwise, we add them to the next pages.
                        int startSearchPageIndex = workspaceScreens.isEmpty() ? 0 : 1;
                        Pair<Long, int[]> coords = LauncherModel.findNextAvailableIconSpace(context,
                                name, launchIntent, startSearchPageIndex, workspaceScreens);
                        if (coords == null) {
                            LauncherProvider lp = LauncherAppState.getLauncherProvider();

                            // If we can't find a valid position, then just add a new screen.
                            // This takes time so we need to re-queue the add until the new
                            // page is added.  Create as many screens as necessary to satisfy
                            // the startSearchPageIndex.
                            int numPagesToAdd = Math.max(1, startSearchPageIndex + 1 -
                                    workspaceScreens.size());
                            while (numPagesToAdd > 0) {
                                long screenId = lp.generateNewScreenId();
                                // Save the screen id for binding in the workspace
                                workspaceScreens.add(screenId);
                                addedWorkspaceScreensFinal.add(screenId);
                                numPagesToAdd--;
                            }

                            // Find the coordinate again
                            coords = LauncherModel.findNextAvailableIconSpace(context,
                                    name, launchIntent, startSearchPageIndex, workspaceScreens);
                        }
                        if (coords == null) {
                            throw new RuntimeException("Coordinates should not be null");
                        }

                        ShortcutInfo shortcutInfo;
                        if (a instanceof ShortcutInfo) {
                            shortcutInfo = (ShortcutInfo) a;
                        } else if (a instanceof AppInfo) {
                            shortcutInfo = ((AppInfo) a).makeShortcut();
                        } else {
                            throw new RuntimeException("Unexpected info type");
                        }

                        // Add the shortcut to the db
                        addItemToDatabase(context, shortcutInfo,
                                LauncherSettings.Favorites.CONTAINER_DESKTOP,
                                coords.first, coords.second[0], coords.second[1], false);
                        // Save the ShortcutInfo for binding in the workspace
                        addedShortcutsFinal.add(shortcutInfo);
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

    public void unbindItemInfosAndClearQueuedBindRunnables() {
        if (sWorkerThread.getThreadId() == Process.myTid()) {
            throw new RuntimeException("Expected unbindLauncherItemInfos() to be called from the " +
                    "main thread");
        }

        // Clear any deferred bind runnables
        synchronized (mDeferredBindRunnables) {
            mDeferredBindRunnables.clear();
        }
        // Remove any queued bind runnables
        mHandler.cancelAllRunnablesOfType(MAIN_THREAD_BINDING_RUNNABLE);
        // Unbind all the workspace items
        unbindWorkspaceItemsOnMainThread();
    }

    /** Unbinds all the sBgWorkspaceItems and sBgAppWidgets on the main thread */
    void unbindWorkspaceItemsOnMainThread() {
        // Ensure that we don't use the same workspace items data structure on the main thread
        // by making a copy of workspace items first.
        final ArrayList<ItemInfo> tmpWorkspaceItems = new ArrayList<ItemInfo>();
        final ArrayList<ItemInfo> tmpAppWidgets = new ArrayList<ItemInfo>();
        synchronized (sBgLock) {
            tmpWorkspaceItems.addAll(sBgWorkspaceItems);
            tmpAppWidgets.addAll(sBgAppWidgets);
        }
        Runnable r = new Runnable() {
                @Override
                public void run() {
                   for (ItemInfo item : tmpWorkspaceItems) {
                       item.unbind();
                   }
                   for (ItemInfo item : tmpAppWidgets) {
                       item.unbind();
                   }
                }
            };
        runOnMainThread(r);
    }

    /**
     * Adds an item to the DB if it was not created previously, or move it to a new
     * <container, screen, cellX, cellY>
     */
    static void addOrMoveItemInDatabase(Context context, ItemInfo item, long container,
            long screenId, int cellX, int cellY) {
        if (item.container == ItemInfo.NO_ID) {
            // From all apps
            addItemToDatabase(context, item, container, screenId, cellX, cellY, false);
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
                        modelShortcut.spanY == shortcut.spanY &&
                        ((modelShortcut.dropPos == null && shortcut.dropPos == null) ||
                        (modelShortcut.dropPos != null &&
                                shortcut.dropPos != null &&
                                modelShortcut.dropPos[0] == shortcut.dropPos[0] &&
                        modelShortcut.dropPos[1] == shortcut.dropPos[1]))) {
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
        final Uri uri = LauncherSettings.Favorites.getContentUri(itemId, false);
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
                    final Uri uri = LauncherSettings.Favorites.getContentUri(itemId, false);
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

    public void flushWorkerThread() {
        mFlushingWorkerThread = true;
        Runnable waiter = new Runnable() {
                public void run() {
                    synchronized (this) {
                        notifyAll();
                        mFlushingWorkerThread = false;
                    }
                }
            };

        synchronized(waiter) {
            runOnWorkerThread(waiter);
            if (mLoaderTask != null) {
                synchronized(mLoaderTask) {
                    mLoaderTask.notify();
                }
            }
            boolean success = false;
            while (!success) {
                try {
                    waiter.wait();
                    success = true;
                } catch (InterruptedException e) {
                }
            }
        }
    }

    /**
     * Move an item in the DB to a new <container, screen, cellX, cellY>
     */
    static void moveItemInDatabase(Context context, final ItemInfo item, final long container,
            final long screenId, final int cellX, final int cellY) {
        item.container = container;
        item.cellX = cellX;
        item.cellY = cellY;

        // We store hotseat items in canonical form which is this orientation invariant position
        // in the hotseat
        if (context instanceof Launcher && screenId < 0 &&
                container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            item.screenId = ((Launcher) context).getHotseat().getOrderInHotseat(cellX, cellY);
        } else {
            item.screenId = screenId;
        }

        final ContentValues values = new ContentValues();
        values.put(LauncherSettings.Favorites.CONTAINER, item.container);
        values.put(LauncherSettings.Favorites.CELLX, item.cellX);
        values.put(LauncherSettings.Favorites.CELLY, item.cellY);
        values.put(LauncherSettings.Favorites.SCREEN, item.screenId);

        updateItemInDatabaseHelper(context, values, item, "moveItemInDatabase");
    }

    /**
     * Move items in the DB to a new <container, screen, cellX, cellY>. We assume that the
     * cellX, cellY have already been updated on the ItemInfos.
     */
    static void moveItemsInDatabase(Context context, final ArrayList<ItemInfo> items,
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
                item.screenId = ((Launcher) context).getHotseat().getOrderInHotseat(item.cellX,
                        item.cellY);
            } else {
                item.screenId = screen;
            }

            final ContentValues values = new ContentValues();
            values.put(LauncherSettings.Favorites.CONTAINER, item.container);
            values.put(LauncherSettings.Favorites.CELLX, item.cellX);
            values.put(LauncherSettings.Favorites.CELLY, item.cellY);
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
            item.screenId = ((Launcher) context).getHotseat().getOrderInHotseat(cellX, cellY);
        } else {
            item.screenId = screenId;
        }

        final ContentValues values = new ContentValues();
        values.put(LauncherSettings.Favorites.CONTAINER, item.container);
        values.put(LauncherSettings.Favorites.CELLX, item.cellX);
        values.put(LauncherSettings.Favorites.CELLY, item.cellY);
        values.put(LauncherSettings.Favorites.SPANX, item.spanX);
        values.put(LauncherSettings.Favorites.SPANY, item.spanY);
        values.put(LauncherSettings.Favorites.SCREEN, item.screenId);

        updateItemInDatabaseHelper(context, values, item, "modifyItemInDatabase");
    }

    /**
     * Update an item to the database in a specified container.
     */
    static void updateItemInDatabase(Context context, final ItemInfo item) {
        final ContentValues values = new ContentValues();
        item.onAddToDatabase(context, values);
        item.updateValuesWithCoordinates(values, item.cellX, item.cellY);
        updateItemInDatabaseHelper(context, values, item, "updateItemInDatabase");
    }

    /**
     * Returns true if the shortcuts already exists in the database.
     * we identify a shortcut by its title and intent.
     */
    static boolean shortcutExists(Context context, String title, Intent intent,
            UserHandleCompat user) {
        final ContentResolver cr = context.getContentResolver();
        final Intent intentWithPkg, intentWithoutPkg;

        if (intent.getComponent() != null) {
            // If component is not null, an intent with null package will produce
            // the same result and should also be a match.
            if (intent.getPackage() != null) {
                intentWithPkg = intent;
                intentWithoutPkg = new Intent(intent).setPackage(null);
            } else {
                intentWithPkg = new Intent(intent).setPackage(
                        intent.getComponent().getPackageName());
                intentWithoutPkg = intent;
            }
        } else {
            intentWithPkg = intent;
            intentWithoutPkg = intent;
        }
        String userSerial = Long.toString(UserManagerCompat.getInstance(context)
                .getSerialNumberForUser(user));
        Cursor c = cr.query(LauncherSettings.Favorites.CONTENT_URI,
            new String[] { "title", "intent", "profileId" },
            "title=? and (intent=? or intent=?) and profileId=?",
            new String[] { title, intentWithPkg.toUri(0), intentWithoutPkg.toUri(0), userSerial },
            null);
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    /**
     * Returns an ItemInfo array containing all the items in the LauncherModel.
     * The ItemInfo.id is not set through this function.
     */
    static ArrayList<ItemInfo> getItemsInLocalCoordinates(Context context) {
        ArrayList<ItemInfo> items = new ArrayList<ItemInfo>();
        final ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(LauncherSettings.Favorites.CONTENT_URI, new String[] {
                LauncherSettings.Favorites.ITEM_TYPE, LauncherSettings.Favorites.CONTAINER,
                LauncherSettings.Favorites.SCREEN,
                LauncherSettings.Favorites.CELLX, LauncherSettings.Favorites.CELLY,
                LauncherSettings.Favorites.SPANX, LauncherSettings.Favorites.SPANY,
                LauncherSettings.Favorites.PROFILE_ID }, null, null, null);

        final int itemTypeIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ITEM_TYPE);
        final int containerIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CONTAINER);
        final int screenIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SCREEN);
        final int cellXIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLX);
        final int cellYIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLY);
        final int spanXIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SPANX);
        final int spanYIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SPANY);
        final int profileIdIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.PROFILE_ID);
        UserManagerCompat userManager = UserManagerCompat.getInstance(context);
        try {
            while (c.moveToNext()) {
                ItemInfo item = new ItemInfo();
                item.cellX = c.getInt(cellXIndex);
                item.cellY = c.getInt(cellYIndex);
                item.spanX = Math.max(1, c.getInt(spanXIndex));
                item.spanY = Math.max(1, c.getInt(spanYIndex));
                item.container = c.getInt(containerIndex);
                item.itemType = c.getInt(itemTypeIndex);
                item.screenId = c.getInt(screenIndex);
                long serialNumber = c.getInt(profileIdIndex);
                item.user = userManager.getUserForSerialNumber(serialNumber);
                // Skip if user has been deleted.
                if (item.user != null) {
                    items.add(item);
                }
            }
        } catch (Exception e) {
            items.clear();
        } finally {
            c.close();
        }

        return items;
    }

    /**
     * Find a folder in the db, creating the FolderInfo if necessary, and adding it to folderList.
     */
    FolderInfo getFolderById(Context context, HashMap<Long,FolderInfo> folderList, long id) {
        final ContentResolver cr = context.getContentResolver();
        Cursor c = cr.query(LauncherSettings.Favorites.CONTENT_URI, null,
                "_id=? and (itemType=? or itemType=?)",
                new String[] { String.valueOf(id),
                        String.valueOf(LauncherSettings.Favorites.ITEM_TYPE_FOLDER)}, null);

        try {
            if (c.moveToFirst()) {
                final int itemTypeIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ITEM_TYPE);
                final int titleIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.TITLE);
                final int containerIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CONTAINER);
                final int screenIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.SCREEN);
                final int cellXIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLX);
                final int cellYIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.CELLY);

                FolderInfo folderInfo = null;
                switch (c.getInt(itemTypeIndex)) {
                    case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                        folderInfo = findOrMakeFolder(folderList, id);
                        break;
                }

                folderInfo.title = c.getString(titleIndex);
                folderInfo.id = id;
                folderInfo.container = c.getInt(containerIndex);
                folderInfo.screenId = c.getInt(screenIndex);
                folderInfo.cellX = c.getInt(cellXIndex);
                folderInfo.cellY = c.getInt(cellYIndex);

                return folderInfo;
            }
        } finally {
            c.close();
        }

        return null;
    }

    /**
     * Add an item to the database in a specified container. Sets the container, screen, cellX and
     * cellY fields of the item. Also assigns an ID to the item.
     */
    static void addItemToDatabase(Context context, final ItemInfo item, final long container,
            final long screenId, final int cellX, final int cellY, final boolean notify) {
        item.container = container;
        item.cellX = cellX;
        item.cellY = cellY;
        // We store hotseat items in canonical form which is this orientation invariant position
        // in the hotseat
        if (context instanceof Launcher && screenId < 0 &&
                container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            item.screenId = ((Launcher) context).getHotseat().getOrderInHotseat(cellX, cellY);
        } else {
            item.screenId = screenId;
        }

        final ContentValues values = new ContentValues();
        final ContentResolver cr = context.getContentResolver();
        item.onAddToDatabase(context, values);

        item.id = LauncherAppState.getLauncherProvider().generateNewItemId();
        values.put(LauncherSettings.Favorites._ID, item.id);
        item.updateValuesWithCoordinates(values, item.cellX, item.cellY);

        final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        Runnable r = new Runnable() {
            public void run() {
                cr.insert(notify ? LauncherSettings.Favorites.CONTENT_URI :
                        LauncherSettings.Favorites.CONTENT_URI_NO_NOTIFICATION, values);

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

    /**
     * Creates a new unique child id, for a given cell span across all layouts.
     */
    static int getCellLayoutChildId(
            long container, long screen, int localCellX, int localCellY, int spanX, int spanY) {
        return (((int) container & 0xFF) << 24)
                | ((int) screen & 0xFF) << 16 | (localCellX & 0xFF) << 8 | (localCellY & 0xFF);
    }

    private static ArrayList<ItemInfo> getItemsByPackageName(
            final String pn, final UserHandleCompat user) {
        ItemInfoFilter filter  = new ItemInfoFilter() {
            @Override
            public boolean filterItem(ItemInfo parent, ItemInfo info, ComponentName cn) {
                return cn.getPackageName().equals(pn) && info.user.equals(user);
            }
        };
        return filterItemInfos(sBgItemsIdMap.values(), filter);
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
     * @param context
     * @param item
     */
    static void deleteItemFromDatabase(Context context, final ItemInfo item) {
        ArrayList<ItemInfo> items = new ArrayList<ItemInfo>();
        items.add(item);
        deleteItemsFromDatabase(context, items);
    }

    /**
     * Removes the specified items from the database
     * @param context
     * @param item
     */
    static void deleteItemsFromDatabase(Context context, final ArrayList<? extends ItemInfo> items) {
        final ContentResolver cr = context.getContentResolver();

        Runnable r = new Runnable() {
            public void run() {
                for (ItemInfo item : items) {
                    final Uri uri = LauncherSettings.Favorites.getContentUri(item.id, false);
                    cr.delete(uri, null, null);

                    // Lock on mBgLock *after* the db operation
                    synchronized (sBgLock) {
                        switch (item.itemType) {
                            case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                                sBgFolders.remove(item.id);
                                for (ItemInfo info: sBgItemsIdMap.values()) {
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
                            case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                            case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                                sBgWorkspaceItems.remove(item);
                                break;
                            case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
                                sBgAppWidgets.remove((LauncherAppWidgetInfo) item);
                                break;
                        }
                        sBgItemsIdMap.remove(item.id);
                        sBgDbIconCache.remove(item);
                    }
                }
            }
        };
        runOnWorkerThread(r);
    }

    /**
     * Update the order of the workspace screens in the database. The array list contains
     * a list of screen ids in the order that they should appear.
     */
    void updateWorkspaceScreenOrder(Context context, final ArrayList<Long> screens) {
        // Log to disk
        Launcher.addDumpLog(TAG, "11683562 - updateWorkspaceScreenOrder()", true);
        Launcher.addDumpLog(TAG, "11683562 -   screens: " + TextUtils.join(", ", screens), true);

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
     * Remove the contents of the specified folder from the database
     */
    static void deleteFolderContentsFromDatabase(Context context, final FolderInfo info) {
        final ContentResolver cr = context.getContentResolver();

        Runnable r = new Runnable() {
            public void run() {
                cr.delete(LauncherSettings.Favorites.getContentUri(info.id, false), null, null);
                // Lock on mBgLock *after* the db operation
                synchronized (sBgLock) {
                    sBgItemsIdMap.remove(info.id);
                    sBgFolders.remove(info.id);
                    sBgDbIconCache.remove(info);
                    sBgWorkspaceItems.remove(info);
                }

                cr.delete(LauncherSettings.Favorites.CONTENT_URI_NO_NOTIFICATION,
                        LauncherSettings.Favorites.CONTAINER + "=" + info.id, null);
                // Lock on mBgLock *after* the db operation
                synchronized (sBgLock) {
                    for (ItemInfo childInfo : info.contents) {
                        sBgItemsIdMap.remove(childInfo.id);
                        sBgDbIconCache.remove(childInfo);
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
            mCallbacks = new WeakReference<Callbacks>(callbacks);
        }
    }

    @Override
    public void onPackageChanged(String packageName, UserHandleCompat user) {
        int op = PackageUpdatedTask.OP_UPDATE;
        enqueuePackageUpdated(new PackageUpdatedTask(op, new String[] { packageName },
                user));
    }

    @Override
    public void onPackageRemoved(String packageName, UserHandleCompat user) {
        int op = PackageUpdatedTask.OP_REMOVE;
        enqueuePackageUpdated(new PackageUpdatedTask(op, new String[] { packageName },
                user));
    }

    @Override
    public void onPackageAdded(String packageName, UserHandleCompat user) {
        int op = PackageUpdatedTask.OP_ADD;
        enqueuePackageUpdated(new PackageUpdatedTask(op, new String[] { packageName },
                user));
    }

    @Override
    public void onPackagesAvailable(String[] packageNames, UserHandleCompat user,
            boolean replacing) {
        if (!replacing) {
            enqueuePackageUpdated(new PackageUpdatedTask(PackageUpdatedTask.OP_ADD, packageNames,
                    user));
            if (mAppsCanBeOnRemoveableStorage) {
                // Only rebind if we support removable storage. It catches the
                // case where
                // apps on the external sd card need to be reloaded
                startLoaderFromBackground();
            }
        } else {
            // If we are replacing then just update the packages in the list
            enqueuePackageUpdated(new PackageUpdatedTask(PackageUpdatedTask.OP_UPDATE,
                    packageNames, user));
        }
    }

    @Override
    public void onPackagesUnavailable(String[] packageNames, UserHandleCompat user,
            boolean replacing) {
        if (!replacing) {
            enqueuePackageUpdated(new PackageUpdatedTask(
                    PackageUpdatedTask.OP_UNAVAILABLE, packageNames,
                    user));
        }

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
        } else if (Intent.ACTION_CONFIGURATION_CHANGED.equals(action)) {
             // Check if configuration change was an mcc/mnc change which would affect app resources
             // and we would need to clear out the labels in all apps/workspace. Same handling as
             // above for ACTION_LOCALE_CHANGED
             Configuration currentConfig = context.getResources().getConfiguration();
             if (mPreviousConfigMcc != currentConfig.mcc) {
                   Log.d(TAG, "Reload apps on config change. curr_mcc:"
                       + currentConfig.mcc + " prevmcc:" + mPreviousConfigMcc);
                   forceReload();
             }
             // Update previousConfig
             mPreviousConfigMcc = currentConfig.mcc;
        } else if (SearchManager.INTENT_GLOBAL_SEARCH_ACTIVITY_CHANGED.equals(action) ||
                   SearchManager.INTENT_ACTION_SEARCHABLES_CHANGED.equals(action)) {
            Callbacks callbacks = getCallback();
            if (callbacks != null) {
                callbacks.bindSearchablesChanged();
            }
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
        }
    }

    /**
     * When the launcher is in the background, it's possible for it to miss paired
     * configuration changes.  So whenever we trigger the loader from the background
     * tell the launcher that it needs to re-run the loader when it comes back instead
     * of doing it now.
     */
    public void startLoaderFromBackground() {
        boolean runLoader = false;
        Callbacks callbacks = getCallback();
        if (callbacks != null) {
            // Only actually run the loader if they're not paused.
            if (!callbacks.setLoadOnResume()) {
                runLoader = true;
            }
        }
        if (runLoader) {
            startLoader(false, PagedView.INVALID_RESTORE_PAGE);
        }
    }

    // If there is already a loader task running, tell it to stop.
    // returns true if isLaunching() was true on the old task
    private boolean stopLoaderLocked() {
        boolean isLaunching = false;
        LoaderTask oldTask = mLoaderTask;
        if (oldTask != null) {
            if (oldTask.isLaunching()) {
                isLaunching = true;
            }
            oldTask.stopLocked();
        }
        return isLaunching;
    }

    public boolean isCurrentCallbacks(Callbacks callbacks) {
        return (mCallbacks != null && mCallbacks.get() == callbacks);
    }

    public void startLoader(boolean isLaunching, int synchronousBindPage) {
        startLoader(isLaunching, synchronousBindPage, LOADER_FLAG_NONE);
    }

    public void startLoader(boolean isLaunching, int synchronousBindPage, int loadFlags) {
        synchronized (mLock) {
            if (DEBUG_LOADERS) {
                Log.d(TAG, "startLoader isLaunching=" + isLaunching);
            }

            // Clear any deferred bind-runnables from the synchronized load process
            // We must do this before any loading/binding is scheduled below.
            synchronized (mDeferredBindRunnables) {
                mDeferredBindRunnables.clear();
            }

            // Don't bother to start the thread if we know it's not going to do anything
            if (mCallbacks != null && mCallbacks.get() != null) {
                // If there is already one running, tell it to stop.
                // also, don't downgrade isLaunching if we're already running
                isLaunching = isLaunching || stopLoaderLocked();
                mLoaderTask = new LoaderTask(mApp.getContext(), isLaunching, loadFlags);
                if (synchronousBindPage != PagedView.INVALID_RESTORE_PAGE
                        && mAllAppsLoaded && mWorkspaceLoaded) {
                    mLoaderTask.runBindSynchronousPage(synchronousBindPage);
                } else {
                    sWorkerThread.setPriority(Thread.NORM_PRIORITY);
                    sWorker.post(mLoaderTask);
                }
            }
        }
    }

    void bindRemainingSynchronousPages() {
        // Post the remaining side pages to be loaded
        if (!mDeferredBindRunnables.isEmpty()) {
            Runnable[] deferredBindRunnables = null;
            synchronized (mDeferredBindRunnables) {
                deferredBindRunnables = mDeferredBindRunnables.toArray(
                        new Runnable[mDeferredBindRunnables.size()]);
                mDeferredBindRunnables.clear();
            }
            for (final Runnable r : deferredBindRunnables) {
                mHandler.post(r, MAIN_THREAD_BINDING_RUNNABLE);
            }
        }
    }

    public void stopLoader() {
        synchronized (mLock) {
            if (mLoaderTask != null) {
                mLoaderTask.stopLocked();
            }
        }
    }

    /** Loads the workspace screens db into a map of Rank -> ScreenId */
    private static TreeMap<Integer, Long> loadWorkspaceScreensDb(Context context) {
        final ContentResolver contentResolver = context.getContentResolver();
        final Uri screensUri = LauncherSettings.WorkspaceScreens.CONTENT_URI;
        final Cursor sc = contentResolver.query(screensUri, null, null, null, null);
        TreeMap<Integer, Long> orderedScreens = new TreeMap<Integer, Long>();

        try {
            final int idIndex = sc.getColumnIndexOrThrow(
                    LauncherSettings.WorkspaceScreens._ID);
            final int rankIndex = sc.getColumnIndexOrThrow(
                    LauncherSettings.WorkspaceScreens.SCREEN_RANK);
            while (sc.moveToNext()) {
                try {
                    long screenId = sc.getLong(idIndex);
                    int rank = sc.getInt(rankIndex);
                    orderedScreens.put(rank, screenId);
                } catch (Exception e) {
                    Launcher.addDumpLog(TAG, "Desktop items loading interrupted - invalid screens: " + e, true);
                }
            }
        } finally {
            sc.close();
        }

        // Log to disk
        Launcher.addDumpLog(TAG, "11683562 - loadWorkspaceScreensDb()", true);
        ArrayList<String> orderedScreensPairs= new ArrayList<String>();
        for (Integer i : orderedScreens.keySet()) {
            orderedScreensPairs.add("{ " + i + ": " + orderedScreens.get(i) + " }");
        }
        Launcher.addDumpLog(TAG, "11683562 -   screens: " +
                TextUtils.join(", ", orderedScreensPairs), true);
        return orderedScreens;
    }

    public boolean isAllAppsLoaded() {
        return mAllAppsLoaded;
    }

    boolean isLoadingWorkspace() {
        synchronized (mLock) {
            if (mLoaderTask != null) {
                return mLoaderTask.isLoadingWorkspace();
            }
        }
        return false;
    }

    /**
     * Runnable for the thread that loads the contents of the launcher:
     *   - workspace icons
     *   - widgets
     *   - all apps icons
     */
    private class LoaderTask implements Runnable {
        private Context mContext;
        private boolean mIsLaunching;
        private boolean mIsLoadingAndBindingWorkspace;
        private boolean mStopped;
        private boolean mLoadAndBindStepFinished;
        private int mFlags;

        private HashMap<Object, CharSequence> mLabelCache;

        LoaderTask(Context context, boolean isLaunching, int flags) {
            mContext = context;
            mIsLaunching = isLaunching;
            mLabelCache = new HashMap<Object, CharSequence>();
            mFlags = flags;
        }

        boolean isLaunching() {
            return mIsLaunching;
        }

        boolean isLoadingWorkspace() {
            return mIsLoadingAndBindingWorkspace;
        }

        /** Returns whether this is an upgrade path */
        private boolean loadAndBindWorkspace() {
            mIsLoadingAndBindingWorkspace = true;

            // Load the workspace
            if (DEBUG_LOADERS) {
                Log.d(TAG, "loadAndBindWorkspace mWorkspaceLoaded=" + mWorkspaceLoaded);
            }

            boolean isUpgradePath = false;
            if (!mWorkspaceLoaded) {
                isUpgradePath = loadWorkspace();
                synchronized (LoaderTask.this) {
                    if (mStopped) {
                        return isUpgradePath;
                    }
                    mWorkspaceLoaded = true;
                }
            }

            // Bind the workspace
            bindWorkspace(-1, isUpgradePath);
            return isUpgradePath;
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

                while (!mStopped && !mLoadAndBindStepFinished && !mFlushingWorkerThread) {
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
            bindWorkspace(synchronousBindPage, false);
            // XXX: For now, continue posting the binding of AllApps as there are other issues that
            //      arise from that.
            onlyBindAllApps();
        }

        public void run() {
            boolean isUpgrade = false;

            synchronized (mLock) {
                mIsLoaderTaskRunning = true;
            }
            // Optimize for end-user experience: if the Launcher is up and // running with the
            // All Apps interface in the foreground, load All Apps first. Otherwise, load the
            // workspace first (default).
            keep_running: {
                // Elevate priority when Home launches for the first time to avoid
                // starving at boot time. Staring at a blank home is not cool.
                synchronized (mLock) {
                    if (DEBUG_LOADERS) Log.d(TAG, "Setting thread priority to " +
                            (mIsLaunching ? "DEFAULT" : "BACKGROUND"));
                    android.os.Process.setThreadPriority(mIsLaunching
                            ? Process.THREAD_PRIORITY_DEFAULT : Process.THREAD_PRIORITY_BACKGROUND);
                }
                if (DEBUG_LOADERS) Log.d(TAG, "step 1: loading workspace");
                isUpgrade = loadAndBindWorkspace();

                if (mStopped) {
                    break keep_running;
                }

                // Whew! Hard work done.  Slow us down, and wait until the UI thread has
                // settled down.
                synchronized (mLock) {
                    if (mIsLaunching) {
                        if (DEBUG_LOADERS) Log.d(TAG, "Setting thread priority to BACKGROUND");
                        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    }
                }
                waitForIdle();

                // second step
                if (DEBUG_LOADERS) Log.d(TAG, "step 2: loading all apps");
                loadAndBindAllApps();

                // Restore the default thread priority after we are done loading items
                synchronized (mLock) {
                    android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
                }
            }

            // Update the saved icons if necessary
            if (DEBUG_LOADERS) Log.d(TAG, "Comparing loaded icons to database icons");
            synchronized (sBgLock) {
                for (Object key : sBgDbIconCache.keySet()) {
                    updateSavedIcon(mContext, (ShortcutInfo) key, sBgDbIconCache.get(key));
                }
                sBgDbIconCache.clear();
            }

            if (LauncherAppState.isDisableAllApps()) {
                // Ensure that all the applications that are in the system are
                // represented on the home screen.
                if (!UPGRADE_USE_MORE_APPS_FOLDER || !isUpgrade) {
                    verifyApplications();
                }
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

        private void verifyApplications() {
            final Context context = mApp.getContext();

            // Cross reference all the applications in our apps list with items in the workspace
            ArrayList<ItemInfo> tmpInfos;
            ArrayList<ItemInfo> added = new ArrayList<ItemInfo>();
            synchronized (sBgLock) {
                for (AppInfo app : mBgAllAppsList.data) {
                    tmpInfos = getItemInfoForComponentName(app.componentName, app.user);
                    if (tmpInfos.isEmpty()) {
                        // We are missing an application icon, so add this to the workspace
                        added.add(app);
                        // This is a rare event, so lets log it
                        Log.e(TAG, "Missing Application on load: " + app);
                    }
                }
            }
            if (!added.isEmpty()) {
                addAndBindAddedWorkspaceApps(context, added);
            }
        }

        // check & update map of what's occupied; used to discard overlapping/invalid items
        private boolean checkItemPlacement(HashMap<Long, ItemInfo[][]> occupied, ItemInfo item) {
            LauncherAppState app = LauncherAppState.getInstance();
            DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
            final int countX = (int) grid.numColumns;
            final int countY = (int) grid.numRows;

            long containerIndex = item.screenId;
            if (item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                // Return early if we detect that an item is under the hotseat button
                if (mCallbacks == null ||
                        mCallbacks.get().isAllAppsButtonRank((int) item.screenId)) {
                    Log.e(TAG, "Error loading shortcut into hotseat " + item
                            + " into position (" + item.screenId + ":" + item.cellX + ","
                            + item.cellY + ") occupied by all apps");
                    return false;
                }

                final ItemInfo[][] hotseatItems =
                        occupied.get((long) LauncherSettings.Favorites.CONTAINER_HOTSEAT);

                if (item.screenId >= grid.numHotseatIcons) {
                    Log.e(TAG, "Error loading shortcut " + item
                            + " into hotseat position " + item.screenId
                            + ", position out of bounds: (0 to " + (grid.numHotseatIcons - 1)
                            + ")");
                    return false;
                }

                if (hotseatItems != null) {
                    if (hotseatItems[(int) item.screenId][0] != null) {
                        Log.e(TAG, "Error loading shortcut into hotseat " + item
                                + " into position (" + item.screenId + ":" + item.cellX + ","
                                + item.cellY + ") occupied by "
                                + occupied.get(LauncherSettings.Favorites.CONTAINER_HOTSEAT)
                                [(int) item.screenId][0]);
                            return false;
                    } else {
                        hotseatItems[(int) item.screenId][0] = item;
                        return true;
                    }
                } else {
                    final ItemInfo[][] items = new ItemInfo[(int) grid.numHotseatIcons][1];
                    items[(int) item.screenId][0] = item;
                    occupied.put((long) LauncherSettings.Favorites.CONTAINER_HOTSEAT, items);
                    return true;
                }
            } else if (item.container != LauncherSettings.Favorites.CONTAINER_DESKTOP) {
                // Skip further checking if it is not the hotseat or workspace container
                return true;
            }

            if (!occupied.containsKey(item.screenId)) {
                ItemInfo[][] items = new ItemInfo[countX + 1][countY + 1];
                occupied.put(item.screenId, items);
            }

            final ItemInfo[][] screens = occupied.get(item.screenId);
            if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                    item.cellX < 0 || item.cellY < 0 ||
                    item.cellX + item.spanX > countX || item.cellY + item.spanY > countY) {
                Log.e(TAG, "Error loading shortcut " + item
                        + " into cell (" + containerIndex + "-" + item.screenId + ":"
                        + item.cellX + "," + item.cellY
                        + ") out of screen bounds ( " + countX + "x" + countY + ")");
                return false;
            }

            // Check if any workspace icons overlap with each other
            for (int x = item.cellX; x < (item.cellX+item.spanX); x++) {
                for (int y = item.cellY; y < (item.cellY+item.spanY); y++) {
                    if (screens[x][y] != null) {
                        Log.e(TAG, "Error loading shortcut " + item
                            + " into cell (" + containerIndex + "-" + item.screenId + ":"
                            + x + "," + y
                            + ") occupied by "
                            + screens[x][y]);
                        return false;
                    }
                }
            }
            for (int x = item.cellX; x < (item.cellX+item.spanX); x++) {
                for (int y = item.cellY; y < (item.cellY+item.spanY); y++) {
                    screens[x][y] = item;
                }
            }

            return true;
        }

        /** Clears all the sBg data structures */
        private void clearSBgDataStructures() {
            synchronized (sBgLock) {
                sBgWorkspaceItems.clear();
                sBgAppWidgets.clear();
                sBgFolders.clear();
                sBgItemsIdMap.clear();
                sBgDbIconCache.clear();
                sBgWorkspaceScreens.clear();
            }
        }

        /** Returns whether this is an upgrade path */
        private boolean loadWorkspace() {
            // Log to disk
            Launcher.addDumpLog(TAG, "11683562 - loadWorkspace()", true);

            final long t = DEBUG_LOADERS ? SystemClock.uptimeMillis() : 0;

            final Context context = mContext;
            final ContentResolver contentResolver = context.getContentResolver();
            final PackageManager manager = context.getPackageManager();
            final AppWidgetManager widgets = AppWidgetManager.getInstance(context);
            final boolean isSafeMode = manager.isSafeMode();
            final LauncherAppsCompat launcherApps = LauncherAppsCompat.getInstance(context);
            final boolean isSdCardReady = context.registerReceiver(null,
                    new IntentFilter(StartupReceiver.SYSTEM_READY)) != null;

            LauncherAppState app = LauncherAppState.getInstance();
            DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
            int countX = (int) grid.numColumns;
            int countY = (int) grid.numRows;

            if ((mFlags & LOADER_FLAG_CLEAR_WORKSPACE) != 0) {
                Launcher.addDumpLog(TAG, "loadWorkspace: resetting launcher database", true);
                LauncherAppState.getLauncherProvider().deleteDatabase();
            }

            if ((mFlags & LOADER_FLAG_MIGRATE_SHORTCUTS) != 0) {
                // append the user's Launcher2 shortcuts
                Launcher.addDumpLog(TAG, "loadWorkspace: migrating from launcher2", true);
                LauncherAppState.getLauncherProvider().migrateLauncher2Shortcuts();
            } else {
                // Make sure the default workspace is loaded
                Launcher.addDumpLog(TAG, "loadWorkspace: loading default favorites", false);
                LauncherAppState.getLauncherProvider().loadDefaultFavoritesIfNecessary();
            }

            // This code path is for our old migration code and should no longer be exercised
            boolean loadedOldDb = false;

            // Log to disk
            Launcher.addDumpLog(TAG, "11683562 -   loadedOldDb: " + loadedOldDb, true);

            synchronized (sBgLock) {
                clearSBgDataStructures();
                final HashSet<String> installingPkgs = PackageInstallerCompat
                        .getInstance(mContext).updateAndGetActiveSessionCache();

                final ArrayList<Long> itemsToRemove = new ArrayList<Long>();
                final ArrayList<Long> restoredRows = new ArrayList<Long>();
                final Uri contentUri = LauncherSettings.Favorites.CONTENT_URI_NO_NOTIFICATION;
                if (DEBUG_LOADERS) Log.d(TAG, "loading model from " + contentUri);
                final Cursor c = contentResolver.query(contentUri, null, null, null, null);

                // +1 for the hotseat (it can be larger than the workspace)
                // Load workspace in reverse order to ensure that latest items are loaded first (and
                // before any earlier duplicates)
                final HashMap<Long, ItemInfo[][]> occupied = new HashMap<Long, ItemInfo[][]>();

                try {
                    final int idIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites._ID);
                    final int intentIndex = c.getColumnIndexOrThrow
                            (LauncherSettings.Favorites.INTENT);
                    final int titleIndex = c.getColumnIndexOrThrow
                            (LauncherSettings.Favorites.TITLE);
                    final int iconTypeIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.ICON_TYPE);
                    final int iconIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.ICON);
                    final int iconPackageIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.ICON_PACKAGE);
                    final int iconResourceIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.ICON_RESOURCE);
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
                    final int restoredIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.RESTORED);
                    final int profileIdIndex = c.getColumnIndexOrThrow(
                            LauncherSettings.Favorites.PROFILE_ID);
                    //final int uriIndex = c.getColumnIndexOrThrow(LauncherSettings.Favorites.URI);
                    //final int displayModeIndex = c.getColumnIndexOrThrow(
                    //        LauncherSettings.Favorites.DISPLAY_MODE);

                    ShortcutInfo info;
                    String intentDescription;
                    LauncherAppWidgetInfo appWidgetInfo;
                    int container;
                    long id;
                    Intent intent;
                    UserHandleCompat user;

                    while (!mStopped && c.moveToNext()) {
                        try {
                            int itemType = c.getInt(itemTypeIndex);
                            boolean restored = 0 != c.getInt(restoredIndex);
                            boolean allowMissingTarget = false;

                            switch (itemType) {
                            case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                            case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                                id = c.getLong(idIndex);
                                intentDescription = c.getString(intentIndex);
                                long serialNumber = c.getInt(profileIdIndex);
                                user = mUserManager.getUserForSerialNumber(serialNumber);
                                int promiseType = c.getInt(restoredIndex);
                                int disabledState = 0;
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

                                        if (validComponent) {
                                            if (restored) {
                                                // no special handling necessary for this item
                                                restoredRows.add(id);
                                                restored = false;
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
                                                    String where = BaseColumns._ID + "= ?";
                                                    String[] args = {Long.toString(id)};
                                                    contentResolver.update(contentUri, values, where, args);
                                                }
                                            }

                                            if (intent == null) {
                                                // The app is installed but the component is no
                                                // longer available.
                                                Launcher.addDumpLog(TAG,
                                                        "Invalid component removed: " + cn, true);
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
                                            Launcher.addDumpLog(TAG,
                                                    "package not yet restored: " + cn, true);

                                            if ((promiseType & ShortcutInfo.FLAG_RESTORE_STARTED) != 0) {
                                                // Restore has started once.
                                            } else if (installingPkgs.contains(cn.getPackageName())) {
                                                // App restore has started. Update the flag
                                                promiseType |= ShortcutInfo.FLAG_RESTORE_STARTED;
                                                ContentValues values = new ContentValues();
                                                values.put(LauncherSettings.Favorites.RESTORED,
                                                        promiseType);
                                                String where = BaseColumns._ID + "= ?";
                                                String[] args = {Long.toString(id)};
                                                contentResolver.update(contentUri, values, where, args);

                                            } else if (REMOVE_UNRESTORED_ICONS) {
                                                Launcher.addDumpLog(TAG,
                                                        "Unrestored package removed: " + cn, true);
                                                itemsToRemove.add(id);
                                                continue;
                                            }
                                        } else if (launcherApps.isAppEnabled(
                                                manager, cn.getPackageName(),
                                                PackageManager.GET_UNINSTALLED_PACKAGES)) {
                                            // Package is present but not available.
                                            allowMissingTarget = true;
                                            disabledState = ShortcutInfo.FLAG_DISABLED_NOT_AVAILABLE;
                                        } else if (!isSdCardReady) {
                                            // SdCard is not ready yet. Package might get available,
                                            // once it is ready.
                                            Launcher.addDumpLog(TAG, "Invalid package: " + cn
                                                    + " (check again later)", true);
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
                                            Launcher.addDumpLog(TAG,
                                                    "Invalid package removed: " + cn, true);
                                            itemsToRemove.add(id);
                                            continue;
                                        }
                                    } else if (cn == null) {
                                        // For shortcuts with no component, keep them as they are
                                        restoredRows.add(id);
                                        restored = false;
                                    }
                                } catch (URISyntaxException e) {
                                    Launcher.addDumpLog(TAG,
                                            "Invalid uri: " + intentDescription, true);
                                    continue;
                                }

                                if (restored) {
                                    if (user.equals(UserHandleCompat.myUserHandle())) {
                                        Launcher.addDumpLog(TAG,
                                                "constructing info for partially restored package",
                                                true);
                                        info = getRestoredItemInfo(c, titleIndex, intent, promiseType);
                                        intent = getRestoredItemIntent(c, context, intent);
                                    } else {
                                        // Don't restore items for other profiles.
                                        itemsToRemove.add(id);
                                        continue;
                                    }
                                } else if (itemType ==
                                        LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                                    info = getShortcutInfo(manager, intent, user, context, c,
                                            iconIndex, titleIndex, mLabelCache, allowMissingTarget);
                                } else {
                                    info = getShortcutInfo(c, context, iconTypeIndex,
                                            iconPackageIndex, iconResourceIndex, iconIndex,
                                            titleIndex);

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
                                    container = c.getInt(containerIndex);
                                    info.container = container;
                                    info.screenId = c.getInt(screenIndex);
                                    info.cellX = c.getInt(cellXIndex);
                                    info.cellY = c.getInt(cellYIndex);
                                    info.spanX = 1;
                                    info.spanY = 1;
                                    info.intent.putExtra(ItemInfo.EXTRA_PROFILE, serialNumber);
                                    info.isDisabled = disabledState;
                                    if (isSafeMode && !Utilities.isSystemApp(context, intent)) {
                                        info.isDisabled |= ShortcutInfo.FLAG_DISABLED_SAFEMODE;
                                    }

                                    // check & update map of what's occupied
                                    if (!checkItemPlacement(occupied, info)) {
                                        itemsToRemove.add(id);
                                        break;
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
                                        folderInfo.add(info);
                                        break;
                                    }
                                    sBgItemsIdMap.put(info.id, info);

                                    // now that we've loaded everthing re-save it with the
                                    // icon in case it disappears somehow.
                                    queueIconToBeChecked(sBgDbIconCache, info, c, iconIndex);
                                } else {
                                    throw new RuntimeException("Unexpected null ShortcutInfo");
                                }
                                break;

                            case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                                id = c.getLong(idIndex);
                                FolderInfo folderInfo = findOrMakeFolder(sBgFolders, id);

                                folderInfo.title = c.getString(titleIndex);
                                folderInfo.id = id;
                                container = c.getInt(containerIndex);
                                folderInfo.container = container;
                                folderInfo.screenId = c.getInt(screenIndex);
                                folderInfo.cellX = c.getInt(cellXIndex);
                                folderInfo.cellY = c.getInt(cellYIndex);
                                folderInfo.spanX = 1;
                                folderInfo.spanY = 1;

                                // check & update map of what's occupied
                                if (!checkItemPlacement(occupied, folderInfo)) {
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
                                // Read all Launcher-specific widget details
                                int appWidgetId = c.getInt(appWidgetIdIndex);
                                String savedProvider = c.getString(appWidgetProviderIndex);
                                id = c.getLong(idIndex);
                                final ComponentName component =
                                        ComponentName.unflattenFromString(savedProvider);

                                final int restoreStatus = c.getInt(restoredIndex);
                                final boolean isIdValid = (restoreStatus &
                                        LauncherAppWidgetInfo.FLAG_ID_NOT_VALID) == 0;

                                final boolean wasProviderReady = (restoreStatus &
                                        LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY) == 0;

                                final AppWidgetProviderInfo provider = isIdValid
                                        ? widgets.getAppWidgetInfo(appWidgetId)
                                        : findAppWidgetProviderInfoWithComponent(context, component);

                                final boolean isProviderReady = isValidProvider(provider);
                                if (!isSafeMode && wasProviderReady && !isProviderReady) {
                                    String log = "Deleting widget that isn't installed anymore: "
                                            + "id=" + id + " appWidgetId=" + appWidgetId;
                                    Log.e(TAG, log);
                                    Launcher.addDumpLog(TAG, log, false);
                                    itemsToRemove.add(id);
                                } else {
                                    if (isProviderReady) {
                                        appWidgetInfo = new LauncherAppWidgetInfo(appWidgetId,
                                                provider.provider);
                                        int[] minSpan =
                                                Launcher.getMinSpanForWidget(context, provider);
                                        appWidgetInfo.minSpanX = minSpan[0];
                                        appWidgetInfo.minSpanY = minSpan[1];

                                        int status = restoreStatus;
                                        if (!wasProviderReady) {
                                            // If provider was not previously ready, update the
                                            // status and UI flag.

                                            // Id would be valid only if the widget restore broadcast was received.
                                            if (isIdValid) {
                                                status = LauncherAppWidgetInfo.RESTORE_COMPLETED;
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

                                        if ((restoreStatus & LauncherAppWidgetInfo.FLAG_RESTORE_STARTED) != 0) {
                                            // Restore has started once.
                                        } else if (installingPkgs.contains(component.getPackageName())) {
                                            // App restore has started. Update the flag
                                            appWidgetInfo.restoreStatus |=
                                                    LauncherAppWidgetInfo.FLAG_RESTORE_STARTED;
                                        } else if (REMOVE_UNRESTORED_ICONS && !isSafeMode) {
                                            Launcher.addDumpLog(TAG,
                                                    "Unrestored widget removed: " + component, true);
                                            itemsToRemove.add(id);
                                            continue;
                                        }
                                    }

                                    appWidgetInfo.id = id;
                                    appWidgetInfo.screenId = c.getInt(screenIndex);
                                    appWidgetInfo.cellX = c.getInt(cellXIndex);
                                    appWidgetInfo.cellY = c.getInt(cellYIndex);
                                    appWidgetInfo.spanX = c.getInt(spanXIndex);
                                    appWidgetInfo.spanY = c.getInt(spanYIndex);

                                    container = c.getInt(containerIndex);
                                    if (container != LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                                        container != LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                                        Log.e(TAG, "Widget found where container != " +
                                            "CONTAINER_DESKTOP nor CONTAINER_HOTSEAT - ignoring!");
                                        continue;
                                    }

                                    appWidgetInfo.container = c.getInt(containerIndex);
                                    // check & update map of what's occupied
                                    if (!checkItemPlacement(occupied, appWidgetInfo)) {
                                        itemsToRemove.add(id);
                                        break;
                                    }

                                    String providerName = appWidgetInfo.providerName.flattenToString();
                                    if (!providerName.equals(savedProvider) ||
                                            (appWidgetInfo.restoreStatus != restoreStatus)) {
                                        ContentValues values = new ContentValues();
                                        values.put(LauncherSettings.Favorites.APPWIDGET_PROVIDER,
                                                providerName);
                                        values.put(LauncherSettings.Favorites.RESTORED,
                                                appWidgetInfo.restoreStatus);
                                        String where = BaseColumns._ID + "= ?";
                                        String[] args = {Long.toString(id)};
                                        contentResolver.update(contentUri, values, where, args);
                                    }
                                    sBgItemsIdMap.put(appWidgetInfo.id, appWidgetInfo);
                                    sBgAppWidgets.add(appWidgetInfo);
                                }
                                break;
                            }
                        } catch (Exception e) {
                            Launcher.addDumpLog(TAG, "Desktop items loading interrupted", e, true);
                        }
                    }
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }

                // Break early if we've stopped loading
                if (mStopped) {
                    clearSBgDataStructures();
                    return false;
                }

                if (itemsToRemove.size() > 0) {
                    ContentProviderClient client = contentResolver.acquireContentProviderClient(
                            contentUri);
                    // Remove dead items
                    for (long id : itemsToRemove) {
                        if (DEBUG_LOADERS) {
                            Log.d(TAG, "Removed id = " + id);
                        }
                        // Don't notify content observers
                        try {
                            client.delete(LauncherSettings.Favorites.getContentUri(id, false),
                                    null, null);
                        } catch (RemoteException e) {
                            Log.w(TAG, "Could not remove id = " + id);
                        }
                    }
                }

                if (restoredRows.size() > 0) {
                    ContentProviderClient updater = contentResolver.acquireContentProviderClient(
                            contentUri);
                    // Update restored items that no longer require special handling
                    try {
                        StringBuilder selectionBuilder = new StringBuilder();
                        selectionBuilder.append(LauncherSettings.Favorites._ID);
                        selectionBuilder.append(" IN (");
                        selectionBuilder.append(TextUtils.join(", ", restoredRows));
                        selectionBuilder.append(")");
                        ContentValues values = new ContentValues();
                        values.put(LauncherSettings.Favorites.RESTORED, 0);
                        updater.update(LauncherSettings.Favorites.CONTENT_URI_NO_NOTIFICATION,
                                values, selectionBuilder.toString(), null);
                    } catch (RemoteException e) {
                        Log.w(TAG, "Could not update restored rows");
                    }
                }

                if (!isSdCardReady && !sPendingPackages.isEmpty()) {
                    context.registerReceiver(new AppsAvailabilityCheck(),
                            new IntentFilter(StartupReceiver.SYSTEM_READY),
                            null, sWorker);
                }

                if (loadedOldDb) {
                    long maxScreenId = 0;
                    // If we're importing we use the old screen order.
                    for (ItemInfo item: sBgItemsIdMap.values()) {
                        long screenId = item.screenId;
                        if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                                !sBgWorkspaceScreens.contains(screenId)) {
                            sBgWorkspaceScreens.add(screenId);
                            if (screenId > maxScreenId) {
                                maxScreenId = screenId;
                            }
                        }
                    }
                    Collections.sort(sBgWorkspaceScreens);
                    // Log to disk
                    Launcher.addDumpLog(TAG, "11683562 -   maxScreenId: " + maxScreenId, true);
                    Launcher.addDumpLog(TAG, "11683562 -   sBgWorkspaceScreens: " +
                            TextUtils.join(", ", sBgWorkspaceScreens), true);

                    LauncherAppState.getLauncherProvider().updateMaxScreenId(maxScreenId);
                    updateWorkspaceScreenOrder(context, sBgWorkspaceScreens);

                    // Update the max item id after we load an old db
                    long maxItemId = 0;
                    // If we're importing we use the old screen order.
                    for (ItemInfo item: sBgItemsIdMap.values()) {
                        maxItemId = Math.max(maxItemId, item.id);
                    }
                    LauncherAppState.getLauncherProvider().updateMaxItemId(maxItemId);
                } else {
                    TreeMap<Integer, Long> orderedScreens = loadWorkspaceScreensDb(mContext);
                    for (Integer i : orderedScreens.keySet()) {
                        sBgWorkspaceScreens.add(orderedScreens.get(i));
                    }
                    // Log to disk
                    Launcher.addDumpLog(TAG, "11683562 -   sBgWorkspaceScreens: " +
                            TextUtils.join(", ", sBgWorkspaceScreens), true);

                    // Remove any empty screens
                    ArrayList<Long> unusedScreens = new ArrayList<Long>(sBgWorkspaceScreens);
                    for (ItemInfo item: sBgItemsIdMap.values()) {
                        long screenId = item.screenId;
                        if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                                unusedScreens.contains(screenId)) {
                            unusedScreens.remove(screenId);
                        }
                    }

                    // If there are any empty screens remove them, and update.
                    if (unusedScreens.size() != 0) {
                        // Log to disk
                        Launcher.addDumpLog(TAG, "11683562 -   unusedScreens (to be removed): " +
                                TextUtils.join(", ", unusedScreens), true);

                        sBgWorkspaceScreens.removeAll(unusedScreens);
                        updateWorkspaceScreenOrder(context, sBgWorkspaceScreens);
                    }
                }

                if (DEBUG_LOADERS) {
                    Log.d(TAG, "loaded workspace in " + (SystemClock.uptimeMillis()-t) + "ms");
                    Log.d(TAG, "workspace layout: ");
                    int nScreens = occupied.size();
                    for (int y = 0; y < countY; y++) {
                        String line = "";

                        Iterator<Long> iter = occupied.keySet().iterator();
                        while (iter.hasNext()) {
                            long screenId = iter.next();
                            if (screenId > 0) {
                                line += " | ";
                            }
                            for (int x = 0; x < countX; x++) {
                                ItemInfo[][] screen = occupied.get(screenId);
                                if (x < screen.length && y < screen[x].length) {
                                    line += (screen[x][y] != null) ? "#" : ".";
                                } else {
                                    line += "!";
                                }
                            }
                        }
                        Log.d(TAG, "[ " + line + " ]");
                    }
                }
            }
            return loadedOldDb;
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
                    return (int) (lhs.container - rhs.container);
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

        /** Filters the set of folders which are on the specified screen. */
        private void filterCurrentFolders(long currentScreenId,
                HashMap<Long, ItemInfo> itemsIdMap,
                HashMap<Long, FolderInfo> folders,
                HashMap<Long, FolderInfo> currentScreenFolders,
                HashMap<Long, FolderInfo> otherScreenFolders) {

            for (long id : folders.keySet()) {
                ItemInfo info = itemsIdMap.get(id);
                FolderInfo folder = folders.get(id);
                if (info == null || folder == null) continue;
                if (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                        info.screenId == currentScreenId) {
                    currentScreenFolders.put(id, folder);
                } else {
                    otherScreenFolders.put(id, folder);
                }
            }
        }

        /** Sorts the set of items by hotseat, workspace (spatially from top to bottom, left to
         * right) */
        private void sortWorkspaceItemsSpatially(ArrayList<ItemInfo> workspaceItems) {
            final LauncherAppState app = LauncherAppState.getInstance();
            final DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
            // XXX: review this
            Collections.sort(workspaceItems, new Comparator<ItemInfo>() {
                @Override
                public int compare(ItemInfo lhs, ItemInfo rhs) {
                    int cellCountX = (int) grid.numColumns;
                    int cellCountY = (int) grid.numRows;
                    int screenOffset = cellCountX * cellCountY;
                    int containerOffset = screenOffset * (Launcher.SCREEN_COUNT + 1); // +1 hotseat
                    long lr = (lhs.container * containerOffset + lhs.screenId * screenOffset +
                            lhs.cellY * cellCountX + lhs.cellX);
                    long rr = (rhs.container * containerOffset + rhs.screenId * screenOffset +
                            rhs.cellY * cellCountX + rhs.cellX);
                    return (int) (lr - rr);
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
            runOnMainThread(r, MAIN_THREAD_BINDING_RUNNABLE);
        }

        private void bindWorkspaceItems(final Callbacks oldCallbacks,
                final ArrayList<ItemInfo> workspaceItems,
                final ArrayList<LauncherAppWidgetInfo> appWidgets,
                final HashMap<Long, FolderInfo> folders,
                ArrayList<Runnable> deferredBindRunnables) {

            final boolean postOnMainThread = (deferredBindRunnables != null);

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
                if (postOnMainThread) {
                    synchronized (deferredBindRunnables) {
                        deferredBindRunnables.add(r);
                    }
                } else {
                    runOnMainThread(r, MAIN_THREAD_BINDING_RUNNABLE);
                }
            }

            // Bind the folders
            if (!folders.isEmpty()) {
                final Runnable r = new Runnable() {
                    public void run() {
                        Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                        if (callbacks != null) {
                            callbacks.bindFolders(folders);
                        }
                    }
                };
                if (postOnMainThread) {
                    synchronized (deferredBindRunnables) {
                        deferredBindRunnables.add(r);
                    }
                } else {
                    runOnMainThread(r, MAIN_THREAD_BINDING_RUNNABLE);
                }
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
                if (postOnMainThread) {
                    deferredBindRunnables.add(r);
                } else {
                    runOnMainThread(r, MAIN_THREAD_BINDING_RUNNABLE);
                }
            }
        }

        /**
         * Binds all loaded data to actual views on the main thread.
         */
        private void bindWorkspace(int synchronizeBindPage, final boolean isUpgradePath) {
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
            ArrayList<ItemInfo> workspaceItems = new ArrayList<ItemInfo>();
            ArrayList<LauncherAppWidgetInfo> appWidgets =
                    new ArrayList<LauncherAppWidgetInfo>();
            HashMap<Long, FolderInfo> folders = new HashMap<Long, FolderInfo>();
            HashMap<Long, ItemInfo> itemsIdMap = new HashMap<Long, ItemInfo>();
            ArrayList<Long> orderedScreenIds = new ArrayList<Long>();
            synchronized (sBgLock) {
                workspaceItems.addAll(sBgWorkspaceItems);
                appWidgets.addAll(sBgAppWidgets);
                folders.putAll(sBgFolders);
                itemsIdMap.putAll(sBgItemsIdMap);
                orderedScreenIds.addAll(sBgWorkspaceScreens);
            }

            final boolean isLoadingSynchronously =
                    synchronizeBindPage != PagedView.INVALID_RESTORE_PAGE;
            int currScreen = isLoadingSynchronously ? synchronizeBindPage :
                oldCallbacks.getCurrentWorkspaceScreen();
            if (currScreen >= orderedScreenIds.size()) {
                // There may be no workspace screens (just hotseat items and an empty page).
                currScreen = PagedView.INVALID_RESTORE_PAGE;
            }
            final int currentScreen = currScreen;
            final long currentScreenId = currentScreen < 0
                    ? INVALID_SCREEN_ID : orderedScreenIds.get(currentScreen);

            // Load all the items that are on the current page first (and in the process, unbind
            // all the existing workspace items before we call startBinding() below.
            unbindWorkspaceItemsOnMainThread();

            // Separate the items that are on the current screen, and all the other remaining items
            ArrayList<ItemInfo> currentWorkspaceItems = new ArrayList<ItemInfo>();
            ArrayList<ItemInfo> otherWorkspaceItems = new ArrayList<ItemInfo>();
            ArrayList<LauncherAppWidgetInfo> currentAppWidgets =
                    new ArrayList<LauncherAppWidgetInfo>();
            ArrayList<LauncherAppWidgetInfo> otherAppWidgets =
                    new ArrayList<LauncherAppWidgetInfo>();
            HashMap<Long, FolderInfo> currentFolders = new HashMap<Long, FolderInfo>();
            HashMap<Long, FolderInfo> otherFolders = new HashMap<Long, FolderInfo>();

            filterCurrentWorkspaceItems(currentScreenId, workspaceItems, currentWorkspaceItems,
                    otherWorkspaceItems);
            filterCurrentAppWidgets(currentScreenId, appWidgets, currentAppWidgets,
                    otherAppWidgets);
            filterCurrentFolders(currentScreenId, itemsIdMap, folders, currentFolders,
                    otherFolders);
            sortWorkspaceItemsSpatially(currentWorkspaceItems);
            sortWorkspaceItemsSpatially(otherWorkspaceItems);

            // Tell the workspace that we're about to start binding items
            r = new Runnable() {
                public void run() {
                    Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                    if (callbacks != null) {
                        callbacks.startBinding();
                    }
                }
            };
            runOnMainThread(r, MAIN_THREAD_BINDING_RUNNABLE);

            bindWorkspaceScreens(oldCallbacks, orderedScreenIds);

            // Load items on the current page
            bindWorkspaceItems(oldCallbacks, currentWorkspaceItems, currentAppWidgets,
                    currentFolders, null);
            if (isLoadingSynchronously) {
                r = new Runnable() {
                    public void run() {
                        Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                        if (callbacks != null && currentScreen != PagedView.INVALID_RESTORE_PAGE) {
                            callbacks.onPageBoundSynchronously(currentScreen);
                        }
                    }
                };
                runOnMainThread(r, MAIN_THREAD_BINDING_RUNNABLE);
            }

            // Load all the remaining pages (if we are loading synchronously, we want to defer this
            // work until after the first render)
            synchronized (mDeferredBindRunnables) {
                mDeferredBindRunnables.clear();
            }
            bindWorkspaceItems(oldCallbacks, otherWorkspaceItems, otherAppWidgets, otherFolders,
                    (isLoadingSynchronously ? mDeferredBindRunnables : null));

            // Tell the workspace that we're done binding items
            r = new Runnable() {
                public void run() {
                    Callbacks callbacks = tryGetCallbacks(oldCallbacks);
                    if (callbacks != null) {
                        callbacks.finishBindingItems(isUpgradePath);
                    }

                    // If we're profiling, ensure this is the last thing in the queue.
                    if (DEBUG_LOADERS) {
                        Log.d(TAG, "bound workspace in "
                            + (SystemClock.uptimeMillis()-t) + "ms");
                    }

                    mIsLoadingAndBindingWorkspace = false;
                }
            };
            if (isLoadingSynchronously) {
                synchronized (mDeferredBindRunnables) {
                    mDeferredBindRunnables.add(r);
                }
            } else {
                runOnMainThread(r, MAIN_THREAD_BINDING_RUNNABLE);
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
                    mAllAppsLoaded = true;
                }
            } else {
                onlyBindAllApps();
            }
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
                                + (SystemClock.uptimeMillis()-t) + "ms");
                    }
                }
            };
            boolean isRunningOnMainThread = !(sWorkerThread.getThreadId() == Process.myTid());
            if (isRunningOnMainThread) {
                r.run();
            } else {
                mHandler.post(r);
            }
        }

        private void loadAllApps() {
            final long loadTime = DEBUG_LOADERS ? SystemClock.uptimeMillis() : 0;

            final Callbacks oldCallbacks = mCallbacks.get();
            if (oldCallbacks == null) {
                // This launcher has exited and nobody bothered to tell us.  Just bail.
                Log.w(TAG, "LoaderTask running with no launcher (loadAllApps)");
                return;
            }

            final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

            final List<UserHandleCompat> profiles = mUserManager.getUserProfiles();

            // Clear the list of apps
            mBgAllAppsList.clear();
            SharedPreferences prefs = mContext.getSharedPreferences(
                    LauncherAppState.getSharedPreferencesKey(), Context.MODE_PRIVATE);
            for (UserHandleCompat user : profiles) {
                // Query for the set of apps
                final long qiaTime = DEBUG_LOADERS ? SystemClock.uptimeMillis() : 0;
                List<LauncherActivityInfoCompat> apps = mLauncherApps.getActivityList(null, user);
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
                // Sort the applications by name
                final long sortTime = DEBUG_LOADERS ? SystemClock.uptimeMillis() : 0;
                Collections.sort(apps,
                        new LauncherModel.ShortcutNameComparator(mLabelCache));
                if (DEBUG_LOADERS) {
                    Log.d(TAG, "sort took "
                            + (SystemClock.uptimeMillis()-sortTime) + "ms");
                }

                // Create the ApplicationInfos
                for (int i = 0; i < apps.size(); i++) {
                    LauncherActivityInfoCompat app = apps.get(i);
                    // This builds the icon bitmaps.
                    mBgAllAppsList.add(new AppInfo(mContext, app, user, mIconCache, mLabelCache));
                }

                if (ADD_MANAGED_PROFILE_SHORTCUTS && !user.equals(UserHandleCompat.myUserHandle())) {
                    // Add shortcuts for packages which were installed while launcher was dead.
                    String shortcutsSetKey = INSTALLED_SHORTCUTS_SET_PREFIX
                            + mUserManager.getSerialNumberForUser(user);
                    Set<String> packagesAdded = prefs.getStringSet(shortcutsSetKey, Collections.EMPTY_SET);
                    HashSet<String> newPackageSet = new HashSet<String>();

                    for (LauncherActivityInfoCompat info : apps) {
                        String packageName = info.getComponentName().getPackageName();
                        if (!packagesAdded.contains(packageName)
                                && !newPackageSet.contains(packageName)) {
                            InstallShortcutReceiver.queueInstallShortcut(info, mContext);
                        }
                        newPackageSet.add(packageName);
                    }

                    prefs.edit().putStringSet(shortcutsSetKey, newPackageSet).commit();
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

            if (DEBUG_LOADERS) {
                Log.d(TAG, "Icons processed in "
                        + (SystemClock.uptimeMillis() - loadTime) + "ms");
            }
        }

        public void dumpState() {
            synchronized (sBgLock) {
                Log.d(TAG, "mLoaderTask.mContext=" + mContext);
                Log.d(TAG, "mLoaderTask.mIsLaunching=" + mIsLaunching);
                Log.d(TAG, "mLoaderTask.mStopped=" + mStopped);
                Log.d(TAG, "mLoaderTask.mLoadAndBindStepFinished=" + mLoadAndBindStepFinished);
                Log.d(TAG, "mItems size=" + sBgWorkspaceItems.size());
            }
        }
    }

    void enqueuePackageUpdated(PackageUpdatedTask task) {
        sWorker.post(task);
    }

    private class AppsAvailabilityCheck extends BroadcastReceiver {

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
                            boolean packageOnSdcard = launcherApps.isAppEnabled(
                                    manager, pkg, PackageManager.GET_UNINSTALLED_PACKAGES);
                            if (packageOnSdcard) {
                                Launcher.addDumpLog(TAG, "Package found on sd-card: " + pkg, true);
                                packagesUnavailable.add(pkg);
                            } else {
                                Launcher.addDumpLog(TAG, "Package not found: " + pkg, true);
                                packagesRemoved.add(pkg);
                            }
                        }
                    }
                    if (!packagesRemoved.isEmpty()) {
                        enqueuePackageUpdated(new PackageUpdatedTask(PackageUpdatedTask.OP_REMOVE,
                                packagesRemoved.toArray(new String[packagesRemoved.size()]), user));
                    }
                    if (!packagesUnavailable.isEmpty()) {
                        enqueuePackageUpdated(new PackageUpdatedTask(PackageUpdatedTask.OP_UNAVAILABLE,
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


        public PackageUpdatedTask(int op, String[] packages, UserHandleCompat user) {
            mOp = op;
            mPackages = packages;
            mUser = user;
        }

        public void run() {
            final Context context = mApp.getContext();

            final String[] packages = mPackages;
            final int N = packages.length;
            switch (mOp) {
                case OP_ADD:
                    for (int i=0; i<N; i++) {
                        if (DEBUG_LOADERS) Log.d(TAG, "mAllAppsList.addPackage " + packages[i]);
                        mIconCache.remove(packages[i], mUser);
                        mBgAllAppsList.addPackage(context, packages[i], mUser);
                    }

                    // Auto add shortcuts for added packages.
                    if (ADD_MANAGED_PROFILE_SHORTCUTS
                            && !UserHandleCompat.myUserHandle().equals(mUser)) {
                        SharedPreferences prefs = context.getSharedPreferences(
                                LauncherAppState.getSharedPreferencesKey(), Context.MODE_PRIVATE);
                        String shortcutsSetKey = INSTALLED_SHORTCUTS_SET_PREFIX
                                + mUserManager.getSerialNumberForUser(mUser);
                        Set<String> shortcutSet = new HashSet<String>(
                                prefs.getStringSet(shortcutsSetKey,Collections.EMPTY_SET));

                        for (int i=0; i<N; i++) {
                            if (!shortcutSet.contains(packages[i])) {
                                shortcutSet.add(packages[i]);
                                List<LauncherActivityInfoCompat> activities =
                                        mLauncherApps.getActivityList(packages[i], mUser);
                                if (activities != null && !activities.isEmpty()) {
                                    InstallShortcutReceiver.queueInstallShortcut(
                                            activities.get(0), context);
                                }
                            }
                        }

                        prefs.edit().putStringSet(shortcutsSetKey, shortcutSet).commit();
                    }
                    break;
                case OP_UPDATE:
                    for (int i=0; i<N; i++) {
                        if (DEBUG_LOADERS) Log.d(TAG, "mAllAppsList.updatePackage " + packages[i]);
                        mBgAllAppsList.updatePackage(context, packages[i], mUser);
                        WidgetPreviewLoader.removePackageFromDb(
                                mApp.getWidgetPreviewCacheDb(), packages[i]);
                    }
                    break;
                case OP_REMOVE:
                    // Remove the packageName for the set of auto-installed shortcuts. This
                    // will ensure that the shortcut when the app is installed again.
                    if (ADD_MANAGED_PROFILE_SHORTCUTS
                            && !UserHandleCompat.myUserHandle().equals(mUser)) {
                        SharedPreferences prefs = context.getSharedPreferences(
                                LauncherAppState.getSharedPreferencesKey(), Context.MODE_PRIVATE);
                        String shortcutsSetKey = INSTALLED_SHORTCUTS_SET_PREFIX
                                + mUserManager.getSerialNumberForUser(mUser);
                        HashSet<String> shortcutSet = new HashSet<String>(
                                prefs.getStringSet(shortcutsSetKey, Collections.EMPTY_SET));
                        shortcutSet.removeAll(Arrays.asList(mPackages));
                        prefs.edit().putStringSet(shortcutsSetKey, shortcutSet).commit();
                    }
                    // Fall through
                case OP_UNAVAILABLE:
                    boolean clearCache = mOp == OP_REMOVE;
                    for (int i=0; i<N; i++) {
                        if (DEBUG_LOADERS) Log.d(TAG, "mAllAppsList.removePackage " + packages[i]);
                        mBgAllAppsList.removePackage(packages[i], mUser, clearCache);
                        WidgetPreviewLoader.removePackageFromDb(
                                mApp.getWidgetPreviewCacheDb(), packages[i]);
                    }
                    break;
            }

            ArrayList<AppInfo> added = null;
            ArrayList<AppInfo> modified = null;
            final ArrayList<AppInfo> removedApps = new ArrayList<AppInfo>();

            if (mBgAllAppsList.added.size() > 0) {
                added = new ArrayList<AppInfo>(mBgAllAppsList.added);
                mBgAllAppsList.added.clear();
            }
            if (mBgAllAppsList.modified.size() > 0) {
                modified = new ArrayList<AppInfo>(mBgAllAppsList.modified);
                mBgAllAppsList.modified.clear();
            }
            if (mBgAllAppsList.removed.size() > 0) {
                removedApps.addAll(mBgAllAppsList.removed);
                mBgAllAppsList.removed.clear();
            }

            final Callbacks callbacks = getCallback();
            if (callbacks == null) {
                Log.w(TAG, "Nobody to tell about the new app.  Launcher is probably loading.");
                return;
            }

            final HashMap<ComponentName, AppInfo> addedOrUpdatedApps =
                    new HashMap<ComponentName, AppInfo>();

            if (added != null) {
                // Ensure that we add all the workspace applications to the db
                if (LauncherAppState.isDisableAllApps()) {
                    final ArrayList<ItemInfo> addedInfos = new ArrayList<ItemInfo>(added);
                    addAndBindAddedWorkspaceApps(context, addedInfos);
                } else {
                    addAppsToAllApps(context, added);
                }
                for (AppInfo ai : added) {
                    addedOrUpdatedApps.put(ai.componentName, ai);
                }
            }

            if (modified != null) {
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
            if (mOp == OP_ADD || mOp == OP_UPDATE) {
                final ArrayList<ShortcutInfo> updatedShortcuts = new ArrayList<ShortcutInfo>();
                final ArrayList<ShortcutInfo> removedShortcuts = new ArrayList<ShortcutInfo>();
                final ArrayList<LauncherAppWidgetInfo> widgets = new ArrayList<LauncherAppWidgetInfo>();

                HashSet<String> packageSet = new HashSet<String>(Arrays.asList(packages));
                synchronized (sBgLock) {
                    for (ItemInfo info : sBgItemsIdMap.values()) {
                        if (info instanceof ShortcutInfo && mUser.equals(info.user)) {
                            ShortcutInfo si = (ShortcutInfo) info;
                            boolean infoUpdated = false;
                            boolean shortcutUpdated = false;

                            // Update shortcuts which use iconResource.
                            if ((si.iconResource != null)
                                    && packageSet.contains(si.iconResource.packageName)) {
                                Bitmap icon = Utilities.createIconBitmap(si.iconResource.packageName,
                                        si.iconResource.resourceName, mIconCache, context);
                                if (icon != null) {
                                    si.setIcon(icon);
                                    si.usingFallbackIcon = false;
                                    infoUpdated = true;
                                }
                            }

                            ComponentName cn = si.getTargetComponent();
                            if (cn != null && packageSet.contains(cn.getPackageName())) {
                                AppInfo appInfo = addedOrUpdatedApps.get(cn);

                                if (si.isPromise()) {
                                    mIconCache.deletePreloadedIcon(cn, mUser);
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
                                    si.intent = si.promisedIntent;
                                    si.promisedIntent = null;
                                    si.status &= ~ShortcutInfo.FLAG_RESTORED_ICON
                                            & ~ShortcutInfo.FLAG_AUTOINTALL_ICON
                                            & ~ShortcutInfo.FLAG_INSTALL_SESSION_ACTIVE;

                                    infoUpdated = true;
                                    si.updateIcon(mIconCache);
                                }

                                if (appInfo != null && Intent.ACTION_MAIN.equals(si.intent.getAction())
                                        && si.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
                                    si.updateIcon(mIconCache);
                                    si.title = appInfo.title.toString();
                                    si.contentDescription = appInfo.contentDescription;
                                    infoUpdated = true;
                                }

                                if ((si.isDisabled & ShortcutInfo.FLAG_DISABLED_NOT_AVAILABLE) != 0) {
                                    // Since package was just updated, the target must be available now.
                                    si.isDisabled &= ~ShortcutInfo.FLAG_DISABLED_NOT_AVAILABLE;
                                    shortcutUpdated = true;
                                }
                            }

                            if (infoUpdated || shortcutUpdated) {
                                updatedShortcuts.add(si);
                            }
                            if (infoUpdated) {
                                updateItemInDatabase(context, si);
                            }
                        } else if (info instanceof LauncherAppWidgetInfo) {
                            LauncherAppWidgetInfo widgetInfo = (LauncherAppWidgetInfo) info;
                            if (mUser.equals(widgetInfo.user)
                                    && widgetInfo.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY)
                                    && packageSet.contains(widgetInfo.providerName.getPackageName())) {
                                widgetInfo.restoreStatus &= ~LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY;
                                widgets.add(widgetInfo);
                                updateItemInDatabase(context, widgetInfo);
                            }
                        }
                    }
                }

                if (!updatedShortcuts.isEmpty() || !removedShortcuts.isEmpty()) {
                    mHandler.post(new Runnable() {

                        public void run() {
                            Callbacks cb = getCallback();
                            if (callbacks == cb && cb != null) {
                                callbacks.bindShortcutsChanged(
                                        updatedShortcuts, removedShortcuts, mUser);
                            }
                        }
                    });
                    if (!removedShortcuts.isEmpty()) {
                        deleteItemsFromDatabase(context, removedShortcuts);
                    }
                }
                if (!widgets.isEmpty()) {
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

            final ArrayList<String> removedPackageNames =
                    new ArrayList<String>();
            if (mOp == OP_REMOVE || mOp == OP_UNAVAILABLE) {
                // Mark all packages in the broadcast to be removed
                removedPackageNames.addAll(Arrays.asList(packages));
            } else if (mOp == OP_UPDATE) {
                // Mark disabled packages in the broadcast to be removed
                for (int i=0; i<N; i++) {
                    if (isPackageDisabled(context, packages[i], mUser)) {
                        removedPackageNames.add(packages[i]);
                    }
                }
            }

            if (!removedPackageNames.isEmpty() || !removedApps.isEmpty()) {
                final int removeReason;
                if (mOp == OP_UNAVAILABLE) {
                    removeReason = ShortcutInfo.FLAG_DISABLED_NOT_AVAILABLE;
                } else {
                    // Remove all the components associated with this package
                    for (String pn : removedPackageNames) {
                        deletePackageFromDatabase(context, pn, mUser);
                    }
                    // Remove all the specific components
                    for (AppInfo a : removedApps) {
                        ArrayList<ItemInfo> infos = getItemInfoForComponentName(a.componentName, mUser);
                        deleteItemsFromDatabase(context, infos);
                    }
                    removeReason = 0;
                }

                // Remove any queued items from the install queue
                InstallShortcutReceiver.removeFromInstallQueue(context, removedPackageNames, mUser);
                // Call the components-removed callback
                mHandler.post(new Runnable() {
                    public void run() {
                        Callbacks cb = getCallback();
                        if (callbacks == cb && cb != null) {
                            callbacks.bindComponentsRemoved(
                                    removedPackageNames, removedApps, mUser, removeReason);
                        }
                    }
                });
            }

            final ArrayList<Object> widgetsAndShortcuts =
                    getSortedWidgetsAndShortcuts(context);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    Callbacks cb = getCallback();
                    if (callbacks == cb && cb != null) {
                        callbacks.bindPackagesUpdated(widgetsAndShortcuts);
                    }
                }
            });

            // Write all the logs to disk
            mHandler.post(new Runnable() {
                public void run() {
                    Callbacks cb = getCallback();
                    if (callbacks == cb && cb != null) {
                        callbacks.dumpLogsToLocalData();
                    }
                }
            });
        }
    }

    // Returns a list of ResolveInfos/AppWindowInfos in sorted order
    public static ArrayList<Object> getSortedWidgetsAndShortcuts(Context context) {
        PackageManager packageManager = context.getPackageManager();
        final ArrayList<Object> widgetsAndShortcuts = new ArrayList<Object>();
        widgetsAndShortcuts.addAll(AppWidgetManagerCompat.getInstance(context).getAllProviders());

        Intent shortcutsIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
        widgetsAndShortcuts.addAll(packageManager.queryIntentActivities(shortcutsIntent, 0));
        Collections.sort(widgetsAndShortcuts, new WidgetAndShortcutNameComparator(context));
        return widgetsAndShortcuts;
    }

    private static boolean isPackageDisabled(Context context, String packageName,
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
    public ShortcutInfo getRestoredItemInfo(Cursor cursor, int titleIndex, Intent intent,
            int promiseType) {
        final ShortcutInfo info = new ShortcutInfo();
        info.user = UserHandleCompat.myUserHandle();
        mIconCache.getTitleAndIcon(info, intent, info.user, true);

        if ((promiseType & ShortcutInfo.FLAG_RESTORED_ICON) != 0) {
            String title = (cursor != null) ? cursor.getString(titleIndex) : null;
            if (!TextUtils.isEmpty(title)) {
                info.title = title;
            }
            info.status = ShortcutInfo.FLAG_RESTORED_ICON;
        } else if  ((promiseType & ShortcutInfo.FLAG_AUTOINTALL_ICON) != 0) {
            if (TextUtils.isEmpty(info.title)) {
                info.title = (cursor != null) ? cursor.getString(titleIndex) : "";
            }
            info.status = ShortcutInfo.FLAG_AUTOINTALL_ICON;
        } else {
            throw new InvalidParameterException("Invalid restoreType " + promiseType);
        }

        info.contentDescription = mUserManager.getBadgedLabelForUser(
                info.title.toString(), info.user);
        info.itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
        info.promisedIntent = intent;
        return info;
    }

    /**
     * Make an Intent object for a restored application or shortcut item that points
     * to the market page for the item.
     */
    private Intent getRestoredItemIntent(Cursor c, Context context, Intent intent) {
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
     * This is called from the code that adds shortcuts from the intent receiver.  This
     * doesn't have a Cursor, but
     */
    public ShortcutInfo getShortcutInfo(PackageManager manager, Intent intent,
            UserHandleCompat user, Context context) {
        return getShortcutInfo(manager, intent, user, context, null, -1, -1, null, false);
    }

    /**
     * Make an ShortcutInfo object for a shortcut that is an application.
     *
     * If c is not null, then it will be used to fill in missing data like the title and icon.
     */
    public ShortcutInfo getShortcutInfo(PackageManager manager, Intent intent,
            UserHandleCompat user, Context context, Cursor c, int iconIndex, int titleIndex,
            HashMap<Object, CharSequence> labelCache, boolean allowMissingTarget) {
        if (user == null) {
            Log.d(TAG, "Null user found in getShortcutInfo");
            return null;
        }

        ComponentName componentName = intent.getComponent();
        if (componentName == null) {
            Log.d(TAG, "Missing component found in getShortcutInfo: " + componentName);
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

        // the resource -- This may implicitly give us back the fallback icon,
        // but don't worry about that.  All we're doing with usingFallbackIcon is
        // to avoid saving lots of copies of that in the database, and most apps
        // have icons anyway.
        Bitmap icon = mIconCache.getIcon(componentName, lai, labelCache);

        // the db
        if (icon == null) {
            if (c != null) {
                icon = getIconFromCursor(c, iconIndex, context);
            }
        }
        // the fallback icon
        if (icon == null) {
            icon = mIconCache.getDefaultIcon(user);
            info.usingFallbackIcon = true;
        }
        info.setIcon(icon);

        // From the cache.
        if (labelCache != null) {
            info.title = labelCache.get(componentName);
        }

        // from the resource
        if (info.title == null && lai != null) {
            info.title = lai.getLabel();
            if (labelCache != null) {
                labelCache.put(componentName, info.title);
            }
        }
        // from the db
        if (info.title == null) {
            if (c != null) {
                info.title =  c.getString(titleIndex);
            }
        }
        // fall back to the class name of the activity
        if (info.title == null) {
            info.title = componentName.getClassName();
        }
        info.itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
        info.user = user;
        info.contentDescription = mUserManager.getBadgedLabelForUser(
                info.title.toString(), info.user);
        return info;
    }

    static ArrayList<ItemInfo> filterItemInfos(Collection<ItemInfo> infos,
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

    private ArrayList<ItemInfo> getItemInfoForComponentName(final ComponentName cname,
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
        return filterItemInfos(sBgItemsIdMap.values(), filter);
    }

    /**
     * Make an ShortcutInfo object for a shortcut that isn't an application.
     */
    private ShortcutInfo getShortcutInfo(Cursor c, Context context,
            int iconTypeIndex, int iconPackageIndex, int iconResourceIndex, int iconIndex,
            int titleIndex) {

        Bitmap icon = null;
        final ShortcutInfo info = new ShortcutInfo();
        // Non-app shortcuts are only supported for current user.
        info.user = UserHandleCompat.myUserHandle();
        info.itemType = LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;

        // TODO: If there's an explicit component and we can't install that, delete it.

        info.title = c.getString(titleIndex);

        int iconType = c.getInt(iconTypeIndex);
        switch (iconType) {
        case LauncherSettings.Favorites.ICON_TYPE_RESOURCE:
            String packageName = c.getString(iconPackageIndex);
            String resourceName = c.getString(iconResourceIndex);
            info.customIcon = false;
            // the resource
            icon = Utilities.createIconBitmap(packageName, resourceName, mIconCache, context);
            // the db
            if (icon == null) {
                icon = getIconFromCursor(c, iconIndex, context);
            }
            // the fallback icon
            if (icon == null) {
                icon = mIconCache.getDefaultIcon(info.user);
                info.usingFallbackIcon = true;
            }
            break;
        case LauncherSettings.Favorites.ICON_TYPE_BITMAP:
            icon = getIconFromCursor(c, iconIndex, context);
            if (icon == null) {
                icon = mIconCache.getDefaultIcon(info.user);
                info.customIcon = false;
                info.usingFallbackIcon = true;
            } else {
                info.customIcon = true;
            }
            break;
        default:
            icon = mIconCache.getDefaultIcon(info.user);
            info.usingFallbackIcon = true;
            info.customIcon = false;
            break;
        }
        info.setIcon(icon);
        return info;
    }

    Bitmap getIconFromCursor(Cursor c, int iconIndex, Context context) {
        @SuppressWarnings("all") // suppress dead code warning
        final boolean debug = false;
        if (debug) {
            Log.d(TAG, "getIconFromCursor app="
                    + c.getString(c.getColumnIndexOrThrow(LauncherSettings.Favorites.TITLE)));
        }
        byte[] data = c.getBlob(iconIndex);
        try {
            return Utilities.createIconBitmap(
                    BitmapFactory.decodeByteArray(data, 0, data.length), context);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Attempts to find an AppWidgetProviderInfo that matches the given component.
     */
    static AppWidgetProviderInfo findAppWidgetProviderInfoWithComponent(Context context,
            ComponentName component) {
        List<AppWidgetProviderInfo> widgets =
            AppWidgetManager.getInstance(context).getInstalledProviders();
        for (AppWidgetProviderInfo info : widgets) {
            if (info.provider.equals(component)) {
                return info;
            }
        }
        return null;
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
                        iconResource.resourceName, mIconCache, context);
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

        info.title = name;
        info.contentDescription = mUserManager.getBadgedLabelForUser(
                info.title.toString(), info.user);
        info.intent = intent;
        info.customIcon = customIcon;
        info.iconResource = iconResource;

        return info;
    }

    boolean queueIconToBeChecked(HashMap<Object, byte[]> cache, ShortcutInfo info, Cursor c,
            int iconIndex) {
        // If apps can't be on SD, don't even bother.
        if (!mAppsCanBeOnRemoveableStorage) {
            return false;
        }
        // If this icon doesn't have a custom icon, check to see
        // what's stored in the DB, and if it doesn't match what
        // we're going to show, store what we are going to show back
        // into the DB.  We do this so when we're loading, if the
        // package manager can't find an icon (for example because
        // the app is on SD) then we can use that instead.
        if (!info.customIcon && !info.usingFallbackIcon) {
            cache.put(info, c.getBlob(iconIndex));
            return true;
        }
        return false;
    }
    void updateSavedIcon(Context context, ShortcutInfo info, byte[] data) {
        boolean needSave = false;
        try {
            if (data != null) {
                Bitmap saved = BitmapFactory.decodeByteArray(data, 0, data.length);
                Bitmap loaded = info.getIcon(mIconCache);
                needSave = !saved.sameAs(loaded);
            } else {
                needSave = true;
            }
        } catch (Exception e) {
            needSave = true;
        }
        if (needSave) {
            Log.d(TAG, "going to save icon bitmap for info=" + info);
            // This is slower than is ideal, but this only happens once
            // or when the app is updated with a new icon.
            updateItemInDatabase(context, info);
        }
    }

    /**
     * Return an existing FolderInfo object if we have encountered this ID previously,
     * or make a new one.
     */
    private static FolderInfo findOrMakeFolder(HashMap<Long, FolderInfo> folders, long id) {
        // See if a placeholder was created for us already
        FolderInfo folderInfo = folders.get(id);
        if (folderInfo == null) {
            // No placeholder -- create a new instance
            folderInfo = new FolderInfo();
            folders.put(id, folderInfo);
        }
        return folderInfo;
    }

    public static final Comparator<AppInfo> getAppNameComparator() {
        final Collator collator = Collator.getInstance();
        return new Comparator<AppInfo>() {
            public final int compare(AppInfo a, AppInfo b) {
                if (a.user.equals(b.user)) {
                    int result = collator.compare(a.title.toString().trim(),
                            b.title.toString().trim());
                    if (result == 0) {
                        result = a.componentName.compareTo(b.componentName);
                    }
                    return result;
                } else {
                    // TODO Need to figure out rules for sorting
                    // profiles, this puts work second.
                    return a.user.toString().compareTo(b.user.toString());
                }
            }
        };
    }
    public static final Comparator<AppInfo> APP_INSTALL_TIME_COMPARATOR
            = new Comparator<AppInfo>() {
        public final int compare(AppInfo a, AppInfo b) {
            if (a.firstInstallTime < b.firstInstallTime) return 1;
            if (a.firstInstallTime > b.firstInstallTime) return -1;
            return 0;
        }
    };
    static ComponentName getComponentNameFromResolveInfo(ResolveInfo info) {
        if (info.activityInfo != null) {
            return new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
        } else {
            return new ComponentName(info.serviceInfo.packageName, info.serviceInfo.name);
        }
    }
    public static class ShortcutNameComparator implements Comparator<LauncherActivityInfoCompat> {
        private Collator mCollator;
        private HashMap<Object, CharSequence> mLabelCache;
        ShortcutNameComparator(PackageManager pm) {
            mLabelCache = new HashMap<Object, CharSequence>();
            mCollator = Collator.getInstance();
        }
        ShortcutNameComparator(HashMap<Object, CharSequence> labelCache) {
            mLabelCache = labelCache;
            mCollator = Collator.getInstance();
        }
        public final int compare(LauncherActivityInfoCompat a, LauncherActivityInfoCompat b) {
            String labelA, labelB;
            ComponentName keyA = a.getComponentName();
            ComponentName keyB = b.getComponentName();
            if (mLabelCache.containsKey(keyA)) {
                labelA = mLabelCache.get(keyA).toString();
            } else {
                labelA = a.getLabel().toString().trim();

                mLabelCache.put(keyA, labelA);
            }
            if (mLabelCache.containsKey(keyB)) {
                labelB = mLabelCache.get(keyB).toString();
            } else {
                labelB = b.getLabel().toString().trim();

                mLabelCache.put(keyB, labelB);
            }
            return mCollator.compare(labelA, labelB);
        }
    };
    public static class WidgetAndShortcutNameComparator implements Comparator<Object> {
        private final AppWidgetManagerCompat mManager;
        private final PackageManager mPackageManager;
        private final HashMap<Object, String> mLabelCache;
        private final Collator mCollator;

        WidgetAndShortcutNameComparator(Context context) {
            mManager = AppWidgetManagerCompat.getInstance(context);
            mPackageManager = context.getPackageManager();
            mLabelCache = new HashMap<Object, String>();
            mCollator = Collator.getInstance();
        }
        public final int compare(Object a, Object b) {
            String labelA, labelB;
            if (mLabelCache.containsKey(a)) {
                labelA = mLabelCache.get(a);
            } else {
                labelA = (a instanceof AppWidgetProviderInfo)
                        ? mManager.loadLabel((AppWidgetProviderInfo) a)
                        : ((ResolveInfo) a).loadLabel(mPackageManager).toString().trim();
                mLabelCache.put(a, labelA);
            }
            if (mLabelCache.containsKey(b)) {
                labelB = mLabelCache.get(b);
            } else {
                labelB = (b instanceof AppWidgetProviderInfo)
                        ? mManager.loadLabel((AppWidgetProviderInfo) b)
                        : ((ResolveInfo) b).loadLabel(mPackageManager).toString().trim();
                mLabelCache.put(b, labelB);
            }
            return mCollator.compare(labelA, labelB);
        }
    };

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
}

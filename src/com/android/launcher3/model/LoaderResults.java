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

package com.android.launcher3.model;

import android.os.Looper;
import android.util.Log;

import com.android.launcher3.AllAppsList;
import com.android.launcher3.AppInfo;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherAppWidgetInfo;
import com.android.launcher3.LauncherModel.Callbacks;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.PagedView;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.LooperIdleLock;
import com.android.launcher3.util.MultiHashMap;
import com.android.launcher3.util.ViewOnDrawExecutor;
import com.android.launcher3.widget.WidgetListRowEntry;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Helper class to handle results of {@link com.android.launcher3.model.LoaderTask}.
 */
public class LoaderResults {

    private static final String TAG = "LoaderResults";
    private static final long INVALID_SCREEN_ID = -1L;
    private static final int ITEMS_CHUNK = 6; // batch size for the workspace icons

    private final Executor mUiExecutor;

    private final LauncherAppState mApp;
    private final BgDataModel mBgDataModel;
    private final AllAppsList mBgAllAppsList;
    private final int mPageToBindFirst;

    private final WeakReference<Callbacks> mCallbacks;

    public LoaderResults(LauncherAppState app, BgDataModel dataModel,
            AllAppsList allAppsList, int pageToBindFirst, WeakReference<Callbacks> callbacks) {
        mUiExecutor = new MainThreadExecutor();
        mApp = app;
        mBgDataModel = dataModel;
        mBgAllAppsList = allAppsList;
        mPageToBindFirst = pageToBindFirst;
        mCallbacks = callbacks == null ? new WeakReference<Callbacks>(null) : callbacks;
    }

    /**
     * Binds all loaded data to actual views on the main thread.
     */
    public void bindWorkspace() {
        Runnable r;

        Callbacks callbacks = mCallbacks.get();
        // Don't use these two variables in any of the callback runnables.
        // Otherwise we hold a reference to them.
        if (callbacks == null) {
            // This launcher has exited and nobody bothered to tell us.  Just bail.
            Log.w(TAG, "LoaderTask running with no launcher");
            return;
        }

        // Save a copy of all the bg-thread collections
        ArrayList<ItemInfo> workspaceItems = new ArrayList<>();
        ArrayList<LauncherAppWidgetInfo> appWidgets = new ArrayList<>();
        final ArrayList<Long> orderedScreenIds = new ArrayList<>();

        synchronized (mBgDataModel) {
            workspaceItems.addAll(mBgDataModel.workspaceItems);
            appWidgets.addAll(mBgDataModel.appWidgets);
            orderedScreenIds.addAll(mBgDataModel.workspaceScreens);
            mBgDataModel.lastBindId++;
        }

        final int currentScreen;
        {
            int currScreen = mPageToBindFirst != PagedView.INVALID_RESTORE_PAGE
                    ? mPageToBindFirst : callbacks.getCurrentWorkspaceScreen();
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
        filterCurrentWorkspaceItems(currentScreenId, appWidgets, currentAppWidgets,
                otherAppWidgets);
        sortWorkspaceItemsSpatially(currentWorkspaceItems);
        sortWorkspaceItemsSpatially(otherWorkspaceItems);

        // Tell the workspace that we're about to start binding items
        r = new Runnable() {
            public void run() {
                Callbacks callbacks = mCallbacks.get();
                if (callbacks != null) {
                    callbacks.clearPendingBinds();
                    callbacks.startBinding();
                }
            }
        };
        mUiExecutor.execute(r);

        // Bind workspace screens
        mUiExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Callbacks callbacks = mCallbacks.get();
                if (callbacks != null) {
                    callbacks.bindScreens(orderedScreenIds);
                }
            }
        });

        Executor mainExecutor = mUiExecutor;
        // Load items on the current page.
        bindWorkspaceItems(currentWorkspaceItems, currentAppWidgets, mainExecutor);

        // In case of validFirstPage, only bind the first screen, and defer binding the
        // remaining screens after first onDraw (and an optional the fade animation whichever
        // happens later).
        // This ensures that the first screen is immediately visible (eg. during rotation)
        // In case of !validFirstPage, bind all pages one after other.
        final Executor deferredExecutor =
                validFirstPage ? new ViewOnDrawExecutor() : mainExecutor;

        mainExecutor.execute(new Runnable() {
            @Override
            public void run() {
                Callbacks callbacks = mCallbacks.get();
                if (callbacks != null) {
                    callbacks.finishFirstPageBind(
                            validFirstPage ? (ViewOnDrawExecutor) deferredExecutor : null);
                }
            }
        });

        bindWorkspaceItems(otherWorkspaceItems, otherAppWidgets, deferredExecutor);

        // Tell the workspace that we're done binding items
        r = new Runnable() {
            public void run() {
                Callbacks callbacks = mCallbacks.get();
                if (callbacks != null) {
                    callbacks.finishBindingItems();
                }
            }
        };
        deferredExecutor.execute(r);

        if (validFirstPage) {
            r = new Runnable() {
                public void run() {
                    Callbacks callbacks = mCallbacks.get();
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
            mUiExecutor.execute(r);
        }
    }


    /** Filters the set of items who are directly or indirectly (via another container) on the
     * specified screen. */
    public static <T extends ItemInfo> void filterCurrentWorkspaceItems(long currentScreenId,
            ArrayList<T> allWorkspaceItems,
            ArrayList<T> currentScreenItems,
            ArrayList<T> otherScreenItems) {
        // Purge any null ItemInfos
        Iterator<T> iter = allWorkspaceItems.iterator();
        while (iter.hasNext()) {
            ItemInfo i = iter.next();
            if (i == null) {
                iter.remove();
            }
        }

        // Order the set of items by their containers first, this allows use to walk through the
        // list sequentially, build up a list of containers that are in the specified screen,
        // as well as all items in those containers.
        Set<Long> itemsOnScreen = new HashSet<>();
        Collections.sort(allWorkspaceItems, new Comparator<ItemInfo>() {
            @Override
            public int compare(ItemInfo lhs, ItemInfo rhs) {
                return Utilities.longCompare(lhs.container, rhs.container);
            }
        });
        for (T info : allWorkspaceItems) {
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

    /** Sorts the set of items by hotseat, workspace (spatially from top to bottom, left to
     * right) */
    private void sortWorkspaceItemsSpatially(ArrayList<ItemInfo> workspaceItems) {
        final InvariantDeviceProfile profile = mApp.getInvariantDeviceProfile();
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
                            if (FeatureFlags.IS_DOGFOOD_BUILD) {
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

    private void bindWorkspaceItems(final ArrayList<ItemInfo> workspaceItems,
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
                    Callbacks callbacks = mCallbacks.get();
                    if (callbacks != null) {
                        callbacks.bindItems(workspaceItems.subList(start, start+chunkSize), false);
                    }
                }
            };
            executor.execute(r);
        }

        // Bind the widgets, one at a time
        N = appWidgets.size();
        for (int i = 0; i < N; i++) {
            final ItemInfo widget = appWidgets.get(i);
            final Runnable r = new Runnable() {
                public void run() {
                    Callbacks callbacks = mCallbacks.get();
                    if (callbacks != null) {
                        callbacks.bindItems(Collections.singletonList(widget), false);
                    }
                }
            };
            executor.execute(r);
        }
    }

    public void bindDeepShortcuts() {
        final MultiHashMap<ComponentKey, String> shortcutMapCopy;
        synchronized (mBgDataModel) {
            shortcutMapCopy = mBgDataModel.deepShortcutMap.clone();
        }
        Runnable r = new Runnable() {
            @Override
            public void run() {
                Callbacks callbacks = mCallbacks.get();
                if (callbacks != null) {
                    callbacks.bindDeepShortcutMap(shortcutMapCopy);
                }
            }
        };
        mUiExecutor.execute(r);
    }

    public void bindAllApps() {
        // shallow copy
        @SuppressWarnings("unchecked")
        final ArrayList<AppInfo> list = (ArrayList<AppInfo>) mBgAllAppsList.data.clone();

        Runnable r = new Runnable() {
            public void run() {
                Callbacks callbacks = mCallbacks.get();
                if (callbacks != null) {
                    callbacks.bindAllApplications(list);
                }
            }
        };
        mUiExecutor.execute(r);
    }

    public void bindWidgets() {
        final ArrayList<WidgetListRowEntry> widgets =
                mBgDataModel.widgetsModel.getWidgetsList(mApp.getContext());
        Runnable r = new Runnable() {
            public void run() {
                Callbacks callbacks = mCallbacks.get();
                if (callbacks != null) {
                    callbacks.bindAllWidgets(widgets);
                }
            }
        };
        mUiExecutor.execute(r);
    }

    public LooperIdleLock newIdleLock(Object lock) {
        LooperIdleLock idleLock = new LooperIdleLock(lock, Looper.getMainLooper());
        // If we are not binding, there is no reason to wait for idle.
        if (mCallbacks.get() == null) {
            idleLock.queueIdle();
        }
        return idleLock;
    }
}

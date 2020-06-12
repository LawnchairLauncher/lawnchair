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

import static com.android.launcher3.model.ModelUtils.filterCurrentWorkspaceItems;
import static com.android.launcher3.model.ModelUtils.getMissingHotseatRanks;
import static com.android.launcher3.model.ModelUtils.sortWorkspaceItemsSpatially;

import android.util.Log;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel.CallbackTask;
import com.android.launcher3.PagedView;
import com.android.launcher3.model.BgDataModel.Callbacks;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.LauncherAppWidgetInfo;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.LooperExecutor;
import com.android.launcher3.util.LooperIdleLock;
import com.android.launcher3.util.ViewOnDrawExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Base Helper class to handle results of {@link com.android.launcher3.model.LoaderTask}.
 */
public abstract class BaseLoaderResults {

    protected static final String TAG = "LoaderResults";
    protected static final int INVALID_SCREEN_ID = -1;
    private static final int ITEMS_CHUNK = 6; // batch size for the workspace icons

    protected final LooperExecutor mUiExecutor;

    protected final LauncherAppState mApp;
    protected final BgDataModel mBgDataModel;
    private final AllAppsList mBgAllAppsList;

    private final Callbacks[] mCallbacksList;

    private int mMyBindingId;

    public BaseLoaderResults(LauncherAppState app, BgDataModel dataModel,
            AllAppsList allAppsList, Callbacks[] callbacksList, LooperExecutor uiExecutor) {
        mUiExecutor = uiExecutor;
        mApp = app;
        mBgDataModel = dataModel;
        mBgAllAppsList = allAppsList;
        mCallbacksList = callbacksList;
    }

    /**
     * Binds all loaded data to actual views on the main thread.
     */
    public void bindWorkspace() {
        // Save a copy of all the bg-thread collections
        ArrayList<ItemInfo> workspaceItems = new ArrayList<>();
        ArrayList<LauncherAppWidgetInfo> appWidgets = new ArrayList<>();
        final IntArray orderedScreenIds = new IntArray();

        synchronized (mBgDataModel) {
            workspaceItems.addAll(mBgDataModel.workspaceItems);
            appWidgets.addAll(mBgDataModel.appWidgets);
            orderedScreenIds.addAll(mBgDataModel.collectWorkspaceScreens());
            mBgDataModel.lastBindId++;
            mMyBindingId = mBgDataModel.lastBindId;
        }

        for (Callbacks cb : mCallbacksList) {
            new WorkspaceBinder(cb, mUiExecutor, mApp, mBgDataModel, mMyBindingId,
                    workspaceItems, appWidgets, orderedScreenIds).bind();
        }
    }

    public abstract void bindDeepShortcuts();

    public void bindAllApps() {
        // shallow copy
        AppInfo[] apps = mBgAllAppsList.copyData();
        int flags = mBgAllAppsList.getFlags();
        executeCallbacksTask(c -> c.bindAllApplications(apps, flags), mUiExecutor);
    }

    public abstract void bindWidgets();

    protected void executeCallbacksTask(CallbackTask task, Executor executor) {
        executor.execute(() -> {
            if (mMyBindingId != mBgDataModel.lastBindId) {
                Log.d(TAG, "Too many consecutive reloads, skipping obsolete data-bind");
                return;
            }
            for (Callbacks cb : mCallbacksList) {
                task.execute(cb);
            }
        });
    }

    public LooperIdleLock newIdleLock(Object lock) {
        LooperIdleLock idleLock = new LooperIdleLock(lock, mUiExecutor.getLooper());
        // If we are not binding or if the main looper is already idle, there is no reason to wait
        if (mUiExecutor.getLooper().getQueue().isIdle()) {
            idleLock.queueIdle();
        }
        return idleLock;
    }

    private static class WorkspaceBinder {

        private final Executor mUiExecutor;
        private final Callbacks mCallbacks;

        private final LauncherAppState mApp;
        private final BgDataModel mBgDataModel;

        private final int mMyBindingId;
        private final ArrayList<ItemInfo> mWorkspaceItems;
        private final ArrayList<LauncherAppWidgetInfo> mAppWidgets;
        private final IntArray mOrderedScreenIds;


        WorkspaceBinder(Callbacks callbacks,
                Executor uiExecutor,
                LauncherAppState app,
                BgDataModel bgDataModel,
                int myBindingId,
                ArrayList<ItemInfo> workspaceItems,
                ArrayList<LauncherAppWidgetInfo> appWidgets,
                IntArray orderedScreenIds) {
            mCallbacks = callbacks;
            mUiExecutor = uiExecutor;
            mApp = app;
            mBgDataModel = bgDataModel;
            mMyBindingId = myBindingId;
            mWorkspaceItems = workspaceItems;
            mAppWidgets = appWidgets;
            mOrderedScreenIds = orderedScreenIds;
        }

        private void bind() {
            final int currentScreen;
            {
                // Create an anonymous scope to calculate currentScreen as it has to be a
                // final variable.
                int currScreen = mCallbacks.getPageToBindSynchronously();
                if (currScreen >= mOrderedScreenIds.size()) {
                    // There may be no workspace screens (just hotseat items and an empty page).
                    currScreen = PagedView.INVALID_PAGE;
                }
                currentScreen = currScreen;
            }
            final boolean validFirstPage = currentScreen >= 0;
            final int currentScreenId =
                    validFirstPage ? mOrderedScreenIds.get(currentScreen) : INVALID_SCREEN_ID;

            // Separate the items that are on the current screen, and all the other remaining items
            ArrayList<ItemInfo> currentWorkspaceItems = new ArrayList<>();
            ArrayList<ItemInfo> otherWorkspaceItems = new ArrayList<>();
            ArrayList<LauncherAppWidgetInfo> currentAppWidgets = new ArrayList<>();
            ArrayList<LauncherAppWidgetInfo> otherAppWidgets = new ArrayList<>();

            filterCurrentWorkspaceItems(currentScreenId, mWorkspaceItems, currentWorkspaceItems,
                    otherWorkspaceItems);
            filterCurrentWorkspaceItems(currentScreenId, mAppWidgets, currentAppWidgets,
                    otherAppWidgets);
            final InvariantDeviceProfile idp = mApp.getInvariantDeviceProfile();
            sortWorkspaceItemsSpatially(idp, currentWorkspaceItems);
            sortWorkspaceItemsSpatially(idp, otherWorkspaceItems);

            // Tell the workspace that we're about to start binding items
            executeCallbacksTask(c -> {
                c.clearPendingBinds();
                c.startBinding();
            }, mUiExecutor);

            // Bind workspace screens
            executeCallbacksTask(c -> c.bindScreens(mOrderedScreenIds), mUiExecutor);

            Executor mainExecutor = mUiExecutor;
            // Load items on the current page.
            bindWorkspaceItems(currentWorkspaceItems, mainExecutor);
            bindAppWidgets(currentAppWidgets, mainExecutor);

            // Locate available spots for prediction using currentWorkspaceItems
            IntArray gaps = getMissingHotseatRanks(currentWorkspaceItems, idp.numHotseatIcons);
            bindPredictedItems(gaps, mainExecutor);
            // In case of validFirstPage, only bind the first screen, and defer binding the
            // remaining screens after first onDraw (and an optional the fade animation whichever
            // happens later).
            // This ensures that the first screen is immediately visible (eg. during rotation)
            // In case of !validFirstPage, bind all pages one after other.
            final Executor deferredExecutor =
                    validFirstPage ? new ViewOnDrawExecutor() : mainExecutor;

            executeCallbacksTask(c -> c.finishFirstPageBind(
                    validFirstPage ? (ViewOnDrawExecutor) deferredExecutor : null), mainExecutor);

            bindWorkspaceItems(otherWorkspaceItems, deferredExecutor);
            bindAppWidgets(otherAppWidgets, deferredExecutor);
            // Tell the workspace that we're done binding items
            executeCallbacksTask(c -> c.finishBindingItems(currentScreen), deferredExecutor);

            if (validFirstPage) {
                executeCallbacksTask(c -> {
                    // We are loading synchronously, which means, some of the pages will be
                    // bound after first draw. Inform the mCallbacks that page binding is
                    // not complete, and schedule the remaining pages.
                    c.onPageBoundSynchronously(currentScreen);
                    c.executeOnNextDraw((ViewOnDrawExecutor) deferredExecutor);

                }, mUiExecutor);
            }
        }

        private void bindWorkspaceItems(
                final ArrayList<ItemInfo> workspaceItems, final Executor executor) {
            // Bind the workspace items
            int count = workspaceItems.size();
            for (int i = 0; i < count; i += ITEMS_CHUNK) {
                final int start = i;
                final int chunkSize = (i + ITEMS_CHUNK <= count) ? ITEMS_CHUNK : (count - i);
                executeCallbacksTask(
                        c -> c.bindItems(workspaceItems.subList(start, start + chunkSize), false),
                        executor);
            }
        }

        private void bindAppWidgets(List<LauncherAppWidgetInfo> appWidgets, Executor executor) {
            // Bind the widgets, one at a time
            int count = appWidgets.size();
            for (int i = 0; i < count; i++) {
                final ItemInfo widget = appWidgets.get(i);
                executeCallbacksTask(
                        c -> c.bindItems(Collections.singletonList(widget), false), executor);
            }
        }

        private void bindPredictedItems(IntArray ranks, final Executor executor) {
            ArrayList<AppInfo> items = new ArrayList<>(mBgDataModel.cachedPredictedItems);
            executeCallbacksTask(c -> c.bindPredictedItems(items, ranks), executor);
        }

        protected void executeCallbacksTask(CallbackTask task, Executor executor) {
            executor.execute(() -> {
                if (mMyBindingId != mBgDataModel.lastBindId) {
                    Log.d(TAG, "Too many consecutive reloads, skipping obsolete data-bind");
                    return;
                }
                task.execute(mCallbacks);
            });
        }
    }
}

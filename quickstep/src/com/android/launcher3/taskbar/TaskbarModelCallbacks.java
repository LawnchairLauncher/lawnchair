/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_ALL_APPS_PREDICTION;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;

import android.util.SparseArray;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.celllayout.CellInfo;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.PredictedContainerInfo;
import com.android.launcher3.model.data.WorkspaceData;
import com.android.launcher3.taskbar.TaskbarView.TaskbarLayoutParams;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.LauncherBindableItemsContainer;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.Preconditions;
import com.android.quickstep.util.GroupTask;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Launcher model Callbacks for rendering taskbar.
 */
public class TaskbarModelCallbacks implements
        BgDataModel.Callbacks, LauncherBindableItemsContainer {

    private final SparseArray<ItemInfo> mHotseatItems = new SparseArray<>();
    private List<ItemInfo> mPredictedItems = Collections.emptyList();

    private final TaskbarActivityContext mContext;
    private final TaskbarView mContainer;

    // Initialized in init.
    protected TaskbarControllers mControllers;

    // Used to defer any UI updates during the SUW unstash animation.
    private boolean mDeferUpdatesForSUW;
    private Runnable mDeferredUpdates;

    public TaskbarModelCallbacks(
            TaskbarActivityContext context, TaskbarView container) {
        mContext = context;
        mContainer = container;
    }

    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
    }

    @Override
    public void bindCompleteModel(WorkspaceData itemIdMap, boolean isBindingSync) {
        mHotseatItems.clear();
        mPredictedItems = itemIdMap.getPredictedContents(CONTAINER_HOTSEAT_PREDICTION);
        handleItemsAdded(itemIdMap);

        if (itemIdMap.get(CONTAINER_ALL_APPS_PREDICTION) instanceof PredictedContainerInfo pci) {
            mControllers.taskbarAllAppsController.setPredictedApps(pci.getContents());
        }
        commitItemsToUI();
    }

    @Override
    public void bindItemsAdded(List<ItemInfo> items) {
        if (handleItemsAdded(items)) {
            commitItemsToUI();
        }
    }

    private boolean handleItemsAdded(Iterable<ItemInfo> items) {
        boolean modified = false;
        for (ItemInfo item : items) {
            if (item.container == Favorites.CONTAINER_HOTSEAT) {
                mHotseatItems.put(item.screenId, item);
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public void bindItemsUpdated(@NonNull Set<ItemInfo> updates) {
        Set<ItemInfo> itemsToRebind = updateContainerItems(updates, mContext);
        boolean removed = handleItemsRemoved(ItemInfoMatcher.ofItems(itemsToRebind));
        boolean added = handleItemsAdded(itemsToRebind);

        boolean predictionsUpdated = false;
        for (ItemInfo update: updates) {
            if (update instanceof PredictedContainerInfo pci) {
                if (pci.id == Favorites.CONTAINER_HOTSEAT_PREDICTION) {
                    mPredictedItems = pci.getContents();
                    predictionsUpdated = true;
                } else if (pci.id == CONTAINER_ALL_APPS_PREDICTION) {
                    mControllers.taskbarAllAppsController.setPredictedApps(pci.getContents());
                }
            }
        }
        if (removed || added || predictionsUpdated) {
            commitItemsToUI();
        }
    }

    @Nullable
    @Override
    public CellInfo getCellInfoForView(@NonNull View view) {
        return view.getLayoutParams() instanceof TaskbarLayoutParams tlp ? tlp.bindInfo : null;
    }

    @Override
    public boolean isContainerSupported(int container) {
        return container == CONTAINER_HOTSEAT || container == CONTAINER_HOTSEAT_PREDICTION;
    }

    @Override
    public View mapOverItems(@NonNull ItemOperator op) {
        final int itemCount = mContainer.getChildCount();
        for (int itemIdx = 0; itemIdx < itemCount; itemIdx++) {
            View item = mContainer.getChildAt(itemIdx);
            if (item.getTag() instanceof ItemInfo itemInfo && op.evaluate(itemInfo, item)) {
                return item;
            }
        }
        return null;
    }

    @Override
    public void bindWorkspaceComponentsRemoved(Predicate<ItemInfo> matcher) {
        if (handleItemsRemoved(matcher)) {
            commitItemsToUI();
        }
    }

    private boolean handleItemsRemoved(Predicate<ItemInfo> matcher) {
        boolean modified = false;
        for (int i = mHotseatItems.size() - 1; i >= 0; i--) {
            if (matcher.test(mHotseatItems.valueAt(i))) {
                modified = true;
                mHotseatItems.removeAt(i);
            }
        }
        return modified;
    }

    private void commitItemsToUI() {
        ItemInfo[] hotseatItemInfos =
                new ItemInfo[mContext.getDeviceProfile().numShownHotseatIcons];
        int predictionSize = mPredictedItems.size();
        int predictionNextIndex = 0;

        for (int i = 0; i < hotseatItemInfos.length; i++) {
            hotseatItemInfos[i] = mHotseatItems.get(i);
            if (hotseatItemInfos[i] == null && predictionNextIndex < predictionSize) {
                hotseatItemInfos[i] = mPredictedItems.get(predictionNextIndex);
                hotseatItemInfos[i].screenId = i;
                predictionNextIndex++;
            }
        }

        final TaskbarRecentAppsController recentAppsController =
                mControllers.taskbarRecentAppsController;
        hotseatItemInfos = recentAppsController.updateHotseatItemInfos(hotseatItemInfos);

        if (mDeferUpdatesForSUW) {
            ItemInfo[] finalHotseatItemInfos = hotseatItemInfos;
            mDeferredUpdates = () ->
                    commitHotseatItemUpdates(finalHotseatItemInfos,
                            recentAppsController.getShownTasks());
        } else {
            commitHotseatItemUpdates(hotseatItemInfos, recentAppsController.getShownTasks());
        }
    }

    private void commitHotseatItemUpdates(
            ItemInfo[] hotseatItemInfos, List<GroupTask> recentTasks) {
        mContainer.updateItems(hotseatItemInfos, recentTasks);
        mControllers.taskbarViewController.updateIconViewsRunningStates();
        mControllers.taskbarPopupController.setHotseatInfosList(mHotseatItems);
    }

    /**
     * This is used to defer UI updates after SUW builds the unstash animation.
     * @param defer if true, defers updates to the UI
     *              if false, posts updates (if any) to the UI
     */
    public void setDeferUpdatesForSUW(boolean defer) {
        mDeferUpdatesForSUW = defer;

        if (!mDeferUpdatesForSUW) {
            if (mDeferredUpdates != null) {
                mContainer.post(mDeferredUpdates);
                mDeferredUpdates = null;
            }
        }
    }

    /** Called when there's a change in running apps to update the UI. */
    public void commitRunningAppsToUI() {
        commitItemsToUI();
    }

    @Override
    public void bindDeepShortcutMap(HashMap<ComponentKey, Integer> deepShortcutMapCopy) {
        mControllers.taskbarPopupController.setDeepShortcutMap(deepShortcutMapCopy);
    }

    @UiThread
    @Override
    public void bindAllApplications(AppInfo[] apps, int flags,
            Map<PackageUserKey, Integer> packageUserKeytoUidMap) {
        Preconditions.assertUIThread();
        mControllers.taskbarAllAppsController.setApps(apps, flags, packageUserKeytoUidMap);
        mControllers.taskbarPopupController.setApps(apps);
    }

    protected void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarModelCallbacks:");

        pw.println(String.format("%s\thotseat items count=%s", prefix, mHotseatItems.size()));
        if (mPredictedItems != null) {
            pw.println(
                    String.format("%s\tpredicted items count=%s", prefix, mPredictedItems.size()));
        }
        pw.println(String.format("%s\tmDeferUpdatesForSUW=%b", prefix, mDeferUpdatesForSUW));
        pw.println(String.format("%s\tupdates pending=%b", prefix, (mDeferredUpdates != null)));
    }
}

/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.text.format.DateUtils.DAY_IN_MILLIS;
import static android.text.format.DateUtils.formatElapsedTime;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_PREDICTION;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_WIDGETS_PREDICTION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
import static com.android.launcher3.Utilities.getDevicePrefs;
import static com.android.launcher3.hybridhotseat.HotseatPredictionModel.convertDataModelToAppTargetBundle;

import android.app.prediction.AppPredictionContext;
import android.app.prediction.AppPredictionManager;
import android.app.prediction.AppPredictor;
import android.app.prediction.AppTarget;
import android.app.prediction.AppTargetEvent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.InstanceIdSequence;
import com.android.launcher3.model.BgDataModel.FixedContainerItems;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.IntSparseArrayMap;
import com.android.launcher3.util.PersistedItemArray;
import com.android.quickstep.logging.StatsLogCompatManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Model delegate which loads prediction items
 */
public class QuickstepModelDelegate extends ModelDelegate implements OnIDPChangeListener {

    public static final String LAST_PREDICTION_ENABLED_STATE = "last_prediction_enabled_state";
    private static final String LAST_SNAPSHOT_TIME_MILLIS = "LAST_SNAPSHOT_TIME_MILLIS";
    private static final int NUM_OF_RECOMMENDED_WIDGETS_PREDICATION = 20;

    private static final boolean IS_DEBUG = false;
    private static final String TAG = "QuickstepModelDelegate";

    private final PredictorState mAllAppsState =
            new PredictorState(CONTAINER_PREDICTION, "all_apps_predictions");
    private final PredictorState mHotseatState =
            new PredictorState(CONTAINER_HOTSEAT_PREDICTION, "hotseat_predictions");
    private final PredictorState mWidgetsRecommendationState =
            new PredictorState(CONTAINER_WIDGETS_PREDICTION, "widgets_prediction");

    private final InvariantDeviceProfile mIDP;
    private final AppEventProducer mAppEventProducer;

    protected boolean mActive = false;

    public QuickstepModelDelegate(Context context) {
        mAppEventProducer = new AppEventProducer(context, this::onAppTargetEvent);

        mIDP = InvariantDeviceProfile.INSTANCE.get(context);
        mIDP.addOnChangeListener(this);
        StatsLogCompatManager.LOGS_CONSUMER.add(mAppEventProducer);
    }

    @Override
    @WorkerThread
    public void loadItems(UserManagerState ums, Map<ShortcutKey, ShortcutInfo> pinnedShortcuts) {
        // TODO: Implement caching and preloading
        super.loadItems(ums, pinnedShortcuts);

        WorkspaceItemFactory allAppsFactory = new WorkspaceItemFactory(
                mApp, ums, pinnedShortcuts, mIDP.numDatabaseAllAppsColumns);
        mAllAppsState.items.setItems(
                mAllAppsState.storage.read(mApp.getContext(), allAppsFactory, ums.allUsers::get));
        mDataModel.extraItems.put(CONTAINER_PREDICTION, mAllAppsState.items);

        WorkspaceItemFactory hotseatFactory =
                new WorkspaceItemFactory(mApp, ums, pinnedShortcuts, mIDP.numDatabaseHotseatIcons);
        mHotseatState.items.setItems(
                mHotseatState.storage.read(mApp.getContext(), hotseatFactory, ums.allUsers::get));
        mDataModel.extraItems.put(CONTAINER_HOTSEAT_PREDICTION, mHotseatState.items);

        // Widgets prediction isn't used frequently. And thus, it is not persisted on disk.
        mDataModel.extraItems.put(CONTAINER_WIDGETS_PREDICTION, mWidgetsRecommendationState.items);
        mActive = true;
    }

    @Override
    public void workspaceLoadComplete() {
        super.workspaceLoadComplete();
        recreatePredictors();
    }

    @Override
    @WorkerThread
    public void modelLoadComplete() {
        super.modelLoadComplete();

        // Log snapshot of the model
        SharedPreferences prefs = getDevicePrefs(mApp.getContext());
        long lastSnapshotTimeMillis = prefs.getLong(LAST_SNAPSHOT_TIME_MILLIS, 0);
        // Log snapshot only if previous snapshot was older than a day
        long now = System.currentTimeMillis();
        if (now - lastSnapshotTimeMillis < DAY_IN_MILLIS) {
            if (IS_DEBUG) {
                String elapsedTime = formatElapsedTime((now - lastSnapshotTimeMillis) / 1000);
                Log.d(TAG, String.format(
                        "Skipped snapshot logging since previous snapshot was %s old.",
                        elapsedTime));
            }
        } else {
            IntSparseArrayMap<ItemInfo> itemsIdMap;
            synchronized (mDataModel) {
                itemsIdMap = mDataModel.itemsIdMap.clone();
            }
            InstanceId instanceId = new InstanceIdSequence().newInstanceId();
            for (ItemInfo info : itemsIdMap) {
                FolderInfo parent = info.container > 0
                        ? (FolderInfo) itemsIdMap.get(info.container) : null;
                StatsLogCompatManager.writeSnapshot(info.buildProto(parent), instanceId);
            }
            additionalSnapshotEvents(instanceId);
            prefs.edit().putLong(LAST_SNAPSHOT_TIME_MILLIS, now).apply();
        }
    }

    protected void additionalSnapshotEvents(InstanceId snapshotInstanceId){}

    @Override
    public void validateData() {
        super.validateData();
        if (mAllAppsState.predictor != null) {
            mAllAppsState.predictor.requestPredictionUpdate();
        }
        if (mWidgetsRecommendationState.predictor != null) {
            mWidgetsRecommendationState.predictor.requestPredictionUpdate();
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        mActive = false;
        StatsLogCompatManager.LOGS_CONSUMER.remove(mAppEventProducer);

        destroyPredictors();
        mIDP.removeOnChangeListener(this);
    }

    private void destroyPredictors() {
        mAllAppsState.destroyPredictor();
        mHotseatState.destroyPredictor();
        mWidgetsRecommendationState.destroyPredictor();
    }

    @WorkerThread
    private void recreatePredictors() {
        destroyPredictors();
        if (!mActive) {
            return;
        }
        Context context = mApp.getContext();
        AppPredictionManager apm = context.getSystemService(AppPredictionManager.class);
        if (apm == null) {
            return;
        }

        registerPredictor(mAllAppsState, apm.createAppPredictionSession(
                new AppPredictionContext.Builder(context)
                        .setUiSurface("home")
                        .setPredictedTargetCount(mIDP.numDatabaseAllAppsColumns)
                        .build()));

        // TODO: get bundle
        registerPredictor(mHotseatState, apm.createAppPredictionSession(
                new AppPredictionContext.Builder(context)
                        .setUiSurface("hotseat")
                        .setPredictedTargetCount(mIDP.numDatabaseHotseatIcons)
                        .setExtras(convertDataModelToAppTargetBundle(context, mDataModel))
                        .build()));

        registerWidgetsPredictor(apm.createAppPredictionSession(
                new AppPredictionContext.Builder(context)
                        .setUiSurface("widgets")
                        .setPredictedTargetCount(NUM_OF_RECOMMENDED_WIDGETS_PREDICATION)
                        .build()));
    }

    private void registerPredictor(PredictorState state, AppPredictor predictor) {
        state.predictor = predictor;
        state.predictor.registerPredictionUpdates(
                Executors.MODEL_EXECUTOR, t -> handleUpdate(state, t));
        state.predictor.requestPredictionUpdate();
    }

    private void handleUpdate(PredictorState state, List<AppTarget> targets) {
        if (state.setTargets(targets)) {
            // No diff, skip
            return;
        }
        mApp.getModel().enqueueModelUpdateTask(new PredictionUpdateTask(state, targets));
    }

    private void registerWidgetsPredictor(AppPredictor predictor) {
        mWidgetsRecommendationState.predictor = predictor;
        mWidgetsRecommendationState.predictor.registerPredictionUpdates(
                Executors.MODEL_EXECUTOR, targets -> {
                    if (mWidgetsRecommendationState.setTargets(targets)) {
                        // No diff, skip
                        return;
                    }
                    mApp.getModel().enqueueModelUpdateTask(
                            new WidgetsPredictionUpdateTask(mWidgetsRecommendationState, targets));
                });
        mWidgetsRecommendationState.predictor.requestPredictionUpdate();
    }

    @Override
    public void onIdpChanged(InvariantDeviceProfile profile) {
        // Reinitialize everything
        Executors.MODEL_EXECUTOR.execute(this::recreatePredictors);
    }

    private void onAppTargetEvent(AppTargetEvent event, int client) {
        PredictorState state = client == CONTAINER_PREDICTION ? mAllAppsState : mHotseatState;
        if (state.predictor != null) {
            state.predictor.notifyAppTargetEvent(event);
        }
    }

    static class PredictorState {

        public final FixedContainerItems items;
        public final PersistedItemArray<ItemInfo> storage;
        public AppPredictor predictor;

        private List<AppTarget> mLastTargets;

        PredictorState(int container, String storageName) {
            items = new FixedContainerItems(container);
            storage = new PersistedItemArray<>(storageName);
            mLastTargets = Collections.emptyList();
        }

        public void destroyPredictor() {
            if (predictor != null) {
                predictor.destroy();
                predictor = null;
            }
        }

        /**
         * Sets the new targets and returns true if it was the same as before.
         */
        boolean setTargets(List<AppTarget> newTargets) {
            List<AppTarget> oldTargets = mLastTargets;
            mLastTargets = newTargets;

            int size = oldTargets.size();
            return size == newTargets.size() && IntStream.range(0, size)
                    .allMatch(i -> areAppTargetsSame(oldTargets.get(i), newTargets.get(i)));
        }
    }

    /**
     * Compares two targets for the properties which we care about
     */
    private static boolean areAppTargetsSame(AppTarget t1, AppTarget t2) {
        if (!Objects.equals(t1.getPackageName(), t2.getPackageName())
                || !Objects.equals(t1.getUser(), t2.getUser())
                || !Objects.equals(t1.getClassName(), t2.getClassName())) {
            return false;
        }

        ShortcutInfo s1 = t1.getShortcutInfo();
        ShortcutInfo s2 = t2.getShortcutInfo();
        if (s1 != null) {
            if (s2 == null || !Objects.equals(s1.getId(), s2.getId())) {
                return false;
            }
        } else if (s2 != null) {
            return false;
        }
        return true;
    }

    private static class WorkspaceItemFactory implements PersistedItemArray.ItemFactory<ItemInfo> {

        private final LauncherAppState mAppState;
        private final UserManagerState mUMS;
        private final Map<ShortcutKey, ShortcutInfo> mPinnedShortcuts;
        private final int mMaxCount;

        private int mReadCount = 0;

        protected WorkspaceItemFactory(LauncherAppState appState, UserManagerState ums,
                Map<ShortcutKey, ShortcutInfo> pinnedShortcuts, int maxCount) {
            mAppState = appState;
            mUMS = ums;
            mPinnedShortcuts = pinnedShortcuts;
            mMaxCount = maxCount;
        }

        @Nullable
        @Override
        public ItemInfo createInfo(int itemType, UserHandle user, Intent intent) {
            if (mReadCount >= mMaxCount) {
                return null;
            }
            switch (itemType) {
                case ITEM_TYPE_APPLICATION: {
                    LauncherActivityInfo lai = mAppState.getContext()
                            .getSystemService(LauncherApps.class)
                            .resolveActivity(intent, user);
                    if (lai == null) {
                        return null;
                    }
                    AppInfo info = new AppInfo(lai, user, mUMS.isUserQuiet(user));
                    mAppState.getIconCache().getTitleAndIcon(info, lai, false);
                    mReadCount++;
                    return info.makeWorkspaceItem();
                }
                case ITEM_TYPE_DEEP_SHORTCUT: {
                    ShortcutKey key = ShortcutKey.fromIntent(intent, user);
                    if (key == null) {
                        return null;
                    }
                    ShortcutInfo si = mPinnedShortcuts.get(key);
                    if (si == null) {
                        return null;
                    }
                    WorkspaceItemInfo wii = new WorkspaceItemInfo(si, mAppState.getContext());
                    mAppState.getIconCache().getShortcutIcon(wii, si);
                    mReadCount++;
                    return wii;
                }
            }
            return null;
        }
    }
}

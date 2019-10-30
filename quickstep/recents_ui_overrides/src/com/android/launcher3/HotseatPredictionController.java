/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.prediction.AppPredictionContext;
import android.app.prediction.AppPredictionManager;
import android.app.prediction.AppPredictor;
import android.app.prediction.AppTarget;
import android.content.ComponentName;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.appprediction.ComponentKeyMapper;
import com.android.launcher3.appprediction.DynamicItemCache;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.ComponentKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides prediction ability for the hotseat. Fills gaps in hotseat with predicted items, allows
 * pinning of predicted apps and manages replacement of predicted apps with user drag.
 */
public class HotseatPredictionController implements DragController.DragListener,
        View.OnAttachStateChangeListener, SystemShortcut.Factory<QuickstepLauncher>,
        InvariantDeviceProfile.OnIDPChangeListener, AllAppsStore.OnUpdateListener,
        IconCache.ItemInfoUpdateReceiver {

    private static final String TAG = "PredictiveHotseat";
    private static final boolean DEBUG = false;

    private static final String PREDICTION_CLIENT = "hotseat";

    private boolean mDragStarted = false;
    private int mHotSeatItemsCount;

    private Launcher mLauncher;
    private Hotseat mHotseat;

    private List<ComponentKeyMapper> mComponentKeyMappers = new ArrayList<>();

    private DynamicItemCache mDynamicItemCache;

    private AppPredictor mAppPredictor;
    private AllAppsStore mAllAppsStore;

    public HotseatPredictionController(Launcher launcher) {
        mLauncher = launcher;
        mHotseat = launcher.getHotseat();
        mAllAppsStore = mLauncher.getAppsView().getAppsStore();
        mAllAppsStore.addUpdateListener(this);
        mDynamicItemCache = new DynamicItemCache(mLauncher, () -> fillGapsWithPrediction(false));
        mHotSeatItemsCount = mLauncher.getDeviceProfile().inv.numHotseatIcons;
        launcher.getDeviceProfile().inv.addOnChangeListener(this);
        mHotseat.addOnAttachStateChangeListener(this);
        createPredictor();
    }

    @Override
    public void onViewAttachedToWindow(View view) {
        mLauncher.getDragController().addDragListener(this);
    }

    @Override
    public void onViewDetachedFromWindow(View view) {
        mLauncher.getDragController().removeDragListener(this);
    }

    /**
     * Fills gaps in the hotseat with predictions
     */
    public void fillGapsWithPrediction(boolean animate) {
        if (mDragStarted) {
            return;
        }
        List<WorkspaceItemInfo> predictedApps = mapToWorkspaceItemInfo(mComponentKeyMappers);
        int predictionIndex = 0;
        ArrayList<ItemInfo> newItemsToAdd = new ArrayList<>();
        for (int rank = 0; rank < mHotSeatItemsCount; rank++) {
            View child = mHotseat.getChildAt(
                    mHotseat.getCellXFromOrder(rank),
                    mHotseat.getCellYFromOrder(rank));

            if (child != null && !isPredictedIcon(child)) {
                continue;
            }
            if (predictedApps.size() <= predictionIndex) {
                // Remove predicted apps from the past
                if (isPredictedIcon(child)) {
                    mHotseat.removeView(child);
                }
                continue;
            }

            WorkspaceItemInfo predictedItem = predictedApps.get(predictionIndex++);
            if (isPredictedIcon(child)) {
                BubbleTextView icon = (BubbleTextView) child;
                icon.applyFromWorkspaceItem(predictedItem);
            } else {
                newItemsToAdd.add(predictedItem);
            }
            preparePredictionInfo(predictedItem, rank);
        }
        mLauncher.bindItems(newItemsToAdd, animate);
        for (BubbleTextView icon : getPredictedIcons()) {
            icon.verifyHighRes();
            icon.setOnLongClickListener((v) -> {
                PopupContainerWithArrow.showForIcon((BubbleTextView) v);
                return true;
            });
        }
    }

    /**
     * Unregisters callbacks and frees resources
     */
    public void destroy() {
        mAllAppsStore.removeUpdateListener(this);
        mLauncher.getDeviceProfile().inv.removeOnChangeListener(this);
        mHotseat.removeOnAttachStateChangeListener(this);
        if (mAppPredictor != null) {
            mAppPredictor.destroy();
        }
    }

    private void createPredictor() {
        AppPredictionManager apm = mLauncher.getSystemService(AppPredictionManager.class);
        if (apm == null) {
            return;
        }
        if (mAppPredictor != null) {
            mAppPredictor.destroy();
        }
        mAppPredictor = apm.createAppPredictionSession(
                new AppPredictionContext.Builder(mLauncher)
                        .setUiSurface(PREDICTION_CLIENT)
                        .setPredictedTargetCount(mHotSeatItemsCount)
                        .build());
        mAppPredictor.registerPredictionUpdates(mLauncher.getMainExecutor(),
                this::setPredictedApps);
        mAppPredictor.requestPredictionUpdate();
    }

    private void setPredictedApps(List<AppTarget> appTargets) {
        mComponentKeyMappers.clear();
        for (AppTarget appTarget : appTargets) {
            ComponentKey key;
            if (appTarget.getShortcutInfo() != null) {
                key = ShortcutKey.fromInfo(appTarget.getShortcutInfo());
            } else {
                key = new ComponentKey(new ComponentName(appTarget.getPackageName(),
                        appTarget.getClassName()), appTarget.getUser());
            }
            mComponentKeyMappers.add(new ComponentKeyMapper(key, mDynamicItemCache));
        }
        updateDependencies();
        fillGapsWithPrediction(false);
    }

    private void updateDependencies() {
        mDynamicItemCache.updateDependencies(mComponentKeyMappers, mAllAppsStore, this,
                mHotSeatItemsCount);
    }

    private void pinPrediction(ItemInfo info) {
        BubbleTextView icon = (BubbleTextView) mHotseat.getChildAt(
                mHotseat.getCellXFromOrder(info.rank),
                mHotseat.getCellYFromOrder(info.rank));
        if (icon == null) {
            return;
        }
        WorkspaceItemInfo workspaceItemInfo = new WorkspaceItemInfo((WorkspaceItemInfo) info);
        mLauncher.getModelWriter().addItemToDatabase(workspaceItemInfo,
                LauncherSettings.Favorites.CONTAINER_HOTSEAT, workspaceItemInfo.screenId,
                workspaceItemInfo.cellX, workspaceItemInfo.cellY);
        ObjectAnimator.ofFloat(icon, SCALE_PROPERTY, 1, 0.8f, 1).start();
        icon.applyFromWorkspaceItem(workspaceItemInfo);
        icon.setOnLongClickListener(ItemLongClickListener.INSTANCE_WORKSPACE);
    }

    private List<WorkspaceItemInfo> mapToWorkspaceItemInfo(
            List<ComponentKeyMapper> components) {
        AllAppsStore allAppsStore = mLauncher.getAppsView().getAppsStore();
        if (allAppsStore.getApps().length == 0) {
            return Collections.emptyList();
        }

        List<WorkspaceItemInfo> predictedApps = new ArrayList<>();
        for (ComponentKeyMapper mapper : components) {
            ItemInfoWithIcon info = mapper.getApp(allAppsStore);
            if (info instanceof AppInfo) {
                WorkspaceItemInfo predictedApp = new WorkspaceItemInfo((AppInfo) info);
                predictedApps.add(predictedApp);
            } else if (info instanceof WorkspaceItemInfo) {
                predictedApps.add(new WorkspaceItemInfo((WorkspaceItemInfo) info));
            } else {
                if (DEBUG) {
                    Log.e(TAG, "Predicted app not found: " + mapper);
                }
            }
            // Stop at the number of hotseat items
            if (predictedApps.size() == mHotSeatItemsCount) {
                break;
            }
        }
        return predictedApps;
    }

    private List<BubbleTextView> getPredictedIcons() {
        List<BubbleTextView> icons = new ArrayList<>();
        ViewGroup vg = mHotseat.getShortcutsAndWidgets();
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            if (isPredictedIcon(child)) {
                icons.add((BubbleTextView) child);
            }
        }
        return icons;
    }

    private void removePredictedApps(boolean animate) {
        for (BubbleTextView icon : getPredictedIcons()) {
            if (animate) {
                icon.animate().scaleY(0).scaleX(0).setListener(new AnimationSuccessListener() {
                    @Override
                    public void onAnimationSuccess(Animator animator) {
                        if (icon.getParent() != null) {
                            mHotseat.removeView(icon);
                        }
                    }
                });
            } else {
                if (icon.getParent() != null) {
                    mHotseat.removeView(icon);
                }
            }
        }
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        removePredictedApps(true);
        mDragStarted = true;
    }

    @Override
    public void onDragEnd() {
        if (!mDragStarted) {
            return;
        }
        mDragStarted = false;
        fillGapsWithPrediction(true);
    }

    @Nullable
    @Override
    public SystemShortcut<QuickstepLauncher> getShortcut(QuickstepLauncher activity,
            ItemInfo itemInfo) {
        if (itemInfo.container != LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION) {
            return null;
        }
        return new PinPrediction(activity, itemInfo);
    }

    private void preparePredictionInfo(WorkspaceItemInfo itemInfo, int rank) {
        itemInfo.container = LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
        itemInfo.rank = rank;
        itemInfo.cellX = rank;
        itemInfo.cellY = mHotSeatItemsCount - rank - 1;
        itemInfo.screenId = rank;
    }

    @Override
    public void onIdpChanged(int changeFlags, InvariantDeviceProfile profile) {
        this.mHotSeatItemsCount = profile.numHotseatIcons;
        createPredictor();
    }

    @Override
    public void onAppsUpdated() {
        updateDependencies();
        fillGapsWithPrediction(false);
    }

    @Override
    public void reapplyItemInfo(ItemInfoWithIcon info) {

    }

    private class PinPrediction extends SystemShortcut<QuickstepLauncher> {

        private PinPrediction(QuickstepLauncher target, ItemInfo itemInfo) {
            super(R.drawable.ic_pin, R.string.pin_prediction, target,
                    itemInfo);
        }

        @Override
        public void onClick(View view) {
            dismissTaskMenuView(mTarget);
            pinPrediction(mItemInfo);
        }
    }

    private static boolean isPredictedIcon(View view) {
        return view instanceof BubbleTextView && view.getTag() instanceof WorkspaceItemInfo
                && ((WorkspaceItemInfo) view.getTag()).container
                == LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
    }
}

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

import android.animation.Animator;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.appprediction.ComponentKeyMapper;
import com.android.launcher3.appprediction.PredictionUiStateManager;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.uioverrides.QuickstepLauncher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides prediction ability for the hotseat. Fills gaps in hotseat with predicted items, allows
 * pinning of predicted apps and manages replacement of predicted apps with user drag.
 */
public class HotseatPredictionController implements DragController.DragListener,
        View.OnAttachStateChangeListener, SystemShortcut.Factory<QuickstepLauncher> {

    private static final String TAG = "PredictiveHotseat";
    private static final boolean DEBUG = false;


    private boolean mDragStarted = false;
    private PredictionUiStateManager mPredictionUiStateManager;
    private ArrayList<WorkspaceItemInfo> mPredictedApps = new ArrayList<>();
    private Launcher mLauncher;

    public HotseatPredictionController(Launcher launcher) {
        mLauncher = launcher;
        mPredictionUiStateManager = PredictionUiStateManager.INSTANCE.get(mLauncher);
        if (FeatureFlags.ENABLE_HYBRID_HOTSEAT.get()) {
            mLauncher.getHotseat().addOnAttachStateChangeListener(this);
        }
    }

    @Override
    public void onViewAttachedToWindow(View view) {
        mPredictionUiStateManager.setHotseatPredictionController(this);
        mLauncher.getDragController().addDragListener(this);
    }

    @Override
    public void onViewDetachedFromWindow(View view) {
        mPredictionUiStateManager.setHotseatPredictionController(null);
        mLauncher.getDragController().removeDragListener(this);
    }

    /**
     * sets the list of predicted items. gets called from PredictionUiStateManager
     */
    public void setPredictedApps(List<ComponentKeyMapper> apps) {
        mPredictedApps.clear();
        mPredictedApps.addAll(mapToWorkspaceItemInfo(apps));
        fillGapsWithPrediction(false);
    }

    /**
     * Fills gaps in the hotseat with predictions
     */
    public void fillGapsWithPrediction(boolean animate) {
        if (!FeatureFlags.ENABLE_HYBRID_HOTSEAT.get() || mDragStarted) {
            return;
        }
        removePredictedApps(false);
        int predictionIndex = 0;
        ArrayList<ItemInfo> itemInfos = new ArrayList<>();
        int cellCount = mLauncher.getWallpaperDeviceProfile().inv.numHotseatIcons;
        for (int rank = 0; rank < cellCount; rank++) {
            if (mPredictedApps.size() == predictionIndex) {
                break;
            }
            View child = mLauncher.getHotseat().getChildAt(
                    mLauncher.getHotseat().getCellXFromOrder(rank),
                    mLauncher.getHotseat().getCellYFromOrder(rank));
            if (child != null) {
                // we already have an item there. skip cell
                continue;
            }
            WorkspaceItemInfo predictedItem = mPredictedApps.get(predictionIndex++);
            preparePredictionInfo(predictedItem, rank);
            itemInfos.add(predictedItem);
        }
        mLauncher.bindItems(itemInfos, animate);
        for (BubbleTextView icon : getPredictedIcons()) {
            icon.verifyHighRes();
            icon.setOnLongClickListener((v) -> {
                PopupContainerWithArrow.showForIcon((BubbleTextView) v);
                return true;
            });
        }
    }

    private void pinPrediction(ItemInfo info) {
        BubbleTextView icon = (BubbleTextView) mLauncher.getHotseat().getChildAt(
                mLauncher.getHotseat().getCellXFromOrder(info.rank),
                mLauncher.getHotseat().getCellYFromOrder(info.rank));
        if (icon == null) {
            return;
        }
        WorkspaceItemInfo workspaceItemInfo = new WorkspaceItemInfo((WorkspaceItemInfo) info);
        workspaceItemInfo.container = LauncherSettings.Favorites.CONTAINER_HOTSEAT;
        mLauncher.getModelWriter().addItemToDatabase(workspaceItemInfo,
                workspaceItemInfo.container, workspaceItemInfo.screenId,
                workspaceItemInfo.cellX, workspaceItemInfo.cellY);
        icon.animate().scaleY(0.8f).scaleX(0.8f).setListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                icon.animate().scaleY(1).scaleX(1);
            }
        });
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
            if (predictedApps.size() == mLauncher.getDeviceProfile().inv.numHotseatIcons) {
                break;
            }
        }
        return predictedApps;
    }

    private List<BubbleTextView> getPredictedIcons() {
        List<BubbleTextView> icons = new ArrayList<>();
        ViewGroup vg = mLauncher.getHotseat().getShortcutsAndWidgets();
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            if (child instanceof BubbleTextView && child.getTag() instanceof WorkspaceItemInfo
                    && ((WorkspaceItemInfo) child.getTag()).container
                    == LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION) {
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
                            ((ViewGroup) icon.getParent()).removeView(icon);
                        }
                    }
                });
            } else {
                if (icon.getParent() != null) {
                    ((ViewGroup) icon.getParent()).removeView(icon);
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
        if (!FeatureFlags.ENABLE_HYBRID_HOTSEAT.get()) return null;
        if (itemInfo.container != LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION) {
            return null;
        }
        return new PinPrediction(activity, itemInfo);
    }

    private void preparePredictionInfo(WorkspaceItemInfo itemInfo, int rank) {
        itemInfo.container = LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
        itemInfo.rank = rank;
        itemInfo.cellX = rank;
        itemInfo.cellY =  LauncherAppState.getIDP(mLauncher).numHotseatIcons - rank - 1;
        itemInfo.screenId = rank;
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
}

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
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.prediction.AppPredictionContext;
import android.app.prediction.AppPredictionManager;
import android.app.prediction.AppPredictor;
import android.app.prediction.AppTarget;
import android.app.prediction.AppTargetEvent;
import android.app.prediction.AppTargetId;
import android.content.ComponentName;
import android.os.Bundle;
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
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.uioverrides.PredictedAppIcon;
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

    //TODO: replace this with AppTargetEvent.ACTION_UNPIN (b/144119543)
    private static final int APPTARGET_ACTION_UNPIN = 4;

    private static final String APP_LOCATION_HOTSEAT = "hotseat";
    private static final String APP_LOCATION_WORKSPACE = "workspace";

    private static final String PREDICTION_CLIENT = "hotseat";

    private DropTarget.DragObject mDragObject;
    private int mHotSeatItemsCount;

    private Launcher mLauncher;
    private Hotseat mHotseat;

    private List<ComponentKeyMapper> mComponentKeyMappers = new ArrayList<>();

    private DynamicItemCache mDynamicItemCache;

    private AppPredictor mAppPredictor;
    private AllAppsStore mAllAppsStore;

    private List<PredictedAppIcon.PredictedIconOutlineDrawing> mOutlineDrawings = new ArrayList<>();

    public HotseatPredictionController(Launcher launcher) {
        mLauncher = launcher;
        mHotseat = launcher.getHotseat();
        mAllAppsStore = mLauncher.getAppsView().getAppsStore();
        mAllAppsStore.addUpdateListener(this);
        mDynamicItemCache = new DynamicItemCache(mLauncher, this::fillGapsWithPrediction);
        mHotSeatItemsCount = mLauncher.getDeviceProfile().inv.numHotseatIcons;
        launcher.getDeviceProfile().inv.addOnChangeListener(this);
        mHotseat.addOnAttachStateChangeListener(this);
    }

    @Override
    public void onViewAttachedToWindow(View view) {
        mLauncher.getDragController().addDragListener(this);
    }

    @Override
    public void onViewDetachedFromWindow(View view) {
        mLauncher.getDragController().removeDragListener(this);
    }

    private void fillGapsWithPrediction() {
        fillGapsWithPrediction(false, null);
    }

    private void fillGapsWithPrediction(boolean animate, Runnable callback) {
        if (mDragObject != null) {
            return;
        }
        List<WorkspaceItemInfo> predictedApps = mapToWorkspaceItemInfo(mComponentKeyMappers);
        int predictionIndex = 0;
        ArrayList<WorkspaceItemInfo> newItems = new ArrayList<>();
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
                PredictedAppIcon icon = (PredictedAppIcon) child;
                icon.applyFromWorkspaceItem(predictedItem);
                icon.finishBinding();
            } else {
                newItems.add(predictedItem);
            }
            preparePredictionInfo(predictedItem, rank);
        }
        bindItems(newItems, animate, callback);
    }

    private void bindItems(List<WorkspaceItemInfo> itemsToAdd, boolean animate, Runnable callback) {
        AnimatorSet animationSet = new AnimatorSet();
        for (WorkspaceItemInfo item : itemsToAdd) {
            PredictedAppIcon icon = PredictedAppIcon.createIcon(mHotseat, item);
            mLauncher.getWorkspace().addInScreenFromBind(icon, item);
            icon.finishBinding();
            if (animate) {
                animationSet.play(ObjectAnimator.ofFloat(icon, SCALE_PROPERTY, 0.2f, 1));
            }
        }
        if (animate) {
            animationSet.addListener(new AnimationSuccessListener() {
                @Override
                public void onAnimationSuccess(Animator animator) {
                    if (callback != null) callback.run();
                }
            });
            animationSet.start();
        } else {
            if (callback != null) callback.run();
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

    /**
     * Creates App Predictor with all the current apps pinned on the hotseat
     */
    public void createPredictor() {
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
                        .setExtras(getAppPredictionContextExtra())
                        .build());
        mAppPredictor.registerPredictionUpdates(mLauncher.getMainExecutor(),
                this::setPredictedApps);

        mAppPredictor.requestPredictionUpdate();
    }

    private Bundle getAppPredictionContextExtra() {
        Bundle bundle = new Bundle();
        bundle.putParcelableArrayList(APP_LOCATION_HOTSEAT,
                getPinnedAppTargetsInViewGroup((mHotseat.getShortcutsAndWidgets())));
        bundle.putParcelableArrayList(APP_LOCATION_WORKSPACE, getPinnedAppTargetsInViewGroup(
                mLauncher.getWorkspace().getScreenWithId(
                        Workspace.FIRST_SCREEN_ID).getShortcutsAndWidgets()));
        return bundle;
    }

    private ArrayList<AppTarget> getPinnedAppTargetsInViewGroup(ViewGroup viewGroup) {
        ArrayList<AppTarget> pinnedApps = new ArrayList<>();
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (isPinnedIcon(child)) {
                WorkspaceItemInfo itemInfo = (WorkspaceItemInfo) child.getTag();
                pinnedApps.add(getAppTargetFromItemInfo(itemInfo));
            }
        }
        return pinnedApps;
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
        fillGapsWithPrediction();
    }

    private void updateDependencies() {
        mDynamicItemCache.updateDependencies(mComponentKeyMappers, mAllAppsStore, this,
                mHotSeatItemsCount);
    }

    private void pinPrediction(ItemInfo info) {
        PredictedAppIcon icon = (PredictedAppIcon) mHotseat.getChildAt(
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
        icon.pin(workspaceItemInfo);
        AppTarget appTarget = getAppTargetFromItemInfo(workspaceItemInfo);
        notifyItemAction(appTarget, APP_LOCATION_HOTSEAT, AppTargetEvent.ACTION_PIN);
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

    private List<PredictedAppIcon> getPredictedIcons() {
        List<PredictedAppIcon> icons = new ArrayList<>();
        ViewGroup vg = mHotseat.getShortcutsAndWidgets();
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            if (isPredictedIcon(child)) {
                icons.add((PredictedAppIcon) child);
            }
        }
        return icons;
    }

    private void removePredictedApps(List<PredictedAppIcon.PredictedIconOutlineDrawing> outlines) {
        for (PredictedAppIcon icon : getPredictedIcons()) {
            int rank = ((WorkspaceItemInfo) icon.getTag()).rank;
            outlines.add(new PredictedAppIcon.PredictedIconOutlineDrawing(
                    mHotseat.getCellXFromOrder(rank), mHotseat.getCellYFromOrder(rank), icon));
            icon.animate().scaleY(0).scaleX(0).setListener(new AnimationSuccessListener() {
                @Override
                public void onAnimationSuccess(Animator animator) {
                    if (icon.getParent() != null) {
                        mHotseat.removeView(icon);
                    }
                }
            });
        }
    }


    private void notifyItemAction(AppTarget target, String location, int action) {
        if (mAppPredictor != null) {
            mAppPredictor.notifyAppTargetEvent(new AppTargetEvent.Builder(target,
                    action).setLaunchLocation(location).build());
        }
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        removePredictedApps(mOutlineDrawings);
        mDragObject = dragObject;
        if (mOutlineDrawings.isEmpty()) return;
        for (PredictedAppIcon.PredictedIconOutlineDrawing outlineDrawing : mOutlineDrawings) {
            mHotseat.addDelegatedCellDrawing(outlineDrawing);
        }
        mHotseat.invalidate();
    }

    @Override
    public void onDragEnd() {
        if (mDragObject == null) {
            return;
        }
        ItemInfo dragInfo = mDragObject.dragInfo;
        if (dragInfo instanceof WorkspaceItemInfo && dragInfo.getTargetComponent() != null) {
            AppTarget appTarget = getAppTargetFromItemInfo(dragInfo);
            if (!isInHotseat(dragInfo) && isInHotseat(mDragObject.originalDragInfo)) {
                notifyItemAction(appTarget, APP_LOCATION_HOTSEAT, APPTARGET_ACTION_UNPIN);
            }
            if (!isInFirstPage(dragInfo) && isInFirstPage(mDragObject.originalDragInfo)) {
                notifyItemAction(appTarget, APP_LOCATION_WORKSPACE, APPTARGET_ACTION_UNPIN);
            }
            if (isInHotseat(dragInfo) && !isInHotseat(mDragObject.originalDragInfo)) {
                notifyItemAction(appTarget, APP_LOCATION_HOTSEAT, AppTargetEvent.ACTION_PIN);
            }
            if (isInFirstPage(dragInfo) && !isInFirstPage(mDragObject.originalDragInfo)) {
                notifyItemAction(appTarget, APP_LOCATION_WORKSPACE, AppTargetEvent.ACTION_PIN);
            }
        }
        mDragObject = null;
        fillGapsWithPrediction(true, () -> {
            if (mOutlineDrawings.isEmpty()) return;
            for (PredictedAppIcon.PredictedIconOutlineDrawing outlineDrawing : mOutlineDrawings) {
                mHotseat.removeDelegatedCellDrawing(outlineDrawing);
            }
            mHotseat.invalidate();
            mOutlineDrawings.clear();
        });
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
        fillGapsWithPrediction();
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
        return view instanceof PredictedAppIcon && view.getTag() instanceof WorkspaceItemInfo
                && ((WorkspaceItemInfo) view.getTag()).container
                == LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
    }

    private static boolean isPinnedIcon(View view) {
        if (!(view instanceof BubbleTextView && view.getTag() instanceof WorkspaceItemInfo)) {
            return false;
        }
        ItemInfo info = (ItemInfo) view.getTag();
        return info.container != LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION && (
                info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION
                        || info.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT);
    }

    private static boolean isInHotseat(ItemInfo itemInfo) {
        return itemInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT;
    }

    private static boolean isInFirstPage(ItemInfo itemInfo) {
        return itemInfo.container == LauncherSettings.Favorites.CONTAINER_DESKTOP
                && itemInfo.screenId == Workspace.FIRST_SCREEN_ID;
    }

    private static AppTarget getAppTargetFromItemInfo(ItemInfo info) {
        if (info.getTargetComponent() == null) return null;
        ComponentName cn = info.getTargetComponent();
        return new AppTarget.Builder(new AppTargetId("app:" + cn.getPackageName()),
                cn.getPackageName(), info.user).setClassName(cn.getClassName()).build();
    }
}

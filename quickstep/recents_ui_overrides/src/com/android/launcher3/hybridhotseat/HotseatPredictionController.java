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
package com.android.launcher3.hybridhotseat;

import static com.android.launcher3.InvariantDeviceProfile.CHANGE_FLAG_GRID;
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.hybridhotseat.HotseatEduController.getSettingsIntent;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOTSEAT_RANKED;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.prediction.AppPredictionContext;
import android.app.prediction.AppPredictionManager;
import android.app.prediction.AppPredictor;
import android.app.prediction.AppTarget;
import android.app.prediction.AppTargetEvent;
import android.content.ComponentName;
import android.os.Process;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.Hotseat;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.appprediction.ComponentKeyMapper;
import com.android.launcher3.appprediction.DynamicItemCache;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.logger.LauncherAtom.ContainerInfo;
import com.android.launcher3.logger.LauncherAtom.PredictedHotseatContainer;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.FolderInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.uioverrides.PredictedAppIcon;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.OnboardingPrefs;
import com.android.launcher3.views.ArrowTipView;
import com.android.launcher3.views.Snackbar;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;

/**
 * Provides prediction ability for the hotseat. Fills gaps in hotseat with predicted items, allows
 * pinning of predicted apps and manages replacement of predicted apps with user drag.
 */
public class HotseatPredictionController implements DragController.DragListener,
        View.OnAttachStateChangeListener, SystemShortcut.Factory<QuickstepLauncher>,
        InvariantDeviceProfile.OnIDPChangeListener, AllAppsStore.OnUpdateListener,
        IconCache.ItemInfoUpdateReceiver, DragSource {

    private static final String TAG = "PredictiveHotseat";
    private static final boolean DEBUG = false;

    private static final String PREDICTION_CLIENT = "hotseat";
    private DropTarget.DragObject mDragObject;
    private int mHotSeatItemsCount;
    private int mPredictedSpotsCount = 0;

    private Launcher mLauncher;
    private final Hotseat mHotseat;

    private final HotseatRestoreHelper mRestoreHelper;

    private List<ComponentKeyMapper> mComponentKeyMappers = new ArrayList<>();

    private DynamicItemCache mDynamicItemCache;

    private final HotseatPredictionModel mPredictionModel;
    private AppPredictor mAppPredictor;
    private AllAppsStore mAllAppsStore;
    private AnimatorSet mIconRemoveAnimators;
    private boolean mUIUpdatePaused = false;
    private boolean mIsDestroyed = false;


    private List<PredictedAppIcon.PredictedIconOutlineDrawing> mOutlineDrawings = new ArrayList<>();

    private final View.OnLongClickListener mPredictionLongClickListener = v -> {
        if (!ItemLongClickListener.canStartDrag(mLauncher)) return false;
        if (mLauncher.getWorkspace().isSwitchingState()) return false;
        if (!mLauncher.getOnboardingPrefs().getBoolean(
                OnboardingPrefs.HOTSEAT_LONGPRESS_TIP_SEEN)) {
            Snackbar.show(mLauncher, R.string.hotseat_tip_gaps_filled,
                    R.string.hotseat_prediction_settings, null,
                    () -> mLauncher.startActivity(getSettingsIntent()));
            mLauncher.getOnboardingPrefs().markChecked(OnboardingPrefs.HOTSEAT_LONGPRESS_TIP_SEEN);
            mLauncher.getDragLayer().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            return true;
        }
        // Start the drag
        mLauncher.getWorkspace().beginDragShared(v, this, new DragOptions());
        return true;
    };

    public HotseatPredictionController(Launcher launcher) {
        mLauncher = launcher;
        mHotseat = launcher.getHotseat();
        mAllAppsStore = mLauncher.getAppsView().getAppsStore();
        LauncherAppState appState = LauncherAppState.getInstance(launcher);
        mPredictionModel = (HotseatPredictionModel) appState.getPredictionModel();
        mAllAppsStore.addUpdateListener(this);
        mDynamicItemCache = new DynamicItemCache(mLauncher, this::fillGapsWithPrediction);
        mHotSeatItemsCount = mLauncher.getDeviceProfile().inv.numHotseatIcons;
        launcher.getDeviceProfile().inv.addOnChangeListener(this);
        mHotseat.addOnAttachStateChangeListener(this);
        mRestoreHelper = new HotseatRestoreHelper(mLauncher);
        if (mHotseat.isAttachedToWindow()) {
            onViewAttachedToWindow(mHotseat);
        }
    }

    /**
     * Shows appropriate hotseat education based on prediction enabled and migration states.
     */
    public void showEdu() {
        mLauncher.getStateManager().goToState(NORMAL, true, () -> {
            if (mComponentKeyMappers.isEmpty()) {
                // launcher has empty predictions set
                Snackbar.show(mLauncher, R.string.hotsaet_tip_prediction_disabled,
                        R.string.hotseat_prediction_settings, null,
                        () -> mLauncher.startActivity(getSettingsIntent()));
            } else if (getPredictedIcons().size() >= (mHotSeatItemsCount + 1) / 2) {
                showDiscoveryTip();
            } else {
                HotseatEduController eduController = new HotseatEduController(mLauncher,
                        mRestoreHelper,
                        this::createPredictor);
                eduController.setPredictedApps(mapToWorkspaceItemInfo(mComponentKeyMappers));
                eduController.showEdu();
            }
        });
    }

    /**
     * Shows educational tip for hotseat if user does not go through Tips app.
     */
    private void showDiscoveryTip() {
        if (getPredictedIcons().isEmpty()) {
            new ArrowTipView(mLauncher).show(
                    mLauncher.getString(R.string.hotseat_tip_no_empty_slots), mHotseat.getTop());
        } else {
            Snackbar.show(mLauncher, R.string.hotseat_tip_gaps_filled,
                    R.string.hotseat_prediction_settings, null,
                    () -> mLauncher.startActivity(getSettingsIntent()));
        }
    }

    /**
     * Returns if hotseat client has predictions
     */
    public boolean hasPredictions() {
        return !mComponentKeyMappers.isEmpty();
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
        if (mUIUpdatePaused || mDragObject != null) {
            return;
        }
        List<WorkspaceItemInfo> predictedApps = mapToWorkspaceItemInfo(mComponentKeyMappers);
        if (mComponentKeyMappers.isEmpty() != predictedApps.isEmpty()) {
            // Safely ignore update as AppsList is not ready yet. This will called again once
            // apps are ready (HotseatPredictionController#onAppsUpdated)
            return;
        }
        int predictionIndex = 0;
        ArrayList<WorkspaceItemInfo> newItems = new ArrayList<>();
        // make sure predicted icon removal and filling predictions don't step on each other
        if (mIconRemoveAnimators != null && mIconRemoveAnimators.isRunning()) {
            mIconRemoveAnimators.addListener(new AnimationSuccessListener() {
                @Override
                public void onAnimationSuccess(Animator animator) {
                    fillGapsWithPrediction(animate, callback);
                    mIconRemoveAnimators.removeListener(this);
                }
            });
            return;
        }
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
            if (isPredictedIcon(child) && child.isEnabled()) {
                PredictedAppIcon icon = (PredictedAppIcon) child;
                icon.applyFromWorkspaceItem(predictedItem);
                icon.finishBinding(mPredictionLongClickListener);
            } else {
                newItems.add(predictedItem);
            }
            preparePredictionInfo(predictedItem, rank);
        }
        mPredictedSpotsCount = predictionIndex;
        bindItems(newItems, animate, callback);
    }

    private void bindItems(List<WorkspaceItemInfo> itemsToAdd, boolean animate, Runnable callback) {
        AnimatorSet animationSet = new AnimatorSet();
        for (WorkspaceItemInfo item : itemsToAdd) {
            PredictedAppIcon icon = PredictedAppIcon.createIcon(mHotseat, item);
            mLauncher.getWorkspace().addInScreenFromBind(icon, item);
            icon.finishBinding(mPredictionLongClickListener);
            if (animate) {
                animationSet.play(ObjectAnimator.ofFloat(icon, SCALE_PROPERTY, 0.2f, 1));
            }
        }
        if (animate) {
            if (callback != null) {
                animationSet.addListener(AnimationSuccessListener.forRunnable(callback));
            }
            animationSet.start();
        } else {
            if (callback != null) callback.run();
        }
    }

    /**
     * Unregisters callbacks and frees resources
     */
    public void destroy() {
        mIsDestroyed = true;
        mAllAppsStore.removeUpdateListener(this);
        mLauncher.getDeviceProfile().inv.removeOnChangeListener(this);
        mHotseat.removeOnAttachStateChangeListener(this);
        if (mAppPredictor != null) {
            mAppPredictor.destroy();
        }
    }

    /**
     * start and pauses predicted apps update on the hotseat
     */
    public void setPauseUIUpdate(boolean paused) {
        mUIUpdatePaused = paused;
        if (!paused) {
            fillGapsWithPrediction();
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
            mAppPredictor = null;
        }
        WeakReference<HotseatPredictionController> controllerRef = new WeakReference<>(this);


        mPredictionModel.createBundle(bundle -> {
            if (mIsDestroyed) return;
            mAppPredictor = apm.createAppPredictionSession(
                    new AppPredictionContext.Builder(mLauncher)
                            .setUiSurface(PREDICTION_CLIENT)
                            .setPredictedTargetCount(mHotSeatItemsCount)
                            .setExtras(bundle)
                            .build());
            mAppPredictor.registerPredictionUpdates(
                    mLauncher.getApplicationContext().getMainExecutor(),
                    list -> {
                        if (controllerRef.get() != null) {
                            controllerRef.get().setPredictedApps(list);
                        }
                    });
            mAppPredictor.requestPredictionUpdate();
        });
        setPauseUIUpdate(false);
    }

    /**
     * Create WorkspaceItemInfo objects and binds PredictedAppIcon views for cached predicted items.
     */
    public void showCachedItems(List<AppInfo> apps, IntArray ranks) {
        if (hasPredictions() && mAppPredictor != null) {
            mAppPredictor.requestPredictionUpdate();
            fillGapsWithPrediction();
            return;
        }
        int count = Math.min(ranks.size(), apps.size());
        List<WorkspaceItemInfo> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            WorkspaceItemInfo item = new WorkspaceItemInfo(apps.get(i));
            ComponentKey componentKey = new ComponentKey(item.getTargetComponent(), item.user);
            preparePredictionInfo(item, ranks.get(i));
            items.add(item);

            mComponentKeyMappers.add(new ComponentKeyMapper(componentKey, mDynamicItemCache));
        }
        updateDependencies();
        bindItems(items, false, null);
    }

    private void setPredictedApps(List<AppTarget> appTargets) {
        mComponentKeyMappers.clear();
        if (appTargets.isEmpty()) {
            mRestoreHelper.restoreBackup();
        }
        StringBuilder predictionLog = new StringBuilder("predictedApps: [\n");
        ArrayList<ComponentKey> componentKeys = new ArrayList<>();
        for (AppTarget appTarget : appTargets) {
            ComponentKey key;
            if (appTarget.getShortcutInfo() != null) {
                key = ShortcutKey.fromInfo(appTarget.getShortcutInfo());
            } else {
                key = new ComponentKey(new ComponentName(appTarget.getPackageName(),
                        appTarget.getClassName()), appTarget.getUser());
            }
            componentKeys.add(key);
            predictionLog.append(key.toString());
            predictionLog.append(",rank:");
            predictionLog.append(appTarget.getRank());
            predictionLog.append("\n");
            mComponentKeyMappers.add(new ComponentKeyMapper(key, mDynamicItemCache));
        }
        predictionLog.append("]");
        if (Utilities.IS_DEBUG_DEVICE) {
            HotseatFileLog.INSTANCE.get(mLauncher).log(TAG, predictionLog.toString());
        }
        updateDependencies();
        fillGapsWithPrediction();
        mPredictionModel.cachePredictionComponentKeys(componentKeys);
    }

    private void updateDependencies() {
        mDynamicItemCache.updateDependencies(mComponentKeyMappers, mAllAppsStore, this,
                mHotSeatItemsCount);
    }

    /**
     * Pins a predicted app icon into place.
     */
    public void pinPrediction(ItemInfo info) {
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
        AppTarget appTarget = mPredictionModel.getAppTargetFromInfo(workspaceItemInfo);
        if (appTarget != null) {
            notifyItemAction(mPredictionModel.wrapAppTargetWithLocation(appTarget,
                    AppTargetEvent.ACTION_PIN, workspaceItemInfo));
        }
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
                predictedApp.container = LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
                predictedApps.add(predictedApp);
            } else if (info instanceof WorkspaceItemInfo) {
                WorkspaceItemInfo predictedApp = new WorkspaceItemInfo((WorkspaceItemInfo) info);
                predictedApp.container = LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
                predictedApps.add(predictedApp);
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

    private void removePredictedApps(List<PredictedAppIcon.PredictedIconOutlineDrawing> outlines,
            ItemInfo draggedInfo) {
        if (mIconRemoveAnimators != null) {
            mIconRemoveAnimators.end();
        }
        mIconRemoveAnimators = new AnimatorSet();
        removeOutlineDrawings();
        for (PredictedAppIcon icon : getPredictedIcons()) {
            if (!icon.isEnabled()) {
                continue;
            }
            if (icon.getTag().equals(draggedInfo)) {
                mHotseat.removeView(icon);
                continue;
            }
            int rank = ((WorkspaceItemInfo) icon.getTag()).rank;
            outlines.add(new PredictedAppIcon.PredictedIconOutlineDrawing(
                    mHotseat.getCellXFromOrder(rank), mHotseat.getCellYFromOrder(rank), icon));
            icon.setEnabled(false);
            ObjectAnimator animator = ObjectAnimator.ofFloat(icon, SCALE_PROPERTY, 0);
            animator.addListener(new AnimationSuccessListener() {
                @Override
                public void onAnimationSuccess(Animator animator) {
                    if (icon.getParent() != null) {
                        mHotseat.removeView(icon);
                    }
                }
            });
            mIconRemoveAnimators.play(animator);
        }
        mIconRemoveAnimators.start();
    }

    private void notifyItemAction(AppTargetEvent event) {
        if (mAppPredictor != null) {
            mAppPredictor.notifyAppTargetEvent(event);
        }
    }

    @Override
    public void onDragStart(DropTarget.DragObject dragObject, DragOptions options) {
        removePredictedApps(mOutlineDrawings, dragObject.dragInfo);
        mDragObject = dragObject;
        if (mOutlineDrawings.isEmpty()) return;
        for (PredictedAppIcon.PredictedIconOutlineDrawing outlineDrawing : mOutlineDrawings) {
            mHotseat.addDelegatedCellDrawing(outlineDrawing);
        }
        mHotseat.invalidate();
    }

    /**
     * Unpins pinned app when it's converted into a folder
     */
    public void folderCreatedFromWorkspaceItem(ItemInfo itemInfo, FolderInfo folderInfo) {
        AppTarget folderTarget = mPredictionModel.getAppTargetFromInfo(folderInfo);
        AppTarget itemTarget = mPredictionModel.getAppTargetFromInfo(itemInfo);
        if (folderTarget != null && HotseatPredictionModel.isTrackedForPrediction(folderInfo)) {
            notifyItemAction(mPredictionModel.wrapAppTargetWithLocation(folderTarget,
                    AppTargetEvent.ACTION_PIN, folderInfo));
        }
        // using folder info with isTrackedForPrediction as itemInfo.container is already changed
        // to folder by this point
        if (itemTarget != null && HotseatPredictionModel.isTrackedForPrediction(folderInfo)) {
            notifyItemAction(mPredictionModel.wrapAppTargetWithLocation(itemTarget,
                    AppTargetEvent.ACTION_UNPIN, folderInfo
            ));
        }
    }

    /**
     * Pins workspace item created when all folder items are removed but one
     */
    public void folderConvertedToWorkspaceItem(ItemInfo itemInfo, FolderInfo folderInfo) {
        AppTarget folderTarget = mPredictionModel.getAppTargetFromInfo(folderInfo);
        AppTarget itemTarget = mPredictionModel.getAppTargetFromInfo(itemInfo);
        if (folderTarget != null && HotseatPredictionModel.isTrackedForPrediction(folderInfo)) {
            notifyItemAction(mPredictionModel.wrapAppTargetWithLocation(folderTarget,
                    AppTargetEvent.ACTION_UNPIN, folderInfo));
        }
        if (itemTarget != null && HotseatPredictionModel.isTrackedForPrediction(itemInfo)) {
            notifyItemAction(mPredictionModel.wrapAppTargetWithLocation(itemTarget,
                    AppTargetEvent.ACTION_PIN, itemInfo));
        }
    }

    @Override
    public void onDragEnd() {
        if (mDragObject == null) {
            return;
        }

        ItemInfo dragInfo = mDragObject.dragInfo;
        if (mDragObject.isMoved()) {
            AppTarget appTarget = mPredictionModel.getAppTargetFromInfo(dragInfo);
            //always send pin event first to prevent AiAi from predicting an item moved within
            // the same page
            if (appTarget != null && HotseatPredictionModel.isTrackedForPrediction(dragInfo)) {
                notifyItemAction(mPredictionModel.wrapAppTargetWithLocation(appTarget,
                        AppTargetEvent.ACTION_PIN, dragInfo));
            }
            if (appTarget != null && HotseatPredictionModel.isTrackedForPrediction(
                    mDragObject.originalDragInfo)) {
                notifyItemAction(mPredictionModel.wrapAppTargetWithLocation(appTarget,
                        AppTargetEvent.ACTION_UNPIN, mDragObject.originalDragInfo));
            }
        }
        mDragObject = null;
        fillGapsWithPrediction(true, this::removeOutlineDrawings);
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
        itemInfo.cellX = mHotseat.getCellXFromOrder(rank);
        itemInfo.cellY = mHotseat.getCellYFromOrder(rank);
        itemInfo.screenId = rank;
    }

    private void removeOutlineDrawings() {
        if (mOutlineDrawings.isEmpty()) return;
        for (PredictedAppIcon.PredictedIconOutlineDrawing outlineDrawing : mOutlineDrawings) {
            mHotseat.removeDelegatedCellDrawing(outlineDrawing);
        }
        mHotseat.invalidate();
        mOutlineDrawings.clear();
    }

    @Override
    public void onIdpChanged(int changeFlags, InvariantDeviceProfile profile) {
        if ((changeFlags & CHANGE_FLAG_GRID) != 0) {
            this.mHotSeatItemsCount = profile.numHotseatIcons;
            createPredictor();
        }
    }

    @Override
    public void onAppsUpdated() {
        fillGapsWithPrediction();
    }

    @Override
    public void reapplyItemInfo(ItemInfoWithIcon info) {
    }

    @Override
    public void onDropCompleted(View target, DropTarget.DragObject d, boolean success) {
        //Does nothing
    }

    @Override
    public void fillInLogContainerData(ItemInfo childInfo, LauncherLogProto.Target child,
            ArrayList<LauncherLogProto.Target> parents) {
        mHotseat.fillInLogContainerData(childInfo, child, parents);
    }

    /**
     * Logs rank info based on current list of predicted items
     */
    public void logLaunchedAppRankingInfo(@NonNull ItemInfo itemInfo, InstanceId instanceId) {
        if (Utilities.IS_DEBUG_DEVICE) {
            final String pkg = itemInfo.getTargetComponent() != null
                    ? itemInfo.getTargetComponent().getPackageName() : "unknown";
            HotseatFileLog.INSTANCE.get(mLauncher).log("UserEvent",
                    "appLaunch: packageName:" + pkg + ",isWorkApp:" + (itemInfo.user != null
                            && !Process.myUserHandle().equals(itemInfo.user))
                            + ",launchLocation:" + itemInfo.container);
        }

        if (itemInfo.getTargetComponent() == null || itemInfo.user == null) {
            return;
        }

        final ComponentKey key = new ComponentKey(itemInfo.getTargetComponent(), itemInfo.user);

        final List<ComponentKeyMapper> predictedApps = new ArrayList<>(mComponentKeyMappers);
        OptionalInt rank = IntStream.range(0, predictedApps.size())
                .filter(index -> key.equals(predictedApps.get(index).getComponentKey()))
                .findFirst();
        if (!rank.isPresent()) {
            return;
        }

        int cardinality = 0;
        for (PredictedAppIcon icon : getPredictedIcons()) {
            ItemInfo info = (ItemInfo) icon.getTag();
            cardinality |= 1 << info.screenId;
        }

        PredictedHotseatContainer.Builder containerBuilder = PredictedHotseatContainer.newBuilder();
        containerBuilder.setCardinality(cardinality);
        if (itemInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION) {
            containerBuilder.setIndex(rank.getAsInt());
        }
        mLauncher.getStatsLogManager().logger()
                .withInstanceId(instanceId)
                .withRank(rank.getAsInt())
                .withContainerInfo(ContainerInfo.newBuilder()
                        .setPredictedHotseatContainer(containerBuilder)
                        .build())
                .log(LAUNCHER_HOTSEAT_RANKED);
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

    /**
     * Fill in predicted_rank field based on app prediction.
     * Only applicable when {@link ItemInfo#itemType} is PREDICTED_HOTSEAT
     */
    public static void encodeHotseatLayoutIntoPredictionRank(
            @NonNull ItemInfo itemInfo, @NonNull LauncherLogProto.Target target) {
        QuickstepLauncher launcher = QuickstepLauncher.ACTIVITY_TRACKER.getCreatedActivity();
        if (launcher == null || launcher.getHotseatPredictionController() == null
                || itemInfo.getTargetComponent() == null) {
            return;
        }
        HotseatPredictionController controller = launcher.getHotseatPredictionController();

        final ComponentKey k = new ComponentKey(itemInfo.getTargetComponent(), itemInfo.user);

        final List<ComponentKeyMapper> predictedApps = controller.mComponentKeyMappers;
        OptionalInt rank = IntStream.range(0, predictedApps.size())
                .filter((i) -> k.equals(predictedApps.get(i).getComponentKey()))
                .findFirst();

        target.predictedRank = 10000 + (controller.mPredictedSpotsCount * 100)
                + (rank.isPresent() ? rank.getAsInt() + 1 : 0);
    }

    private static boolean isPredictedIcon(View view) {
        return view instanceof PredictedAppIcon && view.getTag() instanceof WorkspaceItemInfo
                && ((WorkspaceItemInfo) view.getTag()).container
                == LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION;
    }
}

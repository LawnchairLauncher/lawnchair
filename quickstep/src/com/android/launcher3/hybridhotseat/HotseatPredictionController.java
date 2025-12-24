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

import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.anim.AnimatorListeners.forSuccessCallback;
import static com.android.launcher3.hybridhotseat.HotseatEduController.getSettingsIntent;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOTSEAT_PREDICTION_PINNED;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_HOTSEAT_RANKED;
import static com.android.launcher3.util.OnboardingPrefs.HOTSEAT_LONGPRESS_TIP_SEEN;

import android.animation.ObjectAnimator;
import android.content.ComponentName;
import android.view.HapticFeedbackConstants;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.Flags;
import com.android.launcher3.Hotseat;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.logger.LauncherAtom.ContainerInfo;
import com.android.launcher3.logger.LauncherAtom.PredictedHotseatContainer;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.PredictedContainerInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.HybridHotseatOrganizer;
import com.android.launcher3.views.PredictedAppIcon;
import com.android.launcher3.views.Snackbar;

import java.io.PrintWriter;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Provides prediction ability for the hotseat. Fills gaps in hotseat with predicted items, allows
 * pinning of predicted apps and manages replacement of predicted apps with user drag.
 */
public class HotseatPredictionController implements
        SystemShortcut.Factory<QuickstepLauncher> {

    private final Launcher mLauncher;
    private final HybridHotseatOrganizer mHotseatOrganizer;
    private final Hotseat mHotseat;

    private boolean mEnableHotseatLongPressTipForTesting = true;

    public HotseatPredictionController(QuickstepLauncher launcher) {
        mLauncher = launcher;
        mHotseatOrganizer = new HybridHotseatOrganizer(
                launcher,
                launcher.getWorkspace(),
                launcher.getItemInflater(),
                this::onPredictedItemLongClicked,
                mLauncher::isWorkspaceLoading);
        mHotseat = launcher.getHotseat();
        mLauncher.getDragController().addDragListener(mHotseatOrganizer);
    }

    private boolean onPredictedItemLongClicked(View v) {
        if (!ItemLongClickListener.canStartDrag(mLauncher)) return false;
        if (mLauncher.getWorkspace().isSwitchingState()) return false;

        TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "onWorkspaceItemLongClick");
        if (mEnableHotseatLongPressTipForTesting && !HOTSEAT_LONGPRESS_TIP_SEEN.get(mLauncher)) {
            Snackbar.show(mLauncher, R.string.hotseat_tip_gaps_filled,
                    R.string.hotseat_prediction_settings, null,
                    () -> mLauncher.startActivity(getSettingsIntent()));
            LauncherPrefs.get(mLauncher).put(HOTSEAT_LONGPRESS_TIP_SEEN, true);
            mLauncher.getDragLayer().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            return true;
        }

        // Start the drag
        v.setVisibility(View.INVISIBLE);
        mLauncher.getWorkspace().beginDragShared(v, mHotseatOrganizer, new DragOptions());
        return true;
    }


    /** Enables/disabled the hotseat prediction icon long press edu for testing. */
    @VisibleForTesting
    public void enableHotseatEdu(boolean enable) {
        mEnableHotseatLongPressTipForTesting = enable;
    }

    /**
     * Shows appropriate hotseat education based on prediction enabled and migration states.
     */
    public void showEdu() {
        mLauncher.getStateManager().goToState(NORMAL, true, forSuccessCallback(() -> {
            HotseatEduController eduController = new HotseatEduController(mLauncher);
            eduController.setPredictedApps(mHotseatOrganizer.getPredictedItems().stream()
                    .map(i -> (WorkspaceItemInfo) i)
                    .collect(Collectors.toList()));
            eduController.showEdu();
        }));
    }

    /**
     * Returns if hotseat client has predictions
     */
    public boolean hasPredictions() {
        return !mHotseatOrganizer.getPredictedItems().isEmpty();
    }

    /**
     * Sets or updates the predicted items
     */
    public void setPredictedItems(PredictedContainerInfo items) {
        mHotseatOrganizer.setPredictedItems(items.getContents());
        if (items.getContents().isEmpty()) {
            HotseatRestoreHelper.restoreBackup(mLauncher);
        }
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
        mLauncher.getStatsLogManager().logger()
                .withItemInfo(workspaceItemInfo)
                .log(LAUNCHER_HOTSEAT_PREDICTION_PINNED);
    }

    /**
     * Called when an item is pinned using the context menu.
     * This action directly refreshes all predicted apps without animations to ensure the
     * predictions are updated with the newly pinned item.
     */
    public void onItemPinnedFromContextMenu() {
        for (PredictedAppIcon icon : mHotseatOrganizer.getPredictedIcons()) {
            mHotseatOrganizer.removeIconWithoutNotify(icon);
        }
        mHotseatOrganizer.fillGapsWithPrediction();
    }

    @Nullable
    @Override
    public SystemShortcut<QuickstepLauncher> getShortcut(QuickstepLauncher activity,
            ItemInfo itemInfo, View originalView) {
        if (itemInfo.container != LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION) {
            return null;
        }
        if (Flags.enablePrivateSpace() && UserCache.getInstance(
                activity.getApplicationContext()).getUserInfo(itemInfo.user).isPrivate()) {
            return null;
        }
        return new PinPrediction(activity, itemInfo, originalView);
    }

    /**
     * Logs rank info based on current list of predicted items
     */
    public void logLaunchedAppRankingInfo(@NonNull ItemInfo itemInfo, InstanceId instanceId) {
        ComponentName targetCN = itemInfo.getTargetComponent();
        if (targetCN == null) {
            return;
        }
        int rank = mHotseatOrganizer.getPredictedRank(targetCN, itemInfo.user);
        if (rank < 0) {
            return;
        }

        int cardinality = 0;
        for (PredictedAppIcon icon : mHotseatOrganizer.getPredictedIcons()) {
            ItemInfo info = (ItemInfo) icon.getTag();
            cardinality |= 1 << info.screenId;
        }

        PredictedHotseatContainer.Builder containerBuilder = PredictedHotseatContainer.newBuilder();
        containerBuilder.setCardinality(cardinality);
        if (itemInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION) {
            containerBuilder.setIndex(rank);
        }
        mLauncher.getStatsLogManager().logger()
                .withInstanceId(instanceId)
                .withRank(rank)
                .withContainerInfo(ContainerInfo.newBuilder()
                        .setPredictedHotseatContainer(containerBuilder)
                        .build())
                .log(LAUNCHER_HOTSEAT_RANKED);
    }

    /**
     * Called when app/shortcut icon is removed by system. This is used to prune visible stale
     * predictions while while waiting for AppAPrediction service to send new batch of predictions.
     *
     * @param matcher filter matching items that have been removed
     */
    public void onModelItemsRemoved(Predicate<ItemInfo> matcher) {
        mHotseatOrganizer.onModelItemsRemoved(matcher);
    }

    /**
     * Called when user completes adding item requiring a config activity to the hotseat
     */
    public void onDeferredDrop(int cellX, int cellY) {
        View child = mHotseat.getChildAt(cellX, cellY);
        if (child instanceof PredictedAppIcon predictedAppIcon) {
            mHotseatOrganizer.removeIconWithoutNotify(predictedAppIcon);
        }
    }

    private class PinPrediction extends SystemShortcut<QuickstepLauncher> {

        private PinPrediction(QuickstepLauncher target, ItemInfo itemInfo, View originalView) {
            super(getDrawableId(), R.string.pin_prediction, target,
                    itemInfo, originalView);
        }

        public static int getDrawableId() {
            if (Flags.enableLauncherVisualRefresh()) {
                return R.drawable.keep_24px;
            } else {
                return R.drawable.ic_pin;
            }
        }

        @Override
        public void onClick(View view) {
            dismissTaskMenuView();
            pinPrediction(mItemInfo);
        }
    }

    public void dump(String prefix, PrintWriter writer) {
        mHotseatOrganizer.dump(prefix, writer);
    }
}

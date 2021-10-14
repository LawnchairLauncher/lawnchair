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
package com.android.launcher3.uioverrides;

import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherState.OVERVIEW_MODAL_TASK;
import static com.android.launcher3.compat.AccessibilityManagerCompat.sendCustomAccessibilityEvent;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_WIDGET_APP_START;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_APP_LAUNCH_TAP;
import static com.android.launcher3.testing.TestProtocol.HINT_STATE_ORDINAL;
import static com.android.launcher3.testing.TestProtocol.HINT_STATE_TWO_BUTTON_ORDINAL;
import static com.android.launcher3.testing.TestProtocol.OVERVIEW_STATE_ORDINAL;
import static com.android.launcher3.testing.TestProtocol.QUICK_SWITCH_STATE_ORDINAL;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_HOME_KEY;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.HapticFeedbackConstants;
import android.view.View;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.LauncherState;
import com.android.launcher3.QuickstepAccessibilityDelegate;
import com.android.launcher3.Workspace;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.appprediction.PredictionRowView;
import com.android.launcher3.hybridhotseat.HotseatPredictionController;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.StatsLogManager.StatsLogger;
import com.android.launcher3.model.BgDataModel.FixedContainerItems;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.statemanager.StateManager.AtomicAnimationFactory;
import com.android.launcher3.uioverrides.states.QuickstepAtomicAnimationFactory;
import com.android.launcher3.uioverrides.touchcontrollers.NavBarToHomeTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.NoButtonNavbarToOverviewTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.NoButtonQuickSwitchTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.PortraitStatesTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.QuickSwitchTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.StatusBarTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.TaskViewTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.TransposedQuickSwitchTouchController;
import com.android.launcher3.uioverrides.touchcontrollers.TwoButtonNavbarTouchController;
import com.android.launcher3.util.OnboardingPrefs;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.util.UiThreadHelper;
import com.android.launcher3.util.UiThreadHelper.AsyncCommand;
import com.android.launcher3.widget.LauncherAppWidgetHost;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskUtils;
import com.android.quickstep.util.QuickstepOnboardingPrefs;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class QuickstepLauncher extends BaseQuickstepLauncher {

    public static final boolean GO_LOW_RAM_RECENTS_ENABLED = false;
    /**
     * Reusable command for applying the shelf height on the background thread.
     */
    public static final AsyncCommand SET_SHELF_HEIGHT = (context, arg1, arg2) ->
            SystemUiProxy.INSTANCE.get(context).setShelfHeight(arg1 != 0, arg2);

    private FixedContainerItems mAllAppsPredictions;
    private HotseatPredictionController mHotseatPredictionController;

    @Override
    protected void setupViews() {
        super.setupViews();
        mHotseatPredictionController = new HotseatPredictionController(this);
    }

    @Override
    protected void logAppLaunch(ItemInfo info, InstanceId instanceId) {
        // If the app launch is from any of the surfaces in AllApps then add the InstanceId from
        // LiveSearchManager to recreate the AllApps session on the server side.
        if (mAllAppsSessionLogId != null && ALL_APPS.equals(
                getStateManager().getCurrentStableState())) {
            instanceId = mAllAppsSessionLogId;
        }

        StatsLogger logger = getStatsLogManager()
                .logger().withItemInfo(info).withInstanceId(instanceId);

        if (mAllAppsPredictions != null
                && (info.itemType == ITEM_TYPE_APPLICATION
                || info.itemType == ITEM_TYPE_SHORTCUT
                || info.itemType == ITEM_TYPE_DEEP_SHORTCUT)) {
            int count = mAllAppsPredictions.items.size();
            for (int i = 0; i < count; i++) {
                ItemInfo targetInfo = mAllAppsPredictions.items.get(i);
                if (targetInfo.itemType == info.itemType
                        && targetInfo.user.equals(info.user)
                        && Objects.equals(targetInfo.getIntent(), info.getIntent())) {
                    logger.withRank(i);
                    break;
                }

            }
        }
        logger.log(LAUNCHER_APP_LAUNCH_TAP);

        mHotseatPredictionController.logLaunchedAppRankingInfo(info, instanceId);
    }

    @Override
    protected LauncherAccessibilityDelegate createAccessibilityDelegate() {
        return new QuickstepAccessibilityDelegate(this);
    }

    /**
     * Returns Prediction controller for hybrid hotseat
     */
    public HotseatPredictionController getHotseatPredictionController() {
        return mHotseatPredictionController;
    }

    @Override
    protected OnboardingPrefs createOnboardingPrefs(SharedPreferences sharedPrefs) {
        return new QuickstepOnboardingPrefs(this, sharedPrefs);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        onStateOrResumeChanging(false /* inTransition */);
    }

    @Override
    public boolean startActivitySafely(View v, Intent intent, ItemInfo item) {
        // Only pause is taskbar controller is not present
        mHotseatPredictionController.setPauseUIUpdate(getTaskbarUIController() == null);
        return super.startActivitySafely(v, intent, item);
    }

    @Override
    protected void onActivityFlagsChanged(int changeBits) {
        super.onActivityFlagsChanged(changeBits);
        if ((changeBits & (ACTIVITY_STATE_DEFERRED_RESUMED | ACTIVITY_STATE_STARTED
                | ACTIVITY_STATE_USER_ACTIVE | ACTIVITY_STATE_TRANSITION_ACTIVE)) != 0) {
            onStateOrResumeChanging((getActivityFlags() & ACTIVITY_STATE_TRANSITION_ACTIVE) == 0);
        }

        if (((changeBits & ACTIVITY_STATE_STARTED) != 0
                || (changeBits & getActivityFlags() & ACTIVITY_STATE_DEFERRED_RESUMED) != 0)) {
            mHotseatPredictionController.setPauseUIUpdate(false);
        }
    }

    @Override
    protected void showAllAppsFromIntent(boolean alreadyOnHome) {
        TaskUtils.closeSystemWindowsAsync(CLOSE_SYSTEM_WINDOWS_REASON_HOME_KEY);
        super.showAllAppsFromIntent(alreadyOnHome);
    }

    @Override
    public Stream<SystemShortcut.Factory> getSupportedShortcuts() {
        return Stream.concat(
                Stream.of(mHotseatPredictionController), super.getSupportedShortcuts());
    }

    /**
     * Recents logic that triggers when launcher state changes or launcher activity stops/resumes.
     */
    private void onStateOrResumeChanging(boolean inTransition) {
        LauncherState state = getStateManager().getState();
        boolean started = ((getActivityFlags() & ACTIVITY_STATE_STARTED)) != 0;
        if (started) {
            DeviceProfile profile = getDeviceProfile();
            boolean willUserBeActive =
                    (getActivityFlags() & ACTIVITY_STATE_USER_WILL_BE_ACTIVE) != 0;
            boolean visible = (state == NORMAL || state == OVERVIEW)
                    && (willUserBeActive || isUserActive())
                    && !profile.isVerticalBarLayout()
                    && profile.isPhone && !profile.isLandscape;
            UiThreadHelper.runAsyncCommand(this, SET_SHELF_HEIGHT, visible ? 1 : 0,
                    profile.hotseatBarSizePx);
        }
        if (state == NORMAL && !inTransition) {
            ((RecentsView) getOverviewPanel()).setSwipeDownShouldLaunchApp(false);
        }
    }

    @Override
    public void bindExtraContainerItems(FixedContainerItems item) {
        if (item.containerId == Favorites.CONTAINER_PREDICTION) {
            mAllAppsPredictions = item;
            getAppsView().getFloatingHeaderView().findFixedRowByType(PredictionRowView.class)
                    .setPredictedApps(item.items);
        } else if (item.containerId == Favorites.CONTAINER_HOTSEAT_PREDICTION) {
            mHotseatPredictionController.setPredictedItems(item);
        } else if (item.containerId == Favorites.CONTAINER_WIDGETS_PREDICTION) {
            getPopupDataProvider().setRecommendedWidgets(item.items);
        }
    }

    @Override
    public void bindWorkspaceItemsChanged(List<WorkspaceItemInfo> updated) {
        super.bindWorkspaceItemsChanged(updated);
        if (getTaskbarUIController() != null && updated.stream()
                .filter(w -> w.container == CONTAINER_HOTSEAT).findFirst().isPresent()) {
            getTaskbarUIController().onHotseatUpdated();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mHotseatPredictionController.destroy();
    }

    @Override
    public void onStateSetEnd(LauncherState state) {
        super.onStateSetEnd(state);

        switch (state.ordinal) {
            case HINT_STATE_ORDINAL: {
                Workspace workspace = getWorkspace();
                getStateManager().goToState(NORMAL);
                if (workspace.getNextPage() != Workspace.DEFAULT_PAGE) {
                    workspace.post(workspace::moveToDefaultScreen);
                }
                break;
            }
            case HINT_STATE_TWO_BUTTON_ORDINAL: {
                getStateManager().goToState(OVERVIEW);
                getDragLayer().performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                break;
            }
            case OVERVIEW_STATE_ORDINAL: {
                RecentsView rv = getOverviewPanel();
                sendCustomAccessibilityEvent(
                        rv.getPageAt(rv.getCurrentPage()), TYPE_VIEW_FOCUSED, null);
                break;
            }
            case QUICK_SWITCH_STATE_ORDINAL: {
                RecentsView rv = getOverviewPanel();
                TaskView tasktolaunch = rv.getTaskViewAt(0);
                if (tasktolaunch != null) {
                    tasktolaunch.launchTask(success -> {
                        if (!success) {
                            getStateManager().goToState(OVERVIEW);
                        } else {
                            getStateManager().moveToRestState();
                        }
                    });
                } else {
                    getStateManager().goToState(NORMAL);
                }
                break;
            }

        }
    }

    @Override
    public TouchController[] createTouchControllers() {
        Mode mode = SysUINavigationMode.getMode(this);

        ArrayList<TouchController> list = new ArrayList<>();
        list.add(getDragController());
        switch (mode) {
            case NO_BUTTON:
                list.add(new NoButtonQuickSwitchTouchController(this));
                list.add(new NavBarToHomeTouchController(this));
                list.add(new NoButtonNavbarToOverviewTouchController(this));
                break;
            case TWO_BUTTONS:
                list.add(new TwoButtonNavbarTouchController(this));
                list.add(getDeviceProfile().isVerticalBarLayout()
                        ? new TransposedQuickSwitchTouchController(this)
                        : new QuickSwitchTouchController(this));
                list.add(new PortraitStatesTouchController(this));
                break;
            case THREE_BUTTONS:
            default:
                list.add(new PortraitStatesTouchController(this));
        }

        if (!getDeviceProfile().isMultiWindowMode) {
            list.add(new StatusBarTouchController(this));
        }

        list.add(new LauncherTaskViewController(this));
        return list.toArray(new TouchController[list.size()]);
    }

    @Override
    public AtomicAnimationFactory createAtomicAnimationFactory() {
        return new QuickstepAtomicAnimationFactory(this);
    }

    protected LauncherAppWidgetHost createAppWidgetHost() {
        LauncherAppWidgetHost appWidgetHost = super.createAppWidgetHost();
        if (ENABLE_QUICKSTEP_WIDGET_APP_START.get()) {
            appWidgetHost.setInteractionHandler(new QuickstepInteractionHandler(this));
        }
        return appWidgetHost;
    }

    private static final class LauncherTaskViewController extends
            TaskViewTouchController<Launcher> {

        LauncherTaskViewController(Launcher activity) {
            super(activity);
        }

        @Override
        protected boolean isRecentsInteractive() {
            return mActivity.isInState(OVERVIEW) || mActivity.isInState(OVERVIEW_MODAL_TASK);
        }

        @Override
        protected boolean isRecentsModal() {
            return mActivity.isInState(OVERVIEW_MODAL_TASK);
        }

        @Override
        protected void onUserControlledAnimationCreated(AnimatorPlaybackController animController) {
            mActivity.getStateManager().setCurrentUserControlledAnimation(animController);
        }
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        RecentsView recentsView = getOverviewPanel();
        writer.println("\nQuickstepLauncher:");
        writer.println(prefix + "\tmOrientationState: " + (recentsView == null ? "recentsNull" :
                recentsView.getPagedViewOrientedState()));
    }
}

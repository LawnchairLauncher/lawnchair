/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static com.android.launcher3.LauncherState.NO_OFFSET;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherState.OVERVIEW_MODAL_TASK;
import static com.android.launcher3.LauncherState.OVERVIEW_SPLIT_SELECT;
import static com.android.launcher3.anim.Interpolators.EMPHASIZED;
import static com.android.launcher3.compat.AccessibilityManagerCompat.sendCustomAccessibilityEvent;
import static com.android.launcher3.config.FeatureFlags.ENABLE_SPLIT_FROM_WORKSPACE;
import static com.android.launcher3.config.FeatureFlags.ENABLE_WIDGET_PICKER_DEPTH;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_APP_LAUNCH_TAP;
import static com.android.launcher3.model.data.ItemInfo.NO_MATCHING_ID;
import static com.android.launcher3.popup.QuickstepSystemShortcut.getSplitSelectShortcutByPosition;
import static com.android.launcher3.taskbar.LauncherTaskbarUIController.ALL_APPS_PAGE_PROGRESS_INDEX;
import static com.android.launcher3.taskbar.LauncherTaskbarUIController.MINUS_ONE_PAGE_PROGRESS_INDEX;
import static com.android.launcher3.taskbar.LauncherTaskbarUIController.WIDGETS_PAGE_PROGRESS_INDEX;
import static com.android.launcher3.testing.shared.TestProtocol.HINT_STATE_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.HINT_STATE_TWO_BUTTON_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.OVERVIEW_STATE_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.QUICK_SWITCH_STATE_ORDINAL;
import static com.android.launcher3.util.DisplayController.CHANGE_ACTIVE_SCREEN;
import static com.android.launcher3.util.DisplayController.CHANGE_NAVIGATION_MODE;
import static com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_HOME_KEY;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.hardware.SensorManager;
import android.hardware.devicestate.DeviceStateManager;
import android.media.permission.SafeCloseable;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.os.SystemProperties;
import android.util.Log;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.RemoteAnimationTarget;
import android.view.View;
import android.view.WindowManagerGlobal;
import android.window.SplashScreen;

import androidx.annotation.BinderThread;
import androidx.annotation.Nullable;

import com.android.app.viewcapture.ViewCapture;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.LauncherState;
import com.android.launcher3.QuickstepAccessibilityDelegate;
import com.android.launcher3.QuickstepTransitionManager;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.appprediction.PredictionRowView;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.DragOptions;
import com.android.launcher3.hybridhotseat.HotseatPredictionController;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.logging.StatsLogManager.StatsLogger;
import com.android.launcher3.model.BgDataModel.FixedContainerItems;
import com.android.launcher3.model.WellbeingModel;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.proxy.ProxyActivityStarter;
import com.android.launcher3.proxy.StartActivityParams;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.statemanager.StateManager.AtomicAnimationFactory;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.taskbar.LauncherTaskbarUIController;
import com.android.launcher3.taskbar.TaskbarManager;
import com.android.launcher3.uioverrides.QuickstepWidgetHolder.QuickstepHolderFactory;
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
import com.android.launcher3.util.ActivityOptionsWrapper;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.IntSet;
import com.android.launcher3.util.NavigationMode;
import com.android.launcher3.util.ObjectWrapper;
import com.android.launcher3.util.PendingRequestArgs;
import com.android.launcher3.util.PendingSplitSelectInfo;
import com.android.launcher3.util.RunnableList;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.widget.LauncherWidgetHolder;
import com.android.quickstep.OverviewCommandHelper;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskUtils;
import com.android.quickstep.TouchInteractionService.TISBinder;
import com.android.quickstep.util.LauncherUnfoldAnimationController;
import com.android.quickstep.util.ProxyScreenStatusProvider;
import com.android.quickstep.util.QuickstepOnboardingPrefs;
import com.android.quickstep.util.RemoteAnimationProvider;
import com.android.quickstep.util.RemoteFadeOutAnimationListener;
import com.android.quickstep.util.SplitSelectStateController;
import com.android.quickstep.util.SplitToWorkspaceController;
import com.android.quickstep.util.SplitWithKeyboardShortcutController;
import com.android.quickstep.util.TISBindHelper;
import com.android.quickstep.views.DesktopTaskView;
import com.android.quickstep.views.OverviewActionsView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.unfold.UnfoldSharedComponent;
import com.android.systemui.unfold.UnfoldTransitionFactory;
import com.android.systemui.unfold.UnfoldTransitionProgressProvider;
import com.android.systemui.unfold.config.ResourceUnfoldTransitionConfig;
import com.android.systemui.unfold.config.UnfoldTransitionConfig;
import com.android.systemui.unfold.system.ActivityManagerActivityTypeProvider;
import com.android.systemui.unfold.system.DeviceStateManagerFoldProvider;
import com.android.systemui.unfold.updates.RotationChangeProvider;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class QuickstepLauncher extends Launcher {

    public static final boolean ENABLE_PIP_KEEP_CLEAR_ALGORITHM =
            SystemProperties.getBoolean("persist.wm.debug.enable_pip_keep_clear_algorithm", false);

    public static final boolean GO_LOW_RAM_RECENTS_ENABLED = false;

    private FixedContainerItems mAllAppsPredictions;
    private HotseatPredictionController mHotseatPredictionController;
    private DepthController mDepthController;
    private DesktopVisibilityController mDesktopVisibilityController;
    private QuickstepTransitionManager mAppTransitionManager;
    private OverviewActionsView mActionsView;
    private TISBindHelper mTISBindHelper;
    private @Nullable TaskbarManager mTaskbarManager;
    private @Nullable OverviewCommandHelper mOverviewCommandHelper;
    private @Nullable LauncherTaskbarUIController mTaskbarUIController;
    // Will be updated when dragging from taskbar.
    private @Nullable DragOptions mNextWorkspaceDragOptions = null;
    private @Nullable UnfoldTransitionProgressProvider mUnfoldTransitionProgressProvider;
    private @Nullable RotationChangeProvider mRotationChangeProvider;
    private @Nullable LauncherUnfoldAnimationController mLauncherUnfoldAnimationController;

    private SplitSelectStateController mSplitSelectStateController;
    private SplitWithKeyboardShortcutController mSplitWithKeyboardShortcutController;
    private SplitToWorkspaceController mSplitToWorkspaceController;

    /**
     * If Launcher restarted while in the middle of an Overview split select, it needs this data to
     * recover. In all other cases this will remain null.
     */
    private PendingSplitSelectInfo mPendingSplitSelectInfo = null;

    private SafeCloseable mViewCapture;

    private boolean mEnableWidgetDepth;

    @Override
    protected void setupViews() {
        super.setupViews();

        mActionsView = findViewById(R.id.overview_actions_view);
        RecentsView overviewPanel = getOverviewPanel();
        mSplitSelectStateController =
                new SplitSelectStateController(this, mHandler, getStateManager(),
                        getDepthController(), getStatsLogManager());
        overviewPanel.init(mActionsView, mSplitSelectStateController);
        mSplitWithKeyboardShortcutController = new SplitWithKeyboardShortcutController(this,
                mSplitSelectStateController);
        mSplitToWorkspaceController = new SplitToWorkspaceController(this,
                mSplitSelectStateController);
        mActionsView.updateDimension(getDeviceProfile(), overviewPanel.getLastComputedTaskSize());
        mActionsView.updateVerticalMargin(DisplayController.getNavigationMode(this));

        mAppTransitionManager = new QuickstepTransitionManager(this);
        mAppTransitionManager.registerRemoteAnimations();
        mAppTransitionManager.registerRemoteTransitions();

        mTISBindHelper = new TISBindHelper(this, this::onTISConnected);
        mDepthController = new DepthController(this);
        mDesktopVisibilityController = new DesktopVisibilityController(this);
        mHotseatPredictionController = new HotseatPredictionController(this);

        mEnableWidgetDepth = ENABLE_WIDGET_PICKER_DEPTH.get()
                && SystemProperties.getBoolean("ro.launcher.depth.widget", true);
        getWorkspace().addOverlayCallback(progress ->
                onTaskbarInAppDisplayProgressUpdate(progress, MINUS_ONE_PAGE_PROGRESS_INDEX));
    }

    @Override
    public void logAppLaunch(StatsLogManager statsLogManager, ItemInfo info,
            InstanceId instanceId) {
        // If the app launch is from any of the surfaces in AllApps then add the InstanceId from
        // LiveSearchManager to recreate the AllApps session on the server side.
        if (mAllAppsSessionLogId != null && ALL_APPS.equals(
                getStateManager().getCurrentStableState())) {
            instanceId = mAllAppsSessionLogId;
        }

        StatsLogger logger = statsLogManager.logger().withItemInfo(info).withInstanceId(instanceId);

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
    protected void completeAddShortcut(Intent data, int container, int screenId, int cellX,
            int cellY, PendingRequestArgs args) {
        if (container == CONTAINER_HOTSEAT) {
            mHotseatPredictionController.onDeferredDrop(cellX, cellY);
        }
        super.completeAddShortcut(data, container, screenId, cellX, cellY, args);
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
    protected QuickstepOnboardingPrefs createOnboardingPrefs(SharedPreferences sharedPrefs) {
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
        boolean started = super.startActivitySafely(v, intent, item);
        if (getTaskbarUIController() == null && !started) {
            mHotseatPredictionController.setPauseUIUpdate(false);
        }
        return started;
    }

    @Override
    protected void onActivityFlagsChanged(int changeBits) {
        if ((changeBits & ACTIVITY_STATE_STARTED) != 0) {
            mDepthController.setActivityStarted(isStarted());
        }

        if ((changeBits & ACTIVITY_STATE_RESUMED) != 0) {
            if (mTaskbarUIController != null) {
                mTaskbarUIController.onLauncherResumedOrPaused(hasBeenResumed());
            }
        }

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

    protected void onItemClicked(View view) {
        if (!mSplitToWorkspaceController.handleSecondAppSelectionForSplit(view)) {
            QuickstepLauncher.super.getItemOnClickListener().onClick(view);
        }
    }

    @Override
    public View.OnClickListener getItemOnClickListener() {
        return this::onItemClicked;
    }

    @Override
    public Stream<SystemShortcut.Factory> getSupportedShortcuts() {
        Stream<SystemShortcut.Factory> base = Stream.of(WellbeingModel.SHORTCUT_FACTORY);
        if (ENABLE_SPLIT_FROM_WORKSPACE.get() && mDeviceProfile.isTablet) {
            RecentsView recentsView = getOverviewPanel();
            // TODO: Pull it out of PagedOrentationHandler for split from workspace.
            List<SplitPositionOption> positions =
                    recentsView.getPagedOrientationHandler().getSplitPositionOptions(
                            mDeviceProfile);
            List<SystemShortcut.Factory<QuickstepLauncher>> splitShortcuts = new ArrayList<>();
            for (SplitPositionOption position : positions) {
                splitShortcuts.add(getSplitSelectShortcutByPosition(position));
            }
            base = Stream.concat(base, splitShortcuts.stream());
        }
        return Stream.concat(
                Stream.of(mHotseatPredictionController),
                Stream.concat(base, super.getSupportedShortcuts()));
    }

    /**
     * Recents logic that triggers when launcher state changes or launcher activity stops/resumes.
     */
    private void onStateOrResumeChanging(boolean inTransition) {
        LauncherState state = getStateManager().getState();
        if (!ENABLE_PIP_KEEP_CLEAR_ALGORITHM) {
            boolean started = ((getActivityFlags() & ACTIVITY_STATE_STARTED)) != 0;
            if (started) {
                DeviceProfile profile = getDeviceProfile();
                boolean willUserBeActive =
                        (getActivityFlags() & ACTIVITY_STATE_USER_WILL_BE_ACTIVE) != 0;
                boolean visible = (state == NORMAL || state == OVERVIEW)
                        && (willUserBeActive || isUserActive())
                        && !profile.isVerticalBarLayout();
                SystemUiProxy.INSTANCE.get(this).setShelfHeight(visible, profile.hotseatBarSizePx);
            }
        }
        if (state == NORMAL && !inTransition) {
            ((RecentsView) getOverviewPanel()).setSwipeDownShouldLaunchApp(false);
        }
    }

    @Override
    public void bindExtraContainerItems(FixedContainerItems item) {
        if (item.containerId == Favorites.CONTAINER_PREDICTION) {
            mAllAppsPredictions = item;
            PredictionRowView<?> predictionRowView =
                    getAppsView().getFloatingHeaderView().findFixedRowByType(
                            PredictionRowView.class);
            predictionRowView.setPredictedApps(item.items);
        } else if (item.containerId == Favorites.CONTAINER_HOTSEAT_PREDICTION) {
            mHotseatPredictionController.setPredictedItems(item);
        } else if (item.containerId == Favorites.CONTAINER_WIDGETS_PREDICTION) {
            getPopupDataProvider().setRecommendedWidgets(item.items);
        }
    }

    @Override
    public void bindWorkspaceComponentsRemoved(Predicate<ItemInfo> matcher) {
        super.bindWorkspaceComponentsRemoved(matcher);
        mHotseatPredictionController.onModelItemsRemoved(matcher);
    }

    @Override
    public void onDestroy() {
        mAppTransitionManager.onActivityDestroyed();
        if (mUnfoldTransitionProgressProvider != null) {
            mUnfoldTransitionProgressProvider.destroy();
        }

        mTISBindHelper.onDestroy();
        if (mTaskbarManager != null) {
            mTaskbarManager.clearActivity(this);
        }

        if (mLauncherUnfoldAnimationController != null) {
            mLauncherUnfoldAnimationController.onDestroy();
        }

        super.onDestroy();
        mHotseatPredictionController.destroy();
        mSplitWithKeyboardShortcutController.onDestroy();
        if (mViewCapture != null) mViewCapture.close();
    }

    @Override
    public void onStateSetEnd(LauncherState state) {
        super.onStateSetEnd(state);
        handlePendingActivityRequest();

        switch (state.ordinal) {
            case HINT_STATE_ORDINAL: {
                Workspace<?> workspace = getWorkspace();
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
        NavigationMode mode = DisplayController.getNavigationMode(this);

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

    @Override
    protected LauncherWidgetHolder createAppWidgetHolder() {
        final QuickstepHolderFactory factory =
                (QuickstepHolderFactory) LauncherWidgetHolder.HolderFactory.newFactory(this);
        return factory.newInstance(this,
                appWidgetId -> getWorkspace().removeWidget(appWidgetId),
                new QuickstepInteractionHandler(this));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mPendingSplitSelectInfo = ObjectWrapper.unwrap(
                    savedInstanceState.getIBinder(PENDING_SPLIT_SELECT_INFO));
        }
        addMultiWindowModeChangedListener(mDepthController);
        initUnfoldTransitionProgressProvider();
        if (FeatureFlags.CONTINUOUS_VIEW_TREE_CAPTURE.get()) {
            mViewCapture = ViewCapture.getInstance().startCapture(getWindow());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mLauncherUnfoldAnimationController != null) {
            mLauncherUnfoldAnimationController.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (mLauncherUnfoldAnimationController != null) {
            mLauncherUnfoldAnimationController.onPause();
        }

        super.onPause();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (mOverviewCommandHelper != null) {
            mOverviewCommandHelper.clearPendingCommands();
        }
    }

    public QuickstepTransitionManager getAppTransitionManager() {
        return mAppTransitionManager;
    }

    @Override
    public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();
        // After the transition to home, enable the high-res thumbnail loader if it wasn't enabled
        // as a part of quickstep, so that high-res thumbnails can load the next time we enter
        // overview
        RecentsModel.INSTANCE.get(this).getThumbnailCache()
                .getHighResLoadingState().setVisible(true);
    }

    @Override
    protected void handleGestureContract(Intent intent) {
        if (FeatureFlags.SEPARATE_RECENTS_ACTIVITY.get()) {
            super.handleGestureContract(intent);
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        RecentsModel.INSTANCE.get(this).onTrimMemory(level);
    }

    @Override
    public void onUiChangedWhileSleeping() {
        // Remove the snapshot because the content view may have obvious changes.
        UI_HELPER_EXECUTOR.execute(
                () -> ActivityManagerWrapper.getInstance().invalidateHomeTaskSnapshot(this));
    }

    @Override
    protected void onScreenOff() {
        super.onScreenOff();
        RecentsView recentsView = getOverviewPanel();
        recentsView.finishRecentsAnimation(true /* toRecents */, null);
    }

    @Override
    public void onAllAppsTransition(float progress) {
        super.onAllAppsTransition(progress);
        onTaskbarInAppDisplayProgressUpdate(progress, ALL_APPS_PAGE_PROGRESS_INDEX);
    }

    @Override
    public void onWidgetsTransition(float progress) {
        super.onWidgetsTransition(progress);
        onTaskbarInAppDisplayProgressUpdate(progress, WIDGETS_PAGE_PROGRESS_INDEX);
        if (mEnableWidgetDepth) {
            getDepthController().widgetDepth.setValue(Utilities.mapToRange(
                    progress, 0f, 1f, 0f, getDeviceProfile().bottomSheetDepth, EMPHASIZED));
        }
    }

    private void onTaskbarInAppDisplayProgressUpdate(float progress, int flag) {
        if (mTaskbarManager == null
                || mTaskbarManager.getCurrentActivityContext() == null
                || mTaskbarUIController == null) {
            return;
        }
        mTaskbarUIController.onTaskbarInAppDisplayProgressUpdate(progress, flag);
    }

    @Override
    public void startIntentSenderForResult(IntentSender intent, int requestCode,
            Intent fillInIntent, int flagsMask, int flagsValues, int extraFlags, Bundle options) {
        if (requestCode != -1) {
            mPendingActivityRequestCode = requestCode;
            StartActivityParams params = new StartActivityParams(this, requestCode);
            params.intentSender = intent;
            params.fillInIntent = fillInIntent;
            params.flagsMask = flagsMask;
            params.flagsValues = flagsValues;
            params.extraFlags = extraFlags;
            params.options = options;
            startActivity(ProxyActivityStarter.getLaunchIntent(this, params));
        } else {
            super.startIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask,
                    flagsValues, extraFlags, options);
        }
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        if (requestCode != -1) {
            mPendingActivityRequestCode = requestCode;
            StartActivityParams params = new StartActivityParams(this, requestCode);
            params.intent = intent;
            params.options = options;
            startActivity(ProxyActivityStarter.getLaunchIntent(this, params));
        } else {
            super.startActivityForResult(intent, requestCode, options);
        }
    }

    @Override
    public void setResumed() {
        if (DesktopTaskView.DESKTOP_IS_PROTO2_ENABLED) {
            DesktopVisibilityController controller = mDesktopVisibilityController;
            if (controller != null && controller.areFreeformTasksVisible()) {
                // Return early to skip setting activity to appear as resumed
                // TODO(b/255649902): shouldn't be needed when we have a separate launcher state
                //  for desktop that we can use to control other parts of launcher
                return;
            }
        }
        super.setResumed();
    }

    @Override
    protected void onDeferredResumed() {
        super.onDeferredResumed();
        handlePendingActivityRequest();
    }

    private void handlePendingActivityRequest() {
        if (mPendingActivityRequestCode != -1 && isInState(NORMAL)
                && ((getActivityFlags() & ACTIVITY_STATE_DEFERRED_RESUMED) != 0)) {
            // Remove any active ProxyActivityStarter task and send RESULT_CANCELED to Launcher.
            onActivityResult(mPendingActivityRequestCode, RESULT_CANCELED, null);
            // ProxyActivityStarter is started with clear task to reset the task after which it
            // removes the task itself.
            startActivity(ProxyActivityStarter.getLaunchIntent(this, null));
        }
    }

    private void onTISConnected(TISBinder binder) {
        mTaskbarManager = binder.getTaskbarManager();
        mTaskbarManager.setActivity(this);
        mOverviewCommandHelper = binder.getOverviewCommandHelper();
    }

    @Override
    public void runOnBindToTouchInteractionService(Runnable r) {
        mTISBindHelper.runOnBindToTouchInteractionService(r);
    }

    private void initUnfoldTransitionProgressProvider() {
        final UnfoldTransitionConfig config = new ResourceUnfoldTransitionConfig();
        if (config.isEnabled()) {
            UnfoldSharedComponent unfoldComponent =
                    UnfoldTransitionFactory.createUnfoldSharedComponent(
                            /* context= */ this,
                            config,
                            ProxyScreenStatusProvider.INSTANCE,
                            new DeviceStateManagerFoldProvider(
                                    getSystemService(DeviceStateManager.class), /* context */this),
                            new ActivityManagerActivityTypeProvider(
                                    getSystemService(ActivityManager.class)),
                            getSystemService(SensorManager.class),
                            getMainThreadHandler(),
                            getMainExecutor(),
                            /* backgroundExecutor= */ THREAD_POOL_EXECUTOR,
                            /* tracingTagPrefix= */ "launcher",
                            WindowManagerGlobal.getWindowManagerService()
                    );

            mUnfoldTransitionProgressProvider = unfoldComponent.getUnfoldTransitionProvider()
                    .orElseThrow(() -> new IllegalStateException(
                            "Trying to create UnfoldTransitionProgressProvider when the "
                                    + "transition is disabled"));

            mRotationChangeProvider = unfoldComponent.getRotationChangeProvider();
            mLauncherUnfoldAnimationController = new LauncherUnfoldAnimationController(
                    /* launcher= */ this,
                    getWindowManager(),
                    mUnfoldTransitionProgressProvider,
                    mRotationChangeProvider
            );
            Log.d("b/261320823", "initUnfoldTransitionProgressProvider completed");
        }
    }

    public void setTaskbarUIController(LauncherTaskbarUIController taskbarUIController) {
        mTaskbarUIController = taskbarUIController;
    }

    public @Nullable LauncherTaskbarUIController getTaskbarUIController() {
        return mTaskbarUIController;
    }

    public SplitSelectStateController getSplitSelectStateController() {
        return mSplitSelectStateController;
    }

    public <T extends OverviewActionsView> T getActionsView() {
        return (T) mActionsView;
    }

    @Override
    protected void closeOpenViews(boolean animate) {
        super.closeOpenViews(animate);
        TaskUtils.closeSystemWindowsAsync(CLOSE_SYSTEM_WINDOWS_REASON_HOME_KEY);
    }

    @Override
    protected void collectStateHandlers(List<StateHandler> out) {
        super.collectStateHandlers(out);
        out.add(getDepthController());
        out.add(new RecentsViewStateController(this));
    }

    public DepthController getDepthController() {
        return mDepthController;
    }

    public DesktopVisibilityController getDesktopVisibilityController() {
        return mDesktopVisibilityController;
    }

    @Nullable
    public UnfoldTransitionProgressProvider getUnfoldTransitionProgressProvider() {
        return mUnfoldTransitionProgressProvider;
    }

    @Override
    public boolean supportsAdaptiveIconAnimation(View clickedView) {
        return mAppTransitionManager.hasControlRemoteAppTransitionPermission();
    }

    @Override
    public DragOptions getDefaultWorkspaceDragOptions() {
        if (mNextWorkspaceDragOptions != null) {
            DragOptions options = mNextWorkspaceDragOptions;
            mNextWorkspaceDragOptions = null;
            return options;
        }
        return super.getDefaultWorkspaceDragOptions();
    }

    public void setNextWorkspaceDragOptions(DragOptions dragOptions) {
        mNextWorkspaceDragOptions = dragOptions;
    }

    @Override
    public void useFadeOutAnimationForLauncherStart(CancellationSignal signal) {
        QuickstepTransitionManager appTransitionManager = getAppTransitionManager();
        appTransitionManager.setRemoteAnimationProvider(new RemoteAnimationProvider() {
            @Override
            public AnimatorSet createWindowAnimation(RemoteAnimationTarget[] appTargets,
                    RemoteAnimationTarget[] wallpaperTargets) {

                // On the first call clear the reference.
                signal.cancel();

                ValueAnimator fadeAnimation = ValueAnimator.ofFloat(1, 0);
                fadeAnimation.addUpdateListener(new RemoteFadeOutAnimationListener(appTargets,
                        wallpaperTargets));
                AnimatorSet anim = new AnimatorSet();
                anim.play(fadeAnimation);
                return anim;
            }
        }, signal);
    }

    @Override
    public float[] getNormalOverviewScaleAndOffset() {
        return DisplayController.getNavigationMode(this).hasGestures
                ? new float[] {1, 1} : new float[] {1.1f, NO_OFFSET};
    }

    @Override
    public void finishBindingItems(IntSet pagesBoundFirst) {
        super.finishBindingItems(pagesBoundFirst);
        // Instantiate and initialize WellbeingModel now that its loading won't interfere with
        // populating workspace.
        // TODO: Find a better place for this
        WellbeingModel.INSTANCE.get(this);
    }

    @Override
    public void onInitialBindComplete(IntSet boundPages, RunnableList pendingTasks) {
        pendingTasks.add(() -> {
            // This is added in pending task as we need to wait for views to be positioned
            // correctly before registering them for the animation.
            if (mLauncherUnfoldAnimationController != null) {
                // This is needed in case items are rebound while the unfold animation is in
                // progress.
                mLauncherUnfoldAnimationController.updateRegisteredViewsIfNeeded();
            }
        });
        super.onInitialBindComplete(boundPages, pendingTasks);
    }

    @Override
    public ActivityOptionsWrapper getActivityLaunchOptions(View v, @Nullable ItemInfo item) {
        ActivityOptionsWrapper activityOptions =
                mAppTransitionManager.hasControlRemoteAppTransitionPermission()
                        ? mAppTransitionManager.getActivityLaunchOptions(v)
                        : super.getActivityLaunchOptions(v, item);
        if (mLastTouchUpTime > 0) {
            activityOptions.options.setSourceInfo(ActivityOptions.SourceInfo.TYPE_LAUNCHER,
                    mLastTouchUpTime);
        }
        activityOptions.options.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON);
        activityOptions.options.setLaunchDisplayId(
                (v != null && v.getDisplay() != null) ? v.getDisplay().getDisplayId()
                        : Display.DEFAULT_DISPLAY);
        addLaunchCookie(item, activityOptions.options);
        return activityOptions;
    }

    @Override
    @BinderThread
    public void enterStageSplitFromRunningApp(boolean leftOrTop) {
        mSplitWithKeyboardShortcutController.enterStageSplit(leftOrTop);
    }

    /**
     * Adds a new launch cookie for the activity launch if supported.
     *
     * @param info the item info for the launch
     * @param opts the options to set the launchCookie on.
     */
    public void addLaunchCookie(ItemInfo info, ActivityOptions opts) {
        IBinder launchCookie = getLaunchCookie(info);
        if (launchCookie != null) {
            opts.setLaunchCookie(launchCookie);
        }
    }

    /**
     * Return a new launch cookie for the activity launch if supported.
     *
     * @param info the item info for the launch
     */
    public IBinder getLaunchCookie(ItemInfo info) {
        if (info == null) {
            return null;
        }
        switch (info.container) {
            case Favorites.CONTAINER_DESKTOP:
            case Favorites.CONTAINER_HOTSEAT:
                // Fall through and continue it's on the workspace (we don't support swiping back
                // to other containers like all apps or the hotseat predictions (which can change)
                break;
            default:
                if (info.container >= 0) {
                    // Also allow swiping to folders
                    break;
                }
                // Reset any existing launch cookies associated with the cookie
                return ObjectWrapper.wrap(NO_MATCHING_ID);
        }
        switch (info.itemType) {
            case Favorites.ITEM_TYPE_APPLICATION:
            case Favorites.ITEM_TYPE_SHORTCUT:
            case Favorites.ITEM_TYPE_DEEP_SHORTCUT:
            case Favorites.ITEM_TYPE_APPWIDGET:
                // Fall through and continue if it's an app, shortcut, or widget
                break;
            default:
                // Reset any existing launch cookies associated with the cookie
                return ObjectWrapper.wrap(NO_MATCHING_ID);
        }
        return ObjectWrapper.wrap(new Integer(info.id));
    }

    public void setHintUserWillBeActive() {
        addActivityFlags(ACTIVITY_STATE_USER_WILL_BE_ACTIVE);
    }

    @Override
    public void onDisplayInfoChanged(Context context, DisplayController.Info info, int flags) {
        super.onDisplayInfoChanged(context, info, flags);
        // When changing screens, force moving to rest state similar to StatefulActivity.onStop, as
        // StatefulActivity isn't called consistently.
        if ((flags & CHANGE_ACTIVE_SCREEN) != 0) {
            // Do not animate moving to rest state, as it can clash with Launcher#onIdpChanged
            // where reapplyUi calls StateManager's reapplyState during the state change animation,
            // and cancel the state change unexpectedly. The screen will be off during screen
            // transition, hiding the unanimated transition.
            getStateManager().moveToRestState(/* isAnimated = */false);
        }

        if ((flags & CHANGE_NAVIGATION_MODE) != 0) {
            getDragLayer().recreateControllers();
            if (mActionsView != null) {
                mActionsView.updateVerticalMargin(info.navigationMode);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // If Launcher shuts downs during split select, we save some extra data in the recovery
        // bundle to allow graceful recovery. The normal LauncherState restore mechanism doesn't
        // work in this case because restoring straight to OverviewSplitSelect without staging data,
        // or before the tasks themselves have loaded into Overview, causes a crash. So we tell
        // Launcher to first restore into Overview state, wait for the relevant tasks and icons to
        // load in, and then proceed to OverviewSplitSelect.
        if (isInState(OVERVIEW_SPLIT_SELECT)) {
            SplitSelectStateController splitSelectStateController =
                    ((RecentsView) getOverviewPanel()).getSplitSelectController();
            // Launcher will restart in Overview and then transition to OverviewSplitSelect.
            outState.putIBinder(PENDING_SPLIT_SELECT_INFO, ObjectWrapper.wrap(
                    new PendingSplitSelectInfo(
                            splitSelectStateController.getInitialTaskId(),
                            splitSelectStateController.getActiveSplitStagePosition(),
                            splitSelectStateController.getSplitEvent())
            ));
            outState.putInt(RUNTIME_STATE, OVERVIEW.ordinal);
        }
    }

    /**
     * When Launcher restarts, it sometimes needs to recover to a split selection state.
     * This function checks if such a recovery is needed.
     * @return a boolean representing whether the launcher is waiting to recover to
     * OverviewSplitSelect state.
     */
    public boolean hasPendingSplitSelectInfo() {
        return mPendingSplitSelectInfo != null;
    }

    /**
     * See {@link #hasPendingSplitSelectInfo()}
     */
    public @Nullable PendingSplitSelectInfo getPendingSplitSelectInfo() {
        return mPendingSplitSelectInfo;
    }

    /**
     * When the launcher has successfully recovered to OverviewSplitSelect state, this function
     * deletes the recovery data, returning it to a null state.
     */
    public void finishSplitSelectRecovery() {
        mPendingSplitSelectInfo = null;
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
        if (mDepthController != null) {
            mDepthController.dump(prefix, writer);
        }
        RecentsView recentsView = getOverviewPanel();
        writer.println("\nQuickstepLauncher:");
        writer.println(prefix + "\tmOrientationState: " + (recentsView == null ? "recentsNull" :
                recentsView.getPagedViewOrientedState()));
    }
}

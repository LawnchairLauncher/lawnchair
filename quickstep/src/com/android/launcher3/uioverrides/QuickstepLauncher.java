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

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.os.Trace.TRACE_TAG_APP;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_OPTIMIZE_MEASURE;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED;

import static com.android.app.animation.Interpolators.EMPHASIZED;
import static com.android.internal.jank.Cuj.CUJ_LAUNCHER_LAUNCH_APP_PAIR_FROM_WORKSPACE;
import static com.android.launcher3.Flags.enablePredictiveBackGesture;
import static com.android.launcher3.Flags.enableUnfoldStateAnimation;
import static com.android.launcher3.LauncherConstants.SavedInstanceKeys.PENDING_SPLIT_SELECT_INFO;
import static com.android.launcher3.LauncherConstants.SavedInstanceKeys.RUNTIME_STATE;
import static com.android.launcher3.LauncherSettings.Animation.DEFAULT_NO_ICON;
import static com.android.launcher3.LauncherSettings.Animation.VIEW_BACKGROUND;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.NO_OFFSET;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherState.OVERVIEW_MODAL_TASK;
import static com.android.launcher3.LauncherState.OVERVIEW_SPLIT_SELECT;
import static com.android.launcher3.compat.AccessibilityManagerCompat.sendCustomAccessibilityEvent;
import static com.android.launcher3.config.FeatureFlags.enableSplitContextually;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_APP_LAUNCH_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SPLIT_SELECTION_EXIT_HOME;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SPLIT_SELECTION_EXIT_INTERRUPTED;
import static com.android.launcher3.model.data.ItemInfo.NO_MATCHING_ID;
import static com.android.launcher3.popup.QuickstepSystemShortcut.getSplitSelectShortcutByPosition;
import static com.android.launcher3.popup.SystemShortcut.APP_INFO;
import static com.android.launcher3.popup.SystemShortcut.DONT_SUGGEST_APP;
import static com.android.launcher3.popup.SystemShortcut.INSTALL;
import static com.android.launcher3.popup.SystemShortcut.PRIVATE_PROFILE_INSTALL;
import static com.android.launcher3.popup.SystemShortcut.UNINSTALL_APP;
import static com.android.launcher3.popup.SystemShortcut.WIDGETS;
import static com.android.launcher3.taskbar.LauncherTaskbarUIController.ALL_APPS_PAGE_PROGRESS_INDEX;
import static com.android.launcher3.taskbar.LauncherTaskbarUIController.MINUS_ONE_PAGE_PROGRESS_INDEX;
import static com.android.launcher3.taskbar.LauncherTaskbarUIController.WIDGETS_PAGE_PROGRESS_INDEX;
import static com.android.launcher3.testing.shared.TestProtocol.HINT_STATE_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.HINT_STATE_TWO_BUTTON_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.OVERVIEW_STATE_ORDINAL;
import static com.android.launcher3.testing.shared.TestProtocol.QUICK_SWITCH_STATE_ORDINAL;
import static com.android.launcher3.util.DisplayController.CHANGE_ACTIVE_SCREEN;
import static com.android.launcher3.util.DisplayController.CHANGE_NAVIGATION_MODE;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.quickstep.util.AnimUtils.completeRunnableListCallback;
import static com.android.quickstep.util.SplitAnimationTimings.TABLET_HOME_TO_SPLIT;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_HOME_KEY;
import static com.android.window.flags.Flags.enableDesktopWindowingMode;
import static com.android.window.flags.Flags.enableDesktopWindowingWallpaperActivity;
import static com.android.wm.shell.common.split.SplitScreenConstants.SNAP_TO_50_50;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.media.permission.SafeCloseable;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IRemoteCallback;
import android.os.SystemProperties;
import android.os.Trace;
import android.util.AttributeSet;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AnalogClock;
import android.widget.TextClock;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedDispatcher;
import android.window.RemoteTransition;
import android.window.SplashScreen;

import androidx.annotation.BinderThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.app.viewcapture.ViewCaptureFactory;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.InvariantDeviceProfile;
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
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.appprediction.PredictionRowView;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.desktop.DesktopRecentsTransitionController;
import com.android.launcher3.hybridhotseat.HotseatPredictionController;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.logging.StatsLogManager.StatsLogger;
import com.android.launcher3.model.BgDataModel.FixedContainerItems;
import com.android.launcher3.model.WellbeingModel;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.proxy.ProxyActivityStarter;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.statemanager.StateManager.AtomicAnimationFactory;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.taskbar.LauncherTaskbarUIController;
import com.android.launcher3.taskbar.TaskbarManager;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
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
import com.android.launcher3.util.SplitConfigurationOptions;
import com.android.launcher3.util.SplitConfigurationOptions.SplitPositionOption;
import com.android.launcher3.util.SplitConfigurationOptions.SplitSelectSource;
import com.android.launcher3.util.StartActivityParams;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.widget.LauncherWidgetHolder;
import com.android.quickstep.OverviewCommandHelper;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.RecentsModel;
import com.android.quickstep.SystemUiProxy;
import com.android.quickstep.TaskUtils;
import com.android.quickstep.TouchInteractionService.TISBinder;
import com.android.quickstep.util.AsyncClockEventDelegate;
import com.android.quickstep.util.GroupTask;
import com.android.quickstep.util.LauncherUnfoldAnimationController;
import com.android.quickstep.util.QuickstepOnboardingPrefs;
import com.android.quickstep.util.SplitSelectStateController;
import com.android.quickstep.util.SplitToWorkspaceController;
import com.android.quickstep.util.SplitWithKeyboardShortcutController;
import com.android.quickstep.util.TISBindHelper;
import com.android.quickstep.util.unfold.LauncherUnfoldTransitionController;
import com.android.quickstep.util.unfold.ProxyUnfoldTransitionProvider;
import com.android.quickstep.views.FloatingTaskView;
import com.android.quickstep.views.OverviewActionsView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.unfold.RemoteUnfoldSharedComponent;
import com.android.systemui.unfold.UnfoldTransitionFactory;
import com.android.systemui.unfold.UnfoldTransitionProgressProvider;
import com.android.systemui.unfold.config.ResourceUnfoldTransitionConfig;
import com.android.systemui.unfold.config.UnfoldTransitionConfig;
import com.android.systemui.unfold.dagger.UnfoldMain;
import com.android.systemui.unfold.progress.RemoteUnfoldTransitionReceiver;
import com.android.systemui.unfold.updates.RotationChangeProvider;

import kotlin.Unit;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class QuickstepLauncher extends Launcher implements RecentsViewContainer {
    private static final boolean TRACE_LAYOUTS =
            SystemProperties.getBoolean("persist.debug.trace_layouts", false);
    private static final String TRACE_RELAYOUT_CLASS =
            SystemProperties.get("persist.debug.trace_request_layout_class", null);
    public static final boolean GO_LOW_RAM_RECENTS_ENABLED = false;

    protected static final String RING_APPEAR_ANIMATION_PREFIX = "RingAppearAnimation\t";

    private FixedContainerItems mAllAppsPredictions;
    private HotseatPredictionController mHotseatPredictionController;
    private DepthController mDepthController;
    private @Nullable DesktopVisibilityController mDesktopVisibilityController;
    private QuickstepTransitionManager mAppTransitionManager;

    private OverviewActionsView<?> mActionsView;
    private TISBindHelper mTISBindHelper;
    private @Nullable LauncherTaskbarUIController mTaskbarUIController;
    // Will be updated when dragging from taskbar.
    private @Nullable UnfoldTransitionProgressProvider mUnfoldTransitionProgressProvider;
    private @Nullable LauncherUnfoldAnimationController mLauncherUnfoldAnimationController;

    private SplitSelectStateController mSplitSelectStateController;
    private SplitWithKeyboardShortcutController mSplitWithKeyboardShortcutController;
    private SplitToWorkspaceController mSplitToWorkspaceController;

    /**
     * If Launcher restarted while in the middle of an Overview split select, it needs this data to
     * recover. In all other cases this will remain null.
     */
    private PendingSplitSelectInfo mPendingSplitSelectInfo = null;

    @Nullable
    private DesktopRecentsTransitionController mDesktopRecentsTransitionController;

    private SafeCloseable mViewCapture;

    private boolean mEnableWidgetDepth;

    private boolean mIsPredictiveBackToHomeInProgress;

    public static QuickstepLauncher getLauncher(Context context) {
        return fromContext(context);
    }

    @Override
    protected void setupViews() {
        super.setupViews();

        mActionsView = findViewById(R.id.overview_actions_view);
        RecentsView<?,?> overviewPanel = getOverviewPanel();
        SystemUiProxy systemUiProxy = SystemUiProxy.INSTANCE.get(this);
        mSplitSelectStateController =
                new SplitSelectStateController(this, mHandler, getStateManager(),
                        getDepthController(), getStatsLogManager(),
                        systemUiProxy, RecentsModel.INSTANCE.get(this),
                        () -> onStateBack());
        RecentsAnimationDeviceState deviceState = new RecentsAnimationDeviceState(asContext());
        // TODO(b/337863494): Explore use of the same OverviewComponentObserver across launcher
        OverviewComponentObserver overviewComponentObserver = new OverviewComponentObserver(
                asContext(), deviceState);
        if (enableDesktopWindowingMode()) {
            mDesktopRecentsTransitionController = new DesktopRecentsTransitionController(
                    getStateManager(), systemUiProxy, getIApplicationThread(),
                    getDepthController());
        }
        overviewPanel.init(mActionsView, mSplitSelectStateController,
                mDesktopRecentsTransitionController);
        mSplitWithKeyboardShortcutController = new SplitWithKeyboardShortcutController(this,
                mSplitSelectStateController, overviewComponentObserver, deviceState);
        mSplitToWorkspaceController = new SplitToWorkspaceController(this,
                mSplitSelectStateController);
        mActionsView.updateDimension(getDeviceProfile(), overviewPanel.getLastComputedTaskSize());
        mActionsView.updateVerticalMargin(DisplayController.getNavigationMode(this));

        mAppTransitionManager = buildAppTransitionManager();
        mAppTransitionManager.registerRemoteAnimations();
        mAppTransitionManager.registerRemoteTransitions();

        mTISBindHelper = new TISBindHelper(this, this::onTISConnected);
        mDepthController = new DepthController(this);
        if (enableDesktopWindowingMode()) {
            mDesktopVisibilityController = new DesktopVisibilityController(this);
            mDesktopVisibilityController.registerSystemUiListener();
            mSplitSelectStateController.initSplitFromDesktopController(this,
                    overviewComponentObserver);
        }
        mHotseatPredictionController = new HotseatPredictionController(this);

        mEnableWidgetDepth = SystemProperties.getBoolean("ro.launcher.depth.widget", true);
        getWorkspace().addOverlayCallback(progress ->
                onTaskbarInAppDisplayProgressUpdate(progress, MINUS_ONE_PAGE_PROGRESS_INDEX));
        addBackAnimationCallback(mSplitSelectStateController.getSplitBackHandler());
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
    public void enableHotseatEdu(boolean enable) {
        super.enableHotseatEdu(enable);
        mHotseatPredictionController.enableHotseatEdu(enable);
    }

    /**
     * Builds the {@link QuickstepTransitionManager} instance to use for managing transitions.
     */
    protected QuickstepTransitionManager buildAppTransitionManager() {
        return new QuickstepTransitionManager(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        onStateOrResumeChanging(false /* inTransition */);
    }

    @Override
    public RunnableList startActivitySafely(View v, Intent intent, ItemInfo item) {
        // Only pause is taskbar controller is not present until the transition (if it exists) ends
        mHotseatPredictionController.setPauseUIUpdate(getTaskbarUIController() == null);
        PredictionRowView<?> predictionRowView =
                getAppsView().getFloatingHeaderView().findFixedRowByType(PredictionRowView.class);
        // Pause the prediction row updates until the transition (if it exists) ends.
        predictionRowView.setPredictionUiUpdatePaused(true);
        RunnableList result = super.startActivitySafely(v, intent, item);
        if (result == null) {
            mHotseatPredictionController.setPauseUIUpdate(false);
            predictionRowView.setPredictionUiUpdatePaused(false);
        } else {
            result.add(() -> {
                mHotseatPredictionController.setPauseUIUpdate(false);
                predictionRowView.setPredictionUiUpdatePaused(false);
            });
        }
        return result;
    }

    @Override
    public void startBinding() {
        super.startBinding();
        mHotseatPredictionController.verifyUIUpdateNotPaused();
    }

    @Override
    protected void onActivityFlagsChanged(int changeBits) {
        if ((changeBits & ACTIVITY_STATE_STARTED) != 0) {
            mDepthController.setActivityStarted(isStarted());
        }

        if ((changeBits & ACTIVITY_STATE_RESUMED) != 0) {
            if (!FeatureFlags.enableHomeTransitionListener() && mTaskbarUIController != null) {
                mTaskbarUIController.onLauncherVisibilityChanged(hasBeenResumed());
            }
        }

        super.onActivityFlagsChanged(changeBits);
        if ((changeBits & (ACTIVITY_STATE_DEFERRED_RESUMED | ACTIVITY_STATE_STARTED
                | ACTIVITY_STATE_USER_ACTIVE | ACTIVITY_STATE_TRANSITION_ACTIVE)) != 0) {
            onStateOrResumeChanging((getActivityFlags() & ACTIVITY_STATE_TRANSITION_ACTIVE) == 0);
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
        // Order matters as it affects order of appearance in popup container
        List<SystemShortcut.Factory> shortcuts = new ArrayList(Arrays.asList(
                APP_INFO, WellbeingModel.SHORTCUT_FACTORY, mHotseatPredictionController));
        shortcuts.addAll(getSplitShortcuts());
        shortcuts.add(WIDGETS);
        shortcuts.add(INSTALL);
        if (Flags.enablePrivateSpaceInstallShortcut()) {
            shortcuts.add(PRIVATE_PROFILE_INSTALL);
        }
        if (Flags.enableShortcutDontSuggestApp()) {
            shortcuts.add(DONT_SUGGEST_APP);
        }
        if (Flags.enablePrivateSpace()) {
            shortcuts.add(UNINSTALL_APP);
        }
        return shortcuts.stream();
    }

    private List<SystemShortcut.Factory<QuickstepLauncher>> getSplitShortcuts() {
        if (!mDeviceProfile.isTablet || mSplitSelectStateController.isSplitSelectActive()) {
            return Collections.emptyList();
        }
        RecentsView recentsView = getOverviewPanel();
        // TODO(b/266482558): Pull it out of PagedOrentationHandler for split from workspace.
        List<SplitPositionOption> positions =
                recentsView.getPagedOrientationHandler().getSplitPositionOptions(
                        mDeviceProfile);
        List<SystemShortcut.Factory<QuickstepLauncher>> splitShortcuts = new ArrayList<>();
        for (SplitPositionOption position : positions) {
            splitShortcuts.add(getSplitSelectShortcutByPosition(position));
        }
        return splitShortcuts;
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
                    && !profile.isVerticalBarLayout();
            SystemUiProxy.INSTANCE.get(this)
                    .setLauncherKeepClearAreaHeight(visible, profile.hotseatBarSizePx);
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
        if (mAppTransitionManager != null) {
            mAppTransitionManager.onActivityDestroyed();
        }
        mAppTransitionManager = null;
        mIsPredictiveBackToHomeInProgress = false;

        if (mUnfoldTransitionProgressProvider != null) {
            SystemUiProxy.INSTANCE.get(this).setUnfoldAnimationListener(null);
            mUnfoldTransitionProgressProvider.destroy();
        }

        mTISBindHelper.onDestroy();

        if (mLauncherUnfoldAnimationController != null) {
            mLauncherUnfoldAnimationController.onDestroy();
        }

        if (mDesktopVisibilityController != null) {
            mDesktopVisibilityController.unregisterSystemUiListener();
        }
        mDesktopVisibilityController = null;

        if (mSplitSelectStateController != null) {
            removeBackAnimationCallback(mSplitSelectStateController.getSplitBackHandler());
            mSplitSelectStateController.onDestroy();
        }
        mSplitSelectStateController = null;

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
                TaskView tasktolaunch = rv.getCurrentPageTaskView();
                if (tasktolaunch != null) {
                    tasktolaunch.launchTask(success -> {
                        if (!success) {
                            getStateManager().goToState(OVERVIEW);
                        } else {
                            getStateManager().moveToRestState();
                        }
                        return Unit.INSTANCE;
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
        BiConsumer<AnimatorSet, Long> splitAnimator = (animatorSet, duration) ->
                animatorSet.play(mSplitSelectStateController.getSplitAnimationController()
                        .createPlaceholderDismissAnim(this, LAUNCHER_SPLIT_SELECTION_EXIT_HOME,
                                duration));
        switch (mode) {
            case NO_BUTTON:
                list.add(new NoButtonQuickSwitchTouchController(this));
                list.add(new NavBarToHomeTouchController(this, splitAnimator));
                list.add(new NoButtonNavbarToOverviewTouchController(this, splitAnimator));
                break;
            case TWO_BUTTONS:
                list.add(new TwoButtonNavbarTouchController(this));
                list.add(getDeviceProfile().isVerticalBarLayout()
                        ? new TransposedQuickSwitchTouchController(this)
                        : new QuickSwitchTouchController(this));
                list.add(new PortraitStatesTouchController(this));
                break;
            case THREE_BUTTONS:
                list.add(new NoButtonQuickSwitchTouchController(this));
                list.add(new NavBarToHomeTouchController(this, splitAnimator));
                list.add(new NoButtonNavbarToOverviewTouchController(this, splitAnimator));
                list.add(new PortraitStatesTouchController(this));
                break;
            default:
                list.add(new PortraitStatesTouchController(this));
                break;
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
        // Back dispatcher is registered in {@link BaseActivity#onCreate}. For predictive back to
        // work, we must opt-in BEFORE registering back dispatcher. So we need to call
        // setEnableOnBackInvokedCallback() before super.onCreate()
        if (Utilities.ATLEAST_U && enablePredictiveBackGesture()) {
            getApplicationInfo().setEnableOnBackInvokedCallback(true);
        }
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mPendingSplitSelectInfo = ObjectWrapper.unwrap(
                    savedInstanceState.getIBinder(PENDING_SPLIT_SELECT_INFO));
        }
        addMultiWindowModeChangedListener(mDepthController);
        initUnfoldTransitionProgressProvider();
        if (FeatureFlags.CONTINUOUS_VIEW_TREE_CAPTURE.get()) {
            mViewCapture = ViewCaptureFactory.getInstance(this).startCapture(getWindow());
        }
        getWindow().addPrivateFlags(PRIVATE_FLAG_OPTIMIZE_MEASURE);
        QuickstepOnboardingPrefs.setup(this);
        View.setTraceLayoutSteps(TRACE_LAYOUTS);
        View.setTracedRequestLayoutClassClass(TRACE_RELAYOUT_CLASS);
    }

    @Override
    protected boolean initDeviceProfile(InvariantDeviceProfile idp) {
        final boolean ret = super.initDeviceProfile(idp);
        mDeviceProfile.isPredictiveBackSwipe =
                getApplicationInfo().isOnBackInvokedCallbackEnabled();
        return ret;
    }

    @Override
    public void startSplitSelection(SplitSelectSource splitSelectSource) {
        RecentsView recentsView = getOverviewPanel();
        // Check if there is already an instance of this app running, if so, initiate the split
        // using that.
        mSplitSelectStateController.findLastActiveTasksAndRunCallback(
                Collections.singletonList(splitSelectSource.itemInfo.getComponentKey()),
                false /* findExactPairMatch */,
                foundTasks -> {
                    @Nullable Task foundTask = foundTasks[0];
                    boolean taskWasFound = foundTask != null;
                    splitSelectSource.alreadyRunningTaskId = taskWasFound
                            ? foundTask.key.id
                            : INVALID_TASK_ID;
                    if (enableSplitContextually()) {
                        startSplitToHome(splitSelectSource);
                    } else {
                        recentsView.initiateSplitSelect(splitSelectSource);
                    }
                }
        );
    }

    /** TODO(b/266482558) Migrate into SplitSelectStateController or someplace split specific. */
    private void startSplitToHome(SplitSelectSource source) {
        AbstractFloatingView.closeAllOpenViews(this);
        int splitPlaceholderSize = getResources().getDimensionPixelSize(
                R.dimen.split_placeholder_size);
        int splitPlaceholderInset = getResources().getDimensionPixelSize(
                R.dimen.split_placeholder_inset);
        Rect tempRect = new Rect();

        mSplitSelectStateController.setInitialTaskSelect(source.intent,
                source.position.stagePosition, source.itemInfo, source.splitEvent,
                source.alreadyRunningTaskId);

        RecentsView recentsView = getOverviewPanel();
        recentsView.getPagedOrientationHandler().getInitialSplitPlaceholderBounds(
                splitPlaceholderSize, splitPlaceholderInset, getDeviceProfile(),
                mSplitSelectStateController.getActiveSplitStagePosition(), tempRect);

        PendingAnimation anim = new PendingAnimation(TABLET_HOME_TO_SPLIT.getDuration());
        RectF startingTaskRect = new RectF();
        final FloatingTaskView floatingTaskView = FloatingTaskView.getFloatingTaskView(this,
                source.getView(), null /* thumbnail */, source.getDrawable(), startingTaskRect);
        floatingTaskView.setAlpha(1);
        floatingTaskView.addStagingAnimation(anim, startingTaskRect, tempRect,
                false /* fadeWithThumbnail */, true /* isStagedTask */);
        floatingTaskView.setOnClickListener(view ->
                mSplitSelectStateController.getSplitAnimationController().
                        playAnimPlaceholderToFullscreen(this, view, Optional.empty()));
        mSplitSelectStateController.setFirstFloatingTaskView(floatingTaskView);
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                getDragLayer().removeView(floatingTaskView);
                mSplitSelectStateController.getSplitAnimationController()
                        .removeSplitInstructionsView(QuickstepLauncher.this);
                mSplitSelectStateController.resetState();
            }
        });
        anim.add(mSplitSelectStateController.getSplitAnimationController()
                .getShowSplitInstructionsAnim(this).buildAnim());
        anim.buildAnim().start();
    }

    @Override
    public boolean isSplitSelectionActive() {
        return mSplitSelectStateController.isSplitSelectActive();
    }

    public boolean areBothSplitAppsConfirmed() {
        return mSplitSelectStateController.isBothSplitAppsConfirmed();
    }

    @Override
    public void onStateTransitionCompletedAfterSwipeToHome(LauncherState finalState) {
        if (mTaskbarUIController != null) {
            mTaskbarUIController.onStateTransitionCompletedAfterSwipeToHome(finalState);
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

        if (enableSplitContextually()) {
            // If Launcher pauses before both split apps are selected, exit split screen.
            if (!mSplitSelectStateController.isBothSplitAppsConfirmed() &&
                    !mSplitSelectStateController.isLaunchingFirstAppFullscreen()) {
                mSplitSelectStateController
                        .logExitReason(LAUNCHER_SPLIT_SELECTION_EXIT_INTERRUPTED);
                mSplitSelectStateController.getSplitAnimationController()
                        .playPlaceholderDismissAnim(this, LAUNCHER_SPLIT_SELECTION_EXIT_INTERRUPTED);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        OverviewCommandHelper overviewCommandHelper = mTISBindHelper.getOverviewCommandHelper();
        if (overviewCommandHelper != null) {
            overviewCommandHelper.clearPendingCommands();
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

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return tryHandleBackKey(event) || super.dispatchKeyEvent(event);
    }

    // TODO (b/267248420) Once the recents input consumer has been removed, there is no need to
    //  handle the back key specially.
    private boolean tryHandleBackKey(KeyEvent event) {
        // Unlike normal activity, recents can receive input event from InputConsumer, so the input
        // event won't go through ViewRootImpl#InputStage#onProcess.
        // So when receive back key, try to do the same check thing in
        // ViewRootImpl#NativePreImeInputStage#onProcess
        if (!Utilities.ATLEAST_U || !enablePredictiveBackGesture()
                || event.getKeyCode() != KeyEvent.KEYCODE_BACK
                || event.getAction() != KeyEvent.ACTION_UP || event.isCanceled()) {
            return false;
        }

        getOnBackAnimationCallback().onBackInvoked();
        return true;
    }

    @Override
    protected void registerBackDispatcher() {
        if (!enablePredictiveBackGesture()) {
            super.registerBackDispatcher();
            return;
        }
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                new OnBackAnimationCallback() {

                    @Nullable OnBackAnimationCallback mActiveOnBackAnimationCallback;

                    @Override
                    public void onBackStarted(@NonNull BackEvent backEvent) {
                        if (mActiveOnBackAnimationCallback != null) {
                            mActiveOnBackAnimationCallback.onBackCancelled();
                        }
                        mActiveOnBackAnimationCallback = getOnBackAnimationCallback();
                        mActiveOnBackAnimationCallback.onBackStarted(backEvent);
                    }

                    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    @Override
                    public void onBackInvoked() {
                        // Recreate mActiveOnBackAnimationCallback if necessary to avoid NPE
                        // because:
                        // 1. b/260636433: In 3-button-navigation mode, onBackStarted() is not
                        // called on ACTION_DOWN before onBackInvoked() is called in ACTION_UP.
                        // 2. Launcher#onBackPressed() will call onBackInvoked() without calling
                        // onBackInvoked() beforehand.
                        if (mActiveOnBackAnimationCallback == null) {
                            mActiveOnBackAnimationCallback = getOnBackAnimationCallback();
                        }
                        mActiveOnBackAnimationCallback.onBackInvoked();
                        mActiveOnBackAnimationCallback = null;
                        TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "onBackInvoked");
                    }

                    @Override
                    public void onBackProgressed(@NonNull BackEvent backEvent) {
                        if (!FeatureFlags.IS_STUDIO_BUILD
                                && mActiveOnBackAnimationCallback == null) {
                            return;
                        }
                        mActiveOnBackAnimationCallback.onBackProgressed(backEvent);
                    }

                    @Override
                    public void onBackCancelled() {
                        if (!FeatureFlags.IS_STUDIO_BUILD
                                && mActiveOnBackAnimationCallback == null) {
                            return;
                        }
                        mActiveOnBackAnimationCallback.onBackCancelled();
                        mActiveOnBackAnimationCallback = null;
                    }
                });
    }

    private void onTaskbarInAppDisplayProgressUpdate(float progress, int flag) {
        TaskbarManager taskbarManager = mTISBindHelper.getTaskbarManager();
        if (taskbarManager == null
                || taskbarManager.getCurrentActivityContext() == null
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
        if (!enableDesktopWindowingWallpaperActivity()
                && mDesktopVisibilityController != null
                && mDesktopVisibilityController.areDesktopTasksVisible()
                && !mDesktopVisibilityController.isRecentsGestureInProgress()) {
            // Return early to skip setting activity to appear as resumed
            // TODO: b/333533253 - Remove after flag rollout
            return;
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
        TaskbarManager taskbarManager = mTISBindHelper.getTaskbarManager();
        if (taskbarManager != null) {
            taskbarManager.setActivity(this);
        }
        mTISBindHelper.setPredictiveBackToHomeInProgress(mIsPredictiveBackToHomeInProgress);
    }

    @Override
    public void runOnBindToTouchInteractionService(Runnable r) {
        mTISBindHelper.runOnBindToTouchInteractionService(r);
    }

    private void initUnfoldTransitionProgressProvider() {
        if (!enableUnfoldStateAnimation()) {
            final UnfoldTransitionConfig config = new ResourceUnfoldTransitionConfig();
            if (config.isEnabled()) {
                initRemotelyCalculatedUnfoldAnimation(config);
            }
        } else {
            ProxyUnfoldTransitionProvider provider =
                    SystemUiProxy.INSTANCE.get(this).getUnfoldTransitionProvider();
            if (provider != null) {
                new LauncherUnfoldTransitionController(this, provider);
            }
        }
    }

    /** Receives animation progress from sysui process. */
    private void initRemotelyCalculatedUnfoldAnimation(UnfoldTransitionConfig config) {
        RemoteUnfoldSharedComponent unfoldComponent =
                UnfoldTransitionFactory.createRemoteUnfoldSharedComponent(
                        /* context= */ this,
                        config,
                        getMainExecutor(),
                        getMainThreadHandler(),
                        /* backgroundExecutor= */ UI_HELPER_EXECUTOR,
                        /* bgHandler= */ UI_HELPER_EXECUTOR.getHandler(),
                        /* tracingTagPrefix= */ "launcher",
                        getSystemService(DisplayManager.class)
                );

        final RemoteUnfoldTransitionReceiver remoteUnfoldTransitionProgressProvider =
                unfoldComponent.getRemoteTransitionProgress().orElseThrow(
                        () -> new IllegalStateException(
                                "Trying to create getRemoteTransitionProgress when the transition "
                                        + "is disabled"));
        mUnfoldTransitionProgressProvider = remoteUnfoldTransitionProgressProvider;

        SystemUiProxy.INSTANCE.get(this).setUnfoldAnimationListener(
                remoteUnfoldTransitionProgressProvider);

        initUnfoldAnimationController(mUnfoldTransitionProgressProvider,
                unfoldComponent.getRotationChangeProvider());
    }

    private void initUnfoldAnimationController(UnfoldTransitionProgressProvider progressProvider,
            @UnfoldMain RotationChangeProvider rotationChangeProvider) {
        mLauncherUnfoldAnimationController = new LauncherUnfoldAnimationController(
                /* launcher= */ this,
                getWindowManager(),
                progressProvider,
                rotationChangeProvider
        );
    }

    public void setTaskbarUIController(LauncherTaskbarUIController taskbarUIController) {
        mTaskbarUIController = taskbarUIController;
    }

    public @Nullable LauncherTaskbarUIController getTaskbarUIController() {
        return mTaskbarUIController;
    }

    public SplitToWorkspaceController getSplitToWorkspaceController() {
        return mSplitToWorkspaceController;
    }

    @Override
    protected void handleSplitAnimationGoingToHome(StatsLogManager.EventEnum splitDismissReason) {
        super.handleSplitAnimationGoingToHome(splitDismissReason);
        mSplitSelectStateController.getSplitAnimationController()
                .playPlaceholderDismissAnim(this, splitDismissReason);
    }

    @Override
    public void dismissSplitSelection(StatsLogManager.LauncherEvent splitDismissEvent) {
        super.dismissSplitSelection(splitDismissEvent);
        mSplitSelectStateController.getSplitAnimationController()
                .playPlaceholderDismissAnim(this, splitDismissEvent);
    }

    @Override
    public OverviewActionsView<?> getActionsView() {
        return mActionsView;
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

    @Nullable
    public DesktopVisibilityController getDesktopVisibilityController() {
        return mDesktopVisibilityController;
    }

    @Nullable
    public UnfoldTransitionProgressProvider getUnfoldTransitionProgressProvider() {
        return mUnfoldTransitionProgressProvider;
    }

    @Override
    public boolean supportsAdaptiveIconAnimation(View clickedView) {
        return true;
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

        if (mLauncherUnfoldAnimationController != null) {
            // This is needed in case items are rebound while the unfold animation is in progress.
            mLauncherUnfoldAnimationController.updateRegisteredViewsIfNeeded();
        }
    }

    @Override
    public ActivityOptionsWrapper getActivityLaunchOptions(View v, @Nullable ItemInfo item) {
        ActivityOptionsWrapper activityOptions = mAppTransitionManager.getActivityLaunchOptions(v);
        if (mLastTouchUpTime > 0) {
            activityOptions.options.setSourceInfo(ActivityOptions.SourceInfo.TYPE_LAUNCHER,
                    mLastTouchUpTime);
        }
        if (item != null && (item.animationType == DEFAULT_NO_ICON
                || item.animationType == VIEW_BACKGROUND)) {
            activityOptions.options.setSplashScreenStyle(
                    SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR);
        } else {
            activityOptions.options.setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON);
        }
        activityOptions.options.setLaunchDisplayId(
                (v != null && v.getDisplay() != null) ? v.getDisplay().getDisplayId()
                        : Display.DEFAULT_DISPLAY);
        activityOptions.options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
        addLaunchCookie(item, activityOptions.options);
        return activityOptions;
    }

    @Override
    public ActivityOptionsWrapper makeDefaultActivityOptions(int splashScreenStyle) {
        RunnableList callbacks = new RunnableList();
        ActivityOptions options = ActivityOptions.makeCustomAnimation(this, 0, 0);
        options.setSplashScreenStyle(splashScreenStyle);
        options.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);

        IRemoteCallback endCallback = completeRunnableListCallback(callbacks);
        options.setOnAnimationAbortListener(endCallback);
        options.setOnAnimationFinishedListener(endCallback);
        return new ActivityOptionsWrapper(options, callbacks);
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
            case Favorites.CONTAINER_PRIVATESPACE:
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
            // Launcher will restart in Overview and then transition to OverviewSplitSelect.
            outState.putIBinder(PENDING_SPLIT_SELECT_INFO, ObjectWrapper.wrap(
                    new PendingSplitSelectInfo(
                            mSplitSelectStateController.getInitialTaskId(),
                            mSplitSelectStateController.getActiveSplitStagePosition(),
                            mSplitSelectStateController.getSplitEvent())
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

    /**
     * Sets flag whether a predictive back-to-home animation is in progress
     */
    public void setPredictiveBackToHomeInProgress(boolean isInProgress) {
        mIsPredictiveBackToHomeInProgress = isInProgress;
        mTISBindHelper.setPredictiveBackToHomeInProgress(isInProgress);
    }

    @Override
    public boolean areDesktopTasksVisible() {
        if (mDesktopVisibilityController != null) {
            return mDesktopVisibilityController.areDesktopTasksVisible();
        }
        return false;
    }

    @Override
    protected void onDeviceProfileInitiated() {
        super.onDeviceProfileInitiated();
        SystemUiProxy.INSTANCE.get(this).setLauncherAppIconSize(mDeviceProfile.iconSizePx);
    }

    @Override
    public void dispatchDeviceProfileChanged() {
        super.dispatchDeviceProfileChanged();
        Trace.instantForTrack(TRACE_TAG_APP, "QuickstepLauncher#DeviceProfileChanged",
                getDeviceProfile().toSmallString());
        SystemUiProxy.INSTANCE.get(this).setLauncherAppIconSize(mDeviceProfile.iconSizePx);
        TaskbarManager taskbarManager = mTISBindHelper.getTaskbarManager();
        if (taskbarManager != null) {
            taskbarManager.debugWhyTaskbarNotDestroyed("QuickstepLauncher#onDeviceProfileChanged");
        }
    }

    /**
     * Launches the given {@link GroupTask} in splitscreen.
     */
    public void launchSplitTasks(
            @NonNull GroupTask groupTask, @Nullable RemoteTransition remoteTransition) {
        // Top/left and bottom/right tasks respectively.
        Task task1 = groupTask.task1;
        // task2 should never be null when calling this method. Allow a crash to catch invalid calls
        Task task2 = groupTask.task2;
        mSplitSelectStateController.launchExistingSplitPair(
                null /* launchingTaskView */,
                task1.key.id,
                task2.key.id,
                SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT,
                /* callback= */ success -> mSplitSelectStateController.resetState(),
                /* freezeTaskList= */ false,
                groupTask.mSplitBounds == null
                        ? SNAP_TO_50_50
                        : groupTask.mSplitBounds.snapPosition,
                remoteTransition);
    }

    /**
     * Launches two apps as an app pair.
     */
    public void launchAppPair(AppPairIcon appPairIcon) {
        mSplitSelectStateController.getAppPairsController().launchAppPair(appPairIcon,
                CUJ_LAUNCHER_LAUNCH_APP_PAIR_FROM_WORKSPACE);
    }

    public boolean canStartHomeSafely() {
        OverviewCommandHelper overviewCommandHelper = mTISBindHelper.getOverviewCommandHelper();
        return overviewCommandHelper == null || overviewCommandHelper.canStartHomeSafely();
    }

    @Override
    public boolean isBubbleBarEnabled() {
        return (mTaskbarUIController != null && mTaskbarUIController.isBubbleBarEnabled());
    }

    @Override
    public boolean hasBubbles() {
        return (mTaskbarUIController != null && mTaskbarUIController.hasBubbles());
    }

    @NonNull
    public TISBindHelper getTISBindHelper() {
        return mTISBindHelper;
    }

    @Override
    public boolean handleIncorrectSplitTargetSelection() {
        if (!enableSplitContextually() || !mSplitSelectStateController.isSplitSelectActive()) {
            return false;
        }
        mSplitSelectStateController.getSplitInstructionsView().goBoing();
        return true;
    }

    private static final class LauncherTaskViewController extends
            TaskViewTouchController<QuickstepLauncher> {

        LauncherTaskViewController(QuickstepLauncher activity) {
            super(activity);
        }

        @Override
        protected boolean isRecentsInteractive() {
            return mContainer.isInState(OVERVIEW) || mContainer.isInState(OVERVIEW_MODAL_TASK);
        }

        @Override
        protected boolean isRecentsModal() {
            return mContainer.isInState(OVERVIEW_MODAL_TASK);
        }

        @Override
        protected void onUserControlledAnimationCreated(AnimatorPlaybackController animController) {
            mContainer.getStateManager().setCurrentUserControlledAnimation(animController);
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
        if (recentsView != null) {
            recentsView.getSplitSelectController().dump(prefix, writer);
        }
        if (mAppTransitionManager != null) {
            mAppTransitionManager.dump(prefix + "\t" + RING_APPEAR_ANIMATION_PREFIX, writer);
        }
        if (mHotseatPredictionController != null) {
            mHotseatPredictionController.dump(prefix, writer);
        }
        PredictionRowView<?> predictionRowView =
                getAppsView().getFloatingHeaderView().findFixedRowByType(
                        PredictionRowView.class);
        predictionRowView.dump(prefix, writer);
    }

    @Override
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        switch (name) {
            case "TextClock", "android.widget.TextClock" -> {
                TextClock tc = new TextClock(context, attrs);
                tc.setClockEventDelegate(AsyncClockEventDelegate.INSTANCE.get(this));
                return tc;
            }
            case "AnalogClock", "android.widget.AnalogClock" -> {
                AnalogClock ac = new AnalogClock(context, attrs);
                ac.setClockEventDelegate(AsyncClockEventDelegate.INSTANCE.get(this));
                return ac;
            }
        }
        return super.onCreateView(parent, name, context, attrs);
    }

    @Override
    public boolean isRecentsViewVisible() {
        return getStateManager().getState().isRecentsViewVisible;
    }
}

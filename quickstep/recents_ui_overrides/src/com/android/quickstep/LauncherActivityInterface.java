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
package com.android.quickstep;

import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.uioverrides.states.QuickstepAtomicAnimationFactory.INDEX_SHELF_ANIM;
import static com.android.quickstep.SysUINavigationMode.getMode;
import static com.android.quickstep.SysUINavigationMode.hideShelfInTwoButtonLandscape;
import static com.android.quickstep.util.LayoutUtils.getDefaultSwipeHeight;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.util.Log;
import android.view.animation.Interpolator;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherInitListener;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.allapps.DiscoveryBounce;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.appprediction.PredictionUiStateManager;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statehandlers.DepthController.ClampedDepthProperty;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.touch.PagedOrientationHandler;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.util.ActivityInitListener;
import com.android.quickstep.util.AnimatorControllerWithResistance;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.util.ShelfPeekAnim.ShelfAnimState;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.plugins.shared.LauncherOverlayManager;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * {@link BaseActivityInterface} for the in-launcher recents.
 */
public final class LauncherActivityInterface extends
        BaseActivityInterface<LauncherState, BaseQuickstepLauncher> {

    public static final LauncherActivityInterface INSTANCE = new LauncherActivityInterface();

    private LauncherActivityInterface() {
        super(true, OVERVIEW, BACKGROUND_APP);
    }

    @Override
    public int getSwipeUpDestinationAndLength(DeviceProfile dp, Context context, Rect outRect,
            PagedOrientationHandler orientationHandler) {
        calculateTaskSize(context, dp, outRect, orientationHandler);
        if (dp.isVerticalBarLayout() && SysUINavigationMode.getMode(context) != Mode.NO_BUTTON) {
            Rect targetInsets = dp.getInsets();
            int hotseatInset = dp.isSeascape() ? targetInsets.left : targetInsets.right;
            return dp.hotseatBarSizePx + hotseatInset;
        } else {
            return LayoutUtils.getShelfTrackingDistance(context, dp, orientationHandler);
        }
    }

    @Override
    public void onSwipeUpToRecentsComplete() {
        super.onSwipeUpToRecentsComplete();
        Launcher launcher = getCreatedActivity();
        if (launcher != null) {
            RecentsView recentsView = launcher.getOverviewPanel();
            DiscoveryBounce.showForOverviewIfNeeded(launcher,
                    recentsView.getPagedOrientationHandler());
        }
    }

    @Override
    public void onSwipeUpToHomeComplete(RecentsAnimationDeviceState deviceState) {
        Launcher launcher = getCreatedActivity();
        if (launcher == null) {
            return;
        }
        // Ensure recents is at the correct position for NORMAL state. For example, when we detach
        // recents, we assume the first task is invisible, making translation off by one task.
        launcher.getStateManager().reapplyState();
        launcher.getRootView().setForceHideBackArrow(false);
        notifyRecentsOfOrientation(deviceState.getRotationTouchHelper());
    }

    @Override
    public void onAssistantVisibilityChanged(float visibility) {
        Launcher launcher = getCreatedActivity();
        if (launcher == null) {
            return;
        }
        launcher.onAssistantVisibilityChanged(visibility);
    }

    @Override
    public AnimationFactory prepareRecentsUI(RecentsAnimationDeviceState deviceState,
            boolean activityVisible, Consumer<AnimatorControllerWithResistance> callback) {
        notifyRecentsOfOrientation(deviceState.getRotationTouchHelper());
        DefaultAnimationFactory factory = new DefaultAnimationFactory(callback) {
            @Override
            public void setShelfState(ShelfAnimState shelfState, Interpolator interpolator,
                    long duration) {
                mActivity.getShelfPeekAnim().setShelfState(shelfState, interpolator, duration);
            }

            @Override
            protected void createBackgroundToOverviewAnim(BaseQuickstepLauncher activity,
                    PendingAnimation pa) {
                super.createBackgroundToOverviewAnim(activity, pa);

                if (!activity.getDeviceProfile().isVerticalBarLayout()
                        && SysUINavigationMode.getMode(activity) != Mode.NO_BUTTON) {
                    // Don't animate the shelf when the mode is NO_BUTTON, because we
                    // update it atomically.
                    pa.add(activity.getStateManager().createStateElementAnimation(
                            INDEX_SHELF_ANIM,
                            BACKGROUND_APP.getVerticalProgress(activity),
                            OVERVIEW.getVerticalProgress(activity)));
                }

                // Animate the blur and wallpaper zoom
                float fromDepthRatio = BACKGROUND_APP.getDepth(activity);
                float toDepthRatio = OVERVIEW.getDepth(activity);
                pa.addFloat(getDepthController(),
                        new ClampedDepthProperty(fromDepthRatio, toDepthRatio),
                        fromDepthRatio, toDepthRatio, LINEAR);

            }
        };

        BaseQuickstepLauncher launcher = factory.initUI();
        // Since all apps is not visible, we can safely reset the scroll position.
        // This ensures then the next swipe up to all-apps starts from scroll 0.
        launcher.getAppsView().reset(false /* animate */);
        return factory;
    }

    @Override
    public ActivityInitListener createActivityInitListener(Predicate<Boolean> onInitListener) {
        return new LauncherInitListener((activity, alreadyOnHome) ->
                onInitListener.test(alreadyOnHome));
    }

    @Override
    public void setOnDeferredActivityLaunchCallback(Runnable r) {
        Launcher launcher = getCreatedActivity();
        if (launcher == null) {
            return;
        }
        launcher.setOnDeferredActivityLaunchCallback(r);
    }

    @Nullable
    @Override
    public BaseQuickstepLauncher getCreatedActivity() {
        return BaseQuickstepLauncher.ACTIVITY_TRACKER.getCreatedActivity();
    }

    @Nullable
    @Override
    public DepthController getDepthController() {
        BaseQuickstepLauncher launcher = getCreatedActivity();
        if (launcher == null) {
            return null;
        }
        return launcher.getDepthController();
    }

    @Nullable
    @Override
    public RecentsView getVisibleRecentsView() {
        Launcher launcher = getVisibleLauncher();
        return launcher != null && launcher.getStateManager().getState().overviewUi
                ? launcher.getOverviewPanel() : null;
    }

    @Nullable
    @UiThread
    private Launcher getVisibleLauncher() {
        Launcher launcher = getCreatedActivity();
        return (launcher != null) && launcher.isStarted() && launcher.hasWindowFocus()
                ? launcher : null;
    }

    @Override
    public boolean switchToRecentsIfVisible(Runnable onCompleteCallback) {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.OVERIEW_NOT_ALLAPPS, "switchToRecentsIfVisible");
        }
        Launcher launcher = getVisibleLauncher();
        if (launcher == null) {
            return false;
        }

        launcher.getUserEventDispatcher().logActionCommand(
                LauncherLogProto.Action.Command.RECENTS_BUTTON,
                getContainerType(),
                LauncherLogProto.ContainerType.TASKSWITCHER);
        launcher.getStateManager().goToState(OVERVIEW,
                launcher.getStateManager().shouldAnimateStateChange(), onCompleteCallback);
        return true;
    }


    @Override
    public void onExitOverview(RotationTouchHelper deviceState, Runnable exitRunnable) {
        final StateManager<LauncherState> stateManager = getCreatedActivity().getStateManager();
        stateManager.addStateListener(
                new StateManager.StateListener<LauncherState>() {
                    @Override
                    public void onStateTransitionComplete(LauncherState toState) {
                        // Are we going from Recents to Workspace?
                        if (toState == LauncherState.NORMAL) {
                            exitRunnable.run();
                            notifyRecentsOfOrientation(deviceState);
                            stateManager.removeStateListener(this);
                        }
                    }
                });
    }

    private void notifyRecentsOfOrientation(RotationTouchHelper rotationTouchHelper) {
        // reset layout on swipe to home
        RecentsView recentsView = getCreatedActivity().getOverviewPanel();
        recentsView.setLayoutRotation(rotationTouchHelper.getCurrentActiveRotation(),
                rotationTouchHelper.getDisplayRotation());
    }

    @Override
    public Rect getOverviewWindowBounds(Rect homeBounds, RemoteAnimationTargetCompat target) {
        return homeBounds;
    }

    @Override
    public boolean allowMinimizeSplitScreen() {
        return true;
    }

    @Override
    public void updateOverviewPredictionState() {
        Launcher launcher = getCreatedActivity();
        if (launcher == null) {
            return;
        }
        PredictionUiStateManager.INSTANCE.get(launcher).switchClient(
                PredictionUiStateManager.Client.OVERVIEW);
    }

    @Override
    public int getContainerType() {
        final Launcher launcher = getVisibleLauncher();
        return launcher != null ? launcher.getStateManager().getState().containerType
                : LauncherLogProto.ContainerType.APP;
    }

    @Override
    public boolean isInLiveTileMode() {
        Launcher launcher = getCreatedActivity();
        return launcher != null && launcher.getStateManager().getState() == OVERVIEW &&
                launcher.isStarted();
    }

    @Override
    public void onLaunchTaskFailed() {
        Launcher launcher = getCreatedActivity();
        if (launcher == null) {
            return;
        }
        launcher.getStateManager().goToState(OVERVIEW);
    }

    @Override
    public void closeOverlay() {
        Launcher launcher = getCreatedActivity();
        if (launcher == null) {
            return;
        }
        LauncherOverlayManager om = launcher.getOverlayManager();
        if (!launcher.isStarted() || launcher.isForceInvisible()) {
            om.hideOverlay(false /* animate */);
        } else {
            om.hideOverlay(150);
        }
    }

    @Override
    protected float getExtraSpace(Context context, DeviceProfile dp,
            PagedOrientationHandler orientationHandler) {
        if ((dp.isVerticalBarLayout() && !showOverviewActions(context))
                || hideShelfInTwoButtonLandscape(context, orientationHandler)) {
            return 0;
        } else {
            Resources res = context.getResources();
            if (showOverviewActions(context)) {
                //TODO: this needs to account for the swipe gesture height and accessibility
                // UI when shown.
                float actionsBottomMargin = 0;
                if (!dp.isVerticalBarLayout()) {
                    if (getMode(context) == Mode.THREE_BUTTONS) {
                        actionsBottomMargin = res.getDimensionPixelSize(
                            R.dimen.overview_actions_bottom_margin_three_button);
                    } else {
                        actionsBottomMargin = res.getDimensionPixelSize(
                            R.dimen.overview_actions_bottom_margin_gesture);
                    }
                }
                float actionsHeight = actionsBottomMargin
                        + res.getDimensionPixelSize(R.dimen.overview_actions_height);
                return actionsHeight;
            } else {
                return getDefaultSwipeHeight(context, dp) + dp.workspacePageIndicatorHeight
                        + res.getDimensionPixelSize(
                        R.dimen.dynamic_grid_hotseat_extra_vertical_size)
                        + res.getDimensionPixelSize(
                        R.dimen.dynamic_grid_hotseat_bottom_padding);
            }
        }
    }

}
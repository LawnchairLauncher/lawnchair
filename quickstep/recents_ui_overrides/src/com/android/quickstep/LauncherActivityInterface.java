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

import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.anim.Interpolators.ACCEL_2;
import static com.android.launcher3.anim.Interpolators.INSTANT;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.uioverrides.states.QuickstepAtomicAnimationFactory.INDEX_RECENTS_FADE_ANIM;
import static com.android.launcher3.uioverrides.states.QuickstepAtomicAnimationFactory.INDEX_RECENTS_TRANSLATE_X_ANIM;
import static com.android.launcher3.uioverrides.states.QuickstepAtomicAnimationFactory.INDEX_SHELF_ANIM;
import static com.android.quickstep.LauncherSwipeHandler.RECENTS_ATTACH_DURATION;
import static com.android.quickstep.util.WindowSizeStrategy.LAUNCHER_ACTIVITY_SIZE_STRATEGY;
import static com.android.quickstep.views.RecentsView.ADJACENT_PAGE_OFFSET;
import static com.android.quickstep.views.RecentsView.FULLSCREEN_PROGRESS;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;
import android.view.animation.Interpolator;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherInitListener;
import com.android.launcher3.LauncherState;
import com.android.launcher3.allapps.DiscoveryBounce;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.appprediction.PredictionUiStateManager;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statehandlers.DepthController.ClampedDepthProperty;
import com.android.launcher3.testing.TestProtocol;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.util.ActivityInitListener;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.util.ShelfPeekAnim;
import com.android.quickstep.util.ShelfPeekAnim.ShelfAnimState;
import com.android.quickstep.views.LauncherRecentsView;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.plugins.shared.LauncherOverlayManager;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * {@link BaseActivityInterface} for the in-launcher recents.
 */
public final class LauncherActivityInterface implements BaseActivityInterface<Launcher> {

    @Override
    public int getSwipeUpDestinationAndLength(DeviceProfile dp, Context context, Rect outRect) {
        LAUNCHER_ACTIVITY_SIZE_STRATEGY.calculateTaskSize(context, dp, outRect);
        if (dp.isVerticalBarLayout() && SysUINavigationMode.getMode(context) != Mode.NO_BUTTON) {
            Rect targetInsets = dp.getInsets();
            int hotseatInset = dp.isSeascape() ? targetInsets.left : targetInsets.right;
            return dp.hotseatBarSizePx + hotseatInset;
        } else {
            return LayoutUtils.getShelfTrackingDistance(context, dp);
        }
    }

    @Override
    public void onTransitionCancelled(boolean activityVisible) {
        Launcher launcher = getCreatedActivity();
        if (launcher == null) {
            return;
        }
        LauncherState startState = launcher.getStateManager().getRestState();
        launcher.getStateManager().goToState(startState, activityVisible);
    }

    @Override
    public void onSwipeUpToRecentsComplete() {
        // Re apply state in case we did something funky during the transition.
        Launcher launcher = getCreatedActivity();
        if (launcher == null) {
            return;
        }
        launcher.getStateManager().reapplyState();
        DiscoveryBounce.showForOverviewIfNeeded(launcher);
    }

    @Override
    public void onSwipeUpToHomeComplete() {
        Launcher launcher = getCreatedActivity();
        if (launcher == null) {
            return;
        }
        // Ensure recents is at the correct position for NORMAL state. For example, when we detach
        // recents, we assume the first task is invisible, making translation off by one task.
        launcher.getStateManager().reapplyState();
        setLauncherHideBackArrow(false);
    }

    private void setLauncherHideBackArrow(boolean hideBackArrow) {
        Launcher launcher = getCreatedActivity();
        if (launcher == null) {
            return;
        }
        launcher.getRootView().setForceHideBackArrow(hideBackArrow);
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
    public AnimationFactory prepareRecentsUI(
            boolean activityVisible, Consumer<AnimatorPlaybackController> callback) {
        BaseQuickstepLauncher launcher = getCreatedActivity();
        final LauncherState startState = launcher.getStateManager().getState();

        LauncherState resetState = startState;
        if (startState.shouldDisableRestore()) {
            resetState = launcher.getStateManager().getRestState();
        }
        launcher.getStateManager().setRestState(resetState);

        launcher.getStateManager().goToState(BACKGROUND_APP, false);
        // Since all apps is not visible, we can safely reset the scroll position.
        // This ensures then the next swipe up to all-apps starts from scroll 0.
        launcher.getAppsView().reset(false /* animate */);

        return new AnimationFactory() {
            private final ShelfPeekAnim mShelfAnim = launcher.getShelfPeekAnim();
            private boolean mIsAttachedToWindow;

            @Override
            public void createActivityInterface(long transitionLength) {
                callback.accept(createBackgroundToOverviewAnim(launcher, transitionLength));
                // Creating the activity controller animation sometimes reapplies the launcher state
                // (because we set the animation as the current state animation), so we reapply the
                // attached state here as well to ensure recents is shown/hidden appropriately.
                if (SysUINavigationMode.getMode(launcher) == Mode.NO_BUTTON) {
                    setRecentsAttachedToAppWindow(mIsAttachedToWindow, false);
                }
            }

            @Override
            public void onTransitionCancelled() {
                launcher.getStateManager().goToState(startState, false /* animate */);
            }

            @Override
            public void setShelfState(ShelfAnimState shelfState, Interpolator interpolator,
                    long duration) {
                mShelfAnim.setShelfState(shelfState, interpolator, duration);
            }

            @Override
            public void setRecentsAttachedToAppWindow(boolean attached, boolean animate) {
                if (mIsAttachedToWindow == attached && animate) {
                    return;
                }
                mIsAttachedToWindow = attached;
                LauncherRecentsView recentsView = launcher.getOverviewPanel();
                Animator fadeAnim = launcher.getStateManager()
                        .createStateElementAnimation(
                        INDEX_RECENTS_FADE_ANIM, attached ? 1 : 0);

                float fromTranslation = attached ? 1 : 0;
                float toTranslation = attached ? 0 : 1;
                launcher.getStateManager()
                        .cancelStateElementAnimation(INDEX_RECENTS_TRANSLATE_X_ANIM);
                if (!recentsView.isShown() && animate) {
                    ADJACENT_PAGE_OFFSET.set(recentsView, fromTranslation);
                } else {
                    fromTranslation = ADJACENT_PAGE_OFFSET.get(recentsView);
                }
                if (!animate) {
                    ADJACENT_PAGE_OFFSET.set(recentsView, toTranslation);
                } else {
                    launcher.getStateManager().createStateElementAnimation(
                            INDEX_RECENTS_TRANSLATE_X_ANIM,
                            fromTranslation, toTranslation).start();
                }

                fadeAnim.setInterpolator(attached ? INSTANT : ACCEL_2);
                fadeAnim.setDuration(animate ? RECENTS_ATTACH_DURATION : 0).start();
            }
        };
    }

    private AnimatorPlaybackController createBackgroundToOverviewAnim(
            Launcher activity, long transitionLength) {

        PendingAnimation pa = new PendingAnimation(transitionLength * 2);

        if (!activity.getDeviceProfile().isVerticalBarLayout()
                && SysUINavigationMode.getMode(activity) != Mode.NO_BUTTON) {
            // Don't animate the shelf when the mode is NO_BUTTON, because we update it atomically.
            pa.add(activity.getStateManager().createStateElementAnimation(
                    INDEX_SHELF_ANIM,
                    BACKGROUND_APP.getVerticalProgress(activity),
                    OVERVIEW.getVerticalProgress(activity)));
        }

        // Animate the blur and wallpaper zoom
        float fromDepthRatio = BACKGROUND_APP.getDepth(activity);
        float toDepthRatio = OVERVIEW.getDepth(activity);
        pa.addFloat(getDepthController(), new ClampedDepthProperty(fromDepthRatio, toDepthRatio),
                fromDepthRatio, toDepthRatio, LINEAR);


        //  Scale down recents from being full screen to being in overview.
        RecentsView recentsView = activity.getOverviewPanel();
        pa.addFloat(recentsView, SCALE_PROPERTY,
                BACKGROUND_APP.getOverviewScaleAndOffset(activity)[0],
                OVERVIEW.getOverviewScaleAndOffset(activity)[0],
                LINEAR);
        pa.addFloat(recentsView, FULLSCREEN_PROGRESS,
                BACKGROUND_APP.getOverviewFullscreenProgress(),
                OVERVIEW.getOverviewFullscreenProgress(),
                LINEAR);

        AnimatorPlaybackController controller = pa.createPlaybackController();
        activity.getStateManager().setCurrentUserControlledAnimation(controller);

        // Since we are changing the start position of the UI, reapply the state, at the end
        controller.setEndAction(() -> activity.getStateManager().goToState(
                controller.getInterpolatedProgress() > 0.5 ? OVERVIEW : BACKGROUND_APP, false));
        return controller;
    }

    @Override
    public ActivityInitListener createActivityInitListener(Predicate<Boolean> onInitListener) {
        return new LauncherInitListener((activity, alreadyOnHome) ->
                onInitListener.test(alreadyOnHome));
    }

    @Nullable
    @Override
    public BaseQuickstepLauncher getCreatedActivity() {
        return BaseQuickstepLauncher.ACTIVITY_TRACKER.getCreatedActivity();
    }

    @Nullable
    @UiThread
    private Launcher getVisibleLauncher() {
        Launcher launcher = getCreatedActivity();
        return (launcher != null) && launcher.isStarted() && launcher.hasWindowFocus() ?
                launcher : null;
    }

    @Nullable
    @Override
    public RecentsView getVisibleRecentsView() {
        Launcher launcher = getVisibleLauncher();
        return launcher != null && launcher.getStateManager().getState().overviewUi
                ? launcher.getOverviewPanel() : null;
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
    public boolean deferStartingActivity(RecentsAnimationDeviceState deviceState, MotionEvent ev) {
        return deviceState.isInDeferredGestureRegion(ev);
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
    public void onLaunchTaskSuccess() {
        Launcher launcher = getCreatedActivity();
        if (launcher == null) {
            return;
        }
        launcher.getStateManager().moveToRestState();
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
    public void switchRunningTaskViewToScreenshot(ThumbnailData thumbnailData,
            Runnable onFinishRunnable) {
        Launcher launcher = getCreatedActivity();
        if (launcher == null) {
            return;
        }
        RecentsView recentsView = launcher.getOverviewPanel();
        if (recentsView == null) {
            if (onFinishRunnable != null) {
                onFinishRunnable.run();
            }
            return;
        }
        recentsView.switchToScreenshot(thumbnailData, onFinishRunnable);
    }

    @Override
    public void setOnDeferredActivityLaunchCallback(Runnable r) {
        Launcher launcher = getCreatedActivity();
        if (launcher == null) {
            return;
        }
        launcher.setOnDeferredActivityLaunchCallback(r);
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

    @Nullable
    @Override
    public DepthController getDepthController() {
        BaseQuickstepLauncher launcher = getCreatedActivity();
        if (launcher == null) {
            return null;
        }
        return launcher.getDepthController();
    }
}
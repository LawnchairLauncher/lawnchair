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

import static android.view.View.TRANSLATION_Y;

import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherAppTransitionManagerImpl.INDEX_RECENTS_FADE_ANIM;
import static com.android.launcher3.LauncherAppTransitionManagerImpl.INDEX_RECENTS_TRANSLATE_X_ANIM;
import static com.android.launcher3.LauncherAppTransitionManagerImpl.INDEX_SHELF_ANIM;
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.LauncherStateManager.ANIM_ALL;
import static com.android.launcher3.anim.Interpolators.ACCEL_2;
import static com.android.launcher3.anim.Interpolators.ACCEL_DEACCEL;
import static com.android.launcher3.anim.Interpolators.INSTANT;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.quickstep.WindowTransformSwipeHandler.RECENTS_ATTACH_DURATION;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.UserHandle;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherInitListenerEx;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager;
import com.android.launcher3.allapps.DiscoveryBounce;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.uioverrides.states.OverviewState;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.views.FloatingIconView;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.util.StaggeredWorkspaceAnim;
import com.android.quickstep.views.LauncherRecentsView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * {@link ActivityControlHelper} for the in-launcher recents.
 */
public final class LauncherActivityControllerHelper implements ActivityControlHelper<Launcher> {

    private Runnable mAdjustInterpolatorsRunnable;

    @Override
    public int getSwipeUpDestinationAndLength(DeviceProfile dp, Context context, Rect outRect) {
        LayoutUtils.calculateLauncherTaskSize(context, dp, outRect);
        if (dp.isVerticalBarLayout() && SysUINavigationMode.getMode(context) != Mode.NO_BUTTON) {
            Rect targetInsets = dp.getInsets();
            int hotseatInset = dp.isSeascape() ? targetInsets.left : targetInsets.right;
            return dp.hotseatBarSizePx + hotseatInset;
        } else {
            return LayoutUtils.getShelfTrackingDistance(context, dp);
        }
    }

    @Override
    public void onTransitionCancelled(Launcher activity, boolean activityVisible) {
        LauncherState startState = activity.getStateManager().getRestState();
        activity.getStateManager().goToState(startState, activityVisible);
    }

    @Override
    public void onSwipeUpToRecentsComplete(Launcher activity) {
        // Re apply state in case we did something funky during the transition.
        activity.getStateManager().reapplyState();
        DiscoveryBounce.showForOverviewIfNeeded(activity);
    }

    @Override
    public void onSwipeUpToHomeComplete(Launcher activity) {
        // Ensure recents is at the correct position for NORMAL state. For example, when we detach
        // recents, we assume the first task is invisible, making translation off by one task.
        activity.getStateManager().reapplyState();
    }

    @Override
    public void onAssistantVisibilityChanged(float visibility) {
        Launcher launcher = getCreatedActivity();
        if (launcher != null) {
            launcher.onAssistantVisibilityChanged(visibility);
        }
    }

    @NonNull
    @Override
    public HomeAnimationFactory prepareHomeUI(Launcher activity) {
        final DeviceProfile dp = activity.getDeviceProfile();
        final RecentsView recentsView = activity.getOverviewPanel();
        final TaskView runningTaskView = recentsView.getRunningTaskView();
        final View workspaceView;
        if (runningTaskView != null && runningTaskView.getTask().key.getComponent() != null) {
            workspaceView = activity.getWorkspace().getFirstMatchForAppClose(
                    runningTaskView.getTask().key.getComponent().getPackageName(),
                    UserHandle.of(runningTaskView.getTask().key.userId));
        } else {
            workspaceView = null;
        }
        final RectF iconLocation = new RectF();
        boolean canUseWorkspaceView = workspaceView != null && workspaceView.isAttachedToWindow();
        FloatingIconView floatingIconView = canUseWorkspaceView
                ? FloatingIconView.getFloatingIconView(activity, workspaceView,
                        true /* hideOriginal */, iconLocation, false /* isOpening */)
                : null;

        return new HomeAnimationFactory() {
            @Nullable
            @Override
            public View getFloatingView() {
                return floatingIconView;
            }

            @NonNull
            @Override
            public RectF getWindowTargetRect() {
                if (canUseWorkspaceView) {
                    return iconLocation;
                } else {
                    return HomeAnimationFactory.getDefaultWindowTargetRect(dp);
                }
            }

            @NonNull
            @Override
            public AnimatorPlaybackController createActivityAnimationToHome() {
                // Return an empty APC here since we have an non-user controlled animation to home.
                long accuracy = 2 * Math.max(dp.widthPx, dp.heightPx);
                return activity.getStateManager().createAnimationToNewWorkspace(NORMAL, accuracy,
                        0 /* animComponents */);
            }

            @Override
            public void playAtomicAnimation(float velocity) {
                // Setup workspace with 0 duration to prepare for our staggered animation.
                LauncherStateManager stateManager = activity.getStateManager();
                AnimatorSetBuilder builder = new AnimatorSetBuilder();
                // setRecentsAttachedToAppWindow() will animate recents out.
                builder.addFlag(AnimatorSetBuilder.FLAG_DONT_ANIMATE_OVERVIEW);
                stateManager.createAtomicAnimation(BACKGROUND_APP, NORMAL, builder, ANIM_ALL, 0);
                builder.build().start();

                // Stop scrolling so that it doesn't interfere with the translation offscreen.
                recentsView.getScroller().forceFinished(true);

                new StaggeredWorkspaceAnim(activity, workspaceView, velocity).start();
            }
        };
    }

    @Override
    public AnimationFactory prepareRecentsUI(Launcher activity, boolean activityVisible,
            boolean animateActivity, Consumer<AnimatorPlaybackController> callback) {
        final LauncherState startState = activity.getStateManager().getState();

        LauncherState resetState = startState;
        if (startState.disableRestore) {
            resetState = activity.getStateManager().getRestState();
        }
        activity.getStateManager().setRestState(resetState);

        final LauncherState fromState = animateActivity ? BACKGROUND_APP : OVERVIEW;
        activity.getStateManager().goToState(fromState, false);
        // Since all apps is not visible, we can safely reset the scroll position.
        // This ensures then the next swipe up to all-apps starts from scroll 0.
        activity.getAppsView().reset(false /* animate */);

        return new AnimationFactory() {
            private ShelfAnimState mShelfState;
            private boolean mIsAttachedToWindow;

            @Override
            public void createActivityController(long transitionLength) {
                createActivityControllerInternal(activity, fromState, transitionLength, callback);
                // Creating the activity controller animation sometimes reapplies the launcher state
                // (because we set the animation as the current state animation), so we reapply the
                // attached state here as well to ensure recents is shown/hidden appropriately.
                if (SysUINavigationMode.getMode(activity) == Mode.NO_BUTTON) {
                    setRecentsAttachedToAppWindow(mIsAttachedToWindow, false);
                }
            }

            @Override
            public void adjustActivityControllerInterpolators() {
                if (mAdjustInterpolatorsRunnable != null) {
                    mAdjustInterpolatorsRunnable.run();
                }
            }

            @Override
            public void onTransitionCancelled() {
                activity.getStateManager().goToState(startState, false /* animate */);
            }

            @Override
            public void setShelfState(ShelfAnimState shelfState, Interpolator interpolator,
                    long duration) {
                if (mShelfState == shelfState) {
                    return;
                }
                mShelfState = shelfState;
                activity.getStateManager().cancelStateElementAnimation(INDEX_SHELF_ANIM);
                if (mShelfState == ShelfAnimState.CANCEL) {
                    return;
                }
                float shelfHiddenProgress = BACKGROUND_APP.getVerticalProgress(activity);
                float shelfOverviewProgress = OVERVIEW.getVerticalProgress(activity);
                // Peek based on default overview progress so we can see hotseat if we're showing
                // that instead of predictions in overview.
                float defaultOverviewProgress = OverviewState.getDefaultVerticalProgress(activity);
                float shelfPeekingProgress = shelfHiddenProgress
                        - (shelfHiddenProgress - defaultOverviewProgress) * 0.25f;
                float toProgress = mShelfState == ShelfAnimState.HIDE
                        ? shelfHiddenProgress
                        : mShelfState == ShelfAnimState.PEEK
                                ? shelfPeekingProgress
                                : shelfOverviewProgress;
                Animator shelfAnim = activity.getStateManager()
                        .createStateElementAnimation(INDEX_SHELF_ANIM, toProgress);
                shelfAnim.setInterpolator(interpolator);
                shelfAnim.setDuration(duration).start();
            }

            @Override
            public void setRecentsAttachedToAppWindow(boolean attached, boolean animate) {
                if (mIsAttachedToWindow == attached && animate) {
                    return;
                }
                mIsAttachedToWindow = attached;
                LauncherRecentsView recentsView = activity.getOverviewPanel();
                Animator fadeAnim = activity.getStateManager()
                        .createStateElementAnimation(
                        INDEX_RECENTS_FADE_ANIM, attached ? 1 : 0);

                int runningTaskIndex = recentsView.getRunningTaskIndex();
                if (runningTaskIndex == 0) {
                    // If we are on the first task (we haven't quick switched), translate recents in
                    // from the side. Calculate the start translation based on current scale/scroll.
                    float currScale = recentsView.getScaleX();
                    float scrollOffsetX = recentsView.getScrollOffset();

                    float offscreenX = NORMAL.getOverviewScaleAndTranslation(activity).translationX;
                    // The first task is hidden, so offset by its width.
                    int firstTaskWidth = recentsView.getTaskViewAt(0).getWidth();
                    offscreenX -= (firstTaskWidth + recentsView.getPageSpacing()) * currScale;
                    // Offset since scale pushes tasks outwards.
                    offscreenX += firstTaskWidth * (currScale - 1) / 2;
                    offscreenX = Math.max(0, offscreenX);
                    if (recentsView.isRtl()) {
                        offscreenX = -offscreenX;
                    }

                    float fromTranslationX = attached ? offscreenX - scrollOffsetX : 0;
                    float toTranslationX = attached ? 0 : offscreenX - scrollOffsetX;
                    activity.getStateManager()
                            .cancelStateElementAnimation(INDEX_RECENTS_TRANSLATE_X_ANIM);

                    if (!recentsView.isShown() && animate) {
                        recentsView.setTranslationX(fromTranslationX);
                    } else {
                        fromTranslationX = recentsView.getTranslationX();
                    }

                    if (!animate) {
                        recentsView.setTranslationX(toTranslationX);
                    } else {
                        activity.getStateManager().createStateElementAnimation(
                                INDEX_RECENTS_TRANSLATE_X_ANIM,
                                fromTranslationX, toTranslationX).start();
                    }

                    fadeAnim.setInterpolator(attached ? INSTANT : ACCEL_2);
                } else {
                    fadeAnim.setInterpolator(ACCEL_DEACCEL);
                }
                fadeAnim.setDuration(animate ? RECENTS_ATTACH_DURATION : 0).start();
            }
        };
    }

    private void createActivityControllerInternal(Launcher activity, LauncherState fromState,
            long transitionLength, Consumer<AnimatorPlaybackController> callback) {
        LauncherState endState = OVERVIEW;
        if (fromState == endState) {
            return;
        }

        AnimatorSet anim = new AnimatorSet();
        if (!activity.getDeviceProfile().isVerticalBarLayout()
                && SysUINavigationMode.getMode(activity) != Mode.NO_BUTTON) {
            // Don't animate the shelf when the mode is NO_BUTTON, because we update it atomically.
            anim.play(activity.getStateManager().createStateElementAnimation(
                    INDEX_SHELF_ANIM,
                    fromState.getVerticalProgress(activity),
                    endState.getVerticalProgress(activity)));
        }
        playScaleDownAnim(anim, activity, fromState, endState);

        anim.setDuration(transitionLength * 2);
        anim.setInterpolator(LINEAR);
        AnimatorPlaybackController controller =
                AnimatorPlaybackController.wrap(anim, transitionLength * 2);
        activity.getStateManager().setCurrentUserControlledAnimation(controller);

        // Since we are changing the start position of the UI, reapply the state, at the end
        controller.setEndAction(() -> {
            activity.getStateManager().goToState(
                    controller.getInterpolatedProgress() > 0.5 ? endState : fromState, false);
        });
        callback.accept(controller);
    }

    /**
     * Scale down recents from the center task being full screen to being in overview.
     */
    private void playScaleDownAnim(AnimatorSet anim, Launcher launcher, LauncherState fromState,
            LauncherState endState) {
        RecentsView recentsView = launcher.getOverviewPanel();
        TaskView v = recentsView.getTaskViewAt(recentsView.getCurrentPage());
        if (v == null) {
            return;
        }

        LauncherState.ScaleAndTranslation fromScaleAndTranslation
                = fromState.getOverviewScaleAndTranslation(launcher);
        LauncherState.ScaleAndTranslation endScaleAndTranslation
                = endState.getOverviewScaleAndTranslation(launcher);
        float fromTranslationY = fromScaleAndTranslation.translationY;
        float endTranslationY = endScaleAndTranslation.translationY;
        float fromFullscreenProgress = fromState.getOverviewFullscreenProgress();
        float endFullscreenProgress = endState.getOverviewFullscreenProgress();

        Animator scale = ObjectAnimator.ofFloat(recentsView, SCALE_PROPERTY,
                fromScaleAndTranslation.scale, endScaleAndTranslation.scale);
        Animator translateY = ObjectAnimator.ofFloat(recentsView, TRANSLATION_Y,
                fromTranslationY, endTranslationY);
        Animator applyFullscreenProgress = ObjectAnimator.ofFloat(recentsView,
                RecentsView.FULLSCREEN_PROGRESS, fromFullscreenProgress, endFullscreenProgress);
        anim.playTogether(scale, translateY, applyFullscreenProgress);

        mAdjustInterpolatorsRunnable = () -> {
            // Adjust the translateY interpolator to account for the running task's top inset.
            // When progress <= 1, this is handled by each task view as they set their fullscreen
            // progress. However, once we go to progress > 1, fullscreen progress stays at 0, so
            // recents as a whole needs to translate further to keep up with the app window.
            TaskView runningTaskView = recentsView.getRunningTaskView();
            if (runningTaskView == null) {
                runningTaskView = recentsView.getTaskViewAt(recentsView.getCurrentPage());
                if (runningTaskView == null) {
                    // There are no task views in LockTask mode when Overview is enabled.
                    return;
                }
            }
            TimeInterpolator oldInterpolator = translateY.getInterpolator();
            Rect fallbackInsets = launcher.getDeviceProfile().getInsets();
            float extraTranslationY = runningTaskView.getThumbnail().getInsets(fallbackInsets).top;
            float normalizedTranslationY = extraTranslationY / (fromTranslationY - endTranslationY);
            translateY.setInterpolator(t -> {
                float newT = oldInterpolator.getInterpolation(t);
                return newT <= 1f ? newT : newT + normalizedTranslationY * (newT - 1);
            });
        };
    }

    @Override
    public ActivityInitListener createActivityInitListener(
            BiPredicate<Launcher, Boolean> onInitListener) {
        return new LauncherInitListenerEx(onInitListener);
    }

    @Nullable
    @Override
    public Launcher getCreatedActivity() {
        LauncherAppState app = LauncherAppState.getInstanceNoCreate();
        if (app == null) {
            return null;
        }
        return (Launcher) app.getModel().getCallback();
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
    public boolean deferStartingActivity(Region activeNavBarRegion, MotionEvent ev) {
        return activeNavBarRegion.contains((int) ev.getX(), (int) ev.getY());
    }

    @Override
    public Rect getOverviewWindowBounds(Rect homeBounds, RemoteAnimationTargetCompat target) {
        return homeBounds;
    }

    @Override
    public boolean shouldMinimizeSplitScreen() {
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
    public void onLaunchTaskFailed(Launcher launcher) {
        launcher.getStateManager().goToState(OVERVIEW);
    }

    @Override
    public void onLaunchTaskSuccess(Launcher launcher) {
        launcher.getStateManager().moveToRestState();
    }
}
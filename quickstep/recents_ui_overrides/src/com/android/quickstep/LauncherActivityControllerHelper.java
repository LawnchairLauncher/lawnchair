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
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.allapps.AllAppsTransitionController.SPRING_DAMPING_RATIO;
import static com.android.launcher3.allapps.AllAppsTransitionController.SPRING_STIFFNESS;
import static com.android.launcher3.anim.Interpolators.DEACCEL_3;
import static com.android.launcher3.anim.Interpolators.LINEAR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
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
import com.android.launcher3.LauncherInitListener;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.allapps.DiscoveryBounce;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.SpringObjectAnimator;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.views.FloatingIconView;
import com.android.quickstep.util.ClipAnimationHelper;
import com.android.quickstep.util.LayoutUtils;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * {@link ActivityControlHelper} for the in-launcher recents.
 */
public final class LauncherActivityControllerHelper implements ActivityControlHelper<Launcher> {

    @Override
    public int getSwipeUpDestinationAndLength(DeviceProfile dp, Context context, Rect outRect) {
        LayoutUtils.calculateLauncherTaskSize(context, dp, outRect);
        if (dp.isVerticalBarLayout()) {
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
    public void onSwipeUpComplete(Launcher activity) {
        // Re apply state in case we did something funky during the transition.
        activity.getStateManager().reapplyState();
        DiscoveryBounce.showForOverviewIfNeeded(activity);
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
        final Rect iconLocation = new Rect();
        final FloatingIconView floatingView = workspaceView == null ? null
                : FloatingIconView.getFloatingIconView(activity, workspaceView,
                true /* hideOriginal */, false /* useDrawableAsIs */,
                activity.getDeviceProfile().getAspectRatioWithInsets(), iconLocation, null);

        return new HomeAnimationFactory() {
            @Nullable
            @Override
            public View getFloatingView() {
                return floatingView;
            }

            @NonNull
            @Override
            public RectF getWindowTargetRect() {
                final int halfIconSize = dp.iconSizePx / 2;
                final float targetCenterX = dp.availableWidthPx / 2f;
                final float targetCenterY = dp.availableHeightPx - dp.hotseatBarSizePx;

                if (workspaceView != null) {
                    return new RectF(iconLocation);
                } else {
                    // Fallback to animate to center of screen.
                    return new RectF(targetCenterX - halfIconSize, targetCenterY - halfIconSize,
                            targetCenterX + halfIconSize, targetCenterY + halfIconSize);
                }
            }

            @NonNull
            @Override
            public AnimatorPlaybackController createActivityAnimationToHome() {
                long accuracy = 2 * Math.max(dp.widthPx, dp.heightPx);
                return activity.getStateManager().createAnimationToNewWorkspace(NORMAL, accuracy);
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

        final LauncherState fromState;
        if (!activityVisible) {
            // Since the launcher is not visible, we can safely reset the scroll position.
            // This ensures then the next swipe up to all-apps starts from scroll 0.
            activity.getAppsView().reset(false /* animate */);
            fromState = animateActivity ? BACKGROUND_APP : OVERVIEW;
            activity.getStateManager().goToState(fromState, false);

            // Optimization, hide the all apps view to prevent layout while initializing
            activity.getAppsView().getContentView().setVisibility(View.GONE);

            AccessibilityManagerCompat.sendStateEventToTest(activity, fromState.ordinal);
        } else {
            fromState = startState;
        }

        return new AnimationFactory() {
            private Animator mShelfAnim;
            private ShelfAnimState mShelfState;

            @Override
            public void createActivityController(long transitionLength) {
                createActivityControllerInternal(activity, activityVisible, fromState,
                        transitionLength, callback);
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
                if (mShelfAnim != null) {
                    mShelfAnim.cancel();
                }
                if (mShelfState == ShelfAnimState.CANCEL) {
                    return;
                }
                float shelfHiddenProgress = BACKGROUND_APP.getVerticalProgress(activity);
                float shelfOverviewProgress = OVERVIEW.getVerticalProgress(activity);
                float shelfPeekingProgress = shelfHiddenProgress
                        - (shelfHiddenProgress - shelfOverviewProgress) * 0.25f;
                float toProgress = mShelfState == ShelfAnimState.HIDE
                        ? shelfHiddenProgress
                        : mShelfState == ShelfAnimState.PEEK
                                ? shelfPeekingProgress
                                : shelfOverviewProgress;
                mShelfAnim = createShelfProgressAnim(activity, toProgress);
                mShelfAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mShelfAnim = null;
                    }
                });
                mShelfAnim.setInterpolator(interpolator);
                mShelfAnim.setDuration(duration);
                mShelfAnim.start();
            }
        };
    }

    private void createActivityControllerInternal(Launcher activity, boolean wasVisible,
            LauncherState fromState, long transitionLength,
            Consumer<AnimatorPlaybackController> callback) {
        LauncherState endState = OVERVIEW;
        DeviceProfile dp = activity.getDeviceProfile();
        long accuracy = 2 * Math.max(dp.widthPx, dp.heightPx);
        if (wasVisible && fromState != BACKGROUND_APP) {
            // If a translucent app was launched fom launcher, animate launcher states.
            callback.accept(activity.getStateManager()
                    .createAnimationToNewWorkspace(fromState, endState, accuracy));
            return;
        }
        if (fromState == endState) {
            return;
        }

        AnimatorSet anim = new AnimatorSet();
        if (!activity.getDeviceProfile().isVerticalBarLayout()
                && !FeatureFlags.SWIPE_HOME.get()) {
            // Don't animate the shelf when SWIPE_HOME is true, because we update it atomically.
            Animator shiftAnim = createShelfProgressAnim(activity,
                    fromState.getVerticalProgress(activity),
                    endState.getVerticalProgress(activity));
            anim.play(shiftAnim);
            anim.play(createShelfAlphaAnim(activity, endState, accuracy));
        }
        playScaleDownAnim(anim, activity, endState);

        anim.setDuration(transitionLength * 2);
        activity.getStateManager().setCurrentAnimation(anim);
        AnimatorPlaybackController controller =
                AnimatorPlaybackController.wrap(anim, transitionLength * 2);

        // Since we are changing the start position of the UI, reapply the state, at the end
        controller.setEndAction(() -> {
            activity.getStateManager().goToState(
                    controller.getInterpolatedProgress() > 0.5 ? endState : fromState, false);
        });
        callback.accept(controller);
    }

    private Animator createShelfProgressAnim(Launcher activity, float ... progressValues) {
        Animator shiftAnim = new SpringObjectAnimator<>(activity.getAllAppsController(),
                "allAppsSpringFromACH", activity.getAllAppsController().getShiftRange(),
                SPRING_DAMPING_RATIO, SPRING_STIFFNESS, progressValues);
        shiftAnim.setInterpolator(LINEAR);
        return shiftAnim;
    }

    /**
     * Very quickly fade the alpha of shelf content.
     */
    private Animator createShelfAlphaAnim(Launcher activity, LauncherState toState, long accuracy) {
        AllAppsTransitionController allAppsController = activity.getAllAppsController();
        AnimatorSetBuilder animBuilder = new AnimatorSetBuilder();
        animBuilder.setInterpolator(AnimatorSetBuilder.ANIM_ALL_APPS_FADE, DEACCEL_3);
        LauncherStateManager.AnimationConfig config = new LauncherStateManager.AnimationConfig();
        config.duration = accuracy;
        allAppsController.setAlphas(toState.getVisibleElements(activity), config, animBuilder);
        return animBuilder.build();
    }

    /**
     * Scale down recents from the center task being full screen to being in overview.
     */
    private void playScaleDownAnim(AnimatorSet anim, Launcher launcher,
            LauncherState endState) {
        RecentsView recentsView = launcher.getOverviewPanel();
        TaskView v = recentsView.getTaskViewAt(recentsView.getCurrentPage());
        if (v == null) {
            return;
        }

        // Setup the clip animation helper source/target rects in the final transformed state
        // of the recents view (a scale/translationY may be applied prior to this animation
        // starting to line up the side pages during swipe up)
        float prevRvScale = recentsView.getScaleX();
        float prevRvTransY = recentsView.getTranslationY();
        float targetRvScale = endState.getOverviewScaleAndTranslationY(launcher)[0];
        SCALE_PROPERTY.set(recentsView, targetRvScale);
        recentsView.setTranslationY(0);
        ClipAnimationHelper clipHelper = new ClipAnimationHelper(launcher);
        clipHelper.fromTaskThumbnailView(v.getThumbnail(), (RecentsView) v.getParent(), null);
        SCALE_PROPERTY.set(recentsView, prevRvScale);
        recentsView.setTranslationY(prevRvTransY);

        if (!clipHelper.getSourceRect().isEmpty() && !clipHelper.getTargetRect().isEmpty()) {
            float fromScale = clipHelper.getSourceRect().width()
                    / clipHelper.getTargetRect().width();
            float fromTranslationY = clipHelper.getSourceRect().centerY()
                    - clipHelper.getTargetRect().centerY();
            Animator scale = ObjectAnimator.ofFloat(recentsView, SCALE_PROPERTY, fromScale, 1);
            Animator translateY = ObjectAnimator.ofFloat(recentsView, TRANSLATION_Y,
                    fromTranslationY, 0);
            scale.setInterpolator(LINEAR);
            translateY.setInterpolator(LINEAR);
            anim.playTogether(scale, translateY);
        }
    }

    @Override
    public ActivityInitListener createActivityInitListener(
            BiPredicate<Launcher, Boolean> onInitListener) {
        return new LauncherInitListener(onInitListener);
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
}
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

import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_FRONT;

import static com.android.launcher3.LauncherAnimUtils.VIEW_ALPHA;
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.QuickstepTransitionManager.ANIMATION_DELAY_NAV_FADE_IN;
import static com.android.launcher3.QuickstepTransitionManager.ANIMATION_NAV_FADE_IN_DURATION;
import static com.android.launcher3.QuickstepTransitionManager.ANIMATION_NAV_FADE_OUT_DURATION;
import static com.android.launcher3.QuickstepTransitionManager.NAV_FADE_IN_INTERPOLATOR;
import static com.android.launcher3.QuickstepTransitionManager.NAV_FADE_OUT_INTERPOLATOR;
import static com.android.launcher3.QuickstepTransitionManager.RECENTS_LAUNCH_DURATION;
import static com.android.launcher3.Utilities.getDescendantCoordRelativeToAncestor;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.Interpolators.TOUCH_RESPONSE_INTERPOLATOR;
import static com.android.launcher3.anim.Interpolators.TOUCH_RESPONSE_INTERPOLATOR_ACCEL_DEACCEL;
import static com.android.launcher3.anim.Interpolators.clampToProgress;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.launcher3.statehandlers.DepthController.DEPTH;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_CLOSING;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_OPENING;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.view.SurfaceControl;
import android.view.View;
import android.window.TransitionInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.util.MultiValueUpdateListener;
import com.android.quickstep.util.SurfaceTransactionApplier;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskThumbnailView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams;

/**
 * Utility class for helpful methods related to {@link TaskView} objects and their tasks.
 */
@TargetApi(Build.VERSION_CODES.R)
public final class TaskViewUtils {

    private TaskViewUtils() {}

    /**
     * Try to find a TaskView that corresponds with the component of the launched view.
     *
     * If this method returns a non-null TaskView, it will be used in composeRecentsLaunchAnimation.
     * Otherwise, we will assume we are using a normal app transition, but it's possible that the
     * opening remote target (which we don't get until onAnimationStart) will resolve to a TaskView.
     */
    public static TaskView findTaskViewToLaunch(
            RecentsView recentsView, View v, RemoteAnimationTargetCompat[] targets) {
        if (v instanceof TaskView) {
            TaskView taskView = (TaskView) v;
            return recentsView.isTaskViewVisible(taskView) ? taskView : null;
        }

        // It's possible that the launched view can still be resolved to a visible task view, check
        // the task id of the opening task and see if we can find a match.
        if (v.getTag() instanceof ItemInfo) {
            ItemInfo itemInfo = (ItemInfo) v.getTag();
            ComponentName componentName = itemInfo.getTargetComponent();
            int userId = itemInfo.user.getIdentifier();
            if (componentName != null) {
                for (int i = 0; i < recentsView.getTaskViewCount(); i++) {
                    TaskView taskView = recentsView.getTaskViewAt(i);
                    if (recentsView.isTaskViewVisible(taskView)) {
                        Task.TaskKey key = taskView.getTask().key;
                        if (componentName.equals(key.getComponent()) && userId == key.userId) {
                            return taskView;
                        }
                    }
                }
            }
        }

        if (targets == null) {
            return null;
        }
        // Resolve the opening task id
        int openingTaskId = -1;
        for (RemoteAnimationTargetCompat target : targets) {
            if (target.mode == MODE_OPENING) {
                openingTaskId = target.taskId;
                break;
            }
        }

        // If there is no opening task id, fall back to the normal app icon launch animation
        if (openingTaskId == -1) {
            return null;
        }

        // If the opening task id is not currently visible in overview, then fall back to normal app
        // icon launch animation
        TaskView taskView = recentsView.getTaskView(openingTaskId);
        if (taskView == null || !recentsView.isTaskViewVisible(taskView)) {
            return null;
        }
        return taskView;
    }

    public static void createRecentsWindowAnimator(TaskView v, boolean skipViewChanges,
            RemoteAnimationTargetCompat[] appTargets,
            RemoteAnimationTargetCompat[] wallpaperTargets,
            RemoteAnimationTargetCompat[] nonAppTargets, DepthController depthController,
            PendingAnimation out) {
        boolean isRunningTask = v.isRunningTask();
        TransformParams params = null;
        TaskViewSimulator tsv = null;
        if (ENABLE_QUICKSTEP_LIVE_TILE.get() && isRunningTask) {
            params = v.getRecentsView().getLiveTileParams();
            tsv = v.getRecentsView().getLiveTileTaskViewSimulator();
        }
        createRecentsWindowAnimator(v, skipViewChanges, appTargets, wallpaperTargets, nonAppTargets,
                depthController, out, params, tsv);
    }

    /**
     * Creates an animation that controls the window of the opening targets for the recents launch
     * animation.
     */
    public static void createRecentsWindowAnimator(TaskView v, boolean skipViewChanges,
            RemoteAnimationTargetCompat[] appTargets,
            RemoteAnimationTargetCompat[] wallpaperTargets,
            RemoteAnimationTargetCompat[] nonAppTargets, DepthController depthController,
            PendingAnimation out, @Nullable TransformParams params,
            @Nullable TaskViewSimulator tsv) {
        boolean isQuickSwitch = v.isEndQuickswitchCuj();
        v.setEndQuickswitchCuj(false);

        boolean inLiveTileMode =
                ENABLE_QUICKSTEP_LIVE_TILE.get() && v.getRecentsView().getRunningTaskIndex() != -1;
        final RemoteAnimationTargets targets =
                new RemoteAnimationTargets(appTargets, wallpaperTargets, nonAppTargets,
                        inLiveTileMode ? MODE_CLOSING : MODE_OPENING);
        final RemoteAnimationTargetCompat navBarTarget = targets.getNavBarRemoteAnimationTarget();

        if (params == null) {
            SurfaceTransactionApplier applier = new SurfaceTransactionApplier(v);
            targets.addReleaseCheck(applier);

            params = new TransformParams()
                    .setSyncTransactionApplier(applier)
                    .setTargetSet(targets);
        }

        final RecentsView recentsView = v.getRecentsView();
        int taskIndex = recentsView.indexOfChild(v);
        Context context = v.getContext();
        DeviceProfile dp = BaseActivity.fromContext(context).getDeviceProfile();
        boolean showAsGrid = dp.isTablet && FeatureFlags.ENABLE_OVERVIEW_GRID.get();
        boolean parallaxCenterAndAdjacentTask =
                taskIndex != recentsView.getCurrentPage() && !showAsGrid;
        float gridTranslationSecondary = recentsView.getGridTranslationSecondary(taskIndex);
        int startScroll = recentsView.getScrollOffset(taskIndex);

        TaskViewSimulator topMostSimulator = null;

        if (tsv == null && targets.apps.length > 0) {
            tsv = new TaskViewSimulator(context, recentsView.getSizeStrategy());
            tsv.setDp(dp);

            // RecentsView never updates the display rotation until swipe-up so the value may
            // be stale. Use the display value instead.
            int displayRotation = DisplayController.INSTANCE.get(context).getInfo().rotation;
            tsv.getOrientationState().update(displayRotation, displayRotation);

            tsv.setPreview(targets.apps[targets.apps.length - 1]);
            tsv.fullScreenProgress.value = 0;
            tsv.recentsViewScale.value = 1;
            if (showAsGrid) {
                tsv.taskSecondaryTranslation.value = gridTranslationSecondary;
            }
            tsv.setScroll(startScroll);

            // Fade in the task during the initial 20% of the animation
            out.addFloat(params, TransformParams.TARGET_ALPHA, 0, 1,
                    clampToProgress(LINEAR, 0, 0.2f));
        }

        if (tsv != null) {
            out.setFloat(tsv.fullScreenProgress,
                    AnimatedFloat.VALUE, 1, TOUCH_RESPONSE_INTERPOLATOR);
            out.setFloat(tsv.recentsViewScale,
                    AnimatedFloat.VALUE, tsv.getFullScreenScale(), TOUCH_RESPONSE_INTERPOLATOR);
            if (showAsGrid) {
                out.setFloat(tsv.taskSecondaryTranslation, AnimatedFloat.VALUE, 0,
                        TOUCH_RESPONSE_INTERPOLATOR_ACCEL_DEACCEL);
            }
            out.setFloat(tsv.recentsViewScroll, AnimatedFloat.VALUE, 0,
                    TOUCH_RESPONSE_INTERPOLATOR);

            TaskViewSimulator finalTsv = tsv;
            TransformParams finalParams = params;
            out.addOnFrameCallback(() -> finalTsv.apply(finalParams));
            if (navBarTarget != null) {
                final Rect cropRect = new Rect();
                out.addOnFrameListener(new MultiValueUpdateListener() {
                    FloatProp mNavFadeOut = new FloatProp(1f, 0f, 0,
                            ANIMATION_NAV_FADE_OUT_DURATION, NAV_FADE_OUT_INTERPOLATOR);
                    FloatProp mNavFadeIn = new FloatProp(0f, 1f, ANIMATION_DELAY_NAV_FADE_IN,
                            ANIMATION_NAV_FADE_IN_DURATION, NAV_FADE_IN_INTERPOLATOR);

                    @Override
                    public void onUpdate(float percent, boolean initOnly) {
                        final SurfaceParams.Builder navBuilder =
                                new SurfaceParams.Builder(navBarTarget.leash);
                        if (mNavFadeIn.value > mNavFadeIn.getStartValue()) {
                            finalTsv.getCurrentCropRect().round(cropRect);
                            navBuilder.withMatrix(finalTsv.getCurrentMatrix())
                                    .withWindowCrop(cropRect)
                                    .withAlpha(mNavFadeIn.value);
                        } else {
                            navBuilder.withAlpha(mNavFadeOut.value);
                        }
                        finalParams.applySurfaceParams(navBuilder.build());
                    }
                });
            } else if (inLiveTileMode) {
                // There is no transition animation for app launch from recent in live tile mode so
                // we have to trigger the navigation bar animation from system here.
                final RecentsAnimationController controller =
                        recentsView.getRecentsAnimationController();
                if (controller != null) {
                    controller.animateNavigationBarToApp(RECENTS_LAUNCH_DURATION);
                }
            }
            topMostSimulator = tsv;
        }

        if (!skipViewChanges && parallaxCenterAndAdjacentTask && topMostSimulator != null) {
            out.addFloat(v, VIEW_ALPHA, 1, 0, clampToProgress(LINEAR, 0.2f, 0.4f));

            TaskViewSimulator simulatorToCopy = topMostSimulator;
            simulatorToCopy.apply(params);

            // Mt represents the overall transformation on the thumbnailView relative to the
            // Launcher's rootView
            // K(t) represents transformation on the running window by the taskViewSimulator at
            // any time t.
            // at t = 0, we know that the simulator matches the thumbnailView. So if we apply K(0)`
            // on the Launcher's rootView, the thumbnailView would match the full running task
            // window. If we apply "K(0)` K(t)" thumbnailView will match the final transformed
            // window at any time t. This gives the overall matrix on thumbnailView to be:
            //    Mt K(0)` K(t)
            // During animation we apply transformation on the thumbnailView (and not the rootView)
            // to follow the TaskViewSimulator. So the final matrix applied on the thumbnailView is:
            //    Mt K(0)` K(t) Mt`
            TaskThumbnailView ttv = v.getThumbnail();
            RectF tvBounds = new RectF(0, 0,  ttv.getWidth(), ttv.getHeight());
            float[] tvBoundsMapped = new float[]{0, 0,  ttv.getWidth(), ttv.getHeight()};
            getDescendantCoordRelativeToAncestor(ttv, ttv.getRootView(), tvBoundsMapped, false);
            RectF tvBoundsInRoot = new RectF(
                    tvBoundsMapped[0], tvBoundsMapped[1],
                    tvBoundsMapped[2], tvBoundsMapped[3]);

            Matrix mt = new Matrix();
            mt.setRectToRect(tvBounds, tvBoundsInRoot, ScaleToFit.FILL);

            Matrix mti = new Matrix();
            mt.invert(mti);

            Matrix k0i = new Matrix();
            simulatorToCopy.getCurrentMatrix().invert(k0i);

            Matrix animationMatrix = new Matrix();
            out.addOnFrameCallback(() -> {
                animationMatrix.set(mt);
                animationMatrix.postConcat(k0i);
                animationMatrix.postConcat(simulatorToCopy.getCurrentMatrix());
                animationMatrix.postConcat(mti);
                ttv.setAnimationMatrix(animationMatrix);
            });

            out.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    ttv.setAnimationMatrix(null);
                }
            });
        }

        out.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                if (isQuickSwitch) {
                    InteractionJankMonitorWrapper.end(
                            InteractionJankMonitorWrapper.CUJ_QUICK_SWITCH);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                targets.release();
                super.onAnimationEnd(animation);
            }
        });

        if (depthController != null) {
            out.setFloat(depthController, DEPTH, BACKGROUND_APP.getDepth(context),
                    TOUCH_RESPONSE_INTERPOLATOR);
        }
    }

    /**
     * TODO: This doesn't animate at present. Feel free to blow out everyhing in this method
     * if needed
     *
     * We could manually try to animate the just the bounds for the leashes we get back, but we try
     * to do it through TaskViewSimulator(TVS) since that handles a lot of the recents UI stuff for
     * us.
     *
     * First you have to call TVS#setPreview() to indicate which leash it will operate one
     * Then operations happen in TVS#apply() on each frame callback.
     *
     * TVS uses DeviceProfile to try to figure out things like task height and such based on if the
     * device is in multiWindowMode or not. It's unclear given the two calls to startTask() when the
     * device is considered in multiWindowMode and things like insets and stuff change
     * and calculations have to be adjusted in the animations for that
     */
    public static void composeRecentsSplitLaunchAnimator(@NonNull TaskView initialView,
            @NonNull TaskView v, @NonNull TransitionInfo transitionInfo,
            SurfaceControl.Transaction t, @NonNull Runnable finishCallback) {

        final TransitionInfo.Change[] splitRoots = new TransitionInfo.Change[2];
        for (int i = 0; i < transitionInfo.getChanges().size(); ++i) {
            final TransitionInfo.Change change = transitionInfo.getChanges().get(i);
            final int taskId = change.getTaskInfo() != null ? change.getTaskInfo().taskId : -1;
            final int mode = change.getMode();
            // Find the target tasks' root tasks since those are the split stages that need to
            // be animated (the tasks themselves are children and thus inherit animation).
            if (taskId == initialView.getTask().key.id || taskId == v.getTask().key.id) {
                if (!(mode == TRANSIT_OPEN || mode == TRANSIT_TO_FRONT)) {
                    throw new IllegalStateException(
                            "Expected task to be showing, but it is " + mode);
                }
                if (change.getParent() == null) {
                    throw new IllegalStateException("Initiating multi-split launch but the split"
                            + "root of " + taskId + " is already visible or has broken hierarchy.");
                }
                splitRoots[taskId == initialView.getTask().key.id ? 0 : 1] =
                        transitionInfo.getChange(change.getParent());
            }
        }

        // This is where we should animate the split roots. For now, though, just make them visible.
        for (int i = 0; i < 2; ++i) {
            t.show(splitRoots[i].getLeash());
            t.setAlpha(splitRoots[i].getLeash(), 1.f);
        }

        // This contains the initial state (before animation), so apply this at the beginning of
        // the animation.
        t.apply();

        // Once there is an animation, this should be called AFTER the animation completes.
        finishCallback.run();
    }

    /** Legacy version (until shell transitions are enabled) */
    public static void composeRecentsSplitLaunchAnimatorLegacy(@NonNull AnimatorSet anim,
            @NonNull TaskView v, @NonNull RemoteAnimationTargetCompat[] appTargets,
            @NonNull RemoteAnimationTargetCompat[] wallpaperTargets,
            @NonNull RemoteAnimationTargetCompat[] nonAppTargets, boolean launcherClosing,
            @NonNull StateManager stateManager, @NonNull DepthController depthController,
            int targetStage) {
        PendingAnimation out = new PendingAnimation(RECENTS_LAUNCH_DURATION);
        boolean isRunningTask = v.isRunningTask();
        TransformParams params = null;
        TaskViewSimulator tvs = null;
        RecentsView recentsView = v.getRecentsView();
        if (ENABLE_QUICKSTEP_LIVE_TILE.get() && isRunningTask) {
            params = recentsView.getLiveTileParams();
            tvs = recentsView.getLiveTileTaskViewSimulator();
        }

        boolean inLiveTileMode =
                ENABLE_QUICKSTEP_LIVE_TILE.get() && recentsView.getRunningTaskIndex() != -1;
        final RemoteAnimationTargets targets =
                new RemoteAnimationTargets(appTargets, wallpaperTargets, nonAppTargets,
                        inLiveTileMode ? MODE_CLOSING : MODE_OPENING);

        if (params == null) {
            SurfaceTransactionApplier applier = new SurfaceTransactionApplier(v);
            targets.addReleaseCheck(applier);

            params = new TransformParams()
                    .setSyncTransactionApplier(applier)
                    .setTargetSet(targets);
        }

        Rect crop = new Rect();
        Context context = v.getContext();
        DeviceProfile dp = BaseActivity.fromContext(context).getDeviceProfile();
        if (tvs == null && targets.apps.length > 0) {
            tvs = new TaskViewSimulator(recentsView.getContext(), recentsView.getSizeStrategy());
            tvs.setDp(dp);

            // RecentsView never updates the display rotation until swipe-up so the value may
            // be stale. Use the display value instead.
            int displayRotation = DisplayController.INSTANCE.get(recentsView.getContext())
                    .getInfo().rotation;
            tvs.getOrientationState().update(displayRotation, displayRotation);

            tvs.setPreview(targets.apps[targets.apps.length - 1]);
            tvs.fullScreenProgress.value = 0;
            tvs.recentsViewScale.value = 1;
//            tvs.setScroll(startScroll);

            // Fade in the task during the initial 20% of the animation
            out.addFloat(params, TransformParams.TARGET_ALPHA, 0, 1,
                    clampToProgress(LINEAR, 0, 0.2f));
        }

        TaskViewSimulator topMostSimulator = null;

        if (tvs != null) {
            out.setFloat(tvs.fullScreenProgress,
                    AnimatedFloat.VALUE, 1, TOUCH_RESPONSE_INTERPOLATOR);
            out.setFloat(tvs.recentsViewScale,
                    AnimatedFloat.VALUE, tvs.getFullScreenScale(), TOUCH_RESPONSE_INTERPOLATOR);
            out.setFloat(tvs.recentsViewScroll,
                    AnimatedFloat.VALUE, 0, TOUCH_RESPONSE_INTERPOLATOR);

            TaskViewSimulator finalTsv = tvs;
            TransformParams finalParams = params;
            out.addOnFrameCallback(() -> finalTsv.apply(finalParams));
            topMostSimulator = tvs;
        }

        anim.play(out.buildAnim());
    }

    public static void composeRecentsLaunchAnimator(@NonNull AnimatorSet anim, @NonNull View v,
            @NonNull RemoteAnimationTargetCompat[] appTargets,
            @NonNull RemoteAnimationTargetCompat[] wallpaperTargets,
            @NonNull RemoteAnimationTargetCompat[] nonAppTargets, boolean launcherClosing,
            @NonNull StateManager stateManager, @NonNull RecentsView recentsView,
            @NonNull DepthController depthController) {
        boolean skipLauncherChanges = !launcherClosing;

        TaskView taskView = findTaskViewToLaunch(recentsView, v, appTargets);
        PendingAnimation pa = new PendingAnimation(RECENTS_LAUNCH_DURATION);
        createRecentsWindowAnimator(taskView, skipLauncherChanges, appTargets, wallpaperTargets,
                nonAppTargets, depthController, pa);

        Animator childStateAnimation = null;
        // Found a visible recents task that matches the opening app, lets launch the app from there
        Animator launcherAnim;
        final AnimatorListenerAdapter windowAnimEndListener;
        if (launcherClosing) {
            Context context = v.getContext();
            DeviceProfile dp = BaseActivity.fromContext(context).getDeviceProfile();
            launcherAnim = dp.isTablet && FeatureFlags.ENABLE_OVERVIEW_GRID.get()
                    ? ObjectAnimator.ofFloat(recentsView, RecentsView.CONTENT_ALPHA, 0)
                    : recentsView.createAdjacentPageAnimForTaskLaunch(taskView);
            launcherAnim.setInterpolator(Interpolators.TOUCH_RESPONSE_INTERPOLATOR);
            launcherAnim.setDuration(RECENTS_LAUNCH_DURATION);

            // Make sure recents gets fixed up by resetting task alphas and scales, etc.
            windowAnimEndListener = new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    recentsView.finishRecentsAnimation(false /* toRecents */, () -> {
                        recentsView.post(() -> {
                            stateManager.moveToRestState();
                            stateManager.reapplyState();
                        });
                    });
                }
            };
        } else {
            AnimatorPlaybackController controller =
                    stateManager.createAnimationToNewWorkspace(NORMAL, RECENTS_LAUNCH_DURATION);
            controller.dispatchOnStart();
            childStateAnimation = controller.getTarget();
            launcherAnim = controller.getAnimationPlayer().setDuration(RECENTS_LAUNCH_DURATION);
            windowAnimEndListener = new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    recentsView.finishRecentsAnimation(false /* toRecents */,
                            () -> stateManager.goToState(NORMAL, false));
                }
            };
        }
        pa.add(launcherAnim);
        if (ENABLE_QUICKSTEP_LIVE_TILE.get() && recentsView.getRunningTaskIndex() != -1) {
            pa.addOnFrameCallback(recentsView::redrawLiveTile);
        }
        anim.play(pa.buildAnim());

        // Set the current animation first, before adding windowAnimEndListener. Setting current
        // animation adds some listeners which need to be called before windowAnimEndListener
        // (the ordering of listeners matter in this case).
        stateManager.setCurrentAnimation(anim, childStateAnimation);
        anim.addListener(windowAnimEndListener);
    }
}

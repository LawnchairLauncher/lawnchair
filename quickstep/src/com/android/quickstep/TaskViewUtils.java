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

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.view.RemoteAnimationTarget.MODE_CLOSING;
import static android.view.RemoteAnimationTarget.MODE_OPENING;
import static android.view.WindowManager.LayoutParams.TYPE_DOCK_DIVIDER;

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.app.animation.Interpolators.TOUCH_RESPONSE;
import static com.android.app.animation.Interpolators.clampToProgress;
import static com.android.launcher3.Flags.enableDesktopExplodedView;
import static com.android.launcher3.LauncherAnimUtils.VIEW_ALPHA;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.QuickstepTransitionManager.ANIMATION_DELAY_NAV_FADE_IN;
import static com.android.launcher3.QuickstepTransitionManager.ANIMATION_NAV_FADE_IN_DURATION;
import static com.android.launcher3.QuickstepTransitionManager.ANIMATION_NAV_FADE_OUT_DURATION;
import static com.android.launcher3.QuickstepTransitionManager.NAV_FADE_IN_INTERPOLATOR;
import static com.android.launcher3.QuickstepTransitionManager.NAV_FADE_OUT_INTERPOLATOR;
import static com.android.launcher3.QuickstepTransitionManager.RECENTS_LAUNCH_DURATION;
import static com.android.launcher3.QuickstepTransitionManager.SPLIT_DIVIDER_ANIM_DURATION;
import static com.android.launcher3.QuickstepTransitionManager.SPLIT_LAUNCH_DURATION;
import static com.android.launcher3.Utilities.getDescendantCoordRelativeToAncestor;
import static com.android.launcher3.util.MultiPropertyFactory.MULTI_PROPERTY_VALUE;
import static com.android.launcher3.util.OverviewReleaseFlags.enableGridOnlyOverview;
import static com.android.quickstep.BaseContainerInterface.getTaskDimension;
import static com.android.quickstep.util.AnimUtils.clampToDuration;
import static com.android.wm.shell.shared.TransitionUtil.TYPE_SPLIT_SCREEN_DIM_LAYER;
import static com.android.wm.shell.shared.split.SplitScreenConstants.DEFAULT_OFFSCREEN_DIM;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.util.Pair;
import android.view.RemoteAnimationTarget;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.View;
import android.window.TransitionInfo;
import android.window.WindowAnimationState;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.internal.jank.Cuj;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StatefulContainer;
import com.android.launcher3.taskbar.TaskbarInteractor;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.RemoteTargetGluer.RemoteTargetHandle;
import com.android.quickstep.util.MultiValueUpdateListener;
import com.android.quickstep.util.RemoteTargetHandleUtilKt;
import com.android.quickstep.util.SurfaceTransaction;
import com.android.quickstep.util.SurfaceTransaction.SurfaceProperties;
import com.android.quickstep.util.SurfaceTransactionApplier;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.views.DesktopTaskView;
import com.android.quickstep.views.GroupedTaskView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.quickstep.views.TaskView;
import com.android.systemui.animation.RemoteAnimationTargetCompat;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Utility class for helpful methods related to {@link TaskView} objects and their tasks.
 */
public final class TaskViewUtils {

    private TaskViewUtils() {}

    private static final Rect TEMP_THUMBNAIL_BOUNDS = new Rect();
    private static final Rect TEMP_FULLSCREEN_BOUNDS = new Rect();
    private static final PointF TEMP_TASK_DIMENSION = new PointF();
    private static final PointF TEMP_PIVOT = new PointF();
    private static final String TAG = "TaskViewUtils";

    /**
     * Try to find a TaskView that corresponds with the component of the launched view.
     *
     * If this method returns a non-null TaskView, it will be used in composeRecentsLaunchAnimation.
     * Otherwise, we will assume we are using a normal app transition, but it's possible that the
     * opening remote target (which we don't get until onAnimationStart) will resolve to a TaskView.
     */
    public static TaskView findTaskViewToLaunch(
            RecentsView<?, ?> recentsView, View v, RemoteAnimationTarget[] targets) {
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
                for (TaskView taskView : recentsView.getTaskViews()) {
                    Task firstTask = taskView.getFirstTask();
                    if (firstTask != null && recentsView.isTaskViewVisible(taskView)) {
                        Task.TaskKey key = firstTask.key;
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
        for (RemoteAnimationTarget target : targets) {
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
        TaskView taskView = recentsView.getTaskViewByTaskId(openingTaskId);
        if (taskView == null || !recentsView.isTaskViewVisible(taskView)) {
            return null;
        }
        return taskView;
    }

    public static <T extends Context & RecentsViewContainer & StatefulContainer<?>>
    void createRecentsWindowAnimator(
            @NonNull RecentsView<T, ?> recentsView,
            @NonNull TaskView taskView,
            boolean skipViewChanges,
            @NonNull RemoteAnimationTarget[] appTargets,
            @NonNull RemoteAnimationTarget[] wallpaperTargets,
            @NonNull RemoteAnimationTarget[] nonAppTargets,
            @Nullable DepthController depthController,
            @Nullable TransitionInfo transitionInfo,
            int appearedTaskId,
            PendingAnimation out) {
        boolean isQuickSwitch = taskView.isEndQuickSwitchCuj();
        taskView.setEndQuickSwitchCuj(false);

        final RemoteAnimationTargets targets =
                new RemoteAnimationTargets(appTargets, wallpaperTargets, nonAppTargets,
                        MODE_OPENING);
        final RemoteAnimationTarget navBarTarget = targets.getNavBarRemoteAnimationTarget();

        SurfaceTransactionApplier applier = new SurfaceTransactionApplier(taskView);
        targets.addReleaseCheck(applier);

        Context context = taskView.getContext();
        T container = RecentsViewContainer.containerFromContext(context);
        DeviceProfile dp = container.getDeviceProfile();

        RemoteTargetHandle[] remoteTargetHandles;
        RemoteTargetHandle[] recentsViewHandles = recentsView.getRemoteTargetHandles();
        if (taskView.isRunningTask() && recentsViewHandles != null) {
            // If we have a valid task ID to reorder to front, we need to check if its
            // RemoteTargetHandle exists for animations.
            if (appearedTaskId != INVALID_TASK_ID && taskView instanceof DesktopTaskView
                    && enableDesktopExplodedView()) {
                // First, get the first (or null) RemoteTargetHandle associated with the ID.
                RemoteTargetHandle activatedMinimizedHandle =
                        RemoteTargetHandleUtilKt.getRemoteTargetHandle(
                                recentsViewHandles, appearedTaskId);

                // If we don't have a valid RemoteTargetHandle (expected when activating a
                // minimized task), make and initialize one.
                if (activatedMinimizedHandle == null) {
                    RemoteTargetGluer gluer = new RemoteTargetGluer(recentsViewHandles);
                    recentsViewHandles = gluer.insertNewHandlesInFrontAndAssignTargetsForDesktop(
                            context,
                            recentsView.getContainerInterface(),
                            targets,
                            transitionInfo);

                    // Init the new TaskViewSimulator at the front of recentsViewHandles before we
                    // set the recents rotation to ensure we have the proper orientation.
                    initTaskViewSimulatorsForRemoteTargetHandles(
                            List.of(recentsViewHandles[0]), dp, recentsView, taskView, out);

                    ((DesktopTaskView) taskView).setRemoteTargetHandles(recentsViewHandles);
                }

                RemoteTargetHandle reorderToFrontHandle =
                        RemoteTargetHandleUtilKt.getRemoteTargetHandle(
                                recentsViewHandles, appearedTaskId);
                if (reorderToFrontHandle != null) {
                    // The layer swapping is only applied after [createRecentsWindowAnimator]
                    // starts, which will bring the [remoteTargetHandles] above Recents, therefore
                    // this call won't affect the base surface in [DepthController].
                    reorderToFrontHandle.getTaskViewSimulator().setDrawsAboveOtherApps(true);
                }
            }

            // Re-use existing handles
            remoteTargetHandles = recentsViewHandles;
        } else {
            boolean forDesktop = taskView instanceof DesktopTaskView;
            RemoteTargetGluer gluer = new RemoteTargetGluer(taskView.getContext(),
                    recentsView.getContainerInterface(), targets, forDesktop);
            if (forDesktop) {
                remoteTargetHandles = gluer.assignTargetsForDesktop(targets, transitionInfo);
                if (enableDesktopExplodedView()) {
                    ((DesktopTaskView) taskView).setRemoteTargetHandles(remoteTargetHandles);
                }
            } else if (taskView.containsMultipleTasks()) {
                remoteTargetHandles = gluer.assignTargetsForSplitScreen(targets,
                        ((GroupedTaskView) taskView).getSplitBoundsConfig());
            } else {
                remoteTargetHandles = gluer.assignTargets(targets);
            }
        }

        final int recentsActivityRotation =
                recentsView.getPagedViewOrientedState().getRecentsActivityRotation();
        for (RemoteTargetHandle remoteTargetHandle : remoteTargetHandles) {
            remoteTargetHandle.getTaskViewSimulator().getOrientationState()
                    .setRecentsRotation(recentsActivityRotation);
            remoteTargetHandle.getTransformParams().setSyncTransactionApplier(applier);
        }

        RemoteTargetHandle[] topMostSimulators = null;

        // TVSs already initialized from the running task, no need to re-init
        if (!taskView.isRunningTask()) {
            initTaskViewSimulatorsForRemoteTargetHandles(
                    Arrays.asList(remoteTargetHandles), dp, recentsView, taskView, out);
        }

        for (RemoteTargetHandle targetHandle : remoteTargetHandles) {
            TaskViewSimulator tvsLocal = targetHandle.getTaskViewSimulator();
            out.setFloat(tvsLocal.fullScreenProgress,
                    AnimatedFloat.VALUE, 1, TOUCH_RESPONSE);
            out.setFloat(tvsLocal.recentsViewScale,
                    AnimatedFloat.VALUE, tvsLocal.getFullScreenScale(),
                    TOUCH_RESPONSE);
            if (!enableGridOnlyOverview()) {
                out.setFloat(tvsLocal.recentsViewScroll, AnimatedFloat.VALUE, 0,
                        TOUCH_RESPONSE);
            }

            out.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    final SurfaceTransaction showTransaction = new SurfaceTransaction();
                    for (int i = targets.apps.length - 1; i >= 0; --i) {
                        showTransaction.getTransaction().show(targets.apps[i].leash);
                    }
                    applier.scheduleApply(showTransaction);

                    if (enableGridOnlyOverview()) {
                        taskView.getThumbnailBounds(TEMP_THUMBNAIL_BOUNDS, /*relativeToDragLayer=*/
                                true);
                        getTaskDimension(container.getDeviceProfile(),
                                TEMP_TASK_DIMENSION);
                        TEMP_FULLSCREEN_BOUNDS.set(0, 0, (int) TEMP_TASK_DIMENSION.x,
                                (int) TEMP_TASK_DIMENSION.y);
                        Utilities.getPivotsForScalingRectToRect(TEMP_THUMBNAIL_BOUNDS,
                                TEMP_FULLSCREEN_BOUNDS, TEMP_PIVOT);
                        tvsLocal.setPivotOverride(TEMP_PIVOT);
                    }
                }
            });
            out.addOnFrameCallback(() -> {
                for (RemoteTargetHandle handle : remoteTargetHandles) {
                    handle.getTaskViewSimulator().apply(handle.getTransformParams());
                }
            });
            if (navBarTarget != null) {
                final Rect cropRect = new Rect();
                out.addOnFrameListener(new MultiValueUpdateListener() {
                    FloatProp mNavFadeOut = new FloatProp(1f, 0f, clampToDuration(
                            NAV_FADE_OUT_INTERPOLATOR,
                            0,
                            ANIMATION_NAV_FADE_OUT_DURATION,
                            out.getDuration()));
                    FloatProp mNavFadeIn = new FloatProp(0f, 1f, clampToDuration(
                            NAV_FADE_IN_INTERPOLATOR,
                            ANIMATION_DELAY_NAV_FADE_IN,
                            ANIMATION_NAV_FADE_IN_DURATION,
                            out.getDuration()));

                    @Override
                    public void onUpdate(float percent, boolean initOnly) {


                        // TODO Do we need to operate over multiple TVSs for the navbar leash?
                        for (RemoteTargetHandle handle : remoteTargetHandles) {
                            SurfaceTransaction transaction = new SurfaceTransaction();
                            SurfaceProperties navBuilder =
                                    transaction.forSurface(navBarTarget.leash);

                            if (mNavFadeIn.value > mNavFadeIn.getStartValue()) {
                                TaskViewSimulator taskViewSimulator = handle.getTaskViewSimulator();
                                taskViewSimulator.getCurrentCropRect().round(cropRect);
                                navBuilder.setMatrix(taskViewSimulator.getCurrentMatrix())
                                        .setWindowCrop(cropRect)
                                        .setAlpha(mNavFadeIn.value);
                            } else {
                                navBuilder.setAlpha(mNavFadeOut.value);
                            }
                            handle.getTransformParams().applySurfaceParams(transaction);
                        }
                    }
                });
            }
            topMostSimulators = remoteTargetHandles;
        }

        int taskIndex = recentsView.indexOfChild(taskView);
        boolean parallaxCenterAndAdjacentTask =
                !dp.getDeviceProperties().isTablet() && taskIndex != recentsView.getCurrentPage();
        if (!skipViewChanges && parallaxCenterAndAdjacentTask && topMostSimulators != null
                && topMostSimulators.length > 0) {
            out.addFloat(taskView, VIEW_ALPHA, 1, 0, clampToProgress(LINEAR, 0.2f, 0.4f));

            RemoteTargetHandle[] simulatorCopies = topMostSimulators;
            for (RemoteTargetHandle handle : simulatorCopies) {
                handle.getTaskViewSimulator().apply(handle.getTransformParams());
            }

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
            View[] thumbnails = taskView.getSnapshotViews();

            // In case simulator copies and thumbnail size do no match, ensure we get the lesser.
            // This ensures we do not create arrays with empty elements or attempt to references
            // indexes out of array bounds.
            final int matrixSize = Math.min(simulatorCopies.length, thumbnails.length);

            Matrix[] mt = new Matrix[matrixSize];
            Matrix[] mti = new Matrix[matrixSize];
            for (int i = 0; i < matrixSize; i++) {
                View ttv = thumbnails[i];
                RectF localBounds = new RectF(0, 0,  ttv.getWidth(), ttv.getHeight());
                float[] tvBoundsMapped = new float[]{0, 0,  ttv.getWidth(), ttv.getHeight()};
                getDescendantCoordRelativeToAncestor(ttv, ttv.getRootView(), tvBoundsMapped, false);
                RectF localBoundsInRoot = new RectF(
                        tvBoundsMapped[0], tvBoundsMapped[1],
                        tvBoundsMapped[2], tvBoundsMapped[3]);
                Matrix localMt = new Matrix();
                localMt.setRectToRect(localBounds, localBoundsInRoot, ScaleToFit.FILL);
                mt[i] = localMt;

                Matrix localMti = new Matrix();
                localMt.invert(localMti);
                mti[i] = localMti;

                // Translations for child thumbnails also get scaled as the parent taskView scales
                // Add inverse scaling to keep translations the same
                float translationY = ttv.getTranslationY();
                float translationX = ttv.getTranslationX();
                float fullScreenScale =
                        topMostSimulators[i].getTaskViewSimulator().getFullScreenScale();
                out.addFloat(ttv, VIEW_TRANSLATE_Y, translationY,
                        translationY / fullScreenScale, TOUCH_RESPONSE);
                out.addFloat(ttv, VIEW_TRANSLATE_X, translationX,
                         translationX / fullScreenScale, TOUCH_RESPONSE);
            }

            Matrix[] k0i = new Matrix[matrixSize];
            for (int i = 0; i < matrixSize; i++) {
                k0i[i] = new Matrix();
                simulatorCopies[i].getTaskViewSimulator().getCurrentMatrix().invert(k0i[i]);
            }
            Matrix animationMatrix = new Matrix();
            out.addOnFrameCallback(() -> {
                for (int i = 0; i < matrixSize; i++) {
                    animationMatrix.set(mt[i]);
                    animationMatrix.postConcat(k0i[i]);
                    animationMatrix.postConcat(simulatorCopies[i]
                            .getTaskViewSimulator().getCurrentMatrix());
                    animationMatrix.postConcat(mti[i]);
                    thumbnails[i].setAnimationMatrix(animationMatrix);
                }
            });

            out.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    for (View ttv : thumbnails) {
                        ttv.setAnimationMatrix(null);
                    }
                }
            });
        }

        out.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                recentsView.setDrawAboveRecents(remoteTargetHandles);
            }

            @Override
            public void onAnimationSuccess(Animator animator) {
                if (isQuickSwitch) {
                    InteractionJankMonitorWrapper.end(Cuj.CUJ_LAUNCHER_QUICK_SWITCH);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                targets.release();
                super.onAnimationEnd(animation);
            }
        });

        if (depthController != null) {
            out.setFloat(depthController.stateDepth, MULTI_PROPERTY_VALUE,
                    BACKGROUND_APP.getDepth(container),
                    TOUCH_RESPONSE);
        }
    }

    /**
     * If {@param launchingTaskView} is not null, then this will play the tasks launch animation
     * from the position of the GroupedTaskView (when user taps on the TaskView to start it).
     * Technically this case should be taken care of by
     * {@link #composeRecentsSplitLaunchAnimatorLegacy} below, but the way we launch tasks whether
     * it's a single task or multiple tasks results in different entry-points.
     */
    public static void composeRecentsSplitLaunchAnimator(GroupedTaskView launchingTaskView,
            @NonNull StateManager stateManager, @Nullable DepthController depthController,
            @NonNull TransitionInfo transitionInfo, SurfaceControl.Transaction t,
            @NonNull Runnable finishCallback) {
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                finishCallback.run();
            }
        });

        final RemoteAnimationTarget[] appTargets =
                RemoteAnimationTargetCompat.wrapApps(transitionInfo, t, null /* leashMap */);
        final RemoteAnimationTarget[] wallpaperTargets =
                RemoteAnimationTargetCompat.wrapNonApps(
                        transitionInfo, true /* wallpapers */, t, null /* leashMap */);
        final RemoteAnimationTarget[] nonAppTargets =
                RemoteAnimationTargetCompat.wrapNonApps(
                        transitionInfo, false /* wallpapers */, t, null /* leashMap */);
        final RecentsView recentsView = launchingTaskView.getRecentsView();
        composeRecentsLaunchAnimator(animatorSet, launchingTaskView, appTargets, wallpaperTargets,
                nonAppTargets, /* launcherClosing */ true, stateManager, recentsView,
                depthController, /* transitionInfo= */ null, /* appearedTaskId= */ INVALID_TASK_ID);

        t.apply();
        animatorSet.start();
    }

    /**
     * Legacy version (until shell transitions are enabled)
     *
     * If {@param launchingTaskView} is not null, then this will play the tasks launch animation
     * from the position of the GroupedTaskView (when user taps on the TaskView to start it).
     * Technically this case should be taken care of by
     * {@link #composeRecentsSplitLaunchAnimatorLegacy} below, but the way we launch tasks whether
     * it's a single task or multiple tasks results in different entry-points.
     *
     * If it is null, then it will simply fade in the starting apps and fade out launcher (for the
     * case where launcher handles animating starting split tasks from app icon)
     * @deprecated with shell transitions
     */
    public static void composeRecentsSplitLaunchAnimatorLegacy(
            @Nullable GroupedTaskView launchingTaskView, int initialTaskId, int secondTaskId,
            @NonNull RemoteAnimationTarget[] appTargets,
            @NonNull RemoteAnimationTarget[] wallpaperTargets,
            @NonNull RemoteAnimationTarget[] nonAppTargets,
            @NonNull StateManager stateManager,
            @Nullable DepthController depthController,
            @NonNull Runnable finishCallback) {
        if (launchingTaskView != null) {
            AnimatorSet animatorSet = new AnimatorSet();
            RecentsView recentsView = launchingTaskView.getRecentsView();
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    finishCallback.run();
                }
            });
            composeRecentsLaunchAnimator(animatorSet, launchingTaskView,
                    appTargets, wallpaperTargets, nonAppTargets,
                    true, stateManager,
                    recentsView, depthController, /* transitionInfo= */ null,
                    /* appearedTaskId= */ INVALID_TASK_ID);
            animatorSet.start();
            return;
        }

        final ArrayList<SurfaceControl> openingTargets = new ArrayList<>();
        final ArrayList<SurfaceControl> closingTargets = new ArrayList<>();
        for (RemoteAnimationTarget appTarget : appTargets) {
            final int taskId = appTarget.taskInfo != null ? appTarget.taskInfo.taskId : -1;
            final int mode = appTarget.mode;
            final SurfaceControl leash = appTarget.leash;
            if (leash == null) {
                continue;
            }

            if (mode == MODE_OPENING) {
                openingTargets.add(leash);
            } else if (taskId == initialTaskId || taskId == secondTaskId) {
                throw new IllegalStateException("Expected task to be opening, but it is " + mode);
            } else if (mode == MODE_CLOSING) {
                closingTargets.add(leash);
            }
        }

        for (int i = 0; i < nonAppTargets.length; ++i) {
            final SurfaceControl leash = nonAppTargets[i].leash;
            if (nonAppTargets[i].windowType == TYPE_DOCK_DIVIDER && leash != null) {
                openingTargets.add(leash);
            }
        }

        final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(SPLIT_LAUNCH_DURATION);
        animator.addUpdateListener(valueAnimator -> {
            float progress = valueAnimator.getAnimatedFraction();
            for (SurfaceControl leash: openingTargets) {
                t.setAlpha(leash, progress);
            }
            t.apply();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                for (SurfaceControl leash: openingTargets) {
                    t.show(leash).setAlpha(leash, 0.0f);
                }
                t.apply();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                for (SurfaceControl leash: closingTargets) {
                    t.hide(leash);
                }
                finishCallback.run();
            }
        });
        animator.start();
    }

    /**
     * Start recents to desktop animation
     */
    public static AnimatorSet composeRecentsDesktopLaunchAnimator(
            @NonNull TaskView launchingTaskView,
            @NonNull StateManager stateManager, @Nullable DepthController depthController,
            @NonNull TransitionInfo transitionInfo, SurfaceControl.Transaction t,
            @NonNull Runnable finishCallback
    ) {
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                t.apply();
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                finishCallback.run();
            }
        });

        final RemoteAnimationTarget[] apps = RemoteAnimationTargetCompat.wrapApps(
                transitionInfo, t, null /* leashMap */);
        final RemoteAnimationTarget[] wallpaper = RemoteAnimationTargetCompat.wrapNonApps(
                transitionInfo, true /* wallpapers */, t, null /* leashMap */);
        final RemoteAnimationTarget[] nonApps = RemoteAnimationTargetCompat.wrapNonApps(
                transitionInfo, false /* wallpapers */, t, null /* leashMap */);

        composeRecentsLaunchAnimator(animatorSet, launchingTaskView, apps, wallpaper, nonApps,
                true /* launcherClosing */, stateManager, launchingTaskView.getRecentsView(),
                depthController, transitionInfo, /* appearedTaskId= */ INVALID_TASK_ID);

        return animatorSet;
    }

    public static void composeRecentsLaunchAnimator(@NonNull AnimatorSet anim, @NonNull View v,
            @NonNull RemoteAnimationTarget[] appTargets,
            @NonNull RemoteAnimationTarget[] wallpaperTargets,
            @NonNull RemoteAnimationTarget[] nonAppTargets, boolean launcherClosing,
            @NonNull StateManager stateManager, @NonNull RecentsView recentsView,
            @Nullable DepthController depthController, @Nullable TransitionInfo transitionInfo,
            int appearedTaskId) {
        boolean skipLauncherChanges = !launcherClosing;

        TaskView taskView = findTaskViewToLaunch(recentsView, v, appTargets);
        if (taskView == null) {
            Log.w(TAG, "composeRecentsLaunchAnimator - no TaskView to launch");
            return;
        }
        PendingAnimation pa = new PendingAnimation(RECENTS_LAUNCH_DURATION);
        createRecentsWindowAnimator(recentsView, taskView, skipLauncherChanges, appTargets,
                wallpaperTargets, nonAppTargets, depthController, transitionInfo,
                appearedTaskId, pa);
        if (launcherClosing) {
            // TODO(b/182592057): differentiate between "restore split" vs "launch fullscreen app"
            TaskViewUtils.createSplitAuxiliarySurfacesAnimator(nonAppTargets, true /*shown*/,
                    (dividerAnimator) -> {
                        // If split apps are launching, we want to delay showing the divider bar
                        // until the very end once the apps are mostly in place. This is because we
                        // aren't moving the divider leash in the relative position with the
                        // launching apps.
                        dividerAnimator.setStartDelay(pa.getDuration()
                                - SPLIT_DIVIDER_ANIM_DURATION);
                        pa.add(dividerAnimator);
                    });
        }

        Animator childStateAnimation = null;
        // Found a visible recents task that matches the opening app, lets launch the app from there
        Animator launcherAnim;
        final AnimatorListenerAdapter windowAnimEndListener;
        if (launcherClosing) {
            // Since Overview is in launcher, just opening overview sets willFinishToHome to true.
            // Now that we are closing the launcher, we need to (re)set willFinishToHome back to
            // false. Otherwise, RecentsAnimationController can't differentiate between closing
            // overview to 3p home vs closing overview to app.
            final RecentsAnimationController raController =
                    recentsView.getRecentsAnimationController();
            if (raController != null) {
                raController.setWillFinishToHome(false);
            }
            launcherAnim = recentsView.createAdjacentPageAnimForTaskLaunch(taskView);
            launcherAnim.setInterpolator(Interpolators.TOUCH_RESPONSE);
            launcherAnim.setDuration(RECENTS_LAUNCH_DURATION);

            windowAnimEndListener = new AnimationSuccessListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    recentsView.onTaskLaunchedInLiveTileMode();
                }

                // Make sure recents gets fixed up by resetting task alphas and scales, etc.
                // This should only be run onAnimationSuccess, otherwise finishRecentsAnimation will
                // interfere with a rapid swipe up to home in the live tile + running task case.
                @Override
                public void onAnimationSuccess(Animator animation) {
                    recentsView.finishRecentsAnimation(false /* toHome */, () -> {
                        recentsView.post(() -> {
                            stateManager.moveToRestState();
                            stateManager.reapplyState();

                            // We may have notified launcher is not visible so that taskbar can
                            // stash immediately. Now that the animation is over, we can update
                            // that launcher is still visible.
                            TaskbarInteractor interactor = recentsView.getContainerInterface()
                                    .getTaskbarInteractor();
                            // If we're launching the desktop tile in Overview, no need to change
                            // the launcher visibility and taskbar visibility below.
                            if (interactor != null && !(v instanceof DesktopTaskView)) {
                                boolean launcherVisible = true;
                                for (RemoteAnimationTarget target : appTargets) {
                                    launcherVisible &= target.isTranslucent;
                                }
                                if (launcherVisible) {
                                    interactor.onLauncherVisibilityChanged(true);
                                }
                            }
                        });
                    });
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    super.onAnimationCancel(animation);
                    recentsView.onTaskLaunchedInLiveTileModeCancelled();
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    recentsView.setTaskLaunchCancelledRunnable(null);
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
                    recentsView.finishRecentsAnimation(false /* toHome */,
                            () -> stateManager.goToState(NORMAL, false));
                }
            };
        }
        pa.add(launcherAnim);
        if (recentsView.getRunningTaskIndex() != -1) {
            pa.addOnFrameCallback(recentsView::redrawLiveTile);
        }
        anim.play(pa.buildAnim());

        // Set the current animation first, before adding windowAnimEndListener. Setting current
        // animation adds some listeners which need to be called before windowAnimEndListener
        // (the ordering of listeners matter in this case).
        stateManager.setCurrentAnimation(anim, childStateAnimation);
        anim.addListener(windowAnimEndListener);
    }

    /**
     * Creates an animation to show/hide the auxiliary surfaces (aka. divider bar), only calling
     * {@param animatorHandler} if there are valid surfaces to animate.
     * Passing null handler to apply the visibility immediately.
     *
     * @return the animator animating the surfaces
     */
    public static ValueAnimator createSplitAuxiliarySurfacesAnimator(
            @Nullable RemoteAnimationTarget[] nonApps, boolean shown,
            @Nullable Consumer<ValueAnimator> animatorHandler) {
        if (nonApps == null || nonApps.length == 0) {
            return null;
        }

        // Since dim layers need to animate to a different alpha, we separate them out here.
        List<SurfaceControl> dividerSurfaces = new ArrayList<>();
        List<SurfaceControl> dimLayerSurfaces = new ArrayList<>();
        // For convenience, we also keep a pointer to all the divider + dim layer leashes together.
        List<SurfaceControl> auxiliarySurfaces = new ArrayList<>();
        for (RemoteAnimationTarget target : nonApps) {
            final SurfaceControl leash = target.leash;
            if (leash != null && leash.isValid()) {
                if (target.windowType == TYPE_DOCK_DIVIDER) {
                    dividerSurfaces.add(leash);
                } else if (target.windowType == TYPE_SPLIT_SCREEN_DIM_LAYER) {
                    dimLayerSurfaces.add(leash);
                }
                auxiliarySurfaces.add(leash);
            }
        }

        if (auxiliarySurfaces.isEmpty()) {
            return null;
        }

        SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        if (animatorHandler == null) {
            // Apply the visibility directly without fade animation.
            for (SurfaceControl leash : auxiliarySurfaces) {
                t.setVisibility(leash, shown);
            }
            t.apply();
            t.close();
            return null;
        }

        ValueAnimator dockFadeAnimator = ValueAnimator.ofFloat(0f, 1f);
        dockFadeAnimator.addUpdateListener(valueAnimator -> {
            float progress = valueAnimator.getAnimatedFraction();
            for (SurfaceControl leash : dividerSurfaces) {
                if (leash != null && leash.isValid()) {
                    t.setAlpha(leash, shown ? progress : 1 - progress);
                }
            }
            for (SurfaceControl leash : dimLayerSurfaces) {
                if (leash != null && leash.isValid()) {
                    t.setAlpha(leash, (shown ? progress : 1 - progress) * DEFAULT_OFFSCREEN_DIM);
                }
            }
            t.apply();
        });
        dockFadeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (shown) {
                    for (SurfaceControl leash : auxiliarySurfaces) {
                        t.setLayer(leash, Integer.MAX_VALUE);
                        t.setAlpha(leash, 0);
                        t.show(leash);
                    }
                    t.apply();
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!shown) {
                    for (SurfaceControl leash : auxiliarySurfaces) {
                        if (leash != null && leash.isValid()) {
                            t.hide(leash);
                        }
                    }
                    t.apply();
                }
                t.close();
            }
        });
        dockFadeAnimator.setDuration(SPLIT_DIVIDER_ANIM_DURATION);
        animatorHandler.accept(dockFadeAnimator);
        return dockFadeAnimator;
    }

    /**
     * Creates an array of {@link RemoteAnimationTarget}s and a matching array of
     * {@link WindowAnimationState}s from the provided handles.
     * Important: the ordering of the two arrays is the same, so the state at each index of the
     * second applies to the target in the same index of the first.
     *
     * @param handles The handles wrapping each target.
     * @param timestamp The start time of the current frame.
     * @param velocityPxPerMs The current velocity of the target animations.
     */
    @NonNull
    public static Pair<RemoteAnimationTarget[], WindowAnimationState[]> extractTargetsAndStates(
            @NonNull RemoteTargetHandle[] handles, long timestamp,
            @NonNull PointF velocityPxPerMs) {
        RemoteAnimationTarget[] targets = new RemoteAnimationTarget[handles.length];
        WindowAnimationState[] animationStates = new WindowAnimationState[handles.length];

        for (int i = 0; i < handles.length; i++) {
            targets[i] = handles[i].getTransformParams().getTargetSet().apps[i];

            TaskViewSimulator taskViewSimulator = handles[i].getTaskViewSimulator();
            RectF startRect = taskViewSimulator.getCurrentRect();
            float cornerRadius = taskViewSimulator.getCurrentCornerRadius();

            WindowAnimationState state = new WindowAnimationState();
            state.timestamp = timestamp;
            state.bounds = new RectF(
                    startRect.left, startRect.top, startRect.right, startRect.bottom);
            state.topLeftRadius = cornerRadius;
            state.topRightRadius = cornerRadius;
            state.bottomRightRadius = cornerRadius;
            state.bottomLeftRadius = cornerRadius;
            state.velocityPxPerMs = velocityPxPerMs;

            animationStates[i] = state;
        }

        return new Pair<>(targets, animationStates);
    }

    private static void initTaskViewSimulatorsForRemoteTargetHandles(
            List<RemoteTargetHandle> handleList,
            DeviceProfile deviceProfile,
            RecentsView recentsView,
            TaskView taskView,
            PendingAnimation out) {
        // RecentsView never updates the display rotation until swipe-up so the value may
        // be stale. Use the display value instead.
        int displayId = taskView.getDisplayId();
        DisplayController.Info infoForDisplay =
                DisplayController.INSTANCE.get(taskView.getContext()).getInfoForDisplay(displayId);
        final int displayRotation;
        if (infoForDisplay != null) {
            displayRotation = infoForDisplay.rotation;
        } else {
            // Fallback to portrait orientation if we don't have info for the display.
            // This should never happen - we get displayId from the taskView being launched.
            Log.e(TAG, "Could not get info for displayId " + displayId, new Exception());
            displayRotation = Surface.ROTATION_0;
        }
        int scrollOffset = recentsView.getScrollOffset(
                recentsView.indexOfChild(taskView));
        int gridTranslationY = deviceProfile.getDeviceProperties().isTablet()
                ? (int) taskView.getGridTranslationY() : 0;

        for (RemoteTargetHandle handle : handleList) {
            TaskViewSimulator tvsLocal = handle.getTaskViewSimulator();
            tvsLocal.setDp(deviceProfile);

            tvsLocal.getOrientationState().update(displayRotation, displayRotation);
            tvsLocal.calculateTaskSize();

            tvsLocal.fullScreenProgress.value = 0;
            tvsLocal.recentsViewScale.value = 1;
            if (!enableGridOnlyOverview()) {
                tvsLocal.setIsGridTask(taskView.isGridTask());
            }
            tvsLocal.recentsViewScroll.value = scrollOffset;
            tvsLocal.taskSecondaryTranslation.value = gridTranslationY;

            if (taskView instanceof DesktopTaskView) {
                handle.getTransformParams().setTargetAlpha(1f);
            } else {
                // Fade in the task during the initial 20% of the animation
                out.addFloat(handle.getTransformParams(), TransformParams.TARGET_ALPHA, 0,
                        1, clampToProgress(LINEAR, 0, 0.2f));
            }
        }
    }
}

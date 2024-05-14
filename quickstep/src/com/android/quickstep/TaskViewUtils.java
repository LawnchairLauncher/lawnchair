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

import static android.view.RemoteAnimationTarget.MODE_CLOSING;
import static android.view.RemoteAnimationTarget.MODE_OPENING;
import static android.view.WindowManager.LayoutParams.TYPE_DOCK_DIVIDER;

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.app.animation.Interpolators.TOUCH_RESPONSE;
import static com.android.app.animation.Interpolators.clampToProgress;
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
import static com.android.quickstep.util.AnimUtils.clampToDuration;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.View;
import android.window.TransitionInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.internal.jank.Cuj;
import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.taskbar.TaskbarUIController;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.RemoteTargetGluer.RemoteTargetHandle;
import com.android.quickstep.util.MultiValueUpdateListener;
import com.android.quickstep.util.SurfaceTransaction;
import com.android.quickstep.util.SurfaceTransaction.SurfaceProperties;
import com.android.quickstep.util.SurfaceTransactionApplier;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.views.DesktopTaskView;
import com.android.quickstep.views.GroupedTaskView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskThumbnailViewDeprecated;
import com.android.quickstep.views.TaskView;
import com.android.systemui.animation.RemoteAnimationTargetCompat;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Utility class for helpful methods related to {@link TaskView} objects and their tasks.
 */
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
            RecentsView recentsView, View v, RemoteAnimationTarget[] targets) {
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
                        Task.TaskKey key = taskView.getFirstTask().key;
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

    public static void createRecentsWindowAnimator(
            @NonNull RecentsView recentsView,
            @NonNull TaskView v,
            boolean skipViewChanges,
            @NonNull RemoteAnimationTarget[] appTargets,
            @NonNull RemoteAnimationTarget[] wallpaperTargets,
            @NonNull RemoteAnimationTarget[] nonAppTargets,
            @Nullable DepthController depthController,
            PendingAnimation out) {
        boolean isQuickSwitch = v.isEndQuickSwitchCuj();
        v.setEndQuickSwitchCuj(false);

        final RemoteAnimationTargets targets =
                new RemoteAnimationTargets(appTargets, wallpaperTargets, nonAppTargets,
                        MODE_OPENING);
        final RemoteAnimationTarget navBarTarget = targets.getNavBarRemoteAnimationTarget();

        SurfaceTransactionApplier applier = new SurfaceTransactionApplier(v);
        targets.addReleaseCheck(applier);

        RemoteTargetHandle[] remoteTargetHandles;
        RemoteTargetHandle[] recentsViewHandles = recentsView.getRemoteTargetHandles();
        if (v.isRunningTask() && recentsViewHandles != null) {
            // Re-use existing handles
            remoteTargetHandles = recentsViewHandles;
        } else {
            boolean forDesktop = v instanceof DesktopTaskView;
            RemoteTargetGluer gluer = new RemoteTargetGluer(v.getContext(),
                    recentsView.getSizeStrategy(), targets, forDesktop);
            if (forDesktop) {
                remoteTargetHandles = gluer.assignTargetsForDesktop(targets);
            } else if (v.containsMultipleTasks()) {
                remoteTargetHandles = gluer.assignTargetsForSplitScreen(targets,
                        ((GroupedTaskView) v).getSplitBoundsConfig());
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

        int taskIndex = recentsView.indexOfChild(v);
        Context context = v.getContext();
        BaseActivity baseActivity = BaseActivity.fromContext(context);
        DeviceProfile dp = baseActivity.getDeviceProfile();
        boolean showAsGrid = dp.isTablet;
        boolean parallaxCenterAndAdjacentTask =
                !showAsGrid && taskIndex != recentsView.getCurrentPage();
        int taskRectTranslationPrimary = recentsView.getScrollOffset(taskIndex);
        int taskRectTranslationSecondary = showAsGrid ? (int) v.getGridTranslationY() : 0;

        RemoteTargetHandle[] topMostSimulators = null;

        if (!v.isRunningTask()) {
            // TVSs already initialized from the running task, no need to re-init
            for (RemoteTargetHandle targetHandle : remoteTargetHandles) {
                TaskViewSimulator tvsLocal = targetHandle.getTaskViewSimulator();
                tvsLocal.setDp(dp);

                // RecentsView never updates the display rotation until swipe-up so the value may
                // be stale. Use the display value instead.
                int displayRotation = DisplayController.INSTANCE.get(context).getInfo().rotation;
                tvsLocal.getOrientationState().update(displayRotation, displayRotation);

                tvsLocal.fullScreenProgress.value = 0;
                tvsLocal.recentsViewScale.value = 1;
                tvsLocal.setIsGridTask(v.isGridTask());
                tvsLocal.getOrientationState().getOrientationHandler().set(tvsLocal,
                        TaskViewSimulator::setTaskRectTranslation, taskRectTranslationPrimary,
                        taskRectTranslationSecondary);

                // Fade in the task during the initial 20% of the animation
                out.addFloat(targetHandle.getTransformParams(), TransformParams.TARGET_ALPHA, 0, 1,
                        clampToProgress(LINEAR, 0, 0.2f));
            }
        }

        for (RemoteTargetHandle targetHandle : remoteTargetHandles) {
            TaskViewSimulator tvsLocal = targetHandle.getTaskViewSimulator();
            out.setFloat(tvsLocal.fullScreenProgress,
                    AnimatedFloat.VALUE, 1, TOUCH_RESPONSE);
            out.setFloat(tvsLocal.recentsViewScale,
                    AnimatedFloat.VALUE, tvsLocal.getFullScreenScale(),
                    TOUCH_RESPONSE);
            out.setFloat(tvsLocal.recentsViewScroll, AnimatedFloat.VALUE, 0,
                    TOUCH_RESPONSE);
            out.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    final SurfaceTransaction showTransaction = new SurfaceTransaction();
                    for (int i = targets.apps.length - 1; i >= 0; --i) {
                        showTransaction.getTransaction().show(targets.apps[i].leash);
                    }
                    applier.scheduleApply(showTransaction);
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
            } else {
                // There is no transition animation for app launch from recent in live tile mode so
                // we have to trigger the navigation bar animation from system here.
                final RecentsAnimationController controller =
                        recentsView.getRecentsAnimationController();
                if (controller != null) {
                    controller.animateNavigationBarToApp(RECENTS_LAUNCH_DURATION);
                }
            }
            topMostSimulators = remoteTargetHandles;
        }

        if (!skipViewChanges && parallaxCenterAndAdjacentTask && topMostSimulators != null
                && topMostSimulators.length > 0) {
            out.addFloat(v, VIEW_ALPHA, 1, 0, clampToProgress(LINEAR, 0.2f, 0.4f));

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
            TaskThumbnailViewDeprecated[] thumbnails = v.getThumbnailViews();

            // In case simulator copies and thumbnail size do no match, ensure we get the lesser.
            // This ensures we do not create arrays with empty elements or attempt to references
            // indexes out of array bounds.
            final int matrixSize = Math.min(simulatorCopies.length, thumbnails.length);

            Matrix[] mt = new Matrix[matrixSize];
            Matrix[] mti = new Matrix[matrixSize];
            for (int i = 0; i < matrixSize; i++) {
                TaskThumbnailViewDeprecated ttv = thumbnails[i];
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
                    for (TaskThumbnailViewDeprecated ttv : thumbnails) {
                        ttv.setAnimationMatrix(null);
                    }
                }
            });
        }

        out.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationStart(Animator animation) {
                for (RemoteTargetHandle remoteTargetHandle : remoteTargetHandles) {
                    remoteTargetHandle.getTaskViewSimulator().setDrawsBelowRecents(false);
                }
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
                    BACKGROUND_APP.getDepth(baseActivity), TOUCH_RESPONSE);
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
                depthController);

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
                    recentsView, depthController);
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
    public static void composeRecentsDesktopLaunchAnimator(
            @NonNull DesktopTaskView launchingTaskView,
            @NonNull StateManager stateManager, @Nullable DepthController depthController,
            @NonNull TransitionInfo transitionInfo,
            SurfaceControl.Transaction t, @NonNull Runnable finishCallback) {

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
                depthController);

        animatorSet.start();
    }

    public static void composeRecentsLaunchAnimator(@NonNull AnimatorSet anim, @NonNull View v,
            @NonNull RemoteAnimationTarget[] appTargets,
            @NonNull RemoteAnimationTarget[] wallpaperTargets,
            @NonNull RemoteAnimationTarget[] nonAppTargets, boolean launcherClosing,
            @NonNull StateManager stateManager, @NonNull RecentsView recentsView,
            @Nullable DepthController depthController) {
        boolean skipLauncherChanges = !launcherClosing;

        TaskView taskView = findTaskViewToLaunch(recentsView, v, appTargets);
        PendingAnimation pa = new PendingAnimation(RECENTS_LAUNCH_DURATION);
        createRecentsWindowAnimator(recentsView, taskView, skipLauncherChanges, appTargets,
                wallpaperTargets, nonAppTargets, depthController, pa);
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
                    recentsView.finishRecentsAnimation(false /* toRecents */, () -> {
                        recentsView.post(() -> {
                            stateManager.moveToRestState();
                            stateManager.reapplyState();

                            // We may have notified launcher is not visible so that taskbar can
                            // stash immediately. Now that the animation is over, we can update
                            // that launcher is still visible.
                            TaskbarUIController controller = recentsView.getSizeStrategy()
                                    .getTaskbarController();
                            if (controller != null) {
                                boolean launcherVisible = true;
                                for (RemoteAnimationTarget target : appTargets) {
                                    launcherVisible &= target.isTranslucent;
                                }
                                if (launcherVisible) {
                                    controller.onLauncherVisibilityChanged(true);
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
                    recentsView.finishRecentsAnimation(false /* toRecents */,
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

        List<SurfaceControl> auxiliarySurfaces = new ArrayList<>();
        for (RemoteAnimationTarget target : nonApps) {
            final SurfaceControl leash = target.leash;
            if (target.windowType == TYPE_DOCK_DIVIDER && leash != null && leash.isValid()) {
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
            for (SurfaceControl leash : auxiliarySurfaces) {
                if (leash != null && leash.isValid()) {
                    t.setAlpha(leash, shown ? progress : 1 - progress);
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
}

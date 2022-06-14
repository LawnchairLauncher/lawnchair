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

import static android.view.WindowManager.LayoutParams.TYPE_DOCK_DIVIDER;
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
import static com.android.launcher3.QuickstepTransitionManager.SPLIT_DIVIDER_ANIM_DURATION;
import static com.android.launcher3.QuickstepTransitionManager.SPLIT_LAUNCH_DURATION;
import static com.android.launcher3.Utilities.getDescendantCoordRelativeToAncestor;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.Interpolators.TOUCH_RESPONSE_INTERPOLATOR;
import static com.android.launcher3.anim.Interpolators.clampToProgress;
import static com.android.launcher3.config.FeatureFlags.ENABLE_QUICKSTEP_LIVE_TILE;
import static com.android.launcher3.statehandlers.DepthController.DEPTH;
import static com.android.launcher3.testing.TestProtocol.BAD_STATE;
import static com.android.quickstep.TaskAnimationManager.ENABLE_SHELL_TRANSITIONS;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_CLOSING;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_OPENING;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.Log;
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
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.RemoteTargetGluer.RemoteTargetHandle;
import com.android.quickstep.util.MultiValueUpdateListener;
import com.android.quickstep.util.SurfaceTransactionApplier;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.views.GroupedTaskView;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskThumbnailView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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
        TaskView taskView = recentsView.getTaskViewByTaskId(openingTaskId);
        if (taskView == null || !recentsView.isTaskViewVisible(taskView)) {
            return null;
        }
        return taskView;
    }

    public static void createRecentsWindowAnimator(
            @NonNull TaskView v, boolean skipViewChanges,
            @NonNull RemoteAnimationTargetCompat[] appTargets,
            @NonNull RemoteAnimationTargetCompat[] wallpaperTargets,
            @NonNull RemoteAnimationTargetCompat[] nonAppTargets,
            @Nullable DepthController depthController,
            PendingAnimation out) {
        RecentsView recentsView = v.getRecentsView();
        boolean isQuickSwitch = v.isEndQuickswitchCuj();
        v.setEndQuickswitchCuj(false);

        boolean inLiveTileMode =
                ENABLE_QUICKSTEP_LIVE_TILE.get() && v.getRecentsView().getRunningTaskIndex() != -1;
        final RemoteAnimationTargets targets =
                new RemoteAnimationTargets(appTargets, wallpaperTargets, nonAppTargets,
                        inLiveTileMode ? MODE_CLOSING : MODE_OPENING);
        final RemoteAnimationTargetCompat navBarTarget = targets.getNavBarRemoteAnimationTarget();

        SurfaceTransactionApplier applier = new SurfaceTransactionApplier(v);
        targets.addReleaseCheck(applier);

        RemoteTargetHandle[] remoteTargetHandles;
        RemoteTargetHandle[] recentsViewHandles = recentsView.getRemoteTargetHandles();
        if (v.isRunningTask() && recentsViewHandles != null) {
            // Re-use existing handles
            remoteTargetHandles = recentsViewHandles;
        } else {
            RemoteTargetGluer gluer = new RemoteTargetGluer(v.getContext(),
                    recentsView.getSizeStrategy(), targets);
            if (v.containsMultipleTasks()) {
                remoteTargetHandles = gluer.assignTargetsForSplitScreen(targets, v.getTaskIds());
            } else {
                remoteTargetHandles = gluer.assignTargets(targets);
            }
        }
        for (RemoteTargetHandle remoteTargetGluer : remoteTargetHandles) {
            remoteTargetGluer.getTransformParams().setSyncTransactionApplier(applier);
        }

        int taskIndex = recentsView.indexOfChild(v);
        Context context = v.getContext();
        DeviceProfile dp = BaseActivity.fromContext(context).getDeviceProfile();
        boolean showAsGrid = dp.overviewShowAsGrid;
        boolean parallaxCenterAndAdjacentTask =
                taskIndex != recentsView.getCurrentPage() && !showAsGrid;
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
                    AnimatedFloat.VALUE, 1, TOUCH_RESPONSE_INTERPOLATOR);
            out.setFloat(tvsLocal.recentsViewScale,
                    AnimatedFloat.VALUE, tvsLocal.getFullScreenScale(),
                    TOUCH_RESPONSE_INTERPOLATOR);
            out.setFloat(tvsLocal.recentsViewScroll, AnimatedFloat.VALUE, 0,
                    TOUCH_RESPONSE_INTERPOLATOR);

            out.addOnFrameCallback(() -> {
                for (RemoteTargetHandle handle : remoteTargetHandles) {
                    handle.getTaskViewSimulator().apply(handle.getTransformParams());
                }
            });
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

                        // TODO Do we need to operate over multiple TVSs for the navbar leash?
                        for (RemoteTargetHandle handle : remoteTargetHandles) {
                            if (mNavFadeIn.value > mNavFadeIn.getStartValue()) {
                                TaskViewSimulator taskViewSimulator = handle.getTaskViewSimulator();
                                taskViewSimulator.getCurrentCropRect().round(cropRect);
                                navBuilder.withMatrix(taskViewSimulator.getCurrentMatrix())
                                        .withWindowCrop(cropRect)
                                        .withAlpha(mNavFadeIn.value);
                            } else {
                                navBuilder.withAlpha(mNavFadeOut.value);
                            }
                            handle.getTransformParams().applySurfaceParams(navBuilder.build());
                        }
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
            topMostSimulators = remoteTargetHandles;
        }

        if (!skipViewChanges && parallaxCenterAndAdjacentTask && topMostSimulators.length > 0) {
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
            TaskThumbnailView[] thumbnails = v.getThumbnails();
            Matrix[] mt = new Matrix[simulatorCopies.length];
            Matrix[] mti = new Matrix[simulatorCopies.length];
            for (int i = 0; i < thumbnails.length; i++) {
                TaskThumbnailView ttv = thumbnails[i];
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
            }

            Matrix[] k0i = new Matrix[simulatorCopies.length];
            for (int i = 0; i < simulatorCopies.length; i++) {
                k0i[i] = new Matrix();
                simulatorCopies[i].getTaskViewSimulator().getCurrentMatrix().invert(k0i[i]);
            }
            Matrix animationMatrix = new Matrix();
            out.addOnFrameCallback(() -> {
                for (int i = 0; i < simulatorCopies.length; i++) {
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
                    for (TaskThumbnailView ttv : thumbnails) {
                        ttv.setAnimationMatrix(null);
                    }
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
    public static void composeRecentsSplitLaunchAnimator(@NonNull Task initalTask,
            @NonNull Task secondTask, @NonNull TransitionInfo transitionInfo,
            SurfaceControl.Transaction t, @NonNull Runnable finishCallback) {

        final TransitionInfo.Change[] splitRoots = new TransitionInfo.Change[2];
        for (int i = 0; i < transitionInfo.getChanges().size(); ++i) {
            final TransitionInfo.Change change = transitionInfo.getChanges().get(i);
            final int taskId = change.getTaskInfo() != null ? change.getTaskInfo().taskId : -1;
            final int mode = change.getMode();
            // Find the target tasks' root tasks since those are the split stages that need to
            // be animated (the tasks themselves are children and thus inherit animation).
            if (taskId == initalTask.key.id || taskId == secondTask.key.id) {
                if (!(mode == TRANSIT_OPEN || mode == TRANSIT_TO_FRONT)) {
                    throw new IllegalStateException(
                            "Expected task to be showing, but it is " + mode);
                }
                if (change.getParent() == null) {
                    throw new IllegalStateException("Initiating multi-split launch but the split"
                            + "root of " + taskId + " is already visible or has broken hierarchy.");
                }
                splitRoots[taskId == initalTask.key.id ? 0 : 1] =
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

    /**
     * Legacy version (until shell transitions are enabled)
     *
     * If {@param launchingTaskView} is not null, then this will play the tasks launch animation
     * from the position of the GroupedTaskView (when user taps on the TaskView to start it).
     * Technically this case should be taken care of by
     * {@link #composeRecentsSplitLaunchAnimatorLegacy()} below, but the way we launch tasks whether
     * it's a single task or multiple tasks results in different entry-points.
     *
     * If it is null, then it will simply fade in the starting apps and fade out launcher (for the
     * case where launcher handles animating starting split tasks from app icon) */
    public static void composeRecentsSplitLaunchAnimatorLegacy(
            @Nullable GroupedTaskView launchingTaskView,
            @NonNull Task initialTask,
            @NonNull Task secondTask, @NonNull RemoteAnimationTargetCompat[] appTargets,
            @NonNull RemoteAnimationTargetCompat[] wallpaperTargets,
            @NonNull RemoteAnimationTargetCompat[] nonAppTargets,
            @NonNull StateManager stateManager,
            @Nullable DepthController depthController,
            @NonNull Runnable finishCallback) {
        if (launchingTaskView != null) {
            AnimatorSet animatorSet = new AnimatorSet();
            RecentsView recentsView = launchingTaskView.getRecentsView();
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
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
        for (RemoteAnimationTargetCompat appTarget : appTargets) {
            final int taskId = appTarget.taskInfo != null ? appTarget.taskInfo.taskId : -1;
            final int mode = appTarget.mode;
            final SurfaceControl leash = appTarget.leash.getSurfaceControl();
            if (leash == null) {
                continue;
            }

            if (mode == MODE_OPENING) {
                openingTargets.add(leash);
            } else if (taskId == initialTask.key.id || taskId == secondTask.key.id) {
                throw new IllegalStateException("Expected task to be opening, but it is " + mode);
            } else if (mode == MODE_CLOSING) {
                closingTargets.add(leash);
            }
        }

        for (int i = 0; i < nonAppTargets.length; ++i) {
            final SurfaceControl leash = appTargets[i].leash.getSurfaceControl();
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
            for (SurfaceControl leash: closingTargets) {
                t.setAlpha(leash, 1 - progress);
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
                super.onAnimationEnd(animation);
                finishCallback.run();
            }
        });
        animator.start();
    }

    public static void composeRecentsLaunchAnimator(@NonNull AnimatorSet anim, @NonNull View v,
            @NonNull RemoteAnimationTargetCompat[] appTargets,
            @NonNull RemoteAnimationTargetCompat[] wallpaperTargets,
            @NonNull RemoteAnimationTargetCompat[] nonAppTargets, boolean launcherClosing,
            @NonNull StateManager stateManager, @NonNull RecentsView recentsView,
            @Nullable DepthController depthController) {
        boolean skipLauncherChanges = !launcherClosing;

        TaskView taskView = findTaskViewToLaunch(recentsView, v, appTargets);
        PendingAnimation pa = new PendingAnimation(RECENTS_LAUNCH_DURATION);
        createRecentsWindowAnimator(taskView, skipLauncherChanges, appTargets, wallpaperTargets,
                nonAppTargets, depthController, pa);
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
            Context context = v.getContext();
            DeviceProfile dp = BaseActivity.fromContext(context).getDeviceProfile();
            launcherAnim = dp.overviewShowAsGrid
                    ? ObjectAnimator.ofFloat(recentsView, RecentsView.CONTENT_ALPHA, 0)
                    : recentsView.createAdjacentPageAnimForTaskLaunch(taskView);
            if (dp.isTablet) {
                Log.d(BAD_STATE, "TVU composeRecentsLaunchAnimator alpha=" + 0);
                launcherAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        Log.d(BAD_STATE, "TVU composeRecentsLaunchAnimator onStart");
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        float alpha = recentsView == null
                                ? -1
                                : RecentsView.CONTENT_ALPHA.get(recentsView);
                        Log.d(BAD_STATE, "TVU composeRecentsLaunchAnimator onCancel, alpha="
                                + alpha);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        Log.d(BAD_STATE, "TVU composeRecentsLaunchAnimator onEnd");
                    }
                });
            }
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

    /**
     * Creates an animation to show/hide the auxiliary surfaces (aka. divider bar), only calling
     * {@param animatorHandler} if there are valid surfaces to animate.
     *
     * @return the animator animating the surfaces
     */
    public static ValueAnimator createSplitAuxiliarySurfacesAnimator(
            RemoteAnimationTargetCompat[] nonApps, boolean shown,
            Consumer<ValueAnimator> animatorHandler) {
        if (nonApps == null || nonApps.length == 0) {
            return null;
        }

        SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        List<SurfaceControl> auxiliarySurfaces = new ArrayList<>(nonApps.length);
        boolean hasSurfaceToAnimate = false;
        for (int i = 0; i < nonApps.length; ++i) {
            final RemoteAnimationTargetCompat targ = nonApps[i];
            final SurfaceControl leash = targ.leash.getSurfaceControl();
            if (targ.windowType == TYPE_DOCK_DIVIDER && leash != null) {
                auxiliarySurfaces.add(leash);
                hasSurfaceToAnimate = true;
            }
        }
        if (!hasSurfaceToAnimate) {
            return null;
        }

        ValueAnimator dockFadeAnimator = ValueAnimator.ofFloat(0f, 1f);
        dockFadeAnimator.addUpdateListener(valueAnimator -> {
            float progress = valueAnimator.getAnimatedFraction();
            for (SurfaceControl leash : auxiliarySurfaces) {
                t.setAlpha(leash, shown ? progress : 1 - progress);
            }
            t.apply();
        });
        dockFadeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                if (shown) {
                    for (SurfaceControl leash : auxiliarySurfaces) {
                        t.setAlpha(leash, 0);
                        t.show(leash);
                    }
                    t.apply();
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (!shown) {
                    for (SurfaceControl leash : auxiliarySurfaces) {
                        t.hide(leash);
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

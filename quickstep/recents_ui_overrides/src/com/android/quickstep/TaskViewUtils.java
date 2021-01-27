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

import static com.android.launcher3.LauncherAnimUtils.VIEW_ALPHA;
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.Utilities.getDescendantCoordRelativeToAncestor;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.Interpolators.TOUCH_RESPONSE_INTERPOLATOR;
import static com.android.launcher3.anim.Interpolators.clampToProgress;
import static com.android.launcher3.statehandlers.DepthController.DEPTH;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_OPENING;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Matrix.ScaleToFit;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.util.DefaultDisplay;
import com.android.quickstep.util.SurfaceTransactionApplier;
import com.android.quickstep.util.TaskViewSimulator;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskThumbnailView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

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
            BaseDraggingActivity activity, View v, RemoteAnimationTargetCompat[] targets) {
        RecentsView recentsView = activity.getOverviewPanel();
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

    /**
     * Creates an animation that controls the window of the opening targets for the recents launch
     * animation.
     */
    public static void createRecentsWindowAnimator(TaskView v, boolean skipViewChanges,
            RemoteAnimationTargetCompat[] appTargets,
            RemoteAnimationTargetCompat[] wallpaperTargets, DepthController depthController,
            PendingAnimation out) {

        SurfaceTransactionApplier applier = new SurfaceTransactionApplier(v);
        final RemoteAnimationTargets targets =
                new RemoteAnimationTargets(appTargets, wallpaperTargets, MODE_OPENING);
        targets.addReleaseCheck(applier);

        TransformParams params = new TransformParams()
                    .setSyncTransactionApplier(applier)
                    .setTargetSet(targets);

        final RecentsView recentsView = v.getRecentsView();
        int taskIndex = recentsView.indexOfChild(v);
        boolean parallaxCenterAndAdjacentTask = taskIndex != recentsView.getCurrentPage();
        int startScroll = recentsView.getScrollOffset(taskIndex);

        Context context = v.getContext();
        DeviceProfile dp = BaseActivity.fromContext(context).getDeviceProfile();
        // RecentsView never updates the display rotation until swipe-up so the value may be stale.
        // Use the display value instead.
        int displayRotation = DefaultDisplay.INSTANCE.get(context).getInfo().rotation;

        TaskViewSimulator topMostSimulator = null;
        if (targets.apps.length > 0) {
            TaskViewSimulator tsv = new TaskViewSimulator(context, recentsView.getSizeStrategy());
            tsv.setDp(dp);
            tsv.setLayoutRotation(displayRotation, displayRotation);
            tsv.setPreview(targets.apps[targets.apps.length - 1]);
            tsv.fullScreenProgress.value = 0;
            tsv.recentsViewScale.value = 1;
            tsv.setScroll(startScroll);

            out.setFloat(tsv.fullScreenProgress,
                    AnimatedFloat.VALUE, 1, TOUCH_RESPONSE_INTERPOLATOR);
            out.setFloat(tsv.recentsViewScale,
                    AnimatedFloat.VALUE, tsv.getFullScreenScale(), TOUCH_RESPONSE_INTERPOLATOR);
            out.setInt(tsv, TaskViewSimulator.SCROLL, 0, TOUCH_RESPONSE_INTERPOLATOR);

            out.addOnFrameCallback(() -> tsv.apply(params));
            topMostSimulator = tsv;
        }

        // Fade in the task during the initial 20% of the animation
        out.addFloat(params, TransformParams.TARGET_ALPHA, 0, 1, clampToProgress(LINEAR, 0, 0.2f));

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

        out.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                targets.release();
            }
        });

        if (depthController != null) {
            out.setFloat(depthController, DEPTH, BACKGROUND_APP.getDepth(context),
                    TOUCH_RESPONSE_INTERPOLATOR);
        }
    }
}

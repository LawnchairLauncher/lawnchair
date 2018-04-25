/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.Interpolators.TOUCH_RESPONSE_INTERPOLATOR;
import static com.android.systemui.shared.recents.utilities.Utilities.getNextFrameNumber;
import static com.android.systemui.shared.recents.utilities.Utilities.getSurface;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_OPENING;

import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.UserHandle;
import android.util.Log;
import android.view.Surface;
import android.view.View;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.util.ComponentKey;
import com.android.quickstep.RecentsAnimationInterpolator.TaskWindowBounds;
import com.android.quickstep.util.MultiValueUpdateListener;
import com.android.quickstep.util.RemoteAnimationProvider;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.recents.model.Task;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.TransactionCompat;

/**
 * Contains helpful methods for retrieving data from {@link Task}s.
 */
public class TaskUtils {

    private static final String TAG = "TaskUtils";

    /**
     * TODO: remove this once we switch to getting the icon and label from IconCache.
     */
    public static CharSequence getTitle(Context context, Task task) {
        LauncherAppsCompat launcherAppsCompat = LauncherAppsCompat.getInstance(context);
        UserManagerCompat userManagerCompat = UserManagerCompat.getInstance(context);
        PackageManager packageManager = context.getPackageManager();
        UserHandle user = UserHandle.of(task.key.userId);
        ApplicationInfo applicationInfo = launcherAppsCompat.getApplicationInfo(
            task.getTopComponent().getPackageName(), 0, user);
        if (applicationInfo == null) {
            Log.e(TAG, "Failed to get title for task " + task);
            return "";
        }
        return userManagerCompat.getBadgedLabelForUser(
            applicationInfo.loadLabel(packageManager), user);
    }

    public static ComponentKey getComponentKeyForTask(Task.TaskKey taskKey) {
        return new ComponentKey(taskKey.getComponent(), UserHandle.of(taskKey.userId));
    }


    /**
     * Try to find a TaskView that corresponds with the component of the launched view.
     *
     * If this method returns a non-null TaskView, it will be used in composeRecentsLaunchAnimation.
     * Otherwise, we will assume we are using a normal app transition, but it's possible that the
     * opening remote target (which we don't get until onAnimationStart) will resolve to a TaskView.
     */
    public static TaskView findTaskViewToLaunch(
            BaseDraggingActivity activity, View v, RemoteAnimationTargetCompat[] targets) {
        if (v instanceof TaskView) {
            return (TaskView) v;
        }
        RecentsView recentsView = activity.getOverviewPanel();

        // It's possible that the launched view can still be resolved to a visible task view, check
        // the task id of the opening task and see if we can find a match.
        if (v.getTag() instanceof ItemInfo) {
            ItemInfo itemInfo = (ItemInfo) v.getTag();
            ComponentName componentName = itemInfo.getTargetComponent();
            if (componentName != null) {
                for (int i = 0; i < recentsView.getChildCount(); i++) {
                    TaskView taskView = recentsView.getPageAt(i);
                    if (recentsView.isTaskViewVisible(taskView)) {
                        Task task = taskView.getTask();
                        if (componentName.equals(task.key.getComponent())) {
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
     * @return Animator that controls the window of the opening targets for the recents launch
     * animation.
     */
    public static ValueAnimator getRecentsWindowAnimator(TaskView v, boolean skipViewChanges,
            RemoteAnimationTargetCompat[] targets) {
        final RecentsAnimationInterpolator recentsInterpolator = v.getRecentsInterpolator();

        Rect crop = new Rect();
        Matrix matrix = new Matrix();

        ValueAnimator appAnimator = ValueAnimator.ofFloat(0, 1);
        appAnimator.setInterpolator(TOUCH_RESPONSE_INTERPOLATOR);
        appAnimator.addUpdateListener(new MultiValueUpdateListener() {

            // Defer fading out the view until after the app window gets faded in
            FloatProp mViewAlpha = new FloatProp(1f, 0f, 75, 75, LINEAR);
            FloatProp mTaskAlpha = new FloatProp(0f, 1f, 0, 75, LINEAR);

            boolean isFirstFrame = true;

            @Override
            public void onUpdate(float percent) {
                final Surface surface = getSurface(v);
                final long frameNumber = surface != null ? getNextFrameNumber(surface) : -1;
                if (frameNumber == -1) {
                    // Booo, not cool! Our surface got destroyed, so no reason to animate anything.
                    Log.w(TAG, "Failed to animate, surface got destroyed.");
                    return;
                }
                TaskWindowBounds tw = recentsInterpolator.interpolate(percent);

                if (!skipViewChanges) {
                    v.setScaleX(tw.taskScale);
                    v.setScaleY(tw.taskScale);
                    v.setTranslationX(tw.taskX);
                    v.setTranslationY(tw.taskY);
                    v.setAlpha(mViewAlpha.value);
                }

                matrix.setScale(tw.winScale, tw.winScale);
                matrix.postTranslate(tw.winX, tw.winY);
                crop.set(tw.winCrop);

                TransactionCompat t = new TransactionCompat();
                if (isFirstFrame) {
                    RemoteAnimationProvider.prepareTargetsForFirstFrame(targets, t, MODE_OPENING);
                    isFirstFrame = false;
                }
                for (RemoteAnimationTargetCompat target : targets) {
                    if (target.mode == RemoteAnimationTargetCompat.MODE_OPENING) {
                        t.setAlpha(target.leash, mTaskAlpha.value);

                        // TODO: This isn't correct at the beginning of the animation, but better
                        // than nothing.
                        matrix.postTranslate(target.position.x, target.position.y);
                        t.setMatrix(target.leash, matrix);
                        t.setWindowCrop(target.leash, crop);

                        if (!skipViewChanges) {
                            t.deferTransactionUntil(target.leash, surface, frameNumber);
                        }
                    }
                }
                t.apply();

                matrix.reset();
            }
        });
        return appAnimator;
    }

    public static boolean taskIsATargetWithMode(RemoteAnimationTargetCompat[] targets,
            int taskId, int mode) {
        for (RemoteAnimationTargetCompat target : targets) {
            if (target.mode == mode && target.taskId == taskId) {
                return true;
            }
        }
        return false;
    }
}

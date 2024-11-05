/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.desktopmode;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;

import static com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.getEnterTransitionType;
import static com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.isEnterDesktopModeTransition;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.RectEvaluator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.graphics.Rect;
import android.os.IBinder;
import android.util.Slog;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.view.WindowManager.TransitionType;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.wm.shell.common.desktopmode.DesktopModeTransitionSource;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.windowdecor.OnTaskResizeAnimationListener;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The {@link Transitions.TransitionHandler} that handles transitions for desktop mode tasks
 * entering and exiting freeform.
 */
public class EnterDesktopTaskTransitionHandler implements Transitions.TransitionHandler {

    private static final String TAG = "EnterDesktopTaskTransitionHandler";
    private final Transitions mTransitions;
    private final Supplier<SurfaceControl.Transaction> mTransactionSupplier;

    public static final int FREEFORM_ANIMATION_DURATION = 336;

    private final List<IBinder> mPendingTransitionTokens = new ArrayList<>();

    private OnTaskResizeAnimationListener mOnTaskResizeAnimationListener;

    public EnterDesktopTaskTransitionHandler(
            Transitions transitions) {
        this(transitions, SurfaceControl.Transaction::new);
    }

    public EnterDesktopTaskTransitionHandler(
            Transitions transitions,
            Supplier<SurfaceControl.Transaction> supplier) {
        mTransitions = transitions;
        mTransactionSupplier = supplier;
    }

    void setOnTaskResizeAnimationListener(OnTaskResizeAnimationListener listener) {
        mOnTaskResizeAnimationListener = listener;
    }

    /**
     * Starts Transition of type TRANSIT_MOVE_TO_DESKTOP
     *
     * @param wct WindowContainerTransaction for transition
     * @return the token representing the started transition
     */
    public IBinder moveToDesktop(
            @NonNull WindowContainerTransaction wct,
            DesktopModeTransitionSource transitionSource
    ) {
        final IBinder token = mTransitions.startTransition(getEnterTransitionType(transitionSource),
                wct, this);
        mPendingTransitionTokens.add(token);
        return token;
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        boolean transitionHandled = false;
        for (TransitionInfo.Change change : info.getChanges()) {
            if ((change.getFlags() & TransitionInfo.FLAG_IS_WALLPAPER) != 0) {
                continue;
            }

            final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
            if (taskInfo == null || taskInfo.taskId == -1) {
                continue;
            }

            if (change.getMode() == WindowManager.TRANSIT_CHANGE) {
                transitionHandled |= startChangeTransition(
                        transition, info.getType(), change, startT, finishT, finishCallback);
            }
        }

        mPendingTransitionTokens.remove(transition);

        return transitionHandled;
    }

    private boolean startChangeTransition(
            @NonNull IBinder transition,
            @TransitionType int type,
            @NonNull TransitionInfo.Change change,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        if (!mPendingTransitionTokens.contains(transition)) {
            return false;
        }

        final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
        if (isEnterDesktopModeTransition(type)
                && taskInfo.getWindowingMode() == WINDOWING_MODE_FREEFORM) {
            return animateMoveToDesktop(change, startT, finishCallback);
        }

        return false;
    }

    private boolean animateMoveToDesktop(
            @NonNull TransitionInfo.Change change,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        final SurfaceControl leash = change.getLeash();
        final Rect startBounds = change.getStartAbsBounds();
        final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
        if (mOnTaskResizeAnimationListener == null) {
            Slog.e(TAG, "onTaskResizeAnimationListener is not available for this transition");
            return false;
        }

        startT.setPosition(leash, startBounds.left, startBounds.top)
                .setWindowCrop(leash, startBounds.width(), startBounds.height())
                .show(leash);
        mOnTaskResizeAnimationListener.onAnimationStart(taskInfo.taskId, startT, startBounds);
        final ValueAnimator animator = ValueAnimator.ofObject(new RectEvaluator(),
                change.getStartAbsBounds(), change.getEndAbsBounds());
        animator.setDuration(FREEFORM_ANIMATION_DURATION);
        SurfaceControl.Transaction t = mTransactionSupplier.get();
        animator.addUpdateListener(animation -> {
            final Rect animationValue = (Rect) animator.getAnimatedValue();
            t.setPosition(leash, animationValue.left, animationValue.top)
                    .setWindowCrop(leash, animationValue.width(), animationValue.height())
                    .show(leash);
            mOnTaskResizeAnimationListener.onBoundsChange(taskInfo.taskId, t, animationValue);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mOnTaskResizeAnimationListener.onAnimationEnd(taskInfo.taskId);
                mTransitions.getMainExecutor().execute(
                        () -> finishCallback.onTransitionFinished(null));
            }
        });
        animator.start();
        return true;
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        return null;
    }
}

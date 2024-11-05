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

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;

import static com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.getExitTransitionType;
import static com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.isExitDesktopModeTransition;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.view.WindowManager.TransitionType;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.wm.shell.common.desktopmode.DesktopModeTransitionSource;
import com.android.wm.shell.transition.Transitions;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;


/**
 * The {@link Transitions.TransitionHandler} that handles transitions for desktop mode tasks
 * entering and exiting freeform.
 */
public class ExitDesktopTaskTransitionHandler implements Transitions.TransitionHandler {
    private static final int FULLSCREEN_ANIMATION_DURATION = 336;

    private final Context mContext;
    private final Transitions mTransitions;
    private final List<IBinder> mPendingTransitionTokens = new ArrayList<>();
    private Consumer<SurfaceControl.Transaction> mOnAnimationFinishedCallback;
    private final Supplier<SurfaceControl.Transaction> mTransactionSupplier;
    private Point mPosition;

    public ExitDesktopTaskTransitionHandler(
            Transitions transitions,
            Context context) {
        this(transitions, SurfaceControl.Transaction::new, context);
    }

    private ExitDesktopTaskTransitionHandler(
            Transitions transitions,
            Supplier<SurfaceControl.Transaction> supplier,
            Context context) {
        mTransitions = transitions;
        mTransactionSupplier = supplier;
        mContext = context;
    }

    /**
     * Starts Transition of a given type
     *
     * @param transitionSource       DesktopModeTransitionSource for transition
     * @param wct                    WindowContainerTransaction for transition
     * @param position               Position of the task when transition is started
     * @param onAnimationEndCallback to be called after animation
     */
    public void startTransition(@NonNull DesktopModeTransitionSource transitionSource,
            @NonNull WindowContainerTransaction wct, Point position,
            Consumer<SurfaceControl.Transaction> onAnimationEndCallback) {
        mPosition = position;
        mOnAnimationFinishedCallback = onAnimationEndCallback;
        final IBinder token = mTransitions.startTransition(getExitTransitionType(transitionSource),
                wct, this);
        mPendingTransitionTokens.add(token);
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

    @VisibleForTesting
    boolean startChangeTransition(
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
        if (isExitDesktopModeTransition(type)
                && taskInfo.getWindowingMode() == WINDOWING_MODE_FULLSCREEN) {
            // This Transition animates a task to fullscreen after being dragged to status bar
            final Resources resources = mContext.getResources();
            final DisplayMetrics metrics = resources.getDisplayMetrics();
            final int screenWidth = metrics.widthPixels;
            final int screenHeight = metrics.heightPixels;
            final SurfaceControl sc = change.getLeash();
            final Rect endBounds = change.getEndAbsBounds();
            // Hide the first (fullscreen) frame because the animation will start from the freeform
            // size.
            startT.hide(sc)
                    .setWindowCrop(sc, endBounds.width(), endBounds.height())
                    .apply();
            final ValueAnimator animator = new ValueAnimator();
            animator.setFloatValues(0f, 1f);
            animator.setDuration(FULLSCREEN_ANIMATION_DURATION);
            // The start bounds contain the correct dimensions of the task but hold the positioning
            // before being dragged to the status bar to transition into fullscreen
            final Rect startBounds = change.getStartAbsBounds();
            final float scaleX = (float) startBounds.width() / screenWidth;
            final float scaleY = (float) startBounds.height() / screenHeight;
            final SurfaceControl.Transaction t = mTransactionSupplier.get();
            animator.addUpdateListener(animation -> {
                float fraction = animation.getAnimatedFraction();
                float currentScaleX = scaleX + ((1 - scaleX) * fraction);
                float currentScaleY = scaleY + ((1 - scaleY) * fraction);
                t.setPosition(sc, mPosition.x * (1 - fraction), mPosition.y * (1 - fraction))
                        .setScale(sc, currentScaleX, currentScaleY)
                        .show(sc)
                        .apply();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (mOnAnimationFinishedCallback != null) {
                        mOnAnimationFinishedCallback.accept(finishT);
                    }
                    mTransitions.getMainExecutor().execute(
                            () -> finishCallback.onTransitionFinished(null));
                }
            });
            animator.start();
            return true;
        }

        return false;
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        return null;
    }
}

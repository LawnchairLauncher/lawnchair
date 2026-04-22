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
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Choreographer;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.view.WindowManager.TransitionType;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.jank.Cuj;
import com.android.internal.jank.InteractionJankMonitor;
import com.android.internal.util.LatencyTracker;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource;
import com.android.wm.shell.transition.Transitions;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;


/**
 * The {@link Transitions.TransitionHandler} that handles transitions for desktop mode tasks
 * entering and exiting freeform.
 */
public class ExitDesktopTaskTransitionHandler implements Transitions.TransitionHandler {
    public static final int FULLSCREEN_ANIMATION_DURATION = 336;

    private final Context mContext;
    private final Transitions mTransitions;
    private final InteractionJankMonitor mInteractionJankMonitor;
    private final LatencyTracker mLatencyTracker;
    @ShellMainThread
    private final Handler mHandler;
    private final List<IBinder> mPendingTransitionTokens = new ArrayList<>();
    private Function0<Unit> mOnAnimationFinishedCallback;
    private final Supplier<SurfaceControl.Transaction> mTransactionSupplier;
    private Point mPosition;

    public ExitDesktopTaskTransitionHandler(
            Transitions transitions,
            Context context,
            InteractionJankMonitor interactionJankMonitor,
            @ShellMainThread Handler handler
    ) {
        this(transitions, SurfaceControl.Transaction::new, context, interactionJankMonitor,
                handler);
    }

    private ExitDesktopTaskTransitionHandler(
            Transitions transitions,
            Supplier<SurfaceControl.Transaction> supplier,
            Context context,
            InteractionJankMonitor interactionJankMonitor,
            @ShellMainThread Handler handler) {
        mTransitions = transitions;
        mTransactionSupplier = supplier;
        mContext = context;
        mInteractionJankMonitor = interactionJankMonitor;
        mLatencyTracker = LatencyTracker.getInstance(mContext);
        mHandler = handler;
    }

    /**
     * Starts Transition of a given type
     *
     * @param transitionSource       DesktopModeTransitionSource for transition
     * @param wct                    WindowContainerTransaction for transition
     * @param position               Position of the task when transition is started
     * @param onAnimationEndCallback to be called after animation
     */
    public IBinder startTransition(@NonNull DesktopModeTransitionSource transitionSource,
            @NonNull WindowContainerTransaction wct, Point position,
            Function0<Unit> onAnimationEndCallback) {
        mLatencyTracker.onActionStart(LatencyTracker.ACTION_DESKTOP_MODE_EXIT_MODE);
        mPosition = position;
        mOnAnimationFinishedCallback = onAnimationEndCallback;
        final IBinder token = mTransitions.startTransition(getExitTransitionType(transitionSource),
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


        if (transitionHandled) {
            mLatencyTracker.onActionEnd(LatencyTracker.ACTION_DESKTOP_MODE_EXIT_MODE);
        }

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
            mInteractionJankMonitor
                    .begin(sc, mContext, mHandler, Cuj.CUJ_DESKTOP_MODE_EXIT_MODE);
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
                        .setFrameTimeline(Choreographer.getInstance().getVsyncId())
                        .apply();
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (mOnAnimationFinishedCallback != null) {
                        mOnAnimationFinishedCallback.invoke();
                    }
                    mInteractionJankMonitor.end(Cuj.CUJ_DESKTOP_MODE_EXIT_MODE);
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

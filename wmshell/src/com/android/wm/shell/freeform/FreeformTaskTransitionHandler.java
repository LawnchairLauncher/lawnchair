/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.freeform;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.view.WindowManager.TRANSIT_PIP;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.WindowConfiguration;
import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.IBinder;
import android.util.ArrayMap;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.jank.InteractionJankMonitor;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.animation.MinimizeAnimator;
import com.android.wm.shell.transition.Transitions;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@link Transitions.TransitionHandler} that handles freeform task maximizing, closing, and
 * restoring transitions.
 */
public class FreeformTaskTransitionHandler
        implements Transitions.TransitionHandler, FreeformTaskTransitionStarter {
    private static final String TAG = "FreeformTaskTransitionHandler";
    private static final int CLOSE_ANIM_DURATION = 400;
    private final Transitions mTransitions;
    private final DisplayController mDisplayController;
    private final ShellExecutor mMainExecutor;
    private final ShellExecutor mAnimExecutor;
    private final Handler mAnimHandler;

    private final List<IBinder> mPendingTransitionTokens = new ArrayList<>();

    private final ArrayMap<IBinder, ArrayList<Animator>> mAnimations = new ArrayMap<>();

    public FreeformTaskTransitionHandler(
            Transitions transitions,
            DisplayController displayController,
            ShellExecutor mainExecutor,
            ShellExecutor animExecutor,
            Handler animHandler) {
        mTransitions = transitions;
        mDisplayController = displayController;
        mMainExecutor = mainExecutor;
        mAnimExecutor = animExecutor;
        mAnimHandler = animHandler;
    }

    @Override
    public void startWindowingModeTransition(
            int targetWindowingMode, WindowContainerTransaction wct) {
        final int type;
        switch (targetWindowingMode) {
            case WINDOWING_MODE_FULLSCREEN:
                type = Transitions.TRANSIT_MAXIMIZE;
                break;
            case WINDOWING_MODE_FREEFORM:
                type = Transitions.TRANSIT_RESTORE_FROM_MAXIMIZE;
                break;
            default:
                throw new IllegalArgumentException("Unexpected target windowing mode "
                        + WindowConfiguration.windowingModeToString(targetWindowingMode));
        }
        final IBinder token = mTransitions.startTransition(type, wct, this);
        mPendingTransitionTokens.add(token);
    }

    @Override
    public IBinder startMinimizedModeTransition(
            WindowContainerTransaction wct, int taskId, boolean isLastTask) {
        final int type = Transitions.TRANSIT_MINIMIZE;
        final IBinder token = mTransitions.startTransition(type, wct, this);
        mPendingTransitionTokens.add(token);
        return token;
    }

    @Override
    public IBinder startPipTransition(WindowContainerTransaction wct) {
        final IBinder token = mTransitions.startTransition(TRANSIT_PIP, wct, null);
        mPendingTransitionTokens.add(token);
        return token;
    }

    @Override
    public IBinder startRemoveTransition(WindowContainerTransaction wct) {
        final int type = WindowManager.TRANSIT_CLOSE;
        final IBinder transition = mTransitions.startTransition(type, wct, this);
        mPendingTransitionTokens.add(transition);
        return transition;
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        boolean transitionHandled = false;
        final ArrayList<Animator> animations = new ArrayList<>();
        final Runnable onAnimFinish = () -> mMainExecutor.execute(() -> {
            if (!animations.isEmpty()) return;
            mAnimations.remove(transition);
            finishCallback.onTransitionFinished(null /* wct */);
        });
        for (TransitionInfo.Change change : info.getChanges()) {
            if ((change.getFlags() & TransitionInfo.FLAG_IS_WALLPAPER) != 0) {
                continue;
            }

            final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
            if (taskInfo == null || taskInfo.taskId == -1) {
                continue;
            }

            switch (change.getMode()) {
                case WindowManager.TRANSIT_CHANGE:
                    transitionHandled |= startChangeTransition(
                            transition, info.getType(), change);
                    break;
                case WindowManager.TRANSIT_TO_BACK:
                    transitionHandled |= startMinimizeTransition(
                            transition, info.getType(), change, finishT, animations, onAnimFinish);
                    break;
                case WindowManager.TRANSIT_CLOSE:
                    if (change.getTaskInfo().getWindowingMode() == WINDOWING_MODE_FREEFORM) {
                        transitionHandled |= startCloseTransition(transition, change,
                                finishT, animations, onAnimFinish);
                    }
                    break;
            }
        }
        if (!transitionHandled) {
            return false;
        }
        mAnimations.put(transition, animations);
        // startT must be applied before animations start.
        startT.apply();
        mAnimExecutor.execute(() -> {
            for (Animator anim : animations) {
                anim.start();
            }
        });
        // Run this here in case no animators are created.
        onAnimFinish.run();
        mPendingTransitionTokens.remove(transition);
        return true;
    }

    @Override
    public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT,
            @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        ArrayList<Animator> animations = mAnimations.get(mergeTarget);
        if (animations == null) return;
        mAnimExecutor.execute(() -> {
            for (Animator anim : animations) {
                anim.end();
            }
        });

    }

    private boolean startChangeTransition(
            IBinder transition,
            int type,
            TransitionInfo.Change change) {
        if (!mPendingTransitionTokens.contains(transition)) {
            return false;
        }

        boolean handled = false;
        final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
        if (type == Transitions.TRANSIT_MAXIMIZE
                && taskInfo.getWindowingMode() == WINDOWING_MODE_FULLSCREEN) {
            // TODO: Add maximize animations
            handled = true;
        }

        if (type == Transitions.TRANSIT_RESTORE_FROM_MAXIMIZE
                && taskInfo.getWindowingMode() == WINDOWING_MODE_FREEFORM) {
            // TODO: Add restore animations
            handled = true;
        }

        return handled;
    }

    private boolean startMinimizeTransition(
            IBinder transition,
            int type,
            TransitionInfo.Change change,
            SurfaceControl.Transaction finishT,
            ArrayList<Animator> animations,
            Runnable onAnimFinish) {
        if (!mPendingTransitionTokens.contains(transition)) {
            return false;
        }

        final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
        if (type != Transitions.TRANSIT_MINIMIZE) {
            return false;
        }

        SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        SurfaceControl sc = change.getLeash();
        finishT.hide(sc);
        final Context displayContext =
                mDisplayController.getDisplayContext(taskInfo.displayId);
        if (displayContext == null) {
            Log.w(TAG, "No displayContext for displayId=" + taskInfo.displayId);
            return false;
        }
        final Animator animator = MinimizeAnimator.create(
                displayContext,
                change,
                t,
                (anim) -> {
                    mMainExecutor.execute(() -> {
                        animations.remove(anim);
                        onAnimFinish.run();
                    });
                    return null;
                },
                InteractionJankMonitor.getInstance(),
                mAnimHandler);
        animations.add(animator);
        return true;
    }

    private boolean startCloseTransition(IBinder transition, TransitionInfo.Change change,
            SurfaceControl.Transaction finishT, ArrayList<Animator> animations,
            Runnable onAnimFinish) {
        if (!mPendingTransitionTokens.contains(transition)) return false;
        int screenHeight = mDisplayController
                .getDisplayLayout(change.getTaskInfo().displayId).height();
        ValueAnimator animator = new ValueAnimator();
        animator.setDuration(CLOSE_ANIM_DURATION)
                .setFloatValues(0f, 1f);
        SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        SurfaceControl sc = change.getLeash();
        finishT.hide(sc);
        final Rect startBounds = new Rect(change.getStartAbsBounds());
        animator.addUpdateListener(animation -> {
            final float newTop = startBounds.top + (animation.getAnimatedFraction() * screenHeight);
            t.setPosition(sc, startBounds.left, newTop);
            if (newTop > screenHeight) {
                // At this point the task surface is off-screen, so hide it to prevent flicker
                // failures. See b/377651666.
                t.hide(sc);
            }
            t.apply();
        });
        animator.addListener(
                new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mMainExecutor.execute(() -> {
                            animations.remove(animator);
                            onAnimFinish.run();
                        });
                    }
                });
        animations.add(animator);
        return true;
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        return null;
    }
}

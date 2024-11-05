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

package com.android.wm.shell.activityembedding;

import static android.app.ActivityOptions.ANIM_CUSTOM;
import static android.app.ActivityOptions.ANIM_SCENE_TRANSITION;
import static android.window.TransitionInfo.FLAG_FILLS_TASK;
import static android.window.TransitionInfo.FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY;

import static com.android.wm.shell.transition.DefaultTransitionHandler.isSupportedOverrideAnimation;
import static com.android.wm.shell.transition.Transitions.TRANSIT_TASK_FRAGMENT_DRAG_RESIZE;

import static java.util.Objects.requireNonNull;

import android.content.Context;
import android.graphics.Rect;
import android.os.IBinder;
import android.util.ArrayMap;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionInfo.AnimationOptions;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.window.flags.Flags;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

import java.util.List;

/**
 * Responsible for handling ActivityEmbedding related transitions.
 */
public class ActivityEmbeddingController implements Transitions.TransitionHandler {

    private final Context mContext;
    @VisibleForTesting
    final Transitions mTransitions;
    @VisibleForTesting
    final ActivityEmbeddingAnimationRunner mAnimationRunner;

    /**
     * Keeps track of the currently-running transition callback associated with each transition
     * token.
     */
    private final ArrayMap<IBinder, Transitions.TransitionFinishCallback> mTransitionCallbacks =
            new ArrayMap<>();

    private ActivityEmbeddingController(@NonNull Context context, @NonNull ShellInit shellInit,
            @NonNull Transitions transitions) {
        mContext = requireNonNull(context);
        mTransitions = requireNonNull(transitions);
        mAnimationRunner = new ActivityEmbeddingAnimationRunner(context, this);

        shellInit.addInitCallback(this::onInit, this);
    }

    /**
     * Creates {@link ActivityEmbeddingController}, returns {@code null} if the feature is not
     * supported.
     */
    @Nullable
    public static ActivityEmbeddingController create(@NonNull Context context,
            @NonNull ShellInit shellInit, @NonNull Transitions transitions) {
        return Transitions.ENABLE_SHELL_TRANSITIONS
                ? new ActivityEmbeddingController(context, shellInit, transitions)
                : null;
    }

    /** Registers to handle transitions. */
    public void onInit() {
        mTransitions.addHandler(this);
    }

    /** Whether ActivityEmbeddingController should animate this transition. */
    public boolean shouldAnimate(@NonNull TransitionInfo info) {
        if (info.getType() == TRANSIT_TASK_FRAGMENT_DRAG_RESIZE) {
            // The TRANSIT_TASK_FRAGMENT_DRAG_RESIZE type happens when the user drags the
            // interactive divider to resize the split containers. The content is veiled, so we will
            // handle the transition with a jump cut.
            return true;
        }
        boolean containsEmbeddingChange = false;
        for (TransitionInfo.Change change : info.getChanges()) {
            if (!change.hasFlags(FLAG_FILLS_TASK) && change.hasFlags(
                    FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY)) {
                // Whether the Task contains any ActivityEmbedding split before or after the
                // transition.
                containsEmbeddingChange = true;
            }
        }
        if (!containsEmbeddingChange) {
            // Let the system to play the default animation if there is no ActivityEmbedding split
            // window. This allows to play the app customized animation when there is no embedding,
            // such as the device is in a folded state.
            return false;
        }

        if (containsNonEmbeddedChange(info) && !handleNonEmbeddedChanges(info.getChanges())) {
            return false;
        }

        return shouldAnimateAnimationOptions(info);
    }

    private boolean shouldAnimateAnimationOptions(@NonNull TransitionInfo info) {
        if (!Flags.moveAnimationOptionsToChange()) {
            return shouldAnimateAnimationOptions(info.getAnimationOptions());
        }
        for (TransitionInfo.Change change : info.getChanges()) {
            if (!shouldAnimateAnimationOptions(change.getAnimationOptions())) {
                // If any of override animation is not supported, don't animate the transition.
                return false;
            }
        }
        return true;
    }

    private boolean shouldAnimateAnimationOptions(@Nullable AnimationOptions options) {
        if (options == null) {
            return true;
        }
        // Scene-transition should be handled by app side.
        if (options.getType() == ANIM_SCENE_TRANSITION) {
            return false;
        }
        // The case of ActivityOptions#makeCustomAnimation, Activity#overridePendingTransition,
        // and Activity#overrideActivityTransition are supported.
        if (options.getType() == ANIM_CUSTOM) {
            return true;
        }
        // Use default transition handler to animate other override animation.
        return !isSupportedOverrideAnimation(options);
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {

        if (!shouldAnimate(info)) return false;

        // Start ActivityEmbedding animation.
        mTransitionCallbacks.put(transition, finishCallback);
        mAnimationRunner.startAnimation(transition, info, startTransaction, finishTransaction);
        return true;
    }

    @Override
    public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction t, @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        mAnimationRunner.cancelAnimationFromMerge();
    }

    /** Whether TransitionInfo contains non-ActivityEmbedding embedded window. */
    private boolean containsNonEmbeddedChange(@NonNull TransitionInfo info) {
        for (TransitionInfo.Change change : info.getChanges()) {
            if (!change.hasFlags(FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY)) {
                return true;
            }
        }
        return false;
    }

    private boolean handleNonEmbeddedChanges(List<TransitionInfo.Change> changes) {
        final Rect nonClosingEmbeddedArea = new Rect();
        for (int i = changes.size() - 1; i >= 0; i--) {
            final TransitionInfo.Change change = changes.get(i);
            if (!TransitionUtil.isClosingType(change.getMode())) {
                if (change.hasFlags(FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY)) {
                    nonClosingEmbeddedArea.union(change.getEndAbsBounds());
                    continue;
                }
                // Not able to handle non-embedded container if it is not closing.
                return false;
            }
        }
        for (int i = changes.size() - 1; i >= 0; i--) {
            final TransitionInfo.Change change = changes.get(i);
            if (!change.hasFlags(FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY)
                    && !nonClosingEmbeddedArea.contains(change.getEndAbsBounds())) {
                // Unknown to animate containers outside the area of embedded activities.
                return false;
            }
        }
        // Drop the non-embedded closing change because it is occluded by embedded activities.
        for (int i = changes.size() - 1; i >= 0; i--) {
            final TransitionInfo.Change change = changes.get(i);
            if (!change.hasFlags(FLAG_IN_TASK_WITH_EMBEDDED_ACTIVITY)) {
                changes.remove(i);
            }
        }
        return true;
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        return null;
    }

    @Override
    public void setAnimScaleSetting(float scale) {
        mAnimationRunner.setAnimScaleSetting(scale);
    }

    /** Called when the animation is finished. */
    void onAnimationFinished(@NonNull IBinder transition) {
        final Transitions.TransitionFinishCallback callback =
                mTransitionCallbacks.remove(transition);
        if (callback == null) {
            throw new IllegalStateException("No finish callback found");
        }
        callback.onTransitionFinished(null /* wct */);
    }
}

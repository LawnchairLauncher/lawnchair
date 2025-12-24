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

package com.android.wm.shell.unfold;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.KEYGUARD_VISIBILITY_TRANSIT_FLAGS;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_TO_BACK;

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_TRANSITIONS;
import static com.android.wm.shell.transition.Transitions.TRANSIT_BUBBLE_CONVERT_FLOATING_TO_BAR;

import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.os.Handler;
import android.os.IBinder;
import android.util.Slog;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.bubbles.BubbleTaskUnfoldTransitionMerger;
import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.transition.Transitions.TransitionFinishCallback;
import com.android.wm.shell.transition.Transitions.TransitionHandler;
import com.android.wm.shell.unfold.ShellUnfoldProgressProvider.UnfoldListener;
import com.android.wm.shell.unfold.animation.FullscreenUnfoldTaskAnimator;
import com.android.wm.shell.unfold.animation.SplitTaskUnfoldAnimator;
import com.android.wm.shell.unfold.animation.UnfoldTaskAnimator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Transition handler that is responsible for animating app surfaces when unfolding of foldable
 * devices. It does not handle the folding animation, which is done in
 * {@link UnfoldAnimationController}.
 */
public class UnfoldTransitionHandler implements TransitionHandler, UnfoldListener {

    private static final String TAG = "UnfoldTransitionHandler";
    @VisibleForTesting
    static final int FINISH_ANIMATION_TIMEOUT_MILLIS = 5_000;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            DefaultDisplayChange.DEFAULT_DISPLAY_NO_CHANGE,
            DefaultDisplayChange.DEFAULT_DISPLAY_UNFOLD,
            DefaultDisplayChange.DEFAULT_DISPLAY_FOLD,
    })
    private @interface DefaultDisplayChange {
        int DEFAULT_DISPLAY_NO_CHANGE = 0;
        int DEFAULT_DISPLAY_UNFOLD = 1;
        int DEFAULT_DISPLAY_FOLD = 2;
    }

    private final ShellUnfoldProgressProvider mUnfoldProgressProvider;
    private final Transitions mTransitions;
    private final Optional<BubbleTaskUnfoldTransitionMerger> mBubbleTaskUnfoldTransitionMerger;
    private final Executor mExecutor;
    private final TransactionPool mTransactionPool;
    private final Handler mHandler;

    @Nullable
    private TransitionFinishCallback mFinishCallback;
    @Nullable
    private IBinder mTransition;

    // TODO: b/318803244 - remove when we could guarantee finishing the animation
    //  after startAnimation callback
    private boolean mAnimationFinished = false;
    private float mLastAnimationProgress = 0.0f;
    private final List<UnfoldTaskAnimator> mAnimators = new ArrayList<>();

    private final Runnable mAnimationPlayingTimeoutRunnable = () -> {
        Slog.wtf(TAG, "Timeout occurred when playing the unfold animation, "
                + "force finishing the transition");
        finishTransitionIfNeeded();
    };

    public UnfoldTransitionHandler(ShellInit shellInit,
            ShellUnfoldProgressProvider unfoldProgressProvider,
            FullscreenUnfoldTaskAnimator fullscreenUnfoldAnimator,
            SplitTaskUnfoldAnimator splitUnfoldTaskAnimator,
            TransactionPool transactionPool,
            Executor executor,
            Handler handler,
            Transitions transitions,
            Optional<BubbleTaskUnfoldTransitionMerger> bubbleTaskUnfoldTransitionMerger) {
        mUnfoldProgressProvider = unfoldProgressProvider;
        mTransitions = transitions;
        mTransactionPool = transactionPool;
        mExecutor = executor;
        mHandler = handler;
        mBubbleTaskUnfoldTransitionMerger = bubbleTaskUnfoldTransitionMerger;

        mAnimators.add(splitUnfoldTaskAnimator);
        mAnimators.add(fullscreenUnfoldAnimator);
        // TODO(b/238217847): Temporarily add this check here until we can remove the dynamic
        //                    override for this controller from the base module
        if (unfoldProgressProvider != ShellUnfoldProgressProvider.NO_PROVIDER) {
            shellInit.addInitCallback(this::onInit, this);
        }
    }

    /**
     * Called when the transition handler is initialized.
     */
    public void onInit() {
        for (int i = 0; i < mAnimators.size(); i++) {
            mAnimators.get(i).init();
        }
        mTransitions.addHandler(this);
        mUnfoldProgressProvider.addListener(mExecutor, this);
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull TransitionFinishCallback finishCallback) {
        if (transition != mTransition) return false;

        for (int i = 0; i < mAnimators.size(); i++) {
            final UnfoldTaskAnimator animator = mAnimators.get(i);
            animator.clearTasks();

            info.getChanges().forEach(change -> {
                if (change.getTaskInfo() != null) {
                    ProtoLog.v(WM_SHELL_TRANSITIONS,
                            "startAnimation, check taskInfo: %s, mode: %s, isApplicableTask: %s",
                            change.getTaskInfo(), TransitionInfo.modeToString(change.getMode()),
                            animator.isApplicableTask(change.getTaskInfo()));
                }
                if (change.getTaskInfo() != null && (change.getMode() == TRANSIT_CHANGE
                        || TransitionUtil.isOpeningType(change.getMode()))
                        && animator.isApplicableTask(change.getTaskInfo())) {
                    animator.onTaskAppeared(change.getTaskInfo(), change.getLeash());
                }
            });

            if (animator.hasActiveTasks()) {
                animator.prepareStartTransaction(startTransaction);
                animator.prepareFinishTransaction(finishTransaction);
                animator.start();
            }
        }

        startTransaction.apply();
        mFinishCallback = finishCallback;

        // Shell transition started when unfold animation has already finished,
        // finish shell transition immediately
        if (mAnimationFinished) {
            finishTransitionIfNeeded();
        } else {
            // TODO: b/318803244 - remove timeout handling when we could guarantee that
            //  the animation will be always finished after receiving startAnimation
            mHandler.removeCallbacks(mAnimationPlayingTimeoutRunnable);
            mHandler.postDelayed(mAnimationPlayingTimeoutRunnable, FINISH_ANIMATION_TIMEOUT_MILLIS);
        }

        return true;
    }

    @Override
    public void onStateChangeProgress(float progress) {
        mLastAnimationProgress = progress;

        if (mTransition == null) return;

        SurfaceControl.Transaction transaction = null;

        for (int i = 0; i < mAnimators.size(); i++) {
            final UnfoldTaskAnimator animator = mAnimators.get(i);

            if (animator.hasActiveTasks()) {
                if (transaction == null) {
                    transaction = mTransactionPool.acquire();
                }

                animator.applyAnimationProgress(progress, transaction);
            }
        }

        if (transaction != null) {
            transaction.apply();
            mTransactionPool.release(transaction);
        }
    }

    @Override
    public void onStateChangeFinished() {
        finishTransitionIfNeeded();

        // mLastAnimationProgress is guaranteed to be 0f when folding finishes, see
        // {@link PhysicsBasedUnfoldTransitionProgressProvider#cancelTransition}.
        // We can use it as an indication that the next animation progress events will be related
        // to unfolding, so let's reset mAnimationFinished to 'false' in this case.
        final boolean isFoldingFinished = mLastAnimationProgress == 0f;
        mAnimationFinished = !isFoldingFinished;
    }

    @Override
    public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT,
            @NonNull IBinder mergeTarget,
            @NonNull TransitionFinishCallback finishCallback) {
        if ((info.getFlags() & KEYGUARD_VISIBILITY_TRANSIT_FLAGS) != 0) {
            return;
        }
        // TODO (b/286928742) unfold transition handler should be part of mixed handler to
        //  handle merges better.
        final boolean merged = switch (info.getType()) {
            case TRANSIT_CHANGE, TRANSIT_BUBBLE_CONVERT_FLOATING_TO_BAR ->
                    tryMergeBubbleTaskTransition(info, startT, finishT);
            case TRANSIT_CLOSE -> tryMergeTaskFragmentClose(info, startT);
            default -> false;
        };

        if (!merged) return;

        // Apply changes happening during the unfold animation immediately
        startT.apply();
        finishCallback.onTransitionFinished(null);

        if (getDefaultDisplayChange(info) == DefaultDisplayChange.DEFAULT_DISPLAY_FOLD) {
            // Force-finish current unfold animation as we are processing folding now which doesn't
            // have any animations on the Shell side
            finishTransitionIfNeeded();
        }
    }

    private boolean tryMergeBubbleTaskTransition(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT) {
        for (int i = 0; i < info.getChanges().size(); ++i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            final ActivityManager.RunningTaskInfo taskInfo = change.getTaskInfo();
            if (taskInfo != null
                    && taskInfo.configuration.windowConfiguration.isAlwaysOnTop()) {
                // Tasks that are always on top, excluding bubbles, will handle their own transition
                // as they are on top of everything else. If this is a transition for a bubble task,
                // attempt to merge it. Otherwise skip merging transitions.
                if (mBubbleTaskUnfoldTransitionMerger.isPresent()) {
                    boolean merged =
                            mBubbleTaskUnfoldTransitionMerger
                                    .get()
                                    .mergeTaskWithUnfold(taskInfo, info, change, startT, finishT);
                    if (!merged) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Tries to merge TRANSIT_CLOSE for task fragments
     * @return true if we should continue with merging, false otherwise
     */
    private boolean tryMergeTaskFragmentClose(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startT) {
        boolean shouldMerge = false;
        for (int i = 0; i < info.getChanges().size(); ++i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            final boolean isHideChange = change.getMode() == TRANSIT_TO_BACK
                    || change.getMode() == TRANSIT_CLOSE;

            if (change.getTaskFragmentToken() != null && isHideChange) {
                shouldMerge = true;
                startT.hide(change.getLeash());
            }
        }

        return shouldMerge;
    }

    /** Whether `request` contains an unfold action. */
    public boolean shouldPlayUnfoldAnimation(@NonNull TransitionRequestInfo request) {
        // Unfold animation won't play when animations are disabled
        if (!ValueAnimator.areAnimatorsEnabled()) return false;

        return (request.getType() == TRANSIT_CHANGE
                && getDefaultDisplayChange(request.getDisplayChange())
                == DefaultDisplayChange.DEFAULT_DISPLAY_UNFOLD);
    }

    @DefaultDisplayChange
    private int getDefaultDisplayChange(
            @Nullable TransitionRequestInfo.DisplayChange displayChange) {
        if (displayChange == null) return DefaultDisplayChange.DEFAULT_DISPLAY_NO_CHANGE;

        if (displayChange.getDisplayId() != DEFAULT_DISPLAY) {
            return DefaultDisplayChange.DEFAULT_DISPLAY_NO_CHANGE;
        }

        if (!displayChange.isPhysicalDisplayChanged()) {
            return DefaultDisplayChange.DEFAULT_DISPLAY_NO_CHANGE;
        }

        if (displayChange.getStartAbsBounds() == null || displayChange.getEndAbsBounds() == null) {
            return DefaultDisplayChange.DEFAULT_DISPLAY_NO_CHANGE;
        }

        // Handle only unfolding, currently we don't have an animation when folding
        final int endArea =
                displayChange.getEndAbsBounds().width() * displayChange.getEndAbsBounds().height();
        final int startArea = displayChange.getStartAbsBounds().width()
                * displayChange.getStartAbsBounds().height();

        return endArea > startArea ? DefaultDisplayChange.DEFAULT_DISPLAY_UNFOLD
                : DefaultDisplayChange.DEFAULT_DISPLAY_FOLD;
    }

    private int getDefaultDisplayChange(@NonNull TransitionInfo transitionInfo) {
        for (int i = 0; i < transitionInfo.getChanges().size(); i++) {
            final TransitionInfo.Change change = transitionInfo.getChanges().get(i);
            // We are interested only in display container changes
            if ((change.getFlags() & TransitionInfo.FLAG_IS_DISPLAY) == 0) {
                continue;
            }

            // Handle only unfolding, currently we don't have an animation when folding
            if (change.getEndAbsBounds() == null || change.getStartAbsBounds() == null) {
                continue;
            }

            final int afterArea =
                    change.getEndAbsBounds().width() * change.getEndAbsBounds().height();
            final int beforeArea = change.getStartAbsBounds().width()
                    * change.getStartAbsBounds().height();

            if (afterArea > beforeArea) {
                return DefaultDisplayChange.DEFAULT_DISPLAY_UNFOLD;
            } else {
                return DefaultDisplayChange.DEFAULT_DISPLAY_FOLD;
            }
        }

        return DefaultDisplayChange.DEFAULT_DISPLAY_NO_CHANGE;
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        if (shouldPlayUnfoldAnimation(request)) {
            mTransition = transition;
            return new WindowContainerTransaction();
        }
        return null;
    }

    public boolean willHandleTransition() {
        return mTransition != null;
    }

    @Override
    public void onFoldStateChanged(boolean isFolded) {
        if (isFolded) {
            // If we are currently animating unfold animation we should finish it because
            // the animation might not start and finish as the device was folded
            finishTransitionIfNeeded();
        }
    }

    private void finishTransitionIfNeeded() {
        if (mFinishCallback == null) return;

        for (int i = 0; i < mAnimators.size(); i++) {
            final UnfoldTaskAnimator animator = mAnimators.get(i);
            animator.clearTasks();
            animator.stop();
        }

        mHandler.removeCallbacks(mAnimationPlayingTimeoutRunnable);
        mFinishCallback.onTransitionFinished(null);
        mFinishCallback = null;
        mTransition = null;
    }
}

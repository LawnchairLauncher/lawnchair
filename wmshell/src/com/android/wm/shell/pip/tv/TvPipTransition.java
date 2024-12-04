/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.pip.tv;

import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_PIP;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.transitTypeToString;

import static com.android.wm.shell.common.pip.PipMenuController.ALPHA_NO_CHANGE;
import static com.android.wm.shell.pip.PipAnimationController.TRANSITION_DIRECTION_LEAVE_PIP;
import static com.android.wm.shell.pip.PipAnimationController.TRANSITION_DIRECTION_REMOVE_STACK;
import static com.android.wm.shell.pip.PipAnimationController.TRANSITION_DIRECTION_TO_PIP;
import static com.android.wm.shell.pip.PipTransitionState.ENTERED_PIP;
import static com.android.wm.shell.pip.PipTransitionState.ENTERING_PIP;
import static com.android.wm.shell.pip.PipTransitionState.EXITING_PIP;
import static com.android.wm.shell.pip.PipTransitionState.UNDEFINED;
import static com.android.wm.shell.transition.Transitions.TRANSIT_EXIT_PIP;
import static com.android.wm.shell.transition.Transitions.TRANSIT_REMOVE_PIP;

import android.animation.AnimationHandler;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.TaskInfo;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.IBinder;
import android.os.Trace;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.graphics.SfVsyncFrameCallbackProvider;
import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.pip.PipAnimationController;
import com.android.wm.shell.pip.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.pip.PipTransitionState;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * PiP Transition for TV.
 */
public class TvPipTransition extends PipTransitionController {
    private static final String TAG = "TvPipTransition";
    private static final float ZOOM_ANIMATION_SCALE_FACTOR = 0.97f;

    private final PipTransitionState mPipTransitionState;
    private final PipAnimationController mPipAnimationController;
    private final PipSurfaceTransactionHelper mSurfaceTransactionHelper;
    private final TvPipMenuController mTvPipMenuController;
    private final PipDisplayLayoutState mPipDisplayLayoutState;
    private final PipSurfaceTransactionHelper.VsyncSurfaceControlTransactionFactory
            mTransactionFactory;

    private final ThreadLocal<AnimationHandler> mSfAnimationHandlerThreadLocal =
            ThreadLocal.withInitial(() -> {
                AnimationHandler handler = new AnimationHandler();
                handler.setProvider(new SfVsyncFrameCallbackProvider());
                return handler;
            });

    private final long mEnterFadeOutDuration;
    private final long mEnterFadeInDuration;
    private final long mExitFadeOutDuration;
    private final long mExitFadeInDuration;

    @Nullable
    private Animator mCurrentAnimator;

    /**
     * The Task window that is currently in PIP windowing mode.
     */
    @Nullable
    private WindowContainerToken mCurrentPipTaskToken;

    @Nullable
    private IBinder mPendingExitTransition;

    public TvPipTransition(Context context,
            @NonNull ShellInit shellInit,
            @NonNull ShellTaskOrganizer shellTaskOrganizer,
            @NonNull Transitions transitions,
            TvPipBoundsState tvPipBoundsState,
            TvPipMenuController tvPipMenuController,
            TvPipBoundsAlgorithm tvPipBoundsAlgorithm,
            PipTransitionState pipTransitionState,
            PipAnimationController pipAnimationController,
            PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            PipDisplayLayoutState pipDisplayLayoutState) {
        super(shellInit, shellTaskOrganizer, transitions, tvPipBoundsState, tvPipMenuController,
                tvPipBoundsAlgorithm);
        mPipTransitionState = pipTransitionState;
        mPipAnimationController = pipAnimationController;
        mSurfaceTransactionHelper = pipSurfaceTransactionHelper;
        mTvPipMenuController = tvPipMenuController;
        mPipDisplayLayoutState = pipDisplayLayoutState;
        mTransactionFactory =
                new PipSurfaceTransactionHelper.VsyncSurfaceControlTransactionFactory();

        mEnterFadeOutDuration = context.getResources().getInteger(
                R.integer.config_tvPipEnterFadeOutDuration);
        mEnterFadeInDuration = context.getResources().getInteger(
                R.integer.config_tvPipEnterFadeInDuration);
        mExitFadeOutDuration = context.getResources().getInteger(
                R.integer.config_tvPipExitFadeOutDuration);
        mExitFadeInDuration = context.getResources().getInteger(
                R.integer.config_tvPipExitFadeInDuration);
    }

    @Override
    public void startExitTransition(int type, WindowContainerTransaction out,
            @Nullable Rect destinationBounds) {
        cancelAnimations();
        mPendingExitTransition = mTransitions.startTransition(type, out, this);
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {

        if (isCloseTransition(info)) {
            // PiP is closing (without reentering fullscreen activity)
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Starting close animation", TAG);
            cancelAnimations();
            startCloseAnimation(info, startTransaction, finishTransaction, finishCallback);
            mCurrentPipTaskToken = null;
            return true;

        } else if (transition.equals(mPendingExitTransition)) {
            // PiP is exiting (reentering fullscreen activity)
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Starting exit animation", TAG);

            final TransitionInfo.Change currentPipTaskChange = findCurrentPipTaskChange(info);
            mPendingExitTransition = null;
            // PipTaskChange can be null if the PIP task has been detached, for example, when the
            // task contains multiple activities, the PIP will be moved to a new PIP task when
            // entering, and be moved back when exiting. In that case, the PIP task will be removed
            // immediately.
            final TaskInfo pipTaskInfo = currentPipTaskChange != null
                    ? currentPipTaskChange.getTaskInfo()
                    : mPipOrganizer.getTaskInfo();
            if (pipTaskInfo == null) {
                throw new RuntimeException("Cannot find the pip task for exit-pip transition.");
            }

            final int type = info.getType();
            switch (type) {
                case TRANSIT_EXIT_PIP -> {
                    TransitionInfo.Change pipChange = currentPipTaskChange;
                    SurfaceControl activitySc = null;
                    if (mCurrentPipTaskToken == null) {
                        ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                                "%s: There is no existing PiP Task for TRANSIT_EXIT_PIP", TAG);
                    } else if (pipChange == null) {
                        // The pipTaskChange is null, this can happen if we are reparenting the
                        // PIP activity back to its original Task. In that case, we should animate
                        // the activity leash instead, which should be the change whose last parent
                        // is the recorded PiP Task.
                        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
                            final TransitionInfo.Change change = info.getChanges().get(i);
                            if (mCurrentPipTaskToken.equals(change.getLastParent())) {
                                // Find the activity that is exiting PiP.
                                pipChange = change;
                                activitySc = change.getLeash();
                                break;
                            }
                        }
                    }
                    if (pipChange == null) {
                        ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                                "%s: No window of exiting PIP is found. Can't play expand "
                                        + "animation",
                                TAG);
                        removePipImmediately(info, pipTaskInfo, startTransaction, finishTransaction,
                                finishCallback);
                        return true;
                    }
                    final TransitionInfo.Root root = TransitionUtil.getRootFor(pipChange, info);
                    final SurfaceControl pipLeash;
                    if (activitySc != null) {
                        // Use a local leash to animate activity in case the activity has
                        // letterbox which may be broken by PiP animation, e.g. always end at 0,0
                        // in parent and unable to include letterbox area in crop bounds.
                        final SurfaceControl activitySurface = pipChange.getLeash();
                        pipLeash = new SurfaceControl.Builder()
                                .setName(activitySc + "_pip-leash")
                                .setContainerLayer()
                                .setHidden(false)
                                .setParent(root.getLeash())
                                .setCallsite("TvPipTransition.startAnimation")
                                .build();
                        startTransaction.reparent(activitySurface, pipLeash);
                        // Put the activity at local position with offset in case it is letterboxed.
                        final Point activityOffset = pipChange.getEndRelOffset();
                        startTransaction.setPosition(activitySc, activityOffset.x,
                                activityOffset.y);
                    } else {
                        pipLeash = pipChange.getLeash();
                        startTransaction.reparent(pipLeash, root.getLeash());
                    }
                    startTransaction.setLayer(pipLeash, Integer.MAX_VALUE);
                    final Rect currentBounds = mPipBoundsState.getBounds();
                    final Rect destinationBounds = new Rect(pipChange.getEndAbsBounds());
                    cancelAnimations();
                    startExitAnimation(pipTaskInfo, pipLeash, currentBounds, destinationBounds,
                            startTransaction,
                            finishTransaction, finishCallback);
                }
                // pass through here is intended
                case TRANSIT_TO_BACK, TRANSIT_REMOVE_PIP -> removePipImmediately(info, pipTaskInfo,
                        startTransaction, finishTransaction,
                        finishCallback
                );
                default -> {
                    return false;
                }
            }
            mCurrentPipTaskToken = null;
            return true;

        } else if (isEnteringPip(info)) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Starting enter animation", TAG);

            // Search for an Enter PiP transition
            TransitionInfo.Change enterPip = null;
            for (int i = info.getChanges().size() - 1; i >= 0; --i) {
                final TransitionInfo.Change change = info.getChanges().get(i);
                if (change.getTaskInfo() != null
                        && change.getTaskInfo().getWindowingMode() == WINDOWING_MODE_PINNED) {
                    enterPip = change;
                }
            }
            if (enterPip == null) {
                throw new IllegalStateException("Trying to start PiP animation without a pip"
                        + "participant");
            }

            // Make sure other open changes are visible as entering PIP. Some may be hidden in
            // Transitions#setupStartState because the transition type is OPEN (such as auto-enter).
            for (int i = info.getChanges().size() - 1; i >= 0; --i) {
                final TransitionInfo.Change change = info.getChanges().get(i);
                if (change == enterPip) continue;
                if (TransitionUtil.isOpeningType(change.getMode())) {
                    final SurfaceControl leash = change.getLeash();
                    startTransaction.show(leash).setAlpha(leash, 1.f);
                }
            }

            cancelAnimations();
            startEnterAnimation(enterPip, startTransaction, finishTransaction, finishCallback);
            return true;
        }

        return false;
    }

    /**
     * For {@link Transitions#TRANSIT_REMOVE_PIP}, we just immediately remove the PIP Task.
     */
    private void removePipImmediately(@NonNull TransitionInfo info,
            @NonNull TaskInfo taskInfo, @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE, "%s: removePipImmediately", TAG);
        cancelAnimations();
        startTransaction.apply();
        finishTransaction.setWindowCrop(info.getChanges().get(0).getLeash(),
                mPipDisplayLayoutState.getDisplayBounds());
        mTvPipMenuController.detach();
        mPipOrganizer.onExitPipFinished(taskInfo);
        finishCallback.onTransitionFinished(/* wct= */ null);

        mPipTransitionState.setTransitionState(UNDEFINED);
        sendOnPipTransitionFinished(TRANSITION_DIRECTION_REMOVE_STACK);
    }

    private void startCloseAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        final TransitionInfo.Change pipTaskChange = findCurrentPipTaskChange(info);
        final SurfaceControl pipLeash = pipTaskChange.getLeash();

        final List<SurfaceControl> closeLeashes = new ArrayList<>();
        for (TransitionInfo.Change change : info.getChanges()) {
            if (TransitionUtil.isClosingType(change.getMode()) && change != pipTaskChange) {
                closeLeashes.add(change.getLeash());
            }
        }

        final Rect pipBounds = mPipBoundsState.getBounds();
        mSurfaceTransactionHelper
                .resetScale(startTransaction, pipLeash, pipBounds)
                .crop(startTransaction, pipLeash, pipBounds)
                .shadow(startTransaction, pipLeash, false);

        final SurfaceControl.Transaction transaction = mTransactionFactory.getTransaction();
        for (SurfaceControl leash : closeLeashes) {
            startTransaction.setShadowRadius(leash, 0f);
        }

        ValueAnimator closeFadeOutAnimator = createAnimator();
        closeFadeOutAnimator.setInterpolator(TvPipInterpolators.EXIT);
        closeFadeOutAnimator.setDuration(mExitFadeOutDuration);
        closeFadeOutAnimator.addUpdateListener(
                animationUpdateListener(pipLeash).fadingOut().withMenu());
        for (SurfaceControl leash : closeLeashes) {
            closeFadeOutAnimator.addUpdateListener(animationUpdateListener(leash).fadingOut());
        }

        closeFadeOutAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(@NonNull Animator animation) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: close animation: start", TAG);
                for (SurfaceControl leash : closeLeashes) {
                    startTransaction.setShadowRadius(leash, 0f);
                }
                startTransaction.apply();

                mPipTransitionState.setTransitionState(EXITING_PIP);
                sendOnPipTransitionStarted(TRANSITION_DIRECTION_REMOVE_STACK);
            }

            @Override
            public void onAnimationCancel(@NonNull Animator animation) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: close animation: cancel", TAG);
                sendOnPipTransitionCancelled(TRANSITION_DIRECTION_REMOVE_STACK);
            }

            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: close animation: end", TAG);
                mTvPipMenuController.detach();
                finishCallback.onTransitionFinished(null /* wct */);
                transaction.close();
                mPipTransitionState.setTransitionState(UNDEFINED);
                sendOnPipTransitionFinished(TRANSITION_DIRECTION_REMOVE_STACK);

                mCurrentAnimator = null;
            }
        });

        closeFadeOutAnimator.start();
        mCurrentAnimator = closeFadeOutAnimator;
    }

    @Override
    public void startEnterAnimation(@NonNull TransitionInfo.Change pipChange,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        // Keep track of the PIP task
        mCurrentPipTaskToken = pipChange.getContainer();
        final ActivityManager.RunningTaskInfo taskInfo = pipChange.getTaskInfo();
        final SurfaceControl leash = pipChange.getLeash();

        mTvPipMenuController.attach(leash);
        setBoundsStateForEntry(taskInfo.topActivity, taskInfo.pictureInPictureParams,
                taskInfo.topActivityInfo);

        final Rect pipBounds =
                mPipBoundsAlgorithm.getEntryDestinationBoundsIgnoringKeepClearAreas();
        mPipBoundsState.setBounds(pipBounds);
        mTvPipMenuController.movePipMenu(null, pipBounds, 0f);

        final WindowContainerTransaction resizePipWct = new WindowContainerTransaction();
        resizePipWct.setWindowingMode(taskInfo.token, WINDOWING_MODE_PINNED);
        resizePipWct.setActivityWindowingMode(taskInfo.token, WINDOWING_MODE_PINNED);
        resizePipWct.setBounds(taskInfo.token, pipBounds);

        mSurfaceTransactionHelper
                .resetScale(finishTransaction, leash, pipBounds)
                .crop(finishTransaction, leash, pipBounds)
                .shadow(finishTransaction, leash, false);

        final Rect currentBounds = pipChange.getStartAbsBounds();
        final Rect fadeOutCurrentBounds = scaledRect(currentBounds, ZOOM_ANIMATION_SCALE_FACTOR);

        final ValueAnimator enterFadeOutAnimator = createAnimator();
        enterFadeOutAnimator.setInterpolator(TvPipInterpolators.EXIT);
        enterFadeOutAnimator.setDuration(mEnterFadeOutDuration);
        enterFadeOutAnimator.addUpdateListener(
                animationUpdateListener(leash)
                        .fadingOut()
                        .animateBounds(currentBounds, fadeOutCurrentBounds, currentBounds));

        enterFadeOutAnimator.addListener(new AnimatorListenerAdapter() {
            @SuppressLint("MissingPermission")
            @Override
            public void onAnimationEnd(Animator animation) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: enter fade out animation: end", TAG);
                SurfaceControl.Transaction tx = mTransactionFactory.getTransaction();
                mSurfaceTransactionHelper
                        .resetScale(tx, leash, pipBounds)
                        .crop(tx, leash, pipBounds)
                        .shadow(tx, leash, false);
                mShellTaskOrganizer.applyTransaction(resizePipWct);
                tx.apply();
            }
        });

        final ValueAnimator enterFadeInAnimator = createAnimator();
        enterFadeInAnimator.setInterpolator(TvPipInterpolators.ENTER);
        enterFadeInAnimator.setDuration(mEnterFadeInDuration);
        enterFadeInAnimator.addUpdateListener(
                animationUpdateListener(leash)
                        .fadingIn()
                        .withMenu()
                        .atBounds(pipBounds));

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet
                .play(enterFadeInAnimator)
                .after(500)
                .after(enterFadeOutAnimator);

        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: enter animation: start", TAG);
                startTransaction.apply();
                mPipTransitionState.setTransitionState(ENTERING_PIP);
                sendOnPipTransitionStarted(TRANSITION_DIRECTION_TO_PIP);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: enter animation: cancel", TAG);
                enterFadeInAnimator.setCurrentFraction(1f);
                sendOnPipTransitionCancelled(TRANSITION_DIRECTION_TO_PIP);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: enter animation: end", TAG);
                WindowContainerTransaction wct = new WindowContainerTransaction();
                wct.setActivityWindowingMode(taskInfo.token, WINDOWING_MODE_UNDEFINED);
                wct.setBounds(taskInfo.token, pipBounds);
                finishCallback.onTransitionFinished(wct);

                mPipTransitionState.setTransitionState(ENTERED_PIP);
                sendOnPipTransitionFinished(TRANSITION_DIRECTION_TO_PIP);
                mCurrentAnimator = null;
            }
        });

        animatorSet.start();
        mCurrentAnimator = animatorSet;
    }

    private void startExitAnimation(@NonNull TaskInfo taskInfo, SurfaceControl leash,
            Rect currentBounds, Rect destinationBounds,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        final Rect fadeInStartBounds = scaledRect(destinationBounds, ZOOM_ANIMATION_SCALE_FACTOR);

        final ValueAnimator exitFadeOutAnimator = createAnimator();
        exitFadeOutAnimator.setInterpolator(TvPipInterpolators.EXIT);
        exitFadeOutAnimator.setDuration(mExitFadeOutDuration);
        exitFadeOutAnimator.addUpdateListener(
                animationUpdateListener(leash)
                        .fadingOut()
                        .withMenu()
                        .atBounds(currentBounds));
        exitFadeOutAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: exit fade out animation: end", TAG);
                startTransaction.apply();
                mPipMenuController.detach();
            }
        });

        final ValueAnimator exitFadeInAnimator = createAnimator();
        exitFadeInAnimator.setInterpolator(TvPipInterpolators.ENTER);
        exitFadeInAnimator.setDuration(mExitFadeInDuration);
        exitFadeInAnimator.addUpdateListener(
                animationUpdateListener(leash)
                        .fadingIn()
                        .animateBounds(fadeInStartBounds, destinationBounds, destinationBounds));

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playSequentially(
                exitFadeOutAnimator,
                exitFadeInAnimator
        );

        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: exit animation: start", TAG);
                mPipTransitionState.setTransitionState(EXITING_PIP);
                sendOnPipTransitionStarted(TRANSITION_DIRECTION_LEAVE_PIP);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: exit animation: cancel", TAG);
                sendOnPipTransitionCancelled(TRANSITION_DIRECTION_LEAVE_PIP);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: exit animation: end", TAG);
                mPipOrganizer.onExitPipFinished(taskInfo);

                final WindowContainerTransaction wct = new WindowContainerTransaction();
                wct.setWindowingMode(taskInfo.token, WINDOWING_MODE_UNDEFINED);
                wct.setActivityWindowingMode(taskInfo.token, WINDOWING_MODE_UNDEFINED);
                wct.setBounds(taskInfo.token, destinationBounds);
                finishCallback.onTransitionFinished(wct);

                mPipTransitionState.setTransitionState(UNDEFINED);
                sendOnPipTransitionFinished(TRANSITION_DIRECTION_LEAVE_PIP);

                mCurrentAnimator = null;
            }
        });

        animatorSet.start();
        mCurrentAnimator = animatorSet;
    }

    @NonNull
    private ValueAnimator createAnimator() {
        final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setAnimationHandler(mSfAnimationHandlerThreadLocal.get());
        return animator;
    }

    @NonNull
    private TvPipTransitionAnimatorUpdateListener animationUpdateListener(
            @NonNull SurfaceControl leash) {
        return new TvPipTransitionAnimatorUpdateListener(leash, mTvPipMenuController,
                mTransactionFactory.getTransaction(), mSurfaceTransactionHelper);
    }

    @NonNull
    private static Rect scaledRect(@NonNull Rect rect, float scale) {
        final Rect out = new Rect(rect);
        out.inset((int) (rect.width() * (1 - scale) / 2), (int) (rect.height() * (1 - scale) / 2));
        return out;
    }

    private boolean isCloseTransition(TransitionInfo info) {
        final TransitionInfo.Change currentPipTaskChange = findCurrentPipTaskChange(info);
        return currentPipTaskChange != null && info.getType() == TRANSIT_CLOSE;
    }

    @Nullable
    private TransitionInfo.Change findCurrentPipTaskChange(@NonNull TransitionInfo info) {
        if (mCurrentPipTaskToken == null) {
            return null;
        }
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (mCurrentPipTaskToken.equals(change.getContainer())) {
                return change;
            }
        }
        return null;
    }

    /**
     * Whether we should handle the given {@link TransitionInfo} animation as entering PIP.
     */
    private boolean isEnteringPip(@NonNull TransitionInfo info) {
        for (int i = info.getChanges().size() - 1; i >= 0; --i) {
            final TransitionInfo.Change change = info.getChanges().get(i);
            if (isEnteringPip(change, info.getType())) return true;
        }
        return false;
    }

    /**
     * Whether a particular change is a window that is entering pip.
     */
    @Override
    public boolean isEnteringPip(@NonNull TransitionInfo.Change change,
            @WindowManager.TransitionType int transitType) {
        if (change.getTaskInfo() != null
                && change.getTaskInfo().getWindowingMode() == WINDOWING_MODE_PINNED
                && !Objects.equals(change.getContainer(), mCurrentPipTaskToken)) {
            if (transitType == TRANSIT_PIP || transitType == TRANSIT_OPEN
                    || transitType == TRANSIT_CHANGE) {
                return true;
            }
            // Please file a bug to handle the unexpected transition type.
            android.util.Slog.e(TAG, "Found new PIP in transition with mis-matched type="
                    + transitTypeToString(transitType), new Throwable());
        }
        return false;
    }

    @Override
    public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction t, @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE, "%s: merge animation", TAG);
        if (mCurrentAnimator != null && mCurrentAnimator.isRunning()) {
            mCurrentAnimator.end();
        }
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        if (requestHasPipEnter(request)) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: handle PiP enter request", TAG);
            WindowContainerTransaction wct = new WindowContainerTransaction();
            augmentRequest(transition, request, wct);
            return wct;
        } else if (request.getType() == TRANSIT_TO_BACK && request.getTriggerTask() != null
                && request.getTriggerTask().getWindowingMode() == WINDOWING_MODE_PINNED) {
            // if we receive a TRANSIT_TO_BACK type of request while in PiP
            mPendingExitTransition = transition;

            // update the transition state to avoid {@link PipTaskOrganizer#onTaskVanished()} calls
            mPipTransitionState.setTransitionState(EXITING_PIP);

            // return an empty WindowContainerTransaction so that we don't check other handlers
            return new WindowContainerTransaction();
        } else {
            return null;
        }
    }

    @Override
    public void augmentRequest(@NonNull IBinder transition, @NonNull TransitionRequestInfo request,
            @NonNull WindowContainerTransaction outWCT) {
        if (!requestHasPipEnter(request)) {
            throw new IllegalStateException("Called PiP augmentRequest when request has no PiP");
        }
        outWCT.setActivityWindowingMode(request.getTriggerTask().token, WINDOWING_MODE_UNDEFINED);
    }

    /**
     * Cancel any ongoing PiP transitions/animations.
     */
    public void cancelAnimations() {
        if (mPipAnimationController.isAnimating()) {
            mPipAnimationController.getCurrentAnimator().cancel();
            mPipAnimationController.resetAnimatorState();
        }
        if (mCurrentAnimator != null) {
            mCurrentAnimator.cancel();
        }
    }

    @Override
    public void end() {
        if (mCurrentAnimator != null) {
            mCurrentAnimator.end();
        }
    }

    private static class TvPipTransitionAnimatorUpdateListener implements
            ValueAnimator.AnimatorUpdateListener {
        private final SurfaceControl mLeash;
        private final TvPipMenuController mTvPipMenuController;
        private final SurfaceControl.Transaction mTransaction;
        private final PipSurfaceTransactionHelper mSurfaceTransactionHelper;
        private final RectF mTmpRectF = new RectF();
        private final Rect mTmpRect = new Rect();

        private float mStartAlpha = ALPHA_NO_CHANGE;
        private float mEndAlpha = ALPHA_NO_CHANGE;

        @Nullable
        private Rect mStartBounds;
        @Nullable
        private Rect mEndBounds;
        private Rect mWindowContainerBounds;
        private boolean mShowMenu;

        TvPipTransitionAnimatorUpdateListener(@NonNull SurfaceControl leash,
                @NonNull TvPipMenuController tvPipMenuController,
                @NonNull SurfaceControl.Transaction transaction,
                @NonNull PipSurfaceTransactionHelper pipSurfaceTransactionHelper) {
            mLeash = leash;
            mTvPipMenuController = tvPipMenuController;
            mTransaction = transaction;
            mSurfaceTransactionHelper = pipSurfaceTransactionHelper;
        }

        public TvPipTransitionAnimatorUpdateListener animateAlpha(
                @FloatRange(from = 0.0, to = 1.0) float startAlpha,
                @FloatRange(from = 0.0, to = 1.0) float endAlpha) {
            mStartAlpha = startAlpha;
            mEndAlpha = endAlpha;
            return this;
        }

        public TvPipTransitionAnimatorUpdateListener animateBounds(@NonNull Rect startBounds,
                @NonNull Rect endBounds, @NonNull Rect windowContainerBounds) {
            mStartBounds = startBounds;
            mEndBounds = endBounds;
            mWindowContainerBounds = windowContainerBounds;
            return this;
        }

        public TvPipTransitionAnimatorUpdateListener atBounds(@NonNull Rect bounds) {
            return animateBounds(bounds, bounds, bounds);
        }

        public TvPipTransitionAnimatorUpdateListener fadingOut() {
            return animateAlpha(1f, 0f);
        }

        public TvPipTransitionAnimatorUpdateListener fadingIn() {
            return animateAlpha(0f, 1f);
        }

        public TvPipTransitionAnimatorUpdateListener withMenu() {
            mShowMenu = true;
            return this;
        }

        @Override
        public void onAnimationUpdate(@NonNull ValueAnimator animation) {
            final float fraction = animation.getAnimatedFraction();
            final float alpha = lerp(mStartAlpha, mEndAlpha, fraction);
            if (mStartBounds != null && mEndBounds != null) {
                lerp(mStartBounds, mEndBounds, fraction, mTmpRectF);
                applyAnimatedValue(alpha, mTmpRectF);
            } else {
                applyAnimatedValue(alpha, null);
            }
        }

        private void applyAnimatedValue(float alpha, @Nullable RectF bounds) {
            Trace.beginSection("applyAnimatedValue");
            final SurfaceControl.Transaction tx = mTransaction;

            Trace.beginSection("leash scale and alpha");
            if (alpha != ALPHA_NO_CHANGE) {
                mSurfaceTransactionHelper.alpha(tx, mLeash, alpha);
            }
            if (bounds != null) {
                mSurfaceTransactionHelper.scale(tx, mLeash, mWindowContainerBounds, bounds);
            }
            mSurfaceTransactionHelper.shadow(tx, mLeash, false);
            tx.show(mLeash);
            Trace.endSection();

            if (mShowMenu) {
                Trace.beginSection("movePipMenu");
                if (bounds != null) {
                    mTmpRect.set((int) bounds.left, (int) bounds.top, (int) bounds.right,
                            (int) bounds.bottom);
                    mTvPipMenuController.movePipMenu(tx, mTmpRect, alpha);
                } else {
                    mTvPipMenuController.movePipMenu(tx, null, alpha);
                }
                Trace.endSection();
            } else {
                mTvPipMenuController.movePipMenu(tx, null, 0f);
            }

            tx.apply();
            Trace.endSection();
        }

        private float lerp(float start, float end, float fraction) {
            return start * (1 - fraction) + end * fraction;
        }

        private void lerp(@NonNull Rect start, @NonNull Rect end, float fraction,
                @NonNull RectF out) {
            out.set(
                    start.left * (1 - fraction) + end.left * fraction,
                    start.top * (1 - fraction) + end.top * fraction,
                    start.right * (1 - fraction) + end.right * fraction,
                    start.bottom * (1 - fraction) + end.bottom * fraction);
        }
    }
}

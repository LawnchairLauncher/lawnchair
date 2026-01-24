/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.wm.shell.bubbles;

import static android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS;
import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_ONE_SHOT;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.view.View.INVISIBLE;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_FRONT;

import static com.android.wm.shell.bubbles.util.BubbleUtils.getEnterBubbleTransaction;
import static com.android.wm.shell.bubbles.util.BubbleUtils.getExitBubbleTransaction;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_BUBBLES_NOISY;
import static com.android.wm.shell.shared.TransitionUtil.isOpeningMode;
import static com.android.wm.shell.transition.Transitions.TRANSIT_BUBBLE_CONVERT_FLOATING_TO_BAR;
import static com.android.wm.shell.transition.Transitions.TRANSIT_CONVERT_TO_BUBBLE;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.app.TaskInfo;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.util.Slog;
import android.view.SurfaceControl;
import android.view.SurfaceView;
import android.view.View;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.animation.Animator;
import androidx.core.animation.Animator.AnimatorUpdateListener;
import androidx.core.animation.AnimatorListenerAdapter;
import androidx.core.animation.ValueAnimator;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.launcher3.icons.BubbleIconFactory;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.bubbles.appinfo.BubbleAppInfoProvider;
import com.android.wm.shell.bubbles.bar.BubbleBarExpandedView;
import com.android.wm.shell.bubbles.bar.BubbleBarLayerView;
import com.android.wm.shell.common.HomeIntentProvider;
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper;
import com.android.wm.shell.shared.bubbles.BubbleBarLocation;
import com.android.wm.shell.taskview.TaskView;
import com.android.wm.shell.taskview.TaskViewRepository;
import com.android.wm.shell.taskview.TaskViewTaskController;
import com.android.wm.shell.taskview.TaskViewTransitions;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.transition.Transitions.TransitionHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Implements transition coordination for bubble operations.
 */
public class BubbleTransitions {
    private static final String TAG = "BubbleTransitions";

    /**
     * Multiplier used to convert a view elevation to an "equivalent" shadow-radius. This is the
     * same multiple used by skia and surface-outsets in WMS.
     */
    private static final float ELEVATION_TO_RADIUS = 2;

    @NonNull final Transitions mTransitions;
    @NonNull final ShellTaskOrganizer mTaskOrganizer;
    @NonNull final TaskViewRepository mRepository;
    @NonNull final Executor mMainExecutor;
    @NonNull final BubbleData mBubbleData;
    @NonNull final TaskViewTransitions mTaskViewTransitions;
    @NonNull final Context mContext;
    @NonNull final BubbleAppInfoProvider mAppInfoProvider;

    @VisibleForTesting
    // Map of a launch cookie (used to start an activity) to the associated transition handler
    final Map<IBinder, TransitionHandler> mPendingEnterTransitions =
            new HashMap<>();

    @VisibleForTesting
    // Map of a running transition token to the associated transition handler
    final Map<IBinder, TransitionHandler> mEnterTransitions =
            new HashMap<>();

    private BubbleController mBubbleController;

    public BubbleTransitions(Context context,
            @NonNull Transitions transitions, @NonNull ShellTaskOrganizer organizer,
            @NonNull TaskViewRepository repository, @NonNull BubbleData bubbleData,
            @NonNull TaskViewTransitions taskViewTransitions,
            @NonNull BubbleAppInfoProvider appInfoProvider) {
        mTransitions = transitions;
        mTaskOrganizer = organizer;
        mRepository = repository;
        mMainExecutor = transitions.getMainExecutor();
        mBubbleData = bubbleData;
        mTaskViewTransitions = taskViewTransitions;
        mContext = context;
        mAppInfoProvider = appInfoProvider;
    }

    void setBubbleController(BubbleController controller) {
        mBubbleController = controller;
    }

    /**
     * Returns whether the given Task should be an App Bubble.
     */
    public boolean shouldBeAppBubble(@NonNull ActivityManager.RunningTaskInfo taskInfo) {
        return mBubbleController.shouldBeAppBubble(taskInfo);
    }

    /**
     * Returns whether bubbles are showing as the bubble bar.
     */
    public boolean isShowingAsBubbleBar() {
        return mBubbleController.isShowingAsBubbleBar();
    }

    /**
     * Returns whether there is an existing bubble with the given task id.
     */
    public boolean hasBubbleWithTaskId(int taskId) {
        return mBubbleData.getBubbleInStackWithTaskId(taskId) != null;
    }

    /**
     * Returns whether there is a pending transition for the given request.
     */
    public boolean hasPendingEnterTransition(@NonNull TransitionRequestInfo info) {
        if (info.getTriggerTask() == null) {
            return false;
        }
        for (IBinder cookie : info.getTriggerTask().launchCookies) {
            if (mPendingEnterTransitions.containsKey(cookie)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This is called to "convert" a pending enter transition into an active/running transition.
     * It is also only called after we've confirmed that this is a valid transition into a bubble,
     * ie. `hasPendingEnterTransition()` has been called.
     */
    @NonNull
    public TransitionHandler storePendingEnterTransition(IBinder transition,
            TransitionRequestInfo info) throws IllegalStateException {
        for (IBinder cookie : info.getTriggerTask().launchCookies) {
            final TransitionHandler handler = mPendingEnterTransitions.remove(cookie);
            if (handler != null) {
                ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Transferring pending to playing transition for"
                                + "cookie=%s", cookie);
                mPendingEnterTransitions.remove(cookie);
                mEnterTransitions.put(transition, handler);
                return handler;
            }
        }
        throw new IllegalStateException("Expected pending enter transition for the given request");
    }

    /**
     * Returns the transition handler for the given `transition`, only non-null if called after
     * `storePendingEnterTransition()` (which may not be the case if the transition is consumed).
     */
    @Nullable
    public TransitionHandler getRunningEnterTransition(@NonNull IBinder transition)
            throws IllegalStateException {
        if (mEnterTransitions.containsKey(transition)) {
            return mEnterTransitions.get(transition);
        }
        return null;
    }

    /** Notifies when the unfold transition is starting. */
    public void notifyUnfoldTransitionStarting(@NonNull IBinder transition) {
        // this is used to block task view transitions from coming in while the unfold transition is
        // playing, and allows us to create a specific transition and merge it into unfold. for now
        // we only do this when switching from floating bubbles to bar bubbles so guard this with
        // the bubble bar flag, but once these are combined we should be able to remove this.
        if (com.android.wm.shell.Flags.enableBubbleBar()
                && mBubbleData.getSelectedBubble() instanceof Bubble) {
            Bubble bubble = (Bubble) mBubbleData.getSelectedBubble();
            mTaskViewTransitions.enqueueExternal(
                    bubble.getTaskView().getController(), () -> transition);
        }
    }

    /** Notifies when the unfold transition has finished. */
    public void notifyUnfoldTransitionFinished(@NonNull IBinder transition) {
        if (com.android.wm.shell.Flags.enableBubbleBar()) {
            mTaskViewTransitions.onExternalDone(transition);
        }
    }

    /**
     * Starts a new launch or convert transition to show the given bubble.
     */
    public BubbleTransition startLaunchIntoOrConvertToBubble(Bubble bubble,
            BubbleExpandedViewManager expandedViewManager, BubbleTaskViewFactory factory,
            BubblePositioner positioner, BubbleStackView stackView,
            BubbleBarLayerView layerView, BubbleIconFactory iconFactory,
            boolean inflateSync, @Nullable BubbleBarLocation bubbleBarLocation) {
        return new LaunchOrConvertToBubble(bubble, mContext, expandedViewManager, factory,
                positioner, stackView, layerView, iconFactory, inflateSync, bubbleBarLocation);
    }

    /**
     * Initiates axed bubble-to-bubble launch/existing bubble convert for the given transition.
     *
     * @return whether a new transition was started for the launch
     */
    public boolean startBubbleToBubbleLaunchOrExistingBubbleConvert(@NonNull IBinder transition,
            @NonNull ActivityManager.RunningTaskInfo launchingTask,
            @NonNull Consumer<TransitionHandler> onInflatedCallback) {
        TransitionHandler handler =
                mBubbleController.expandStackAndSelectBubbleForExistingTransition(
                        launchingTask, transition, onInflatedCallback);
        if (handler != null) {
            mEnterTransitions.put(transition, handler);
        }
        return handler != null;
    }

    /**
     * Starts a new launch or convert transition to show the given bubble.
     */
    public TransitionHandler startLaunchNewTaskBubbleForExistingTransition(Bubble bubble,
            BubbleExpandedViewManager expandedViewManager, BubbleTaskViewFactory factory,
            BubblePositioner positioner, BubbleStackView stackView,
            BubbleBarLayerView layerView, BubbleIconFactory iconFactory,
            boolean inflateSync, IBinder transition,
            Consumer<TransitionHandler> onInflatedCallback) {
        return new LaunchNewTaskBubbleForExistingTransition(bubble, mContext, expandedViewManager,
                factory, positioner, stackView, layerView, iconFactory, inflateSync, transition,
                onInflatedCallback);
    }

    /**
     * Starts a convert-to-bubble transition.
     *
     * @see ConvertToBubble
     */
    public BubbleTransition startConvertToBubble(Bubble bubble, TaskInfo taskInfo,
            BubbleExpandedViewManager expandedViewManager, BubbleTaskViewFactory factory,
            BubblePositioner positioner, BubbleStackView stackView, BubbleBarLayerView layerView,
            BubbleIconFactory iconFactory, HomeIntentProvider homeIntentProvider, DragData dragData,
            boolean inflateSync) {
        return new ConvertToBubble(bubble, taskInfo, mContext, expandedViewManager, factory,
                positioner, stackView, layerView, iconFactory, homeIntentProvider, dragData,
                inflateSync);
    }

    /**
     * Starts a convert-from-bubble transition.
     *
     * @see ConvertFromBubble
     */
    public BubbleTransition startConvertFromBubble(Bubble bubble,
            TaskInfo taskInfo) {
        return new ConvertFromBubble(bubble, taskInfo);
    }

    /** Starts a transition that converts a floating expanded bubble to a bar bubble. */
    public BubbleTransition startFloatingToBarConversion(Bubble bubble,
            BubblePositioner positioner) {
        return new FloatingToBarConversion(bubble, positioner);
    }

    /** Starts a transition that converts a dragged bubble icon to a full screen task. */
    public BubbleTransition startDraggedBubbleIconToFullscreen(Bubble bubble, Point dropLocation) {
        return new DraggedBubbleIconToFullscreen(bubble, dropLocation);
    }

    /**
     * Plucks the task-surface out of an ancestor view while making the view invisible. This helper
     * attempts to do this seamlessly (ie. view becomes invisible in sync with task reparent).
     */
    private void pluck(SurfaceControl taskLeash, View fromView, SurfaceControl dest,
            float destX, float destY, float cornerRadius, SurfaceControl.Transaction t,
            Runnable onPlucked) {
        SurfaceControl.Transaction pluckT = new SurfaceControl.Transaction();
        pluckT.reparent(taskLeash, dest);
        t.reparent(taskLeash, dest);
        pluckT.setPosition(taskLeash, destX, destY);
        t.setPosition(taskLeash, destX, destY);
        pluckT.show(taskLeash);
        pluckT.setAlpha(taskLeash, 1.f);
        float shadowRadius = fromView.getElevation() * ELEVATION_TO_RADIUS;
        pluckT.setShadowRadius(taskLeash, shadowRadius);
        pluckT.setCornerRadius(taskLeash, cornerRadius);
        t.setShadowRadius(taskLeash, shadowRadius);
        t.setCornerRadius(taskLeash, cornerRadius);

        // Need to remove the taskview AFTER applying the startTransaction because it isn't
        // synchronized.
        pluckT.addTransactionCommittedListener(mMainExecutor, onPlucked::run);
        fromView.getViewRootImpl().applyTransactionOnDraw(pluckT);
        fromView.setVisibility(INVISIBLE);
    }

    /**
     * Interface to a bubble-specific transition. Bubble transitions have a multi-step lifecycle
     * in order to coordinate with the bubble view logic. These steps are communicated on this
     * interface.
     */
    public interface BubbleTransition {
        default void surfaceCreated() {}
        default void continueExpand() {}
        default void skip() {}
        default void continueCollapse() {}
        /** Continues the conversion transition. */
        default void continueConvert(BubbleBarLayerView layerView) {}
        /** Merge this transition with the unfold transition. */
        default void mergeWithUnfold(
                SurfaceControl taskLeash, SurfaceControl.Transaction finishT) {}
        /** Whether this transition is for converting a floating bubble to a bubble bar bubble. */
        default boolean isConvertingBubbleToBar() {
            return (this instanceof FloatingToBarConversion);
        }
    }

    /**
     * Information about the task when it is being dragged to a bubble.
     */
    public static class DragData {
        private final boolean mReleasedOnLeft;
        private final float mTaskScale;
        private final float mCornerRadius;
        private final PointF mDragPosition;

        /**
         * @param releasedOnLeft true if the bubble was released in the left drop target
         * @param taskScale      the scale of the task when it was dragged to bubble
         * @param cornerRadius   the corner radius of the task when it was dragged to bubble
         * @param dragPosition   the position of the task when it was dragged to bubble
         */
        public DragData(boolean releasedOnLeft, float taskScale, float cornerRadius,
                @Nullable PointF dragPosition) {
            mReleasedOnLeft = releasedOnLeft;
            mTaskScale = taskScale;
            mCornerRadius = cornerRadius;
            mDragPosition = dragPosition != null ? dragPosition : new PointF(0, 0);
        }

        /**
         * @return true if the bubble was released in the left drop target
         */
        public boolean isReleasedOnLeft() {
            return mReleasedOnLeft;
        }

        /**
         * @return the scale of the task when it was dragged to bubble
         */
        public float getTaskScale() {
            return mTaskScale;
        }

        /**
         * @return the corner radius of the task when it was dragged to bubble
         */
        public float getCornerRadius() {
            return mCornerRadius;
        }

        /**
         * @return position of the task when it was dragged to bubble
         */
        public PointF getDragPosition() {
            return mDragPosition;
        }
    }

    /**
     * Keeps track of internal state of different steps of a BubbleTransition. Serves as a gating
     * mechanism to block animations or updates until necessary states are set.
     */
    private static class TransitionProgress {

        private final Bubble mBubble;
        private boolean mTransitionReady;
        private boolean mInflated;
        private boolean mReadyToExpand;
        private boolean mSurfaceReady;

        TransitionProgress(Bubble bubble) {
            mBubble = bubble;
        }

        void setInflated() {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TransitionProgress.setInflated()");
            mInflated = true;
            onUpdate();
        }

        void setTransitionReady() {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TransitionProgress.setTransitionReady()");
            mTransitionReady = true;
            onUpdate();
        }

        void setReadyToExpand() {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TransitionProgress.setReadyToExpand()");
            mReadyToExpand = true;
            onUpdate();
        }

        void setSurfaceReady() {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "TransitionProgress.setSurfaceReady()");
            mSurfaceReady = true;
            onUpdate();
        }

        boolean isReadyToAnimate() {
            // Animation only depends on transition and surface state
            return mTransitionReady && mSurfaceReady && mInflated;
        }

        private void onUpdate() {
            if (mTransitionReady && mReadyToExpand && mSurfaceReady && mInflated) {
                // Clear the transition from bubble when all the steps are ready
                mBubble.setPreparingTransition(null);
            }
        }
    }

    /**
     * Starts a new bubble for an existing playing transition.
     * TODO(b/408328557): To be consolidated with LaunchOrConvertToBubble and ConvertToBubble
     */
    @VisibleForTesting
    class LaunchNewTaskBubbleForExistingTransition implements TransitionHandler, BubbleTransition {
        final BubblePositioner mPositioner;
        final BubbleExpandedViewTransitionAnimator mExpandedViewAnimator;
        private final TransitionProgress mTransitionProgress;
        Bubble mBubble;
        IBinder mTransition;
        Transitions.TransitionFinishCallback mFinishCb;
        WindowContainerTransaction mFinishWct = null;
        final Rect mStartBounds = new Rect();
        SurfaceControl mSnapshot = null;
        // The task info is resolved once we find the task from the transition info using the
        // pending launch cookie otherwise
        @Nullable
        TaskInfo mTaskInfo;
        BubbleViewProvider mPriorBubble = null;
        // Whether we should play the convert-task animation, or the launch-task animation
        private boolean mPlayConvertTaskAnimation;

        private SurfaceControl.Transaction mFinishT;
        private SurfaceControl mTaskLeash;

        LaunchNewTaskBubbleForExistingTransition(Bubble bubble, Context context,
                BubbleExpandedViewManager expandedViewManager, BubbleTaskViewFactory factory,
                BubblePositioner positioner, BubbleStackView stackView,
                BubbleBarLayerView layerView, BubbleIconFactory iconFactory,
                boolean inflateSync, IBinder transition,
                Consumer<TransitionHandler> onInflatedCallback) {
            if (layerView != null) {
                mExpandedViewAnimator = layerView;
            } else {
                mExpandedViewAnimator = stackView;
            }
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "LaunchNewTaskBubble(): expanded=%s",
                    mExpandedViewAnimator.isExpanded());
            mBubble = bubble;
            mTransition = transition;
            mTransitionProgress = new TransitionProgress(bubble);
            mPositioner = positioner;
            mBubble.setInflateSynchronously(inflateSync);
            mBubble.setPreparingTransition(this);
            mBubble.inflate(
                    b -> {
                        onInflated(b);
                        onInflatedCallback.accept(LaunchNewTaskBubbleForExistingTransition.this);
                    },
                    context,
                    expandedViewManager,
                    factory,
                    positioner,
                    stackView,
                    layerView,
                    iconFactory,
                    mAppInfoProvider,
                    false /* skipInflation */);
        }

        @VisibleForTesting
        void onInflated(Bubble b) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "LaunchNewTaskBubble.onInflated()");
            if (b != mBubble) {
                throw new IllegalArgumentException("inflate callback doesn't match bubble");
            }
            if (!mBubble.isShortcut() && !mBubble.isApp()) {
                throw new IllegalArgumentException("Unsupported bubble type");
            }
            final TaskView tv = b.getTaskView();
            tv.setSurfaceLifecycle(SurfaceView.SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT);
            final TaskViewRepository.TaskViewState state = mRepository.byTaskView(
                    tv.getController());
            if (state != null) {
                state.mVisible = true;
            }
            mTransitionProgress.setInflated();
            // Remove any intermediate queued transitions that were started as a result of the
            // inflation (the task view will be in the right bounds)
            mTaskViewTransitions.removePendingTransitions(tv.getController());
            mTaskViewTransitions.enqueueExternal(tv.getController(), () -> {
                return mTransition;
            });
        }

        @Override
        public void skip() {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "LaunchNewTaskBubble.skip()");
            mBubble.setPreparingTransition(null);
            cleanup();
        }

        @Override
        public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
                @Nullable TransitionRequestInfo request) {
            return null;
        }

        @Override
        public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startT,
                @NonNull SurfaceControl.Transaction finishT,
                @NonNull IBinder mergeTarget,
                @NonNull Transitions.TransitionFinishCallback finishCallback) {
        }

        @Override
        public void onTransitionConsumed(@NonNull IBinder transition, boolean aborted,
                @NonNull SurfaceControl.Transaction finishTransaction) {
            if (!aborted) return;
            mTaskViewTransitions.onExternalDone(mTransition);
            mTransition = null;
        }

        /**
         * @return true As DefaultMixedTransition assumes that this transition will be handled by
         * this handler in all cases.
         */
        @Override
        public boolean startAnimation(@NonNull IBinder transition,
                @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startTransaction,
                @NonNull SurfaceControl.Transaction finishTransaction,
                @NonNull Transitions.TransitionFinishCallback finishCallback) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "LaunchNewTaskBubble.startAnimation()");

            // Identify the task that we are converting or launching. Note, we iterate back to front
            // so that we can adjust alpha for revealed surfaces as needed.
            boolean found = false;
            mPlayConvertTaskAnimation = false;
            for (int i = info.getChanges().size() - 1; i >= 0; i--) {
                final TransitionInfo.Change chg = info.getChanges().get(i);
                final boolean isLaunchedTask = (chg.getTaskInfo() != null)
                        && (chg.getMode() == TRANSIT_CHANGE || isOpeningMode(chg.getMode()));
                if (isLaunchedTask) {
                    mStartBounds.set(chg.getStartAbsBounds());
                    // Converting a task into taskview, so treat as "new"
                    mFinishWct = new WindowContainerTransaction();
                    mTaskInfo = chg.getTaskInfo();
                    mFinishT = finishTransaction;
                    mTaskLeash = chg.getLeash();
                    mSnapshot = chg.getSnapshot();
                    // TODO: This should be set for the CHANGE transition, but for some reason there
                    //  is no snapshot, so fallback to the open transition for now
                    mPlayConvertTaskAnimation = false;
                    found = true;
                } else {
                    // In core-initiated launches, the transition is of an OPEN type, and we need to
                    // manually show the surfaces behind the newly bubbled task
                    if (info.getType() == TRANSIT_OPEN && isOpeningMode(chg.getMode())) {
                        startTransaction.setAlpha(chg.getLeash(), 1f);
                    }
                }
            }
            if (!found) {
                Slog.w(TAG, "Expected a TaskView conversion in this transition but didn't get "
                        + "one, cleaning up the task view");
                mBubble.getTaskView().getController().setTaskNotFound();
                mTaskViewTransitions.onExternalDone(mTransition);
                finishCallback.onTransitionFinished(null /* finishWct */);
                return true;
            }
            mFinishCb = finishCallback;

            // Now update state (and talk to launcher) in parallel with snapshot stuff
            mBubbleData.notificationEntryUpdated(mBubble, /* suppressFlyout= */ true,
                    /* showInShade= */ false);

            if (mPlayConvertTaskAnimation) {
                final int left = mStartBounds.left - info.getRoot(0).getOffset().x;
                final int top = mStartBounds.top - info.getRoot(0).getOffset().y;
                startTransaction.setPosition(mTaskLeash, left, top);
                startTransaction.show(mSnapshot);
                // Move snapshot to root so that it remains visible while task is moved to taskview
                startTransaction.reparent(mSnapshot, info.getRoot(0).getLeash());
                startTransaction.setPosition(mSnapshot, left, top);
                startTransaction.setLayer(mSnapshot, Integer.MAX_VALUE);
            } else {
                final int left = mStartBounds.left - info.getRoot(0).getOffset().x;
                final int top = mStartBounds.top - info.getRoot(0).getOffset().y;
                startTransaction.setPosition(mTaskLeash, left, top);
            }
            startTransaction.apply();

            mTaskViewTransitions.onExternalDone(mTransition);
            mTransitionProgress.setTransitionReady();
            startExpandAnim();
            return true;
        }

        private void startExpandAnim() {
            if (mExpandedViewAnimator.canExpandView(mBubble)) {
                mPriorBubble = mExpandedViewAnimator.prepareConvertedView(mBubble);
            } else if (mExpandedViewAnimator.isExpanded()) {
                mTransitionProgress.setReadyToExpand();
            }
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "LaunchNewTaskBubble.startExpandAnim(): "
                    + "readyToAnimate=%b", mTransitionProgress.isReadyToAnimate());
            if (mTransitionProgress.isReadyToAnimate()) {
                playAnimation();
            }
        }

        @Override
        public void continueExpand() {
            mTransitionProgress.setReadyToExpand();
        }

        @Override
        public void surfaceCreated() {
            mTransitionProgress.setSurfaceReady();
            mMainExecutor.execute(() -> {
                ProtoLog.d(WM_SHELL_BUBBLES_NOISY,
                        "LaunchNewTaskBubble.surfaceCreated(): mTaskLeash=%s readyToAnimate=%b",
                        mTaskLeash, mTransitionProgress.isReadyToAnimate());
                final TaskViewTaskController tvc = mBubble.getTaskView().getController();
                final TaskViewRepository.TaskViewState state = mRepository.byTaskView(tvc);
                if (state == null) return;
                state.mVisible = true;
                if (mTransitionProgress.isReadyToAnimate()) {
                    playAnimation();
                }
            });
        }

        private void playAnimation() {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY,
                    "LaunchNewTaskBubble.playAnimation(): playConvert=%b",
                    mPlayConvertTaskAnimation);
            final TaskViewTaskController tv = mBubble.getTaskView().getController();
            final SurfaceControl.Transaction startT = new SurfaceControl.Transaction();
            // Set task position to 0,0 as it will be placed inside the TaskView
            startT.setPosition(mTaskLeash, 0, 0)
                    .reparent(mTaskLeash, mBubble.getTaskView().getSurfaceControl())
                    .setAlpha(mTaskLeash, 1f)
                    .show(mTaskLeash);
            mTaskViewTransitions.prepareOpenAnimation(tv, true /* new */, startT, mFinishT,
                    (ActivityManager.RunningTaskInfo) mTaskInfo, mTaskLeash, mFinishWct);
            // Add the task view task listener manually since we aren't going through
            // TaskViewTransitions (which normally sets up the listener via a pending launch cookie)
            // Note: In this path, because a new task is being started, the transition may receive
            // the transition for the task before the organizer does
            mTaskOrganizer.addListenerForTaskId(tv, mTaskInfo.taskId);

            if (mFinishWct.isEmpty()) {
                mFinishWct = null;
            }

            float startScale = 1f;
            if (mPlayConvertTaskAnimation) {
                mExpandedViewAnimator.animateConvert(startT, mStartBounds, startScale, mSnapshot,
                        mTaskLeash,
                        this::cleanup);
            } else {
                startT.apply();
                mExpandedViewAnimator.animateExpand(null, this::cleanup);
            }
        }

        private void cleanup() {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "LaunchNewTaskBubble.cleanup()");
            mFinishCb.onTransitionFinished(mFinishWct);
            mFinishCb = null;
        }
    }

    /**
     * Starts a new transition into a bubble, which will either play a launch animation (if the task
     * was not previously visible) or a convert animation (if the task is currently visible).
     */
    @VisibleForTesting
    class LaunchOrConvertToBubble implements TransitionHandler, BubbleTransition {
        final BubbleExpandedViewTransitionAnimator mExpandedViewAnimator;
        final BubblePositioner mPositioner;
        private final TransitionProgress mTransitionProgress;
        Bubble mBubble;
        IBinder mTransition;
        IBinder mPlayingTransition;
        Transitions.TransitionFinishCallback mFinishCb;
        WindowContainerTransaction mFinishWct = null;
        final Rect mStartBounds = new Rect();
        SurfaceControl mSnapshot = null;
        // The task info is resolved once we find the task from the transition info using the
        // pending launch cookie otherwise
        @Nullable
        TaskInfo mTaskInfo;
        @Nullable
        ActivityOptions.LaunchCookie mLaunchCookie;
        BubbleViewProvider mPriorBubble = null;
        // Whether we should play the convert-task animation, or the launch-task animation
        private boolean mPlayConvertTaskAnimation;

        private SurfaceControl.Transaction mFinishT;
        private SurfaceControl mTaskLeash;
        @Nullable
        private BubbleBarLocation mBubbleBarLocation;

        LaunchOrConvertToBubble(Bubble bubble, Context context,
                BubbleExpandedViewManager expandedViewManager, BubbleTaskViewFactory factory,
                BubblePositioner positioner, BubbleStackView stackView,
                BubbleBarLayerView layerView, BubbleIconFactory iconFactory,
                boolean inflateSync, @Nullable BubbleBarLocation bubbleBarLocation) {
            if (layerView != null) {
                mExpandedViewAnimator = layerView;
            } else {
                mExpandedViewAnimator = stackView;
            }
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "LaunchOrConvert(): expanded=%s",
                    mExpandedViewAnimator.isExpanded());
            mBubble = bubble;
            mTransitionProgress = new TransitionProgress(bubble);
            mPositioner = positioner;
            mBubble.setInflateSynchronously(inflateSync);
            mBubble.setPreparingTransition(this);
            mBubbleBarLocation = bubbleBarLocation;
            mBubble.inflate(
                    this::onInflated,
                    context,
                    expandedViewManager,
                    factory,
                    positioner,
                    stackView,
                    layerView,
                    iconFactory,
                    mAppInfoProvider,
                    false /* skipInflation */);
        }

        @VisibleForTesting
        void onInflated(Bubble b) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "LaunchOrConvert.onInflated()");
            if (b != mBubble) {
                throw new IllegalArgumentException("inflate callback doesn't match bubble");
            }
            if (!mBubble.isShortcut() && !mBubble.isApp()) {
                throw new IllegalArgumentException("Unsupported bubble type");
            }
            final Rect launchBounds = new Rect();
            mPositioner.getTaskViewRestBounds(launchBounds);

            final TaskView tv = b.getTaskView();
            tv.setSurfaceLifecycle(SurfaceView.SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT);
            final TaskViewRepository.TaskViewState state = mRepository.byTaskView(
                    tv.getController());
            if (state != null) {
                state.mVisible = true;
            }
            mTransitionProgress.setInflated();
            mTaskViewTransitions.enqueueExternal(tv.getController(), () -> {
                // We need to convert the next launch into a bubble
                mLaunchCookie = new ActivityOptions.LaunchCookie();
                mPendingEnterTransitions.put(mLaunchCookie.binder, this);
                ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Starting activity with pending cookie=%s",
                        mLaunchCookie.binder);

                final ActivityOptions opts = ActivityOptions.makeBasic();
                opts.setLaunchCookie(mLaunchCookie);
                opts.setTaskAlwaysOnTop(true);
                opts.setReparentLeafTaskToTda(true);
                final ActivityManager.RunningTaskInfo rootInfo =
                        mBubbleController.getAppBubbleRootTaskInfo();
                if (rootInfo != null) {
                    opts.setLaunchRootTask(rootInfo.token);
                } else {
                    opts.setLaunchNextToBubble(true);
                }
                opts.setLaunchWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
                opts.setLaunchBounds(launchBounds);
                if (mBubble.isShortcut()) {
                    final LauncherApps launcherApps = mContext.getSystemService(
                            LauncherApps.class);
                    launcherApps.startShortcut(mBubble.getShortcutInfo(),
                            null /* sourceBounds */, opts.toBundle());
                } else if (mBubble.isApp()) {
                    final ActivityOptions sendOpts = ActivityOptions.makeBasic();
                    sendOpts.setPendingIntentBackgroundActivityStartMode(
                            MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS);
                    final Bundle sendOptsBundle = sendOpts.toBundle();
                    final PendingIntent intent;
                    if (mBubble.getPendingIntent() != null) {
                        intent = mBubble.getPendingIntent();
                        sendOptsBundle.putAll(opts.toBundle());
                    } else {
                        opts.setPendingIntentCreatorBackgroundActivityStartMode(
                                MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS);
                        intent = PendingIntent.getActivityAsUser(mContext, 0,
                                mBubble.getIntent(), FLAG_IMMUTABLE | FLAG_ONE_SHOT,
                                opts.toBundle(), mBubble.getUser());
                    }
                    try {
                        intent.send(sendOptsBundle);
                    } catch (PendingIntent.CanceledException e) {
                        Log.w(TAG, "Failed to launch app bubble");
                    }
                }

                // Add the task view task listener manually since we aren't going through
                // TaskViewTransitions (which normally sets up the listener via a pending launch cookie
                mTaskOrganizer.setPendingLaunchCookieListener(mLaunchCookie.binder,
                        mBubble.getTaskView().getController());

                // We use a stub transition here since we don't know what is incoming, but it
                // won't actually match any transition when queried in TaskViewTransitions,
                // which is Ok since we don't want TaskViewTransitions to handle this anyways.
                // However, we do need to use it whenever calling onExternalDone() instead of
                // the incoming transition.
                ProtoLog.d(WM_SHELL_BUBBLES, "starting activity");
                mTransition = new Binder();
                return mTransition;
            });
        }

        @Override
        public void skip() {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "LaunchOrConvert.skip()");
            mBubble.setPreparingTransition(null);
            cleanup();
        }

        @Override
        public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
                @Nullable TransitionRequestInfo request) {
            return null;
        }

        @Override
        public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startT,
                @NonNull SurfaceControl.Transaction finishT,
                @NonNull IBinder mergeTarget,
                @NonNull Transitions.TransitionFinishCallback finishCallback) {
        }

        @Override
        public void onTransitionConsumed(@NonNull IBinder transition, boolean aborted,
                @NonNull SurfaceControl.Transaction finishTransaction) {
            if (!aborted) return;
            mTaskViewTransitions.onExternalDone(mTransition);
            mTransition = null;
            if (mLaunchCookie != null) {
                ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "Removing pending transition for cookie=%s",
                        mLaunchCookie.binder);
                mPendingEnterTransitions.remove(mLaunchCookie.binder);
            }
            mEnterTransitions.remove(transition);
        }

        /**
         * @return true As DefaultMixedTransition assumes that this transition will be handled by
         * this handler in all cases.
         */
        @Override
        public boolean startAnimation(@NonNull IBinder transition,
                @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startTransaction,
                @NonNull SurfaceControl.Transaction finishTransaction,
                @NonNull Transitions.TransitionFinishCallback finishCallback) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "LaunchOrConvert.startAnimation()");
            mPlayingTransition = transition;

            // Identify the task that we are converting or launching. Note, we iterate back to front
            // so that we can adjust alpha for revealed surfaces as needed.
            boolean found = false;
            mPlayConvertTaskAnimation = false;
            for (int i = info.getChanges().size() - 1; i >= 0; i--) {
                final TransitionInfo.Change chg = info.getChanges().get(i);
                final boolean isLaunchedTask = (chg.getTaskInfo() != null)
                        && (chg.getMode() == TRANSIT_CHANGE || isOpeningMode(chg.getMode()))
                        && (chg.getTaskInfo().launchCookies.contains(mLaunchCookie.binder));
                if (isLaunchedTask) {
                    mStartBounds.set(chg.getStartAbsBounds());
                    // Converting a task into taskview, so treat as "new"
                    mFinishWct = new WindowContainerTransaction();
                    mTaskInfo = chg.getTaskInfo();
                    mFinishT = finishTransaction;
                    mTaskLeash = chg.getLeash();
                    mSnapshot = chg.getSnapshot();
                    mPlayConvertTaskAnimation = !isOpeningMode(chg.getMode()) && mSnapshot != null;
                    found = true;
                } else {
                    // In core-initiated launches, the transition is of an OPEN type, and we need to
                    // manually show the surfaces behind the newly bubbled task
                    if (info.getType() == TRANSIT_OPEN && isOpeningMode(chg.getMode())) {
                        startTransaction.setAlpha(chg.getLeash(), 1f);
                    }
                }
            }
            if (!found) {
                Slog.w(TAG, "Expected a TaskView conversion in this transition but didn't get "
                        + "one, cleaning up the task view");
                mBubble.getTaskView().getController().setTaskNotFound();
                mTaskViewTransitions.onExternalDone(mTransition);
                finishCallback.onTransitionFinished(null /* finishWct */);
                return true;
            }
            mFinishCb = finishCallback;

            // Now update state (and talk to launcher) in parallel with snapshot stuff
            mBubbleData.notificationEntryUpdated(mBubble, /* suppressFlyout= */ true,
                    /* showInShade= */ false, mBubbleBarLocation);

            if (mPlayConvertTaskAnimation) {
                final int left = mStartBounds.left - info.getRoot(0).getOffset().x;
                final int top = mStartBounds.top - info.getRoot(0).getOffset().y;
                startTransaction.setPosition(mTaskLeash, left, top);
                startTransaction.show(mSnapshot);
                // Move snapshot to root so that it remains visible while task is moved to taskview
                startTransaction.reparent(mSnapshot, info.getRoot(0).getLeash());
                startTransaction.setPosition(mSnapshot, left, top);
                startTransaction.setLayer(mSnapshot, Integer.MAX_VALUE);
            } else {
                final int left = mStartBounds.left - info.getRoot(0).getOffset().x;
                final int top = mStartBounds.top - info.getRoot(0).getOffset().y;
                startTransaction.setPosition(mTaskLeash, left, top);
            }
            startTransaction.apply();

            mTaskViewTransitions.onExternalDone(mTransition);
            mTransitionProgress.setTransitionReady();
            startExpandAnim();
            return true;
        }

        private void startExpandAnim() {
            final boolean animate = mExpandedViewAnimator.canExpandView(mBubble);
            if (animate) {
                mPriorBubble = mExpandedViewAnimator.prepareConvertedView(mBubble);
            }
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "LaunchOrConvert.startExpandAnim(): "
                    + "readyToAnimate=%b", mTransitionProgress.isReadyToAnimate());
            if (mPriorBubble != null) {
                // TODO: an animation. For now though, just remove it.
                final BubbleBarExpandedView priorView = mPriorBubble.getBubbleBarExpandedView();
                mExpandedViewAnimator.removeViewFromTransition(priorView);
                mPriorBubble = null;
            }
            if (!animate || mTransitionProgress.isReadyToAnimate()) {
                playAnimation(animate);
            }
        }

        @Override
        public void continueExpand() {
            mTransitionProgress.setReadyToExpand();
        }

        @Override
        public void surfaceCreated() {
            mTransitionProgress.setSurfaceReady();
            mMainExecutor.execute(() -> {
                ProtoLog.d(WM_SHELL_BUBBLES_NOISY,
                        "LaunchOrConvert.surfaceCreated(): mTaskLeash=%s readyToAnimate=%b",
                        mTaskLeash, mTransitionProgress.isReadyToAnimate());
                final TaskViewTaskController tvc = mBubble.getTaskView().getController();
                final TaskViewRepository.TaskViewState state = mRepository.byTaskView(tvc);
                if (state == null) return;
                state.mVisible = true;
                if (mTransitionProgress.isReadyToAnimate()) {
                    playAnimation(true /* animate */);
                }
            });
        }

        private void playAnimation(boolean animate) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY,
                    "LaunchOrConvert.playAnimation(): playConvert=%b",
                    mPlayConvertTaskAnimation);
            final TaskViewTaskController tv = mBubble.getTaskView().getController();
            final SurfaceControl.Transaction startT = new SurfaceControl.Transaction();
            // Set task position to 0,0 as it will be placed inside the TaskView
            startT.setPosition(mTaskLeash, 0, 0);
            if (!mPlayConvertTaskAnimation) {
                startT.reparent(mTaskLeash, mBubble.getTaskView().getSurfaceControl())
                        .setAlpha(mTaskLeash, 1f)
                        .show(mTaskLeash);
            }
            mTaskViewTransitions.prepareOpenAnimation(tv, true /* new */, startT, mFinishT,
                    (ActivityManager.RunningTaskInfo) mTaskInfo, mTaskLeash, mFinishWct);

            if (mFinishWct.isEmpty()) {
                mFinishWct = null;
            }

            if (animate) {
                float startScale = 1f;
                if (mPlayConvertTaskAnimation) {
                    mExpandedViewAnimator.animateConvert(startT,
                            mStartBounds,
                            startScale,
                            mSnapshot,
                            mTaskLeash,
                            this::cleanup);
                } else {
                    startT.apply();
                    mExpandedViewAnimator.animateExpand(null, this::cleanup);
                }
            } else {
                startT.apply();
                cleanup();
            }
        }

        private void cleanup() {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "LaunchNewTaskBubble.cleanup(): removeCookie=%s",
                    mLaunchCookie.binder);
            mFinishCb.onTransitionFinished(mFinishWct);
            mFinishCb = null;
            mPendingEnterTransitions.remove(mLaunchCookie.binder);
            mEnterTransitions.remove(mPlayingTransition);
        }
    }

    /**
     * BubbleTransition that coordinates the process of a non-bubble task becoming a bubble. The
     * steps are as follows:
     *
     * 1. Start inflating the bubble view
     * 2. Once inflated (but not-yet visible), tell WM to do the shell-transition.
     * 3. When the transition becomes ready, notify Launcher in parallel
     * 4. Wait for surface to be created
     * 5. Once surface is ready, animate the task to a bubble
     *
     * While the animation is pending, we keep a reference to the pending transition in the bubble.
     * This allows us to check in other parts of the code that this bubble will be shown via the
     * transition animation.
     *
     * startAnimation, continueExpand and surfaceCreated are set-up to happen in either order,
     * to support UX/timing adjustments.
     */
    @VisibleForTesting
    class ConvertToBubble implements Transitions.TransitionHandler, BubbleTransition {
        final BubbleExpandedViewTransitionAnimator mExpandedViewAnimator;
        final BubblePositioner mPositioner;
        final HomeIntentProvider mHomeIntentProvider;
        Bubble mBubble;
        @Nullable
        DragData mDragData;
        IBinder mTransition;
        Transitions.TransitionFinishCallback mFinishCb;
        WindowContainerTransaction mFinishWct = null;
        final Rect mStartBounds = new Rect();
        SurfaceControl mSnapshot = null;
        TaskInfo mTaskInfo;
        BubbleViewProvider mPriorBubble = null;

        private final TransitionProgress mTransitionProgress;
        private SurfaceControl.Transaction mFinishT;
        private SurfaceControl mTaskLeash;

        ConvertToBubble(Bubble bubble, TaskInfo taskInfo, Context context,
                BubbleExpandedViewManager expandedViewManager, BubbleTaskViewFactory factory,
                BubblePositioner positioner, BubbleStackView stackView,
                BubbleBarLayerView layerView, BubbleIconFactory iconFactory,
                HomeIntentProvider homeIntentProvider, @Nullable DragData dragData,
                boolean inflateSync) {
            if (layerView != null) {
                mExpandedViewAnimator = layerView;
            } else {
                mExpandedViewAnimator = stackView;
            }
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "ConvertToBubble(): expanded=%s",
                    mExpandedViewAnimator.isExpanded());
            mBubble = bubble;
            mTransitionProgress = new TransitionProgress(bubble);
            mTaskInfo = taskInfo;
            mPositioner = positioner;
            mHomeIntentProvider = homeIntentProvider;
            mDragData = dragData;
            mBubble.setInflateSynchronously(inflateSync);
            mBubble.setPreparingTransition(this);
            mBubble.inflate(
                    this::onInflated,
                    context,
                    expandedViewManager,
                    factory,
                    positioner,
                    stackView,
                    layerView,
                    iconFactory,
                    mAppInfoProvider,
                    false /* skipInflation */);
        }

        @VisibleForTesting
        void onInflated(Bubble b) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "ConvertToBubble.onInflated()");
            if (b != mBubble) {
                throw new IllegalArgumentException("inflate callback doesn't match bubble");
            }
            final Rect launchBounds = new Rect();
            mPositioner.getTaskViewRestBounds(launchBounds);
            final boolean reparentToTda =
                    mTaskInfo.getWindowingMode() == WINDOWING_MODE_MULTI_WINDOW
                            && mTaskInfo.getParentTaskId() != INVALID_TASK_ID;

            final WindowContainerTransaction wct = getEnterBubbleTransaction(
                    mTaskInfo.token, true /* isAppBubble */, reparentToTda);
            mHomeIntentProvider.addLaunchHomePendingIntent(wct, mTaskInfo.displayId,
                    mTaskInfo.userId);

            wct.setBounds(mTaskInfo.token, launchBounds);

            final TaskView tv = b.getTaskView();
            tv.setSurfaceLifecycle(SurfaceView.SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT);
            final TaskViewRepository.TaskViewState state = mRepository.byTaskView(
                    tv.getController());
            if (state != null) {
                state.mVisible = true;
            }
            mTransitionProgress.setInflated();
            mTaskViewTransitions.enqueueExternal(tv.getController(), () -> {
                mTransition = mTransitions.startTransition(TRANSIT_CONVERT_TO_BUBBLE, wct, this);
                return mTransition;
            });
        }

        @Override
        public void skip() {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "ConvertToBubble.skip()");
            mBubble.setPreparingTransition(null);
            mFinishCb.onTransitionFinished(mFinishWct);
            mFinishCb = null;
        }

        @Override
        public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
                @Nullable TransitionRequestInfo request) {
            return null;
        }

        @Override
        public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startT,
                @NonNull SurfaceControl.Transaction finishT,
                @NonNull IBinder mergeTarget,
                @NonNull Transitions.TransitionFinishCallback finishCallback) {
        }

        @Override
        public void onTransitionConsumed(@NonNull IBinder transition, boolean aborted,
                @NonNull SurfaceControl.Transaction finishTransaction) {
            if (!aborted) return;
            mTransition = null;
            mTaskViewTransitions.onExternalDone(transition);
        }

        @Override
        public boolean startAnimation(@NonNull IBinder transition,
                @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startTransaction,
                @NonNull SurfaceControl.Transaction finishTransaction,
                @NonNull Transitions.TransitionFinishCallback finishCallback) {
            if (mTransition != transition) return false;
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "ConvertToBubble.startAnimation()");
            boolean found = false;
            for (int i = 0; i < info.getChanges().size(); ++i) {
                final TransitionInfo.Change chg = info.getChanges().get(i);
                if (chg.getTaskInfo() == null) continue;
                if (chg.getMode() != TRANSIT_CHANGE && chg.getMode() != TRANSIT_TO_FRONT) continue;
                if (!mTaskInfo.token.equals(chg.getTaskInfo().token)) continue;
                mStartBounds.set(chg.getStartAbsBounds());
                // Converting a task into taskview, so treat as "new"
                mFinishWct = new WindowContainerTransaction();
                mTaskInfo = chg.getTaskInfo();
                mFinishT = finishTransaction;
                mTaskLeash = chg.getLeash();
                found = true;
                mSnapshot = chg.getSnapshot();
                break;
            }
            if (!found) {
                Slog.w(TAG, "Expected a TaskView conversion in this transition but didn't get "
                        + "one, cleaning up the task view");
                mBubble.getTaskView().getController().setTaskNotFound();
                mTaskViewTransitions.onExternalDone(transition);
                return false;
            }
            mFinishCb = finishCallback;

            if (mDragData != null) {
                mStartBounds.offsetTo((int) mDragData.getDragPosition().x,
                        (int) mDragData.getDragPosition().y);
                startTransaction.setScale(mSnapshot, mDragData.getTaskScale(),
                        mDragData.getTaskScale());
                startTransaction.setCornerRadius(mSnapshot, mDragData.getCornerRadius());
            }

            // Now update state (and talk to launcher) in parallel with snapshot stuff
            mBubbleData.notificationEntryUpdated(mBubble, /* suppressFlyout= */ true,
                    /* showInShade= */ false);

            final int left = mStartBounds.left - info.getRoot(0).getOffset().x;
            final int top = mStartBounds.top - info.getRoot(0).getOffset().y;
            startTransaction.setPosition(mTaskLeash, left, top);
            startTransaction.show(mSnapshot);
            // Move snapshot to root so that it remains visible while task is moved to taskview
            startTransaction.reparent(mSnapshot, info.getRoot(0).getLeash());
            startTransaction.setPosition(mSnapshot, left, top);
            startTransaction.setLayer(mSnapshot, Integer.MAX_VALUE);

            startTransaction.apply();

            mTaskViewTransitions.onExternalDone(transition);
            mTransitionProgress.setTransitionReady();
            startExpandAnim();
            return true;
        }

        private void startExpandAnim() {
            final boolean animate = mExpandedViewAnimator.canExpandView(mBubble);
            if (animate) {
                mPriorBubble = mExpandedViewAnimator.prepareConvertedView(mBubble);
            }
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "ConvertToBubble.startExpandAnim(): "
                    + "readyToAnimate=%b", mTransitionProgress.isReadyToAnimate());
            if (mPriorBubble != null) {
                // TODO: an animation. For now though, just remove it.
                final BubbleBarExpandedView priorView = mPriorBubble.getBubbleBarExpandedView();
                mExpandedViewAnimator.removeViewFromTransition(priorView);
                mPriorBubble = null;
            }
            if (!animate || mTransitionProgress.isReadyToAnimate()) {
                playAnimation(animate);
            }
        }

        @Override
        public void continueExpand() {
            mTransitionProgress.setReadyToExpand();
        }

        @Override
        public void surfaceCreated() {
            mTransitionProgress.setSurfaceReady();
            mMainExecutor.execute(() -> {
                ProtoLog.d(WM_SHELL_BUBBLES_NOISY,
                        "ConvertToBubble.surfaceCreated(): mTaskLeash=%s readyToAnimate=%b",
                        mTaskLeash, mTransitionProgress.isReadyToAnimate());
                final TaskViewTaskController tvc = mBubble.getTaskView().getController();
                final TaskViewRepository.TaskViewState state = mRepository.byTaskView(tvc);
                if (state == null) return;
                state.mVisible = true;
                if (mTransitionProgress.isReadyToAnimate()) {
                    playAnimation(true /* animate */);
                }
            });
        }

        private void playAnimation(boolean animate) {
            ProtoLog.d(WM_SHELL_BUBBLES_NOISY, "ConvertToBubble.playAnimation()");
            final TaskViewTaskController tv = mBubble.getTaskView().getController();
            final SurfaceControl.Transaction startT = new SurfaceControl.Transaction();
            // Set task position to 0,0 as it will be placed inside the TaskView
            startT.setPosition(mTaskLeash, 0, 0);
            mTaskViewTransitions.prepareOpenAnimation(tv, true /* new */, startT, mFinishT,
                    (ActivityManager.RunningTaskInfo) mTaskInfo, mTaskLeash, mFinishWct);
            // Add the task view task listener manually since we aren't going through
            // TaskViewTransitions (which normally sets up the listener via a pending launch cookie)
            mTaskOrganizer.addListenerForTaskId(tv, mTaskInfo.taskId);

            if (mFinishWct.isEmpty()) {
                mFinishWct = null;
            }

            if (animate) {
                float startScale = mDragData != null ? mDragData.getTaskScale() : 1f;
                mExpandedViewAnimator.animateConvert(startT,
                        mStartBounds,
                        startScale,
                        mSnapshot,
                        mTaskLeash,
                        () -> {
                            mFinishCb.onTransitionFinished(mFinishWct);
                            mFinishCb = null;
                        });
            } else {
                startT.apply();
                mFinishCb.onTransitionFinished(mFinishWct);
                mFinishCb = null;
            }
        }
    }

    /**
     * BubbleTransition that coordinates the setup for moving a task out of a bubble. The actual
     * animation is owned by the "receiver" of the task; however, because Bubbles uses TaskView,
     * we need to do some extra coordination work to get the task surface out of the view
     * "seamlessly".
     *
     * The process here looks like:
     * 1. Send transition to WM for leaving bubbles mode
     * 2. in startAnimation, set-up a "pluck" operation to pull the task surface out of taskview
     * 3. Once "plucked", remove the view (calls continueCollapse when surfaces can be cleaned-up)
     * 4. Then re-dispatch the transition animation so that the "receiver" can animate it.
     *
     * So, constructor -> startAnimation -> continueCollapse -> re-dispatch.
     */
    @VisibleForTesting
    class ConvertFromBubble implements TransitionHandler, BubbleTransition {
        @NonNull final Bubble mBubble;
        IBinder mTransition;
        TaskInfo mTaskInfo;
        SurfaceControl mTaskLeash;
        SurfaceControl mRootLeash;

        ConvertFromBubble(@NonNull Bubble bubble, TaskInfo taskInfo) {
            mBubble = bubble;
            mTaskInfo = taskInfo;

            mBubble.setPreparingTransition(this);
            final WindowContainerToken token = mTaskInfo.getToken();
            final Binder captionInsetsOwner = mBubble.getTaskView().getCaptionInsetsOwner();
            final WindowContainerTransaction wct =
                    getExitBubbleTransaction(token, captionInsetsOwner);
            mTaskViewTransitions.enqueueExternal(
                    mBubble.getTaskView().getController(),
                    () -> {
                        mTransition = mTransitions.startTransition(TRANSIT_CHANGE, wct, this);
                        return mTransition;
                    });
        }

        @Override
        public void skip() {
            mBubble.setPreparingTransition(null);
            final TaskViewTaskController tv =
                    mBubble.getTaskView().getController();
            tv.notifyTaskRemovalStarted(tv.getTaskInfo());
            mTaskLeash = null;
        }

        @Override
        public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
                @Nullable TransitionRequestInfo request) {
            return null;
        }

        @Override
        public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startT,
                @NonNull SurfaceControl.Transaction finishT,
                @NonNull IBinder mergeTarget,
                @NonNull Transitions.TransitionFinishCallback finishCallback) {
        }

        @Override
        public void onTransitionConsumed(@NonNull IBinder transition, boolean aborted,
                @NonNull SurfaceControl.Transaction finishTransaction) {
            if (!aborted) return;
            mTransition = null;
            skip();
            mTaskViewTransitions.onExternalDone(transition);
        }

        @Override
        public boolean startAnimation(@NonNull IBinder transition,
                @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startTransaction,
                @NonNull SurfaceControl.Transaction finishTransaction,
                @NonNull Transitions.TransitionFinishCallback finishCallback) {
            if (mTransition != transition) return false;

            final TaskViewTaskController tv =
                    mBubble.getTaskView().getController();
            if (tv == null) {
                mTaskViewTransitions.onExternalDone(transition);
                return false;
            }

            TransitionInfo.Change taskChg = null;

            boolean found = false;
            for (int i = 0; i < info.getChanges().size(); ++i) {
                final TransitionInfo.Change chg = info.getChanges().get(i);
                if (chg.getTaskInfo() == null) continue;
                if (chg.getMode() != TRANSIT_CHANGE) continue;
                if (!mTaskInfo.token.equals(chg.getTaskInfo().token)) continue;
                found = true;
                mRepository.remove(tv);
                taskChg = chg;
                break;
            }

            if (!found) {
                Slog.w(TAG, "Expected a TaskView conversion in this transition but didn't get "
                        + "one, cleaning up the task view");
                tv.setTaskNotFound();
                skip();
                mTaskViewTransitions.onExternalDone(transition);
                return false;
            }

            mTaskLeash = taskChg.getLeash();
            mRootLeash = info.getRoot(0).getLeash();

            SurfaceControl dest = getExpandedView(mBubble).getViewRootImpl().getSurfaceControl();
            final Runnable onPlucked = () -> {
                // Need to remove the taskview AFTER applying the startTransaction because
                // it isn't synchronized.
                tv.notifyTaskRemovalStarted(tv.getTaskInfo());
                // Unset after removeView so it can be used to pick a different animation.
                mBubble.setPreparingTransition(null);
                mBubbleData.setExpanded(false /* expanded */);
            };
            if (dest != null) {
                pluck(mTaskLeash, getExpandedView(mBubble), dest,
                        taskChg.getStartAbsBounds().left - info.getRoot(0).getOffset().x,
                        taskChg.getStartAbsBounds().top - info.getRoot(0).getOffset().y,
                        getCornerRadius(mBubble), startTransaction,
                        onPlucked);
                getExpandedView(mBubble).post(() -> mTransitions.dispatchTransition(
                        mTransition, info, startTransaction, finishTransaction, finishCallback,
                        null));
            } else {
                onPlucked.run();
                mTransitions.dispatchTransition(mTransition, info, startTransaction,
                        finishTransaction, finishCallback, null);
            }

            mTaskViewTransitions.onExternalDone(transition);
            return true;
        }

        @Override
        public void continueCollapse() {
            mBubble.cleanupTaskView();
            if (mTaskLeash == null || !mTaskLeash.isValid() || !mRootLeash.isValid()) return;
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            t.reparent(mTaskLeash, mRootLeash);
            t.apply();
        }

        private View getExpandedView(@NonNull Bubble bubble) {
            if (bubble.getBubbleBarExpandedView() != null) {
                return bubble.getBubbleBarExpandedView();
            }
            return bubble.getExpandedView();
        }

        private float getCornerRadius(@NonNull Bubble bubble) {
            if (bubble.getBubbleBarExpandedView() != null) {
                return bubble.getBubbleBarExpandedView().getCornerRadius();
            }
            return bubble.getExpandedView().getCornerRadius();
        }
    }

    /**
     * A transition that converts a dragged bubble icon to a full screen window.
     *
     * <p>This transition assumes that the bubble is invisible so it is simply sent to front.
     */
    class DraggedBubbleIconToFullscreen implements TransitionHandler, BubbleTransition {

        IBinder mTransition;
        final Bubble mBubble;
        final Point mDropLocation;
        final TransactionProvider mTransactionProvider;

        DraggedBubbleIconToFullscreen(Bubble bubble, Point dropLocation) {
            this(bubble, dropLocation, SurfaceControl.Transaction::new);
        }

        @VisibleForTesting
        DraggedBubbleIconToFullscreen(Bubble bubble, Point dropLocation,
                TransactionProvider transactionProvider) {
            mBubble = bubble;
            mDropLocation = dropLocation;
            mTransactionProvider = transactionProvider;
            bubble.setPreparingTransition(this);
            final WindowContainerToken token = bubble.getTaskView().getTaskInfo().getToken();
            final Binder captionInsetsOwner = bubble.getTaskView().getCaptionInsetsOwner();
            final WindowContainerTransaction wct =
                    getExitBubbleTransaction(token, captionInsetsOwner);
            wct.reorder(token, /* onTop= */ true);
            if (!BubbleAnythingFlagHelper.enableCreateAnyBubbleWithForceExcludedFromRecents()) {
                wct.setHidden(token, false);
            }
            mTaskViewTransitions.enqueueExternal(bubble.getTaskView().getController(), () -> {
                mTransition = mTransitions.startTransition(TRANSIT_TO_FRONT, wct, this);
                return mTransition;
            });
        }

        @Override
        public void skip() {
            mBubble.setPreparingTransition(null);
        }

        @Override
        public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startTransaction,
                @NonNull SurfaceControl.Transaction finishTransaction,
                @NonNull Transitions.TransitionFinishCallback finishCallback) {
            if (mTransition != transition) {
                return false;
            }

            final TaskViewTaskController taskViewTaskController =
                    mBubble.getTaskView().getController();
            if (taskViewTaskController == null) {
                mTaskViewTransitions.onExternalDone(transition);
                finishCallback.onTransitionFinished(null);
                return true;
            }

            TransitionInfo.Change change = findTransitionChange(info);
            if (change == null) {
                Slog.w(TAG, "Expected a TaskView transition to front but didn't find "
                        + "one, cleaning up the task view");
                taskViewTaskController.setTaskNotFound();
                mTaskViewTransitions.onExternalDone(transition);
                finishCallback.onTransitionFinished(null);
                return true;
            }
            mRepository.remove(taskViewTaskController);

            final SurfaceControl taskLeash = change.getLeash();
            // set the initial position of the task with 0 scale
            startTransaction.setPosition(taskLeash, mDropLocation.x, mDropLocation.y);
            startTransaction.setScale(taskLeash, 0, 0);
            startTransaction.apply();

            final SurfaceControl.Transaction animT = mTransactionProvider.get();
            ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
            animator.setDuration(250);
            animator.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(@NonNull Animator animation) {
                    float progress = animator.getAnimatedFraction();
                    float x = mDropLocation.x * (1 - progress);
                    float y = mDropLocation.y * (1 - progress);
                    animT.setPosition(taskLeash, x, y);
                    animT.setScale(taskLeash, progress, progress);
                    animT.apply();
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(@NonNull Animator animation) {
                    animT.close();
                    finishCallback.onTransitionFinished(null);
                }
            });
            animator.start();
            taskViewTaskController.notifyTaskRemovalStarted(mBubble.getTaskView().getTaskInfo());
            mTaskViewTransitions.onExternalDone(transition);
            return true;
        }

        private TransitionInfo.Change findTransitionChange(TransitionInfo info) {
            TransitionInfo.Change result = null;
            WindowContainerToken token = mBubble.getTaskView().getTaskInfo().getToken();
            for (int i = 0; i < info.getChanges().size(); ++i) {
                final TransitionInfo.Change change = info.getChanges().get(i);
                if (change.getTaskInfo() == null) {
                    continue;
                }
                if (change.getMode() != TRANSIT_TO_FRONT) {
                    continue;
                }
                if (!token.equals(change.getTaskInfo().token)) {
                    continue;
                }
                result = change;
                break;
            }
            return result;
        }

        @Override
        public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startTransaction,
                @NonNull SurfaceControl.Transaction finishTransaction,
                @NonNull IBinder mergeTarget,
                @NonNull Transitions.TransitionFinishCallback finishCallback) {
        }

        @Override
        public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
                @NonNull TransitionRequestInfo request) {
            return null;
        }

        @Override
        public void onTransitionConsumed(@NonNull IBinder transition, boolean aborted,
                @Nullable SurfaceControl.Transaction finishTransaction) {
            if (!aborted) {
                return;
            }
            mTransition = null;
            mTaskViewTransitions.onExternalDone(transition);
        }
    }

    /**
     * A transition to convert an expanded floating bubble to a bar bubble.
     *
     * <p>This transition class should be created after switching over to the new Bubbles window for
     * bubble bar mode, and adding the TaskView to the new window.
     *
     * <p>The transition is only started after calling {@link #continueConvert(BubbleBarLayerView)}
     * once the bubble bar location on the screen is known.
     */
    class FloatingToBarConversion implements TransitionHandler, BubbleTransition {
        private final BubblePositioner mPositioner;
        private final Bubble mBubble;
        private final TransactionProvider mTransactionProvider;
        IBinder mTransition;
        private final Rect mBounds = new Rect();
        private final WindowContainerTransaction mWct = new WindowContainerTransaction();
        private SurfaceControl mTaskLeash;
        private SurfaceControl.Transaction mFinishTransaction;
        private boolean mIsStarted = false;

        FloatingToBarConversion(Bubble bubble, BubblePositioner positioner) {
            this(bubble, SurfaceControl.Transaction::new, positioner);
        }

        @VisibleForTesting
        FloatingToBarConversion(Bubble bubble, TransactionProvider transactionProvider,
                BubblePositioner positioner) {
            mBubble = bubble;
            mBubble.setPreparingTransition(this);
            mTransactionProvider = transactionProvider;
            mPositioner = positioner;
        }

        @Override
        public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
                @Nullable TransitionRequestInfo request) {
            return null;
        }

        @Override
        public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startT,
                @NonNull SurfaceControl.Transaction finishT,
                @NonNull IBinder mergeTarget,
                @NonNull Transitions.TransitionFinishCallback finishCallback) {
        }

        @Override
        public void onTransitionConsumed(@NonNull IBinder transition, boolean aborted,
                @Nullable SurfaceControl.Transaction finishTransaction) {
            if (!aborted) return;
            mTransition = null;
            mTaskViewTransitions.onExternalDone(transition);
        }

        @Override
        public boolean startAnimation(@NonNull IBinder transition,
                @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startTransaction,
                @NonNull SurfaceControl.Transaction finishTransaction,
                @NonNull Transitions.TransitionFinishCallback finishCallback) {
            if (mTransition != transition) {
                return false;
            }

            final TaskViewTaskController taskViewTaskController =
                    mBubble.getTaskView().getController();
            if (taskViewTaskController == null) {
                mTaskViewTransitions.onExternalDone(transition);
                finishCallback.onTransitionFinished(null);
                return true;
            }

            TransitionInfo.Change change = findTransitionChange(info);
            if (change == null) {
                Slog.w(TAG, "Expected a TaskView transition to front but didn't find "
                        + "one, cleaning up the task view");
                taskViewTaskController.setTaskNotFound();
                mTaskViewTransitions.onExternalDone(transition);
                finishCallback.onTransitionFinished(null);
                return true;
            }

            mTaskLeash = change.getLeash();
            mFinishTransaction = finishTransaction;
            updateBubbleTask();
            return true;
        }

        private TransitionInfo.Change findTransitionChange(TransitionInfo info) {
            WindowContainerToken token = mBubble.getTaskView().getTaskInfo().getToken();
            for (int i = 0; i < info.getChanges().size(); ++i) {
                final TransitionInfo.Change change = info.getChanges().get(i);
                if (change.getTaskInfo() == null) {
                    continue;
                }
                if (!token.equals(change.getTaskInfo().token)) {
                    continue;
                }
                return change;
            }
            return null;
        }

        @Override
        public void continueConvert(BubbleBarLayerView layerView) {
            mPositioner.getTaskViewRestBounds(mBounds);
            mWct.setBounds(mBubble.getTaskView().getTaskInfo().token, mBounds);
            if (!mIsStarted) {
                startTransition();
            }
        }

        private void startTransition() {
            mIsStarted = true;
            final TaskView tv = mBubble.getTaskView();
            mTransition = mTransitions.startTransition(TRANSIT_BUBBLE_CONVERT_FLOATING_TO_BAR,
                    mWct, this);
            mTaskViewTransitions.enqueueExternal(tv.getController(), () -> mTransition);
        }

        @Override
        public void mergeWithUnfold(SurfaceControl taskLeash, SurfaceControl.Transaction finishT) {
            mTaskLeash = taskLeash;
            mFinishTransaction = finishT;
            updateBubbleTask();
        }

        private void updateBubbleTask() {
            final TaskViewTaskController tvc = mBubble.getTaskView().getController();
            final SurfaceControl taskViewSurface = mBubble.getTaskView().getSurfaceControl();
            final TaskViewRepository.TaskViewState state = mRepository.byTaskView(tvc);
            if (state == null) return;
            state.mVisible = true;
            state.mBounds.set(mBounds);
            final SurfaceControl.Transaction startT = mTransactionProvider.get();

            // since the task view is switching windows, its surface needs to be moved over to the
            // new bubble window surface
            startT.reparent(taskViewSurface,
                    mBubble.getBubbleBarExpandedView()
                            .getViewRootImpl()
                            .updateAndGetBoundsLayer(startT));

            startT.reparent(mTaskLeash, taskViewSurface);
            startT.setPosition(mTaskLeash, 0, 0);
            startT.setCornerRadius(mTaskLeash,
                    mBubble.getBubbleBarExpandedView().getRestingCornerRadius());
            startT.setWindowCrop(mTaskLeash, mBounds.width(), mBounds.height());
            startT.apply();
            mBubble.setPreparingTransition(null);
            mFinishTransaction.reparent(mTaskLeash, taskViewSurface);
            mFinishTransaction.setPosition(mTaskLeash, 0, 0);
            mFinishTransaction.setWindowCrop(mTaskLeash, mBounds.width(), mBounds.height());
            mTaskViewTransitions.onExternalDone(mTransition);
            mTransition = null;
        }
    }

    interface TransactionProvider {
        SurfaceControl.Transaction get();
    }
}

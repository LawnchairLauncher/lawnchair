/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.pip;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.util.RotationUtils.deltaRotation;
import static android.util.RotationUtils.rotateBounds;

import static com.android.wm.shell.ShellTaskOrganizer.TASK_LISTENER_TYPE_PIP;
import static com.android.wm.shell.ShellTaskOrganizer.taskListenerTypeToString;
import static com.android.wm.shell.desktopmode.DesktopModeUtils.calculateInitialBounds;
import static com.android.wm.shell.desktopmode.DesktopModeUtils.DESKTOP_MODE_INITIAL_BOUNDS_SCALE;
import static com.android.wm.shell.pip.PipAnimationController.TRANSITION_DIRECTION_EXPAND_OR_UNEXPAND;
import static com.android.wm.shell.pip.PipAnimationController.TRANSITION_DIRECTION_LEAVE_PIP;
import static com.android.wm.shell.pip.PipAnimationController.TRANSITION_DIRECTION_LEAVE_PIP_TO_SPLIT_SCREEN;
import static com.android.wm.shell.pip.PipAnimationController.TRANSITION_DIRECTION_NONE;
import static com.android.wm.shell.pip.PipAnimationController.TRANSITION_DIRECTION_REMOVE_STACK;
import static com.android.wm.shell.pip.PipAnimationController.TRANSITION_DIRECTION_SAME;
import static com.android.wm.shell.pip.PipAnimationController.TRANSITION_DIRECTION_SNAP_AFTER_RESIZE;
import static com.android.wm.shell.pip.PipAnimationController.TRANSITION_DIRECTION_TO_PIP;
import static com.android.wm.shell.pip.PipAnimationController.TRANSITION_DIRECTION_USER_RESIZE;
import static com.android.wm.shell.pip.PipAnimationController.isInPipDirection;
import static com.android.wm.shell.pip.PipAnimationController.isOutPipDirection;
import static com.android.wm.shell.pip.PipAnimationController.isRemovePipDirection;
import static com.android.wm.shell.pip.PipTransitionController.ANIM_TYPE_ALPHA;
import static com.android.wm.shell.pip.PipTransitionController.ANIM_TYPE_BOUNDS;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;
import static com.android.wm.shell.transition.Transitions.TRANSIT_EXIT_PIP;
import static com.android.wm.shell.transition.Transitions.TRANSIT_EXIT_PIP_TO_SPLIT;
import static com.android.wm.shell.transition.Transitions.TRANSIT_REMOVE_PIP;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.PictureInPictureParams;
import android.app.TaskInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.SystemProperties;
import android.view.Choreographer;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;
import android.window.DesktopExperienceFlags;
import android.window.DisplayAreaInfo;
import android.window.TaskOrganizer;
import android.window.TaskSnapshot;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.ScreenshotUtils;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipMenuController;
import com.android.wm.shell.common.pip.PipPerfHintController;
import com.android.wm.shell.common.pip.PipUiEventLogger;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.desktopmode.DesktopUserRepositories;
import com.android.wm.shell.desktopmode.data.DesktopRepository;
import com.android.wm.shell.pip.phone.PipMotionHelper;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.animation.Interpolators;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.shared.pip.PipContentOverlay;
import com.android.wm.shell.shared.pip.PipFlags;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Manages PiP tasks such as resize and offset.
 *
 * This class listens on {@link TaskOrganizer} callbacks for windowing mode change
 * both to and from PiP and issues corresponding animation if applicable.
 * Normally, we apply series of {@link SurfaceControl.Transaction} when the animator is running
 * and files a final {@link WindowContainerTransaction} at the end of the transition.
 *
 * This class is also responsible for general resize/offset PiP operations within SysUI component,
 * see also {@link PipMotionHelper}.
 */
public class PipTaskOrganizer implements ShellTaskOrganizer.TaskListener,
        DisplayController.OnDisplaysChangedListener {
    private static final String TAG = PipTaskOrganizer.class.getSimpleName();

    /**
     * The fixed start delay in ms when fading out the content overlay from bounds animation.
     * This is to overcome the flicker caused by configuration change when rotating from landscape
     * to portrait PiP in button navigation mode.
     */
    private static final int CONTENT_OVERLAY_FADE_OUT_DELAY_MS = 500;

    private static final int EXTRA_CONTENT_OVERLAY_FADE_OUT_DELAY_MS =
            SystemProperties.getInt(
                    "persist.wm.debug.extra_content_overlay_fade_out_delay_ms", 400);

    private final Context mContext;
    private final SyncTransactionQueue mSyncTransactionQueue;
    private final PipBoundsState mPipBoundsState;
    private final PipDisplayLayoutState mPipDisplayLayoutState;
    private final PipBoundsAlgorithm mPipBoundsAlgorithm;
    private final @NonNull PipMenuController mPipMenuController;
    private final PipAnimationController mPipAnimationController;
    protected final PipTransitionController mPipTransitionController;
    protected final PipParamsChangedForwarder mPipParamsChangedForwarder;
    private final PipUiEventLogger mPipUiEventLoggerLogger;
    private final int mEnterAnimationDuration;
    private final int mExitAnimationDuration;
    private final int mCrossFadeAnimationDuration;
    private final PipSurfaceTransactionHelper mSurfaceTransactionHelper;
    private final Optional<SplitScreenController> mSplitScreenOptional;
    @Nullable private final PipPerfHintController mPipPerfHintController;
    private final Optional<DesktopUserRepositories> mDesktopUserRepositoriesOptional;
    private final RootTaskDisplayAreaOrganizer mRootTaskDisplayAreaOrganizer;
    private final DisplayController mDisplayController;
    protected final ShellTaskOrganizer mTaskOrganizer;
    protected final ShellExecutor mMainExecutor;

    // the runnable to execute after WindowContainerTransactions is applied to finish resizing pip
    private Runnable mPipFinishResizeWCTRunnable;

    private void maybePerformFinishResizeCallback() {
        if (mPipFinishResizeWCTRunnable != null) {
            mPipFinishResizeWCTRunnable.run();
            mPipFinishResizeWCTRunnable = null;
        }
    }

    // These callbacks are called on the update thread
    private final PipAnimationController.PipAnimationCallback mPipAnimationCallback =
            new PipAnimationController.PipAnimationCallback() {
                private boolean mIsCancelled;
                @Nullable private PipPerfHintController.PipHighPerfSession mPipHighPerfSession;

                private void onHighPerfSessionTimeout(
                        PipPerfHintController.PipHighPerfSession session) {}

                private void cleanUpHighPerfSessionMaybe() {
                    if (mPipHighPerfSession != null) {
                        // Close the high perf session once pointer interactions are over;
                        mPipHighPerfSession.close();
                        mPipHighPerfSession = null;
                    }
                }


                @Override
                public void onPipAnimationStart(TaskInfo taskInfo,
                        PipAnimationController.PipTransitionAnimator animator) {
                    if (mPipPerfHintController != null) {
                        // Start a high perf session with a timeout callback.
                        mPipHighPerfSession = mPipPerfHintController.startSession(
                                this::onHighPerfSessionTimeout,
                                "PipTaskOrganizer::mPipAnimationCallback");
                    }

                    final int direction = animator.getTransitionDirection();
                    mIsCancelled = false;
                    sendOnPipTransitionStarted(direction);
                }

                @Override
                public void onPipAnimationEnd(TaskInfo taskInfo, SurfaceControl.Transaction tx,
                        PipAnimationController.PipTransitionAnimator animator) {
                    // Close the high perf session if needed.
                    cleanUpHighPerfSessionMaybe();

                    final int direction = animator.getTransitionDirection();
                    if (mIsCancelled) {
                        sendOnPipTransitionFinished(direction);
                        maybePerformFinishResizeCallback();
                        return;
                    }
                    final int animationType = animator.getAnimationType();
                    final Rect destinationBounds = animator.getDestinationBounds();
                    if (isInPipDirection(direction) && mPipOverlay != null) {
                        fadeOutAndRemoveOverlay(mPipOverlay,
                                null /* callback */, true /* withStartDelay*/);
                    }
                    if (mWaitForFixedRotation && animationType == ANIM_TYPE_BOUNDS
                            && direction == TRANSITION_DIRECTION_TO_PIP) {
                        // The final task bounds will be applied by onFixedRotationFinished so
                        // that all coordinates are in new rotation.
                        mSurfaceTransactionHelper.round(tx, mLeash, isInPip());
                        return;
                    }
                    final boolean isExitPipDirection = isOutPipDirection(direction)
                            || isRemovePipDirection(direction);
                    if (mPipTransitionState.getTransitionState() != PipTransitionState.EXITING_PIP
                            || isExitPipDirection) {
                        // execute the finish resize callback if needed after the transaction is
                        // committed
                        tx.addTransactionCommittedListener(mMainExecutor,
                                PipTaskOrganizer.this::maybePerformFinishResizeCallback);

                        // Finish resize as long as we're not exiting PIP, or, if we are, only if
                        // this is the end of an exit PIP animation.
                        // This is necessary in case there was a resize animation ongoing when
                        // exit PIP started, in which case the first resize will be skipped to
                        // let the exit operation handle the final resize out of PIP mode.
                        // See b/185306679.
                        finishResizeDelayedIfNeeded(() -> {
                            finishResize(tx, destinationBounds, direction, animationType);
                            sendOnPipTransitionFinished(direction);
                        });
                    }
                }

                @Override
                public void onPipAnimationCancel(TaskInfo taskInfo,
                        PipAnimationController.PipTransitionAnimator animator) {
                    final int direction = animator.getTransitionDirection();
                    mIsCancelled = true;
                    if (isInPipDirection(direction) && mPipOverlay != null) {
                        fadeOutAndRemoveOverlay(mPipOverlay,
                                null /* callback */, true /* withStartDelay */);
                    }
                    sendOnPipTransitionCancelled(direction);
                }
            };

    /**
     * Finishes resizing the PiP, delaying the operation if it has to be synced with the PiP menu.
     *
     * This is done to avoid a race condition between the last transaction applied in
     * onPipAnimationUpdate and the finishResize in onPipAnimationEnd. The transaction in
     * onPipAnimationUpdate is applied directly from WmShell, while onPipAnimationEnd creates a
     * WindowContainerTransaction in finishResize, which is to be applied by WmCore later. Normally,
     * the WCT should be the last transaction to finish the animation. However, it  may happen that
     * it gets applied *before* the transaction created by the last onPipAnimationUpdate. This
     * happens only when the PiP surface transaction has to be synced with the PiP menu due to the
     * necessity for a delay when syncing the PiP surface animation with the PiP menu surface
     * animation and redrawing the PiP menu contents. As a result, the PiP surface gets scaled after
     * the new bounds are applied by WmCore, which makes the PiP surface have unexpected bounds.
     *
     * To avoid this, we delay the finishResize operation until
     * the next frame. This aligns the last onAnimationUpdate transaction with the WCT application.
     */
    private void finishResizeDelayedIfNeeded(Runnable finishResizeRunnable) {
        if (!shouldSyncPipTransactionWithMenu()) {
            finishResizeRunnable.run();
            return;
        }

        // Delay the finishResize to the next frame
        Choreographer.getInstance().postCallback(Choreographer.CALLBACK_COMMIT, () -> {
            mMainExecutor.execute(finishResizeRunnable);
        }, null);
    }

    protected boolean shouldSyncPipTransactionWithMenu() {
        return mPipMenuController.isMenuVisible();
    }

    @VisibleForTesting
    final PipTransitionController.PipTransitionCallback mPipTransitionCallback =
            new PipTransitionController.PipTransitionCallback() {
                @Override
                public void onPipTransitionStarted(int direction, Rect pipBounds) {}

                @Override
                public void onPipTransitionFinished(int direction) {
                    // Apply the deferred RunningTaskInfo if applicable after all proper callbacks
                    // are sent.
                    if (direction == TRANSITION_DIRECTION_TO_PIP && mDeferredTaskInfo != null) {
                        onTaskInfoChanged(mDeferredTaskInfo);
                        mDeferredTaskInfo = null;
                    }
                }

                @Override
                public void onPipTransitionCanceled(int direction) {}
            };

    private final PipAnimationController.PipTransactionHandler mPipTransactionHandler =
            new PipAnimationController.PipTransactionHandler() {
                @Override
                public boolean handlePipTransaction(SurfaceControl leash,
                        SurfaceControl.Transaction tx, Rect destinationBounds, float alpha) {
                    if (shouldSyncPipTransactionWithMenu()) {
                        mPipMenuController.movePipMenu(leash, tx, destinationBounds, alpha);
                        return true;
                    }
                    return false;
                }
            };

    private ActivityManager.RunningTaskInfo mTaskInfo;
    // To handle the edge case that onTaskInfoChanged callback is received during the entering
    // PiP transition, where we do not want to intercept the transition but still want to apply the
    // changed RunningTaskInfo when it finishes.
    private ActivityManager.RunningTaskInfo mDeferredTaskInfo;
    private WindowContainerToken mToken;
    protected SurfaceControl mLeash;
    protected PipTransitionState mPipTransitionState;
    protected PipSurfaceTransactionHelper.SurfaceControlTransactionFactory
            mSurfaceControlTransactionFactory;
    protected PictureInPictureParams mPictureInPictureParams;
    private IntConsumer mOnDisplayIdChangeCallback;

    /**
     * If set to {@code true}, the entering animation will be skipped and we will wait for
     * {@link #onFixedRotationFinished(int)} callback to actually enter PiP.
     */
    private boolean mWaitForFixedRotation;

    /**
     * The rotation that the display will apply after expanding PiP to fullscreen. This is only
     * meaningful if {@link #mWaitForFixedRotation} is true.
     */
    private @Surface.Rotation int mNextRotation;

    private @Surface.Rotation int mCurrentRotation;

    /**
     * An optional overlay used to mask content changing between an app in/out of PiP.
     */
    @Nullable
    SurfaceControl mPipOverlay;

    /**
     * The app bounds used for the buffer size of the {@link PipContentOverlay.PipAppIconOverlay}.
     *
     * Note that this is empty if the overlay is removed or if it's some other type of overlay
     * defined in {@link PipContentOverlay}.
     */
    @NonNull
    final Rect mAppBounds = new Rect();

    /** The source rect hint from stopSwipePipToHome(). */
    @Nullable
    private Rect mSwipeSourceRectHint;

    public PipTaskOrganizer(Context context,
            @NonNull ShellInit shellInit,
            @NonNull SyncTransactionQueue syncTransactionQueue,
            @NonNull PipTransitionState pipTransitionState,
            @NonNull PipBoundsState pipBoundsState,
            @NonNull PipDisplayLayoutState pipDisplayLayoutState,
            @NonNull PipBoundsAlgorithm boundsHandler,
            @NonNull PipMenuController pipMenuController,
            @NonNull PipAnimationController pipAnimationController,
            @NonNull PipSurfaceTransactionHelper surfaceTransactionHelper,
            @NonNull PipTransitionController pipTransitionController,
            @NonNull PipParamsChangedForwarder pipParamsChangedForwarder,
            Optional<SplitScreenController> splitScreenOptional,
            Optional<PipPerfHintController> pipPerfHintControllerOptional,
            Optional<DesktopUserRepositories> desktopUserRepositoriesOptional,
            RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            @NonNull DisplayController displayController,
            @NonNull PipUiEventLogger pipUiEventLogger,
            @NonNull ShellTaskOrganizer shellTaskOrganizer,
            @ShellMainThread ShellExecutor mainExecutor) {
        mContext = context;
        mSyncTransactionQueue = syncTransactionQueue;
        mPipTransitionState = pipTransitionState;
        mPipBoundsState = pipBoundsState;
        mPipDisplayLayoutState = pipDisplayLayoutState;
        mPipBoundsAlgorithm = boundsHandler;
        mPipMenuController = pipMenuController;
        mPipTransitionController = pipTransitionController;
        mPipParamsChangedForwarder = pipParamsChangedForwarder;
        mEnterAnimationDuration = context.getResources()
                .getInteger(R.integer.config_pipEnterAnimationDuration);
        mExitAnimationDuration = context.getResources()
                .getInteger(R.integer.config_pipExitAnimationDuration);
        mCrossFadeAnimationDuration = context.getResources()
                .getInteger(R.integer.config_pipCrossfadeAnimationDuration);
        mSurfaceTransactionHelper = surfaceTransactionHelper;
        mPipAnimationController = pipAnimationController;
        mPipUiEventLoggerLogger = pipUiEventLogger;
        mSurfaceControlTransactionFactory =
                new PipSurfaceTransactionHelper.VsyncSurfaceControlTransactionFactory();
        mSplitScreenOptional = splitScreenOptional;
        mPipPerfHintController = pipPerfHintControllerOptional.orElse(null);
        mDesktopUserRepositoriesOptional = desktopUserRepositoriesOptional;
        mRootTaskDisplayAreaOrganizer = rootTaskDisplayAreaOrganizer;
        mDisplayController = displayController;
        mTaskOrganizer = shellTaskOrganizer;
        mMainExecutor = mainExecutor;

        if (!PipFlags.isPip2ExperimentEnabled()) {
            shellInit.addInitCallback(this::onInit, this);
        }
    }

    @VisibleForTesting
    void onInit() {
        // TODO: Can be removed once wm components are created on the shell-main thread
        mMainExecutor.execute(() -> mTaskOrganizer.addListenerForType(
                this, TASK_LISTENER_TYPE_PIP));
        mPipTransitionController.setPipOrganizer(this);
        mDisplayController.addDisplayWindowListener(this);
        mPipTransitionController.registerPipTransitionCallback(
                mPipTransitionCallback, mMainExecutor);
    }

    public PipTransitionController getTransitionController() {
        return mPipTransitionController;
    }

    PipAnimationController.PipTransactionHandler getPipTransactionHandler() {
        return mPipTransactionHandler;
    }

    public Rect getCurrentOrAnimatingBounds() {
        PipAnimationController.PipTransitionAnimator animator =
                mPipAnimationController.getCurrentAnimator();
        if (animator != null && animator.isRunning()) {
            return new Rect(animator.getDestinationBounds());
        }
        return mPipBoundsState.getBounds();
    }

    public boolean isInPip() {
        return mPipTransitionState.isInPip();
    }

    private boolean isLaunchIntoPipTask() {
        return mPictureInPictureParams != null && mPictureInPictureParams.isLaunchIntoPip();
    }

    /**
     * Returns whether the entry animation is waiting to be started.
     */
    public boolean isEntryScheduled() {
        return mPipTransitionState.getTransitionState() == PipTransitionState.ENTRY_SCHEDULED;
    }

    /**
     * Registers a callback when a display change has been detected when we enter PiP.
     */
    public void registerOnDisplayIdChangeCallback(IntConsumer onDisplayIdChangeCallback) {
        mOnDisplayIdChangeCallback = onDisplayIdChangeCallback;
    }

    /**
     * Override if the PiP should always use a fade-in animation during PiP entry.
     *
     * @return true if the mOneShotAnimationType should always be
     * {@link PipAnimationController#ANIM_TYPE_ALPHA}.
     */
    protected boolean shouldAlwaysFadeIn() {
        return false;
    }

    /**
     * Whether the menu should get attached as early as possible when entering PiP.
     *
     * @return whether the menu should be attached before
     * {@link PipBoundsAlgorithm#getEntryDestinationBounds()} is called.
     */
    protected boolean shouldAttachMenuEarly() {
        return false;
    }

    /**
     * Callback when Launcher starts swipe-pip-to-home operation.
     * @return {@link Rect} for destination bounds.
     */
    public Rect startSwipePipToHome(ComponentName componentName, ActivityInfo activityInfo,
            PictureInPictureParams pictureInPictureParams) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "startSwipePipToHome: %s, state=%s", componentName, mPipTransitionState);
        mPipTransitionState.setInSwipePipToHomeTransition(true);
        mPipTransitionController.onStartSwipePipToHome();
        setBoundsStateForEntry(componentName, pictureInPictureParams, activityInfo);
        return mPipBoundsAlgorithm.getEntryDestinationBounds();
    }

    /**
     * Callback when launcher finishes preparation of swipe-pip-to-home operation.
     * Expect {@link #onTaskAppeared(ActivityManager.RunningTaskInfo, SurfaceControl)} afterwards.
     */
    public void stopSwipePipToHome(int taskId, ComponentName componentName, Rect destinationBounds,
            SurfaceControl overlay, Rect appBounds, Rect sourceRectHint) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "stopSwipePipToHome: %s, stat=%s", componentName, mPipTransitionState);
        // do nothing if there is no startSwipePipToHome being called before
        if (!mPipTransitionState.getInSwipePipToHomeTransition()) {
            return;
        }
        mPipBoundsState.setBounds(destinationBounds);
        setContentOverlay(overlay, appBounds);
        mSwipeSourceRectHint = sourceRectHint;
        if (overlay != null) {
            // With Shell transition, the overlay was attached to the remote transition leash, which
            // will be removed when the current transition is finished, so we need to reparent it
            // to the actual Task surface now.
            // PipTransition is responsible to fade it out and cleanup when finishing the enter PIP
            // transition.
            final SurfaceControl.Transaction t = mSurfaceControlTransactionFactory.getTransaction();
            mTaskOrganizer.reparentChildSurfaceToTask(taskId, overlay, t);
            t.setLayer(overlay, Integer.MAX_VALUE);
            t.apply();
            // This serves as a last resort in case the Shell Transition is not handled properly.
            // We want to make sure the overlay passed from Launcher gets removed eventually.
            mayRemoveContentOverlay(overlay);
        }
    }

    /**
     * Returns non-null Rect if the pip is entering from swipe-to-home with a specified source hint.
     * This also consumes the rect hint.
     */
    @Nullable
    Rect takeSwipeSourceRectHint() {
        final Rect sourceRectHint = mSwipeSourceRectHint;
        if (sourceRectHint == null || sourceRectHint.isEmpty()) {
            return null;
        }
        mSwipeSourceRectHint = null;
        return mPipTransitionState.getInSwipePipToHomeTransition() ? sourceRectHint : null;
    }

    private void mayRemoveContentOverlay(SurfaceControl overlay) {
        final WeakReference<SurfaceControl> overlayRef = new WeakReference<>(overlay);
        final long timeoutDuration = (mEnterAnimationDuration
                + CONTENT_OVERLAY_FADE_OUT_DELAY_MS
                + EXTRA_CONTENT_OVERLAY_FADE_OUT_DELAY_MS) * 2L;
        mMainExecutor.executeDelayed(() -> {
            final SurfaceControl overlayLeash = overlayRef.get();
            if (overlayLeash != null && overlayLeash.isValid() && overlayLeash == mPipOverlay) {
                ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "Cleanup the overlay(%s) as a last resort.", overlayLeash);
                removeContentOverlay(overlayLeash, null /* callback */);
            }
        }, timeoutDuration);
    }

    /**
     * Callback when launcher aborts swipe-pip-to-home operation.
     */
    public void abortSwipePipToHome(int taskId, ComponentName componentName) {
        if (!mPipTransitionState.getInSwipePipToHomeTransition()) {
            return;
        }
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "Abort swipe-pip-to-home for %s", componentName);
        sendOnPipTransitionCancelled(TRANSITION_DIRECTION_TO_PIP);
        // Cleanup internal states
        mPipTransitionState.setInSwipePipToHomeTransition(false);
        mPictureInPictureParams = null;
        mPipTransitionState.setTransitionState(PipTransitionState.UNDEFINED);
    }

    public ActivityManager.RunningTaskInfo getTaskInfo() {
        return mTaskInfo;
    }

    public SurfaceControl getSurfaceControl() {
        return mLeash;
    }

    private void setBoundsStateForEntry(ComponentName componentName,
            PictureInPictureParams params, ActivityInfo activityInfo) {
        mPipBoundsState.setBoundsStateForEntry(componentName, activityInfo, params,
                mPipBoundsAlgorithm);
    }

    /**
     * Expands PiP to the previous bounds, this is done in two phases using
     * {@link WindowContainerTransaction}
     * - setActivityWindowingMode to either fullscreen or split-secondary at beginning of the
     *   transaction. without changing the windowing mode of the Task itself. This makes sure the
     *   activity render it's final configuration while the Task is still in PiP.
     * - setWindowingMode to undefined at the end of transition
     * @param animationDurationMs duration in millisecond for the exiting PiP transition
     * @param requestEnterSplit whether the enterSplit button is pressed on PiP or not.
     *                             Indicate the user wishes to directly put PiP into split screen
     *                             mode.
     */
    public void exitPip(int animationDurationMs, boolean requestEnterSplit) {
        if (!mPipTransitionState.isInPip()
                || mPipTransitionState.getTransitionState() == PipTransitionState.EXITING_PIP
                || mPipTransitionState.getInSwipePipToHomeTransition()
                || mToken == null) {
            ProtoLog.wtf(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Not allowed to exitPip in current state"
                            + " mState=%d mToken=%s", TAG, mPipTransitionState.getTransitionState(),
                    mToken);
            return;
        }

        if (mPipTransitionState.isEnteringPip()
                && !mPipTransitionState.getInSwipePipToHomeTransition()) {
            // If we are still entering PiP with Shell playing enter animation, jump-cut to
            // the end of the enter animation and reschedule exitPip to run after enter-PiP
            // has finished its transition and allowed the client to draw in PiP mode.
            mPipTransitionController.end(() -> {
                // TODO(341627042): force set to entered state to avoid potential stack overflow.
                mPipTransitionState.setTransitionState(PipTransitionState.ENTERED_PIP);
                exitPip(animationDurationMs, requestEnterSplit);
            });
            return;
        }

        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "exitPip: %s, state=%s", mTaskInfo.topActivity, mPipTransitionState);
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        if (isLaunchIntoPipTask()) {
            exitLaunchIntoPipTask(wct);
            return;
        }

        // bail early if leash is null
        if (mLeash == null) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "exitPip: leash is null");
            return;
        }

        final Rect destinationBounds = new Rect(getExitDestinationBounds());
        final int direction = syncWithSplitScreenBounds(destinationBounds, requestEnterSplit)
                ? TRANSITION_DIRECTION_LEAVE_PIP_TO_SPLIT_SCREEN
                : TRANSITION_DIRECTION_LEAVE_PIP;
        // For exiting to fullscreen, the windowing mode of task will be changed to fullscreen
        // until the animation is finished. Otherwise if the activity is resumed and focused at the
        // begin of aniamtion, the app may do something too early to distub the animation.

        if (Transitions.SHELL_TRANSITIONS_ROTATION) {
            // When exit to fullscreen with Shell transition enabled, we update the Task windowing
            // mode directly so that it can also trigger display rotation and visibility update in
            // the same transition if there will be any.
            wct.setWindowingMode(mToken, getOutPipWindowingMode());
            // We can inherit the parent bounds as it is going to be fullscreen. The
            // destinationBounds calculated above will be incorrect if this is with rotation.
            wct.setBounds(mToken, null);
        } else {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "exitPip: %s, dest=%s", mTaskInfo.topActivity, destinationBounds);
            final SurfaceControl.Transaction tx =
                    mSurfaceControlTransactionFactory.getTransaction();
            mSurfaceTransactionHelper.scale(tx, mLeash, destinationBounds,
                    mPipBoundsState.getBounds());
            tx.setWindowCrop(mLeash, destinationBounds.width(), destinationBounds.height());
            // We set to fullscreen here for now, but later it will be set to UNDEFINED for
            // the proper windowing mode to take place. See #applyWindowingModeChangeOnExit.
            wct.setActivityWindowingMode(mToken, WINDOWING_MODE_FULLSCREEN);
            wct.setBounds(mToken, destinationBounds);
            wct.setBoundsChangeTransaction(mToken, tx);
        }

        // Cancel the existing animator if there is any.
        // TODO(b/232439933): this is disabled temporarily to unblock b/234502692.
        // cancelCurrentAnimator();

        // Set the exiting state first so if there is fixed rotation later, the running animation
        // won't be interrupted by alpha animation for existing PiP.
        mPipTransitionState.setTransitionState(PipTransitionState.EXITING_PIP);

        if (requestEnterSplit && mSplitScreenOptional.isPresent()) {
            wct.setWindowingMode(mToken, WINDOWING_MODE_UNDEFINED);
            mSplitScreenOptional.get().onPipExpandToSplit(wct, mTaskInfo);
            mPipTransitionController.startExitTransition(
                    TRANSIT_EXIT_PIP_TO_SPLIT, wct, destinationBounds);
            return;
        }

        if (mSplitScreenOptional.isPresent()) {
            // If pip activity will reparent to origin task case and if the origin task still under
            // split root, apply exit split transaction to make it expand to fullscreen.
            SplitScreenController split = mSplitScreenOptional.get();
            if (split.isTaskInSplitScreen(mTaskInfo.lastParentTaskIdBeforePip)) {
                split.prepareExitSplitScreen(wct, split.getStageOfTask(
                        mTaskInfo.lastParentTaskIdBeforePip),
                        SplitScreenController.EXIT_REASON_APP_FINISHED);
            }
        }
        mPipTransitionController.startExitTransition(TRANSIT_EXIT_PIP, wct, destinationBounds);
    }

    /** Returns the bounds to restore to when exiting PIP mode. */
    // TODO(b/377581840): Instead of manually tracking bounds, use bounds from Core.
    public Rect getExitDestinationBounds() {
        if (isPipExitingToDesktopMode()) {
            // If we are exiting PiP while device is in Desktop mode:
            // 1) If PiP was entered via Desktop minimize (e.g. via minimize button), restore to the
            //    previous freeform bounds that is saved in DesktopRepository.
            // 2) If PiP was entered through other means (e.g. user swipe up), exit to initial
            //    freeform bounds. Note that this case has a flicker at the moment (b/379984108).
            Rect freeformBounds = getCurrentRepo().removeBoundsBeforeMinimize(
                    mTaskInfo.taskId);
            return freeformBounds != null
                    ? freeformBounds
                    : calculateInitialBounds(
                            mDisplayController.getDisplayLayout(mTaskInfo.displayId),
                            mTaskInfo,
                            DESKTOP_MODE_INITIAL_BOUNDS_SCALE);
        }
        return mPipBoundsState.getDisplayBounds();
    }

    /** Returns whether PiP is exiting while we're in desktop mode. */
    // TODO(b/377581840): Update this check to include non-minimized cases, e.g. split to PiP etc.
    private boolean isPipExitingToDesktopMode() {
        DesktopRepository currentRepo = getCurrentRepo();
        return DesktopExperienceFlags.ENABLE_DESKTOP_WINDOWING_PIP.isTrue() && currentRepo != null
                && (currentRepo.isAnyDeskActive(mTaskInfo.displayId)
                    || isDisplayInFreeform());
    }

    private DesktopRepository getCurrentRepo() {
        return mDesktopUserRepositoriesOptional.map(DesktopUserRepositories::getCurrent).orElse(
                null);
    }

    private void exitLaunchIntoPipTask(WindowContainerTransaction wct) {
        wct.startTask(mTaskInfo.launchIntoPipHostTaskId, null /* ActivityOptions */);
        mTaskOrganizer.applyTransaction(wct);

        // Remove the PiP with fade-out animation right after the host Task is brought to front.
        removePip();
    }

    void applyWindowingModeChangeOnExit(WindowContainerTransaction wct, int direction) {
        // Reset the final windowing mode.
        wct.setWindowingMode(mToken, getOutPipWindowingMode());
        // Simply reset the activity mode set prior to the animation running.
        wct.setActivityWindowingMode(mToken, WINDOWING_MODE_UNDEFINED);
    }

    /**
     * Removes PiP immediately.
     */
    public void removePip() {
        if (!mPipTransitionState.isInPip() || mToken == null || mLeash == null) {
            ProtoLog.wtf(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Not allowed to removePip in current state"
                            + " mState=%d mToken=%s mLeash=%s", TAG,
                    mPipTransitionState.getTransitionState(), mToken, mLeash);
            return;
        }

        // removePipImmediately is expected when the following animation finishes.
        ValueAnimator animator = mPipAnimationController
                .getAnimator(mTaskInfo, mLeash, mPipBoundsState.getBounds(),
                        1f /* alphaStart */, 0f /* alphaEnd */)
                .setTransitionDirection(TRANSITION_DIRECTION_REMOVE_STACK)
                .setPipTransactionHandler(mPipTransactionHandler)
                .setPipAnimationCallback(mPipAnimationCallback);
        animator.setDuration(mExitAnimationDuration);
        animator.setInterpolator(Interpolators.ALPHA_OUT);
        animator.start();
        mPipTransitionState.setTransitionState(PipTransitionState.EXITING_PIP);
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "removePip: %s, state=%s", mTaskInfo.topActivity, mPipTransitionState);
    }

    private void removePipImmediately() {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "removePipImmediately: %s, state=%s", mTaskInfo.topActivity, mPipTransitionState);
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setBounds(mToken, null);
        wct.setWindowingMode(mToken, getOutPipWindowingMode());
        wct.reorder(mToken, false);
        mPipTransitionController.startExitTransition(TRANSIT_REMOVE_PIP, wct,
                null /* destinationBounds */);
    }

    @Override
    public void onTaskAppeared(ActivityManager.RunningTaskInfo info, SurfaceControl leash) {
        Objects.requireNonNull(info, "Requires RunningTaskInfo");
        mTaskInfo = info;
        mToken = mTaskInfo.token;
        mPipTransitionState.setTransitionState(PipTransitionState.TASK_APPEARED);
        mLeash = leash;
        mPictureInPictureParams = mTaskInfo.pictureInPictureParams;
        setBoundsStateForEntry(mTaskInfo.topActivity, mPictureInPictureParams,
                mTaskInfo.topActivityInfo);
        if (mPictureInPictureParams != null) {
            mPipParamsChangedForwarder.notifyActionsChanged(mPictureInPictureParams.getActions(),
                    mPictureInPictureParams.getCloseAction());
            mPipParamsChangedForwarder.notifyTitleChanged(
                    mPictureInPictureParams.getTitle());
            mPipParamsChangedForwarder.notifySubtitleChanged(
                    mPictureInPictureParams.getSubtitle());
            logRemoteActions(mPictureInPictureParams);
        }

        mPipUiEventLoggerLogger.setTaskInfo(mTaskInfo);

        // If the displayId of the task is different than what PipBoundsHandler has, then update
        // it. This is possible if we entered PiP on an external display.
        if (info.displayId != mPipDisplayLayoutState.getDisplayId()
                && mOnDisplayIdChangeCallback != null) {
            mOnDisplayIdChangeCallback.accept(info.displayId);
        }

        // UiEvent logging.
        final PipUiEventLogger.PipUiEventEnum uiEventEnum;
        if (isLaunchIntoPipTask()) {
            uiEventEnum = PipUiEventLogger.PipUiEventEnum.PICTURE_IN_PICTURE_ENTER_CONTENT_PIP;
        } else if (mPipTransitionState.getInSwipePipToHomeTransition()) {
            uiEventEnum = PipUiEventLogger.PipUiEventEnum.PICTURE_IN_PICTURE_AUTO_ENTER;
        } else {
            uiEventEnum = PipUiEventLogger.PipUiEventEnum.PICTURE_IN_PICTURE_ENTER;
        }
        mPipUiEventLoggerLogger.log(uiEventEnum);

        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "onTaskAppeared: %s, state=%s, taskId=%s", mTaskInfo.topActivity,
                mPipTransitionState, mTaskInfo.taskId);
        if (mPipTransitionState.getInSwipePipToHomeTransition()) {
            if (mWaitForFixedRotation) {
                ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: Defer onTaskAppeared-SwipePipToHome until end of fixed rotation.",
                        TAG);
            }
            return;
        }

        final int animationType = shouldAlwaysFadeIn()
                ? ANIM_TYPE_ALPHA
                : mPipAnimationController.takeOneShotEnterAnimationType();
        mPipTransitionController.setEnterAnimationType(animationType);
    }

    /**
     * Called when the display rotation handling is skipped (e.g. when rotation happens while in
     * the middle of an entry transition).
     */
    public void onDisplayRotationSkipped() {
        if (isEntryScheduled()) {
            // The PIP animation is scheduled to start with the previous orientation's bounds,
            // re-calculate the entry bounds and restart the alpha animation.
            final Rect destinationBounds = mPipBoundsAlgorithm.getEntryDestinationBounds();
            enterPipWithAlphaAnimation(destinationBounds, mEnterAnimationDuration);
        }
    }

    @VisibleForTesting
    void enterPipWithAlphaAnimation(Rect destinationBounds, long durationMs) {
        // If we are fading the PIP in, then we should move the pip to the final location as
        // soon as possible, but set the alpha immediately since the transaction can take a
        // while to process
        final SurfaceControl.Transaction tx =
                mSurfaceControlTransactionFactory.getTransaction();
        tx.setAlpha(mLeash, 0f);
        tx.apply();

        // When entering PiP this transaction will be applied within WindowContainerTransaction and
        // ensure that the PiP has rounded corners.
        final SurfaceControl.Transaction boundsChangeTx =
                mSurfaceControlTransactionFactory.getTransaction();
        mSurfaceTransactionHelper
                .cropAndPosition(boundsChangeTx, mLeash, destinationBounds)
                .round(boundsChangeTx, mLeash, true /* applyCornerRadius */);

        mPipTransitionState.setTransitionState(PipTransitionState.ENTRY_SCHEDULED);
        applyEnterPipSyncTransaction(destinationBounds, () -> {
            mPipAnimationController
                    .getAnimator(mTaskInfo, mLeash, destinationBounds, 0f, 1f)
                    .setTransitionDirection(TRANSITION_DIRECTION_TO_PIP)
                    .setPipAnimationCallback(mPipAnimationCallback)
                    .setPipTransactionHandler(mPipTransactionHandler)
                    .setDuration(durationMs)
                    .start();
            // mState is set right after the animation is kicked off to block any resize
            // requests such as offsetPip that may have been called prior to the transition.
            mPipTransitionState.setTransitionState(PipTransitionState.ENTERING_PIP);
        }, boundsChangeTx);
    }

    private void applyEnterPipSyncTransaction(Rect destinationBounds, Runnable runnable,
            @Nullable SurfaceControl.Transaction boundsChangeTransaction) {
        // PiP menu is attached late in the process here to avoid any artifacts on the leash
        // caused by addShellRoot when in gesture navigation mode.
        if (!shouldAttachMenuEarly()) {
            mPipMenuController.attach(mLeash);
        }
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setActivityWindowingMode(mToken, WINDOWING_MODE_UNDEFINED);
        wct.setBounds(mToken, destinationBounds);
        if (boundsChangeTransaction != null) {
            wct.setBoundsChangeTransaction(mToken, boundsChangeTransaction);
        }
        mSyncTransactionQueue.queue(wct);
        if (runnable != null) {
            mSyncTransactionQueue.runInSync(t -> runnable.run());
        }
    }

    private void sendOnPipTransitionStarted(
            @PipAnimationController.TransitionDirection int direction) {
        if (direction == TRANSITION_DIRECTION_TO_PIP) {
            mPipTransitionState.setTransitionState(PipTransitionState.ENTERING_PIP);
        }
        mPipTransitionController.sendOnPipTransitionStarted(direction);
    }

    @VisibleForTesting
    void sendOnPipTransitionFinished(
            @PipAnimationController.TransitionDirection int direction) {
        if (direction == TRANSITION_DIRECTION_TO_PIP) {
            mPipTransitionState.setTransitionState(PipTransitionState.ENTERED_PIP);
        }
        mPipTransitionController.sendOnPipTransitionFinished(direction);
    }

    private void sendOnPipTransitionCancelled(
            @PipAnimationController.TransitionDirection int direction) {
        mPipTransitionController.sendOnPipTransitionCancelled(direction);
    }

    /**
     * Note that dismissing PiP is now originated from SystemUI, see {@link #exitPip(int, boolean)}.
     * Meanwhile this callback is invoked whenever the task is removed. For instance:
     *   - as a result of removeRootTasksInWindowingModes from WM
     *   - activity itself is died
     * Nevertheless, we simply update the internal state here as all the heavy lifting should
     * have been done in WM.
     */
    @Override
    public void onTaskVanished(ActivityManager.RunningTaskInfo info) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "onTaskVanished: %s, state=%s", mTaskInfo.topActivity, mPipTransitionState);
        if (mPipTransitionState.getTransitionState() == PipTransitionState.UNDEFINED) {
            return;
        }
        if (mPipTransitionState.getTransitionState() == PipTransitionState.EXITING_PIP) {
            // With Shell transition, we do the cleanup in PipTransition after exiting PIP.
            return;
        }
        final WindowContainerToken token = info.token;
        Objects.requireNonNull(token, "Requires valid WindowContainerToken");
        if (token.asBinder() != mToken.asBinder()) {
            ProtoLog.wtf(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Unrecognized token: %s", TAG, token);
            return;
        }

        cancelAnimationOnTaskVanished();
        onExitPipFinished(info);

        mPipTransitionController.forceFinishTransition();
    }

    protected void cancelAnimationOnTaskVanished() {
        cancelCurrentAnimator();
    }

    @Override
    public void onTaskInfoChanged(ActivityManager.RunningTaskInfo info) {
        Objects.requireNonNull(mToken, "onTaskInfoChanged requires valid existing mToken");
        if (mPipTransitionState.getTransitionState() != PipTransitionState.ENTERED_PIP
                && mPipTransitionState.getTransitionState() != PipTransitionState.EXITING_PIP) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Defer onTaskInfoChange in current state: %d", TAG,
                    mPipTransitionState.getTransitionState());
            // Defer applying PiP parameters if the task is entering PiP to avoid disturbing
            // the animation.
            mDeferredTaskInfo = info;
            return;
        }
        mPipBoundsState.setLastPipComponentName(info.topActivity);
        mPipBoundsState.setOverrideMinSize(
                mPipBoundsAlgorithm.getMinimalSize(info.topActivityInfo));
        final PictureInPictureParams newParams = info.pictureInPictureParams;
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "onTaskInfoChanged: %s, state=%s oldParams=%s newParams=%s",
                mTaskInfo.topActivity, mPipTransitionState, mPictureInPictureParams, newParams);

        // mPictureInPictureParams is only null if there is no PiP
        if (newParams == null || mPictureInPictureParams == null) {
            return;
        }
        applyNewPictureInPictureParams(newParams);
        mPictureInPictureParams = newParams;
        logRemoteActions(mPictureInPictureParams);
    }

    @Override
    public boolean supportCompatUI() {
        // PIP doesn't support compat.
        return false;
    }

    @Override
    public void attachChildSurfaceToTask(int taskId, SurfaceControl.Builder b) {
        b.setParent(findTaskSurface(taskId));
    }

    @Override
    public void reparentChildSurfaceToTask(int taskId, SurfaceControl sc,
            SurfaceControl.Transaction t) {
        t.reparent(sc, findTaskSurface(taskId));
    }

    private SurfaceControl findTaskSurface(int taskId) {
        if (mTaskInfo == null || mLeash == null || mTaskInfo.taskId != taskId) {
            throw new IllegalArgumentException("There is no surface for taskId=" + taskId);
        }
        return mLeash;
    }

    @Override
    public void onFixedRotationStarted(int displayId, int newRotation) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "onFixedRotationStarted: %s, state=%s", mTaskInfo, mPipTransitionState);
        mNextRotation = newRotation;
        mWaitForFixedRotation = true;

        // The fixed rotation will also be included in the transition info. However, if it is
        // not a PIP transition (such as open another app to different orientation),
        // PIP transition handler may not be aware of the fixed rotation start.
        // Notify the PIP transition handler so that it can fade out the PIP window early for
        // fixed transition of other windows.
        mPipTransitionController.onFixedRotationStarted();
    }

    @Override
    public void onFixedRotationFinished(int displayId) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "onFixedRotationFinished: %s, state=%s", mTaskInfo, mPipTransitionState);
        if (!mWaitForFixedRotation) {
            return;
        }
        mPipTransitionController.onFixedRotationFinished();
        clearWaitForFixedRotation();
    }

    /** Called when exiting PIP transition is finished to do the state cleanup. */
    public void onExitPipFinished(TaskInfo info) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "onExitPipFinished: %s, state=%s leash=%s",
                info.topActivity, mPipTransitionState, mLeash);
        if (mLeash == null) {
            // TODO(239461594): Remove once the double call to onExitPipFinished() is fixed
            ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "Warning, onExitPipFinished() called multiple times in the same session");
            return;
        }

        clearWaitForFixedRotation();
        if (mPipOverlay != null) {
            removeContentOverlay(mPipOverlay, null /* callback */);
            mPipOverlay = null;
        }
        resetShadowRadius();
        mPipTransitionState.setInSwipePipToHomeTransition(false);
        mPictureInPictureParams = null;
        mPipTransitionState.setTransitionState(PipTransitionState.UNDEFINED);
        // Re-set the PIP bounds to none.
        mPipBoundsState.setBounds(new Rect());
        mPipUiEventLoggerLogger.setTaskInfo(null);
        mPipMenuController.detach();
        mLeash = null;

        if (info.displayId != Display.DEFAULT_DISPLAY && mOnDisplayIdChangeCallback != null) {
            mOnDisplayIdChangeCallback.accept(Display.DEFAULT_DISPLAY);
        }
    }

    private void clearWaitForFixedRotation() {
        mWaitForFixedRotation = false;
    }

    /** Explicitly set the visibility of PiP window. */
    public void setPipVisibility(boolean visible) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "setPipVisibility: %s, state=%s visible=%s",
                (mTaskInfo != null ? mTaskInfo.topActivity : null), mPipTransitionState, visible);
        if (!isInPip()) {
            return;
        }
        if (mLeash == null || !mLeash.isValid()) {
            ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Invalid leash on setPipVisibility: %s", TAG, mLeash);
            return;
        }
        final SurfaceControl.Transaction tx =
                mSurfaceControlTransactionFactory.getTransaction();
        mSurfaceTransactionHelper.alpha(tx, mLeash, visible ? 1f : 0f);
        tx.apply();
    }

    @Override
    public void onDisplayConfigurationChanged(int displayId, Configuration newConfig) {
        mCurrentRotation = newConfig.windowConfiguration.getRotation();
    }

    /**
     * Called when display size or font size of settings changed
     */
    public void onDensityOrFontScaleChanged(Context context) {
        mSurfaceTransactionHelper.onDensityOrFontScaleChanged(context);
    }

    /**
     * TODO(b/152809058): consolidate the display info handling logic in SysUI
     *
     * @param destinationBoundsOut the current destination bounds will be populated to this param
     */
    @SuppressWarnings("unchecked")
    public void onMovementBoundsChanged(Rect destinationBoundsOut, boolean fromRotation,
            boolean fromImeAdjustment, boolean fromShelfAdjustment,
            WindowContainerTransaction wct) {
        // note that this can be called when swipe-to-home or fixed-rotation is happening.
        // Skip this entirely if that's the case.
        final boolean waitForFixedRotationOnEnteringPip = mWaitForFixedRotation
                && (mPipTransitionState.getTransitionState() != PipTransitionState.ENTERED_PIP);
        if ((mPipTransitionState.getInSwipePipToHomeTransition()
                || waitForFixedRotationOnEnteringPip) && fromRotation) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Skip onMovementBoundsChanged on rotation change"
                            + " InSwipePipToHomeTransition=%b"
                            + " mWaitForFixedRotation=%b"
                            + " getTransitionState=%d", TAG,
                    mPipTransitionState.getInSwipePipToHomeTransition(), mWaitForFixedRotation,
                    mPipTransitionState.getTransitionState());
            return;
        }
        final PipAnimationController.PipTransitionAnimator animator =
                mPipAnimationController.getCurrentAnimator();
        if (animator == null || !animator.isRunning()
                || animator.getTransitionDirection() != TRANSITION_DIRECTION_TO_PIP) {
            final boolean rotatingPip = mPipTransitionState.isInPip() && fromRotation;
            if (rotatingPip) {
                // The animation and surface update will be handled by the shell transition handler.
                mPipBoundsState.setBounds(destinationBoundsOut);
            } else if (rotatingPip) {
                // Update bounds state to final destination first. It's important to do this
                // before finishing & cancelling the transition animation so that the MotionHelper
                // bounds are synchronized to the destination bounds when the animation ends.
                mPipBoundsState.setBounds(destinationBoundsOut);
                // If we are rotating while there is a current animation, immediately cancel the
                // animation (remove the listeners so we don't trigger the normal finish resize
                // call that should only happen on the update thread)
                int direction = TRANSITION_DIRECTION_NONE;
                if (animator != null) {
                    direction = animator.getTransitionDirection();
                    PipAnimationController.quietCancel(animator);
                    // Do notify the listeners that this was canceled
                    sendOnPipTransitionCancelled(direction);
                    sendOnPipTransitionFinished(direction);
                }

                // Create a reset surface transaction for the new bounds and update the window
                // container transaction
                final SurfaceControl.Transaction tx = createFinishResizeSurfaceTransaction(
                        destinationBoundsOut);
                prepareFinishResizeTransaction(destinationBoundsOut, direction, tx, wct);
            } else  {
                // There could be an animation on-going. If there is one on-going, last-reported
                // bounds isn't yet updated. We'll use the animator's bounds instead.
                if (animator != null && animator.isRunning()) {
                    if (!animator.getDestinationBounds().isEmpty()) {
                        destinationBoundsOut.set(animator.getDestinationBounds());
                    }
                } else {
                    if (!mPipBoundsState.getBounds().isEmpty()) {
                        destinationBoundsOut.set(mPipBoundsState.getBounds());
                    }
                }
            }
            return;
        }

        final Rect currentDestinationBounds = animator.getDestinationBounds();
        destinationBoundsOut.set(currentDestinationBounds);
        if (!fromImeAdjustment && !fromShelfAdjustment
                && mPipBoundsState.getDisplayBounds().contains(currentDestinationBounds)) {
            // no need to update the destination bounds, bail early
            return;
        }

        final Rect newDestinationBounds = mPipBoundsAlgorithm.getEntryDestinationBounds();
        if (newDestinationBounds.equals(currentDestinationBounds)) return;
        updateAnimatorBounds(newDestinationBounds);
        destinationBoundsOut.set(newDestinationBounds);
    }

    /**
     * Directly update the animator bounds.
     */
    public void updateAnimatorBounds(Rect bounds) {
        final PipAnimationController.PipTransitionAnimator animator =
                mPipAnimationController.getCurrentAnimator();
        if (animator != null && animator.isRunning()) {
            if (animator.getAnimationType() == ANIM_TYPE_BOUNDS) {
                if (mWaitForFixedRotation) {
                    // The new destination bounds are in next rotation (DisplayLayout has been
                    // rotated in computeRotatedBounds). The animation runs in previous rotation so
                    // the end bounds need to be transformed.
                    final Rect displayBounds = mPipBoundsState.getDisplayBounds();
                    final Rect rotatedEndBounds = new Rect(bounds);
                    rotateBounds(rotatedEndBounds, displayBounds, mNextRotation, mCurrentRotation);
                    animator.updateEndValue(rotatedEndBounds);
                } else {
                    animator.updateEndValue(bounds);
                }
            }
            animator.setDestinationBounds(bounds);
        }
    }

    /**
     * Handles all changes to the PictureInPictureParams.
     */
    protected void applyNewPictureInPictureParams(@NonNull PictureInPictureParams params) {
        if (PipUtils.aspectRatioChanged(params.getAspectRatioFloat(),
                mPictureInPictureParams.getAspectRatioFloat())) {
            if (mPipBoundsAlgorithm.isValidPictureInPictureAspectRatio(
                    params.getAspectRatioFloat())) {
                mPipParamsChangedForwarder.notifyAspectRatioChanged(params.getAspectRatioFloat());
            } else {
                ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: New aspect ratio is not valid."
                                + " hasAspectRatio=%b"
                                + " aspectRatio=%f",
                        TAG, params.hasSetAspectRatio(), params.getAspectRatioFloat());
            }
        }
        if (PipUtils.remoteActionsChanged(params.getActions(),
                mPictureInPictureParams.getActions())
                || !PipUtils.remoteActionsMatch(params.getCloseAction(),
                mPictureInPictureParams.getCloseAction())) {
            mPipParamsChangedForwarder.notifyActionsChanged(params.getActions(),
                    params.getCloseAction());
        }
    }

    private void logRemoteActions(@NonNull PictureInPictureParams params) {
        StringJoiner sj = new StringJoiner("|", "[", "]");
        if (params.hasSetActions()) {
            params.getActions().forEach((action) -> sj.add(action.getTitle()));
        }

        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: PIP remote actions=%s", TAG, sj.toString());
    }

    /**
     * Animates resizing of the pinned stack given the duration.
     */
    public void scheduleAnimateResizePip(Rect toBounds, int duration,
            Consumer<Rect> updateBoundsCallback) {
        scheduleAnimateResizePip(toBounds, duration, TRANSITION_DIRECTION_NONE,
                updateBoundsCallback);
    }

    /**
     * Animates resizing of the pinned stack given the duration.
     */
    public void scheduleAnimateResizePip(Rect toBounds, int duration,
            @PipAnimationController.TransitionDirection int direction,
            Consumer<Rect> updateBoundsCallback) {
        if (mWaitForFixedRotation) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: skip scheduleAnimateResizePip, entering pip deferred", TAG);
            return;
        }
        scheduleAnimateResizePip(mPipBoundsState.getBounds(), toBounds, 0 /* startingAngle */,
                null /* sourceHintRect */, direction, duration, updateBoundsCallback);
    }

    /**
     * Animates resizing of the pinned stack given the duration and start bounds.
     * This is used when the starting bounds is not the current PiP bounds.
     *
     * @param pipFinishResizeWCTRunnable callback to run after window updates are complete
     */
    public void scheduleAnimateResizePip(Rect fromBounds, Rect toBounds, int duration,
            float startingAngle, Consumer<Rect> updateBoundsCallback,
            Runnable pipFinishResizeWCTRunnable) {
        mPipFinishResizeWCTRunnable = pipFinishResizeWCTRunnable;
        if (mPipFinishResizeWCTRunnable != null) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "mPipFinishResizeWCTRunnable is set to be called once window updates");
        }

        scheduleAnimateResizePip(fromBounds, toBounds, duration, startingAngle,
                updateBoundsCallback);
    }

    private void scheduleAnimateResizePip(Rect fromBounds, Rect toBounds, int duration,
            float startingAngle, Consumer<Rect> updateBoundsCallback) {
        if (mWaitForFixedRotation) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: skip scheduleAnimateResizePip, entering pip deferred", TAG);
            return;
        }
        scheduleAnimateResizePip(fromBounds, toBounds, startingAngle, null /* sourceHintRect */,
                TRANSITION_DIRECTION_SNAP_AFTER_RESIZE, duration, updateBoundsCallback);
    }

    /**
     * Animates resizing of the pinned stack given the duration and start bounds.
     * This always animates the angle to zero from the starting angle.
     */
    private @Nullable PipAnimationController.PipTransitionAnimator<?> scheduleAnimateResizePip(
            Rect currentBounds, Rect destinationBounds, float startingAngle, Rect sourceHintRect,
            @PipAnimationController.TransitionDirection int direction, int durationMs,
            Consumer<Rect> updateBoundsCallback) {
        if (!mPipTransitionState.isInPip()) {
            // TODO: tend to use shouldBlockResizeRequest here as well but need to consider
            // the fact that when in exitPip, scheduleAnimateResizePip is executed in the window
            // container transaction callback and we want to set the mState immediately.
            return null;
        }

        final PipAnimationController.PipTransitionAnimator<?> animator = animateResizePip(
                currentBounds, destinationBounds, sourceHintRect, direction, durationMs,
                startingAngle);
        if (updateBoundsCallback != null) {
            updateBoundsCallback.accept(destinationBounds);
        }
        return animator;
    }

    /**
     * Directly perform manipulation/resize on the leash. This will not perform any
     * {@link WindowContainerTransaction} until {@link #scheduleFinishResizePip} is called.
     */
    public void scheduleResizePip(Rect toBounds, Consumer<Rect> updateBoundsCallback) {
        // Could happen when exitPip
        if (mToken == null || mLeash == null) {
            ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Abort animation, invalid leash", TAG);
            return;
        }
        mPipBoundsState.setBounds(toBounds);
        final SurfaceControl.Transaction tx = mSurfaceControlTransactionFactory.getTransaction();
        mSurfaceTransactionHelper
                .cropAndPosition(tx, mLeash, toBounds)
                .round(tx, mLeash, mPipTransitionState.isInPip());
        if (shouldSyncPipTransactionWithMenu()) {
            mPipMenuController.resizePipMenu(mLeash, tx, toBounds);
        } else {
            tx.apply();
        }
        if (updateBoundsCallback != null) {
            updateBoundsCallback.accept(toBounds);
        }
    }

    /**
     * Directly perform manipulation/resize on the leash, along with rotation. This will not perform
     * any {@link WindowContainerTransaction} until {@link #scheduleFinishResizePip} is called.
     */
    public void scheduleUserResizePip(Rect startBounds, Rect toBounds,
            Consumer<Rect> updateBoundsCallback) {
        scheduleUserResizePip(startBounds, toBounds, 0 /* degrees */, updateBoundsCallback);
    }

    /**
     * Directly perform a scaled matrix transformation on the leash. This will not perform any
     * {@link WindowContainerTransaction} until {@link #scheduleFinishResizePip} is called.
     */
    public void scheduleUserResizePip(Rect startBounds, Rect toBounds, float degrees,
            Consumer<Rect> updateBoundsCallback) {
        // Could happen when exitPip
        if (mToken == null || mLeash == null) {
            ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Abort animation, invalid leash", TAG);
            return;
        }

        if (startBounds.isEmpty() || toBounds.isEmpty()) {
            ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Attempted to user resize PIP to or from empty bounds, aborting.", TAG);
            return;
        }

        final SurfaceControl.Transaction tx = mSurfaceControlTransactionFactory.getTransaction();
        mSurfaceTransactionHelper
                .scale(tx, mLeash, startBounds, toBounds, degrees)
                .round(tx, mLeash, startBounds, toBounds);
        if (shouldSyncPipTransactionWithMenu()) {
            mPipMenuController.movePipMenu(mLeash, tx, toBounds, PipMenuController.ALPHA_NO_CHANGE);
        } else {
            tx.apply();
        }
        if (updateBoundsCallback != null) {
            updateBoundsCallback.accept(toBounds);
        }
    }

    /**
     * Finish an intermediate resize operation. This is expected to be called after
     * {@link #scheduleResizePip}.
     */
    public void scheduleFinishResizePip(Rect destinationBounds) {
        scheduleFinishResizePip(destinationBounds, null /* updateBoundsCallback */);
    }

    /**
     * Same as {@link #scheduleFinishResizePip} but with a callback.
     */
    public void scheduleFinishResizePip(Rect destinationBounds,
            Consumer<Rect> updateBoundsCallback) {
        scheduleFinishResizePip(destinationBounds, TRANSITION_DIRECTION_NONE, updateBoundsCallback);
    }

    /**
     * Finish an intermediate resize operation. This is expected to be called after
     * {@link #scheduleResizePip}.
     *
     * @param destinationBounds the final bounds of the PIP after resizing
     * @param direction the transition direction
     * @param updateBoundsCallback a callback to invoke after finishing the resize
     */
    public void scheduleFinishResizePip(Rect destinationBounds,
            @PipAnimationController.TransitionDirection int direction,
            Consumer<Rect> updateBoundsCallback) {
        if (mPipTransitionState.shouldBlockResizeRequest()) {
            return;
        }

        if (mLeash == null || !mLeash.isValid()) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: scheduleFinishResizePip with null leash! mState=%d",
                    TAG, mPipTransitionState.getTransitionState());
            return;
        }

        finishResize(createFinishResizeSurfaceTransaction(destinationBounds), destinationBounds,
                direction, -1);
        if (updateBoundsCallback != null) {
            updateBoundsCallback.accept(destinationBounds);
        }
    }

    private SurfaceControl.Transaction createFinishResizeSurfaceTransaction(
            Rect destinationBounds) {
        final SurfaceControl.Transaction tx = mSurfaceControlTransactionFactory.getTransaction();
        mSurfaceTransactionHelper
                .cropAndPosition(tx, mLeash, destinationBounds)
                .resetScale(tx, mLeash, destinationBounds)
                .round(tx, mLeash, mPipTransitionState.isInPip());
        return tx;
    }

    /**
     * Offset the PiP window by a given offset on Y-axis, triggered also from screen rotation.
     */
    public void scheduleOffsetPip(Rect originalBounds, int offset, int duration,
            Consumer<Rect> updateBoundsCallback) {
        if (mPipTransitionState.shouldBlockResizeRequest()
                || mPipTransitionState.getInSwipePipToHomeTransition()) {
            return;
        }
        if (mWaitForFixedRotation) {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: skip scheduleOffsetPip, entering pip deferred", TAG);
            return;
        }
        offsetPip(originalBounds, 0 /* xOffset */, offset, duration);
        Rect toBounds = new Rect(originalBounds);
        toBounds.offset(0, offset);
        if (updateBoundsCallback != null) {
            updateBoundsCallback.accept(toBounds);
        }
    }

    private void offsetPip(Rect originalBounds, int xOffset, int yOffset, int durationMs) {
        if (mTaskInfo == null) {
            ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE, "%s: mTaskInfo is not set",
                    TAG);
            return;
        }
        final Rect destinationBounds = new Rect(originalBounds);
        destinationBounds.offset(xOffset, yOffset);
        animateResizePip(originalBounds, destinationBounds, null /* sourceHintRect */,
                TRANSITION_DIRECTION_SAME, durationMs, 0);
    }

    private void finishResize(SurfaceControl.Transaction tx, Rect destinationBounds,
            @PipAnimationController.TransitionDirection int direction,
            @PipTransitionController.AnimationType int type) {
        final Rect preResizeBounds = new Rect(mPipBoundsState.getBounds());
        mPipBoundsState.setBounds(destinationBounds);
        if (direction == TRANSITION_DIRECTION_REMOVE_STACK) {
            removePipImmediately();
            return;
        } else if (isInPipDirection(direction) && type == ANIM_TYPE_ALPHA) {
            // TODO: Synchronize this correctly in #applyEnterPipSyncTransaction
            finishResizeForMenu(destinationBounds);
            return;
        }

        WindowContainerTransaction wct = new WindowContainerTransaction();
        prepareFinishResizeTransaction(destinationBounds, direction, tx, wct);

        // Only corner drag, pinch or expand/un-expand resizing may lead to animating the finish
        // resize operation.
        final boolean mayAnimateFinishResize = direction == TRANSITION_DIRECTION_USER_RESIZE
                || direction == TRANSITION_DIRECTION_SNAP_AFTER_RESIZE
                || direction == TRANSITION_DIRECTION_EXPAND_OR_UNEXPAND;
        // Animate with a cross-fade if enabled and seamless resize is disables by the app.
        final boolean animateCrossFadeResize = mayAnimateFinishResize
                && mPictureInPictureParams != null
                && !mPictureInPictureParams.isSeamlessResizeEnabled();
        if (animateCrossFadeResize) {
            // Take a snapshot of the PIP task and show it. We'll fade it out after the wct
            // transaction is applied and the activity is laid out again.
            preResizeBounds.offsetTo(0, 0);
            final Rect snapshotDest = new Rect(0, 0, destinationBounds.width(),
                    destinationBounds.height());
            // Note: Put this at layer=MAX_VALUE-2 since the input consumer for PIP is placed at
            //       MAX_VALUE-1
            final SurfaceControl snapshotSurface = ScreenshotUtils.takeScreenshot(
                    mSurfaceControlTransactionFactory.getTransaction(), mLeash, preResizeBounds,
                    Integer.MAX_VALUE - 2);
            if (snapshotSurface != null) {
                mSyncTransactionQueue.queue(wct);
                mSyncTransactionQueue.runInSync(t -> {
                    // reset the pinch gesture
                    maybePerformFinishResizeCallback();

                    // Scale the snapshot from its pre-resize bounds to the post-resize bounds.
                    mSurfaceTransactionHelper.scale(t, snapshotSurface, preResizeBounds,
                            snapshotDest);

                    // Start animation to fade out the snapshot.
                    fadeOutAndRemoveOverlay(snapshotSurface,
                            null /* callback */, false /* withStartDelay */);
                });
            } else {
                applyFinishBoundsResize(wct, direction, false);
            }
        } else {
            applyFinishBoundsResize(wct, direction, isPipToTopLeft());
            // Use sync transaction to apply finish transaction for enter split case.
            if (direction == TRANSITION_DIRECTION_LEAVE_PIP_TO_SPLIT_SCREEN) {
                mSyncTransactionQueue.runInSync(t -> {
                    t.merge(tx);
                });
            }
        }

        finishResizeForMenu(destinationBounds);
    }

    /** Moves the PiP menu to the destination bounds. */
    public void finishResizeForMenu(Rect destinationBounds) {
        if (!isInPip()) {
            return;
        }
        mPipMenuController.movePipMenu(null, null, destinationBounds,
                PipMenuController.ALPHA_NO_CHANGE);
        mPipMenuController.updateMenuBounds(destinationBounds);
    }

    private void prepareFinishResizeTransaction(Rect destinationBounds,
            @PipAnimationController.TransitionDirection int direction,
            SurfaceControl.Transaction tx,
            WindowContainerTransaction wct) {
        if (mLeash == null || !mLeash.isValid()) {
            ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Invalid leash on prepareFinishResizeTransaction: %s", TAG, mLeash);
            return;
        }
        final Rect taskBounds;
        if (isInPipDirection(direction)) {
            // If we are animating from fullscreen using a bounds animation, then reset the
            // activity windowing mode set by WM, and set the task bounds to the final bounds
            taskBounds = destinationBounds;
            wct.setActivityWindowingMode(mToken, WINDOWING_MODE_UNDEFINED);
        } else if (isOutPipDirection(direction)) {
            // If we are animating to fullscreen or split screen, then we need to reset the
            // override bounds on the task to ensure that the task "matches" the parent's bounds.
            taskBounds = null;
            applyWindowingModeChangeOnExit(wct, direction);
        } else {
            // Just a resize in PIP
            taskBounds = destinationBounds;
        }
        mSurfaceTransactionHelper.round(tx, mLeash, isInPip());

        wct.setBounds(mToken, taskBounds);
        // Pip to split should use sync transaction to sync split bounds change.
        if (direction != TRANSITION_DIRECTION_LEAVE_PIP_TO_SPLIT_SCREEN) {
            wct.setBoundsChangeTransaction(mToken, tx);
        }
    }

    /**
     * Applies the window container transaction to finish a bounds resize.
     *
     * Called by {@link #finishResize(SurfaceControl.Transaction, Rect, int, int)}} once it has
     * finished preparing the transaction. It allows subclasses to modify the transaction before
     * applying it.
     */
    public void applyFinishBoundsResize(@NonNull WindowContainerTransaction wct,
            @PipAnimationController.TransitionDirection int direction, boolean wasPipTopLeft) {
        if (direction == TRANSITION_DIRECTION_LEAVE_PIP_TO_SPLIT_SCREEN) {
            mSplitScreenOptional.ifPresent(splitScreenController ->
                    splitScreenController.enterSplitScreen(mTaskInfo.taskId, wasPipTopLeft, wct));
        } else {
            mTaskOrganizer.applyTransaction(wct);
        }
    }

    private boolean isPipToTopLeft() {
        if (!mSplitScreenOptional.isPresent()) {
            return false;
        }
        return mSplitScreenOptional.get().getActivateSplitPosition(mTaskInfo)
                == SPLIT_POSITION_TOP_OR_LEFT;
    }

    private boolean isDisplayInFreeform() {
        final DisplayAreaInfo tdaInfo = mRootTaskDisplayAreaOrganizer.getDisplayAreaInfo(
                mTaskInfo.displayId);
        if (tdaInfo != null) {
            return tdaInfo.configuration.windowConfiguration.getWindowingMode()
                    == WINDOWING_MODE_FREEFORM;
        }
        return false;
    }

    /**
     * The windowing mode to restore to when resizing out of PIP direction. Defaults to undefined
     * and can be overridden to restore to an alternate windowing mode.
     */
    public int getOutPipWindowingMode() {
        // If we are exiting PiP while the device is in Desktop mode (the task should expand to
        // freeform windowing mode):
        // 1) If the display windowing mode is freeform, set windowing mode to undefined so it will
        //    resolve the windowing mode to the display's windowing mode.
        // 2) If the display windowing mode is not freeform, set windowing mode to freeform.
        if (isPipExitingToDesktopMode()) {
            if (isDisplayInFreeform()) {
                return WINDOWING_MODE_UNDEFINED;
            } else {
                return WINDOWING_MODE_FREEFORM;
            }
        }

        // By default, or if the task is going to fullscreen, reset the windowing mode to undefined.
        return WINDOWING_MODE_UNDEFINED;
    }

    private @Nullable PipAnimationController.PipTransitionAnimator<?> animateResizePip(
            Rect currentBounds, Rect destinationBounds, Rect sourceHintRect,
            @PipAnimationController.TransitionDirection int direction, int durationMs,
            float startingAngle) {
        // Could happen when exitPip
        if (mToken == null || mLeash == null) {
            ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: Abort animation, invalid leash", TAG);
            return null;
        }
        if (isInPipDirection(direction) && !PipBoundsAlgorithm
                .isSourceRectHintValidForEnterPip(sourceHintRect, destinationBounds)) {
            // The given source rect hint is too small for enter PiP animation, reset it to null.
            sourceHintRect = null;
        }
        final int rotationDelta = mWaitForFixedRotation
                ? deltaRotation(mCurrentRotation, mNextRotation)
                : Surface.ROTATION_0;
        if (rotationDelta != Surface.ROTATION_0) {
            sourceHintRect = computeRotatedBounds(rotationDelta, direction, destinationBounds,
                    sourceHintRect);
        }
        final Rect baseBounds = direction == TRANSITION_DIRECTION_SNAP_AFTER_RESIZE
                ? mPipBoundsState.getBounds() : currentBounds;
        final boolean existingAnimatorRunning = mPipAnimationController.getCurrentAnimator() != null
                && mPipAnimationController.getCurrentAnimator().isRunning();
        // For resize animation, we always animate the whole PIP task bounds.
        final PipAnimationController.PipTransitionAnimator<?> animator = mPipAnimationController
                .getAnimator(mTaskInfo, mLeash, baseBounds, currentBounds, destinationBounds,
                        sourceHintRect, direction, startingAngle, rotationDelta,
                        true /* alwaysAnimateTaskBounds */);
        animator.setTransitionDirection(direction)
                .setPipTransactionHandler(mPipTransactionHandler)
                .setDuration(durationMs);
        if (!existingAnimatorRunning) {
            animator.setPipAnimationCallback(mPipAnimationCallback);
        }
        if (isInPipDirection(direction)) {
            // Similar to auto-enter-pip transition, we use content overlay when there is no
            // source rect hint to enter PiP use bounds animation.
            if (sourceHintRect == null) {
                // We use content overlay when there is no source rect hint to enter PiP use bounds
                // animation.
                // TODO(b/272819817): cleanup the null-check and extra logging.
                final boolean hasTopActivityInfo = mTaskInfo.topActivityInfo != null;
                if (hasTopActivityInfo) {
                    animator.setAppIconContentOverlay(
                            mContext, currentBounds, destinationBounds, mTaskInfo.topActivityInfo,
                            mPipBoundsState.getLauncherState().getAppIconSizePx());
                } else {
                    ProtoLog.w(ShellProtoLogGroup.WM_SHELL_TRANSITIONS,
                            "%s: TaskInfo.topActivityInfo is null", TAG);
                    animator.setColorContentOverlay(mContext);
                }
            } else {
                final TaskSnapshot snapshot = PipUtils.getTaskSnapshot(
                        mTaskInfo.launchIntoPipHostTaskId, false /* isLowResolution */);
                if (snapshot != null) {
                    // use the task snapshot during the animation, this is for
                    // launch-into-pip aka. content-pip use case.
                    animator.setSnapshotContentOverlay(snapshot, sourceHintRect);
                }
            }
            mPipOverlay = animator.getContentOverlayLeash();
            // The destination bounds are used for the end rect of animation and the final bounds
            // after animation finishes. So after the animation is started, the destination bounds
            // can be updated to new rotation (computeRotatedBounds has changed the DisplayLayout
            // without affecting the animation.
            if (rotationDelta != Surface.ROTATION_0) {
                animator.setDestinationBounds(mPipBoundsAlgorithm.getEntryDestinationBounds());
            }
        }
        animator.start();
        return animator;
    }

    /** Computes destination bounds in old rotation and returns source hint rect if available.
     *
     * Note: updates the internal state of {@link PipDisplayLayoutState} by applying a rotation
     * transformation onto the display layout.
     */
    private @Nullable Rect computeRotatedBounds(int rotationDelta, int direction,
            Rect outDestinationBounds, Rect sourceHintRect) {
        if (direction == TRANSITION_DIRECTION_TO_PIP) {
            mPipDisplayLayoutState.rotateTo(mNextRotation);

            final Rect displayBounds = mPipBoundsState.getDisplayBounds();
            outDestinationBounds.set(mPipBoundsAlgorithm.getEntryDestinationBounds());
            // Transform the destination bounds to current display coordinates.
            rotateBounds(outDestinationBounds, displayBounds, mNextRotation, mCurrentRotation);
            // When entering PiP (from button navigation mode), adjust the source rect hint by
            // display cutout if applicable.
            if (sourceHintRect != null && mTaskInfo.displayCutoutInsets != null) {
                if (rotationDelta == Surface.ROTATION_270) {
                    sourceHintRect.offset(mTaskInfo.displayCutoutInsets.left,
                            mTaskInfo.displayCutoutInsets.top);
                }
            }
        } else if (direction == TRANSITION_DIRECTION_LEAVE_PIP) {
            final Rect rotatedDestinationBounds = new Rect(outDestinationBounds);
            rotateBounds(rotatedDestinationBounds, mPipBoundsState.getDisplayBounds(),
                    rotationDelta);
            return PipBoundsAlgorithm.getValidSourceHintRect(mPictureInPictureParams,
                    rotatedDestinationBounds);
        }
        return sourceHintRect;
    }

    /**
     * Sync with {@link SplitScreenController} on destination bounds if PiP is going to
     * split screen.
     *
     * @param destinationBoundsOut contain the updated destination bounds if applicable
     * @return {@code true} if destinationBounds is altered for split screen
     */
    private boolean syncWithSplitScreenBounds(Rect destinationBoundsOut, boolean enterSplit) {
        if (mSplitScreenOptional.isEmpty()) {
            return false;
        }
        final SplitScreenController split = mSplitScreenOptional.get();
        final int position = mTaskInfo.lastParentTaskIdBeforePip > 0
                ? split.getSplitPosition(mTaskInfo.lastParentTaskIdBeforePip)
                : SPLIT_POSITION_UNDEFINED;
        if (position == SPLIT_POSITION_UNDEFINED && !enterSplit) {
            return false;
        }
        final Rect topLeft = new Rect();
        final Rect bottomRight = new Rect();
        split.getStageBounds(topLeft, bottomRight);
        if (enterSplit) {
            destinationBoundsOut.set(isPipToTopLeft() ? topLeft : bottomRight);
            return true;
        }
        // Moving to an existing split task.
        destinationBoundsOut.set(position == SPLIT_POSITION_TOP_OR_LEFT ? topLeft : bottomRight);
        return false;
    }

    /**
     * Fades out and removes an overlay surface.
     */
    void fadeOutAndRemoveOverlay(SurfaceControl surface, Runnable callback,
            boolean withStartDelay) {
        if (surface == null || !surface.isValid()) {
            return;
        }

        final ValueAnimator animator = ValueAnimator.ofFloat(1.0f, 0.0f);
        animator.setDuration(mCrossFadeAnimationDuration);
        animator.addUpdateListener(animation -> {
            if (mPipTransitionState.getTransitionState() == PipTransitionState.UNDEFINED) {
                // Could happen if onTaskVanished happens during the animation since we may have
                // set a start delay on this animation.
                ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: Task vanished, skip fadeOutAndRemoveOverlay", TAG);
                PipAnimationController.quietCancel(animation);
            } else if (surface.isValid()) {
                final float alpha = (float) animation.getAnimatedValue();
                final SurfaceControl.Transaction transaction =
                        mSurfaceControlTransactionFactory.getTransaction();
                transaction.setAlpha(surface, alpha);
                transaction.apply();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                removeContentOverlay(surface, callback);
            }
        });
        animator.setStartDelay(withStartDelay
                ? CONTENT_OVERLAY_FADE_OUT_DELAY_MS
                : EXTRA_CONTENT_OVERLAY_FADE_OUT_DELAY_MS);
        animator.start();
    }

    private void removeContentOverlay(SurfaceControl surface, Runnable callback) {
        ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "removeContentOverlay: %s, state=%s, surface=%s",
                mTaskInfo, mPipTransitionState, surface);
        if (mPipOverlay != null) {
            if (mPipOverlay != surface) {
                ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s: trying to remove overlay (%s) which is not local reference (%s)",
                        TAG, surface, mPipOverlay);
            }
            clearContentOverlay();
        }
        if (surface == null || !surface.isValid()) {
            ProtoLog.w(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: trying to remove invalid content overlay (%s)", TAG, surface);
            return;
        }
        final SurfaceControl.Transaction tx = mSurfaceControlTransactionFactory.getTransaction();
        tx.remove(surface);
        tx.apply();
        if (callback != null) callback.run();
    }

    void clearContentOverlay() {
        mPipOverlay = null;
        mAppBounds.setEmpty();
    }

    void setContentOverlay(@Nullable SurfaceControl leash, @NonNull Rect appBounds) {
        mPipOverlay = leash;
        if (mPipOverlay != null) {
            mAppBounds.set(appBounds);
        } else {
            mAppBounds.setEmpty();
        }
    }

    private void resetShadowRadius() {
        if (mPipTransitionState.getTransitionState() == PipTransitionState.UNDEFINED) {
            // mLeash is undefined when in PipTransitionState.UNDEFINED
            return;
        }
        final SurfaceControl.Transaction tx = mSurfaceControlTransactionFactory.getTransaction();
        mSurfaceTransactionHelper.shadow(tx, mLeash, false /* applyShadowRadius */);
        tx.apply();
    }

    /**
     * Cancels the currently running animator if there is one and removes an overlay if present.
     */
    public void cancelCurrentAnimator() {
        final PipAnimationController.PipTransitionAnimator<?> animator =
                mPipAnimationController.getCurrentAnimator();
        // remove any overlays if present
        if (mPipOverlay != null) {
            removeContentOverlay(mPipOverlay, null /* callback */);
        }
        if (animator != null) {
            animator.cancel();
            mPipAnimationController.resetAnimatorState();
        }
    }

    @VisibleForTesting
    public void setSurfaceControlTransactionFactory(
            PipSurfaceTransactionHelper.SurfaceControlTransactionFactory factory) {
        mSurfaceControlTransactionFactory = factory;
    }

    public boolean isLaunchToSplit(TaskInfo taskInfo) {
        return mSplitScreenOptional.isPresent()
                && mSplitScreenOptional.get().isLaunchToSplit(taskInfo);
    }

    /**
     * Dumps internal states.
     */
    @Override
    public void dump(PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + TAG);
        pw.println(innerPrefix + "mTaskInfo=" + mTaskInfo);
        pw.println(innerPrefix + "mToken=" + mToken
                + " binder=" + (mToken != null ? mToken.asBinder() : null));
        pw.println(innerPrefix + "mLeash=" + mLeash);
        pw.println(innerPrefix + "mPipOverlay=" + mPipOverlay);
        pw.println(innerPrefix + "mState=" + mPipTransitionState.getTransitionState());
        pw.println(innerPrefix + "mPictureInPictureParams=" + mPictureInPictureParams);
        mPipTransitionController.dump(pw, innerPrefix);
        if (mPipPerfHintController != null) {
            mPipPerfHintController.dump(pw, innerPrefix);
        }
    }

    @Override
    public String toString() {
        return TAG + ":" + taskListenerTypeToString(TASK_LISTENER_TYPE_PIP);
    }

    /**
     * Called when the theme changes.
     */
    public void onThemeChanged(Context context) {
        if (isInPip()) {
            mSurfaceTransactionHelper.onThemeChanged(context);
            SurfaceControl.Transaction tx = mSurfaceControlTransactionFactory.getTransaction();
            mSurfaceTransactionHelper.shadow(tx, mLeash, true /* applyShadowRadius */);
            tx.apply();
        }
    }
}

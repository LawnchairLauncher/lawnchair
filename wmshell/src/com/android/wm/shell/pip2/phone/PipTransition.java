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

package com.android.wm.shell.pip2.phone;

import static android.app.WindowConfiguration.ROTATION_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_PIP;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;

import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getChangeByToken;
import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getFixedRotationDelta;
import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getLeash;
import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getPipChange;
import static com.android.wm.shell.pip2.phone.transition.PipTransitionUtils.getPipParams;
import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE;
import static com.android.wm.shell.transition.Transitions.TRANSIT_EXIT_PIP;
import static com.android.wm.shell.transition.Transitions.TRANSIT_EXIT_PIP_TO_SPLIT;
import static com.android.wm.shell.transition.Transitions.TRANSIT_PIP_BOUNDS_CHANGE;
import static com.android.wm.shell.transition.Transitions.TRANSIT_REMOVE_PIP;
import static com.android.wm.shell.transition.Transitions.transitTypeToString;

import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.PictureInPictureParams;
import android.app.TaskInfo;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Debug;
import android.os.IBinder;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.Nullable;

import com.android.internal.protolog.ProtoLog;
import com.android.internal.util.Preconditions;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.ComponentUtils;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDesktopState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipMenuController;
import com.android.wm.shell.common.pip.PipUtils;
import com.android.wm.shell.desktopmode.DesktopPipTransitionController;
import com.android.wm.shell.pip.PipTransitionController;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip2.animation.PipAlphaAnimator;
import com.android.wm.shell.pip2.animation.PipEnterAnimator;
import com.android.wm.shell.pip2.phone.transition.ContentPipHandler;
import com.android.wm.shell.pip2.phone.transition.PipDisplayChangeObserver;
import com.android.wm.shell.pip2.phone.transition.PipExpandHandler;
import com.android.wm.shell.pip2.phone.transition.PipTransitionUtils;
import com.android.wm.shell.shared.TransitionUtil;
import com.android.wm.shell.shared.pip.PipFlags;
import com.android.wm.shell.splitscreen.SplitScreenController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

import java.util.Optional;

/**
 * Implementation of transitions for PiP on phone.
 */
public class PipTransition extends PipTransitionController implements
        PipTransitionState.PipTransitionStateChangedListener,
        PipDisplayLayoutState.DisplayIdListener {
    private static final String TAG = PipTransition.class.getSimpleName();

    // Used when for ENTERING_PIP state update.
    private static final String PIP_TASK_LEASH = "pip_task_leash";
    private static final String PIP_TASK_INFO = "pip_task_info";

    // Used for PiP CHANGING_BOUNDS state update.
    static final String PIP_START_TX = "pip_start_tx";
    static final String PIP_FINISH_TX = "pip_finish_tx";
    static final String PIP_DESTINATION_BOUNDS = "pip_dest_bounds";
    static final String ANIMATING_BOUNDS_CHANGE_DURATION =
            "animating_bounds_change_duration";
    static final int BOUNDS_CHANGE_JUMPCUT_DURATION = 0;

    //
    // Dependencies
    //

    private Context mContext;
    private final PipTaskListener mPipTaskListener;
    private final PipScheduler mPipScheduler;
    private final PipTransitionState mPipTransitionState;
    private final PipDisplayLayoutState mPipDisplayLayoutState;
    private final DisplayController mDisplayController;
    private final Optional<SplitScreenController> mSplitScreenControllerOptional;
    private final PipSurfaceTransactionHelper mPipSurfaceTransactionHelper;
    private final PipDesktopState mPipDesktopState;
    private final Optional<DesktopPipTransitionController> mDesktopPipTransitionController;
    private final PipInteractionHandler mPipInteractionHandler;

    //
    // Transition caches
    //

    @Nullable
    private IBinder mEnterTransition;
    @Nullable
    private IBinder mExitViaExpandTransition;
    @Nullable
    private IBinder mBoundsChangeTransition;
    private int mBoundsChangeDuration = BOUNDS_CHANGE_JUMPCUT_DURATION;
    private boolean mPendingRemoveWithFadeout;


    //
    // Internal state and relevant cached info
    //
    private final PipExpandHandler mExpandHandler;
    private final ContentPipHandler mContentPipHandler;
    private final PipDisplayChangeObserver mPipDisplayChangeObserver;

    private Transitions.TransitionFinishCallback mFinishCallback;

    private ValueAnimator mTransitionAnimator;

    private @AnimationType int mEnterAnimationType = ANIM_TYPE_BOUNDS;

    public PipTransition(
            Context context,
            @NonNull PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            @NonNull ShellInit shellInit,
            @NonNull ShellTaskOrganizer shellTaskOrganizer,
            @NonNull Transitions transitions,
            PipBoundsState pipBoundsState,
            PipMenuController pipMenuController,
            PipBoundsAlgorithm pipBoundsAlgorithm,
            PipTaskListener pipTaskListener,
            PipScheduler pipScheduler,
            PipTransitionState pipTransitionState,
            PipDisplayLayoutState pipDisplayLayoutState,
            PipUiStateChangeController pipUiStateChangeController,
            DisplayController displayController,
            Optional<SplitScreenController> splitScreenControllerOptional,
            PipDesktopState pipDesktopState,
            Optional<DesktopPipTransitionController> desktopPipTransitionController,
            PipInteractionHandler pipInteractionHandler) {
        super(shellInit, shellTaskOrganizer, transitions, pipBoundsState, pipMenuController,
                pipBoundsAlgorithm);

        mContext = context;
        mPipTaskListener = pipTaskListener;
        mPipScheduler = pipScheduler;
        mPipScheduler.setPipTransitionController(this);
        mPipTransitionState = pipTransitionState;
        mPipTransitionState.addPipTransitionStateChangedListener(this);
        mPipDisplayLayoutState = pipDisplayLayoutState;
        mPipDisplayLayoutState.addDisplayIdListener(this);
        mDisplayController = displayController;
        mSplitScreenControllerOptional = splitScreenControllerOptional;
        mPipSurfaceTransactionHelper = pipSurfaceTransactionHelper;
        mPipDesktopState = pipDesktopState;
        mDesktopPipTransitionController = desktopPipTransitionController;
        mPipInteractionHandler = pipInteractionHandler;

        mExpandHandler = new PipExpandHandler(mContext, mPipSurfaceTransactionHelper,
                pipBoundsState, pipBoundsAlgorithm,
                pipTransitionState, pipDisplayLayoutState, pipDesktopState, pipInteractionHandler,
                splitScreenControllerOptional);
        mContentPipHandler = new ContentPipHandler(mContext, mPipSurfaceTransactionHelper,
                pipTransitionState);
        mPipDisplayChangeObserver = new PipDisplayChangeObserver(pipTransitionState,
                pipBoundsState);
    }

    @Override
    protected void onInit() {
        if (PipFlags.isPip2ExperimentEnabled()) {
            mTransitions.addHandler(this);
            mTransitions.registerObserver(mPipDisplayChangeObserver);
        }
    }

    @Override
    public void onDisplayIdChanged(@NonNull Context context) {
        mContext = context;
        mExpandHandler.onDisplayIdChanged(context);
    }

    @Override
    protected boolean isInSwipePipToHomeTransition() {
        return mPipTransitionState.isInSwipePipToHomeTransition();
    }

    //
    // Transition collection stage lifecycle hooks
    //

    @Override
    public void startExpandTransition(WindowContainerTransaction wct, boolean toSplit) {
        if (wct == null) return;
        mPipTransitionState.setState(PipTransitionState.EXITING_PIP);
        mExitViaExpandTransition = mTransitions.startTransition(toSplit ? TRANSIT_EXIT_PIP_TO_SPLIT
                : TRANSIT_EXIT_PIP, wct, this);
    }

    @Override
    public void startRemoveTransition(WindowContainerTransaction wct, boolean withFadeout) {
        if (wct == null) return;
        mPipTransitionState.setState(PipTransitionState.EXITING_PIP);
        mPendingRemoveWithFadeout = withFadeout;
        mTransitions.startTransition(TRANSIT_REMOVE_PIP, wct, this);
    }

    @Override
    public void startPipBoundsChangeTransition(WindowContainerTransaction wct, int duration) {
        if (wct == null) {
            return;
        }
        mBoundsChangeTransition = mTransitions.startTransition(TRANSIT_PIP_BOUNDS_CHANGE, wct,
                this);
        mBoundsChangeDuration = duration;
    }

    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        if (mPipTransitionState.getState() == PipTransitionState.SCHEDULED_ENTER_PIP) {
            // An enter PiP transition has already been scheduled and is waiting to be played.
            return null;
        }
        if (isAutoEnterInButtonNavigation(request) || isEnterPictureInPictureModeRequest(request)) {
            mEnterTransition = transition;
            mPipTransitionState.setState(PipTransitionState.SCHEDULED_ENTER_PIP);
            final WindowContainerTransaction wct = getEnterPipTransaction(transition,
                    request.getPipChange());

            mDesktopPipTransitionController.ifPresent(
                    desktopPipTransitionController ->
                            desktopPipTransitionController.handlePipTransition(
                                    wct,
                                    transition,
                                    request.getPipChange().getTaskInfo()
                            )
            );
            return wct;
        }
        return null;
    }

    @Override
    public void augmentRequest(@NonNull IBinder transition, @NonNull TransitionRequestInfo request,
            @NonNull WindowContainerTransaction outWct) {
        if (isAutoEnterInButtonNavigation(request) || isEnterPictureInPictureModeRequest(request)) {
            outWct.merge(getEnterPipTransaction(transition, request.getPipChange()),
                    true /* transfer */);
            mEnterTransition = transition;
            mPipTransitionState.setState(PipTransitionState.SCHEDULED_ENTER_PIP);
        }
    }

    //
    // Transition playing stage lifecycle hooks
    //

    @Override
    public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startT,
            @NonNull SurfaceControl.Transaction finishT,
            @NonNull IBinder mergeTarget,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        if (info.getType() == TRANSIT_EXIT_PIP) {
            end();
        }
        mExpandHandler.mergeAnimation(transition, info, startT, finishT, mergeTarget,
                finishCallback);
    }

    @Override
    public void onTransitionConsumed(@NonNull IBinder transition, boolean aborted,
            @Nullable SurfaceControl.Transaction finishT) {
        if ((transition == mBoundsChangeTransition || transition == mEnterTransition) && aborted) {
            onTransitionAborted();
        }
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition,
            @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        if (transition == mEnterTransition || info.getType() == TRANSIT_PIP) {
            mEnterTransition = null;
            // If we are in swipe PiP to Home transition we are ENTERING_PIP as a jumpcut transition
            // is being carried out.
            TransitionInfo.Change pipChange = getPipChange(info);

            // If there is no PiP change, exit this transition handler and potentially try others.
            if (pipChange == null) return false;

            // Other targets might have default transforms applied that are not relevant when
            // playing PiP transitions, so reset those transforms if needed.
            prepareOtherTargetTransforms(info, startTransaction, finishTransaction);

            // This PiP transition might have caused a previous PiP to be dismissed. If so, we need
            // to clean up the PiP state.
            cleanUpPrevPipIfPresent(info, startTransaction, finishTransaction);

            // Update the PipTransitionState while supplying the PiP leash and token to be cached.
            Bundle extra = new Bundle();
            extra.putParcelable(PIP_TASK_LEASH, pipChange.getLeash());
            extra.putParcelable(PIP_TASK_INFO, pipChange.getTaskInfo());
            mPipTransitionState.setState(PipTransitionState.ENTERING_PIP, extra);

            if (isInSwipePipToHomeTransition()) {
                // If this is the second transition as a part of swipe PiP to home cuj,
                // handle this transition as a special case with no-op animation.
                return handleSwipePipToHomeTransition(info, startTransaction, finishTransaction,
                        finishCallback);
            }
            if (mContentPipHandler.startAnimation(transition, info,
                    startTransaction, finishTransaction, finishCallback)) {
                return true;
            }
            if (isLegacyEnter(info)) {
                // If this is a legacy-enter-pip (auto-enter is off and PiP activity went to pause),
                // then we should run an ALPHA type (cross-fade) animation.
                return startAlphaTypeEnterAnimation(info, startTransaction, finishTransaction,
                        finishCallback);
            }

            TransitionInfo.Change pipActivityChange = PipTransitionUtils
                    .getDeferConfigActivityChange(info, pipChange.getTaskInfo().getToken());
            if (pipActivityChange == null) {
                // Legacy-enter and swipe-pip-to-home filters did not resolve a scheduled PiP entry.
                // Bounds-type enter animation is the last resort, and it requires a config-at-end
                // activity amongst the list of changes. If no such change, something went wrong.
                Log.wtf(TAG, String.format("""
                        PipTransition.startAnimation didn't handle a scheduled PiP entry
                        transitionInfo=%s,
                        callers=%s""", info, Debug.getCallers(4)));
                return false;
            }

            return startBoundsTypeEnterAnimation(info, startTransaction, finishTransaction,
                    finishCallback);
        } else if (transition == mExitViaExpandTransition) {
            mExitViaExpandTransition = null;
            return mExpandHandler.startAnimation(transition, info, startTransaction,
                    finishTransaction, finishCallback);
        } else if (transition == mBoundsChangeTransition) {
            mBoundsChangeTransition = null;
            return startBoundsChangeAnimation(info, startTransaction, finishTransaction,
                    finishCallback);
        }

        if (isRemovePipTransition(info)) {
            mPipTransitionState.setState(PipTransitionState.EXITING_PIP);
            return startRemoveAnimation(info, startTransaction, finishTransaction, finishCallback);
        }

        if (shouldCleanUp(info)) {
            ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                    "Cleaning up previously pinned task since in a different windowing mode: %s",
                    mPipTransitionState);
            cleanUpState(finishCallback);
            return true;
        }

        // For any unhandled transition, make sure the PiP surface is properly updated,
        // i.e. corner and shadow radius.
        syncPipSurfaceState(info, startTransaction, finishTransaction);
        return false;
    }

    private boolean shouldCleanUp(TransitionInfo info) {
        // Clean up state if task no longer in PIP windowing mode.
        TransitionInfo.Change pipChange = getChangeByToken(info,
                mPipTransitionState.getPipTaskToken());
        return pipChange != null && pipChange.getTaskInfo() != null
                && pipChange.getTaskInfo().getWindowingMode() != WINDOWING_MODE_PINNED;
    }

    @Override
    public void cleanUpState() {
        cleanUpState(/* finishCallback= */ null);
    }

    private void cleanUpState(@Nullable Transitions.TransitionFinishCallback finishCallback) {
        mFinishCallback = finishCallback;
        mPipBoundsState.setLastPipComponentName(null /* lastPipComponentName */);
        mPipTransitionState.setState(PipTransitionState.EXITING_PIP);
        finishTransition();
    }

    @Override
    public boolean isEnteringPip(@NonNull TransitionInfo.Change change,
            @WindowManager.TransitionType int transitType) {
        if (change.getTaskInfo() != null
                && change.getTaskInfo().getWindowingMode() == WINDOWING_MODE_PINNED) {
            // TRANSIT_TO_FRONT, though uncommon with triggering PiP, should semantically also
            // be allowed to animate if the task in question is pinned already - see b/308054074.
            if (transitType == TRANSIT_PIP || transitType == TRANSIT_OPEN
                    || transitType == TRANSIT_TO_FRONT) {
                return true;
            }
            // This can happen if the request to enter PIP happens when we are collecting for
            // another transition, such as TRANSIT_CHANGE (display rotation).
            if (transitType == TRANSIT_CHANGE) {
                return true;
            }

            // Please file a bug to handle the unexpected transition type.
            android.util.Slog.e(TAG, "Found new PIP in transition with mis-matched type="
                    + transitTypeToString(transitType), new Throwable());
        }
        return false;
    }


    @Override
    public void end() {
        if (mTransitionAnimator != null && mTransitionAnimator.isRunning()) {
            mTransitionAnimator.end();
            mTransitionAnimator = null;
        }
    }

    @Override
    public boolean syncPipSurfaceState(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction) {
        final TransitionInfo.Change pipChange = getPipChange(info);
        if (pipChange == null) return false;

        // add shadow and corner radii
        final SurfaceControl leash = pipChange.getLeash();
        final boolean isInPip = mPipTransitionState.isInPip();

        mPipSurfaceTransactionHelper.round(startTransaction, leash, isInPip)
                .shadow(startTransaction, leash, isInPip);
        mPipSurfaceTransactionHelper.round(finishTransaction, leash, isInPip)
                .shadow(finishTransaction, leash, isInPip);

        return true;
    }

    //
    // Animation schedulers and entry points
    //

    private boolean startBoundsChangeAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        TransitionInfo.Change pipChange = getPipChange(info);
        if (pipChange == null) {
            return false;
        }
        mFinishCallback = finishCallback;
        // We expect the PiP activity as a separate change in a config-at-end transition;
        // only flings are not using config-at-end for resize bounds changes
        TransitionInfo.Change pipActivityChange = PipTransitionUtils.getDeferConfigActivityChange(
                info, pipChange.getTaskInfo().getToken());
        if (pipActivityChange != null) {
            // Transform calculations use PiP params by default, so make sure they are null to
            // default to using bounds for scaling calculations instead.
            pipChange.getTaskInfo().pictureInPictureParams = null;
            prepareConfigAtEndActivity(startTransaction, finishTransaction, pipChange,
                    pipActivityChange);
        }

        SurfaceControl pipLeash = pipChange.getLeash();
        startTransaction.setWindowCrop(pipLeash, pipChange.getEndAbsBounds().width(),
                pipChange.getEndAbsBounds().height());

        // Classes interested in continuing the animation would subscribe to this state update
        // getting info such as endBounds, startTx, and finishTx as an extra Bundle
        // Once done state needs to be updated to CHANGED_PIP_BOUNDS via {@link PipScheduler#}.
        Bundle extra = new Bundle();
        extra.putParcelable(PIP_START_TX, startTransaction);
        extra.putParcelable(PIP_FINISH_TX, finishTransaction);
        extra.putParcelable(PIP_DESTINATION_BOUNDS, pipChange.getEndAbsBounds());
        if (mBoundsChangeDuration > BOUNDS_CHANGE_JUMPCUT_DURATION) {
            extra.putInt(ANIMATING_BOUNDS_CHANGE_DURATION, mBoundsChangeDuration);
            mBoundsChangeDuration = BOUNDS_CHANGE_JUMPCUT_DURATION;
        }

        mPipTransitionState.setState(PipTransitionState.CHANGING_PIP_BOUNDS, extra);
        return true;
    }

    private boolean handleSwipePipToHomeTransition(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        TransitionInfo.Change pipChange = getPipChange(info);
        if (pipChange == null) {
            return false;
        }

        // We expect the PiP activity as a separate change in a config-at-end transition.
        TransitionInfo.Change pipActivityChange = PipTransitionUtils.getDeferConfigActivityChange(
                info, pipChange.getTaskInfo().getToken());
        if (pipActivityChange == null) {
            return false;
        }
        mFinishCallback = finishCallback;

        final SurfaceControl pipLeash = getLeash(pipChange);
        final Rect destinationBounds = pipChange.getEndAbsBounds();
        final SurfaceControl swipePipToHomeOverlay = mPipTransitionState.getSwipePipToHomeOverlay();
        if (swipePipToHomeOverlay != null) {
            final int overlaySize = PipAppIconOverlay.getOverlaySize(
                    mPipTransitionState.getSwipePipToHomeAppBounds(), destinationBounds);
            // It is possible we reparent the PIP activity to a new PIP task (in multi-activity
            // apps), so we should also reparent the overlay to the final PIP task.
            startTransaction.reparent(swipePipToHomeOverlay, pipLeash)
                    .setLayer(swipePipToHomeOverlay, Integer.MAX_VALUE)
                    .setScale(swipePipToHomeOverlay, 1f, 1f)
                    .setPosition(swipePipToHomeOverlay,
                            (destinationBounds.width() - overlaySize) / 2f,
                            (destinationBounds.height() - overlaySize) / 2f);
        }

        final int delta = getFixedRotationDelta(info, pipChange, mPipDisplayLayoutState);
        if (delta != ROTATION_0) {
            // Update transition target changes in place to prepare for fixed rotation.
            updatePipChangesForFixedRotation(info, pipChange, pipActivityChange);
        }

        // Update the src-rect-hint in params in place, to set up initial animator transform.
        Rect sourceRectHint = getAdjustedSourceRectHint(info, pipChange, pipActivityChange);
        final PictureInPictureParams params = getPipParams(pipChange);
        params.copyOnlySet(
                new PictureInPictureParams.Builder().setSourceRectHint(sourceRectHint).build());

        // Config-at-end transitions need to have their activities transformed before starting
        // the animation; this makes the buffer seem like it's been updated to final size.
        prepareConfigAtEndActivity(startTransaction, finishTransaction, pipChange,
                pipActivityChange);

        startTransaction.merge(finishTransaction);
        PipEnterAnimator animator = new PipEnterAnimator(mContext, mPipSurfaceTransactionHelper,
                pipLeash,
                startTransaction, finishTransaction, destinationBounds, delta);
        animator.setEnterStartState(pipChange);
        animator.onEnterAnimationUpdate(1.0f /* fraction */, startTransaction);
        startTransaction.apply();

        if (swipePipToHomeOverlay != null) {
            // fadeout the overlay if needed.
            mPipScheduler.startOverlayFadeoutAnimation(swipePipToHomeOverlay,
                    true /* withStartDelay */, () -> {
                SurfaceControl.Transaction tx = new SurfaceControl.Transaction();
                tx.remove(swipePipToHomeOverlay);
                tx.apply();
            });
        }
        finishTransition();
        return true;
    }

    private boolean startBoundsTypeEnterAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        TransitionInfo.Change pipChange = getPipChange(info);
        if (pipChange == null) {
            return false;
        }

        // We expect the PiP activity as a separate change in a config-at-end transition.
        TransitionInfo.Change pipActivityChange = PipTransitionUtils.getDeferConfigActivityChange(
                info, pipChange.getTaskInfo().getToken());
        if (pipActivityChange == null) {
            return false;
        }

        // In multi-activity case, set the parent's leash to invisible while we're animating to PiP
        TransitionInfo.Change parentBeforePip = pipActivityChange.getLastParent() != null
                ? getChangeByToken(info, pipActivityChange.getLastParent()) : null;
        if (parentBeforePip != null && TransitionUtil.isClosingMode(parentBeforePip.getMode())) {
            startTransaction.setAlpha(parentBeforePip.getLeash(), 0);
        }

        mFinishCallback = finishCallback;

        final SurfaceControl pipLeash = getLeash(pipChange);
        final Rect startBounds = pipChange.getStartAbsBounds();
        final Rect endBounds = pipChange.getEndAbsBounds();
        final PictureInPictureParams params = getPipParams(pipChange);
        final Rect adjustedSourceRectHint = getAdjustedSourceRectHint(info, pipChange,
                pipActivityChange);

        final int delta = getFixedRotationDelta(info, pipChange, mPipDisplayLayoutState);
        if (delta != ROTATION_0) {
            // Update transition target changes in place to prepare for fixed rotation.
            updatePipChangesForFixedRotation(info, pipChange, pipActivityChange);
        }

        PipEnterAnimator animator = new PipEnterAnimator(mContext, mPipSurfaceTransactionHelper,
                pipLeash,
                startTransaction, finishTransaction, endBounds, delta);
        if (PipBoundsAlgorithm.getValidSourceHintRect(params, startBounds, endBounds) == null) {
            // If app provided src-rect-hint is invalid, use app icon overlay.
            animator.setAppIconContentOverlay(
                    mContext, startBounds, endBounds, pipChange.getTaskInfo().topActivityInfo,
                    mPipBoundsState.getLauncherState().getAppIconSizePx());
        }

        // Update the src-rect-hint in params in place, to set up initial animator transform.
        params.copyOnlySet(new PictureInPictureParams.Builder()
                .setSourceRectHint(adjustedSourceRectHint).build());

        // Config-at-end transitions need to have their activities transformed before starting
        // the animation; this makes the buffer seem like it's been updated to final size.
        prepareConfigAtEndActivity(startTransaction, finishTransaction, pipChange,
                pipActivityChange);

        animator.setAnimationStartCallback(() -> animator.setEnterStartState(pipChange));
        animator.setAnimationEndCallback(() -> {
            if (animator.getContentOverlayLeash() != null) {
                mPipScheduler.startOverlayFadeoutAnimation(animator.getContentOverlayLeash(),
                        true /* withStartDelay */, animator::clearAppIconOverlay);
            }
            finishTransition();
        });
        cacheAndStartTransitionAnimator(animator);
        return true;
    }

    private void updatePipChangesForFixedRotation(TransitionInfo info,
            TransitionInfo.Change outPipTaskChange,
            TransitionInfo.Change outPipActivityChange) {
        final TransitionInfo.Change fixedRotationChange = findFixedRotationChange(info);
        final Rect endBounds = outPipTaskChange.getEndAbsBounds();
        final Rect endActivityBounds = outPipActivityChange.getEndAbsBounds();
        int startRotation = outPipTaskChange.getStartRotation();
        int endRotation = fixedRotationChange != null
                ? fixedRotationChange.getEndFixedRotation() : mPipDisplayLayoutState.getRotation();

        if (startRotation == endRotation) {
            return;
        }

        // This is used by display change listeners to respond properly to fixed rotation.
        mPipTransitionState.setInFixedRotation(true);

        // Cache the task to activity offset to potentially restore later.
        Point activityEndOffset = new Point(endActivityBounds.left - endBounds.left,
                endActivityBounds.top - endBounds.top);

        // If we are running a fixed rotation bounds enter PiP animation,
        // then update the display layout rotation, and recalculate the end rotation bounds.
        // Update the endBounds in place, so that the PiP change is up-to-date.
        mPipDisplayLayoutState.rotateTo(endRotation);
        float snapFraction = mPipBoundsAlgorithm.getSnapFraction(
                mPipBoundsAlgorithm.getEntryDestinationBounds());
        mPipBoundsAlgorithm.applySnapFraction(endBounds, snapFraction);
        mPipBoundsState.setBounds(endBounds);

        // Display bounds were already updated to represent the final orientation,
        // so we just need to readjust the origin, and perform rotation about (0, 0).
        boolean isClockwise = (endRotation - startRotation) == -ROTATION_270;
        Rect displayBounds = mPipDisplayLayoutState.getDisplayBounds();
        int originTranslateX = isClockwise ? 0 : -displayBounds.width();
        int originTranslateY = isClockwise ? -displayBounds.height() : 0;
        endBounds.offset(originTranslateX, originTranslateY);

        // Update the activity end bounds in place as well, as this is used for transform
        // calculation later.
        endActivityBounds.offsetTo(endBounds.left + activityEndOffset.x,
                endBounds.top + activityEndOffset.y);
    }

    private boolean startAlphaTypeEnterAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        TransitionInfo.Change pipChange = getPipChange(info);
        if (pipChange == null) {
            return false;
        }
        mFinishCallback = finishCallback;

        final Rect destinationBounds = pipChange.getEndAbsBounds();
        if (pipChange.getEndRotation() != ROTATION_UNDEFINED
                && pipChange.getStartRotation() != pipChange.getEndRotation()) {
            // If we are playing an enter PiP animation with display change collected together
            // in the same transition, then PipController#onDisplayChange() must have already
            // updated the PiP bounds state to reflect the final desired destination bounds.
            // This might not be in the WM state yet as PiP task token might have been null then.
            // WM state will be updated via a follow-up bounds change transition after.
            destinationBounds.set(mPipBoundsState.getBounds());
        }

        final SurfaceControl pipLeash = mPipTransitionState.getPinnedTaskLeash();
        Preconditions.checkNotNull(pipLeash, "Leash is null for alpha transition.");

        // Note that fixed rotation is different from the same transition display change rotation;
        // with fixed rotation, we expect a follow-up async rotation transition after this one.
        final int delta = getFixedRotationDelta(info, pipChange, mPipDisplayLayoutState);
        if (delta != ROTATION_0) {
            updatePipChangesForFixedRotation(info, pipChange,
                    // We don't have an activity change to animate in legacy enter,
                    // so just use a placeholder one as the outPipActivityChange.
                    new TransitionInfo.Change(null /* container */, new SurfaceControl()));
        }
        startTransaction.setWindowCrop(pipLeash,
                destinationBounds.width(), destinationBounds.height());
        if (delta != ROTATION_0) {
            // In a fixed rotation case, rotate PiP leash in the old orientation to its final
            // position, but keep the bounds visually invariant until async rotation changes
            // the display rotation after
            int normalizedRotation = delta;
            if (normalizedRotation == ROTATION_270) {
                normalizedRotation = -ROTATION_90;
            }
            Matrix transformTensor = new Matrix();
            final float[] matrixTmp = new float[9];
            transformTensor.setTranslate(destinationBounds.left, destinationBounds.top);
            transformTensor.postRotate(-normalizedRotation * 90f);

            startTransaction.setMatrix(pipLeash, transformTensor, matrixTmp);
            finishTransaction.setMatrix(pipLeash, transformTensor, matrixTmp);
        } else {
            startTransaction.setPosition(pipLeash, destinationBounds.left, destinationBounds.top);
            finishTransaction.setPosition(pipLeash, destinationBounds.left, destinationBounds.top);
        }

        PipAlphaAnimator animator = new PipAlphaAnimator(mContext, mPipSurfaceTransactionHelper,
                pipLeash, startTransaction,
                finishTransaction, PipAlphaAnimator.FADE_IN);
        // This should update the pip transition state accordingly after we stop playing.
        animator.setAnimationEndCallback(this::finishTransition);
        cacheAndStartTransitionAnimator(animator);
        return true;
    }

    private boolean startRemoveAnimation(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        TransitionInfo.Change pipChange = getChangeByToken(info,
                mPipTransitionState.getPipTaskToken());
        mFinishCallback = finishCallback;

        if (isPipClosing(info)) {
            // If PiP is removed via a close (e.g. finishing of the activity), then
            // clear out the PiP cache related to that activity component (e.g. reentry state).
            mPipBoundsState.setLastPipComponentName(null /* lastPipComponentName */);
        }

        finishTransaction.setAlpha(pipChange.getLeash(), 0f);
        if (mPendingRemoveWithFadeout) {
            PipAlphaAnimator animator = new PipAlphaAnimator(mContext, mPipSurfaceTransactionHelper,
                    pipChange.getLeash(),
                    startTransaction, finishTransaction, PipAlphaAnimator.FADE_OUT);
            animator.setAnimationEndCallback(this::finishTransition);
            animator.start();
        } else {
            // Jumpcut to a faded-out PiP if no fadeout animation was requested.
            startTransaction.setAlpha(pipChange.getLeash(), 0f);
            startTransaction.apply();
            finishTransition();
        }
        return true;
    }

    //
    // Various helpers to resolve transition requests and infos
    //

    @NonNull
    private Rect getAdjustedSourceRectHint(@NonNull TransitionInfo info,
            @NonNull TransitionInfo.Change pipTaskChange,
            @NonNull TransitionInfo.Change pipActivityChange) {
        final Rect startBounds = pipTaskChange.getStartAbsBounds();
        final Rect endBounds = pipTaskChange.getEndAbsBounds();
        final PictureInPictureParams params = pipTaskChange.getTaskInfo().pictureInPictureParams;

        // Get the source-rect-hint provided by the app and check its validity; null if invalid.
        final Rect sourceRectHint = PipBoundsAlgorithm.getValidSourceHintRect(params, startBounds,
                endBounds);

        final Rect adjustedSourceRectHint = new Rect();
        if (sourceRectHint != null) {
            adjustedSourceRectHint.set(sourceRectHint);
            // If multi-activity PiP, use the parent task before PiP to retrieve display cutouts;
            // then, offset the valid app provided source rect hint by the cutout insets.
            // For single-activity PiP, just use the pinned task to get the cutouts instead.
            TransitionInfo.Change parentBeforePip = pipActivityChange.getLastParent() != null
                    ? getChangeByToken(info, pipActivityChange.getLastParent()) : null;
            Rect cutoutInsets = parentBeforePip != null
                    ? parentBeforePip.getTaskInfo().displayCutoutInsets
                    : pipTaskChange.getTaskInfo().displayCutoutInsets;
            if (cutoutInsets != null && getFixedRotationDelta(info, pipTaskChange,
                    mPipDisplayLayoutState) == ROTATION_90) {
                adjustedSourceRectHint.offset(cutoutInsets.left, cutoutInsets.top);
            }
            if (mPipDesktopState.isDesktopWindowingPipEnabled()) {
                adjustedSourceRectHint.offset(-pipActivityChange.getStartAbsBounds().left,
                        -pipActivityChange.getStartAbsBounds().top);
            }
        } else {
            // For non-valid app provided src-rect-hint, calculate one to crop into during
            // app icon overlay animation.
            float aspectRatio = mPipBoundsAlgorithm.getAspectRatioOrDefault(params);
            adjustedSourceRectHint.set(
                    PipUtils.getPseudoSourceRectHint(startBounds, aspectRatio));
        }
        return adjustedSourceRectHint;
    }

    private void prepareOtherTargetTransforms(TransitionInfo info,
            SurfaceControl.Transaction startTransaction,
            SurfaceControl.Transaction finishTransaction) {
        // For opening type transitions, if there is a change of mode TO_FRONT/OPEN,
        // make sure that change has alpha of 1f, since it's init state might be set to alpha=0f
        // by the Transitions framework to simplify Task opening transitions.
        if (TransitionUtil.isOpeningType(info.getType())) {
            for (TransitionInfo.Change change : info.getChanges()) {
                if (change.getLeash() == null) continue;
                if (TransitionUtil.isOpeningMode(change.getMode())) {
                    startTransaction.setAlpha(change.getLeash(), 1f);
                }
            }
        }
    }

    private WindowContainerTransaction getEnterPipTransaction(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo.PipChange pipChange) {
        // cache the original task token to check for multi-activity case later
        final ActivityManager.RunningTaskInfo pipTask = pipChange.getTaskInfo();
        PictureInPictureParams pipParams = pipTask.pictureInPictureParams;
        mPipTaskListener.setPictureInPictureParams(pipParams);
        mPipBoundsState.setBoundsStateForEntry(pipTask.topActivity, pipTask.topActivityInfo,
                pipParams, mPipBoundsAlgorithm);

        // If PiP is enabled on Connected Displays, update PipDisplayLayoutState to have the correct
        // display info that PiP is entering in.
        if (mPipDesktopState.isConnectedDisplaysPipEnabled()
                && pipTask.displayId != mPipDisplayLayoutState.getDisplayId()) {
            final DisplayLayout displayLayout = mDisplayController.getDisplayLayout(
                    pipTask.displayId);
            if (displayLayout != null) {
                mPipDisplayLayoutState.setDisplayId(pipTask.displayId);
                mPipDisplayLayoutState.setDisplayLayout(displayLayout);
            }
        }

        if (!mPipTransitionState.isInSwipePipToHomeTransition()) {
            // Update the size spec in case aspect ratio is invariant, but display has changed
            // since the last PiP session, or this is the first PiP session altogether.
            // Skip the update if in swipe PiP to home, as this has already been done.
            mPipBoundsState.updateMinMaxSize(mPipBoundsState.getAspectRatio());
        }

        // calculate the entry bounds and notify core to move task to pinned with final bounds
        final Rect entryBounds = mPipBoundsAlgorithm.getEntryDestinationBounds();
        mPipBoundsState.setBounds(entryBounds);

        // Operate on the TF token in case we are dealing with AE case; this should avoid marking
        // activities in other TFs as config-at-end.
        WindowContainerToken token = pipChange.getTaskFragmentToken();
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.movePipActivityToPinnedRootTask(token, entryBounds);
        wct.deferConfigToTransitionEnd(token);
        return wct;
    }

    private boolean isAutoEnterInButtonNavigation(@NonNull TransitionRequestInfo requestInfo) {
        final ActivityManager.RunningTaskInfo pipTask = requestInfo.getPipChange() != null
                ? requestInfo.getPipChange().getTaskInfo() : null;
        if (pipTask == null) {
            return false;
        }
        if (pipTask.pictureInPictureParams == null) {
            return false;
        }

        // Since opening a new task while in Desktop Mode always first open in Fullscreen
        // until DesktopMode Shell code resolves it to Freeform, PipTransition will get a
        // possibility to handle it also. In this case return false to not have it enter PiP.
        if (mPipDesktopState.isPipInDesktopMode()) {
            return false;
        }

        // Assuming auto-enter is enabled and pipTask is non-null, the TRANSIT_OPEN request type
        // implies that we are entering PiP in button navigation mode. This is guaranteed by
        // TaskFragment#startPausing()` in Core which wouldn't get called in gesture nav.
        return requestInfo.getType() == TRANSIT_OPEN
                && pipTask.pictureInPictureParams.isAutoEnterEnabled();
    }

    private boolean isEnterPictureInPictureModeRequest(@NonNull TransitionRequestInfo requestInfo) {
        return requestInfo.getType() == TRANSIT_PIP;
    }

    private boolean isLegacyEnter(@NonNull TransitionInfo info) {
        TransitionInfo.Change pipChange = getPipChange(info);
        if (pipChange != null) {
            if (mEnterAnimationType == ANIM_TYPE_ALPHA) {
                // If enter animation type is force overridden to an alpha type,
                // treat this as legacy, and reset the animation type to default (i.e. bounds type).
                setEnterAnimationType(ANIM_TYPE_BOUNDS);
                return true;
            }

            // #getEnterPipTransaction() always attempts to mark PiP activity as config-at-end one.
            // However, the activity will only actually be marked config-at-end by Core if it is
            // both isVisible and isVisibleRequested, which is when we can't run bounds animation.
            //
            // So we can use the absence of a config-at-end activity as a signal that we should run
            // a legacy-enter PiP animation instead.
            return TransitionUtil.isOpeningMode(pipChange.getMode())
                    && PipTransitionUtils.getDeferConfigActivityChange(
                            info, pipChange.getContainer()) == null;
        }
        return false;
    }

    private boolean isRemovePipTransition(@NonNull TransitionInfo info) {
        if (mPipTransitionState.getPipTaskToken() == null) {
            // PiP removal makes sense if enter-PiP has cached a valid pinned task token.
            return false;
        }
        TransitionInfo.Change pipChange = info.getChange(mPipTransitionState.getPipTaskToken());
        if (pipChange == null) {
            // Search for the PiP change by token since the windowing mode might be FULLSCREEN now.
            return false;
        }

        boolean isPipMovedToBack = info.getType() == TRANSIT_TO_BACK
                && pipChange.getMode() == TRANSIT_TO_BACK;
        // If PiP is dismissed by user (i.e. via dismiss button in PiP menu)
        boolean isPipDismissed = info.getType() == TRANSIT_REMOVE_PIP
                && pipChange.getMode() == TRANSIT_TO_BACK;
        // PiP is being removed if the pinned task is either moved to back, closed, or dismissed.
        return isPipMovedToBack || isPipClosing(info) || isPipDismissed;
    }

    private boolean isPipClosing(@NonNull TransitionInfo info) {
        if (mPipTransitionState.getPipTaskToken() == null) {
            // PiP removal makes sense if enter-PiP has cached a valid pinned task token.
            return false;
        }
        TransitionInfo.Change pipChange = info.getChange(mPipTransitionState.getPipTaskToken());
        TransitionInfo.Change pipActivityChange = info.getChanges().stream().filter(change ->
                change.getTaskInfo() == null && change.getParent() != null
                        && change.getParent() == mPipTransitionState.getPipTaskToken())
                .findFirst().orElse(null);

        boolean isPipTaskClosed = pipChange != null
                && pipChange.getMode() == TRANSIT_CLOSE;
        boolean isPipActivityClosed = pipActivityChange != null
                && pipActivityChange.getMode() == TRANSIT_CLOSE;
        return isPipTaskClosed || isPipActivityClosed;
    }

    private void prepareConfigAtEndActivity(@NonNull SurfaceControl.Transaction startTx,
            @NonNull SurfaceControl.Transaction finishTx,
            @NonNull TransitionInfo.Change pipChange,
            @NonNull TransitionInfo.Change pipActivityChange) {
        PointF initActivityScale = new PointF();
        PointF initActivityPos = new PointF();
        PipUtils.calcEndTransform(pipActivityChange, pipChange, initActivityScale,
                initActivityPos);
        if (pipActivityChange.getLeash() != null) {
            startTx.setCrop(pipActivityChange.getLeash(), null);
            startTx.setScale(pipActivityChange.getLeash(), initActivityScale.x,
                    initActivityScale.y);
            startTx.setPosition(pipActivityChange.getLeash(), initActivityPos.x,
                    initActivityPos.y);

            finishTx.setCrop(pipActivityChange.getLeash(), null);
            finishTx.setScale(pipActivityChange.getLeash(), initActivityScale.x,
                    initActivityScale.y);
            finishTx.setPosition(pipActivityChange.getLeash(), initActivityPos.x,
                    initActivityPos.y);
        }
    }

    /**
     * This is called by [startAnimation] when a enter PiP transition is received, and before
     * mPipTransitionState is updated with the incoming PiP task info. If a change is found
     * for the previous PiP with change TO_BACK, the previous PiP was dismissed by Core. We want to
     * update the state in PipTransitionState so everything is cleaned up and also ensure the
     * previous PiP is no longer visible.
     */
    private void cleanUpPrevPipIfPresent(@NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTx,
            @NonNull SurfaceControl.Transaction finishTx) {
        TransitionInfo.Change previousPipChange = null;
        TaskInfo previousPipTaskInfo = mPipTransitionState.getPipTaskInfo();
        if (previousPipTaskInfo == null) {
            return;
        }

        for (TransitionInfo.Change change : info.getChanges()) {
            if (change.getTaskInfo() != null
                    && change.getTaskInfo().getTaskId() == previousPipTaskInfo.getTaskId()
                    && TransitionUtil.isClosingMode(change.getMode())) {
                previousPipChange = change;
                break;
            }
        }

        if (previousPipChange == null) {
            return;
        }

        ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                "cleanUpPrevPipIfPresent: Previous PiP with taskId=%d found with closing mode, "
                        + "clean up PiP state",
                previousPipTaskInfo.getTaskId());

        mPipTransitionState.setState(PipTransitionState.EXITING_PIP);
        mPipTransitionState.setState(PipTransitionState.EXITED_PIP);
        startTx.setAlpha(previousPipChange.getLeash(), 0);
        finishTx.setAlpha(previousPipChange.getLeash(), 0);
    }

    /**
     * Sets the type of animation to run upon entering PiP.
     *
     * By default, {@link PipTransition} uses various signals from Transitions to figure out
     * the animation type. But we should provide the ability to override this animation type to
     * mixed handlers, for instance, as they can split up and modify incoming {@link TransitionInfo}
     * to pass it onto multiple {@link Transitions.TransitionHandler}s.
     */
    @Override
    public void setEnterAnimationType(@AnimationType int type) {
        mEnterAnimationType = type;
    }

    void cacheAndStartTransitionAnimator(@NonNull ValueAnimator animator) {
        mTransitionAnimator = animator;
        mTransitionAnimator.start();
    }

    //
    // Miscellaneous callbacks and listeners
    //

    @Override
    public void finishTransition() {
        final int currentState = mPipTransitionState.getState();
        int nextState = PipTransitionState.UNDEFINED;
        switch (currentState) {
            case PipTransitionState.ENTERING_PIP:
                nextState = PipTransitionState.ENTERED_PIP;
                break;
            case PipTransitionState.CHANGING_PIP_BOUNDS:
                nextState = PipTransitionState.CHANGED_PIP_BOUNDS;
                break;
            case PipTransitionState.EXITING_PIP:
                nextState = PipTransitionState.EXITED_PIP;
                break;
        }

        if (nextState == PipTransitionState.UNDEFINED) {
            Log.wtf(TAG, String.format("""
                        PipTransitionState resolved to an undefined state in finishTransition().
                        callers=%s""", Debug.getCallers(4)));
        }

        mPipTransitionState.setState(nextState);

        if (mFinishCallback != null) {
            // Need to unset mFinishCallback first because onTransitionFinished can re-enter this
            // handler if there is a pending PiP animation.
            final Transitions.TransitionFinishCallback finishCallback = mFinishCallback;
            mFinishCallback = null;
            finishCallback.onTransitionFinished(null /* finishWct */);
        }
    }

    @Override
    public void onTransitionAborted() {
        final int currentState = mPipTransitionState.getState();
        int nextState = PipTransitionState.UNDEFINED;
        switch (currentState) {
            case PipTransitionState.SCHEDULED_BOUNDS_CHANGE:
                nextState = PipTransitionState.CHANGED_PIP_BOUNDS;
                break;
            case PipTransitionState.SCHEDULED_ENTER_PIP:
                if (mPipTransitionState.getPipTaskToken() != null) {
                    nextState = PipTransitionState.ENTERED_PIP;
                } else {
                    nextState = PipTransitionState.EXITED_PIP;
                }
                break;
        }

        if (nextState == PipTransitionState.UNDEFINED) {
            Log.wtf(TAG, String.format("""
                        PipTransitionState resolved to an undefined state in abortTransition().
                        callers=%s""", Debug.getCallers(4)));
        }

        mPipTransitionState.setState(nextState);
    }

    @Override
    public void onPipTransitionStateChanged(@PipTransitionState.TransitionState int oldState,
            @PipTransitionState.TransitionState int newState, @Nullable Bundle extra) {
        switch (newState) {
            case PipTransitionState.ENTERING_PIP:
                Preconditions.checkState(extra != null,
                        "No extra bundle for " + mPipTransitionState);

                mPipTransitionState.setPinnedTaskLeash(extra.getParcelable(
                        PIP_TASK_LEASH, SurfaceControl.class));
                mPipTransitionState.setPipTaskInfo(extra.getParcelable(
                        PIP_TASK_INFO, TaskInfo.class));
                boolean hasValidTokenAndLeash = mPipTransitionState.getPipTaskToken() != null
                        && mPipTransitionState.getPinnedTaskLeash() != null;

                Preconditions.checkState(hasValidTokenAndLeash,
                        "Unexpected bundle for " + mPipTransitionState);
                break;
            case PipTransitionState.EXITED_PIP:
                mPipTransitionState.setPinnedTaskLeash(null);
                mPipTransitionState.setPipTaskInfo(null);
                mPendingRemoveWithFadeout = false;
                break;
        }
    }

    @Override
    public boolean isPackageActiveInPip(@Nullable String packageName) {
        final TaskInfo inPipTask = mPipTransitionState.getPipTaskInfo();
        return packageName != null && inPipTask != null && mPipTransitionState.isInPip()
                && packageName.equals(ComponentUtils.getPackageName(inPipTask.baseIntent));
    }

    @Override
    public boolean isTaskActiveInPip(int taskId) {
        final TaskInfo inPipTask = mPipTransitionState.getPipTaskInfo();
        return inPipTask != null && mPipTransitionState.isInPip()
                && taskId == inPipTask.getTaskId();
    }
}

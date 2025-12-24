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
package com.android.wm.shell.pip2.phone;

import static com.android.wm.shell.pip2.phone.PipTransition.ANIMATING_BOUNDS_CHANGE_DURATION;
import static com.android.wm.shell.pip2.phone.PipTransition.PIP_DESTINATION_BOUNDS;

import android.annotation.Nullable;
import android.app.TaskInfo;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Trace;
import android.util.ArrayMap;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.MultiDisplayDragMoveBoundsCalculator;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip2.animation.PipResizeAnimator;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

/**
 * Handler for moving PiP window to another display when the device is connected to external
 * display(s) in extended mode.
 */
public class PipDisplayTransferHandler implements
        PipTransitionState.PipTransitionStateChangedListener {

    private static final String TAG = "PipDisplayTransferHandler";
    static final String ORIGIN_DISPLAY_ID_KEY = "origin_display_id";
    static final String TARGET_DISPLAY_ID_KEY = "target_display_id";

    private final PipBoundsState mPipBoundsState;
    private PipSurfaceTransactionHelper.SurfaceControlTransactionFactory
            mSurfaceControlTransactionFactory;
    private final RootTaskDisplayAreaOrganizer mRootTaskDisplayAreaOrganizer;
    private PipSurfaceTransactionHelper mPipSurfaceTransactionHelper;
    private final DisplayController mDisplayController;
    private final PipTransitionState mPipTransitionState;
    private final PipScheduler mPipScheduler;
    private final Context mContext;
    private final PipDisplayLayoutState mPipDisplayLayoutState;
    private final PipBoundsAlgorithm mPipBoundsAlgorithm;

    @VisibleForTesting boolean mWaitingForDisplayTransfer;
    @VisibleForTesting
    ArrayMap<Integer, SurfaceControl> mOnDragMirrorPerDisplayId = new ArrayMap<>();
    @VisibleForTesting int mTargetDisplayId;
    private PipResizeAnimatorSupplier mPipResizeAnimatorSupplier;
    private boolean mIsMirrorShown;
    public PipDisplayTransferHandler(Context context, PipTransitionState pipTransitionState,
            PipScheduler pipScheduler, RootTaskDisplayAreaOrganizer rootTaskDisplayAreaOrganizer,
            PipBoundsState pipBoundsState, DisplayController displayController,
            PipDisplayLayoutState pipDisplayLayoutState, PipBoundsAlgorithm pipBoundsAlgorithm,
            PipSurfaceTransactionHelper pipSurfaceTransactionHelper) {
        mContext = context;
        mPipTransitionState = pipTransitionState;
        mPipTransitionState.addPipTransitionStateChangedListener(this);
        mPipScheduler = pipScheduler;
        mSurfaceControlTransactionFactory =
                new PipSurfaceTransactionHelper.VsyncSurfaceControlTransactionFactory();
        mRootTaskDisplayAreaOrganizer = rootTaskDisplayAreaOrganizer;
        mPipSurfaceTransactionHelper = pipSurfaceTransactionHelper;
        mPipBoundsState = pipBoundsState;
        mDisplayController = displayController;
        mPipDisplayLayoutState = pipDisplayLayoutState;
        mPipBoundsAlgorithm = pipBoundsAlgorithm;
        mPipResizeAnimatorSupplier = PipResizeAnimator::new;
    }

    void scheduleMovePipToDisplay(int originDisplayId, int targetDisplayId,
            Rect boundsOnRelease) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s scheduleMovePipToDisplay from=%d to=%d", TAG, originDisplayId, targetDisplayId);
        mPipDisplayLayoutState.setDisplayId(targetDisplayId);
        mPipDisplayLayoutState.setDisplayLayout(
                mDisplayController.getDisplayLayout(targetDisplayId));
        mPipBoundsState.updateMinMaxSize(mPipBoundsState.getAspectRatio());

        // Set bounds to the bounds on drag release so that we can use this as the origin bounds
        // during animation to snap to the display's edge.
        mPipBoundsState.setBounds(boundsOnRelease);

        // Snap to movement bounds edge of the target display ID on drag release.
        // The target display layout needs to be supplied since this happens before the PiP
        // is released and the display ID and layout are updated.
        mPipBoundsAlgorithm.snapToMovementBoundsEdge(boundsOnRelease,
                mDisplayController.getDisplayLayout(targetDisplayId));
        snapBoundsWithinMinMaxSize(boundsOnRelease);

        Bundle extra = new Bundle();
        extra.putInt(ORIGIN_DISPLAY_ID_KEY, originDisplayId);
        extra.putInt(TARGET_DISPLAY_ID_KEY, targetDisplayId);
        extra.putParcelable(PIP_DESTINATION_BOUNDS, boundsOnRelease);

        mPipTransitionState.setState(PipTransitionState.SCHEDULED_BOUNDS_CHANGE, extra);
    }

    /**
     * Restricts {@param bounds} to the allowed min/max size constraints and snaps bounds to the
     * correct edge based on the snap fraction.
     */
    private void snapBoundsWithinMinMaxSize(Rect bounds) {
        final float snapFraction = mPipBoundsAlgorithm.getSnapAlgorithm().getSnapFraction(
                bounds,
                mPipBoundsAlgorithm.getMovementBounds(bounds),
                mPipBoundsState.getStashedState());

        final Point minSize = mPipBoundsState.getMinSize();
        final Point maxSize = mPipBoundsState.getMaxSize();
        int newWidth = bounds.width();
        int newHeight = bounds.height();

        if (bounds.width() < minSize.x || bounds.height() < minSize.y) {
            newWidth = minSize.x;
            newHeight = minSize.y;
        } else if (bounds.width() > maxSize.x || bounds.height() > maxSize.y) {
            newWidth = maxSize.x;
            newHeight = maxSize.y;
        }

        bounds.set(0, 0, newWidth, newHeight);

        mPipBoundsAlgorithm.getSnapAlgorithm().applySnapFraction(bounds,
                mPipBoundsAlgorithm.getMovementBounds(bounds), snapFraction,
                mPipBoundsState.getStashedState(), mPipBoundsState.getStashOffset(),
                mPipDisplayLayoutState.getDisplayBounds(),
                mPipDisplayLayoutState.getDisplayLayout().stableInsets());
    }

    @Override
    public void onPipTransitionStateChanged(@PipTransitionState.TransitionState int oldState,
            @PipTransitionState.TransitionState int newState, @Nullable Bundle extra) {
        switch (newState) {
            case PipTransitionState.SCHEDULED_BOUNDS_CHANGE:
                if (!extra.containsKey(ORIGIN_DISPLAY_ID_KEY) || !extra.containsKey(
                        TARGET_DISPLAY_ID_KEY)) {
                    break;
                }

                final int originDisplayId = extra.getInt(ORIGIN_DISPLAY_ID_KEY);
                mTargetDisplayId = extra.getInt(TARGET_DISPLAY_ID_KEY);
                if (originDisplayId == mTargetDisplayId) {
                    break;
                }

                mWaitingForDisplayTransfer = true;
                mPipScheduler.scheduleMoveToDisplay(mTargetDisplayId,
                        extra.getParcelable(PIP_DESTINATION_BOUNDS, Rect.class));
                break;
            case PipTransitionState.CHANGING_PIP_BOUNDS:
                if (!mWaitingForDisplayTransfer) {
                    break;
                }
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s Animating PiP display change to=%d", TAG, mTargetDisplayId);

                final SurfaceControl pipLeash = mPipTransitionState.getPinnedTaskLeash();
                final TaskInfo pipTaskInfo = mPipTransitionState.getPipTaskInfo();
                final TaskInfo pipCandidateTaskInfo = mPipTransitionState.getPipCandidateTaskInfo();
                final int duration = extra.getInt(ANIMATING_BOUNDS_CHANGE_DURATION,
                        PipTransition.BOUNDS_CHANGE_JUMPCUT_DURATION);
                final Transaction startTx = extra.getParcelable(
                        PipTransition.PIP_START_TX, Transaction.class);
                final Transaction finishTx = extra.getParcelable(
                        PipTransition.PIP_FINISH_TX, Transaction.class);
                final Rect pipBounds = extra.getParcelable(
                        PIP_DESTINATION_BOUNDS, Rect.class);

                Trace.instant(Trace.TRACE_TAG_WINDOW_MANAGER,
                        "PipDisplayTransferHandler#changingPipBounds");

                mPipSurfaceTransactionHelper.round(startTx, pipLeash, true).shadow(startTx,
                        pipLeash, true /* applyShadowRadius */);
                // Set state to exiting and exited PiP to unregister input consumer on the current
                // display.
                // TODO(b/414864788): Refactor transition states setting during display transfer
                mPipTransitionState.setState(PipTransitionState.EXITING_PIP);
                mPipTransitionState.setState(PipTransitionState.EXITED_PIP);

                // Set PiP task states to make sure they're not null after we exited PiP
                mPipTransitionState.setPinnedTaskLeash(pipLeash);
                mPipTransitionState.setPipTaskInfo(pipTaskInfo);
                mPipTransitionState.setPipCandidateTaskInfo(pipCandidateTaskInfo);

                final PipResizeAnimator animator = mPipResizeAnimatorSupplier.get(mContext,
                        mPipSurfaceTransactionHelper, pipLeash, startTx, finishTx,
                        pipBounds, mPipBoundsState.getBounds(), pipBounds,
                        duration, 0);

                animator.setAnimationEndCallback(() -> {
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                            "%s Finished animating PiP display change to=%d", TAG,
                            mTargetDisplayId);
                    mPipScheduler.scheduleFinishPipBoundsChange(pipBounds);
                    // Set state to ENTERED_PIP to register input consumer on the target display
                    mPipTransitionState.setState(PipTransitionState.ENTERED_PIP);
                    mPipBoundsState.setHasUserResizedPip(true);
                    mWaitingForDisplayTransfer = false;
                });
                animator.start();
                break;
            case PipTransitionState.EXITED_PIP:
                ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                        "%s Exited PiP. Removing drag mirrors", TAG);
                removeMirrors();
                break;
        }
    }

    /**
     * Show a drag indicator mirror on each connected display according to the current pointer
     * position.
     *
     * @param globalDpPipBounds         the PiP bounds in display topology-aware global DP
     * @param focusedDisplayId          the display ID where the cursor is currently on
     */
    public void showDragMirrorOnConnectedDisplays(RectF globalDpPipBounds, int focusedDisplayId) {
        final Transaction transaction = mSurfaceControlTransactionFactory.getTransaction();
        mIsMirrorShown = false;
        // If PiP is on a display that's not in the topology, don't show drag mirrors on other
        // displays because it can't be dragged over.
        if (!mDisplayController.isDisplayInTopology(focusedDisplayId)) return;

        // Iterate through each connected display ID to ensure partial PiP bounds are shown on
        // all corresponding displays while dragging
        for (int displayId : mRootTaskDisplayAreaOrganizer.getDisplayIds()) {
            DisplayLayout displayLayout = mDisplayController.getDisplayLayout(displayId);
            if (displayLayout == null) continue;

            // Hide mirror(s) if it shouldn't be shown on this display.
            if (!canShowMirrorForDisplay(displayId, focusedDisplayId, displayLayout,
                    globalDpPipBounds)) {
                if (mOnDragMirrorPerDisplayId.containsKey(displayId)) {
                    SurfaceControl pipMirror = mOnDragMirrorPerDisplayId.get(displayId);
                    transaction.hide(pipMirror);
                }
                continue;
            }

            // Create a mirror for the current display if it hasn't been created yet
            SurfaceControl mirror;
            if (!mOnDragMirrorPerDisplayId.containsKey(displayId)) {
                mirror = SurfaceControl.mirrorSurface(mPipTransitionState.getPinnedTaskLeash());
                mOnDragMirrorPerDisplayId.put(displayId, mirror);
            } else {
                mirror = mOnDragMirrorPerDisplayId.get(displayId);
            }

            // Convert the PiP bounds in dp to px based on the current display layout
            final Rect boundsOnCurrentDisplay =
                    MultiDisplayDragMoveBoundsCalculator.convertGlobalDpToLocalPxForRect(
                            globalDpPipBounds, displayLayout);
            mPipSurfaceTransactionHelper.setPipTransformations(mirror, transaction,
                    mPipBoundsState.getBounds(), boundsOnCurrentDisplay,
                    /* degrees= */ 0).setMirrorTransformations(transaction, mirror);
            mRootTaskDisplayAreaOrganizer.reparentToDisplayArea(displayId, mirror, transaction);
            mIsMirrorShown = true;
        }
        transaction.apply();
    }

    /**
     * Drag mirrors can only be shown on non-focused display(s) in the topology that intersect
     * with the PiP global DP bounds.
     *
     * @param displayId         the given display ID on which drag mirror should be shown
     * @param focusedDisplayId  the display ID of where the PiP window is focused on
     *                          (where the pointer is)
     * @param displayLayout     the display layout of the given display ID
     * @param globalDpBounds    the PiP bounds in global DP
     * @return whether drag mirror can be shown on a given display ID.
     */
    private boolean canShowMirrorForDisplay(int displayId, int focusedDisplayId,
            DisplayLayout displayLayout, RectF globalDpBounds) {
        boolean pipBoundsIntersectDisplay = RectF.intersects(globalDpBounds,
                displayLayout.globalBoundsDp());

        return displayId != focusedDisplayId && pipBoundsIntersectDisplay
                && mDisplayController.isDisplayInTopology(displayId);
    }

    /**
     * Remove all drag indicator mirrors from each connected display.
     */
    public void removeMirrors() {
        final Transaction transaction = mSurfaceControlTransactionFactory.getTransaction();
        for (SurfaceControl mirror : mOnDragMirrorPerDisplayId.values()) {
            transaction.remove(mirror);
        }
        transaction.apply();
        mOnDragMirrorPerDisplayId.clear();
    }

    @VisibleForTesting
    void setSurfaceControlTransactionFactory(
            @NonNull PipSurfaceTransactionHelper.SurfaceControlTransactionFactory factory) {
        mSurfaceControlTransactionFactory = factory;
    }

    @VisibleForTesting
    void setSurfaceTransactionHelper(PipSurfaceTransactionHelper surfaceTransactionHelper) {
        mPipSurfaceTransactionHelper = surfaceTransactionHelper;
    }

    /**
     * Whether any of the drag mirror(s) are showing on any display other than the primary display.
     */
    boolean isMirrorShown() {
        return mIsMirrorShown;
    }

    @VisibleForTesting
    interface PipResizeAnimatorSupplier {
        PipResizeAnimator get(@NonNull Context context,
                @NonNull PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
                @NonNull SurfaceControl leash,
                @Nullable SurfaceControl.Transaction startTx,
                @Nullable SurfaceControl.Transaction finishTx,
                @NonNull Rect baseBounds,
                @NonNull Rect startBounds,
                @NonNull Rect endBounds,
                int duration,
                float delta);
    }

    @VisibleForTesting
    void setPipResizeAnimatorSupplier(@NonNull PipResizeAnimatorSupplier supplier) {
        mPipResizeAnimatorSupplier = supplier;
    }
}

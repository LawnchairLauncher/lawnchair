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
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
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
            Rect destinationBounds) {
        ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s scheduleMovePipToDisplay from=%d to=%d", TAG, originDisplayId, targetDisplayId);

        Bundle extra = new Bundle();
        extra.putInt(ORIGIN_DISPLAY_ID_KEY, originDisplayId);
        extra.putInt(TARGET_DISPLAY_ID_KEY, targetDisplayId);
        extra.putParcelable(PIP_DESTINATION_BOUNDS, destinationBounds);

        mPipTransitionState.setState(PipTransitionState.SCHEDULED_BOUNDS_CHANGE, extra);
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

                SurfaceControl pipLeash = mPipTransitionState.getPinnedTaskLeash();
                TaskInfo taskInfo = mPipTransitionState.getPipTaskInfo();
                final int duration = extra.getInt(ANIMATING_BOUNDS_CHANGE_DURATION,
                        PipTransition.BOUNDS_CHANGE_JUMPCUT_DURATION);
                final Transaction startTx = extra.getParcelable(
                        PipTransition.PIP_START_TX, Transaction.class);
                final Transaction finishTx = extra.getParcelable(
                        PipTransition.PIP_FINISH_TX, Transaction.class);
                final Rect pipBounds = extra.getParcelable(
                        PIP_DESTINATION_BOUNDS, Rect.class);

                Rect finalBounds = new Rect(pipBounds);
                final DisplayLayout targetDisplayLayout = mDisplayController.getDisplayLayout(
                        mTargetDisplayId);

                mPipDisplayLayoutState.setDisplayId(mTargetDisplayId);
                mPipDisplayLayoutState.setDisplayLayout(targetDisplayLayout);

                // Snap to movement bounds edge of the target display ID on drag release.
                // The target display layout needs to be supplied since this happens before the PiP
                // is released and the display ID and layout are updated.
                mPipBoundsAlgorithm.snapToMovementBoundsEdge(finalBounds, targetDisplayLayout);

                mPipSurfaceTransactionHelper.round(startTx, pipLeash, true).shadow(startTx,
                        pipLeash, true /* applyShadowRadius */);
                // Set state to exiting and exited PiP to unregister input consumer on the current
                // display.
                // TODO(b/414864788): Refactor transition states setting during display transfer
                mPipTransitionState.setState(PipTransitionState.EXITING_PIP);
                mPipTransitionState.setState(PipTransitionState.EXITED_PIP);

                mPipTransitionState.setPinnedTaskLeash(pipLeash);
                mPipTransitionState.setPipTaskInfo(taskInfo);

                final PipResizeAnimator animator = mPipResizeAnimatorSupplier.get(mContext,
                        mPipSurfaceTransactionHelper, pipLeash, startTx, finishTx,
                        pipBounds, pipBounds, finalBounds, duration, 0);

                animator.setAnimationEndCallback(() -> {
                    ProtoLog.v(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                            "%s Finished animating PiP display change to=%d", TAG,
                            mTargetDisplayId);
                    mPipScheduler.scheduleFinishPipBoundsChange(finalBounds);
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
        // Iterate through each connected display ID to ensure partial PiP bounds are shown on
        // all corresponding displays while dragging
        for (int displayId : mRootTaskDisplayAreaOrganizer.getDisplayIds()) {
            DisplayLayout displayLayout = mDisplayController.getDisplayLayout(displayId);
            if (displayLayout == null) continue;

            boolean shouldShowOnDisplay = RectF.intersects(globalDpPipBounds,
                    displayLayout.globalBoundsDp());

            // Hide mirror if it's the currently focused display or if the PiP bounds do not
            // intersect with the boundaries of a given display bounds
            if (displayId == focusedDisplayId || !shouldShowOnDisplay) {
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

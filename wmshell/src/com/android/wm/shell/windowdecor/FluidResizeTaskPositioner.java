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

package com.android.wm.shell.windowdecor;

import static android.view.WindowManager.TRANSIT_CHANGE;

import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.IBinder;
import android.view.Surface;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.transition.Transitions;

import java.util.function.Supplier;

/**
 * A task positioner that resizes/relocates task contents as it is dragged.
 * Utilizes {@link DragPositioningCallbackUtility} to determine new task bounds.
 *
 * This positioner applies the final bounds after a resize or drag using a shell transition in order
 * to utilize the startAnimation callback to set the final task position and crop. In most cases,
 * the transition will be aborted since the final bounds are usually the same bounds set in the
 * final {@link #onDragPositioningMove} call. In this case, the cropping and positioning would be
 * set by {@link WindowDecoration#relayout} due to the final bounds change; however, it is important
 * that we send the final shell transition since we still utilize the {@link #onTransitionConsumed}
 * callback.
 */
class FluidResizeTaskPositioner implements DragPositioningCallback,
        TaskDragResizer, Transitions.TransitionHandler {
    private final ShellTaskOrganizer mTaskOrganizer;
    private final Transitions mTransitions;
    private final WindowDecoration mWindowDecoration;
    private final Supplier<SurfaceControl.Transaction> mTransactionSupplier;
    private DisplayController mDisplayController;
    private DragPositioningCallbackUtility.DragStartListener mDragStartListener;
    private final Rect mStableBounds = new Rect();
    private final Rect mTaskBoundsAtDragStart = new Rect();
    private final PointF mRepositionStartPoint = new PointF();
    private final Rect mRepositionTaskBounds = new Rect();
    private boolean mHasDragResized;
    private boolean mIsResizingOrAnimatingResize;
    private int mCtrlType;
    private IBinder mDragResizeEndTransition;
    @Surface.Rotation private int mRotation;

    FluidResizeTaskPositioner(ShellTaskOrganizer taskOrganizer, Transitions transitions,
            WindowDecoration windowDecoration, DisplayController displayController) {
        this(taskOrganizer, transitions, windowDecoration, displayController,
                dragStartListener -> {}, SurfaceControl.Transaction::new);
    }

    FluidResizeTaskPositioner(ShellTaskOrganizer taskOrganizer,
            Transitions transitions,
            WindowDecoration windowDecoration,
            DisplayController displayController,
            DragPositioningCallbackUtility.DragStartListener dragStartListener,
            Supplier<SurfaceControl.Transaction> supplier) {
        mTaskOrganizer = taskOrganizer;
        mTransitions = transitions;
        mWindowDecoration = windowDecoration;
        mDisplayController = displayController;
        mDragStartListener = dragStartListener;
        mTransactionSupplier = supplier;
    }

    @Override
    public Rect onDragPositioningStart(int ctrlType, float x, float y) {
        mCtrlType = ctrlType;
        mTaskBoundsAtDragStart.set(
                mWindowDecoration.mTaskInfo.configuration.windowConfiguration.getBounds());
        mRepositionStartPoint.set(x, y);
        mDragStartListener.onDragStart(mWindowDecoration.mTaskInfo.taskId);
        if (mCtrlType != CTRL_TYPE_UNDEFINED && !mWindowDecoration.mTaskInfo.isFocused) {
            WindowContainerTransaction wct = new WindowContainerTransaction();
            wct.reorder(mWindowDecoration.mTaskInfo.token, true);
            mTaskOrganizer.applyTransaction(wct);
        }
        mRepositionTaskBounds.set(mTaskBoundsAtDragStart);
        int rotation = mWindowDecoration
                .mTaskInfo.configuration.windowConfiguration.getDisplayRotation();
        if (mStableBounds.isEmpty() || mRotation != rotation) {
            mRotation = rotation;
            mDisplayController.getDisplayLayout(mWindowDecoration.mDisplay.getDisplayId())
                    .getStableBounds(mStableBounds);
        }
        return new Rect(mRepositionTaskBounds);
    }

    @Override
    public Rect onDragPositioningMove(float x, float y) {
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        PointF delta = DragPositioningCallbackUtility.calculateDelta(x, y, mRepositionStartPoint);
        if (isResizing() && DragPositioningCallbackUtility.changeBounds(mCtrlType,
                mRepositionTaskBounds, mTaskBoundsAtDragStart, mStableBounds, delta,
                mDisplayController, mWindowDecoration)) {
            // The task is being resized, send the |dragResizing| hint to core with the first
            // bounds-change wct.
            if (!mHasDragResized) {
                // This is the first bounds change since drag resize operation started.
                wct.setDragResizing(mWindowDecoration.mTaskInfo.token, true /* dragResizing */);
            }
            wct.setBounds(mWindowDecoration.mTaskInfo.token, mRepositionTaskBounds);
            mTaskOrganizer.applyTransaction(wct);
            mHasDragResized = true;
            mIsResizingOrAnimatingResize = true;
        } else if (mCtrlType == CTRL_TYPE_UNDEFINED) {
            final SurfaceControl.Transaction t = mTransactionSupplier.get();
            DragPositioningCallbackUtility.setPositionOnDrag(mWindowDecoration,
                    mRepositionTaskBounds, mTaskBoundsAtDragStart, mRepositionStartPoint, t, x, y);
            t.apply();
        }
        return new Rect(mRepositionTaskBounds);
    }

    @Override
    public Rect onDragPositioningEnd(float x, float y) {
        // If task has been resized or task was dragged into area outside of
        // mDisallowedAreaForEndBounds, apply WCT to finish it.
        if (isResizing() && mHasDragResized) {
            final WindowContainerTransaction wct = new WindowContainerTransaction();
            wct.setDragResizing(mWindowDecoration.mTaskInfo.token, false /* dragResizing */);
            PointF delta = DragPositioningCallbackUtility.calculateDelta(x, y,
                    mRepositionStartPoint);
            if (DragPositioningCallbackUtility.changeBounds(mCtrlType, mRepositionTaskBounds,
                    mTaskBoundsAtDragStart, mStableBounds, delta, mDisplayController,
                    mWindowDecoration)) {
                wct.setBounds(mWindowDecoration.mTaskInfo.token, mRepositionTaskBounds);
            }
            mDragResizeEndTransition = mTransitions.startTransition(TRANSIT_CHANGE, wct, this);
        } else if (mCtrlType == CTRL_TYPE_UNDEFINED) {
            final WindowContainerTransaction wct = new WindowContainerTransaction();
            DragPositioningCallbackUtility.updateTaskBounds(mRepositionTaskBounds,
                    mTaskBoundsAtDragStart, mRepositionStartPoint, x, y);
            wct.setBounds(mWindowDecoration.mTaskInfo.token, mRepositionTaskBounds);
            mTransitions.startTransition(TRANSIT_CHANGE, wct, this);
        }

        mTaskBoundsAtDragStart.setEmpty();
        mRepositionStartPoint.set(0, 0);
        mCtrlType = CTRL_TYPE_UNDEFINED;
        mHasDragResized = false;
        return new Rect(mRepositionTaskBounds);
    }

    private boolean isResizing() {
        return (mCtrlType & CTRL_TYPE_TOP) != 0 || (mCtrlType & CTRL_TYPE_BOTTOM) != 0
                || (mCtrlType & CTRL_TYPE_LEFT) != 0 || (mCtrlType & CTRL_TYPE_RIGHT) != 0;
    }

    @Override
    public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
            @NonNull SurfaceControl.Transaction startTransaction,
            @NonNull SurfaceControl.Transaction finishTransaction,
            @NonNull Transitions.TransitionFinishCallback finishCallback) {
        for (TransitionInfo.Change change: info.getChanges()) {
            final SurfaceControl sc = change.getLeash();
            final Rect endBounds = change.getEndAbsBounds();
            final Point endPosition = change.getEndRelOffset();
            startTransaction.setWindowCrop(sc, endBounds.width(), endBounds.height())
                    .setPosition(sc, endPosition.x, endPosition.y);
            finishTransaction.setWindowCrop(sc, endBounds.width(), endBounds.height())
                    .setPosition(sc, endPosition.x, endPosition.y);
        }

        startTransaction.apply();
        if (transition.equals(mDragResizeEndTransition)) {
            mIsResizingOrAnimatingResize = false;
            mDragResizeEndTransition = null;
        }
        finishCallback.onTransitionFinished(null);
        return true;
    }

    /**
     * We should never reach this as this handler's transitions are only started from shell
     * explicitly.
     */
    @Nullable
    @Override
    public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
            @NonNull TransitionRequestInfo request) {
        return null;
    }

    @Override
    public void onTransitionConsumed(@NonNull IBinder transition, boolean aborted,
            @Nullable SurfaceControl.Transaction finishTransaction) {
        if (transition.equals(mDragResizeEndTransition)) {
            mIsResizingOrAnimatingResize = false;
            mDragResizeEndTransition = null;
        }
    }

    @Override
    public boolean isResizingOrAnimating() {
        return mIsResizingOrAnimatingResize;
    }
}

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

import static com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_DRAG_WINDOW;
import static com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_RESIZE_WINDOW;

import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.jank.Cuj;
import com.android.internal.jank.InteractionJankMonitor;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.shared.desktopmode.DesktopState;
import com.android.wm.shell.transition.Transitions;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A task positioner that also takes into account resizing a
 * {@link com.android.wm.shell.windowdecor.ResizeVeil}.
 * If the drag is resizing the task, we resize the veil instead.
 * If the drag is repositioning, we update in the typical manner.
 * <p>
 * @deprecated This class will be replaced by
 * {@link com.android.wm.shell.windowdecor.MultiDisplayVeiledResizeTaskPositioner}.
 * TODO(b/383632995): Remove this class after MultiDisplayVeiledResizeTaskPositioner is launched.
 */
@Deprecated
public class VeiledResizeTaskPositioner implements TaskPositioner, Transitions.TransitionHandler {
    // Timeout used for resize and drag CUJs, this is longer than the default timeout to avoid
    // timing out in the middle of a resize or drag action.
    private static final long LONG_CUJ_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10L);

    private DesktopModeWindowDecoration mDesktopWindowDecoration;
    private ShellTaskOrganizer mTaskOrganizer;
    private DisplayController mDisplayController;
    private ArrayList<DragPositioningCallbackUtility.DragEventListener>
            mDragEventListeners = new ArrayList<>();
    private final Transitions mTransitions;
    private final Rect mStableBounds = new Rect();
    private final Rect mTaskBoundsAtDragStart = new Rect();
    private final PointF mRepositionStartPoint = new PointF();
    private final Rect mRepositionTaskBounds = new Rect();
    private final Supplier<SurfaceControl.Transaction> mTransactionSupplier;
    private final InteractionJankMonitor mInteractionJankMonitor;
    private final DesktopState mDesktopState;
    private int mCtrlType;
    private boolean mIsResizingOrAnimatingResize;
    @Surface.Rotation private int mRotation;
    @ShellMainThread
    private final Handler mHandler;

    public VeiledResizeTaskPositioner(ShellTaskOrganizer taskOrganizer,
            DesktopModeWindowDecoration windowDecoration,
            DisplayController displayController,
            Transitions transitions, InteractionJankMonitor interactionJankMonitor,
            @ShellMainThread Handler handler, DesktopState desktopState) {
        this(taskOrganizer, windowDecoration, displayController,
                SurfaceControl.Transaction::new, transitions, interactionJankMonitor, handler,
                desktopState);
    }

    public VeiledResizeTaskPositioner(ShellTaskOrganizer taskOrganizer,
            DesktopModeWindowDecoration windowDecoration,
            DisplayController displayController,
            Supplier<SurfaceControl.Transaction> supplier, Transitions transitions,
            InteractionJankMonitor interactionJankMonitor, @ShellMainThread Handler handler,
            DesktopState desktopState) {
        mDesktopWindowDecoration = windowDecoration;
        mTaskOrganizer = taskOrganizer;
        mDisplayController = displayController;
        mTransactionSupplier = supplier;
        mTransitions = transitions;
        mInteractionJankMonitor = interactionJankMonitor;
        mHandler = handler;
        mDesktopState = desktopState;
    }

    @Override
    public Rect onDragPositioningStart(int ctrlType, int displayId, float x, float y) {
        mCtrlType = ctrlType;
        mTaskBoundsAtDragStart.set(
                mDesktopWindowDecoration.mTaskInfo.configuration.windowConfiguration.getBounds());
        mRepositionStartPoint.set(x, y);
        if (isResizing()) {
            // Capture CUJ for re-sizing window in DW mode.
            mInteractionJankMonitor.begin(
                    createLongTimeoutJankConfigBuilder(CUJ_DESKTOP_MODE_RESIZE_WINDOW));
            if (!mDesktopWindowDecoration.mHasGlobalFocus) {
                WindowContainerTransaction wct = new WindowContainerTransaction();
                wct.reorder(mDesktopWindowDecoration.mTaskInfo.token, true /* onTop */,
                        true /* includingParents */);
                mTaskOrganizer.applyTransaction(wct);
            }
        }
        mRepositionTaskBounds.set(mTaskBoundsAtDragStart);
        int rotation = mDesktopWindowDecoration
                .mTaskInfo.configuration.windowConfiguration.getDisplayRotation();
        if (mStableBounds.isEmpty() || mRotation != rotation) {
            mRotation = rotation;
            mDisplayController.getDisplayLayout(mDesktopWindowDecoration.mDisplay.getDisplayId())
                    .getStableBounds(mStableBounds);
        }
        return new Rect(mRepositionTaskBounds);
    }

    @Override
    public Rect onDragPositioningMove(int displayId, float x, float y) {
        if (Looper.myLooper() != mHandler.getLooper()) {
            // This method must run on the shell main thread to use the correct Choreographer
            // instance below.
            throw new IllegalStateException("This method must run on the shell main thread.");
        }
        PointF delta = DragPositioningCallbackUtility.calculateDelta(x, y, mRepositionStartPoint);
        if (isResizing() && DragPositioningCallbackUtility.changeBounds(mCtrlType,
                mRepositionTaskBounds, mTaskBoundsAtDragStart, mStableBounds, delta,
                mDisplayController, mDesktopWindowDecoration,
                mDesktopState.canEnterDesktopMode())) {
            if (!mIsResizingOrAnimatingResize) {
                for (DragPositioningCallbackUtility.DragEventListener dragEventListener :
                        mDragEventListeners) {
                    dragEventListener.onDragMove(mDesktopWindowDecoration.mTaskInfo.taskId);
                }
                mDesktopWindowDecoration.showResizeVeil(mRepositionTaskBounds);
                mIsResizingOrAnimatingResize = true;
            } else {
                mDesktopWindowDecoration.updateResizeVeil(mRepositionTaskBounds);
            }
        } else if (mCtrlType == CTRL_TYPE_UNDEFINED) {
            // Begin window drag CUJ instrumentation only when drag position moves.
            mInteractionJankMonitor.begin(
                    createLongTimeoutJankConfigBuilder(CUJ_DESKTOP_MODE_DRAG_WINDOW));
            final SurfaceControl.Transaction t = mTransactionSupplier.get();
            DragPositioningCallbackUtility.setPositionOnDrag(mDesktopWindowDecoration,
                    mRepositionTaskBounds, mTaskBoundsAtDragStart, mRepositionStartPoint, t, x, y);
            t.setFrameTimeline(Choreographer.getInstance().getVsyncId());
            t.apply();
        }
        return new Rect(mRepositionTaskBounds);
    }

    @Override
    public Rect onDragPositioningEnd(int displayId, float x, float y) {
        PointF delta = DragPositioningCallbackUtility.calculateDelta(x, y,
                mRepositionStartPoint);
        if (isResizing()) {
            if (!mTaskBoundsAtDragStart.equals(mRepositionTaskBounds)) {
                DragPositioningCallbackUtility.changeBounds(
                        mCtrlType, mRepositionTaskBounds, mTaskBoundsAtDragStart, mStableBounds,
                        delta, mDisplayController, mDesktopWindowDecoration,
                        mDesktopState.canEnterDesktopMode());
                mDesktopWindowDecoration.updateResizeVeil(mRepositionTaskBounds);
                final WindowContainerTransaction wct = new WindowContainerTransaction();
                wct.setBounds(mDesktopWindowDecoration.mTaskInfo.token, mRepositionTaskBounds);
                mTransitions.startTransition(TRANSIT_CHANGE, wct, this);
            } else {
                // If bounds haven't changed, perform necessary veil reset here as startAnimation
                // won't be called.
                resetVeilIfVisible();
            }
            mInteractionJankMonitor.end(CUJ_DESKTOP_MODE_RESIZE_WINDOW);
        } else {
            DragPositioningCallbackUtility.updateTaskBounds(mRepositionTaskBounds,
                    mTaskBoundsAtDragStart, mRepositionStartPoint, x, y);
            mInteractionJankMonitor.end(CUJ_DESKTOP_MODE_DRAG_WINDOW);
        }

        mCtrlType = CTRL_TYPE_UNDEFINED;
        mTaskBoundsAtDragStart.setEmpty();
        mRepositionStartPoint.set(0, 0);
        return new Rect(mRepositionTaskBounds);
    }

    @Override
    public void close() {}

    private boolean isResizing() {
        return (mCtrlType & CTRL_TYPE_TOP) != 0 || (mCtrlType & CTRL_TYPE_BOTTOM) != 0
                || (mCtrlType & CTRL_TYPE_LEFT) != 0 || (mCtrlType & CTRL_TYPE_RIGHT) != 0;
    }

    private void resetVeilIfVisible() {
        if (mIsResizingOrAnimatingResize) {
            mDesktopWindowDecoration.hideResizeVeil();
            mIsResizingOrAnimatingResize = false;
        }
    }

    private InteractionJankMonitor.Configuration.Builder createLongTimeoutJankConfigBuilder(
            @Cuj.CujType int cujType) {
        return InteractionJankMonitor.Configuration.Builder
                .withSurface(cujType, mDesktopWindowDecoration.mContext,
                        mDesktopWindowDecoration.mTaskSurface, mHandler)
                .setTimeout(LONG_CUJ_TIMEOUT_MS);
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
        resetVeilIfVisible();
        mCtrlType = CTRL_TYPE_UNDEFINED;
        finishCallback.onTransitionFinished(null);
        mIsResizingOrAnimatingResize = false;
        mInteractionJankMonitor.end(CUJ_DESKTOP_MODE_DRAG_WINDOW);
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
    public boolean isResizingOrAnimating() {
        return mIsResizingOrAnimatingResize;
    }

    @Override
    public void addDragEventListener(
            DragPositioningCallbackUtility.DragEventListener dragEventListener) {
        mDragEventListeners.add(dragEventListener);
    }

    @Override
    public void removeDragEventListener(
            DragPositioningCallbackUtility.DragEventListener dragEventListener) {
        mDragEventListeners.remove(dragEventListener);
    }
}

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

package com.android.wm.shell.windowdecor;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.INPUT_FEATURE_NO_INPUT_CHANNEL;
import static android.view.WindowManager.LayoutParams.INPUT_FEATURE_SPY;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_CONSUMER;

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_BOTTOM;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_LEFT;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_RIGHT;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_TOP;
import static com.android.wm.shell.windowdecor.DragResizeWindowGeometry.isEdgeResizePermitted;
import static com.android.wm.shell.windowdecor.DragResizeWindowGeometry.isEventFromTouchscreen;

import android.annotation.NonNull;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.hardware.input.InputManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Size;
import android.view.Choreographer;
import android.view.IWindowSession;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManagerGlobal;
import android.window.InputTransferToken;

import com.android.internal.protolog.common.ProtoLog;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An input event listener registered to InputDispatcher to receive input events on task edges and
 * and corners. Converts them to drag resize requests.
 * Task edges are for resizing with a mouse.
 * Task corners are for resizing with touch input.
 */
class DragResizeInputListener implements AutoCloseable {
    private static final String TAG = "DragResizeInputListener";
    private final IWindowSession mWindowSession = WindowManagerGlobal.getWindowSession();
    private final Supplier<SurfaceControl.Transaction> mSurfaceControlTransactionSupplier;

    private final int mDisplayId;

    private final IBinder mClientToken;

    private final SurfaceControl mDecorationSurface;
    private final InputChannel mInputChannel;
    private final TaskResizeInputEventReceiver mInputEventReceiver;

    private final SurfaceControl mInputSinkSurface;
    private final IBinder mSinkClientToken;
    private final InputChannel mSinkInputChannel;
    private final DisplayController mDisplayController;
    private final Region mTouchRegion = new Region();

    DragResizeInputListener(
            Context context,
            Handler handler,
            Choreographer choreographer,
            int displayId,
            SurfaceControl decorationSurface,
            DragPositioningCallback callback,
            Supplier<SurfaceControl.Builder> surfaceControlBuilderSupplier,
            Supplier<SurfaceControl.Transaction> surfaceControlTransactionSupplier,
            DisplayController displayController) {
        mSurfaceControlTransactionSupplier = surfaceControlTransactionSupplier;
        mDisplayId = displayId;
        mDecorationSurface = decorationSurface;
        mDisplayController = displayController;
        mClientToken = new Binder();
        final InputTransferToken inputTransferToken = new InputTransferToken();
        mInputChannel = new InputChannel();
        try {
            mWindowSession.grantInputChannel(
                    mDisplayId,
                    mDecorationSurface,
                    mClientToken,
                    null /* hostInputToken */,
                    FLAG_NOT_FOCUSABLE,
                    PRIVATE_FLAG_TRUSTED_OVERLAY,
                    INPUT_FEATURE_SPY,
                    TYPE_APPLICATION,
                    null /* windowToken */,
                    inputTransferToken,
                    TAG + " of " + decorationSurface.toString(),
                    mInputChannel);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }

        mInputEventReceiver = new TaskResizeInputEventReceiver(context, mInputChannel, callback,
                handler, choreographer, () -> {
            final DisplayLayout layout = mDisplayController.getDisplayLayout(mDisplayId);
            return new Size(layout.width(), layout.height());
        }, this::updateSinkInputChannel);
        mInputEventReceiver.setTouchSlop(ViewConfiguration.get(context).getScaledTouchSlop());

        mInputSinkSurface = surfaceControlBuilderSupplier.get()
                .setName("TaskInputSink of " + decorationSurface)
                .setContainerLayer()
                .setParent(mDecorationSurface)
                .setCallsite("DragResizeInputListener.constructor")
                .build();
        mSurfaceControlTransactionSupplier.get()
                .setLayer(mInputSinkSurface, WindowDecoration.INPUT_SINK_Z_ORDER)
                .show(mInputSinkSurface)
                .apply();
        mSinkClientToken = new Binder();
        mSinkInputChannel = new InputChannel();
        try {
            mWindowSession.grantInputChannel(
                    mDisplayId,
                    mInputSinkSurface,
                    mSinkClientToken,
                    null /* hostInputToken */,
                    FLAG_NOT_FOCUSABLE,
                    0 /* privateFlags */,
                    INPUT_FEATURE_NO_INPUT_CHANNEL,
                    TYPE_INPUT_CONSUMER,
                    null /* windowToken */,
                    inputTransferToken,
                    "TaskInputSink of " + decorationSurface,
                    mSinkInputChannel);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
    }

    /**
     * Updates the geometry (the touch region) of this drag resize handler.
     *
     * @param incomingGeometry The geometry update to apply for this task's drag resize regions.
     * @param touchSlop        The distance in pixels user has to drag with touch for it to register
     *                         as a resize action.
     * @return whether the geometry has changed or not
     */
    boolean setGeometry(@NonNull DragResizeWindowGeometry incomingGeometry, int touchSlop) {
        DragResizeWindowGeometry geometry = mInputEventReceiver.getGeometry();
        if (incomingGeometry.equals(geometry)) {
            // Geometry hasn't changed size so skip all updates.
            return false;
        } else {
            geometry = incomingGeometry;
        }
        mInputEventReceiver.setTouchSlop(touchSlop);

        mTouchRegion.setEmpty();
        // Apply the geometry to the touch region.
        geometry.union(mTouchRegion);
        mInputEventReceiver.setGeometry(geometry);
        mInputEventReceiver.setTouchRegion(mTouchRegion);

        try {
            mWindowSession.updateInputChannel(
                    mInputChannel.getToken(),
                    mDisplayId,
                    mDecorationSurface,
                    FLAG_NOT_FOCUSABLE,
                    PRIVATE_FLAG_TRUSTED_OVERLAY,
                    INPUT_FEATURE_SPY,
                    mTouchRegion);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }

        final Size taskSize = geometry.getTaskSize();
        mSurfaceControlTransactionSupplier.get()
                .setWindowCrop(mInputSinkSurface, taskSize.getWidth(), taskSize.getHeight())
                .apply();
        // The touch region of the TaskInputSink should be the touch region of this
        // DragResizeInputHandler minus the task bounds. Pilfering events isn't enough to prevent
        // input windows from handling down events, which will bring tasks in the back to front.
        //
        // Note not the entire touch region responds to both mouse and touchscreen events.
        // Therefore, in the region that only responds to one of them, it would be a no-op to
        // perform a gesture in the other type of events. We currently only have a mouse-only region
        // out of the task bounds, and due to the roughness of touchscreen events, it's not a severe
        // issue. However, were there touchscreen-only a region out of the task bounds, mouse
        // gestures will become no-op in that region, even though the mouse gestures may appear to
        // be performed on the input window behind the resize handle.
        mTouchRegion.op(0, 0, taskSize.getWidth(), taskSize.getHeight(), Region.Op.DIFFERENCE);
        updateSinkInputChannel(mTouchRegion);
        return true;
    }

    /**
     * Generate a Region that encapsulates all 4 corner handles and window edges.
     */
    @NonNull Region getCornersRegion() {
        return mInputEventReceiver.getCornersRegion();
    }

    private void updateSinkInputChannel(Region region) {
        try {
            mWindowSession.updateInputChannel(
                    mSinkInputChannel.getToken(),
                    mDisplayId,
                    mInputSinkSurface,
                    FLAG_NOT_FOCUSABLE,
                    0 /* privateFlags */,
                    INPUT_FEATURE_NO_INPUT_CHANNEL,
                    region);
        } catch (RemoteException ex) {
            ex.rethrowFromSystemServer();
        }
    }

    boolean shouldHandleEvent(@NonNull MotionEvent e, @NonNull Point offset) {
        return mInputEventReceiver.shouldHandleEvent(e, offset);
    }

    boolean isHandlingDragResize() {
        return mInputEventReceiver.isHandlingEvents();
    }

    @Override
    public void close() {
        mInputEventReceiver.dispose();
        mInputChannel.dispose();
        try {
            mWindowSession.remove(mClientToken);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }

        mSinkInputChannel.dispose();
        try {
            mWindowSession.remove(mSinkClientToken);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
        mSurfaceControlTransactionSupplier.get()
                .remove(mInputSinkSurface)
                .apply();
    }

    private static class TaskResizeInputEventReceiver extends InputEventReceiver implements
            DragDetector.MotionEventHandler {
        @NonNull private final Context mContext;
        private final InputManager mInputManager;
        @NonNull private final InputChannel mInputChannel;
        @NonNull private final DragPositioningCallback mCallback;
        @NonNull private final Choreographer mChoreographer;
        @NonNull private final Runnable mConsumeBatchEventRunnable;
        @NonNull private final DragDetector mDragDetector;
        @NonNull private final Supplier<Size> mDisplayLayoutSizeSupplier;
        @NonNull private final Consumer<Region> mTouchRegionConsumer;
        private final Rect mTmpRect = new Rect();
        private boolean mConsumeBatchEventScheduled;
        private DragResizeWindowGeometry mDragResizeWindowGeometry;
        private Region mTouchRegion;
        private boolean mShouldHandleEvents;
        private int mLastCursorType = PointerIcon.TYPE_DEFAULT;
        private Rect mDragStartTaskBounds;
        // The id of the particular pointer in a MotionEvent that we are listening to for drag
        // resize events. For example, if multiple fingers are touching the screen, then each one
        // has a separate pointer id, but we only accept drag input from one.
        private int mDragPointerId = -1;

        private TaskResizeInputEventReceiver(@NonNull Context context,
                @NonNull InputChannel inputChannel,
                @NonNull DragPositioningCallback callback, @NonNull Handler handler,
                @NonNull Choreographer choreographer,
                @NonNull Supplier<Size> displayLayoutSizeSupplier,
                @NonNull Consumer<Region> touchRegionConsumer) {
            super(inputChannel, handler.getLooper());
            mContext = context;
            mInputManager = context.getSystemService(InputManager.class);
            mInputChannel = inputChannel;
            mCallback = callback;
            mChoreographer = choreographer;

            mConsumeBatchEventRunnable = () -> {
                mConsumeBatchEventScheduled = false;
                if (consumeBatchedInputEvents(mChoreographer.getFrameTimeNanos())) {
                    // If we consumed a batch here, we want to go ahead and schedule the
                    // consumption of batched input events on the next frame. Otherwise, we would
                    // wait until we have more input events pending and might get starved by other
                    // things occurring in the process.
                    scheduleConsumeBatchEvent();
                }
            };

            mDragDetector = new DragDetector(this);
            mDisplayLayoutSizeSupplier = displayLayoutSizeSupplier;
            mTouchRegionConsumer = touchRegionConsumer;
        }

        /**
         * Returns the geometry of the areas to drag resize.
         */
        DragResizeWindowGeometry getGeometry() {
            return mDragResizeWindowGeometry;
        }

        /**
         * Updates the geometry of the areas to drag resize.
         */
        void setGeometry(@NonNull DragResizeWindowGeometry dragResizeWindowGeometry) {
            mDragResizeWindowGeometry = dragResizeWindowGeometry;
        }

        /**
         * Sets how much slop to allow for touches.
         */
        void setTouchSlop(int touchSlop) {
            mDragDetector.setTouchSlop(touchSlop);
        }

        /**
         * Updates the region accepting input for drag resizing the task.
         */
        void setTouchRegion(@NonNull Region touchRegion) {
            mTouchRegion = touchRegion;
        }

        /**
         * Returns the union of all regions that can be touched for drag resizing; the corners and
         * window edges.
         */
        @NonNull Region getCornersRegion() {
            Region region = new Region();
            mDragResizeWindowGeometry.union(region);
            return region;
        }

        @Override
        public void onBatchedInputEventPending(int source) {
            scheduleConsumeBatchEvent();
        }

        private void scheduleConsumeBatchEvent() {
            if (mConsumeBatchEventScheduled) {
                return;
            }
            mChoreographer.postCallback(
                    Choreographer.CALLBACK_INPUT, mConsumeBatchEventRunnable, null);
            mConsumeBatchEventScheduled = true;
        }

        @Override
        public void onInputEvent(InputEvent inputEvent) {
            finishInputEvent(inputEvent, handleInputEvent(inputEvent));
        }

        boolean isHandlingEvents() {
            return mShouldHandleEvents;
        }

        private boolean handleInputEvent(InputEvent inputEvent) {
            if (!(inputEvent instanceof MotionEvent)) {
                return false;
            }
            return mDragDetector.onMotionEvent((MotionEvent) inputEvent);
        }

        @Override
        public boolean handleMotionEvent(View v, MotionEvent e) {
            boolean result = false;
            // Check if this is a touch event vs mouse event.
            // Touch events are tracked in four corners. Other events are tracked in resize edges.
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    mShouldHandleEvents = mDragResizeWindowGeometry.shouldHandleEvent(e,
                            new Point() /* offset */);
                    if (mShouldHandleEvents) {
                        // Save the id of the pointer for this drag interaction; we will use the
                        // same pointer for all subsequent MotionEvents in this interaction.
                        mDragPointerId = e.getPointerId(0);
                        float x = e.getX(0);
                        float y = e.getY(0);
                        float rawX = e.getRawX(0);
                        float rawY = e.getRawY(0);
                        final int ctrlType = mDragResizeWindowGeometry.calculateCtrlType(
                                isEventFromTouchscreen(e), isEdgeResizePermitted(e), x, y);
                        ProtoLog.d(WM_SHELL_DESKTOP_MODE,
                                "%s: Handling action down, update ctrlType to %d", TAG, ctrlType);
                        mDragStartTaskBounds = mCallback.onDragPositioningStart(ctrlType,
                                rawX, rawY);
                        // Increase the input sink region to cover the whole screen; this is to
                        // prevent input and focus from going to other tasks during a drag resize.
                        updateInputSinkRegionForDrag(mDragStartTaskBounds);
                        result = true;
                    } else {
                        ProtoLog.d(WM_SHELL_DESKTOP_MODE,
                                "%s: Handling action down, but ignore event", TAG);
                    }
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (!mShouldHandleEvents) {
                        break;
                    }
                    mInputManager.pilferPointers(mInputChannel.getToken());
                    final int dragPointerIndex = e.findPointerIndex(mDragPointerId);
                    if (dragPointerIndex < 0) {
                        ProtoLog.d(WM_SHELL_DESKTOP_MODE,
                                "%s: Handling action move, but ignore event due to invalid "
                                        + "pointer index",
                                TAG);
                        break;
                    }
                    final float rawX = e.getRawX(dragPointerIndex);
                    final float rawY = e.getRawY(dragPointerIndex);
                    final Rect taskBounds = mCallback.onDragPositioningMove(rawX, rawY);
                    updateInputSinkRegionForDrag(taskBounds);
                    result = true;
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    if (mShouldHandleEvents) {
                        final int dragPointerIndex = e.findPointerIndex(mDragPointerId);
                        if (dragPointerIndex < 0) {
                            ProtoLog.d(WM_SHELL_DESKTOP_MODE,
                                    "%s: Handling action %d, but ignore event due to invalid "
                                            + "pointer index",
                                    TAG, e.getActionMasked());
                            break;
                        }
                        final Rect taskBounds = mCallback.onDragPositioningEnd(
                                e.getRawX(dragPointerIndex), e.getRawY(dragPointerIndex));
                        // If taskBounds has changed, setGeometry will be called and update the
                        // sink region. Otherwise, we should revert it here.
                        if (taskBounds.equals(mDragStartTaskBounds)) {
                            mTouchRegionConsumer.accept(mTouchRegion);
                        }
                    }
                    mShouldHandleEvents = false;
                    mDragPointerId = -1;
                    result = true;
                    break;
                }
                case MotionEvent.ACTION_HOVER_ENTER:
                case MotionEvent.ACTION_HOVER_MOVE: {
                    updateCursorType(e.getDisplayId(), e.getDeviceId(),
                            e.getPointerId(/*pointerIndex=*/0), e.getXCursorPosition(),
                            e.getYCursorPosition());
                    result = true;
                    break;
                }
                case MotionEvent.ACTION_HOVER_EXIT:
                    result = true;
                    break;
            }
            return result;
        }

        private void updateInputSinkRegionForDrag(Rect taskBounds) {
            mTmpRect.set(taskBounds);
            final Size displayLayoutSize = mDisplayLayoutSizeSupplier.get();
            final Region dragTouchRegion = new Region(-taskBounds.left, -taskBounds.top,
                    -taskBounds.left + displayLayoutSize.getWidth(),
                    -taskBounds.top + displayLayoutSize.getHeight());
            // Remove the localized task bounds from the touch region.
            mTmpRect.offsetTo(0, 0);
            dragTouchRegion.op(mTmpRect, Region.Op.DIFFERENCE);
            mTouchRegionConsumer.accept(dragTouchRegion);
        }

        private void updateCursorType(int displayId, int deviceId, int pointerId, float x,
                float y) {
            // Since we are handling cursor, we know that this is not a touchscreen event, and
            // that edge resizing should always be allowed.
            @DragPositioningCallback.CtrlType int ctrlType =
                    mDragResizeWindowGeometry.calculateCtrlType(/* isTouchscreen= */
                            false, /* isEdgeResizePermitted= */ true, x, y);

            int cursorType = PointerIcon.TYPE_DEFAULT;
            switch (ctrlType) {
                case CTRL_TYPE_LEFT:
                case CTRL_TYPE_RIGHT:
                    cursorType = PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW;
                    break;
                case CTRL_TYPE_TOP:
                case CTRL_TYPE_BOTTOM:
                    cursorType = PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW;
                    break;
                case CTRL_TYPE_LEFT | CTRL_TYPE_TOP:
                case CTRL_TYPE_RIGHT | CTRL_TYPE_BOTTOM:
                    cursorType = PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW;
                    break;
                case CTRL_TYPE_LEFT | CTRL_TYPE_BOTTOM:
                case CTRL_TYPE_RIGHT | CTRL_TYPE_TOP:
                    cursorType = PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW;
                    break;
            }
            // Only update the cursor type to default once so that views behind the decor container
            // layer that aren't in the active resizing regions have chances to update the cursor
            // type. We would like to enforce the cursor type by setting the cursor type multilple
            // times in active regions because we shouldn't allow the views behind to change it, as
            // we'll pilfer the gesture initiated in this area. This is necessary because 1) we
            // should allow the views behind regions only for touches to set the cursor type; and 2)
            // there is a small region out of each rounded corner that's inside the task bounds,
            // where views in the task can receive input events because we can't set touch regions
            // of input sinks to have rounded corners.
            if (mLastCursorType != cursorType || cursorType != PointerIcon.TYPE_DEFAULT) {
                ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: update pointer icon from %d to %d",
                        TAG, mLastCursorType, cursorType);
                mInputManager.setPointerIcon(PointerIcon.getSystemIcon(mContext, cursorType),
                        displayId, deviceId, pointerId, mInputChannel.getToken());
                mLastCursorType = cursorType;
            }
        }

        private boolean shouldHandleEvent(MotionEvent e, Point offset) {
            return mDragResizeWindowGeometry.shouldHandleEvent(e, offset);
        }
    }
}

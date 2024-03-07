/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3.dragndrop;

import static com.android.launcher3.Utilities.ATLEAST_Q;
import static com.android.launcher3.config.FeatureFlags.ENABLE_NO_LONG_PRESS_DRAG;

import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.app.animation.Interpolators;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.logging.InstanceId;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.TouchController;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Class for initiating a drag within a view or across multiple views.
 * @param <T>
 */
public abstract class DragController<T extends ActivityContext>
        implements DragDriver.EventListener, TouchController {

    /**
     * When a drag is started from a deep press, you need to drag this much farther than normal to
     * end a pre-drag. See {@link DragOptions.PreDragCondition#shouldStartDrag(double)}.
     */
    private static final int DEEP_PRESS_DISTANCE_FACTOR = 3;

    protected final T mActivity;

    // temporaries to avoid gc thrash
    private final Rect mRectTemp = new Rect();
    private final int[] mCoordinatesTemp = new int[2];

    /**
     * Drag driver for the current drag/drop operation, or null if there is no active DND operation.
     * It's null during accessible drag operations.
     */
    protected DragDriver mDragDriver = null;

    /** Options controlling the drag behavior. */
    protected DragOptions mOptions;

    /** Coordinate for motion down event */
    protected final Point mMotionDown = new Point();
    /** Coordinate for last touch event **/
    protected final Point mLastTouch = new Point();

    protected final Point mTmpPoint = new Point();

    protected DropTarget.DragObject mDragObject;

    /** Who can receive drop events */
    private final ArrayList<DropTarget> mDropTargets = new ArrayList<>();
    private final ArrayList<DragListener> mListeners = new ArrayList<>();

    protected DropTarget mLastDropTarget;

    private int mLastTouchClassification;
    protected int mDistanceSinceScroll = 0;

    /**
     * This variable is to differentiate between a long press and a drag, if it's true that means
     * it's a long press and when it's false means that we are no longer in a long press.
     */
    protected boolean mIsInPreDrag;

    private final int DRAG_VIEW_SCALE_DURATION_MS = 500;

    /**
     * Interface to receive notifications when a drag starts or stops
     */
    public interface DragListener {
        /**
         * A drag has begun
         *
         * @param dragObject The object being dragged
         * @param options Options used to start the drag
         */
        void onDragStart(DropTarget.DragObject dragObject, DragOptions options);

        /**
         * The drag has ended
         */
        void onDragEnd();
    }

    /**
     * Used to create a new DragLayer from XML.
     */
    public DragController(T activity) {
        mActivity = activity;
    }

    /**
     * Starts a drag.
     *
     * <p>When the drag is started, the UI automatically goes into spring loaded mode. On a
     * successful drop, it is the responsibility of the {@link DropTarget} to exit out of the spring
     * loaded mode. If the drop was cancelled for some reason, the UI will automatically exit out of
     * this mode.
     *
     * @param drawable The drawable to be displayed in the drag view.  It will be re-scaled to the
     *                 enlarged size.
     * @param originalView The source view (ie. icon, widget etc.) that is being dragged and which
     *                     the DragView represents
     * @param dragLayerX The x position in the DragLayer of the left-top of the bitmap.
     * @param dragLayerY The y position in the DragLayer of the left-top of the bitmap.
     * @param source An object representing where the drag originated
     * @param dragInfo The data associated with the object that is being dragged
     * @param dragRegion Coordinates within the bitmap b for the position of item being dragged.
     *                   Makes dragging feel more precise, e.g. you can clip out a transparent
     *                   border
     */
    public DragView startDrag(
            Drawable drawable,
            DraggableView originalView,
            int dragLayerX,
            int dragLayerY,
            DragSource source,
            ItemInfo dragInfo,
            Rect dragRegion,
            float initialDragViewScale,
            float dragViewScaleOnDrop,
            DragOptions options) {
        return startDrag(drawable, /* view= */ null, originalView, dragLayerX, dragLayerY, source,
                dragInfo, dragRegion, initialDragViewScale, dragViewScaleOnDrop, options);
    }

    /**
     * Starts a drag.
     *
     * <p>When the drag is started, the UI automatically goes into spring loaded mode. On a
     * successful drop, it is the responsibility of the {@link DropTarget} to exit out of the spring
     * loaded mode. If the drop was cancelled for some reason, the UI will automatically exit out of
     * this mode.
     *
     * @param view The view to be displayed in the drag view.  It will be re-scaled to the
     *             enlarged size.
     * @param originalView The source view (ie. icon, widget etc.) that is being dragged and which
     *                     the DragView represents
     * @param dragLayerX The x position in the DragLayer of the left-top of the bitmap.
     * @param dragLayerY The y position in the DragLayer of the left-top of the bitmap.
     * @param source An object representing where the drag originated
     * @param dragInfo The data associated with the object that is being dragged
     * @param dragRegion Coordinates within the bitmap b for the position of item being dragged.
     *                   Makes dragging feel more precise, e.g. you can clip out a transparent
     *                   border
     */
    public DragView startDrag(
            View view,
            DraggableView originalView,
            int dragLayerX,
            int dragLayerY,
            DragSource source,
            ItemInfo dragInfo,
            Rect dragRegion,
            float initialDragViewScale,
            float dragViewScaleOnDrop,
            DragOptions options) {
        return startDrag(/* drawable= */ null, view, originalView, dragLayerX, dragLayerY, source,
                dragInfo, dragRegion, initialDragViewScale, dragViewScaleOnDrop, options);
    }

    protected abstract DragView startDrag(
            @Nullable Drawable drawable,
            @Nullable View view,
            DraggableView originalView,
            int dragLayerX,
            int dragLayerY,
            DragSource source,
            ItemInfo dragInfo,
            Rect dragRegion,
            float initialDragViewScale,
            float dragViewScaleOnDrop,
            DragOptions options);

    protected void callOnDragStart() {
        if (mOptions.preDragCondition != null) {
            mOptions.preDragCondition.onPreDragEnd(mDragObject, true /* dragStarted*/);
        }
        mIsInPreDrag = false;
        if (mOptions.preDragEndScale != 0) {
            mDragObject.dragView
                    .animate()
                    .scaleX(mOptions.preDragEndScale)
                    .scaleY(mOptions.preDragEndScale)
                    .setInterpolator(Interpolators.EMPHASIZED)
                    .setDuration(DRAG_VIEW_SCALE_DURATION_MS)
                    .start();
        }
        mDragObject.dragView.onDragStart();
        for (DragListener listener : new ArrayList<>(mListeners)) {
            listener.onDragStart(mDragObject, mOptions);
        }
    }

    public Optional<InstanceId> getLogInstanceId() {
        return Optional.ofNullable(mDragObject)
                .map(dragObject -> dragObject.logInstanceId);
    }

    /**
     * Call this from a drag source view like this:
     *
     * <pre>
     *  @Override
     *  public boolean dispatchKeyEvent(KeyEvent event) {
     *      return mDragController.dispatchKeyEvent(this, event)
     *              || super.dispatchKeyEvent(event);
     * </pre>
     */
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mDragDriver != null;
    }

    public boolean isDragging() {
        return mDragDriver != null || (mOptions != null && mOptions.isAccessibleDrag);
    }

    /**
     * Stop dragging without dropping.
     */
    public void cancelDrag() {
        if (isDragging()) {
            if (mLastDropTarget != null) {
                mLastDropTarget.onDragExit(mDragObject);
            }
            mDragObject.deferDragViewCleanupPostAnimation = false;
            mDragObject.cancelled = true;
            mDragObject.dragComplete = true;
            if (!mIsInPreDrag) {
                dispatchDropComplete(null, false);
            }
        }
        endDrag();
    }

    private void dispatchDropComplete(View dropTarget, boolean accepted) {
        if (!accepted) {
            // If it was not accepted, cleanup the state. If it was accepted, it is the
            // responsibility of the drop target to cleanup the state.
            exitDrag();
            mDragObject.deferDragViewCleanupPostAnimation = false;
        }

        mDragObject.dragSource.onDropCompleted(dropTarget, mDragObject, accepted);
    }

    protected abstract void exitDrag();

    public void onAppsRemoved(Predicate<ItemInfo> matcher) {
        // Cancel the current drag if we are removing an app that we are dragging
        if (mDragObject != null) {
            ItemInfo dragInfo = mDragObject.dragInfo;
            if (dragInfo instanceof WorkspaceItemInfo && matcher.test(dragInfo)) {
                cancelDrag();
            }
        }
    }

    protected void endDrag() {
        if (isDragging()) {
            mDragDriver = null;
            boolean isDeferred = false;
            if (mDragObject.dragView != null) {
                isDeferred = mDragObject.deferDragViewCleanupPostAnimation;
                if (!isDeferred) {
                    mDragObject.dragView.remove();
                } else if (mIsInPreDrag) {
                    animateDragViewToOriginalPosition(null, null, -1);
                }
                mDragObject.dragView.clearAnimation();
                mDragObject.dragView = null;
            }
            // Only end the drag if we are not deferred
            if (!isDeferred) {
                callOnDragEnd();
            }
        }
    }

    public void animateDragViewToOriginalPosition(final Runnable onComplete,
            final View originalIcon, int duration) {
        Runnable onCompleteRunnable = new Runnable() {
            @Override
            public void run() {
                if (originalIcon != null) {
                    originalIcon.setVisibility(View.VISIBLE);
                }
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        };
        mDragObject.dragView.animateTo(mMotionDown.x, mMotionDown.y, onCompleteRunnable, duration);
    }

    protected void callOnDragEnd() {
        if (mIsInPreDrag && mOptions.preDragCondition != null) {
            mOptions.preDragCondition.onPreDragEnd(mDragObject, false /* dragStarted*/);
        }
        mIsInPreDrag = false;
        mOptions = null;
        for (DragListener listener : new ArrayList<>(mListeners)) {
            listener.onDragEnd();
        }
    }

    /**
     * This only gets called as a result of drag view cleanup being deferred in endDrag();
     */
    void onDeferredEndDrag(DragView dragView) {
        dragView.remove();

        if (mDragObject.deferDragViewCleanupPostAnimation) {
            // If we skipped calling onDragEnd() before, do it now
            callOnDragEnd();
        }
    }

    /**
     * Clamps the position to the drag layer bounds.
     */
    protected Point getClampedDragLayerPos(float x, float y) {
        mActivity.getDragLayer().getLocalVisibleRect(mRectTemp);
        mTmpPoint.x = (int) Math.max(mRectTemp.left, Math.min(x, mRectTemp.right - 1));
        mTmpPoint.y = (int) Math.max(mRectTemp.top, Math.min(y, mRectTemp.bottom - 1));
        return mTmpPoint;
    }

    @Override
    public void onDriverDragMove(float x, float y) {
        Point dragLayerPos = getClampedDragLayerPos(x, y);
        handleMoveEvent(dragLayerPos.x, dragLayerPos.y);
    }

    @Override
    public void onDriverDragExitWindow() {
        if (mLastDropTarget != null) {
            mLastDropTarget.onDragExit(mDragObject);
            mLastDropTarget = null;
        }
    }

    @Override
    public void onDriverDragEnd(float x, float y) {
        if (!endWithFlingAnimation()) {
            drop(findDropTarget((int) x, (int) y), null);
        }
        endDrag();
    }

    protected boolean endWithFlingAnimation() {
        return false;
    }

    @Override
    public void onDriverDragCancel() {
        cancelDrag();
    }

    /**
     * Call this from a drag source view.
     */
    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (mOptions != null && mOptions.isAccessibleDrag) {
            return false;
        }

        Point dragLayerPos = getClampedDragLayerPos(getX(ev), getY(ev));
        mLastTouch.set(dragLayerPos.x,  dragLayerPos.y);
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            // Remember location of down touch
            mMotionDown.set(dragLayerPos.x,  dragLayerPos.y);
        }

        if (ATLEAST_Q) {
            mLastTouchClassification = ev.getClassification();
        }
        return mDragDriver != null && mDragDriver.onInterceptTouchEvent(ev);
    }

    protected float getX(MotionEvent ev) {
        return ev.getX();
    }

    protected float getY(MotionEvent ev) {
        return ev.getY();
    }

    /**
     * Call this from a drag source view.
     */
    @Override
    public boolean onControllerTouchEvent(MotionEvent ev) {
        return mDragDriver != null && mDragDriver.onTouchEvent(ev);
    }

    /**
     * Call this from a drag source view.
     */
    public boolean onDragEvent(DragEvent event) {
        return mDragDriver != null && mDragDriver.onDragEvent(event);
    }

    protected void handleMoveEvent(int x, int y) {
        mDragObject.dragView.move(x, y);

        // Check if we are hovering over the scroll areas
        mDistanceSinceScroll += Math.hypot(mLastTouch.x - x, mLastTouch.y - y);
        mLastTouch.set(x, y);

        int distanceDragged = mDistanceSinceScroll;
        if (ATLEAST_Q && mLastTouchClassification == MotionEvent.CLASSIFICATION_DEEP_PRESS) {
            distanceDragged /= DEEP_PRESS_DISTANCE_FACTOR;
        }
        if (mIsInPreDrag && mOptions.preDragCondition != null
                && mOptions.preDragCondition.shouldStartDrag(distanceDragged)) {
            callOnDragStart();
        }

        // Drop on someone?
        checkTouchMove(x, y);
    }

    public float getDistanceDragged() {
        return mDistanceSinceScroll;
    }

    public void forceTouchMove() {
        checkTouchMove(mLastTouch.x, mLastTouch.y);
    }

    private DropTarget checkTouchMove(final int x, final int y) {
        // If we are in predrag, don't trigger any other event until we get out of it
        if (ENABLE_NO_LONG_PRESS_DRAG.get() && mIsInPreDrag) {
            return mLastDropTarget;
        }
        DropTarget dropTarget = findDropTarget(x, y);
        if (dropTarget != null) {
            if (mLastDropTarget != dropTarget) {
                if (mLastDropTarget != null) {
                    mLastDropTarget.onDragExit(mDragObject);
                }
                dropTarget.onDragEnter(mDragObject);
            }
            dropTarget.onDragOver(mDragObject);
        } else if (mLastDropTarget != null) {
            mLastDropTarget.onDragExit(mDragObject);
        }
        mLastDropTarget = dropTarget;
        return mLastDropTarget;
    }

    /**
     * As above, since accessible drag and drop won't cause the same sequence of touch events,
     * we manually ensure appropriate drag and drop events get emulated for accessible drag.
     */
    public void completeAccessibleDrag(int[] location) {
        // We make sure that we prime the target for drop.
        DropTarget dropTarget = checkTouchMove(location[0], location[1]);

        dropTarget.prepareAccessibilityDrop();
        // Perform the drop
        drop(dropTarget, null);
        endDrag();
    }

    protected void drop(DropTarget dropTarget, Runnable flingAnimation) {
        // Move dragging to the final target.
        if (dropTarget != mLastDropTarget) {
            if (mLastDropTarget != null) {
                mLastDropTarget.onDragExit(mDragObject);
            }
            mLastDropTarget = dropTarget;
            if (dropTarget != null) {
                dropTarget.onDragEnter(mDragObject);
            }
        }

        mDragObject.dragComplete = true;
        if (mIsInPreDrag) {
            if (dropTarget != null) {
                dropTarget.onDragExit(mDragObject);
            }
            return;
        }

        // Drop onto the target.
        boolean accepted = false;
        if (dropTarget != null) {
            dropTarget.onDragExit(mDragObject);
            if (dropTarget.acceptDrop(mDragObject)) {
                if (flingAnimation != null) {
                    flingAnimation.run();
                } else {
                    dropTarget.onDrop(mDragObject, mOptions);
                }
                accepted = true;
            }
        }
        final View dropTargetAsView = dropTarget instanceof View ? (View) dropTarget : null;
        dispatchDropComplete(dropTargetAsView, accepted);
    }

    private DropTarget findDropTarget(final int x, final int y) {
        mCoordinatesTemp[0] = x;
        mCoordinatesTemp[1] = y;

        final Rect r = mRectTemp;
        final ArrayList<DropTarget> dropTargets = mDropTargets;
        final int count = dropTargets.size();
        for (int i = count - 1; i >= 0; i--) {
            DropTarget target = dropTargets.get(i);
            if (!target.isDropEnabled())
                continue;

            target.getHitRectRelativeToDragLayer(r);
            if (r.contains(x, y)) {
                mActivity.getDragLayer().mapCoordInSelfToDescendant((View) target,
                        mCoordinatesTemp);
                mDragObject.x = mCoordinatesTemp[0];
                mDragObject.y = mCoordinatesTemp[1];
                return target;
            }
        }
        DropTarget dropTarget = getDefaultDropTarget(mCoordinatesTemp);
        mDragObject.x = mCoordinatesTemp[0];
        mDragObject.y = mCoordinatesTemp[1];
        return dropTarget;
    }

    protected abstract DropTarget getDefaultDropTarget(int[] dropCoordinates);

    /**
     * Sets the drag listener which will be notified when a drag starts or ends.
     */
    public void addDragListener(DragListener l) {
        mListeners.add(l);
    }

    /**
     * Remove a previously installed drag listener.
     */
    public void removeDragListener(DragListener l) {
        mListeners.remove(l);
    }

    /**
     * Add a DropTarget to the list of potential places to receive drop events.
     */
    public void addDropTarget(DropTarget target) {
        mDropTargets.add(target);
    }

    /**
     * Don't send drop events to <em>target</em> any more.
     */
    public void removeDropTarget(DropTarget target) {
        mDropTargets.remove(target);
    }
}

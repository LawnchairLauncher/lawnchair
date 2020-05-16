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

import static com.android.launcher3.AbstractFloatingView.TYPE_DISCOVERY_BOUNCE;
import static com.android.launcher3.LauncherAnimUtils.SPRING_LOADED_EXIT_DELAY;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.Utilities.ATLEAST_Q;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.accessibility.DragViewStateAnnouncer;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.TouchController;

import java.util.ArrayList;

/**
 * Class for initiating a drag within a view or across multiple views.
 */
public class DragController implements DragDriver.EventListener, TouchController {
    private static final boolean PROFILE_DRAWING_DURING_DRAG = false;

    /**
     * When a drag is started from a deep press, you need to drag this much farther than normal to
     * end a pre-drag. See {@link DragOptions.PreDragCondition#shouldStartDrag(double)}.
     */
    private static final int DEEP_PRESS_DISTANCE_FACTOR = 3;

    private final Launcher mLauncher;
    private final FlingToDeleteHelper mFlingToDeleteHelper;

    // temporaries to avoid gc thrash
    private final Rect mRectTemp = new Rect();
    private final int[] mCoordinatesTemp = new int[2];

    /**
     * Drag driver for the current drag/drop operation, or null if there is no active DND operation.
     * It's null during accessible drag operations.
     */
    private DragDriver mDragDriver = null;

    /** Options controlling the drag behavior. */
    private DragOptions mOptions;

    /** Coordinate for motion down event */
    private final Point mMotionDown = new Point();
    /** Coordinate for last touch event **/
    private final Point mLastTouch = new Point();

    private final Point mTmpPoint = new Point();

    private DropTarget.DragObject mDragObject;

    /** Who can receive drop events */
    private final ArrayList<DropTarget> mDropTargets = new ArrayList<>();
    private final ArrayList<DragListener> mListeners = new ArrayList<>();

    private DropTarget mLastDropTarget;

    private int mLastTouchClassification;
    private int mDistanceSinceScroll = 0;

    private boolean mIsInPreDrag;

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
    public DragController(Launcher launcher) {
        mLauncher = launcher;
        mFlingToDeleteHelper = new FlingToDeleteHelper(launcher);
    }

    /**
     * Starts a drag.
     * When the drag is started, the UI automatically goes into spring loaded mode. On a successful
     * drop, it is the responsibility of the {@link DropTarget} to exit out of the spring loaded
     * mode. If the drop was cancelled for some reason, the UI will automatically exit out of this mode.
     *
     * @param b The bitmap to display as the drag image.  It will be re-scaled to the
     *          enlarged size.
     * @param originalView The source view (ie. icon, widget etc.) that is being dragged
     *          and which the DragView represents
     * @param dragLayerX The x position in the DragLayer of the left-top of the bitmap.
     * @param dragLayerY The y position in the DragLayer of the left-top of the bitmap.
     * @param source An object representing where the drag originated
     * @param dragInfo The data associated with the object that is being dragged
     * @param dragRegion Coordinates within the bitmap b for the position of item being dragged.
     *          Makes dragging feel more precise, e.g. you can clip out a transparent border
     */
    public DragView startDrag(Bitmap b, DraggableView originalView, int dragLayerX, int dragLayerY,
            DragSource source, ItemInfo dragInfo, Point dragOffset, Rect dragRegion,
            float initialDragViewScale, float dragViewScaleOnDrop, DragOptions options) {
        if (PROFILE_DRAWING_DURING_DRAG) {
            android.os.Debug.startMethodTracing("Launcher");
        }

        mLauncher.hideKeyboard();
        AbstractFloatingView.closeOpenViews(mLauncher, false, TYPE_DISCOVERY_BOUNCE);

        mOptions = options;
        if (mOptions.simulatedDndStartPoint != null) {
            mLastTouch.x = mMotionDown.x = mOptions.simulatedDndStartPoint.x;
            mLastTouch.y = mMotionDown.y = mOptions.simulatedDndStartPoint.y;
        }

        final int registrationX = mMotionDown.x - dragLayerX;
        final int registrationY = mMotionDown.y - dragLayerY;

        final int dragRegionLeft = dragRegion == null ? 0 : dragRegion.left;
        final int dragRegionTop = dragRegion == null ? 0 : dragRegion.top;

        mLastDropTarget = null;

        mDragObject = new DropTarget.DragObject(mLauncher.getApplicationContext());
        mDragObject.originalView = originalView;

        mIsInPreDrag = mOptions.preDragCondition != null
                && !mOptions.preDragCondition.shouldStartDrag(0);

        final Resources res = mLauncher.getResources();
        final float scaleDps = mIsInPreDrag
                ? res.getDimensionPixelSize(R.dimen.pre_drag_view_scale) : 0f;
        final DragView dragView = mDragObject.dragView = new DragView(mLauncher, b, registrationX,
                registrationY, initialDragViewScale, dragViewScaleOnDrop, scaleDps);
        dragView.setItemInfo(dragInfo);
        mDragObject.dragComplete = false;

        mDragObject.xOffset = mMotionDown.x - (dragLayerX + dragRegionLeft);
        mDragObject.yOffset = mMotionDown.y - (dragLayerY + dragRegionTop);

        mDragDriver = DragDriver.create(this, mOptions, mFlingToDeleteHelper::recordMotionEvent);
        if (!mOptions.isAccessibleDrag) {
            mDragObject.stateAnnouncer = DragViewStateAnnouncer.createFor(dragView);
        }

        mDragObject.dragSource = source;
        mDragObject.dragInfo = dragInfo;
        mDragObject.originalDragInfo = mDragObject.dragInfo.makeShallowCopy();

        if (dragOffset != null) {
            dragView.setDragVisualizeOffset(new Point(dragOffset));
        }
        if (dragRegion != null) {
            dragView.setDragRegion(new Rect(dragRegion));
        }

        mLauncher.getDragLayer().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        dragView.show(mLastTouch.x, mLastTouch.y);
        mDistanceSinceScroll = 0;

        if (!mIsInPreDrag) {
            callOnDragStart();
        } else if (mOptions.preDragCondition != null) {
            mOptions.preDragCondition.onPreDragStart(mDragObject);
        }

        handleMoveEvent(mLastTouch.x, mLastTouch.y);
        mLauncher.getUserEventDispatcher().resetActionDurationMillis();

        if (!mLauncher.isTouchInProgress() && options.simulatedDndStartPoint == null) {
            // If it is an internal drag and the touch is already complete, cancel immediately
            MAIN_EXECUTOR.submit(this::cancelDrag);
        }
        return dragView;
    }

    private void callOnDragStart() {
        if (mOptions.preDragCondition != null) {
            mOptions.preDragCondition.onPreDragEnd(mDragObject, true /* dragStarted*/);
        }
        mIsInPreDrag = false;
        for (DragListener listener : new ArrayList<>(mListeners)) {
            listener.onDragStart(mDragObject, mOptions);
        }
    }

    public void addFirstFrameAnimationHelper(ValueAnimator anim) {
        if (mDragObject != null && mDragObject.dragView != null) {
            mDragObject.dragView.mFirstFrameAnimatorHelper.addTo(anim);
        }
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
            mLauncher.getStateManager().goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);
            mDragObject.deferDragViewCleanupPostAnimation = false;
        }

        mDragObject.dragSource.onDropCompleted(dropTarget, mDragObject, accepted);
    }

    public void onAppsRemoved(ItemInfoMatcher matcher) {
        // Cancel the current drag if we are removing an app that we are dragging
        if (mDragObject != null) {
            ItemInfo dragInfo = mDragObject.dragInfo;
            if (dragInfo instanceof WorkspaceItemInfo) {
                ComponentName cn = dragInfo.getTargetComponent();
                if (cn != null && matcher.matches(dragInfo, cn)) {
                    cancelDrag();
                }
            }
        }
    }

    private void endDrag() {
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
                mDragObject.dragView = null;
            }

            // Only end the drag if we are not deferred
            if (!isDeferred) {
                callOnDragEnd();
            }
        }

        mFlingToDeleteHelper.releaseVelocityTracker();
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

    private void callOnDragEnd() {
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
    private Point getClampedDragLayerPos(float x, float y) {
        mLauncher.getDragLayer().getLocalVisibleRect(mRectTemp);
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
        DropTarget dropTarget;
        Runnable flingAnimation = mFlingToDeleteHelper.getFlingAnimation(mDragObject, mOptions);
        if (flingAnimation != null) {
            dropTarget = mFlingToDeleteHelper.getDropTarget();
        } else {
            dropTarget = findDropTarget((int) x, (int) y, mCoordinatesTemp);
        }

        drop(dropTarget, flingAnimation);

        endDrag();
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

        Point dragLayerPos = getClampedDragLayerPos(ev.getX(), ev.getY());
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

    private void handleMoveEvent(int x, int y) {
        mDragObject.dragView.move(x, y);

        // Drop on someone?
        final int[] coordinates = mCoordinatesTemp;
        DropTarget dropTarget = findDropTarget(x, y, coordinates);
        mDragObject.x = coordinates[0];
        mDragObject.y = coordinates[1];
        checkTouchMove(dropTarget);

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
    }

    public float getDistanceDragged() {
        return mDistanceSinceScroll;
    }

    public void forceTouchMove() {
        int[] dummyCoordinates = mCoordinatesTemp;
        DropTarget dropTarget = findDropTarget(mLastTouch.x, mLastTouch.y, dummyCoordinates);
        mDragObject.x = dummyCoordinates[0];
        mDragObject.y = dummyCoordinates[1];
        checkTouchMove(dropTarget);
    }

    private void checkTouchMove(DropTarget dropTarget) {
        if (dropTarget != null) {
            if (mLastDropTarget != dropTarget) {
                if (mLastDropTarget != null) {
                    mLastDropTarget.onDragExit(mDragObject);
                }
                dropTarget.onDragEnter(mDragObject);
            }
            dropTarget.onDragOver(mDragObject);
        } else {
            if (mLastDropTarget != null) {
                mLastDropTarget.onDragExit(mDragObject);
            }
        }
        mLastDropTarget = dropTarget;
    }

    /**
     * As above, since accessible drag and drop won't cause the same sequence of touch events,
     * we manually ensure appropriate drag and drop events get emulated for accessible drag.
     */
    public void completeAccessibleDrag(int[] location) {
        final int[] coordinates = mCoordinatesTemp;

        // We make sure that we prime the target for drop.
        DropTarget dropTarget = findDropTarget(location[0], location[1], coordinates);
        mDragObject.x = coordinates[0];
        mDragObject.y = coordinates[1];
        checkTouchMove(dropTarget);

        dropTarget.prepareAccessibilityDrop();
        // Perform the drop
        drop(dropTarget, null);
        endDrag();
    }

    private void drop(DropTarget dropTarget, Runnable flingAnimation) {
        final int[] coordinates = mCoordinatesTemp;
        mDragObject.x = coordinates[0];
        mDragObject.y = coordinates[1];

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
        mLauncher.getUserEventDispatcher().logDragNDrop(mDragObject, dropTargetAsView);
        dispatchDropComplete(dropTargetAsView, accepted);
    }

    private DropTarget findDropTarget(int x, int y, int[] dropCoordinates) {
        mDragObject.x = x;
        mDragObject.y = y;

        final Rect r = mRectTemp;
        final ArrayList<DropTarget> dropTargets = mDropTargets;
        final int count = dropTargets.size();
        for (int i = count - 1; i >= 0; i--) {
            DropTarget target = dropTargets.get(i);
            if (!target.isDropEnabled())
                continue;

            target.getHitRectRelativeToDragLayer(r);
            if (r.contains(x, y)) {
                dropCoordinates[0] = x;
                dropCoordinates[1] = y;
                mLauncher.getDragLayer().mapCoordInSelfToDescendant((View) target, dropCoordinates);
                return target;
            }
        }
        // Pass all unhandled drag to workspace. Workspace finds the correct
        // cell layout to drop to in the existing drag/drop logic.
        dropCoordinates[0] = x;
        dropCoordinates[1] = y;
        mLauncher.getDragLayer().mapCoordInSelfToDescendant(mLauncher.getWorkspace(),
                dropCoordinates);
        return mLauncher.getWorkspace();
    }

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

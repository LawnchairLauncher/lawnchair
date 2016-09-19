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

import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Handler;
import android.os.IBinder;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.InputMethodManager;

import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.PagedView;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.accessibility.DragViewStateAnnouncer;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.util.TouchController;

import java.util.ArrayList;

/**
 * Class for initiating a drag within a view or across multiple views.
 */
public class DragController implements DragDriver.EventListener, TouchController {
    private static final String TAG = "Launcher.DragController";

    public static final int SCROLL_DELAY = 500;
    public static final int RESCROLL_DELAY = PagedView.PAGE_SNAP_ANIMATION_DURATION + 150;

    private static final boolean PROFILE_DRAWING_DURING_DRAG = false;

    private static final int SCROLL_OUTSIDE_ZONE = 0;
    private static final int SCROLL_WAITING_IN_ZONE = 1;

    public static final int SCROLL_NONE = -1;
    public static final int SCROLL_LEFT = 0;
    public static final int SCROLL_RIGHT = 1;

    private static final float MAX_FLING_DEGREES = 35f;

    @Thunk Launcher mLauncher;
    private Handler mHandler;

    // temporaries to avoid gc thrash
    private Rect mRectTemp = new Rect();
    private final int[] mCoordinatesTemp = new int[2];
    private final boolean mIsRtl;

    /**
     * Drag driver for the current drag/drop operation, or null if there is no active DND operation.
     * It's null during accessible drag operations.
     */
    private DragDriver mDragDriver = null;

    /** Options controlling the drag behavior. */
    private DragOptions mOptions;

    /** X coordinate of the down event. */
    private int mMotionDownX;

    /** Y coordinate of the down event. */
    private int mMotionDownY;

    /** the area at the edge of the screen that makes the workspace go left
     *   or right while you're dragging.
     */
    private final int mScrollZone;

    private DropTarget.DragObject mDragObject;

    /** Who can receive drop events */
    private ArrayList<DropTarget> mDropTargets = new ArrayList<DropTarget>();
    private ArrayList<DragListener> mListeners = new ArrayList<DragListener>();
    private DropTarget mFlingToDeleteDropTarget;

    /** The window token used as the parent for the DragView. */
    private IBinder mWindowToken;

    /** The view that will be scrolled when dragging to the left and right edges of the screen. */
    private View mScrollView;

    private View mMoveTarget;

    @Thunk DragScroller mDragScroller;
    @Thunk int mScrollState = SCROLL_OUTSIDE_ZONE;
    private ScrollRunnable mScrollRunnable = new ScrollRunnable();

    private DropTarget mLastDropTarget;

    private InputMethodManager mInputMethodManager;

    @Thunk int mLastTouch[] = new int[2];
    @Thunk long mLastTouchUpTime = -1;
    @Thunk int mDistanceSinceScroll = 0;

    private int mTmpPoint[] = new int[2];
    private Rect mDragLayerRect = new Rect();

    protected final int mFlingToDeleteThresholdVelocity;
    private VelocityTracker mVelocityTracker;

    private boolean mIsDragDeferred;

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
        Resources r = launcher.getResources();
        mLauncher = launcher;
        mHandler = new Handler();
        mScrollZone = r.getDimensionPixelSize(R.dimen.scroll_zone);
        mVelocityTracker = VelocityTracker.obtain();

        mFlingToDeleteThresholdVelocity =
                r.getDimensionPixelSize(R.dimen.drag_flingToDeleteMinVelocity);
        mIsRtl = Utilities.isRtl(r);
    }

    /**
     * Starts a drag.
     *
     * @param v The view that is being dragged
     * @param bmp The bitmap that represents the view being dragged
     * @param source An object representing where the drag originated
     * @param dragInfo The data associated with the object that is being dragged
     * @param viewImageBounds the position of the image inside the view
     */
    public void startDrag(View v, Bitmap bmp, DragSource source, ItemInfo dragInfo,
            Rect viewImageBounds, float initialDragViewScale, DragOptions options) {
        int[] loc = mCoordinatesTemp;
        mLauncher.getDragLayer().getLocationInDragLayer(v, loc);
        int dragLayerX = loc[0] + viewImageBounds.left
                + (int) ((initialDragViewScale * bmp.getWidth() - bmp.getWidth()) / 2);
        int dragLayerY = loc[1] + viewImageBounds.top
                + (int) ((initialDragViewScale * bmp.getHeight() - bmp.getHeight()) / 2);

        startDrag(bmp, dragLayerX, dragLayerY, source, dragInfo, null,
                null, initialDragViewScale, options);
    }

    /**
     * Starts a drag.
     *
     * @param b The bitmap to display as the drag image.  It will be re-scaled to the
     *          enlarged size.
     * @param dragLayerX The x position in the DragLayer of the left-top of the bitmap.
     * @param dragLayerY The y position in the DragLayer of the left-top of the bitmap.
     * @param source An object representing where the drag originated
     * @param dragInfo The data associated with the object that is being dragged
     * @param dragRegion Coordinates within the bitmap b for the position of item being dragged.
     *          Makes dragging feel more precise, e.g. you can clip out a transparent border
     */
    public DragView startDrag(Bitmap b, int dragLayerX, int dragLayerY,
            DragSource source, ItemInfo dragInfo, Point dragOffset, Rect dragRegion,
            float initialDragViewScale, DragOptions options) {
        if (PROFILE_DRAWING_DURING_DRAG) {
            android.os.Debug.startMethodTracing("Launcher");
        }

        // Hide soft keyboard, if visible
        if (mInputMethodManager == null) {
            mInputMethodManager = (InputMethodManager)
                    mLauncher.getSystemService(Context.INPUT_METHOD_SERVICE);
        }
        mInputMethodManager.hideSoftInputFromWindow(mWindowToken, 0);

        mOptions = options;
        if (mOptions.systemDndStartPoint != null) {
            mMotionDownX = mOptions.systemDndStartPoint.x;
            mMotionDownY = mOptions.systemDndStartPoint.y;
        }

        final int registrationX = mMotionDownX - dragLayerX;
        final int registrationY = mMotionDownY - dragLayerY;

        final int dragRegionLeft = dragRegion == null ? 0 : dragRegion.left;
        final int dragRegionTop = dragRegion == null ? 0 : dragRegion.top;

        mLastDropTarget = null;

        mDragObject = new DropTarget.DragObject();

        mIsDragDeferred = !mOptions.deferDragCondition.shouldStartDeferredDrag(0);

        final Resources res = mLauncher.getResources();
        final float scaleDps = FeatureFlags.LAUNCHER3_LEGACY_WORKSPACE_DND
                ? res.getDimensionPixelSize(R.dimen.dragViewScale)
                : mIsDragDeferred
                    ? res.getDimensionPixelSize(R.dimen.deferred_drag_view_scale)
                    : 0f;
        final DragView dragView = mDragObject.dragView = new DragView(mLauncher, b, registrationX,
                registrationY, initialDragViewScale, scaleDps);

        mDragObject.dragComplete = false;
        if (mOptions.isAccessibleDrag) {
            // For an accessible drag, we assume the view is being dragged from the center.
            mDragObject.xOffset = b.getWidth() / 2;
            mDragObject.yOffset = b.getHeight() / 2;
            mDragObject.accessibleDrag = true;
        } else {
            mDragObject.xOffset = mMotionDownX - (dragLayerX + dragRegionLeft);
            mDragObject.yOffset = mMotionDownY - (dragLayerY + dragRegionTop);
            mDragObject.stateAnnouncer = DragViewStateAnnouncer.createFor(dragView);

            mDragDriver = DragDriver.create(mLauncher, this, mDragObject, mOptions);
        }

        mDragObject.dragSource = source;
        mDragObject.dragInfo = dragInfo;
        mDragObject.originalDragInfo = new ItemInfo();
        mDragObject.originalDragInfo.copyFrom(dragInfo);

        if (dragOffset != null) {
            dragView.setDragVisualizeOffset(new Point(dragOffset));
        }
        if (dragRegion != null) {
            dragView.setDragRegion(new Rect(dragRegion));
        }

        mLauncher.getDragLayer().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        dragView.show(mMotionDownX, mMotionDownY);
        mDistanceSinceScroll = 0;

        if (!mIsDragDeferred) {
            startDeferredDrag();
        } else {
            mOptions.deferDragCondition.onDeferredDragStart();
        }

        mLastTouch[0] = mMotionDownX;
        mLastTouch[1] = mMotionDownY;
        handleMoveEvent(mMotionDownX, mMotionDownY);
        mLauncher.getUserEventDispatcher().resetActionDurationMillis();
        return dragView;
    }

    public boolean isDeferringDrag() {
        return mIsDragDeferred;
    }

    public void startDeferredDrag() {
        for (DragListener listener : new ArrayList<>(mListeners)) {
            listener.onDragStart(mDragObject, mOptions);
        }
        mOptions.deferDragCondition.onDragStart();
        mIsDragDeferred = false;
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

    public boolean isExternalDrag() {
        return (mOptions != null && mOptions.systemDndStartPoint != null);
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
            mDragObject.dragSource.onDropCompleted(null, mDragObject, false, false);
        }
        endDrag();
    }

    public void onAppsRemoved(ItemInfoMatcher matcher) {
        // Cancel the current drag if we are removing an app that we are dragging
        if (mDragObject != null) {
            ItemInfo dragInfo = mDragObject.dragInfo;
            if (dragInfo instanceof ShortcutInfo) {
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
            mOptions = null;
            clearScrollRunnable();
            boolean isDeferred = false;
            if (mDragObject.dragView != null) {
                isDeferred = mDragObject.deferDragViewCleanupPostAnimation;
                if (!isDeferred) {
                    mDragObject.dragView.remove();
                }
                mDragObject.dragView = null;
            }

            // Only end the drag if we are not deferred
            if (!isDeferred) {
                for (DragListener listener : new ArrayList<>(mListeners)) {
                    listener.onDragEnd();
                }
            }
        }

        releaseVelocityTracker();
    }

    /**
     * This only gets called as a result of drag view cleanup being deferred in endDrag();
     */
    void onDeferredEndDrag(DragView dragView) {
        dragView.remove();

        if (mDragObject.deferDragViewCleanupPostAnimation) {
            // If we skipped calling onDragEnd() before, do it now
            for (DragListener listener : new ArrayList<>(mListeners)) {
                listener.onDragEnd();
            }
        }
    }

    public void onDeferredEndFling(DropTarget.DragObject d) {
        d.dragSource.onFlingToDeleteCompleted();
    }

    /**
     * Clamps the position to the drag layer bounds.
     */
    private int[] getClampedDragLayerPos(float x, float y) {
        mLauncher.getDragLayer().getLocalVisibleRect(mDragLayerRect);
        mTmpPoint[0] = (int) Math.max(mDragLayerRect.left, Math.min(x, mDragLayerRect.right - 1));
        mTmpPoint[1] = (int) Math.max(mDragLayerRect.top, Math.min(y, mDragLayerRect.bottom - 1));
        return mTmpPoint;
    }

    public long getLastGestureUpTime() {
        if (mDragDriver != null) {
            return System.currentTimeMillis();
        } else {
            return mLastTouchUpTime;
        }
    }

    public void resetLastGestureUpTime() {
        mLastTouchUpTime = -1;
    }

    @Override
    public void onDriverDragMove(float x, float y) {
        final int[] dragLayerPos = getClampedDragLayerPos(x, y);

        handleMoveEvent(dragLayerPos[0], dragLayerPos[1]);
    }

    @Override
    public void onDriverDragExitWindow() {
        if (mLastDropTarget != null) {
            mLastDropTarget.onDragExit(mDragObject);
            mLastDropTarget = null;
        }
    }

    @Override
    public void onDriverDragEnd(float x, float y, DropTarget dropTargetOverride) {
        DropTarget dropTarget;
        PointF vec = null;

        if (dropTargetOverride != null) {
            dropTarget = dropTargetOverride;
        } else {
            vec = isFlingingToDelete(mDragObject.dragSource);
            if (vec != null) {
                dropTarget = mFlingToDeleteDropTarget;
            } else {
                dropTarget = findDropTarget((int) x, (int) y, mCoordinatesTemp);
            }
        }

        drop(dropTarget, x, y, vec);

        endDrag();
    }

    @Override
    public void onDriverDragCancel() {
        cancelDrag();
    }

    /**
     * Call this from a drag source view.
     */
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mOptions != null && mOptions.isAccessibleDrag) {
            return false;
        }

        // Update the velocity tracker
        acquireVelocityTrackerAndAddMovement(ev);

        final int action = ev.getAction();
        final int[] dragLayerPos = getClampedDragLayerPos(ev.getX(), ev.getY());
        final int dragLayerX = dragLayerPos[0];
        final int dragLayerY = dragLayerPos[1];

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // Remember location of down touch
                mMotionDownX = dragLayerX;
                mMotionDownY = dragLayerY;
                break;
            case MotionEvent.ACTION_UP:
                mLastTouchUpTime = System.currentTimeMillis();
                break;
        }

        return mDragDriver != null && mDragDriver.onInterceptTouchEvent(ev);
    }

    /**
     * Call this from a drag source view.
     */
    public boolean onDragEvent(DragEvent event) {
        return mDragDriver != null && mDragDriver.onDragEvent(event);
    }

    /**
     * Call this from a drag view.
     */
    public void onDragViewAnimationEnd() {
        if (mDragDriver != null) {
            mDragDriver.onDragViewAnimationEnd();
        }
    }

    /**
     * Sets the view that should handle move events.
     */
    public void setMoveTarget(View view) {
        mMoveTarget = view;
    }

    public boolean dispatchUnhandledMove(View focused, int direction) {
        return mMoveTarget != null && mMoveTarget.dispatchUnhandledMove(focused, direction);
    }

    private void clearScrollRunnable() {
        mHandler.removeCallbacks(mScrollRunnable);
        if (mScrollState == SCROLL_WAITING_IN_ZONE) {
            mScrollState = SCROLL_OUTSIDE_ZONE;
            mScrollRunnable.setDirection(SCROLL_RIGHT);
            mDragScroller.onExitScrollArea();
            mLauncher.getDragLayer().onExitScrollArea();
        }
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
        mDistanceSinceScroll += Math.hypot(mLastTouch[0] - x, mLastTouch[1] - y);
        mLastTouch[0] = x;
        mLastTouch[1] = y;
        checkScrollState(x, y);

        if (mIsDragDeferred && mOptions.deferDragCondition.shouldStartDeferredDrag(
                Math.hypot(x - mMotionDownX, y - mMotionDownY))) {
            startDeferredDrag();
        }
    }

    public float getDistanceDragged() {
        return mDistanceSinceScroll;
    }

    public void forceTouchMove() {
        int[] dummyCoordinates = mCoordinatesTemp;
        DropTarget dropTarget = findDropTarget(mLastTouch[0], mLastTouch[1], dummyCoordinates);
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

    @Thunk void checkScrollState(int x, int y) {
        final int slop = ViewConfiguration.get(mLauncher).getScaledWindowTouchSlop();
        final int delay = mDistanceSinceScroll < slop ? RESCROLL_DELAY : SCROLL_DELAY;
        final DragLayer dragLayer = mLauncher.getDragLayer();
        final int forwardDirection = mIsRtl ? SCROLL_RIGHT : SCROLL_LEFT;
        final int backwardsDirection = mIsRtl ? SCROLL_LEFT : SCROLL_RIGHT;

        if (x < mScrollZone) {
            if (mScrollState == SCROLL_OUTSIDE_ZONE) {
                mScrollState = SCROLL_WAITING_IN_ZONE;
                if (mDragScroller.onEnterScrollArea(x, y, forwardDirection)) {
                    dragLayer.onEnterScrollArea();
                    mScrollRunnable.setDirection(forwardDirection);
                    mHandler.postDelayed(mScrollRunnable, delay);
                }
            }
        } else if (x > mScrollView.getWidth() - mScrollZone) {
            if (mScrollState == SCROLL_OUTSIDE_ZONE) {
                mScrollState = SCROLL_WAITING_IN_ZONE;
                if (mDragScroller.onEnterScrollArea(x, y, backwardsDirection)) {
                    dragLayer.onEnterScrollArea();
                    mScrollRunnable.setDirection(backwardsDirection);
                    mHandler.postDelayed(mScrollRunnable, delay);
                }
            }
        } else {
            clearScrollRunnable();
        }
    }

    /**
     * Call this from a drag source view.
     */
    public boolean onTouchEvent(MotionEvent ev) {
        if (mDragDriver == null || mOptions == null || mOptions.isAccessibleDrag) {
            return false;
        }

        // Update the velocity tracker
        acquireVelocityTrackerAndAddMovement(ev);

        final int action = ev.getAction();
        final int[] dragLayerPos = getClampedDragLayerPos(ev.getX(), ev.getY());
        final int dragLayerX = dragLayerPos[0];
        final int dragLayerY = dragLayerPos[1];

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // Remember where the motion event started
                mMotionDownX = dragLayerX;
                mMotionDownY = dragLayerY;

                if ((dragLayerX < mScrollZone) || (dragLayerX > mScrollView.getWidth() - mScrollZone)) {
                    mScrollState = SCROLL_WAITING_IN_ZONE;
                    mHandler.postDelayed(mScrollRunnable, SCROLL_DELAY);
                } else {
                    mScrollState = SCROLL_OUTSIDE_ZONE;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mHandler.removeCallbacks(mScrollRunnable);
                break;
        }

        return mDragDriver.onTouchEvent(ev);
    }

    /**
     * Since accessible drag and drop won't cause the same sequence of touch events, we manually
     * inject the appropriate state.
     */
    public void prepareAccessibleDrag(int x, int y) {
        mMotionDownX = x;
        mMotionDownY = y;
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
        drop(dropTarget, location[0], location[1], null);
        endDrag();
    }

    /**
     * Determines whether the user flung the current item to delete it.
     *
     * @return the vector at which the item was flung, or null if no fling was detected.
     */
    private PointF isFlingingToDelete(DragSource source) {
        if (mFlingToDeleteDropTarget == null) return null;
        if (!source.supportsFlingToDelete()) return null;

        ViewConfiguration config = ViewConfiguration.get(mLauncher);
        mVelocityTracker.computeCurrentVelocity(1000, config.getScaledMaximumFlingVelocity());
        PointF vel = new PointF(mVelocityTracker.getXVelocity(), mVelocityTracker.getYVelocity());
        float theta = MAX_FLING_DEGREES + 1;
        if (mVelocityTracker.getYVelocity() < mFlingToDeleteThresholdVelocity) {
            // Do a quick dot product test to ensure that we are flinging upwards
            PointF upVec = new PointF(0f, -1f);
            theta = getAngleBetweenVectors(vel, upVec);
        } else if (mLauncher.getDeviceProfile().isVerticalBarLayout() &&
                mVelocityTracker.getXVelocity() < mFlingToDeleteThresholdVelocity) {
            // Remove icon is on left side instead of top, so check if we are flinging to the left.
            PointF leftVec = new PointF(-1f, 0f);
            theta = getAngleBetweenVectors(vel, leftVec);
        }
        if (theta <= Math.toRadians(MAX_FLING_DEGREES)) {
            return vel;
        }
        return null;
    }

    private float getAngleBetweenVectors(PointF vec1, PointF vec2) {
        return (float) Math.acos(((vec1.x * vec2.x) + (vec1.y * vec2.y)) /
                (vec1.length() * vec2.length()));
    }

    void drop(DropTarget dropTarget, float x, float y, PointF flingVel) {
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

        // Drop onto the target.
        boolean accepted = false;
        if (dropTarget != null) {
            dropTarget.onDragExit(mDragObject);
            if (dropTarget.acceptDrop(mDragObject)) {
                if (flingVel != null) {
                    dropTarget.onFlingToDelete(mDragObject, flingVel);
                } else {
                    dropTarget.onDrop(mDragObject);
                }
                accepted = true;
            }
        }
        final View dropTargetAsView = dropTarget instanceof View ? (View) dropTarget : null;
        mDragObject.dragSource.onDropCompleted(
                dropTargetAsView, mDragObject, flingVel != null, accepted);
        mLauncher.getUserEventDispatcher().logDragNDrop(mDragObject, dropTargetAsView);
        if (mIsDragDeferred) {
            mOptions.deferDragCondition.onDropBeforeDeferredDrag();
        }
    }

    private DropTarget findDropTarget(int x, int y, int[] dropCoordinates) {
        final Rect r = mRectTemp;

        final ArrayList<DropTarget> dropTargets = mDropTargets;
        final int count = dropTargets.size();
        for (int i=count-1; i>=0; i--) {
            DropTarget target = dropTargets.get(i);
            if (!target.isDropEnabled())
                continue;

            target.getHitRectRelativeToDragLayer(r);

            mDragObject.x = x;
            mDragObject.y = y;
            if (r.contains(x, y)) {

                dropCoordinates[0] = x;
                dropCoordinates[1] = y;
                mLauncher.getDragLayer().mapCoordInSelfToDescendent((View) target, dropCoordinates);

                return target;
            }
        }
        return null;
    }

    public void setDragScoller(DragScroller scroller) {
        mDragScroller = scroller;
    }

    public void setWindowToken(IBinder token) {
        mWindowToken = token;
    }

    /**
     * Sets the drag listner which will be notified when a drag starts or ends.
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

    /**
     * Sets the current fling-to-delete drop target.
     */
    public void setFlingToDeleteDropTarget(DropTarget target) {
        mFlingToDeleteDropTarget = target;
    }

    private void acquireVelocityTrackerAndAddMovement(MotionEvent ev) {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);
    }

    private void releaseVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    /**
     * Set which view scrolls for touch events near the edge of the screen.
     */
    public void setScrollView(View v) {
        mScrollView = v;
    }

    private class ScrollRunnable implements Runnable {
        private int mDirection;

        ScrollRunnable() {
        }

        public void run() {
            if (mDragScroller != null) {
                if (mDirection == SCROLL_LEFT) {
                    mDragScroller.scrollLeft();
                } else {
                    mDragScroller.scrollRight();
                }
                mScrollState = SCROLL_OUTSIDE_ZONE;
                mDistanceSinceScroll = 0;
                mDragScroller.onExitScrollArea();
                mLauncher.getDragLayer().onExitScrollArea();

                if (isDragging()) {
                    // Check the scroll again so that we can requeue the scroller if necessary
                    checkScrollState(mLastTouch[0], mLastTouch[1]);
                }
            }
        }

        void setDirection(int direction) {
            mDirection = direction;
        }
    }
}

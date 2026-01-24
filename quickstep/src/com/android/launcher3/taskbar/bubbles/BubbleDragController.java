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
package com.android.launcher3.taskbar.bubbles;

import android.annotation.SuppressLint;
import android.graphics.Point;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.FloatPropertyCompat;

import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.bubbles.BubbleBarController.BubbleBarLocationListener;
import com.android.wm.shell.shared.bubbles.BaseBubblePinController.LocationChangeListener;
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper;
import com.android.wm.shell.shared.bubbles.BubbleBarLocation;
import com.android.wm.shell.shared.bubbles.DeviceConfig;
import com.android.wm.shell.shared.bubbles.DragZone;
import com.android.wm.shell.shared.bubbles.DragZoneFactory;
import com.android.wm.shell.shared.bubbles.DragZoneFactory.BubbleBarPropertiesProvider;
import com.android.wm.shell.shared.bubbles.DragZoneFactory.DesktopWindowModeChecker;
import com.android.wm.shell.shared.bubbles.DragZoneFactory.SplitScreenModeChecker;
import com.android.wm.shell.shared.bubbles.DraggedObject;
import com.android.wm.shell.shared.bubbles.DropTargetManager;
import com.android.wm.shell.shared.bubbles.DropTargetManager.DragZoneChangedListener;

/**
 * Controls bubble bar drag interactions.
 * Interacts with {@link BubbleDismissController}, used by {@link BubbleBarViewController}.
 * Supported interactions:
 * - Drag a single bubble view into dismiss target to remove it.
 * - Drag the bubble stack into dismiss target to remove all.
 * Restores initial position of dragged view if released outside of the dismiss target.
 */
public class BubbleDragController {

    /**
     * Property to update dragged bubble x-translation value.
     * <p>
     * When applied to {@link BubbleView}, will use set the translation through
     * {@link BubbleView#getDragTranslationX()} and {@link BubbleView#setDragTranslationX(float)}
     * methods.
     * <p>
     * When applied to {@link BubbleBarView}, will use {@link View#getTranslationX()} and
     * {@link View#setTranslationX(float)}.
     */
    public static final FloatPropertyCompat<View> DRAG_TRANSLATION_X = new FloatPropertyCompat<>(
            "dragTranslationX") {
        @Override
        public float getValue(View view) {
            if (view instanceof BubbleView bubbleView) {
                return bubbleView.getDragTranslationX();
            }
            return view.getTranslationX();
        }

        @Override
        public void setValue(View view, float value) {
            if (view instanceof BubbleView bubbleView) {
                bubbleView.setDragTranslationX(value);
            } else {
                view.setTranslationX(value);
            }
        }
    };

    private final TaskbarActivityContext mActivity;
    private BubbleBarController mBubbleBarController;
    private BubbleBarViewController mBubbleBarViewController;
    private BubbleDismissController mBubbleDismissController;
    private BubbleBarPinController mBubbleBarPinController;
    private BubblePinController mBubblePinController;
    private BubbleBarLocationListener mBubbleBarLocationListener;
    private final DropTargetManager mDropTargetManager;
    private final DragZoneFactory mDragZoneFactory;
    private final BubbleDragZoneChangedListener mBubbleDragZoneChangedListener;

    private boolean mIsDragging;

    public BubbleDragController(TaskbarActivityContext activity, FrameLayout dropTargetParent) {
        mActivity = activity;
        WindowManager windowManager =
                mActivity.getApplicationContext().getSystemService(WindowManager.class);
        DeviceConfig deviceConfig =
                DeviceConfig.create(mActivity.getApplicationContext(), windowManager);
        SplitScreenModeChecker splitScreenModeChecker = new SplitScreenModeChecker() {
            @NonNull
            @Override
            public SplitScreenMode getSplitScreenMode() {
                return SplitScreenMode.NONE;
            }
        };
        DesktopWindowModeChecker desktopWindowModeChecker = new DesktopWindowModeChecker() {
            @Override
            public boolean isSupported() {
                return false;
            }
        };
        BubbleBarPropertiesProvider bubbleBarPropertiesProvider =
                new BubbleBarPropertiesProvider() {
                    @Override
                    public int getHeight() {
                        return (int) mBubbleBarViewController.getBubbleBarCollapsedHeight();
                    }

                    @Override
                    public int getWidth() {
                        return (int) mBubbleBarViewController.getBubbleBarCollapsedWidth();
                    }

                    @Override
                    public int getBottomPadding() {
                        return (int) -mBubbleBarViewController.getBubbleBarTranslationY().value;
                    }
                };
        mDragZoneFactory = new DragZoneFactory(mActivity.getApplicationContext(), deviceConfig,
                splitScreenModeChecker, desktopWindowModeChecker, bubbleBarPropertiesProvider);
        mBubbleDragZoneChangedListener = new BubbleDragZoneChangedListener();
        mDropTargetManager = new DropTargetManager(mActivity.getApplicationContext(),
                dropTargetParent, mBubbleDragZoneChangedListener);
    }

    /**
     * Initializes dependencies when bubble controllers are created.
     * Should be careful to only access things that were created in constructors for now, as some
     * controllers may still be waiting for init().
     */
    public void init(@NonNull BubbleControllers bubbleControllers,
            BubbleBarLocationListener bubbleBarLocationListener) {
        mBubbleBarController = bubbleControllers.bubbleBarController;
        mBubbleBarViewController = bubbleControllers.bubbleBarViewController;
        mBubbleDismissController = bubbleControllers.bubbleDismissController;
        mBubbleBarPinController = bubbleControllers.bubbleBarPinController;
        mBubblePinController = bubbleControllers.bubblePinController;
        mBubbleBarLocationListener = bubbleBarLocationListener;
        mBubbleDismissController.setListener(
                stuck -> {
                    if (stuck) {
                        mBubbleBarPinController.onStuckToDismissTarget();
                        mBubblePinController.onStuckToDismissTarget();
                    }
                });
    }

    /**
     * Setup the bubble view for dragging and attach touch listener to it
     */
    @SuppressLint("ClickableViewAccessibility")
    public void setupBubbleView(@NonNull BubbleView bubbleView) {
        if (!(bubbleView.getBubble() instanceof BubbleBarBubble)) {
            // Don't setup dragging for overflow bubble view
            return;
        }

        bubbleView.setOnTouchListener(new BubbleTouchListener() {

            private BubbleBarLocation mReleasedLocation = BubbleBarLocation.DEFAULT;

            private final LocationChangeListener mLocationChangeListener =
                    new LocationChangeListener() {
                        @Override
                        public void onChange(@NonNull BubbleBarLocation location) {
                            mBubbleBarController.animateBubbleBarLocation(location);
                        }

                        @Override
                        public void onRelease(@NonNull BubbleBarLocation location) {
                            mReleasedLocation = location;
                        }
                    };

            private BubbleBarLocation getBubbleBarLocationDuringDrag() {
                return BubbleAnythingFlagHelper.enableBubbleToFullscreen()
                        ? mBubbleDragZoneChangedListener.mBubbleBarLocation
                        : mReleasedLocation;
            }

            @Override
            void onDragStart() {
                mBubbleBarViewController.onBubbleDragStart(bubbleView);
                if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
                    DraggedObject.Bubble draggedBubble =
                            new DraggedObject.Bubble(
                                    mBubbleBarViewController.getBubbleBarLocation());
                    mDropTargetManager.onDragStarted(draggedBubble,
                            mDragZoneFactory.createSortedDragZones(draggedBubble));
                } else {
                    mBubblePinController.setListener(mLocationChangeListener);
                    mBubblePinController.onDragStart(
                            mBubbleBarViewController.getBubbleBarLocation().isOnLeft(
                                    bubbleView.isLayoutRtl()));
                }
            }

            @Override
            protected void onDragUpdate(float x, float y, float newTx, float newTy) {
                bubbleView.setDragTranslationX(newTx);
                bubbleView.setTranslationY(newTy);
                if (!BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
                    mBubblePinController.onDragUpdate(x, y);
                }
            }

            @Override
            protected void onDragRelease() {
                if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
                    mDropTargetManager.onDragEnded();
                    if (!mBubbleDragZoneChangedListener.isDraggedToFullscreen()) {
                        // TODO b/393173014: check for desktop window and split once they're
                        //  implemented. this notifies wm shell that the dragged bubble was
                        //  released so that we can show the expanded view. we only want to show it
                        //  after releasing in a Bubble zone. But Split and Desktop Window aren't
                        //  implemented yet, so we only check for full screen for now.
                        mBubbleBarViewController.onBubbleDragRelease(
                                getBubbleBarLocationDuringDrag());
                    }
                } else {
                    mBubblePinController.onDragEnd();
                    mBubbleBarViewController.onBubbleDragRelease(getBubbleBarLocationDuringDrag());
                }
            }

            @Override
            protected void onDragDismiss() {
                if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
                    mDropTargetManager.onDragEnded();
                } else {
                    mBubblePinController.onDragEnd();
                }
                mBubbleBarViewController.onBubbleDismissed(bubbleView);
                mBubbleBarViewController.onBubbleDragEnd();
            }

            @Override
            void onDragEnd(float x, float y) {
                mBubbleBarController.updateBubbleBarLocation(getBubbleBarLocationDuringDrag(),
                        BubbleBarLocation.UpdateSource.DRAG_BUBBLE);
                if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
                    mDropTargetManager.onDragEnded();
                    if (mBubbleDragZoneChangedListener.isDraggedToFullscreen()) {
                        mBubbleBarViewController.moveDraggedBubbleToFullscreen(
                                bubbleView, new Point((int) x, (int) y));
                    }
                } else {
                    mBubblePinController.setListener(null);
                }
                mBubbleBarViewController.onBubbleDragEnd();
            }

            @Override
            protected PointF getRestingPosition() {
                return mBubbleBarViewController.getDraggedBubbleReleaseTranslation(
                        getInitialPosition(), getBubbleBarLocationDuringDrag());
            }
        });
    }

    /**
     * Setup the bubble bar view for dragging and attach touch listener to it
     */
    @SuppressLint("ClickableViewAccessibility")
    public void setupBubbleBarView(@NonNull BubbleBarView bubbleBarView) {
        PointF initialRelativePivot = new PointF();
        bubbleBarView.setOnTouchListener(new BubbleTouchListener() {

            private BubbleBarLocation mReleasedLocation = BubbleBarLocation.DEFAULT;

            private final LocationChangeListener mLocationChangeListener =
                    location -> mReleasedLocation = location;

            private BubbleBarLocation getBubbleBarLocationDuringDrag() {
                return BubbleAnythingFlagHelper.enableBubbleToFullscreen()
                        ? mBubbleDragZoneChangedListener.mBubbleBarLocation
                        : mReleasedLocation;
            }

            @Override
            protected boolean onTouchDown(@NonNull View view, @NonNull MotionEvent event) {
                if (bubbleBarView.isExpanded()) return false;
                return super.onTouchDown(view, event);
            }

            @Override
            void onDragStart() {
                initialRelativePivot.set(bubbleBarView.getRelativePivotX(),
                        bubbleBarView.getRelativePivotY());
                // By default the bubble bar view pivot is in bottom right corner, while dragging
                // it should be centered in order to align it with the dismiss target view
                bubbleBarView.setRelativePivot(/* x = */ 0.5f, /* y = */ 0.5f);
                bubbleBarView.setIsDragging(true);
                if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
                    DraggedObject.BubbleBar draggedBubbleBar = new DraggedObject.BubbleBar(
                            mBubbleBarViewController.getBubbleBarLocation());
                    mDropTargetManager.onDragStarted(draggedBubbleBar,
                            mDragZoneFactory.createSortedDragZones(draggedBubbleBar));
                } else {
                    mBubbleBarPinController.setListener(mLocationChangeListener);
                    mBubbleBarPinController.onDragStart(
                            bubbleBarView.getBubbleBarLocation().isOnLeft(
                                    bubbleBarView.isLayoutRtl()));
                }
            }

            @Override
            protected void onDragUpdate(float x, float y, float newTx, float newTy) {
                bubbleBarView.setTranslationX(newTx);
                bubbleBarView.setTranslationY(newTy);
                if (!BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
                    mBubbleBarPinController.onDragUpdate(x, y);
                }
            }

            @Override
            protected void onDragRelease() {
                if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
                    mDropTargetManager.onDragEnded();
                } else {
                    mBubbleBarPinController.onDragEnd();
                }
            }

            @Override
            protected void onDragDismiss() {
                if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
                    mDropTargetManager.onDragEnded();
                } else {
                    mBubbleBarPinController.onDragEnd();
                }
            }

            @Override
            void onDragEnd(float x, float y) {
                // Make sure to update location as the first thing. Pivot update causes a relayout
                mBubbleBarController.updateBubbleBarLocation(getBubbleBarLocationDuringDrag(),
                        BubbleBarLocation.UpdateSource.DRAG_BAR);
                bubbleBarView.setIsDragging(false);
                // Restoring the initial pivot for the bubble bar view
                bubbleBarView.setRelativePivot(initialRelativePivot.x, initialRelativePivot.y);
                mBubbleBarViewController.onBubbleBarDragEnd();
                if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
                    mDropTargetManager.onDragEnded();
                } else {
                    mBubbleBarPinController.setListener(null);
                }
            }

            @Override
            protected PointF getRestingPosition() {
                return mBubbleBarViewController.getBubbleBarDragReleaseTranslation(
                        getInitialPosition(), getBubbleBarLocationDuringDrag());
            }
        });
    }

    /** Whether there is an item being dragged or not. */
    public boolean isDragging() {
        return mIsDragging;
    }

    /** Sets whether something is being dragged or not. */
    public void setIsDragging(boolean isDragging) {
        mIsDragging = isDragging;
    }

    /**
     * Bubble touch listener for handling a single bubble view or bubble bar view while dragging.
     * The dragging starts after "shorter" long click (the long click duration might change):
     * - When the touch gesture moves out of the {@code ACTION_DOWN} location the dragging
     * interaction is cancelled.
     * - When {@code ACTION_UP} happens before long click is registered and there was no significant
     * movement the view will perform click.
     * - When the listener registers long click it starts dragging interaction, all the subsequent
     * {@code ACTION_MOVE} events will drag the view, and the interaction finishes when
     * {@code ACTION_UP} or {@code ACTION_CANCEL} are received.
     * Lifecycle methods can be overridden do add extra setup/clean up steps.
     */
    private abstract class BubbleTouchListener implements View.OnTouchListener {
        /**
         * The internal state of the touch listener
         */
        private enum State {
            // Idle and ready for the touch events.
            // Changes to:
            // - TOUCHED, when the {@code ACTION_DOWN} is handled
            IDLE,

            // Touch down was handled and the lister is recognising the gestures.
            // Changes to:
            // - IDLE, when performs the click
            // - DRAGGING, when registers the long click and starts dragging interaction
            // - CANCELLED, when the touch events move out of the initial location before the long
            // click is recognised

            TOUCHED,

            // The long click was registered and the view is being dragged.
            // Changes to:
            // - IDLE, when the gesture ends with the {@code ACTION_UP} or {@code ACTION_CANCEL}
            DRAGGING,

            // The dragging was cancelled.
            // Changes to:
            // - IDLE, when the current gesture completes
            CANCELLED
        }

        private final PointF mTouchDownLocation = new PointF();
        private final PointF mViewInitialPosition = new PointF();
        private final VelocityTracker mVelocityTracker = VelocityTracker.obtain();
        private final long mPressToDragTimeout = ViewConfiguration.getLongPressTimeout();
        private State mState = State.IDLE;
        private int mTouchSlop = -1;
        private BubbleDragAnimator mAnimator;
        @Nullable
        private Runnable mLongClickRunnable;

        /**
         * Called when the dragging interaction has started
         */
        abstract void onDragStart();

        /**
         * Called when bubble is dragged to new coordinates.
         * Not called while bubble is stuck to the dismiss target.
         */
        protected abstract void onDragUpdate(float x, float y, float newTx, float newTy);

        /**
         * Called when the dragging interaction has ended and all the animations have completed
         */
        abstract void onDragEnd(float x, float y);

        /**
         * Called when the dragged bubble is released outside of the dismiss target area and will
         * move back to its initial position
         */
        protected void onDragRelease() {
        }

        /**
         * Called when the dragged bubble is released inside of the dismiss target area and will get
         * dismissed with animation
         */
        protected void onDragDismiss() {
        }

        /**
         * Get the initial position of the view when drag started
         */
        protected PointF getInitialPosition() {
            return mViewInitialPosition;
        }

        /**
         * Get the resting position of the view when drag is released
         */
        protected PointF getRestingPosition() {
            return mViewInitialPosition;
        }

        @Override
        @SuppressLint("ClickableViewAccessibility")
        public boolean onTouch(@NonNull View view, @NonNull MotionEvent event) {
            updateVelocity(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    return onTouchDown(view, event);
                case MotionEvent.ACTION_MOVE:
                    onTouchMove(view, event);
                    break;
                case MotionEvent.ACTION_UP:
                    onTouchUp(view, event);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    onTouchCancel(view, event);
                    break;
            }
            return true;
        }

        /**
         * The touch down starts the interaction and schedules the long click handler.
         *
         * @param view  the view that received the event
         * @param event the motion event
         * @return true if the gesture should be intercepted and handled, false otherwise. Note if
         * the false is returned subsequent events in the gesture won't get reported.
         */
        protected boolean onTouchDown(@NonNull View view, @NonNull MotionEvent event) {
            mState = State.TOUCHED;
            mTouchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
            mTouchDownLocation.set(event.getRawX(), event.getRawY());
            mViewInitialPosition.set(view.getTranslationX(), view.getTranslationY());
            setupLongClickHandler(view);
            return true;
        }

        /**
         * The move event drags the view or cancels the interaction if hasn't long clicked yet.
         *
         * @param view  the view that received the event
         * @param event the motion event
         */
        protected void onTouchMove(@NonNull View view, @NonNull MotionEvent event) {
            float rawX = event.getRawX();
            float rawY = event.getRawY();
            final float dx = rawX - mTouchDownLocation.x;
            final float dy = rawY - mTouchDownLocation.y;
            switch (mState) {
                case TOUCHED:
                    final boolean movedOut = Math.hypot(dx, dy) > mTouchSlop;
                    if (movedOut) {
                        // Moved out of the initial location before the long click was registered
                        mState = State.CANCELLED;
                        cleanUpLongClickHandler(view);
                    }
                    break;
                case DRAGGING:
                    drag(view, event, dx, dy, rawX, rawY);
                    break;
            }
        }

        /**
         * On touch up performs click or finishes the dragging depending on the state.
         *
         * @param view  the view that received the event
         * @param event the motion event
         */
        protected void onTouchUp(@NonNull View view, @NonNull MotionEvent event) {
            switch (mState) {
                case TOUCHED:
                    view.performClick();
                    cleanUp(view);
                    break;
                case DRAGGING:
                    stopDragging(view, event);
                    break;
                default:
                    cleanUp(view);
                    break;
            }
        }

        /**
         * The gesture is cancelled and the interaction should clean up and complete.
         *
         * @param view  the view that received the event
         * @param event the motion event
         */
        protected void onTouchCancel(@NonNull View view, @NonNull MotionEvent event) {
            if (mState == State.DRAGGING) {
                stopDragging(view, event);
            } else {
                cleanUp(view);
            }
        }

        private void startDragging(@NonNull View view) {
            onDragStart();
            BubbleDragController.this.setIsDragging(true);
            mActivity.setTaskbarWindowFullscreen(true);
            mAnimator = new BubbleDragAnimator(view);
            mAnimator.animateFocused();
            mBubbleDismissController.setupDismissView(view, mAnimator);
            mBubbleDismissController.showDismissView();
        }

        private void drag(@NonNull View view, @NonNull MotionEvent event, float dx, float dy,
                float x, float y) {
            if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
                // notify drop target manager about the new drag location regardless of whether we
                // are in the dismiss zone so that it can keep track of the current zone and update
                // the drop target view
                mDropTargetManager.onDragUpdated((int) x, (int) y);
            }
            if (mBubbleDismissController.handleTouchEvent(event)) {
                // if we're dragging within the dismiss target, return immediately; the dragged
                // object is manipulated by the dismiss target
                return;
            }
            final float newTx = mViewInitialPosition.x + dx;
            final float newTy = mViewInitialPosition.y + dy;
            onDragUpdate(x, y, newTx, newTy);
        }

        private void stopDragging(@NonNull View view, @NonNull MotionEvent event) {
            BubbleDragController.this.setIsDragging(false);
            Runnable onComplete = () -> {
                mActivity.setTaskbarWindowFullscreen(false);
                cleanUp(view);
                onDragEnd(event.getRawX(), event.getRawY());
            };

            if (mBubbleDismissController.handleTouchEvent(event)) {
                onDragDismiss();
                mAnimator.animateDismiss(mViewInitialPosition, onComplete);
            } else {
                onDragRelease();
                if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
                    if (mBubbleDragZoneChangedListener.isDraggedToFullscreen()) {
                        onComplete.run();
                    } else {
                        mAnimator.animateToRestingState(getRestingPosition(), getCurrentVelocity(),
                                onComplete);
                    }
                } else {
                    mAnimator.animateToRestingState(getRestingPosition(), getCurrentVelocity(),
                            onComplete);
                }
            }
            mBubbleDismissController.hideDismissView();
        }

        private void setupLongClickHandler(@NonNull View view) {
            cleanUpLongClickHandler(view);
            mLongClickRunnable = () -> {
                // Register long click and start dragging interaction
                mState = State.DRAGGING;
                startDragging(view);
            };
            view.getHandler().postDelayed(mLongClickRunnable, mPressToDragTimeout);
        }

        private void cleanUpLongClickHandler(@NonNull View view) {
            if (mLongClickRunnable == null || view.getHandler() == null) return;
            view.getHandler().removeCallbacks(mLongClickRunnable);
            mLongClickRunnable = null;
        }

        private void cleanUp(@NonNull View view) {
            cleanUpLongClickHandler(view);
            mVelocityTracker.clear();
            mState = State.IDLE;
        }

        private void updateVelocity(MotionEvent event) {
            final float deltaX = event.getRawX() - event.getX();
            final float deltaY = event.getRawY() - event.getY();
            event.offsetLocation(deltaX, deltaY);
            mVelocityTracker.addMovement(event);
            event.offsetLocation(-deltaX, -deltaY);
        }

        private PointF getCurrentVelocity() {
            mVelocityTracker.computeCurrentVelocity(/* units = */ 1000);
            return new PointF(mVelocityTracker.getXVelocity(), mVelocityTracker.getYVelocity());
        }
    }

    private class BubbleDragZoneChangedListener implements DragZoneChangedListener {

        private BubbleBarLocation mBubbleBarLocation = BubbleBarLocation.DEFAULT;
        private DragZone mDragZone;

        boolean isDraggedToFullscreen() {
            return mDragZone instanceof DragZone.FullScreen;
        }

        @Override
        public void onInitialDragZoneSet(@Nullable DragZone dragZone) {
            mDragZone = dragZone;
            if (dragZone instanceof DragZone.Bubble.Left) {
                mBubbleBarLocation = BubbleBarLocation.LEFT;
            } else if (dragZone instanceof DragZone.Bubble.Right) {
                mBubbleBarLocation = BubbleBarLocation.RIGHT;
            }
        }

        @Override
        public void onDragZoneChanged(@NonNull DraggedObject draggedObject, @Nullable DragZone from,
                @Nullable DragZone to) {
            mDragZone = to;
            if (to instanceof DragZone.Bubble.Left
                    && mBubbleBarLocation != BubbleBarLocation.LEFT) {
                if (draggedObject instanceof DraggedObject.Bubble) {
                    // listener will be notified by BubbleBarController
                    mBubbleBarController.animateBubbleBarLocation(BubbleBarLocation.LEFT);
                } else {
                    // otherwise notify listener manually
                    mBubbleBarLocationListener.onBubbleBarLocationAnimated(BubbleBarLocation.LEFT);
                }
                mBubbleBarLocation = BubbleBarLocation.LEFT;
            } else if (to instanceof DragZone.Bubble.Right
                    && mBubbleBarLocation != BubbleBarLocation.RIGHT) {
                if (draggedObject instanceof DraggedObject.Bubble) {
                    mBubbleBarController.animateBubbleBarLocation(BubbleBarLocation.RIGHT);
                } else {
                    mBubbleBarLocationListener.onBubbleBarLocationAnimated(BubbleBarLocation.RIGHT);
                }
                mBubbleBarLocation = BubbleBarLocation.RIGHT;
            }
        }

        @Override
        public void onDragEnded(@Nullable DragZone zone) {}
    }
}

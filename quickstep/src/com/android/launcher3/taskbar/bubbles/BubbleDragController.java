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
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.taskbar.TaskbarActivityContext;

/**
 * Controls bubble bar drag to dismiss interaction.
 * Interacts with {@link BubbleDismissController}, used by {@link BubbleBarViewController}.
 * Supported interactions:
 * - Drag a single bubble view into dismiss target to remove it.
 * - Drag the bubble stack into dismiss target to remove all.
 * Restores initial position of dragged view if released outside of the dismiss target.
 */
public class BubbleDragController {
    private final TaskbarActivityContext mActivity;
    private BubbleBarViewController mBubbleBarViewController;
    private BubbleDismissController mBubbleDismissController;

    public BubbleDragController(TaskbarActivityContext activity) {
        mActivity = activity;
    }

    /**
     * Initializes dependencies when bubble controllers are created.
     * Should be careful to only access things that were created in constructors for now, as some
     * controllers may still be waiting for init().
     */
    public void init(@NonNull BubbleControllers bubbleControllers) {
        mBubbleBarViewController = bubbleControllers.bubbleBarViewController;
        mBubbleDismissController = bubbleControllers.bubbleDismissController;
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
            @Override
            void onDragStart() {
                mBubbleBarViewController.onDragStart(bubbleView);
            }

            @Override
            void onDragEnd() {
                mBubbleBarViewController.onDragEnd();
            }

            @Override
            protected void onDragRelease() {
                mBubbleBarViewController.onDragRelease(bubbleView);
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
            }

            @Override
            void onDragEnd() {
                // Restoring the initial pivot for the bubble bar view
                bubbleBarView.setRelativePivot(initialRelativePivot.x, initialRelativePivot.y);
            }
        });
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
        private final long mPressToDragTimeout = ViewConfiguration.getLongPressTimeout() / 2;
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
         * Called when the dragging interaction has ended and all the animations have completed
         */
        abstract void onDragEnd();

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
            final float dx = event.getRawX() - mTouchDownLocation.x;
            final float dy = event.getRawY() - mTouchDownLocation.y;
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
                    drag(view, event, dx, dy);
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
            mActivity.setTaskbarWindowFullscreen(true);
            mAnimator = new BubbleDragAnimator(view);
            mAnimator.animateFocused();
            mBubbleDismissController.setupDismissView(view, mAnimator);
            mBubbleDismissController.showDismissView();
        }

        private void drag(@NonNull View view, @NonNull MotionEvent event, float dx, float dy) {
            if (mBubbleDismissController.handleTouchEvent(event)) return;
            view.setTranslationX(mViewInitialPosition.x + dx);
            view.setTranslationY(mViewInitialPosition.y + dy);
        }

        private void stopDragging(@NonNull View view, @NonNull MotionEvent event) {
            Runnable onComplete = () -> {
                mActivity.setTaskbarWindowFullscreen(false);
                cleanUp(view);
                onDragEnd();
            };

            if (mBubbleDismissController.handleTouchEvent(event)) {
                onDragDismiss();
                mAnimator.animateDismiss(mViewInitialPosition, onComplete);
            } else {
                onDragRelease();
                mAnimator.animateToInitialState(mViewInitialPosition, getCurrentVelocity(),
                        onComplete);
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
}

/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.launcher3;

import android.content.res.Resources;
import android.os.SystemClock;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

/**
 * AutoScrollHelper is a utility class for adding automatic edge-triggered
 * scrolling to Views.
 * <p>
 * <b>Note:</b> Implementing classes are responsible for overriding the
 * {@link #scrollTargetBy}, {@link #canTargetScrollHorizontally}, and
 * {@link #canTargetScrollVertically} methods. See
 * {@link ListViewAutoScrollHelper} for a {@link android.widget.ListView}
 * -specific implementation.
 * <p>
 * <h1>Activation</h1> Automatic scrolling starts when the user touches within
 * an activation area. By default, activation areas are defined as the top,
 * left, right, and bottom 20% of the host view's total area. Touching within
 * the top activation area scrolls up, left scrolls to the left, and so on.
 * <p>
 * As the user touches closer to the extreme edge of the activation area,
 * scrolling accelerates up to a maximum velocity. When using the default edge
 * type, {@link #EDGE_TYPE_INSIDE_EXTEND}, moving outside of the view bounds
 * will scroll at the maximum velocity.
 * <p>
 * The following activation properties may be configured:
 * <ul>
 * <li>Delay after entering activation area before auto-scrolling begins, see
 * {@link #setActivationDelay}. Default value is
 * {@link ViewConfiguration#getTapTimeout()} to avoid conflicting with taps.
 * <li>Location of activation areas, see {@link #setEdgeType}. Default value is
 * {@link #EDGE_TYPE_INSIDE_EXTEND}.
 * <li>Size of activation areas relative to view size, see
 * {@link #setRelativeEdges}. Default value is 20% for both vertical and
 * horizontal edges.
 * <li>Maximum size used to constrain relative size, see
 * {@link #setMaximumEdges}. Default value is {@link #NO_MAX}.
 * </ul>
 * <h1>Scrolling</h1> When automatic scrolling is active, the helper will
 * repeatedly call {@link #scrollTargetBy} to apply new scrolling offsets.
 * <p>
 * The following scrolling properties may be configured:
 * <ul>
 * <li>Acceleration ramp-up duration, see {@link #setRampUpDuration}. Default
 * value is 2500 milliseconds.
 * <li>Acceleration ramp-down duration, see {@link #setRampDownDuration}.
 * Default value is 500 milliseconds.
 * <li>Target velocity relative to view size, see {@link #setRelativeVelocity}.
 * Default value is 100% per second for both vertical and horizontal.
 * <li>Minimum velocity used to constrain relative velocity, see
 * {@link #setMinimumVelocity}. When set, scrolling will accelerate to the
 * larger of either this value or the relative target value. Default value is
 * approximately 5 centimeters or 315 dips per second.
 * <li>Maximum velocity used to constrain relative velocity, see
 * {@link #setMaximumVelocity}. Default value is approximately 25 centimeters or
 * 1575 dips per second.
 * </ul>
 */
public abstract class AutoScrollHelper implements View.OnTouchListener {
    /**
     * Constant passed to {@link #setRelativeEdges} or
     * {@link #setRelativeVelocity}. Using this value ensures that the computed
     * relative value is ignored and the absolute maximum value is always used.
     */
    public static final float RELATIVE_UNSPECIFIED = 0;

    /**
     * Constant passed to {@link #setMaximumEdges}, {@link #setMaximumVelocity},
     * or {@link #setMinimumVelocity}. Using this value ensures that the
     * computed relative value is always used without constraining to a
     * particular minimum or maximum value.
     */
    public static final float NO_MAX = Float.MAX_VALUE;

    /**
     * Constant passed to {@link #setMaximumEdges}, or
     * {@link #setMaximumVelocity}, or {@link #setMinimumVelocity}. Using this
     * value ensures that the computed relative value is always used without
     * constraining to a particular minimum or maximum value.
     */
    public static final float NO_MIN = 0;

    /**
     * Edge type that specifies an activation area starting at the view bounds
     * and extending inward. Moving outside the view bounds will stop scrolling.
     *
     * @see #setEdgeType
     */
    public static final int EDGE_TYPE_INSIDE = 0;

    /**
     * Edge type that specifies an activation area starting at the view bounds
     * and extending inward. After activation begins, moving outside the view
     * bounds will continue scrolling.
     *
     * @see #setEdgeType
     */
    public static final int EDGE_TYPE_INSIDE_EXTEND = 1;

    /**
     * Edge type that specifies an activation area starting at the view bounds
     * and extending outward. Moving inside the view bounds will stop scrolling.
     *
     * @see #setEdgeType
     */
    public static final int EDGE_TYPE_OUTSIDE = 2;

    private static final int HORIZONTAL = 0;
    private static final int VERTICAL = 1;

    /** Scroller used to control acceleration toward maximum velocity. */
    private final ClampedScroller mScroller = new ClampedScroller();

    /** Interpolator used to scale velocity with touch position. */
    private final Interpolator mEdgeInterpolator = new AccelerateInterpolator();

    /** The view to auto-scroll. Might not be the source of touch events. */
    private final View mTarget;

    /** Runnable used to animate scrolling. */
    private Runnable mRunnable;

    /** Edge insets used to activate auto-scrolling. */
    private float[] mRelativeEdges = new float[] { RELATIVE_UNSPECIFIED, RELATIVE_UNSPECIFIED };

    /** Clamping values for edge insets used to activate auto-scrolling. */
    private float[] mMaximumEdges = new float[] { NO_MAX, NO_MAX };

    /** The type of edge being used. */
    private int mEdgeType;

    /** Delay after entering an activation edge before auto-scrolling begins. */
    private int mActivationDelay;

    /** Relative scrolling velocity at maximum edge distance. */
    private float[] mRelativeVelocity = new float[] { RELATIVE_UNSPECIFIED, RELATIVE_UNSPECIFIED };

    /** Clamping values used for scrolling velocity. */
    private float[] mMinimumVelocity = new float[] { NO_MIN, NO_MIN };

    /** Clamping values used for scrolling velocity. */
    private float[] mMaximumVelocity = new float[] { NO_MAX, NO_MAX };

    /** Whether to start activation immediately. */
    private boolean mAlreadyDelayed;

    /** Whether to reset the scroller start time on the next animation. */
    private boolean mNeedsReset;

    /** Whether to send a cancel motion event to the target view. */
    private boolean mNeedsCancel;

    /** Whether the auto-scroller is actively scrolling. */
    private boolean mAnimating;

    /** Whether the auto-scroller is enabled. */
    private boolean mEnabled;

    /** Whether the auto-scroller consumes events when scrolling. */
    private boolean mExclusive;

    // Default values.
    private static final int DEFAULT_EDGE_TYPE = EDGE_TYPE_INSIDE_EXTEND;
    private static final int DEFAULT_MINIMUM_VELOCITY_DIPS = 315;
    private static final int DEFAULT_MAXIMUM_VELOCITY_DIPS = 1575;
    private static final float DEFAULT_MAXIMUM_EDGE = NO_MAX;
    private static final float DEFAULT_RELATIVE_EDGE = 0.2f;
    private static final float DEFAULT_RELATIVE_VELOCITY = 1f;
    private static final int DEFAULT_ACTIVATION_DELAY = ViewConfiguration.getTapTimeout();
    private static final int DEFAULT_RAMP_UP_DURATION = 2500;
    private static final int DEFAULT_RAMP_DOWN_DURATION = 500;

    /**
     * Creates a new helper for scrolling the specified target view.
     * <p>
     * The resulting helper may be configured by chaining setter calls and
     * should be set as a touch listener on the target view.
     * <p>
     * By default, the helper is disabled and will not respond to touch events
     * until it is enabled using {@link #setEnabled}.
     *
     * @param target The view to automatically scroll.
     */
    public AutoScrollHelper(View target) {
        mTarget = target;

        final DisplayMetrics metrics = Resources.getSystem().getDisplayMetrics();
        final int maxVelocity = (int) (DEFAULT_MAXIMUM_VELOCITY_DIPS * metrics.density + 0.5f);
        final int minVelocity = (int) (DEFAULT_MINIMUM_VELOCITY_DIPS * metrics.density + 0.5f);
        setMaximumVelocity(maxVelocity, maxVelocity);
        setMinimumVelocity(minVelocity, minVelocity);

        setEdgeType(DEFAULT_EDGE_TYPE);
        setMaximumEdges(DEFAULT_MAXIMUM_EDGE, DEFAULT_MAXIMUM_EDGE);
        setRelativeEdges(DEFAULT_RELATIVE_EDGE, DEFAULT_RELATIVE_EDGE);
        setRelativeVelocity(DEFAULT_RELATIVE_VELOCITY, DEFAULT_RELATIVE_VELOCITY);
        setActivationDelay(DEFAULT_ACTIVATION_DELAY);
        setRampUpDuration(DEFAULT_RAMP_UP_DURATION);
        setRampDownDuration(DEFAULT_RAMP_DOWN_DURATION);
    }

    /**
     * Sets whether the scroll helper is enabled and should respond to touch
     * events.
     *
     * @param enabled Whether the scroll helper is enabled.
     * @return The scroll helper, which may used to chain setter calls.
     */
    public AutoScrollHelper setEnabled(boolean enabled) {
        if (mEnabled && !enabled) {
            requestStop();
        }

        mEnabled = enabled;
        return this;
    }

    /**
     * @return True if this helper is enabled and responding to touch events.
     */
    public boolean isEnabled() {
        return mEnabled;
    }

    /**
     * Enables or disables exclusive handling of touch events during scrolling.
     * By default, exclusive handling is disabled and the target view receives
     * all touch events.
     * <p>
     * When enabled, {@link #onTouch} will return true if the helper is
     * currently scrolling and false otherwise.
     *
     * @param exclusive True to exclusively handle touch events during scrolling,
     *            false to allow the target view to receive all touch events.
     * @return The scroll helper, which may used to chain setter calls.
     */
    public AutoScrollHelper setExclusive(boolean exclusive) {
        mExclusive = exclusive;
        return this;
    }

    /**
     * Indicates whether the scroll helper handles touch events exclusively
     * during scrolling.
     *
     * @return True if exclusive handling of touch events during scrolling is
     *         enabled, false otherwise.
     * @see #setExclusive(boolean)
     */
    public boolean isExclusive() {
        return mExclusive;
    }

    /**
     * Sets the absolute maximum scrolling velocity.
     * <p>
     * If relative velocity is not specified, scrolling will always reach the
     * same maximum velocity. If both relative and maximum velocities are
     * specified, the maximum velocity will be used to clamp the calculated
     * relative velocity.
     *
     * @param horizontalMax The maximum horizontal scrolling velocity, or
     *            {@link #NO_MAX} to leave the relative value unconstrained.
     * @param verticalMax The maximum vertical scrolling velocity, or
     *            {@link #NO_MAX} to leave the relative value unconstrained.
     * @return The scroll helper, which may used to chain setter calls.
     */
    public AutoScrollHelper setMaximumVelocity(float horizontalMax, float verticalMax) {
        mMaximumVelocity[HORIZONTAL] = horizontalMax / 1000f;
        mMaximumVelocity[VERTICAL] = verticalMax / 1000f;
        return this;
    }

    /**
     * Sets the absolute minimum scrolling velocity.
     * <p>
     * If both relative and minimum velocities are specified, the minimum
     * velocity will be used to clamp the calculated relative velocity.
     *
     * @param horizontalMin The minimum horizontal scrolling velocity, or
     *            {@link #NO_MIN} to leave the relative value unconstrained.
     * @param verticalMin The minimum vertical scrolling velocity, or
     *            {@link #NO_MIN} to leave the relative value unconstrained.
     * @return The scroll helper, which may used to chain setter calls.
     */
    public AutoScrollHelper setMinimumVelocity(float horizontalMin, float verticalMin) {
        mMinimumVelocity[HORIZONTAL] = horizontalMin / 1000f;
        mMinimumVelocity[VERTICAL] = verticalMin / 1000f;
        return this;
    }

    /**
     * Sets the target scrolling velocity relative to the host view's
     * dimensions.
     * <p>
     * If both relative and maximum velocities are specified, the maximum
     * velocity will be used to clamp the calculated relative velocity.
     *
     * @param horizontal The target horizontal velocity as a fraction of the
     *            host view width per second, or {@link #RELATIVE_UNSPECIFIED}
     *            to ignore.
     * @param vertical The target vertical velocity as a fraction of the host
     *            view height per second, or {@link #RELATIVE_UNSPECIFIED} to
     *            ignore.
     * @return The scroll helper, which may used to chain setter calls.
     */
    public AutoScrollHelper setRelativeVelocity(float horizontal, float vertical) {
        mRelativeVelocity[HORIZONTAL] = horizontal / 1000f;
        mRelativeVelocity[VERTICAL] = vertical / 1000f;
        return this;
    }

    /**
     * Sets the activation edge type, one of:
     * <ul>
     * <li>{@link #EDGE_TYPE_INSIDE} for edges that respond to touches inside
     * the bounds of the host view. If touch moves outside the bounds, scrolling
     * will stop.
     * <li>{@link #EDGE_TYPE_INSIDE_EXTEND} for inside edges that continued to
     * scroll when touch moves outside the bounds of the host view.
     * <li>{@link #EDGE_TYPE_OUTSIDE} for edges that only respond to touches
     * that move outside the bounds of the host view.
     * </ul>
     *
     * @param type The type of edge to use.
     * @return The scroll helper, which may used to chain setter calls.
     */
    public AutoScrollHelper setEdgeType(int type) {
        mEdgeType = type;
        return this;
    }

    /**
     * Sets the activation edge size relative to the host view's dimensions.
     * <p>
     * If both relative and maximum edges are specified, the maximum edge will
     * be used to constrain the calculated relative edge size.
     *
     * @param horizontal The horizontal edge size as a fraction of the host view
     *            width, or {@link #RELATIVE_UNSPECIFIED} to always use the
     *            maximum value.
     * @param vertical The vertical edge size as a fraction of the host view
     *            height, or {@link #RELATIVE_UNSPECIFIED} to always use the
     *            maximum value.
     * @return The scroll helper, which may used to chain setter calls.
     */
    public AutoScrollHelper setRelativeEdges(float horizontal, float vertical) {
        mRelativeEdges[HORIZONTAL] = horizontal;
        mRelativeEdges[VERTICAL] = vertical;
        return this;
    }

    /**
     * Sets the absolute maximum edge size.
     * <p>
     * If relative edge size is not specified, activation edges will always be
     * the maximum edge size. If both relative and maximum edges are specified,
     * the maximum edge will be used to constrain the calculated relative edge
     * size.
     *
     * @param horizontalMax The maximum horizontal edge size in pixels, or
     *            {@link #NO_MAX} to use the unconstrained calculated relative
     *            value.
     * @param verticalMax The maximum vertical edge size in pixels, or
     *            {@link #NO_MAX} to use the unconstrained calculated relative
     *            value.
     * @return The scroll helper, which may used to chain setter calls.
     */
    public AutoScrollHelper setMaximumEdges(float horizontalMax, float verticalMax) {
        mMaximumEdges[HORIZONTAL] = horizontalMax;
        mMaximumEdges[VERTICAL] = verticalMax;
        return this;
    }

    /**
     * Sets the delay after entering an activation edge before activation of
     * auto-scrolling. By default, the activation delay is set to
     * {@link ViewConfiguration#getTapTimeout()}.
     * <p>
     * Specifying a delay of zero will start auto-scrolling immediately after
     * the touch position enters an activation edge.
     *
     * @param delayMillis The activation delay in milliseconds.
     * @return The scroll helper, which may used to chain setter calls.
     */
    public AutoScrollHelper setActivationDelay(int delayMillis) {
        mActivationDelay = delayMillis;
        return this;
    }

    /**
     * Sets the amount of time after activation of auto-scrolling that is takes
     * to reach target velocity for the current touch position.
     * <p>
     * Specifying a duration greater than zero prevents sudden jumps in
     * velocity.
     *
     * @param durationMillis The ramp-up duration in milliseconds.
     * @return The scroll helper, which may used to chain setter calls.
     */
    public AutoScrollHelper setRampUpDuration(int durationMillis) {
        mScroller.setRampUpDuration(durationMillis);
        return this;
    }

    /**
     * Sets the amount of time after de-activation of auto-scrolling that is
     * takes to slow to a stop.
     * <p>
     * Specifying a duration greater than zero prevents sudden jumps in
     * velocity.
     *
     * @param durationMillis The ramp-down duration in milliseconds.
     * @return The scroll helper, which may used to chain setter calls.
     */
    public AutoScrollHelper setRampDownDuration(int durationMillis) {
        mScroller.setRampDownDuration(durationMillis);
        return this;
    }

    /**
     * Handles touch events by activating automatic scrolling, adjusting scroll
     * velocity, or stopping.
     * <p>
     * If {@link #isExclusive()} is false, always returns false so that
     * the host view may handle touch events. Otherwise, returns true when
     * automatic scrolling is active and false otherwise.
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (!mEnabled) {
            return false;
        }

        final int action = MotionEventCompat.getActionMasked(event);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mNeedsCancel = true;
                mAlreadyDelayed = false;
                // $FALL-THROUGH$
            case MotionEvent.ACTION_MOVE:
                final float xTargetVelocity = computeTargetVelocity(
                        HORIZONTAL, event.getX(), v.getWidth(), mTarget.getWidth());
                final float yTargetVelocity = computeTargetVelocity(
                        VERTICAL, event.getY(), v.getHeight(), mTarget.getHeight());
                mScroller.setTargetVelocity(xTargetVelocity, yTargetVelocity);

                // If the auto scroller was not previously active, but it should
                // be, then update the state and start animations.
                if (!mAnimating && shouldAnimate()) {
                    startAnimating();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                requestStop();
                break;
        }

        return mExclusive && mAnimating;
    }

    /**
     * @return whether the target is able to scroll in the requested direction
     */
    private boolean shouldAnimate() {
        final ClampedScroller scroller = mScroller;
        final int verticalDirection = scroller.getVerticalDirection();
        final int horizontalDirection = scroller.getHorizontalDirection();

        return verticalDirection != 0 && canTargetScrollVertically(verticalDirection)
                || horizontalDirection != 0 && canTargetScrollHorizontally(horizontalDirection);
    }

    /**
     * Starts the scroll animation.
     */
    private void startAnimating() {
        if (mRunnable == null) {
            mRunnable = new ScrollAnimationRunnable();
        }

        mAnimating = true;
        mNeedsReset = true;

        if (!mAlreadyDelayed && mActivationDelay > 0) {
            ViewCompat.postOnAnimationDelayed(mTarget, mRunnable, mActivationDelay);
        } else {
            mRunnable.run();
        }

        // If we start animating again before the user lifts their finger, we
        // already know it's not a tap and don't need an activation delay.
        mAlreadyDelayed = true;
    }

    /**
     * Requests that the scroll animation slow to a stop. If there is an
     * activation delay, this may occur between posting the animation and
     * actually running it.
     */
    private void requestStop() {
        if (mNeedsReset) {
            // The animation has been posted, but hasn't run yet. Manually
            // stopping animation will prevent it from running.
            mAnimating = false;
        } else {
            mScroller.requestStop();
        }
    }

    private float computeTargetVelocity(
            int direction, float coordinate, float srcSize, float dstSize) {
        final float relativeEdge = mRelativeEdges[direction];
        final float maximumEdge = mMaximumEdges[direction];
        final float value = getEdgeValue(relativeEdge, srcSize, maximumEdge, coordinate);
        if (value == 0) {
            // The edge in this direction is not activated.
            return 0;
        }

        final float relativeVelocity = mRelativeVelocity[direction];
        final float minimumVelocity = mMinimumVelocity[direction];
        final float maximumVelocity = mMaximumVelocity[direction];
        final float targetVelocity = relativeVelocity * dstSize;

        // Target velocity is adjusted for interpolated edge position, then
        // clamped to the minimum and maximum values. Later, this value will be
        // adjusted for time-based acceleration.
        if (value > 0) {
            return constrain(value * targetVelocity, minimumVelocity, maximumVelocity);
        } else {
            return -constrain(-value * targetVelocity, minimumVelocity, maximumVelocity);
        }
    }

    /**
     * Override this method to scroll the target view by the specified number of
     * pixels.
     *
     * @param deltaX The number of pixels to scroll by horizontally.
     * @param deltaY The number of pixels to scroll by vertically.
     */
    public abstract void scrollTargetBy(int deltaX, int deltaY);

    /**
     * Override this method to return whether the target view can be scrolled
     * horizontally in a certain direction.
     *
     * @param direction Negative to check scrolling left, positive to check
     *            scrolling right.
     * @return true if the target view is able to horizontally scroll in the
     *         specified direction.
     */
    public abstract boolean canTargetScrollHorizontally(int direction);

    /**
     * Override this method to return whether the target view can be scrolled
     * vertically in a certain direction.
     *
     * @param direction Negative to check scrolling up, positive to check
     *            scrolling down.
     * @return true if the target view is able to vertically scroll in the
     *         specified direction.
     */
    public abstract boolean canTargetScrollVertically(int direction);

    /**
     * Returns the interpolated position of a touch point relative to an edge
     * defined by its relative inset, its maximum absolute inset, and the edge
     * interpolator.
     *
     * @param relativeValue The size of the inset relative to the total size.
     * @param size Total size.
     * @param maxValue The maximum size of the inset, used to clamp (relative *
     *            total).
     * @param current Touch position within within the total size.
     * @return Interpolated value of the touch position within the edge.
     */
    private float getEdgeValue(float relativeValue, float size, float maxValue, float current) {
        // For now, leading and trailing edges are always the same size.
        final float edgeSize = constrain(relativeValue * size, NO_MIN, maxValue);
        final float valueLeading = constrainEdgeValue(current, edgeSize);
        final float valueTrailing = constrainEdgeValue(size - current, edgeSize);
        final float value = (valueTrailing - valueLeading);
        final float interpolated;
        if (value < 0) {
            interpolated = -mEdgeInterpolator.getInterpolation(-value);
        } else if (value > 0) {
            interpolated = mEdgeInterpolator.getInterpolation(value);
        } else {
            return 0;
        }

        return constrain(interpolated, -1, 1);
    }

    private float constrainEdgeValue(float current, float leading) {
        if (leading == 0) {
            return 0;
        }

        switch (mEdgeType) {
            case EDGE_TYPE_INSIDE:
            case EDGE_TYPE_INSIDE_EXTEND:
                if (current < leading) {
                    if (current > 0) {
                        // Movement up to the edge is scaled.
                        return 1f - current / leading;
                    } else if (mAnimating && (mEdgeType == EDGE_TYPE_INSIDE_EXTEND)) {
                        // Movement beyond the edge is always maximum.
                        return 1f;
                    }
                }
                break;
            case EDGE_TYPE_OUTSIDE:
                if (current < 0) {
                    // Movement beyond the edge is scaled.
                    return current / -leading;
                }
                break;
        }

        return 0;
    }

    private static int constrain(int value, int min, int max) {
        if (value > max) {
            return max;
        } else if (value < min) {
            return min;
        } else {
            return value;
        }
    }

    private static float constrain(float value, float min, float max) {
        if (value > max) {
            return max;
        } else if (value < min) {
            return min;
        } else {
            return value;
        }
    }

    /**
     * Sends a {@link MotionEvent#ACTION_CANCEL} event to the target view,
     * canceling any ongoing touch events.
     */
    private void cancelTargetTouch() {
        final long eventTime = SystemClock.uptimeMillis();
        final MotionEvent cancel = MotionEvent.obtain(
                eventTime, eventTime, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        mTarget.onTouchEvent(cancel);
        cancel.recycle();
    }

    private class ScrollAnimationRunnable implements Runnable {
        @Override
        public void run() {
            if (!mAnimating) {
                return;
            }

            if (mNeedsReset) {
                mNeedsReset = false;
                mScroller.start();
            }

            final ClampedScroller scroller = mScroller;
            if (scroller.isFinished() || !shouldAnimate()) {
                mAnimating = false;
                return;
            }

            if (mNeedsCancel) {
                mNeedsCancel = false;
                cancelTargetTouch();
            }

            scroller.computeScrollDelta();

            final int deltaX = scroller.getDeltaX();
            final int deltaY = scroller.getDeltaY();
            scrollTargetBy(deltaX,  deltaY);

            // Keep going until the scroller has permanently stopped.
            ViewCompat.postOnAnimation(mTarget, this);
        }
    }

    /**
     * Scroller whose velocity follows the curve of an {@link Interpolator} and
     * is clamped to the interpolated 0f value before starting and the
     * interpolated 1f value after a specified duration.
     */
    private static class ClampedScroller {
        private int mRampUpDuration;
        private int mRampDownDuration;
        private float mTargetVelocityX;
        private float mTargetVelocityY;

        private long mStartTime;

        private long mDeltaTime;
        private int mDeltaX;
        private int mDeltaY;

        private long mStopTime;
        private float mStopValue;
        private int mEffectiveRampDown;

        /**
         * Creates a new ramp-up scroller that reaches full velocity after a
         * specified duration.
         */
        public ClampedScroller() {
            mStartTime = Long.MIN_VALUE;
            mStopTime = -1;
            mDeltaTime = 0;
            mDeltaX = 0;
            mDeltaY = 0;
        }

        public void setRampUpDuration(int durationMillis) {
            mRampUpDuration = durationMillis;
        }

        public void setRampDownDuration(int durationMillis) {
            mRampDownDuration = durationMillis;
        }

        /**
         * Starts the scroller at the current animation time.
         */
        public void start() {
            mStartTime = AnimationUtils.currentAnimationTimeMillis();
            mStopTime = -1;
            mDeltaTime = mStartTime;
            mStopValue = 0.5f;
            mDeltaX = 0;
            mDeltaY = 0;
        }

        /**
         * Stops the scroller at the current animation time.
         */
        public void requestStop() {
            final long currentTime = AnimationUtils.currentAnimationTimeMillis();
            mEffectiveRampDown = constrain((int) (currentTime - mStartTime), 0, mRampDownDuration);
            mStopValue = getValueAt(currentTime);
            mStopTime = currentTime;
        }

        public boolean isFinished() {
            return mStopTime > 0
                    && AnimationUtils.currentAnimationTimeMillis() > mStopTime + mEffectiveRampDown;
        }

        private float getValueAt(long currentTime) {
            if (currentTime < mStartTime) {
                return 0f;
            } else if (mStopTime < 0 || currentTime < mStopTime) {
                final long elapsedSinceStart = currentTime - mStartTime;
                return 0.5f * constrain(elapsedSinceStart / (float) mRampUpDuration, 0, 1);
            } else {
                final long elapsedSinceEnd = currentTime - mStopTime;
                return (1 - mStopValue) + mStopValue
                        * constrain(elapsedSinceEnd / (float) mEffectiveRampDown, 0, 1);
            }
        }

        /**
         * Interpolates the value along a parabolic curve corresponding to the equation
         * <code>y = -4x * (x-1)</code>.
         *
         * @param value The value to interpolate, between 0 and 1.
         * @return the interpolated value, between 0 and 1.
         */
        private float interpolateValue(float value) {
            return -4 * value * value + 4 * value;
        }

        /**
         * Computes the current scroll deltas. This usually only be called after
         * starting the scroller with {@link #start()}.
         *
         * @see #getDeltaX()
         * @see #getDeltaY()
         */
        public void computeScrollDelta() {
            if (mDeltaTime == 0) {
                throw new RuntimeException("Cannot compute scroll delta before calling start()");
            }

            final long currentTime = AnimationUtils.currentAnimationTimeMillis();
            final float value = getValueAt(currentTime);
            final float scale = interpolateValue(value);
            final long elapsedSinceDelta = currentTime - mDeltaTime;

            mDeltaTime = currentTime;
            mDeltaX = (int) (elapsedSinceDelta * scale * mTargetVelocityX);
            mDeltaY = (int) (elapsedSinceDelta * scale * mTargetVelocityY);
        }

        /**
         * Sets the target velocity for this scroller.
         *
         * @param x The target X velocity in pixels per millisecond.
         * @param y The target Y velocity in pixels per millisecond.
         */
        public void setTargetVelocity(float x, float y) {
            mTargetVelocityX = x;
            mTargetVelocityY = y;
        }

        public int getHorizontalDirection() {
            return (int) (mTargetVelocityX / Math.abs(mTargetVelocityX));
        }

        public int getVerticalDirection() {
            return (int) (mTargetVelocityY / Math.abs(mTargetVelocityY));
        }

        /**
         * The distance traveled in the X-coordinate computed by the last call
         * to {@link #computeScrollDelta()}.
         */
        public int getDeltaX() {
            return mDeltaX;
        }

        /**
         * The distance traveled in the Y-coordinate computed by the last call
         * to {@link #computeScrollDelta()}.
         */
        public int getDeltaY() {
            return mDeltaY;
        }
    }
}
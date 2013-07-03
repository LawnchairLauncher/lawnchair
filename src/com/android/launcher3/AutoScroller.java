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

import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.AbsListView;

class AutoScroller implements View.OnTouchListener, Runnable {
    private static final int SCALE_RELATIVE = 0;
    private static final int SCALE_ABSOLUTE = 1;

    private final View mTarget;
    private final RampUpScroller mScroller;

    /** Interpolator used to scale velocity with touch position, may be null. */
    private Interpolator mEdgeInterpolator = new AccelerateInterpolator();

    /**
     * Type of maximum velocity scaling to use, one of:
     * <ul>
     * <li>{@link #SCALE_RELATIVE}
     * <li>{@link #SCALE_ABSOLUTE}
     * </ul>
     */
    private int mMaxVelocityScale = SCALE_RELATIVE;

    /**
     * Type of activation edge scaling to use, one of:
     * <ul>
     * <li>{@link #SCALE_RELATIVE}
     * <li>{@link #SCALE_ABSOLUTE}
     * </ul>
     */
    private int mActivationEdgeScale = SCALE_RELATIVE;

    /** Edge insets used to activate auto-scrolling. */
    private RectF mActivationEdges = new RectF(0.2f, 0.2f, 0.2f, 0.2f);

    /** Delay after entering an activation edge before auto-scrolling begins. */
    private int mActivationDelay;

    /** Maximum horizontal scrolling velocity. */
    private float mMaxVelocityX = 0.001f;

    /** Maximum vertical scrolling velocity. */
    private float mMaxVelocityY = 0.001f;

    /**
     * Whether positive insets should also extend beyond the view bounds when
     * auto-scrolling is already active. This allows a user to start scrolling
     * at an inside edge, then move beyond the edge and continue scrolling.
     */
    private boolean mExtendsBeyondEdges = true;

    /** Whether to start activation immediately. */
    private boolean mSkipDelay;

    /** Whether to reset the scroller start time on the next animation. */
    private boolean mResetScroller;

    /** Whether the auto-scroller is active. */
    private boolean mActive;
    private long[] mScrollStart = new long[2];

    /**
     * If the event is within this percentage of the edge of the scrolling area,
     * use accelerated scrolling.
     */
    private float mFastScrollingRange = 0.8f;

    /**
     * Duration of time spent in accelerated scrolling area before reaching
     * maximum velocity
     */
    private float mDurationToMax = 2500f;

    private static final int X = 0;
    private static final int Y = 1;

    public AutoScroller(View target) {
        mTarget = target;
        mScroller = new RampUpScroller(250);
        mActivationDelay = ViewConfiguration.getTapTimeout();
    }

    /**
     * Sets the maximum scrolling velocity as a fraction of the host view size
     * per second. For example, a maximum Y velocity of 1 would scroll one
     * vertical page per second. By default, both values are 1.
     *
     * @param x The maximum X velocity as a fraction of the host view width per
     *            second.
     * @param y The maximum Y velocity as a fraction of the host view height per
     *            second.
     */
    public void setMaximumVelocityRelative(float x, float y) {
        mMaxVelocityScale = SCALE_RELATIVE;
        mMaxVelocityX = x / 1000f;
        mMaxVelocityY = y / 1000f;
    }

    /**
     * Sets the maximum scrolling velocity as an absolute pixel distance per
     * second. For example, a maximum Y velocity of 100 would scroll one hundred
     * pixels per second.
     *
     * @param x The maximum X velocity as a fraction of the host view width per
     *            second.
     * @param y The maximum Y velocity as a fraction of the host view height per
     *            second.
     */
    public void setMaximumVelocityAbsolute(float x, float y) {
        mMaxVelocityScale = SCALE_ABSOLUTE;
        mMaxVelocityX = x / 1000f;
        mMaxVelocityY = y / 1000f;
    }

    /**
     * Sets the delay after entering an activation edge before activation of
     * auto-scrolling. By default, the activation delay is set to
     * {@link ViewConfiguration#getTapTimeout()}.
     *
     * @param delayMillis The delay in milliseconds.
     */
    public void setActivationDelay(int delayMillis) {
        mActivationDelay = delayMillis;
    }

    /**
     * Sets the activation edges in pixels. Edges are treated as insets, so
     * positive values expand into the view bounds while negative values extend
     * outside the bounds.
     *
     * @param l The left activation edge, in pixels.
     * @param t The top activation edge, in pixels.
     * @param r The right activation edge, in pixels.
     * @param b The bottom activation edge, in pixels.
     */
    public void setEdgesAbsolute(int l, int t, int r, int b) {
        mActivationEdgeScale = SCALE_ABSOLUTE;
        mActivationEdges.set(l, t, r, b);
    }

    /**
     * Whether positive insets should also extend beyond the view bounds when
     * auto-scrolling is already active. This allows a user to start scrolling
     * at an inside edge, then move beyond the edge and continue scrolling.
     *
     * @param e
     */
    public void setExtendsBeyondEdges(boolean e) {
        mExtendsBeyondEdges = e;
    }

    /**
     * Sets the activation edges as fractions of the host view size. Edges are
     * treated as insets, so positive values expand into the view bounds while
     * negative values extend outside the bounds. By default, all values are
     * 0.25.
     *
     * @param l The left activation edge, as a fraction of view size.
     * @param t The top activation edge, as a fraction of view size.
     * @param r The right activation edge, as a fraction of view size.
     * @param b The bottom activation edge, as a fraction of view size.
     */
    public void setEdgesRelative(float l, float t, float r, float b) {
        mActivationEdgeScale = SCALE_RELATIVE;
        mActivationEdges.set(l, t, r, b);
    }

    /**
     * Sets the {@link Interpolator} used for scaling touches within activation
     * edges. By default, uses the {@link AccelerateInterpolator} to gradually
     * speed up scrolling.
     *
     * @param edgeInterpolator The interpolator to use for activation edges, or
     *            {@code null} to use a fixed velocity during auto-scrolling.
     */
    public void setEdgeInterpolator(Interpolator edgeInterpolator) {
        mEdgeInterpolator = edgeInterpolator;
    }

    /**
     * Stop tracking scrolling.
     */
    public void stop() {
        stop(true);
    }

    /**
     * Pass the rectangle defining the drawing region for the object used to
     * trigger drag scrolling.
     *
     * @param v View on which the scrolling regions are defined
     * @param r Rect defining the drawing bounds of the object being dragged
     * @return whether the event was handled
     */
    public boolean onTouch(View v, Rect r) {
        MotionEvent event = MotionEvent.obtain(SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, r.left, r.top, 0);
        return onTouch(v, event);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                final int sourceWidth = v.getWidth();
                final int sourceHeight = v.getHeight();
                final float x = event.getX();
                final float y = event.getY();
                final float l;
                final float t;
                final float r;
                final float b;
                final RectF activationEdges = mActivationEdges;
                if (mActivationEdgeScale == SCALE_ABSOLUTE) {
                    l = activationEdges.left;
                    t = activationEdges.top;
                    r = activationEdges.right;
                    b = activationEdges.bottom;
                } else {
                    l = activationEdges.left * sourceWidth;
                    t = activationEdges.top * sourceHeight;
                    r = activationEdges.right * sourceWidth;
                    b = activationEdges.bottom * sourceHeight;
                }

                final float maxVelX;
                final float maxVelY;
                if (mMaxVelocityScale == SCALE_ABSOLUTE) {
                    maxVelX = mMaxVelocityX;
                    maxVelY = mMaxVelocityY;
                } else {
                    maxVelX = mMaxVelocityX * mTarget.getWidth();
                    maxVelY = mMaxVelocityY * mTarget.getHeight();
                }

                final float velocityX = getEdgeVelocity(X, l, r, x, sourceWidth, event);
                final float velocityY = getEdgeVelocity(Y, t, b, y, sourceHeight, event);
                mScroller.setTargetVelocity(velocityX * maxVelX, velocityY * maxVelY);

                if ((velocityX != 0 || velocityY != 0) && !mActive) {
                    mActive = true;
                    mResetScroller = true;
                    if (mSkipDelay) {
                        mTarget.postOnAnimation(this);
                    } else {
                        mSkipDelay = true;
                        mTarget.postOnAnimationDelayed(this, mActivationDelay);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                stop(true);
                break;
        }

        return false;
    }

    /**
     * @param leading Size of the leading activation inset.
     * @param trailing Size of the trailing activation inset.
     * @param current Position within within the total area.
     * @param size Size of the total area.
     * @return The fraction of the activation area.
     */
    private float getEdgeVelocity(int dir, float leading, float trailing,
            float current, float size, MotionEvent ev) {
        float valueLeading = 0;
        if (leading > 0) {
            if (current < leading) {
                if (current > 0) {
                    // Movement up to the edge is scaled.
                    valueLeading = 1f - current / leading;
                } else if (mActive && mExtendsBeyondEdges) {
                    // Movement beyond the edge is always maximum.
                    valueLeading = 1f;
                }
            }
        } else if (leading < 0) {
            if (current < 0) {
                // Movement beyond the edge is scaled.
                valueLeading = current / leading;
            }
        }

        float valueTrailing = 0;
        if (trailing > 0) {
            if (current > size - trailing) {
                if (current < size) {
                    // Movement up to the edge is scaled.
                    valueTrailing = 1f - (size - current) / trailing;
                } else if (mActive && mExtendsBeyondEdges) {
                    // Movement beyond the edge is always maximum.
                    valueTrailing = 1f;
                }
            }
        } else if (trailing < 0) {
            if (current > size) {
                // Movement beyond the edge is scaled.
                valueTrailing = (size - current) / trailing;
            }
        }

        float value = (valueTrailing - valueLeading);
        if ((value > mFastScrollingRange || value < -mFastScrollingRange)
            && mScrollStart[dir] == 0) {
            // within auto scrolling area
            mScrollStart[dir] = ev.getEventTime();
        } else {
            // Outside fast scrolling area; reset duration
            mScrollStart[dir] = 0;
        }
        final float duration = (ev.getEventTime() - mScrollStart[dir])/mDurationToMax;
        final float interpolated;
        if (value < 0) {
            if (value < -mFastScrollingRange) {
                // Close to top; use duration!
                value += mEdgeInterpolator.getInterpolation(-duration);
            }
            interpolated = mEdgeInterpolator == null ? -1
                    : -mEdgeInterpolator.getInterpolation(-value);
        } else if (value > 0) {
            // Close to bottom; use duration
            if (value > mFastScrollingRange) {
                // Close to bottom; use duration!
                value += mEdgeInterpolator.getInterpolation(duration);
            }
            interpolated = mEdgeInterpolator == null ? 1
                    : mEdgeInterpolator.getInterpolation(value);
        } else {
            mScrollStart[dir] = 0;
            return 0;
        }

        return constrain(interpolated, -1, 1);
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
     * Stops auto-scrolling immediately, optionally reseting the auto-scrolling
     * delay.
     *
     * @param reset Whether to reset the auto-scrolling delay.
     */
    private void stop(boolean reset) {
        mActive = false;
        mSkipDelay = !reset;
        mTarget.removeCallbacks(this);
    }

    @Override
    public void run() {
        if (!mActive) {
            return;
        }

        if (mResetScroller) {
            mResetScroller = false;
            mScroller.start();
        }

        final View target = mTarget;
        final RampUpScroller scroller = mScroller;
        final float targetVelocityX = scroller.getTargetVelocityX();
        final float targetVelocityY = scroller.getTargetVelocityY();
        if ((targetVelocityY == 0 || !target.canScrollVertically(targetVelocityY > 0 ? 1 : -1)
                && (targetVelocityX == 0
                        || !target.canScrollHorizontally(targetVelocityX > 0 ? 1 : -1)))) {
            stop(false);
            return;
        }

        scroller.computeScrollDelta();

        final int deltaX = scroller.getDeltaX();
        final int deltaY = scroller.getDeltaY();

        if (target instanceof AbsListView) {
            final AbsListView list = (AbsListView) target;
            list.smoothScrollBy(deltaY, 0);
        } else {
            target.scrollBy(deltaX, deltaY);
        }

        target.postOnAnimation(this);
    }
}

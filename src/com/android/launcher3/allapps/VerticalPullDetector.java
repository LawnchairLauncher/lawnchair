package com.android.launcher3.allapps;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/**
 * One dimensional scroll gesture detector for all apps container pull up interaction.
 * Client (e.g., AllAppsTransitionController) of this class can register a listener.
 *
 * Features that this gesture detector can support.
 */
public class VerticalPullDetector {

    private static final boolean DBG = false;
    private static final String TAG = "VerticalPullDetector";

    private float mTouchSlop;

    private int mScrollDirections;
    public static final int THRESHOLD_UP = 1 << 0;
    public static final int THRESHOLD_DOWN = 1 << 1;
    public static final int THRESHOLD_ONLY = THRESHOLD_DOWN | THRESHOLD_UP;


    /**
     * The minimum release velocity in pixels per millisecond that triggers fling..
     */
    private static final float RELEASE_VELOCITY_PX_MS = 1.0f;

    /**
     * The time constant used to calculate dampening in the low-pass filter of scroll velocity.
     * Cutoff frequency is set at 10 Hz.
     */
    public static final float SCROLL_VELOCITY_DAMPENING_RC = 1000f / (2f * (float) Math.PI * 10);

    /* Scroll state, this is set to true during dragging and animation. */
    private State mState = State.NONE;

    enum State {
        NONE,
        CATCH,          // onScrollStart
        DRAG,           // onScrollStart, onScroll
        SCROLLING       // onScrollEnd
    };

    //------------------- State transition diagram -----------------------------------
    //
    // NONE -> (mDisplacement > mTouchSlop) -> DRAG
    // DRAG -> (MotionEvent#ACTION_UP, MotionEvent#ACTION_CANCEL) -> SCROLLING
    // SCROLLING -> (MotionEvent#ACTION_DOWN) && (mDisplacement > mTouchSlop) -> CATCH
    // SCROLLING -> (View settled) -> NONE

    private void setState(State newState) {
        if (DBG) {
            Log.d(TAG, "setState:" + mState + "->" + newState);
        }
        mState = newState;
    }

    public boolean shouldIntercept() {
        return mState == State.DRAG || mState == State.SCROLLING || mState == State.CATCH;
    }

    /**
     * There's no touch and there's no animation.
     */
    public boolean isRestingState() {
        return mState == State.NONE;
    }

    public boolean isScrollingState() {
        return mState == State.SCROLLING;
    }

    private float mDownX;
    private float mDownY;
    private float mDownMillis;

    private float mLastY;
    private float mLastMillis;

    private float mVelocity;
    private float mLastDisplacement;
    private float mDisplacementY;
    private float mDisplacementX;

    private float mSubtractDisplacement;

    /* Client of this gesture detector can register a callback. */
    Listener mListener;

    public void setListener(Listener l) {
        mListener = l;
    }

    interface Listener{
        void onScrollStart(boolean start);
        boolean onScroll(float displacement, float velocity);
        void onScrollEnd(float velocity, boolean fling);
    }

    public VerticalPullDetector(Context context) {
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setDetectableScrollConditions(int scrollDirectionFlags) {
        mScrollDirections = scrollDirectionFlags;
    }

    private boolean shouldScrollStart() {
        // reject cases where the slop condition is not met.
        if (Math.abs(mDisplacementY) < mTouchSlop) {
            return false;
        }

        // reject cases where the angle condition is not met.
        float deltaY = Math.abs(mDisplacementY);
        float deltaX = Math.max(Math.abs(mDisplacementX), 1);
        if (deltaX > deltaY) {
            return false;
        }
        // Check if the client is interested in scroll in current direction.
        if (((mScrollDirections & THRESHOLD_DOWN) > 0 && mDisplacementY > 0) ||
            ((mScrollDirections & THRESHOLD_UP) > 0 && mDisplacementY < 0)) {
            return true;
        }
        return false;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownMillis = ev.getDownTime();
                mDownX = ev.getX();
                mDownY = ev.getY();
                mLastDisplacement = 0;
                mVelocity = 0;

                // handle state and listener calls.
                if (mState == State.SCROLLING && shouldScrollStart()){
                    reportScrollStart(true /* recatch */);
                    setState(State.CATCH);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                mDisplacementX = ev.getX() - mDownX;
                mDisplacementY = ev.getY() - mDownY;
                mVelocity = computeVelocity(ev, mVelocity);

                // handle state and listener calls.
                if (shouldScrollStart() && mState != State.DRAG) {
                    if (mState == State.NONE) {
                        reportScrollStart(false /* recatch */);
                    }
                    setState(State.DRAG);
                }
                if (mState == State.DRAG) {
                    reportScroll();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                // These are synthetic events and there is no need to update internal values.
                if (mState == State.DRAG || mState == State.CATCH) {
                    reportScrollEnd();
                    setState(State.SCROLLING);
                }
                break;
            default:
                //TODO: add multi finger tracking by tracking active pointer.
                break;
        }
        // Do house keeping.
        mLastDisplacement = mDisplacementY;

        mLastY = ev.getY();
        mLastMillis = ev.getEventTime();

        return true;
    }

    public void finishedScrolling() {
        setState(State.NONE);
    }

    private boolean reportScrollStart(boolean recatch) {
        mListener.onScrollStart(!recatch);
        if (mDisplacementY > 0) {
            mSubtractDisplacement = mTouchSlop;
        } else {
            mSubtractDisplacement = -mTouchSlop;
        }
        if (DBG) {
            Log.d(TAG, "onScrollStart recatch:" + recatch);
        }
        return true;
    }

    private boolean reportScroll() {
        float delta = mDisplacementY - mLastDisplacement;
        if (delta != 0) {
            if (DBG) {
                Log.d(TAG, String.format("onScroll disp=%.1f, velocity=%.1f",
                        mDisplacementY, mVelocity));
            }

            return mListener.onScroll(mDisplacementY - mSubtractDisplacement, mVelocity);
        }
        return true;
    }

    private void reportScrollEnd() {
        if (DBG) {
            Log.d(TAG, String.format("onScrolEnd disp=%.1f, velocity=%.1f",
                    mDisplacementY, mVelocity));
        }
        mListener.onScrollEnd(mVelocity, Math.abs(mVelocity) > RELEASE_VELOCITY_PX_MS);

    }
    /**
     * Computes the damped velocity using the two motion events and the previous velocity.
     */
    private float computeVelocity(MotionEvent to, float previousVelocity) {
        float delta = computeDelta(to);

        float deltaTimeMillis = to.getEventTime() - mLastMillis;
        float velocity = (deltaTimeMillis > 0) ? (delta / deltaTimeMillis) : 0;
        if (Math.abs(previousVelocity) < 0.001f) {
            return velocity;
        }

        float alpha = computeDampeningFactor(deltaTimeMillis);
        return interpolate(previousVelocity, velocity, alpha);
    }

    private float computeDelta(MotionEvent to) {
        return to.getY() - mLastY;
    }

    /**
     * Returns a time-dependent dampening factor using delta time.
     */
    private static float computeDampeningFactor(float deltaTime) {
        return deltaTime / (SCROLL_VELOCITY_DAMPENING_RC + deltaTime);
    }

    /**
     * Returns the linear interpolation between two values
     */
    private static float interpolate(float from, float to, float alpha) {
        return (1.0f - alpha) * from + alpha * to;
    }
}

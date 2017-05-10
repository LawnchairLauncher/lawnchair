package com.android.launcher3.allapps;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.Interpolator;

/**
 * One dimensional scroll gesture detector for all apps container pull up interaction.
 * Client (e.g., AllAppsTransitionController) of this class can register a listener.
 * <p/>
 * Features that this gesture detector can support.
 */
public class VerticalPullDetector {

    private static final boolean DBG = false;
    private static final String TAG = "VerticalPullDetector";

    private float mTouchSlop;

    private int mScrollConditions;
    public static final int DIRECTION_UP = 1 << 0;
    public static final int DIRECTION_DOWN = 1 << 1;
    public static final int DIRECTION_BOTH = DIRECTION_DOWN | DIRECTION_UP;

    private static final float ANIMATION_DURATION = 1200;
    private static final float FAST_FLING_PX_MS = 10;

    /**
     * The minimum release velocity in pixels per millisecond that triggers fling..
     */
    public static final float RELEASE_VELOCITY_PX_MS = 1.0f;

    /**
     * The time constant used to calculate dampening in the low-pass filter of scroll velocity.
     * Cutoff frequency is set at 10 Hz.
     */
    public static final float SCROLL_VELOCITY_DAMPENING_RC = 1000f / (2f * (float) Math.PI * 10);

    /* Scroll state, this is set to true during dragging and animation. */
    private ScrollState mState = ScrollState.IDLE;

    enum ScrollState {
        IDLE,
        DRAGGING,      // onDragStart, onDrag
        SETTLING       // onDragEnd
    }

    ;

    //------------------- ScrollState transition diagram -----------------------------------
    //
    // IDLE ->      (mDisplacement > mTouchSlop) -> DRAGGING
    // DRAGGING -> (MotionEvent#ACTION_UP, MotionEvent#ACTION_CANCEL) -> SETTLING
    // SETTLING -> (MotionEvent#ACTION_DOWN) -> DRAGGING
    // SETTLING -> (View settled) -> IDLE

    private void setState(ScrollState newState) {
        if (DBG) {
            Log.d(TAG, "setState:" + mState + "->" + newState);
        }
        // onDragStart and onDragEnd is reported ONLY on state transition
        if (newState == ScrollState.DRAGGING) {
            initializeDragging();
            if (mState == ScrollState.IDLE) {
                reportDragStart(false /* recatch */);
            } else if (mState == ScrollState.SETTLING) {
                reportDragStart(true /* recatch */);
            }
        }
        if (newState == ScrollState.SETTLING) {
            reportDragEnd();
        }

        mState = newState;
    }

    public boolean isDraggingOrSettling() {
        return mState == ScrollState.DRAGGING || mState == ScrollState.SETTLING;
    }

    /**
     * There's no touch and there's no animation.
     */
    public boolean isIdleState() {
        return mState == ScrollState.IDLE;
    }

    public boolean isSettlingState() {
        return mState == ScrollState.SETTLING;
    }

    public boolean isDraggingState() {
        return mState == ScrollState.DRAGGING;
    }

    private float mDownX;
    private float mDownY;

    private float mLastY;
    private long mCurrentMillis;

    private float mVelocity;
    private float mLastDisplacement;
    private float mDisplacementY;
    private float mDisplacementX;

    private float mSubtractDisplacement;
    private boolean mIgnoreSlopWhenSettling;

    /* Client of this gesture detector can register a callback. */
    Listener mListener;

    public void setListener(Listener l) {
        mListener = l;
    }

    public interface Listener {
        void onDragStart(boolean start);

        boolean onDrag(float displacement, float velocity);

        void onDragEnd(float velocity, boolean fling);
    }

    public VerticalPullDetector(Context context) {
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setDetectableScrollConditions(int scrollDirectionFlags, boolean ignoreSlop) {
        mScrollConditions = scrollDirectionFlags;
        mIgnoreSlopWhenSettling = ignoreSlop;
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
        if (((mScrollConditions & DIRECTION_DOWN) > 0 && mDisplacementY > 0) ||
                ((mScrollConditions & DIRECTION_UP) > 0 && mDisplacementY < 0)) {
            return true;
        }
        return false;
    }

    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = ev.getX();
                mDownY = ev.getY();
                mLastDisplacement = 0;
                mDisplacementY = 0;
                mVelocity = 0;

                if (mState == ScrollState.SETTLING && mIgnoreSlopWhenSettling) {
                    setState(ScrollState.DRAGGING);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                mDisplacementX = ev.getX() - mDownX;
                mDisplacementY = ev.getY() - mDownY;
                computeVelocity(ev);

                // handle state and listener calls.
                if (mState != ScrollState.DRAGGING && shouldScrollStart()) {
                    setState(ScrollState.DRAGGING);
                }
                if (mState == ScrollState.DRAGGING) {
                    reportDragging();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                // These are synthetic events and there is no need to update internal values.
                if (mState == ScrollState.DRAGGING) {
                    setState(ScrollState.SETTLING);
                }
                break;
            default:
                //TODO: add multi finger tracking by tracking active pointer.
                break;
        }
        // Do house keeping.
        mLastDisplacement = mDisplacementY;
        mLastY = ev.getY();
        return true;
    }

    public void finishedScrolling() {
        setState(ScrollState.IDLE);
    }

    private boolean reportDragStart(boolean recatch) {
        mListener.onDragStart(!recatch);
        if (DBG) {
            Log.d(TAG, "onDragStart recatch:" + recatch);
        }
        return true;
    }

    private void initializeDragging() {
        if (mState == ScrollState.SETTLING && mIgnoreSlopWhenSettling) {
            mSubtractDisplacement = 0;
        }
        if (mDisplacementY > 0) {
            mSubtractDisplacement = mTouchSlop;
        } else {
            mSubtractDisplacement = -mTouchSlop;
        }
    }

    private boolean reportDragging() {
        float delta = mDisplacementY - mLastDisplacement;
        if (delta != 0) {
            if (DBG) {
                Log.d(TAG, String.format("onDrag disp=%.1f, velocity=%.1f",
                        mDisplacementY, mVelocity));
            }

            return mListener.onDrag(mDisplacementY - mSubtractDisplacement, mVelocity);
        }
        return true;
    }

    private void reportDragEnd() {
        if (DBG) {
            Log.d(TAG, String.format("onScrollEnd disp=%.1f, velocity=%.1f",
                    mDisplacementY, mVelocity));
        }
        mListener.onDragEnd(mVelocity, Math.abs(mVelocity) > RELEASE_VELOCITY_PX_MS);

    }

    /**
     * Computes the damped velocity using the two motion events and the previous velocity.
     */
    private float computeVelocity(MotionEvent to) {
        return computeVelocity(to.getY() - mLastY, to.getEventTime());
    }

    public float computeVelocity(float delta, long currentMillis) {
        long previousMillis = mCurrentMillis;
        mCurrentMillis = currentMillis;

        float deltaTimeMillis = mCurrentMillis - previousMillis;
        float velocity = (deltaTimeMillis > 0) ? (delta / deltaTimeMillis) : 0;
        if (Math.abs(mVelocity) < 0.001f) {
            mVelocity = velocity;
        } else {
            float alpha = computeDampeningFactor(deltaTimeMillis);
            mVelocity = interpolate(mVelocity, velocity, alpha);
        }
        return mVelocity;
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

    public long calculateDuration(float velocity, float progressNeeded) {
        // TODO: make these values constants after tuning.
        float velocityDivisor = Math.max(2f, Math.abs(0.5f * velocity));
        float travelDistance = Math.max(0.2f, progressNeeded);
        long duration = (long) Math.max(100, ANIMATION_DURATION / velocityDivisor * travelDistance);
        if (DBG) {
            Log.d(TAG, String.format("calculateDuration=%d, v=%f, d=%f", duration, velocity, progressNeeded));
        }
        return duration;
    }

    public static class ScrollInterpolator implements Interpolator {

        boolean mSteeper;

        public void setVelocityAtZero(float velocity) {
            mSteeper = velocity > FAST_FLING_PX_MS;
        }

        public float getInterpolation(float t) {
            t -= 1.0f;
            float output = t * t * t;
            if (mSteeper) {
                output *= t * t; // Make interpolation initial slope steeper
            }
            return output + 1;
        }
    }
}

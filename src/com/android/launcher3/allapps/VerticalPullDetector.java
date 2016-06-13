package com.android.launcher3.allapps;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/**
 * One dimensional scroll gesture detector for all apps container pull up interaction.
 */
public class VerticalPullDetector {

    private static final String TAG = "ScrollGesture";
    private static final boolean DBG = false;

    private float mTouchSlop;
    private boolean mScrollDown; // if false, only scroll up will be reported.
    private boolean mDisallowRecatchFromTop;
    private boolean mDisallowRecatchFromBottom;

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
    enum State {NONE, DRAG, SCROLLING};

    private void setState(State newState) {
        if (DBG) {
            Log.d(TAG, mState + "->" + newState);
        }
        mState = newState;
    }

    public boolean shouldIntercept() {
        return mState == State.DRAG;
    }

    public boolean isRestingState() {
        return mState == State.NONE;
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

    /* scroll started during previous animation */
    private boolean mSubtractSlop = true;

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

    public void setDetectableScrollConditions(boolean scrollDown, boolean disallowRecatchFromTop,
            boolean disallowRecatchFromBottom) {
        mScrollDown = scrollDown;
        mDisallowRecatchFromTop = disallowRecatchFromTop;
        mDisallowRecatchFromBottom = disallowRecatchFromBottom;
    }

    private boolean shouldScrollStart() {
        float deltaY = Math.abs(mDisplacementY);
        float deltaX = Math.max(Math.abs(mDisplacementX), 1);
        if (mScrollDown && mDisplacementY > mTouchSlop) {
            if (deltaY > deltaX) {
                return true;
            }
        }
        if (!mScrollDown && mDisplacementY < -mTouchSlop) {
            if (deltaY > deltaX) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldRecatchScrollStart() {
        if (!mDisallowRecatchFromBottom && !mDisallowRecatchFromTop) {
            return true;
        }
        if (mDisallowRecatchFromTop && mDisplacementY > mTouchSlop) {
            mDisallowRecatchFromTop = false;
            return true;
        }
        if (mDisallowRecatchFromBottom && mDisplacementY < -mTouchSlop) {
            mDisallowRecatchFromBottom = false;
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
                if (mState == State.SCROLLING && shouldRecatchScrollStart()){
                    reportScrollStart(true /* recatch */);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                mDisplacementX = ev.getX() - mDownX;
                mDisplacementY = ev.getY() - mDownY;
                mVelocity = computeVelocity(ev, mVelocity);

                if (mState == State.SCROLLING && shouldRecatchScrollStart()){
                    setState(State.DRAG);
                    reportScrollStart(true /* recatch */);
                }
                if (mState == State.NONE && shouldScrollStart()) {
                    setState(State.DRAG);
                    reportScrollStart(false /* recatch */);
                }
                if (mState == State.DRAG && mListener != null) {
                    reportScroll();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                // These are synthetic events and there is no need to update internal values.
                if (mState == State.DRAG && mListener != null) {
                    reportScrollEnd();
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
        mSubtractSlop = !recatch;
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
            float subtractDisplacement = 0f;
            if (mSubtractSlop) {
                if (mDisplacementY > 0) {
                    subtractDisplacement = mTouchSlop;
                } else {
                    subtractDisplacement = -mTouchSlop;
                }
            }
            return mListener.onScroll(mDisplacementY - subtractDisplacement, mVelocity);
        }
        return true;
    }

    private void reportScrollEnd() {
        if (DBG) {
            Log.d(TAG, String.format("onScrolEnd disp=%.1f, velocity=%.1f",
                    mDisplacementY, mVelocity));
        }
        mListener.onScrollEnd(mVelocity, Math.abs(mVelocity) > RELEASE_VELOCITY_PX_MS);
        setState(State.SCROLLING);
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

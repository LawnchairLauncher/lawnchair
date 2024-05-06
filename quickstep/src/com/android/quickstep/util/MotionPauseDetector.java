/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep.util;

import static com.android.launcher3.testing.shared.TestProtocol.PAUSE_DETECTED_MESSAGE;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;

import com.android.launcher3.Alarm;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.AccessibilityManagerCompat;

/**
 * Given positions along x- or y-axis, tracks velocity and acceleration and determines when there is
 * a pause in motion.
 */
public class MotionPauseDetector {

    private static final String TAG = "MotionPauseDetector";

    // The percentage of the previous speed that determines whether this is a rapid deceleration.
    // The bigger this number, the easier it is to trigger the first pause.
    private static final float RAPID_DECELERATION_FACTOR = 0.6f;

    /** If no motion is added for this amount of time, assume the motion has paused. */
    private static final long FORCE_PAUSE_TIMEOUT = 300;

    /**
     * After {@link #mMakePauseHarderToTrigger}, must move slowly for this long to trigger a pause.
     */
    private static final long HARDER_TRIGGER_TIMEOUT = 400;

    /**
     * When running in a test harness, if no motion is added for this amount of time, assume the
     * motion has paused. (We use an increased timeout since sometimes test devices can be slow.)
     */
    private static final long TEST_HARNESS_TRIGGER_TIMEOUT = 2000;

    private final float mSpeedVerySlow;
    private final float mSpeedSlow;
    private final float mSpeedSomewhatFast;
    private final float mSpeedFast;
    private final Alarm mForcePauseTimeout;
    private final boolean mMakePauseHarderToTrigger;
    private final Context mContext;
    private final SystemVelocityProvider mVelocityProvider;

    private Float mPreviousVelocity = null;

    private OnMotionPauseListener mOnMotionPauseListener;
    private boolean mIsPaused;
    // Bias more for the first pause to make it feel extra responsive.
    private boolean mHasEverBeenPaused;
    /** @see #setDisallowPause(boolean) */
    private boolean mDisallowPause;
    // Time at which speed became < mSpeedSlow (only used if mMakePauseHarderToTrigger == true).
    private long mSlowStartTime;

    public MotionPauseDetector(Context context) {
        this(context, false);
    }

    /**
     * @param makePauseHarderToTrigger Used for gestures that require a more explicit pause.
     */
    public MotionPauseDetector(Context context, boolean makePauseHarderToTrigger) {
        this(context, makePauseHarderToTrigger, MotionEvent.AXIS_Y);
    }

    /**
     * @param makePauseHarderToTrigger Used for gestures that require a more explicit pause.
     */
    public MotionPauseDetector(Context context, boolean makePauseHarderToTrigger, int axis) {
        mContext = context;
        Resources res = context.getResources();
        mSpeedVerySlow = res.getDimension(R.dimen.motion_pause_detector_speed_very_slow);
        mSpeedSlow = res.getDimension(R.dimen.motion_pause_detector_speed_slow);
        mSpeedSomewhatFast = res.getDimension(R.dimen.motion_pause_detector_speed_somewhat_fast);
        mSpeedFast = res.getDimension(R.dimen.motion_pause_detector_speed_fast);
        mForcePauseTimeout = new Alarm();
        mForcePauseTimeout.setOnAlarmListener(alarm -> {
            ActiveGestureLog.CompoundString log =
                    new ActiveGestureLog.CompoundString("Force pause timeout after ")
                            .append(alarm.getLastSetTimeout())
                            .append("ms");
            addLogs(log);
            updatePaused(true /* isPaused */, log);
        });
        mMakePauseHarderToTrigger = makePauseHarderToTrigger;
        mVelocityProvider = new SystemVelocityProvider(axis);
    }

    /**
     * Get callbacks for when motion pauses and resumes.
     */
    public void setOnMotionPauseListener(OnMotionPauseListener listener) {
        mOnMotionPauseListener = listener;
    }

    /**
     * @param disallowPause If true, we will not detect any pauses until this is set to false again.
     */
    public void setDisallowPause(boolean disallowPause) {
        ActiveGestureLog.CompoundString log =
                new ActiveGestureLog.CompoundString("Set disallowPause=")
                        .append(disallowPause);
        if (mDisallowPause != disallowPause) {
            addLogs(log);
        }
        mDisallowPause = disallowPause;
        updatePaused(mIsPaused, log);
    }

    /**
     * Computes velocity and acceleration to determine whether the motion is paused.
     * @param ev The motion being tracked.
     */
    public void addPosition(MotionEvent ev) {
        addPosition(ev, 0);
    }

    /**
     * Computes velocity and acceleration to determine whether the motion is paused.
     * @param ev The motion being tracked.
     * @param pointerIndex Index for the pointer being tracked in the motion event
     */
    public void addPosition(MotionEvent ev, int pointerIndex) {
        long timeoutMs = Utilities.isRunningInTestHarness()
                ? TEST_HARNESS_TRIGGER_TIMEOUT
                : mMakePauseHarderToTrigger
                        ? HARDER_TRIGGER_TIMEOUT
                        : FORCE_PAUSE_TIMEOUT;
        mForcePauseTimeout.setAlarm(timeoutMs);
        float newVelocity = mVelocityProvider.addMotionEvent(ev, ev.getPointerId(pointerIndex));
        if (mPreviousVelocity != null) {
            checkMotionPaused(newVelocity, mPreviousVelocity, ev.getEventTime());
        }
        mPreviousVelocity = newVelocity;
    }

    private void checkMotionPaused(float velocity, float prevVelocity, long time) {
        float speed = Math.abs(velocity);
        float previousSpeed = Math.abs(prevVelocity);
        boolean isPaused;
        ActiveGestureLog.CompoundString isPausedReason;
        if (mIsPaused) {
            // Continue to be paused until moving at a fast speed.
            isPaused = speed < mSpeedFast || previousSpeed < mSpeedFast;
            isPausedReason = new ActiveGestureLog.CompoundString(
                    "Was paused, but started moving at a fast speed");
        } else {
            if (velocity < 0 != prevVelocity < 0) {
                // We're just changing directions, not necessarily stopping.
                isPaused = false;
                isPausedReason = new ActiveGestureLog.CompoundString("Velocity changed directions");
            } else {
                isPaused = speed < mSpeedVerySlow && previousSpeed < mSpeedVerySlow;
                isPausedReason = new ActiveGestureLog.CompoundString(
                        "Pause requires back to back slow speeds");
                if (!isPaused && !mHasEverBeenPaused) {
                    // We want to be more aggressive about detecting the first pause to ensure it
                    // feels as responsive as possible; getting two very slow speeds back to back
                    // takes too long, so also check for a rapid deceleration.
                    boolean isRapidDeceleration = speed < previousSpeed * RAPID_DECELERATION_FACTOR;
                    isPaused = isRapidDeceleration && speed < mSpeedSomewhatFast;
                    isPausedReason = new ActiveGestureLog.CompoundString(
                            "Didn't have back to back slow speeds, checking for rapid ")
                            .append(" deceleration on first pause only");
                }
                if (mMakePauseHarderToTrigger) {
                    if (speed < mSpeedSlow) {
                        if (mSlowStartTime == 0) {
                            mSlowStartTime = time;
                        }
                        isPaused = time - mSlowStartTime >= HARDER_TRIGGER_TIMEOUT;
                        isPausedReason = new ActiveGestureLog.CompoundString(
                                "Maintained slow speed for sufficient duration when making")
                                .append(" pause harder to trigger");
                    } else {
                        mSlowStartTime = 0;
                        isPaused = false;
                        isPausedReason = new ActiveGestureLog.CompoundString(
                                "Intentionally making pause harder to trigger");
                    }
                }
            }
        }
        updatePaused(isPaused, isPausedReason);
    }

    private void updatePaused(boolean isPaused, ActiveGestureLog.CompoundString reason) {
        if (mDisallowPause) {
            reason = new ActiveGestureLog.CompoundString(
                    "Disallow pause; otherwise, would have been ")
                    .append(isPaused)
                    .append(" due to reason:")
                    .append(reason);
            isPaused = false;
        }
        if (mIsPaused != isPaused) {
            mIsPaused = isPaused;
            addLogs(new ActiveGestureLog.CompoundString("onMotionPauseChanged triggered; paused=")
                    .append(mIsPaused)
                    .append(", reason=")
                    .append(reason));
            boolean isFirstDetectedPause = !mHasEverBeenPaused && mIsPaused;
            if (mIsPaused) {
                AccessibilityManagerCompat.sendTestProtocolEventToTest(mContext,
                        PAUSE_DETECTED_MESSAGE);
                mHasEverBeenPaused = true;
            }
            if (mOnMotionPauseListener != null) {
                if (isFirstDetectedPause) {
                    mOnMotionPauseListener.onMotionPauseDetected();
                }
                // Null check again as onMotionPauseDetected() maybe have called clear().
                if (mOnMotionPauseListener != null) {
                    mOnMotionPauseListener.onMotionPauseChanged(mIsPaused);
                }
            }
        }
    }

    private void addLogs(ActiveGestureLog.CompoundString compoundString) {
        ActiveGestureLog.CompoundString logString =
                new ActiveGestureLog.CompoundString("MotionPauseDetector: ")
                        .append(compoundString);
        if (Utilities.isRunningInTestHarness()) {
            Log.d(TAG, logString.toString());
        }
        ActiveGestureLog.INSTANCE.addLog(logString);
    }

    public void clear() {
        mVelocityProvider.clear();
        mPreviousVelocity = null;
        setOnMotionPauseListener(null);
        mIsPaused = mHasEverBeenPaused = false;
        mSlowStartTime = 0;
        mForcePauseTimeout.cancelAlarm();
    }

    public boolean isPaused() {
        return mIsPaused;
    }

    public interface OnMotionPauseListener {
        /** Called only the first time motion pause is detected. */
        void onMotionPauseDetected();
        /** Called every time motion changes from paused to not paused and vice versa. */
        default void onMotionPauseChanged(boolean isPaused) { }
    }

    private static class SystemVelocityProvider {

        private final VelocityTracker mVelocityTracker;
        private final int mAxis;

        SystemVelocityProvider(int axis) {
            mVelocityTracker = VelocityTracker.obtain();
            mAxis = axis;
        }

        /**
         * Adds a new motion events, and returns the velocity at this point, or null if
         * the velocity is not available
         */
        public float addMotionEvent(MotionEvent ev, int pointer) {
            mVelocityTracker.addMovement(ev);
            mVelocityTracker.computeCurrentVelocity(1); // px / ms
            return mAxis == MotionEvent.AXIS_X
                    ? mVelocityTracker.getXVelocity(pointer)
                    : mVelocityTracker.getYVelocity(pointer);
        }

        /**
         * Clears all stored motion event records
         */
        public void clear() {
            mVelocityTracker.clear();
        }
    }
}

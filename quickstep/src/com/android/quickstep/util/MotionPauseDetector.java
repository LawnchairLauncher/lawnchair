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

import static com.android.launcher3.config.FeatureFlags.ENABLE_LSQ_VELOCITY_PROVIDER;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.MotionEvent;

import com.android.launcher3.Alarm;
import com.android.launcher3.R;
import com.android.launcher3.compat.AccessibilityManagerCompat;
import com.android.launcher3.testing.TestProtocol;

/**
 * Given positions along x- or y-axis, tracks velocity and acceleration and determines when there is
 * a pause in motion.
 */
public class MotionPauseDetector {

    // The percentage of the previous speed that determines whether this is a rapid deceleration.
    // The bigger this number, the easier it is to trigger the first pause.
    private static final float RAPID_DECELERATION_FACTOR = 0.6f;

    /** If no motion is added for this amount of time, assume the motion has paused. */
    private static final long FORCE_PAUSE_TIMEOUT = 300;

    /**
     * After {@link #mMakePauseHarderToTrigger}, must move slowly for this long to trigger a pause.
     */
    private static final long HARDER_TRIGGER_TIMEOUT = 400;

    private final float mSpeedVerySlow;
    private final float mSpeedSlow;
    private final float mSpeedSomewhatFast;
    private final float mSpeedFast;
    private final Alarm mForcePauseTimeout;
    private final boolean mMakePauseHarderToTrigger;
    private final Context mContext;
    private final VelocityProvider mVelocityProvider;

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
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.PAUSE_NOT_DETECTED, "creating alarm");
        }
        mForcePauseTimeout = new Alarm();
        mForcePauseTimeout.setOnAlarmListener(alarm -> updatePaused(true /* isPaused */));
        mMakePauseHarderToTrigger = makePauseHarderToTrigger;
        mVelocityProvider = ENABLE_LSQ_VELOCITY_PROVIDER.get()
                ? new LSqVelocityProvider(axis) : new LinearVelocityProvider(axis);
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
        mDisallowPause = disallowPause;
        updatePaused(mIsPaused);
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
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.PAUSE_NOT_DETECTED, "setting alarm");
        }
        mForcePauseTimeout.setAlarm(mMakePauseHarderToTrigger
                ? HARDER_TRIGGER_TIMEOUT
                : FORCE_PAUSE_TIMEOUT);
        Float newVelocity = mVelocityProvider.addMotionEvent(ev, pointerIndex);
        if (newVelocity != null && mPreviousVelocity != null) {
            checkMotionPaused(newVelocity, mPreviousVelocity, ev.getEventTime());
        }
        mPreviousVelocity = newVelocity;
    }

    private void checkMotionPaused(float velocity, float prevVelocity, long time) {
        float speed = Math.abs(velocity);
        float previousSpeed = Math.abs(prevVelocity);
        boolean isPaused;
        if (mIsPaused) {
            // Continue to be paused until moving at a fast speed.
            isPaused = speed < mSpeedFast || previousSpeed < mSpeedFast;
        } else {
            if (velocity < 0 != prevVelocity < 0) {
                // We're just changing directions, not necessarily stopping.
                isPaused = false;
            } else {
                isPaused = speed < mSpeedVerySlow && previousSpeed < mSpeedVerySlow;
                if (!isPaused && !mHasEverBeenPaused) {
                    // We want to be more aggressive about detecting the first pause to ensure it
                    // feels as responsive as possible; getting two very slow speeds back to back
                    // takes too long, so also check for a rapid deceleration.
                    boolean isRapidDeceleration = speed < previousSpeed * RAPID_DECELERATION_FACTOR;
                    isPaused = isRapidDeceleration && speed < mSpeedSomewhatFast;
                }
                if (mMakePauseHarderToTrigger) {
                    if (speed < mSpeedSlow) {
                        if (mSlowStartTime == 0) {
                            mSlowStartTime = time;
                        }
                        isPaused = time - mSlowStartTime >= HARDER_TRIGGER_TIMEOUT;
                    } else {
                        mSlowStartTime = 0;
                        isPaused = false;
                    }
                }
            }
        }
        updatePaused(isPaused);
    }

    private void updatePaused(boolean isPaused) {
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.PAUSE_NOT_DETECTED, "updatePaused: " + isPaused);
        }
        if (mDisallowPause) {
            isPaused = false;
        }
        if (mIsPaused != isPaused) {
            mIsPaused = isPaused;
            if (mIsPaused) {
                AccessibilityManagerCompat.sendPauseDetectedEventToTest(mContext);
                mHasEverBeenPaused = true;
            }
            if (mOnMotionPauseListener != null) {
                mOnMotionPauseListener.onMotionPauseChanged(mIsPaused);
            }
        }
    }

    public void clear() {
        mVelocityProvider.clear();
        mPreviousVelocity = null;
        setOnMotionPauseListener(null);
        mIsPaused = mHasEverBeenPaused = false;
        mSlowStartTime = 0;
        if (TestProtocol.sDebugTracing) {
            Log.d(TestProtocol.PAUSE_NOT_DETECTED, "canceling alarm");
        }
        mForcePauseTimeout.cancelAlarm();
    }

    public boolean isPaused() {
        return mIsPaused;
    }

    public interface OnMotionPauseListener {
        void onMotionPauseChanged(boolean isPaused);
    }

    /**
     * Interface to abstract out velocity calculations
     */
    protected interface VelocityProvider {

        /**
         * Adds a new motion events, and returns the velocity at this point, or null if
         * the velocity is not available
         */
        Float addMotionEvent(MotionEvent ev, int pointer);

        /**
         * Clears all stored motion event records
         */
        void clear();
    }

    private static class LinearVelocityProvider implements VelocityProvider {

        private Long mPreviousTime = null;
        private Float mPreviousPosition = null;

        private final int mAxis;

        LinearVelocityProvider(int axis) {
            mAxis = axis;
        }

        @Override
        public Float addMotionEvent(MotionEvent ev, int pointer) {
            long time = ev.getEventTime();
            float position = ev.getAxisValue(mAxis, pointer);
            Float velocity = null;

            if (mPreviousTime != null && mPreviousPosition != null) {
                long changeInTime = Math.max(1, time - mPreviousTime);
                float changeInPosition = position - mPreviousPosition;
                velocity = changeInPosition / changeInTime;
            }
            mPreviousTime = time;
            mPreviousPosition = position;
            return velocity;
        }

        @Override
        public void clear() {
            mPreviousTime = null;
            mPreviousPosition = null;
        }
    }

    /**
     * Java implementation of {@link android.view.VelocityTracker} using the Least Square (deg 2)
     * algorithm.
     */
    private static class LSqVelocityProvider implements VelocityProvider {

        // Maximum age of a motion event to be considered when calculating the velocity.
        private static final long HORIZON_MS = 100;
        // Number of samples to keep.
        private static final int HISTORY_SIZE = 20;

        // Position history are stored in a circular array
        private final long[] mHistoricTimes = new long[HISTORY_SIZE];
        private final float[] mHistoricPos = new float[HISTORY_SIZE];
        private int mHistoryCount = 0;
        private int mHistoryStart = 0;

        private final int mAxis;

        LSqVelocityProvider(int axis) {
            mAxis = axis;
        }

        @Override
        public void clear() {
            mHistoryCount = mHistoryStart = 0;
        }

        private void addPositionAndTime(long eventTime, float eventPosition) {
            mHistoricTimes[mHistoryStart] = eventTime;
            mHistoricPos[mHistoryStart] = eventPosition;
            mHistoryStart++;
            if (mHistoryStart >= HISTORY_SIZE) {
                mHistoryStart = 0;
            }
            mHistoryCount = Math.min(HISTORY_SIZE, mHistoryCount + 1);
        }

        @Override
        public Float addMotionEvent(MotionEvent ev, int pointer) {
            // Add all historic points
            int historyCount = ev.getHistorySize();
            for (int i = 0; i < historyCount; i++) {
                addPositionAndTime(
                        ev.getHistoricalEventTime(i), ev.getHistoricalAxisValue(mAxis, pointer, i));
            }

            // Start index for the last position (about to be added)
            int eventStartIndex = mHistoryStart;
            addPositionAndTime(ev.getEventTime(), ev.getAxisValue(mAxis, pointer));
            return solveUnweightedLeastSquaresDeg2(eventStartIndex);
        }

        /**
         * Solves the instantaneous velocity.
         * Based on solveUnweightedLeastSquaresDeg2 in VelocityTracker.cpp
         */
        private Float solveUnweightedLeastSquaresDeg2(final int pointPos) {
            final long eventTime = mHistoricTimes[pointPos];

            float sxi = 0, sxiyi = 0, syi = 0, sxi2 = 0, sxi3 = 0, sxi2yi = 0, sxi4 = 0;
            int count = 0;
            for (int i = 0; i < mHistoryCount; i++) {
                int index = pointPos - i;
                if (index < 0) {
                    index += HISTORY_SIZE;
                }

                long time = mHistoricTimes[index];
                long age = eventTime - time;
                if (age > HORIZON_MS) {
                    break;
                }
                count++;
                float xi = -age;

                float yi = mHistoricPos[index];
                float xi2 = xi * xi;
                float xi3 = xi2 * xi;
                float xi4 = xi3 * xi;
                float xiyi = xi * yi;
                float xi2yi = xi2 * yi;

                sxi += xi;
                sxi2 += xi2;
                sxiyi += xiyi;
                sxi2yi += xi2yi;
                syi += yi;
                sxi3 += xi3;
                sxi4 += xi4;
            }

            if (count < 3) {
                // Too few samples
                switch (count) {
                    case 2: {
                        int endPos = pointPos - 1;
                        if (endPos < 0) {
                            endPos += HISTORY_SIZE;
                        }
                        long denominator = eventTime - mHistoricTimes[endPos];
                        if (denominator != 0) {
                            return (mHistoricPos[pointPos] - mHistoricPos[endPos]) / denominator;
                        }
                    }
                    // fall through
                    case 1:
                        return 0f;
                    default:
                        return null;
                }
            }

            float Sxx = sxi2 - sxi * sxi / count;
            float Sxy = sxiyi - sxi * syi / count;
            float Sxx2 = sxi3 - sxi * sxi2 / count;
            float Sx2y = sxi2yi - sxi2 * syi / count;
            float Sx2x2 = sxi4 - sxi2 * sxi2 / count;

            float denominator = Sxx * Sx2x2 - Sxx2 * Sxx2;
            if (denominator == 0) {
                // division by 0 when computing velocity
                return null;
            }
            // Compute a
            // float numerator = Sx2y*Sxx - Sxy*Sxx2;

            // Compute b
            float numerator = Sxy * Sx2x2 - Sx2y * Sxx2;
            float b = numerator / denominator;

            // Compute c
            // float c = syi/count - b * sxi/count - a * sxi2/count;

            return b;
        }
    }
}

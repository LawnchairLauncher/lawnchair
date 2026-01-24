/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.pip.phone;

import android.graphics.PointF;
import android.view.Display;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

import java.io.PrintWriter;

/**
 * This keeps track of the touch state throughout the current touch gesture.
 */
public class PipTouchState {
    private static final String TAG = "PipTouchState";
    private static final boolean DEBUG = false;

    @VisibleForTesting
    public static final long DOUBLE_TAP_TIMEOUT = ViewConfiguration.getDoubleTapTimeout();
    static final long HOVER_EXIT_TIMEOUT = 50;

    private final ShellExecutor mMainExecutor;
    private final ViewConfiguration mViewConfig;
    private final Runnable mDoubleTapTimeoutCallback;
    private final Runnable mHoverExitTimeoutCallback;

    private VelocityTracker mVelocityTracker;
    private long mDownTouchTime = 0;
    private long mLastDownTouchTime = 0;
    private long mUpTouchTime = 0;
    private final PointF mDownTouch = new PointF();
    private final PointF mDownDelta = new PointF();
    private final PointF mLastTouch = new PointF();
    private final PointF mLastDelta = new PointF();
    private final PointF mVelocity = new PointF();
    private boolean mAllowTouches = true;

    // Set to false to block both PipTouchHandler and PipResizeGestureHandler's input processing
    private boolean mAllowInputEvents = true;
    private boolean mIsUserInteracting = false;
    // Set to true only if the multiple taps occur within the double tap timeout
    private boolean mIsDoubleTap = false;
    // Set to true only if a gesture
    private boolean mIsWaitingForDoubleTap = false;
    private boolean mIsDragging = false;
    // The previous gesture was a drag
    private boolean mPreviouslyDragging = false;
    private boolean mStartedDragging = false;
    private boolean mAllowDraggingOffscreen = false;
    private int mActivePointerId;
    private int mLastTouchDisplayId = Display.INVALID_DISPLAY;

    public PipTouchState(ViewConfiguration viewConfig, Runnable doubleTapTimeoutCallback,
            Runnable hoverExitTimeoutCallback, ShellExecutor mainExecutor) {
        mViewConfig = viewConfig;
        mDoubleTapTimeoutCallback = doubleTapTimeoutCallback;
        mHoverExitTimeoutCallback = hoverExitTimeoutCallback;
        mMainExecutor = mainExecutor;
    }

    /**
     * @return true if input processing is enabled for PiP in general.
     */
    public boolean getAllowInputEvents() {
        return mAllowInputEvents;
    }

    /**
     * @param allowInputEvents true to enable input processing for PiP in general.
     */
    public void setAllowInputEvents(boolean allowInputEvents) {
        mAllowInputEvents = allowInputEvents;
    }

    /**
     * Resets this state.
     */
    public void reset() {
        mAllowDraggingOffscreen = false;
        mIsDragging = false;
        mStartedDragging = false;
        mIsUserInteracting = false;
        mLastTouchDisplayId = Display.INVALID_DISPLAY;
    }

    /**
     * Processes a given touch event and updates the state.
     */
    public void onTouchEvent(MotionEvent ev) {
        mLastTouchDisplayId = ev.getDisplayId();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (!mAllowTouches) {
                    return;
                }

                // Initialize the velocity tracker
                initOrResetVelocityTracker();
                addMovementToVelocityTracker(ev);

                mActivePointerId = ev.getPointerId(0);
                if (DEBUG) {
                    ProtoLog.e(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                            "%s: Setting active pointer id on DOWN: %d", TAG, mActivePointerId);
                }
                mLastTouch.set(ev.getRawX(), ev.getRawY());
                mDownTouch.set(mLastTouch);
                mAllowDraggingOffscreen = true;
                mIsUserInteracting = true;
                mDownTouchTime = ev.getEventTime();
                mIsDoubleTap = !mPreviouslyDragging
                        && (mDownTouchTime - mLastDownTouchTime) < DOUBLE_TAP_TIMEOUT;
                mIsWaitingForDoubleTap = false;
                mIsDragging = false;
                mLastDownTouchTime = mDownTouchTime;
                if (mDoubleTapTimeoutCallback != null) {
                    mMainExecutor.removeCallbacks(mDoubleTapTimeoutCallback);
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                // Skip event if we did not start processing this touch gesture
                if (!mIsUserInteracting) {
                    break;
                }

                // Update the velocity tracker
                addMovementToVelocityTracker(ev);
                int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex == -1) {
                    ProtoLog.e(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                            "%s: Invalid active pointer id on MOVE: %d", TAG, mActivePointerId);
                    break;
                }

                float x = ev.getRawX(pointerIndex);
                float y = ev.getRawY(pointerIndex);
                mLastDelta.set(x - mLastTouch.x, y - mLastTouch.y);
                mDownDelta.set(x - mDownTouch.x, y - mDownTouch.y);

                boolean hasMovedBeyondTap = mDownDelta.length() > mViewConfig.getScaledTouchSlop();
                if (!mIsDragging) {
                    if (hasMovedBeyondTap) {
                        mIsDragging = true;
                        mStartedDragging = true;
                    }
                } else {
                    mStartedDragging = false;
                }
                mLastTouch.set(x, y);
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                // Skip event if we did not start processing this touch gesture
                if (!mIsUserInteracting) {
                    break;
                }

                // Update the velocity tracker
                addMovementToVelocityTracker(ev);

                int pointerIndex = ev.getActionIndex();
                int pointerId = ev.getPointerId(pointerIndex);
                if (pointerId == mActivePointerId) {
                    // Select a new active pointer id and reset the movement state
                    final int newPointerIndex = (pointerIndex == 0) ? 1 : 0;
                    mActivePointerId = ev.getPointerId(newPointerIndex);
                    if (DEBUG) {
                        ProtoLog.e(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                                "%s: Relinquish active pointer id on POINTER_UP: %d",
                                TAG, mActivePointerId);
                    }
                    mLastTouch.set(ev.getRawX(newPointerIndex), ev.getRawY(newPointerIndex));
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                // Skip event if we did not start processing this touch gesture
                if (!mIsUserInteracting) {
                    break;
                }

                // Update the velocity tracker
                addMovementToVelocityTracker(ev);
                mVelocityTracker.computeCurrentVelocity(1000,
                        mViewConfig.getScaledMaximumFlingVelocity());
                mVelocity.set(mVelocityTracker.getXVelocity(), mVelocityTracker.getYVelocity());

                int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex == -1) {
                    ProtoLog.e(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                            "%s: Invalid active pointer id on UP: %d", TAG, mActivePointerId);
                    break;
                }

                mUpTouchTime = ev.getEventTime();
                mLastTouch.set(ev.getRawX(pointerIndex), ev.getRawY(pointerIndex));
                mPreviouslyDragging = mIsDragging;
                mIsWaitingForDoubleTap = !mIsDoubleTap && !mIsDragging
                        && (mUpTouchTime - mDownTouchTime) < DOUBLE_TAP_TIMEOUT;

                // Fall through to clean up
            }
            case MotionEvent.ACTION_CANCEL: {
                recycleVelocityTracker();
                break;
            }
            case MotionEvent.ACTION_BUTTON_PRESS: {
                removeHoverExitTimeoutCallback();
                break;
            }
        }
    }

    /**
     * @return the velocity of the active touch pointer at the point it is lifted off the screen.
     */
    public PointF getVelocity() {
        return mVelocity;
    }

    /**
     * @return the last touch position of the active pointer.
     */
    public PointF getLastTouchPosition() {
        return mLastTouch;
    }

    /**
     * @return the movement delta between the last handled touch event and the previous touch
     * position.
     */
    public PointF getLastTouchDelta() {
        return mLastDelta;
    }

    /**
     * @return the down touch position.
     */
    public PointF getDownTouchPosition() {
        return mDownTouch;
    }

    /**
     * @return the movement delta between the last handled touch event and the down touch
     * position.
     */
    public PointF getDownTouchDelta() {
        return mDownDelta;
    }

    /**
     * @return whether the user has started dragging.
     */
    public boolean isDragging() {
        return mIsDragging;
    }

    /**
     * @return whether the user is currently interacting with the PiP.
     */
    public boolean isUserInteracting() {
        return mIsUserInteracting;
    }

    /**
     * @return whether the user has started dragging just in the last handled touch event.
     */
    public boolean startedDragging() {
        return mStartedDragging;
    }

    /**
     * @return Display ID of the last touch event.
     */
    public int getLastTouchDisplayId() {
        return mLastTouchDisplayId;
    }

    /**
     * Sets whether touching is currently allowed.
     */
    public void setAllowTouches(boolean allowTouches) {
        mAllowTouches = allowTouches;

        // If the user happens to touch down before this is sent from the system during a transition
        // then block any additional handling by resetting the state now
        if (mIsUserInteracting) {
            reset();
        }
    }

    /**
     * Disallows dragging offscreen for the duration of the current gesture.
     */
    public void setDisallowDraggingOffscreen() {
        mAllowDraggingOffscreen = false;
    }

    /**
     * @return whether dragging offscreen is allowed during this gesture.
     */
    public boolean allowDraggingOffscreen() {
        return mAllowDraggingOffscreen;
    }

    /**
     * @return whether this gesture is a double-tap.
     */
    public boolean isDoubleTap() {
        return mIsDoubleTap;
    }

    /**
     * @return whether this gesture will potentially lead to a following double-tap.
     */
    public boolean isWaitingForDoubleTap() {
        return mIsWaitingForDoubleTap;
    }

    /**
     * Schedules the callback to run if the next double tap does not occur.  Only runs if
     * isWaitingForDoubleTap() is true.
     */
    public void scheduleDoubleTapTimeoutCallback() {
        if (mIsWaitingForDoubleTap) {
            long delay = getDoubleTapTimeoutCallbackDelay();
            mMainExecutor.removeCallbacks(mDoubleTapTimeoutCallback);
            mMainExecutor.executeDelayed(mDoubleTapTimeoutCallback, delay);
        }
    }

    @VisibleForTesting
    public long getDoubleTapTimeoutCallbackDelay() {
        if (mIsWaitingForDoubleTap) {
            return Math.max(0, DOUBLE_TAP_TIMEOUT - (mUpTouchTime - mDownTouchTime));
        }
        return -1;
    }

    /**
     * Removes the timeout callback if it's in queue.
     */
    public void removeDoubleTapTimeoutCallback() {
        mIsWaitingForDoubleTap = false;
        mMainExecutor.removeCallbacks(mDoubleTapTimeoutCallback);
    }

    @VisibleForTesting
    public void scheduleHoverExitTimeoutCallback() {
        mMainExecutor.removeCallbacks(mHoverExitTimeoutCallback);
        mMainExecutor.executeDelayed(mHoverExitTimeoutCallback, HOVER_EXIT_TIMEOUT);
    }

    void removeHoverExitTimeoutCallback() {
        mMainExecutor.removeCallbacks(mHoverExitTimeoutCallback);
    }

    void addMovementToVelocityTracker(MotionEvent event) {
        if (mVelocityTracker == null) {
            return;
        }

        // Add movement to velocity tracker using raw screen X and Y coordinates instead
        // of window coordinates because the window frame may be moving at the same time.
        float deltaX = event.getRawX() - event.getX();
        float deltaY = event.getRawY() - event.getY();
        event.offsetLocation(deltaX, deltaY);
        mVelocityTracker.addMovement(event);
        event.offsetLocation(-deltaX, -deltaY);
    }

    private void initOrResetVelocityTracker() {
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        } else {
            mVelocityTracker.clear();
        }
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    public void dump(PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + TAG);
        pw.println(innerPrefix + "mAllowTouches=" + mAllowTouches);
        pw.println(innerPrefix + "mAllowInputEvents=" + mAllowInputEvents);
        pw.println(innerPrefix + "mActivePointerId=" + mActivePointerId);
        pw.println(innerPrefix + "mLastTouchDisplayId=" + mLastTouchDisplayId);
        pw.println(innerPrefix + "mDownTouch=" + mDownTouch);
        pw.println(innerPrefix + "mDownDelta=" + mDownDelta);
        pw.println(innerPrefix + "mLastTouch=" + mLastTouch);
        pw.println(innerPrefix + "mLastDelta=" + mLastDelta);
        pw.println(innerPrefix + "mVelocity=" + mVelocity);
        pw.println(innerPrefix + "mIsUserInteracting=" + mIsUserInteracting);
        pw.println(innerPrefix + "mIsDragging=" + mIsDragging);
        pw.println(innerPrefix + "mStartedDragging=" + mStartedDragging);
        pw.println(innerPrefix + "mAllowDraggingOffscreen=" + mAllowDraggingOffscreen);
    }
}

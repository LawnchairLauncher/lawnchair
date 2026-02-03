/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.quickstep.inputconsumers;

import static android.view.MotionEvent.INVALID_POINTER_ID;

import android.content.Context;
import android.graphics.PointF;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;

import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.bubbles.BubbleBarSwipeController;
import com.android.launcher3.taskbar.bubbles.BubbleBarViewController;
import com.android.launcher3.taskbar.bubbles.BubbleControllers;
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.quickstep.InputConsumer;
import com.android.systemui.shared.system.InputMonitorCompat;

/**
 * Listens for touch events on the bubble bar.
 */
// TODO(b/385928447): remove debug logs with Log.d
public class BubbleBarInputConsumer implements InputConsumer {

    private static final String TAG = "BubbleBarInputConsumer";

    private final BubbleStashController mBubbleStashController;
    private final BubbleBarViewController mBubbleBarViewController;
    @Nullable
    private final BubbleBarSwipeController mBubbleBarSwipeController;
    private final InputMonitorCompat mInputMonitorCompat;

    private boolean mPilfered;
    private boolean mPassedTouchSlop;
    private boolean mStashedOrCollapsedOnDown;

    private final int mTouchSlop;
    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();

    private final int mDisplayId;

    private long mDownTime;
    private final long mTimeForLongPress;
    private int mActivePointerId = INVALID_POINTER_ID;

    public BubbleBarInputConsumer(
            Context context,
            int displayId,
            BubbleControllers bubbleControllers,
            InputMonitorCompat inputMonitorCompat) {
        mDisplayId = displayId;
        mBubbleStashController = bubbleControllers.bubbleStashController;
        mBubbleBarViewController = bubbleControllers.bubbleBarViewController;
        mBubbleBarSwipeController = bubbleControllers.bubbleBarSwipeController.orElse(null);

        mInputMonitorCompat = inputMonitorCompat;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mTimeForLongPress = ViewConfiguration.getLongPressTimeout();
    }

    @Override
    public int getType() {
        return TYPE_BUBBLE_BAR;
    }

    @Override
    public int getDisplayId() {
        return mDisplayId;
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        final int action = ev.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mDownTime = System.currentTimeMillis();
                mActivePointerId = ev.getPointerId(0);
                mDownPos.set(ev.getX(), ev.getY());
                mLastPos.set(mDownPos);
                mStashedOrCollapsedOnDown = mBubbleStashController.isStashed() || isCollapsed();
                Log.d(TAG,
                        "ACTION_DOWN stashedOrCollapsed=" + mStashedOrCollapsedOnDown + " downPos="
                                + mDownPos);
                if (mBubbleBarSwipeController != null) {
                    mBubbleBarSwipeController.start();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex == INVALID_POINTER_ID) {
                    Log.d(TAG, "ACTION_MOVE skip, invalid pointer id");
                    break;
                }
                mLastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex));

                float dX = mLastPos.x - mDownPos.x;
                float dY = mLastPos.y - mDownPos.y;
                if (!mPassedTouchSlop) {
                    mPassedTouchSlop = Math.abs(dY) > mTouchSlop || Math.abs(dX) > mTouchSlop;
                    if (mPassedTouchSlop) {
                        Log.d(TAG, "ACTION_MOVE passed touch slop pos=" + mLastPos);
                    }
                }
                if (mBubbleBarSwipeController != null) {
                    mBubbleBarSwipeController.swipeTo(dY);
                    if (!mPilfered && mBubbleBarSwipeController.isSwipeGesture()) {
                        Log.d(TAG, "ACTION_MOVE swipe gesture, pilfering");
                        mPilfered = true;
                        // Bubbles is handling the swipe so make sure no one else gets it.
                        TestLogging.recordEvent(TestProtocol.SEQUENCE_PILFER, "pilferPointers");
                        mInputMonitorCompat.pilferPointers();
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                long tapTime = System.currentTimeMillis() - mDownTime;
                boolean swipeUpOnBubbleHandle = mBubbleBarSwipeController != null
                        && mBubbleBarSwipeController.isSwipeGesture();
                // Anything less than a long-press is a tap
                boolean isWithinTapTime = tapTime <= mTimeForLongPress;
                Log.d(TAG, "ACTION_UP swipeUp=" + swipeUpOnBubbleHandle + " isInTapTime="
                        + isWithinTapTime + " tapTime=" + tapTime + " passedTouchSlop="
                        + mPassedTouchSlop + " stashedOrCollapsedOnDown="
                        + mStashedOrCollapsedOnDown);
                if (isWithinTapTime && !swipeUpOnBubbleHandle && !mPassedTouchSlop
                        && mStashedOrCollapsedOnDown) {
                    Log.d(TAG, "ACTION_UP showing bubble bar");
                    // Taps on the handle / collapsed state should open the bar
                    mBubbleStashController.showBubbleBar(
                            /* expandBubbles= */ true, /* bubbleBarGesture= */ true);
                } else {
                    Log.d(TAG, "ACTION_UP nothing to do");
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                Log.d(TAG, "ACTION_CANCEL");
                break;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            cleanupAfterMotionEvent();
        }
    }

    private void cleanupAfterMotionEvent() {
        Log.d(TAG, "cleaning up passedSlop=" + mPassedTouchSlop + " pilfered=" + mPilfered);
        if (mBubbleBarSwipeController != null) {
            mBubbleBarSwipeController.finish();
        }
        mPassedTouchSlop = false;
        mPilfered = false;
        mDownTime = 0;
    }

    private boolean isCollapsed() {
        return mBubbleStashController.isBubbleBarVisible()
                && !mBubbleBarViewController.isExpanded();
    }

    /**
     * Returns whether the event is occurring on a visible bubble bar or the bar handle.
     */
    public static boolean isEventOnBubbles(TaskbarActivityContext tac, MotionEvent ev) {
        if (tac == null || !tac.isBubbleBarEnabled()) {
            return false;
        }
        BubbleControllers controllers = tac.getBubbleControllers();
        if (controllers == null || !controllers.bubbleBarViewController.hasBubbles()) {
            return false;
        }
        if (controllers.bubbleStashController.isStashed()
                && controllers.bubbleStashedHandleViewController.isPresent()) {
            return controllers.bubbleStashedHandleViewController.get().isEventOverHandle(ev);
        } else if (controllers.bubbleBarViewController.isBubbleBarVisible()) {
            return controllers.bubbleBarViewController.isEventOverBubbleBar(ev);
        }
        return false;
    }
}

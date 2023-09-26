/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.launcher3.MotionEventsUtils.isTrackpadMotionEvent;
import static com.android.launcher3.Utilities.squaredHypot;
import static com.android.launcher3.config.FeatureFlags.ENABLE_CURSOR_HOVER_STATES;
import static com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_TOUCHING;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PointF;
import android.graphics.Rect;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.InputDevice;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarThresholdUtils;
import com.android.launcher3.taskbar.TaskbarTranslationController.TransitionCallback;
import com.android.launcher3.taskbar.bubbles.BubbleControllers;
import com.android.launcher3.touch.OverScroll;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.OverviewCommandHelper;
import com.android.systemui.shared.system.InputMonitorCompat;

/**
 * Listens for touch and hover events to unstash the Taskbar.
 *
 * <p>Cancels the current gesture if the long press causes the Taskbar to be unstashed.
 */
public class TaskbarUnstashInputConsumer extends DelegateInputConsumer {

    private final TaskbarActivityContext mTaskbarActivityContext;
    private final OverviewCommandHelper mOverviewCommandHelper;
    private final GestureDetector mLongPressDetector;
    private final float mSquaredTouchSlop;

    private float mLongPressDownX, mLongPressDownY;
    private boolean mCanceledUnstashHint;
    private final float mUnstashArea;
    private final float mScreenWidth;

    private final int mTaskbarNavThreshold;
    private final int mTaskbarNavThresholdY;
    private final boolean mIsTaskbarAllAppsOpen;
    private boolean mHasPassedTaskbarNavThreshold;
    private boolean mIsInBubbleBarArea;

    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    private int mActivePointerId = INVALID_POINTER_ID;

    private final boolean mIsTransientTaskbar;

    private boolean mIsStashedTaskbarHovered = false;
    private final Rect mStashedTaskbarHandleBounds = new Rect();
    private final Rect mBottomEdgeBounds = new Rect();
    private final int mBottomScreenEdge;
    private final int mStashedTaskbarBottomEdge;

    private final @Nullable TransitionCallback mTransitionCallback;

    public TaskbarUnstashInputConsumer(Context context, InputConsumer delegate,
            InputMonitorCompat inputMonitor, TaskbarActivityContext taskbarActivityContext,
            OverviewCommandHelper overviewCommandHelper) {
        super(delegate, inputMonitor);
        mTaskbarActivityContext = taskbarActivityContext;
        mOverviewCommandHelper = overviewCommandHelper;
        // TODO(b/270395798): remove this when cleaning up old Persistent Taskbar code.
        mSquaredTouchSlop = Utilities.squaredTouchSlop(context);
        mScreenWidth = taskbarActivityContext.getDeviceProfile().widthPx;

        Resources res = context.getResources();
        mUnstashArea = res.getDimensionPixelSize(R.dimen.taskbar_unstash_input_area);
        mTaskbarNavThreshold = TaskbarThresholdUtils.getFromNavThreshold(res,
                taskbarActivityContext.getDeviceProfile());
        mTaskbarNavThresholdY = taskbarActivityContext.getDeviceProfile().heightPx
                - mTaskbarNavThreshold;
        mIsTaskbarAllAppsOpen =
                mTaskbarActivityContext != null && mTaskbarActivityContext.isTaskbarAllAppsOpen();

        mIsTransientTaskbar = DisplayController.isTransientTaskbar(context);

        mLongPressDetector = new GestureDetector(context, new SimpleOnGestureListener() {
            @Override
            public void onLongPress(MotionEvent motionEvent) {
                onLongPressDetected(motionEvent);
            }
        });

        mBottomScreenEdge = res.getDimensionPixelSize(
                R.dimen.taskbar_stashed_screen_edge_hover_deadzone_height);
        mStashedTaskbarBottomEdge =
                res.getDimensionPixelSize(R.dimen.taskbar_stashed_below_hover_deadzone_height);

        mTransitionCallback = mIsTransientTaskbar
                ? taskbarActivityContext.getTranslationCallbacks()
                : null;
    }

    @Override
    public int getType() {
        return TYPE_TASKBAR_STASH | TYPE_CURSOR_HOVER | mDelegate.getType();
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        mLongPressDetector.onTouchEvent(ev);
        if (mState != STATE_ACTIVE) {
            boolean isStashedTaskbarHovered = isMouseEvent(ev)
                    && isStashedTaskbarHovered((int) ev.getX(), (int) ev.getY());
            if (!isStashedTaskbarHovered) {
                mDelegate.onMotionEvent(ev);
            }

            // Only show the transient task bar if the touch events are on the screen.
            if (mTaskbarActivityContext != null && !isTrackpadMotionEvent(ev)) {
                final float x = ev.getRawX();
                final float y = ev.getRawY();
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mActivePointerId = ev.getPointerId(0);
                        mDownPos.set(ev.getX(), ev.getY());
                        mLastPos.set(mDownPos);

                        mHasPassedTaskbarNavThreshold = false;
                        mTaskbarActivityContext.setAutohideSuspendFlag(
                                FLAG_AUTOHIDE_SUSPEND_TOUCHING, true);
                        if (isInTaskbarArea(x)) {
                            if (!mIsTransientTaskbar) {
                                mLongPressDownX = x;
                                mLongPressDownY = y;
                                mTaskbarActivityContext.startTaskbarUnstashHint(
                                        /* animateForward = */ true);
                                mCanceledUnstashHint = false;
                            }
                        }
                        if (mTransitionCallback != null && !mIsTaskbarAllAppsOpen) {
                            mTransitionCallback.onActionDown();
                        }
                        if (mIsTransientTaskbar && isInBubbleBarArea(x)) {
                            mIsInBubbleBarArea = true;
                        }
                        break;
                    case MotionEvent.ACTION_POINTER_UP:
                        int ptrIdx = ev.getActionIndex();
                        int ptrId = ev.getPointerId(ptrIdx);
                        if (ptrId == mActivePointerId) {
                            final int newPointerIdx = ptrIdx == 0 ? 1 : 0;
                            mDownPos.set(
                                    ev.getX(newPointerIdx) - (mLastPos.x - mDownPos.x),
                                    ev.getY(newPointerIdx) - (mLastPos.y - mDownPos.y));
                            mLastPos.set(ev.getX(newPointerIdx), ev.getY(newPointerIdx));
                            mActivePointerId = ev.getPointerId(newPointerIdx);
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (!mIsTransientTaskbar
                                && !mCanceledUnstashHint
                                && squaredHypot(mLongPressDownX - x, mLongPressDownY - y)
                                > mSquaredTouchSlop) {
                            mTaskbarActivityContext.startTaskbarUnstashHint(
                                    /* animateForward = */ false);
                            mCanceledUnstashHint = true;
                        }

                        int pointerIndex = ev.findPointerIndex(mActivePointerId);
                        if (pointerIndex == INVALID_POINTER_ID) {
                            break;
                        }
                        mLastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex));

                        if (mIsTransientTaskbar) {
                            float dY = mLastPos.y - mDownPos.y;
                            boolean passedTaskbarNavThreshold = dY < 0
                                    && Math.abs(dY) >= mTaskbarNavThreshold;

                            if (!mHasPassedTaskbarNavThreshold && passedTaskbarNavThreshold) {
                                mHasPassedTaskbarNavThreshold = true;
                                if (mIsInBubbleBarArea) {
                                    mTaskbarActivityContext.onSwipeToOpenBubblebar();
                                } else {
                                    mTaskbarActivityContext.onSwipeToUnstashTaskbar();
                                }
                            }

                            if (dY < 0) {
                                dY = -OverScroll.dampedScroll(-dY, mTaskbarNavThresholdY);
                                if (mTransitionCallback != null && !mIsTaskbarAllAppsOpen) {
                                    mTransitionCallback.onActionMove(dY);
                                }
                            }
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!mIsTransientTaskbar && !mCanceledUnstashHint) {
                            mTaskbarActivityContext.startTaskbarUnstashHint(
                                    /* animateForward = */ false);
                        }
                        mTaskbarActivityContext.setAutohideSuspendFlag(
                                FLAG_AUTOHIDE_SUSPEND_TOUCHING, false);
                        if (mTransitionCallback != null) {
                            mTransitionCallback.onActionEnd();
                        }
                        mHasPassedTaskbarNavThreshold = false;
                        mIsInBubbleBarArea = false;
                        break;
                    case MotionEvent.ACTION_BUTTON_RELEASE:
                        if (isStashedTaskbarHovered) {
                            mOverviewCommandHelper.addCommand(OverviewCommandHelper.TYPE_HOME);
                        }
                        break;
                }
            }
        }
    }

    private boolean isInTaskbarArea(float x) {
        float areaFromMiddle = mUnstashArea / 2.0f;
        float distFromMiddle = Math.abs(mScreenWidth / 2.0f - x);
        return distFromMiddle < areaFromMiddle;
    }

    private boolean isInBubbleBarArea(float x) {
        if (mTaskbarActivityContext != null && mIsTransientTaskbar) {
            BubbleControllers controllers = mTaskbarActivityContext.getBubbleControllers();
            if (controllers == null) return false;
            Rect bubbleBarBounds = controllers.bubbleBarViewController.getBubbleBarBounds();
            return x >= bubbleBarBounds.left && x <= bubbleBarBounds.right;
        }
        return false;
    }

    private void onLongPressDetected(MotionEvent motionEvent) {
        if (mTaskbarActivityContext != null
                && isInTaskbarArea(motionEvent.getRawX())
                && !mIsTransientTaskbar) {
            boolean taskBarPressed = mTaskbarActivityContext.onLongPressToUnstashTaskbar();
            if (taskBarPressed) {
                setActive(motionEvent);
            }
        }
    }

    /**
     * Listen for hover events for the stashed taskbar.
     *
     * <p>When hovered over the stashed taskbar handle, show the unstash hint.
     * <p>When the cursor is touching the bottom edge below the stashed taskbar, unstash it.
     * <p>When the cursor is within a defined threshold of the screen's bottom edge outside of
     * the stashed taskbar, unstash it.
     */
    @Override
    public void onHoverEvent(MotionEvent ev) {
        if (!ENABLE_CURSOR_HOVER_STATES.get() || mTaskbarActivityContext == null
                || !mTaskbarActivityContext.isTaskbarStashed()) {
            return;
        }

        if (mIsStashedTaskbarHovered) {
            updateHoveredTaskbarState((int) ev.getX(), (int) ev.getY());
        } else {
            updateUnhoveredTaskbarState((int) ev.getX(), (int) ev.getY());
        }
    }

    private void updateHoveredTaskbarState(int x, int y) {
        DeviceProfile dp = mTaskbarActivityContext.getDeviceProfile();
        mBottomEdgeBounds.set(
                (dp.widthPx - (int) mUnstashArea) / 2,
                dp.heightPx - mStashedTaskbarBottomEdge,
                (int) (((dp.widthPx - mUnstashArea) / 2) + mUnstashArea),
                dp.heightPx);

        if (mBottomEdgeBounds.contains(x, y)) {
            // If hovering stashed taskbar and then hover screen bottom edge, unstash it.
            mTaskbarActivityContext.onSwipeToUnstashTaskbar();
            mIsStashedTaskbarHovered = false;
        } else if (!isStashedTaskbarHovered(x, y)) {
            // If exit hovering stashed taskbar, remove hint.
            startStashedTaskbarHover(/* isHovered = */ false);
        }
    }

    private void updateUnhoveredTaskbarState(int x, int y) {
        DeviceProfile dp = mTaskbarActivityContext.getDeviceProfile();
        mBottomEdgeBounds.set(
                0,
                dp.heightPx - mBottomScreenEdge,
                dp.widthPx,
                dp.heightPx);

        if (isStashedTaskbarHovered(x, y)) {
            // If enter hovering stashed taskbar, start hint.
            startStashedTaskbarHover(/* isHovered = */ true);
        } else if (mBottomEdgeBounds.contains(x, y)) {
            // If hover screen's bottom edge not below the stashed taskbar, unstash it.
            mTaskbarActivityContext.onSwipeToUnstashTaskbar();
        }
    }

    private void startStashedTaskbarHover(boolean isHovered) {
        mTaskbarActivityContext.startTaskbarUnstashHint(isHovered, /* forceUnstash = */ true);
        mIsStashedTaskbarHovered = isHovered;
    }

    private boolean isStashedTaskbarHovered(int x, int y) {
        if (!mTaskbarActivityContext.isTaskbarStashed()
                || mTaskbarActivityContext.isTaskbarAllAppsOpen()
                || !ENABLE_CURSOR_HOVER_STATES.get()) {
            return false;
        }
        DeviceProfile dp = mTaskbarActivityContext.getDeviceProfile();
        mStashedTaskbarHandleBounds.set(
                (dp.widthPx - (int) mUnstashArea) / 2,
                dp.heightPx - dp.stashedTaskbarHeight,
                (int) (((dp.widthPx - mUnstashArea) / 2) + mUnstashArea),
                dp.heightPx);
        return mStashedTaskbarHandleBounds.contains(x, y);
    }

    private boolean isMouseEvent(MotionEvent event) {
        return event.getSource() == InputDevice.SOURCE_MOUSE;
    }

    @Override
    protected String getDelegatorName() {
        return "TaskbarUnstashInputConsumer";
    }
}

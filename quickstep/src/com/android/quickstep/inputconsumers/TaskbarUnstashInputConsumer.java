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

import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.INVALID_POINTER_ID;
import static android.view.RoundedCorner.POSITION_BOTTOM_LEFT;
import static android.view.RoundedCorner.POSITION_BOTTOM_RIGHT;

import static com.android.launcher3.Flags.enableCursorHoverStates;
import static com.android.launcher3.Flags.enableScalingRevealHomeAnimation;
import static com.android.launcher3.MotionEventsUtils.isTrackpadMotionEvent;
import static com.android.launcher3.taskbar.TaskbarAutohideSuspendController.FLAG_AUTOHIDE_SUSPEND_TOUCHING;
import static com.android.systemui.shared.Flags.cursorHotCorner;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.RoundedCorner;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarThresholdUtils;
import com.android.launcher3.taskbar.TaskbarTranslationController.TransitionCallback;
import com.android.launcher3.touch.OverScroll;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.GestureState;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.OverviewCommandHelper;
import com.android.quickstep.OverviewCommandHelper.CommandType;
import com.android.systemui.shared.system.InputMonitorCompat;

/**
 * Listens for touch (swipe) and hover events to unstash the Taskbar.
 */
public class TaskbarUnstashInputConsumer extends DelegateInputConsumer {

    private static final int HOVER_TASKBAR_UNSTASH_TIMEOUT = 500;

    private static final int NUM_MOTION_MOVE_THRESHOLD = 3;

    private static final Handler sUnstashHandler = new Handler(Looper.getMainLooper());

    private final TaskbarActivityContext mTaskbarActivityContext;
    private final OverviewCommandHelper mOverviewCommandHelper;
    private final DisplayManager mDisplayManager;
    private final float mUnstashArea;
    private final int mActionCornerPadding;
    private final int mTaskbarNavThreshold;
    private final int mTaskbarNavThresholdY;
    private final boolean mIsTaskbarAllAppsOpen;
    private boolean mHasPassedTaskbarNavThreshold;
    private final int mTouchSlop;

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
    private final GestureState mGestureState;
    private VelocityTracker mVelocityTracker;
    private boolean mCanPlayTaskbarBgAlphaAnimation = true;
    private int mMotionMoveCount = 0;
    // Velocity defined as dp per s
    private float mTaskbarSlowVelocityYThreshold;

    public TaskbarUnstashInputConsumer(
            Context context,
            InputConsumer delegate,
            InputMonitorCompat inputMonitor,
            TaskbarActivityContext taskbarActivityContext,
            OverviewCommandHelper overviewCommandHelper,
            GestureState gestureState) {
        super(gestureState.getDisplayId(), delegate, inputMonitor);
        mTaskbarActivityContext = taskbarActivityContext;
        mIsTransientTaskbar = DisplayController.isTransientTaskbar(context);
        mOverviewCommandHelper = overviewCommandHelper;
        mDisplayManager = context.getSystemService(DisplayManager.class);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        Resources res = context.getResources();
        mUnstashArea = res.getDimensionPixelSize(R.dimen.taskbar_unstash_input_area);
        mActionCornerPadding = res.getDimensionPixelSize(
                R.dimen.transient_taskbar_action_corner_padding);

        boolean pinnedTaskbarWithAutoStashing =
                mTaskbarActivityContext.shouldAllowTaskbarToAutoStash() && !mIsTransientTaskbar;

        mTaskbarNavThreshold =
                pinnedTaskbarWithAutoStashing ? 0 : TaskbarThresholdUtils.getFromNavThreshold(res,
                        taskbarActivityContext.getDeviceProfile());

        mTaskbarNavThresholdY = taskbarActivityContext.getDeviceProfile().getDeviceProperties().getHeightPx()
                - mTaskbarNavThreshold;
        mIsTaskbarAllAppsOpen = mTaskbarActivityContext.isTaskbarAllAppsOpen();

        mTaskbarSlowVelocityYThreshold =
                res.getDimensionPixelSize(R.dimen.taskbar_slow_velocity_y_threshold);

        mBottomScreenEdge = res.getDimensionPixelSize(
                R.dimen.taskbar_stashed_screen_edge_hover_deadzone_height);
        mStashedTaskbarBottomEdge =
                res.getDimensionPixelSize(R.dimen.taskbar_stashed_below_hover_deadzone_height);

        mTransitionCallback = mIsTransientTaskbar
                ? taskbarActivityContext.getTranslationCallbacks()
                : null;
        mGestureState = gestureState;
    }

    @Override
    public int getType() {
        return TYPE_TASKBAR_STASH | TYPE_CURSOR_HOVER | mDelegate.getType();
    }

    @Override
    public boolean allowInterceptByParent() {
        return super.allowInterceptByParent() && !mHasPassedTaskbarNavThreshold;
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        if (enableScalingRevealHomeAnimation() && mIsTransientTaskbar) {
            checkVelocityForTaskbarBackground(ev);
        }
        if (mState != STATE_ACTIVE) {
            boolean isStashedTaskbarHovered = isMouseEvent(ev)
                    && isStashedTaskbarHovered((int) ev.getX(), (int) ev.getY());
            // Only show the transient task bar if the touch events are on the screen.
            if (!isTrackpadMotionEvent(ev)) {
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
                        if (mTransitionCallback != null && !mIsTaskbarAllAppsOpen) {
                            mTransitionCallback.onActionDown();
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
                        int pointerIndex = ev.findPointerIndex(mActivePointerId);
                        if (pointerIndex == INVALID_POINTER_ID) {
                            break;
                        }
                        mLastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex));

                        float dX = mLastPos.x - mDownPos.x;
                        float dY = mLastPos.y - mDownPos.y;

                        if (mTaskbarActivityContext.shouldAllowTaskbarToAutoStash()) {
                            boolean passedTaskbarNavThreshold = dY < 0
                                    && Math.abs(dY) >= mTaskbarNavThreshold;

                            // we only care about nav thresholds when we are transient taskbar
                            if (!mHasPassedTaskbarNavThreshold && passedTaskbarNavThreshold
                                    && !mGestureState.isInExtendedSlopRegion()) {
                                mHasPassedTaskbarNavThreshold = true;
                                mTaskbarActivityContext.onSwipeToUnstashTaskbar(true);
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
                        cleanupAfterMotionEvent();
                        break;
                    case MotionEvent.ACTION_BUTTON_RELEASE:
                        if (isStashedTaskbarHovered) {
                            mOverviewCommandHelper.addCommand(CommandType.HOME);
                        }
                        break;
                }
            }
            if (!isStashedTaskbarHovered) {
                mDelegate.onMotionEvent(ev);
            }
        }
    }

    private void checkVelocityForTaskbarBackground(MotionEvent ev) {
        int actionMasked = ev.getActionMasked();
        if (actionMasked == MotionEvent.ACTION_DOWN && mVelocityTracker != null) {
            mVelocityTracker.clear();
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(ev);

        mVelocityTracker.computeCurrentVelocity(1000);
        if (ev.getAction() == ACTION_MOVE) {
            mMotionMoveCount++;
        }

        float velocityYPxPerS = mVelocityTracker.getYVelocity();
        float dY = Math.abs(mLastPos.y - mDownPos.y);
        if (mCanPlayTaskbarBgAlphaAnimation
                && mMotionMoveCount >= NUM_MOTION_MOVE_THRESHOLD // Arbitrary value
                && velocityYPxPerS != 0 // Ignore these
                && velocityYPxPerS >= mTaskbarSlowVelocityYThreshold
                && dY != 0
                && dY > mTouchSlop) {
            mTaskbarActivityContext.playTaskbarBackgroundAlphaAnimation();
            mCanPlayTaskbarBgAlphaAnimation = false;
        }
    }

    private void cleanupAfterMotionEvent() {
        mTaskbarActivityContext.setAutohideSuspendFlag(
                FLAG_AUTOHIDE_SUSPEND_TOUCHING, false);
        if (mTransitionCallback != null) {
            mTransitionCallback.onActionEnd();
        }
        mHasPassedTaskbarNavThreshold = false;

        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
        }
        mVelocityTracker = null;
        mCanPlayTaskbarBgAlphaAnimation = true;
        mMotionMoveCount = 0;
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
        if (!enableCursorHoverStates() || mTaskbarActivityContext == null
                || !mTaskbarActivityContext.isTaskbarStashed()) {
            return;
        }

        if (mIsStashedTaskbarHovered) {
            updateHoveredTaskbarState((int) ev.getX(), (int) ev.getY());
        } else {
            updateUnhoveredTaskbarState((int) ev.getX(), (int) ev.getY(), ev.getDisplayId());
        }
    }

    private void updateHoveredTaskbarState(int x, int y) {
        DeviceProfile dp = mTaskbarActivityContext.getDeviceProfile();
        mBottomEdgeBounds.set(
                (dp.getDeviceProperties().getWidthPx() - (int) mUnstashArea) / 2,
                dp.getDeviceProperties().getHeightPx() - mStashedTaskbarBottomEdge,
                (int) (((dp.getDeviceProperties().getWidthPx() - mUnstashArea) / 2) + mUnstashArea),
                dp.getDeviceProperties().getHeightPx());

        if (mBottomEdgeBounds.contains(x, y)) {
            // start a single unstash timeout if hovering bottom edge under the hinted taskbar.
            if (!sUnstashHandler.hasMessagesOrCallbacks()) {
                sUnstashHandler.postDelayed(() -> {
                    mTaskbarActivityContext.onSwipeToUnstashTaskbar(false);
                    mIsStashedTaskbarHovered = false;
                }, HOVER_TASKBAR_UNSTASH_TIMEOUT);
            }
        } else if (!isStashedTaskbarHovered(x, y)) {
            // If exit hovering stashed taskbar, remove hint and clear pending unstash calls.
            sUnstashHandler.removeCallbacksAndMessages(null);
            startStashedTaskbarHover(/* isHovered = */ false);
        } else {
            sUnstashHandler.removeCallbacksAndMessages(null);
        }
    }

    private void updateUnhoveredTaskbarState(int x, int y, int displayId) {
        sUnstashHandler.removeCallbacksAndMessages(null);

        DeviceProfile dp = mTaskbarActivityContext.getDeviceProfile();
        mBottomEdgeBounds.set(
                0,
                dp.getDeviceProperties().getHeightPx() - mBottomScreenEdge,
                dp.getDeviceProperties().getWidthPx(),
                dp.getDeviceProperties().getHeightPx());

        if (cursorHotCorner() && mDisplayManager != null) {
            Display display = mDisplayManager.getDisplay(displayId);
            if (display != null) {
                RoundedCorner leftBottomCorner = display.getRoundedCorner(POSITION_BOTTOM_LEFT);
                int leftCornerRadius =
                        leftBottomCorner == null ? 0 : leftBottomCorner.getRadius();
                RoundedCorner rightBottomCorner = display.getRoundedCorner(
                        POSITION_BOTTOM_RIGHT);
                int rightCornerRadius =
                        rightBottomCorner == null ? 0 : rightBottomCorner.getRadius();
                mBottomEdgeBounds.inset(leftCornerRadius + mActionCornerPadding, 0,
                        rightCornerRadius + mActionCornerPadding, 0);
            }
        }

        if (isStashedTaskbarHovered(x, y)) {
            // If enter hovering stashed taskbar, start hint.
            startStashedTaskbarHover(/* isHovered = */ true);
        } else if (mBottomEdgeBounds.contains(x, y)) {
            // If hover screen's bottom edge not below the stashed taskbar, unstash it.
            mTaskbarActivityContext.onSwipeToUnstashTaskbar(false);
        }
    }

    private void startStashedTaskbarHover(boolean isHovered) {
        mTaskbarActivityContext.startTaskbarUnstashHint(isHovered);
        mIsStashedTaskbarHovered = isHovered;
    }

    private boolean isStashedTaskbarHovered(int x, int y) {
        if (!mTaskbarActivityContext.isTaskbarStashed()
                || mTaskbarActivityContext.isTaskbarAllAppsOpen()
                || !enableCursorHoverStates()) {
            return false;
        }
        DeviceProfile dp = mTaskbarActivityContext.getDeviceProfile();
        mStashedTaskbarHandleBounds.set(
                (dp.getDeviceProperties().getWidthPx() - (int) mUnstashArea) / 2,
                dp.getDeviceProperties().getHeightPx()
                        - dp.getTaskbarProfile().getStashedTaskbarHeight(),
                (int) (((dp.getDeviceProperties().getWidthPx() - mUnstashArea) / 2) + mUnstashArea),
                dp.getDeviceProperties().getHeightPx());
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

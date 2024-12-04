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

import static com.android.internal.policy.TaskResizingAlgorithm.CTRL_NONE;

import android.annotation.Nullable;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Looper;
import android.view.BatchedInputEventReceiver;
import android.view.Choreographer;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputMonitor;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.annotation.VisibleForTesting;

import com.android.wm.shell.R;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipPerfHintController;
import com.android.wm.shell.common.pip.PipPinchResizingAlgorithm;
import com.android.wm.shell.common.pip.PipUiEventLogger;
import com.android.wm.shell.pip.PipAnimationController;
import com.android.wm.shell.pip.PipTaskOrganizer;

import java.io.PrintWriter;
import java.util.function.Consumer;

/**
 * Helper on top of PipTouchHandler that handles inputs OUTSIDE of the PIP window, which is used to
 * trigger dynamic resize.
 */
public class PipResizeGestureHandler {

    private static final String TAG = "PipResizeGestureHandler";
    private static final int PINCH_RESIZE_SNAP_DURATION = 250;
    private static final float PINCH_RESIZE_AUTO_MAX_RATIO = 0.9f;

    private final Context mContext;
    private final PipBoundsAlgorithm mPipBoundsAlgorithm;
    private final PipMotionHelper mMotionHelper;
    private final PipBoundsState mPipBoundsState;
    private final PipTouchState mPipTouchState;
    private final PipTaskOrganizer mPipTaskOrganizer;
    private final PhonePipMenuController mPhonePipMenuController;
    private final PipDismissTargetHandler mPipDismissTargetHandler;
    private final PipUiEventLogger mPipUiEventLogger;
    private final PipPinchResizingAlgorithm mPinchResizingAlgorithm;
    private final int mDisplayId;
    private final ShellExecutor mMainExecutor;

    private final PointF mDownPoint = new PointF();
    private final PointF mDownSecondPoint = new PointF();
    private final PointF mLastPoint = new PointF();
    private final PointF mLastSecondPoint = new PointF();
    private final Point mMaxSize = new Point();
    private final Point mMinSize = new Point();
    private final Rect mLastResizeBounds = new Rect();
    private final Rect mUserResizeBounds = new Rect();
    private final Rect mDownBounds = new Rect();
    private final Runnable mUpdateMovementBoundsRunnable;
    private final Consumer<Rect> mUpdateResizeBoundsCallback;

    private float mTouchSlop;

    private boolean mAllowGesture;
    private boolean mIsAttached;
    private boolean mIsEnabled;
    private boolean mEnablePinchResize;
    private boolean mIsSysUiStateValid;
    private boolean mThresholdCrossed;
    private boolean mOngoingPinchToResize = false;
    private float mAngle = 0;
    int mFirstIndex = -1;
    int mSecondIndex = -1;

    private InputMonitor mInputMonitor;
    private InputEventReceiver mInputEventReceiver;

    @Nullable
    private final PipPerfHintController mPipPerfHintController;

    @Nullable
    private PipPerfHintController.PipHighPerfSession mPipHighPerfSession;

    private int mCtrlType;
    private int mOhmOffset;

    public PipResizeGestureHandler(Context context, PipBoundsAlgorithm pipBoundsAlgorithm,
            PipBoundsState pipBoundsState, PipMotionHelper motionHelper,
            PipTouchState pipTouchState, PipTaskOrganizer pipTaskOrganizer,
            PipDismissTargetHandler pipDismissTargetHandler,
            Runnable updateMovementBoundsRunnable,
            PipUiEventLogger pipUiEventLogger, PhonePipMenuController menuActivityController,
            ShellExecutor mainExecutor, @Nullable PipPerfHintController pipPerfHintController) {
        mContext = context;
        mDisplayId = context.getDisplayId();
        mMainExecutor = mainExecutor;
        mPipPerfHintController = pipPerfHintController;
        mPipBoundsAlgorithm = pipBoundsAlgorithm;
        mPipBoundsState = pipBoundsState;
        mMotionHelper = motionHelper;
        mPipTouchState = pipTouchState;
        mPipTaskOrganizer = pipTaskOrganizer;
        mPipDismissTargetHandler = pipDismissTargetHandler;
        mUpdateMovementBoundsRunnable = updateMovementBoundsRunnable;
        mPhonePipMenuController = menuActivityController;
        mPipUiEventLogger = pipUiEventLogger;
        mPinchResizingAlgorithm = new PipPinchResizingAlgorithm();

        mUpdateResizeBoundsCallback = (rect) -> {
            mUserResizeBounds.set(rect);
            mMotionHelper.synchronizePinnedStackBounds();
            mUpdateMovementBoundsRunnable.run();
            resetState();
        };
    }

    public void init() {
        mContext.getDisplay().getRealSize(mMaxSize);
        reloadResources();

        final Resources res = mContext.getResources();
        mEnablePinchResize = res.getBoolean(R.bool.config_pipEnablePinchResize);
    }

    public void onConfigurationChanged() {
        reloadResources();
    }

    /**
     * Called when SysUI state changed.
     *
     * @param isSysUiStateValid Is SysUI valid or not.
     */
    public void onSystemUiStateChanged(boolean isSysUiStateValid) {
        mIsSysUiStateValid = isSysUiStateValid;
    }

    private void reloadResources() {
        mTouchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();
    }

    private void disposeInputChannel() {
        if (mInputEventReceiver != null) {
            mInputEventReceiver.dispose();
            mInputEventReceiver = null;
        }
        if (mInputMonitor != null) {
            mInputMonitor.dispose();
            mInputMonitor = null;
        }
    }

    void onActivityPinned() {
        mIsAttached = true;
        updateIsEnabled();
    }

    void onActivityUnpinned() {
        mIsAttached = false;
        mUserResizeBounds.setEmpty();
        updateIsEnabled();
    }

    private void updateIsEnabled() {
        boolean isEnabled = mIsAttached;
        if (isEnabled == mIsEnabled) {
            return;
        }
        mIsEnabled = isEnabled;
        disposeInputChannel();

        if (mIsEnabled) {
            // Register input event receiver
            mInputMonitor = mContext.getSystemService(InputManager.class).monitorGestureInput(
                    "pip-resize", mDisplayId);
            try {
                mMainExecutor.executeBlocking(() -> {
                    mInputEventReceiver = new PipResizeInputEventReceiver(
                            mInputMonitor.getInputChannel(), Looper.myLooper());
                });
            } catch (InterruptedException e) {
                throw new RuntimeException("Failed to create input event receiver", e);
            }
        }
    }

    @VisibleForTesting
    void onInputEvent(InputEvent ev) {
        if (!mEnablePinchResize) {
            // No need to handle anything if neither form of resizing is enabled.
            return;
        }

        if (!mPipTouchState.getAllowInputEvents()) {
            // No need to handle anything if touches are not enabled
            return;
        }

        // Don't allow resize when PiP is stashed.
        if (mPipBoundsState.isStashed()) {
            return;
        }

        if (ev instanceof MotionEvent) {
            MotionEvent mv = (MotionEvent) ev;
            int action = mv.getActionMasked();
            final Rect pipBounds = mPipBoundsState.getBounds();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (!pipBounds.contains((int) mv.getRawX(), (int) mv.getRawY())
                        && mPhonePipMenuController.isMenuVisible()) {
                    mPhonePipMenuController.hideMenu();
                }
            }

            if (mEnablePinchResize && mOngoingPinchToResize) {
                onPinchResize(mv);
            }
        }
    }

    /**
     * Checks if there is currently an on-going gesture, either drag-resize or pinch-resize.
     */
    public boolean hasOngoingGesture() {
        return mCtrlType != CTRL_NONE || mOngoingPinchToResize;
    }

    public boolean isUsingPinchToZoom() {
        return mEnablePinchResize;
    }

    public boolean isResizing() {
        return mAllowGesture;
    }

    public boolean willStartResizeGesture(MotionEvent ev) {
        if (isInValidSysUiState()) {
            if (ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                if (mEnablePinchResize && ev.getPointerCount() == 2) {
                    onPinchResize(ev);
                    mOngoingPinchToResize = mAllowGesture;
                    return mAllowGesture;
                }
            }
        }
        return false;
    }

    private boolean isInValidSysUiState() {
        return mIsSysUiStateValid;
    }

    private void onHighPerfSessionTimeout(PipPerfHintController.PipHighPerfSession session) {}

    private void cleanUpHighPerfSessionMaybe() {
        if (mPipHighPerfSession != null) {
            // Close the high perf session once pointer interactions are over;
            mPipHighPerfSession.close();
            mPipHighPerfSession = null;
        }
    }

    @VisibleForTesting
    void onPinchResize(MotionEvent ev) {
        int action = ev.getActionMasked();

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            mFirstIndex = -1;
            mSecondIndex = -1;
            mAllowGesture = false;
            finishResize();
            cleanUpHighPerfSessionMaybe();
        }

        if (ev.getPointerCount() != 2) {
            return;
        }

        final Rect pipBounds = mPipBoundsState.getBounds();
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            if (mFirstIndex == -1 && mSecondIndex == -1
                    && pipBounds.contains((int) ev.getRawX(0), (int) ev.getRawY(0))
                    && pipBounds.contains((int) ev.getRawX(1), (int) ev.getRawY(1))) {
                mAllowGesture = true;
                mFirstIndex = 0;
                mSecondIndex = 1;
                mDownPoint.set(ev.getRawX(mFirstIndex), ev.getRawY(mFirstIndex));
                mDownSecondPoint.set(ev.getRawX(mSecondIndex), ev.getRawY(mSecondIndex));
                mDownBounds.set(pipBounds);

                mLastPoint.set(mDownPoint);
                mLastSecondPoint.set(mLastSecondPoint);
                mLastResizeBounds.set(mDownBounds);

                // start the high perf session as the second pointer gets detected
                if (mPipPerfHintController != null) {
                    mPipHighPerfSession = mPipPerfHintController.startSession(
                            this::onHighPerfSessionTimeout, "onPinchResize");
                }
            }
        }

        if (action == MotionEvent.ACTION_MOVE) {
            if (mFirstIndex == -1 || mSecondIndex == -1) {
                return;
            }

            float x0 = ev.getRawX(mFirstIndex);
            float y0 = ev.getRawY(mFirstIndex);
            float x1 = ev.getRawX(mSecondIndex);
            float y1 = ev.getRawY(mSecondIndex);
            mLastPoint.set(x0, y0);
            mLastSecondPoint.set(x1, y1);

            // Capture inputs
            if (!mThresholdCrossed
                    && (distanceBetween(mDownSecondPoint, mLastSecondPoint) > mTouchSlop
                            || distanceBetween(mDownPoint, mLastPoint) > mTouchSlop)) {
                pilferPointers();
                mThresholdCrossed = true;
                // Reset the down to begin resizing from this point
                mDownPoint.set(mLastPoint);
                mDownSecondPoint.set(mLastSecondPoint);

                if (mPhonePipMenuController.isMenuVisible()) {
                    mPhonePipMenuController.hideMenu();
                }
            }

            if (mThresholdCrossed) {
                mAngle = mPinchResizingAlgorithm.calculateBoundsAndAngle(mDownPoint,
                        mDownSecondPoint, mLastPoint, mLastSecondPoint, mMinSize, mMaxSize,
                        mDownBounds, mLastResizeBounds);

                mPipTaskOrganizer.scheduleUserResizePip(mDownBounds, mLastResizeBounds,
                        mAngle, null);
                mPipBoundsState.setHasUserResizedPip(true);
            }
        }
    }

    private void snapToMovementBoundsEdge(Rect bounds, Rect movementBounds) {
        final int leftEdge = bounds.left;


        final int fromLeft = Math.abs(leftEdge - movementBounds.left);
        final int fromRight = Math.abs(movementBounds.right - leftEdge);

        // The PIP will be snapped to either the right or left edge, so calculate which one
        // is closest to the current position.
        final int newLeft = fromLeft < fromRight
                ? movementBounds.left : movementBounds.right;

        bounds.offsetTo(newLeft, mLastResizeBounds.top);
    }

    /**
     * Resizes the pip window and updates user-resized bounds.
     *
     * @param bounds target bounds to resize to
     * @param snapFraction snap fraction to apply after resizing
     */
    void userResizeTo(Rect bounds, float snapFraction) {
        Rect finalBounds = new Rect(bounds);

        // get the current movement bounds
        final Rect movementBounds = mPipBoundsAlgorithm.getMovementBounds(finalBounds);

        // snap the target bounds to the either left or right edge, by choosing the closer one
        snapToMovementBoundsEdge(finalBounds, movementBounds);

        // apply the requested snap fraction onto the target bounds
        mPipBoundsAlgorithm.applySnapFraction(finalBounds, snapFraction);

        // resize from current bounds to target bounds without animation
        mPipTaskOrganizer.scheduleUserResizePip(mPipBoundsState.getBounds(), finalBounds, null);
        // set the flag that pip has been resized
        mPipBoundsState.setHasUserResizedPip(true);

        // finish the resize operation and update the state of the bounds
        mPipTaskOrganizer.scheduleFinishResizePip(finalBounds, mUpdateResizeBoundsCallback);
    }

    private void finishResize() {
        if (!mLastResizeBounds.isEmpty()) {
            // Pinch-to-resize needs to re-calculate snap fraction and animate to the snapped
            // position correctly. Drag-resize does not need to move, so just finalize resize.
            if (mOngoingPinchToResize) {
                final Rect startBounds = new Rect(mLastResizeBounds);
                // If user resize is pretty close to max size, just auto resize to max.
                if (mLastResizeBounds.width() >= PINCH_RESIZE_AUTO_MAX_RATIO * mMaxSize.x
                        || mLastResizeBounds.height() >= PINCH_RESIZE_AUTO_MAX_RATIO * mMaxSize.y) {
                    resizeRectAboutCenter(mLastResizeBounds, mMaxSize.x, mMaxSize.y);
                }

                // If user resize is smaller than min size, auto resize to min
                if (mLastResizeBounds.width() < mMinSize.x
                        || mLastResizeBounds.height() < mMinSize.y) {
                    resizeRectAboutCenter(mLastResizeBounds, mMinSize.x, mMinSize.y);
                }

                // get the current movement bounds
                final Rect movementBounds = mPipBoundsAlgorithm
                        .getMovementBounds(mLastResizeBounds);

                // snap mLastResizeBounds to the correct edge based on movement bounds
                snapToMovementBoundsEdge(mLastResizeBounds, movementBounds);

                final float snapFraction = mPipBoundsAlgorithm.getSnapFraction(
                        mLastResizeBounds, movementBounds);
                mPipBoundsAlgorithm.applySnapFraction(mLastResizeBounds, snapFraction);

                // disable any touch events beyond resizing too
                mPipTouchState.setAllowInputEvents(false);

                mPipTaskOrganizer.scheduleAnimateResizePip(startBounds, mLastResizeBounds,
                        PINCH_RESIZE_SNAP_DURATION, mAngle, mUpdateResizeBoundsCallback, () -> {
                            // enable touch events
                            mPipTouchState.setAllowInputEvents(true);
                        });
            } else {
                mPipTaskOrganizer.scheduleFinishResizePip(mLastResizeBounds,
                        PipAnimationController.TRANSITION_DIRECTION_USER_RESIZE,
                        mUpdateResizeBoundsCallback);
            }
            final float magnetRadiusPercent = (float) mLastResizeBounds.width() / mMinSize.x / 2.f;
            mPipDismissTargetHandler
                    .setMagneticFieldRadiusPercent(magnetRadiusPercent);
            mPipUiEventLogger.log(
                    PipUiEventLogger.PipUiEventEnum.PICTURE_IN_PICTURE_RESIZE);
        } else {
            resetState();
        }
    }

    private void resetState() {
        mCtrlType = CTRL_NONE;
        mAngle = 0;
        mOngoingPinchToResize = false;
        mAllowGesture = false;
        mThresholdCrossed = false;
    }

    void setUserResizeBounds(Rect bounds) {
        mUserResizeBounds.set(bounds);
    }

    void invalidateUserResizeBounds() {
        mUserResizeBounds.setEmpty();
    }

    Rect getUserResizeBounds() {
        return mUserResizeBounds;
    }

    @VisibleForTesting
    Rect getLastResizeBounds() {
        return mLastResizeBounds;
    }

    @VisibleForTesting
    void pilferPointers() {
        mInputMonitor.pilferPointers();
    }


    @VisibleForTesting public void updateMaxSize(int maxX, int maxY) {
        mMaxSize.set(maxX, maxY);
    }

    @VisibleForTesting public void updateMinSize(int minX, int minY) {
        mMinSize.set(minX, minY);
    }

    void setOhmOffset(int offset) {
        mOhmOffset = offset;
    }

    private float distanceBetween(PointF p1, PointF p2) {
        return (float) Math.hypot(p2.x - p1.x, p2.y - p1.y);
    }

    private void resizeRectAboutCenter(Rect rect, int w, int h) {
        int cx = rect.centerX();
        int cy = rect.centerY();
        int l = cx - w / 2;
        int r = l + w;
        int t = cy - h / 2;
        int b = t + h;
        rect.set(l, t, r, b);
    }

    public void dump(PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + TAG);
        pw.println(innerPrefix + "mAllowGesture=" + mAllowGesture);
        pw.println(innerPrefix + "mIsAttached=" + mIsAttached);
        pw.println(innerPrefix + "mIsEnabled=" + mIsEnabled);
        pw.println(innerPrefix + "mEnablePinchResize=" + mEnablePinchResize);
        pw.println(innerPrefix + "mThresholdCrossed=" + mThresholdCrossed);
        pw.println(innerPrefix + "mOhmOffset=" + mOhmOffset);
        pw.println(innerPrefix + "mMinSize=" + mMinSize);
        pw.println(innerPrefix + "mMaxSize=" + mMaxSize);
    }

    class PipResizeInputEventReceiver extends BatchedInputEventReceiver {
        PipResizeInputEventReceiver(InputChannel channel, Looper looper) {
            super(channel, looper, Choreographer.getInstance());
        }

        public void onInputEvent(InputEvent event) {
            PipResizeGestureHandler.this.onInputEvent(event);
            finishInputEvent(event, true);
        }
    }
}

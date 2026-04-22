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
package com.android.wm.shell.pip2.phone;

import static com.android.internal.policy.TaskResizingAlgorithm.CTRL_NONE;
import static com.android.wm.shell.pip2.phone.PipTransition.ANIMATING_BOUNDS_CHANGE_DURATION;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Looper;
import android.view.BatchedInputEventReceiver;
import android.view.Choreographer;
import android.view.InputChannel;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.InputMonitor;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.ViewConfiguration;

import androidx.annotation.VisibleForTesting;

import com.android.internal.util.Preconditions;
import com.android.wm.shell.R;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.pip.PipBoundsAlgorithm;
import com.android.wm.shell.common.pip.PipBoundsState;
import com.android.wm.shell.common.pip.PipDesktopState;
import com.android.wm.shell.common.pip.PipDisplayLayoutState;
import com.android.wm.shell.common.pip.PipPerfHintController;
import com.android.wm.shell.common.pip.PipUiEventLogger;
import com.android.wm.shell.pip2.PipSurfaceTransactionHelper;
import com.android.wm.shell.pip2.animation.PipResizeAnimator;

import java.io.PrintWriter;
import java.util.function.Function;

/**
 * Helper on top of PipTouchHandler that handles inputs OUTSIDE of the PIP window, which is used to
 * trigger dynamic resize.
 */
public class PipResizeGestureHandler implements
        PipTransitionState.PipTransitionStateChangedListener,
        PipDisplayLayoutState.DisplayIdListener {

    private static final String TAG = "PipResizeGestureHandler";
    private static final int PINCH_RESIZE_SNAP_DURATION = 250;
    private static final float PINCH_RESIZE_AUTO_MAX_RATIO = 0.9f;
    private static final String RESIZE_BOUNDS_CHANGE = "resize_bounds_change";

    private Context mContext;
    private final PipBoundsAlgorithm mPipBoundsAlgorithm;
    private final PipBoundsState mPipBoundsState;
    private final PipTouchState mPipTouchState;
    private final PipScheduler mPipScheduler;
    private final PipTransitionState mPipTransitionState;
    private final PhonePipMenuController mPhonePipMenuController;
    private final PipDisplayLayoutState mPipDisplayLayoutState;
    private final PipDesktopState mPipDesktopState;
    private final PipUiEventLogger mPipUiEventLogger;
    private final ShellExecutor mMainExecutor;

    private final PointF mDownPoint = new PointF();
    private final PointF mDownSecondPoint = new PointF();
    private final PointF mLastPoint = new PointF();
    private final PointF mLastSecondPoint = new PointF();
    private final Rect mLastResizeBounds = new Rect();
    private final Rect mUserResizeBounds = new Rect();
    private final Rect mDownBounds = new Rect();
    private final Rect mStartBoundsAfterRelease = new Rect();

    private float mTouchSlop;

    private boolean mAllowGesture;
    private boolean mIsAttached;
    private boolean mIsEnabled;
    private boolean mEnablePinchResize;
    private boolean mEnableDragCornerResize;
    private boolean mThresholdCrossed;
    private boolean mOngoingPinchToResize = false;
    private boolean mWaitingForBoundsChangeTransition = false;
    private float mAngle = 0;


    private InputMonitor mInputMonitor;
    private InputEventReceiver mInputEventReceiver;
    private PipDragToResizeHandler mPipDragToResizeHandler;
    private PipPinchToResizeHandler mPipPinchToResizeHandler;

    @Nullable
    private final PipPerfHintController mPipPerfHintController;

    @Nullable
    private PipPerfHintController.PipHighPerfSession mPipHighPerfSession;

    private int mCtrlType;
    private int mOhmOffset;

    private final @NonNull PipSurfaceTransactionHelper mSurfaceTransactionHelper;

    public PipResizeGestureHandler(Context context,
            @NonNull PipSurfaceTransactionHelper pipSurfaceTransactionHelper,
            PipBoundsAlgorithm pipBoundsAlgorithm,
            PipBoundsState pipBoundsState,
            PipTouchState pipTouchState,
            PipScheduler pipScheduler,
            PipTransitionState pipTransitionState,
            PipUiEventLogger pipUiEventLogger,
            PhonePipMenuController menuActivityController,
            Function<Rect, Rect> movementBoundsSupplier,
            PipDisplayLayoutState pipDisplayLayoutState,
            PipDesktopState pipDesktopState,
            ShellExecutor mainExecutor,
            @Nullable PipPerfHintController pipPerfHintController) {
        mContext = context;
        mSurfaceTransactionHelper = pipSurfaceTransactionHelper;
        mMainExecutor = mainExecutor;
        mPipPerfHintController = pipPerfHintController;
        mPipBoundsAlgorithm = pipBoundsAlgorithm;
        mPipBoundsState = pipBoundsState;
        mPipTouchState = pipTouchState;
        mPipScheduler = pipScheduler;

        mPipTransitionState = pipTransitionState;
        mPipTransitionState.addPipTransitionStateChangedListener(this);

        mPhonePipMenuController = menuActivityController;
        mPipDisplayLayoutState = pipDisplayLayoutState;
        mPipDisplayLayoutState.addDisplayIdListener(this);
        mPipDesktopState = pipDesktopState;
        mPipUiEventLogger = pipUiEventLogger;

        mPipDragToResizeHandler = new PipDragToResizeHandler(context, this, pipBoundsState,
                menuActivityController, pipBoundsAlgorithm, pipScheduler, movementBoundsSupplier);
        mPipPinchToResizeHandler = new PipPinchToResizeHandler(this, pipBoundsState,
                menuActivityController, pipScheduler);

    }

    void init() {
        reloadResources();

        final Resources res = mContext.getResources();
        mEnablePinchResize = res.getBoolean(R.bool.config_pipEnablePinchResize);
    }

    void onConfigurationChanged() {
        reloadResources();
    }

    private void reloadResources() {
        mPipDragToResizeHandler.reloadResources(mContext);
        mTouchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();
    }

    @Override
    public void onDisplayIdChanged(@NonNull Context context) {
        mContext = context;
        reloadResources();
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
        // Only enable drag-corner-to-resize if PiP was entered when Desktop Mode session is active.
        mEnableDragCornerResize = mPipDesktopState.isPipInDesktopMode();
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
                    "pip-resize", mPipDisplayLayoutState.getDisplayId());
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

    boolean getAllowGesture() {
        return mAllowGesture;
    }

    void setAllowGesture(boolean allowGesture) {
        mAllowGesture = allowGesture;
    }

    boolean getThresholdCrossed() {
        return mThresholdCrossed;
    }

    void setThresholdCrossed(boolean thresholdCrossed) {
        mThresholdCrossed = thresholdCrossed;
    }

    int getCtrlType() {
        return mCtrlType;
    }

    void setCtrlType(int ctrlType) {
        mCtrlType = ctrlType;
    }

    void setAngle(float angle) {
        mAngle = angle;
    }

    void startHighPerfSession() {
        if (mPipPerfHintController != null) {
            mPipHighPerfSession = mPipPerfHintController.startSession(
                    this::onHighPerfSessionTimeout, "onPinchResize");
        }
    }

    @VisibleForTesting
    void onInputEvent(InputEvent ev) {
        if (!mEnableDragCornerResize && !mEnablePinchResize) {
            // No need to handle anything if resizing isn't enabled.
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

            final Point minSize = mPipBoundsState.getMinSize();
            final Point maxSize = mPipBoundsState.getMaxSize();
            if (mOngoingPinchToResize) {
                mPipPinchToResizeHandler.onPinchResize(mv, mDownPoint, mDownSecondPoint,
                        mDownBounds, mLastPoint, mLastSecondPoint, mLastResizeBounds, mTouchSlop,
                        minSize, maxSize);
            } else if (mEnableDragCornerResize) {
                mPipDragToResizeHandler.onDragCornerResize(mv, mLastResizeBounds, mDownPoint,
                        mDownBounds, minSize, maxSize, mTouchSlop);
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

    boolean willStartResizeGesture(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (mEnableDragCornerResize && mPipDragToResizeHandler.isWithinDragResizeRegion(
                        (int) ev.getRawX(),
                        (int) ev.getRawY())) {
                    return true;
                }
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (mEnablePinchResize && ev.getPointerCount() == 2) {
                    mPipPinchToResizeHandler.onPinchResize(ev, mDownPoint, mDownSecondPoint,
                            mDownBounds, mLastPoint, mLastSecondPoint, mLastResizeBounds,
                            mTouchSlop, mPipBoundsState.getMinSize(), mPipBoundsState.getMaxSize());
                    mOngoingPinchToResize = mAllowGesture;
                    return mAllowGesture;
                }
                break;

            default:
                break;
        }
        return false;
    }

    private void onHighPerfSessionTimeout(PipPerfHintController.PipHighPerfSession session) {}

    private void cleanUpHighPerfSessionMaybe() {
        if (mPipHighPerfSession != null) {
            // Close the high perf session once pointer interactions are over;
            mPipHighPerfSession.close();
            mPipHighPerfSession = null;
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
        // mPipTaskOrganizer.scheduleUserResizePip(mPipBoundsState.getBounds(), finalBounds, null);
        // set the flag that pip has been resized
        mPipBoundsState.setHasUserResizedPip(true);

        // finish the resize operation and update the state of the bounds
        // mPipTaskOrganizer.scheduleFinishResizePip(finalBounds, mUpdateResizeBoundsCallback);
    }

    /** Handles additional resizing and state changes after gesture resizing is done. */
    void finishResize() {
        if (mLastResizeBounds.isEmpty()) {
            resetState();
            return;
        }

        // Cache initial bounds after release for animation before mLastResizeBounds are modified.
        mStartBoundsAfterRelease.set(mLastResizeBounds);

        // Drag-corner-to-resize - we don't need to adjust the bounds at this point
        if (!mOngoingPinchToResize) {
            scheduleBoundsChange();
            return;
        }

        final Point minSize = mPipBoundsState.getMinSize();
        final Point maxSize = mPipBoundsState.getMaxSize();

        // If user resize is pretty close to max size, just auto resize to max.
        if (mLastResizeBounds.width() >= PINCH_RESIZE_AUTO_MAX_RATIO * maxSize.x
                || mLastResizeBounds.height() >= PINCH_RESIZE_AUTO_MAX_RATIO * maxSize.y) {
            resizeRectAboutCenter(mLastResizeBounds, maxSize.x, maxSize.y);
        }

        // If user resize is smaller than min size, auto resize to min
        if (mLastResizeBounds.width() < minSize.x
                || mLastResizeBounds.height() < minSize.y) {
            resizeRectAboutCenter(mLastResizeBounds, minSize.x, minSize.y);
        }

        // get the current movement bounds
        final Rect movementBounds = mPipBoundsAlgorithm
                .getMovementBounds(mLastResizeBounds);

        // snap mLastResizeBounds to the correct edge based on movement bounds
        snapToMovementBoundsEdge(mLastResizeBounds, movementBounds);

        final float snapFraction = mPipBoundsAlgorithm.getSnapFraction(
                mLastResizeBounds, movementBounds);
        mPipBoundsAlgorithm.applySnapFraction(mLastResizeBounds, snapFraction);

        scheduleBoundsChange();
    }

    private void scheduleBoundsChange() {
        // Update the transition state to schedule a resize transition.
        Bundle extra = new Bundle();
        extra.putBoolean(RESIZE_BOUNDS_CHANGE, true);
        mPipTransitionState.setState(PipTransitionState.SCHEDULED_BOUNDS_CHANGE, extra);

        mPipUiEventLogger.log(PipUiEventLogger.PipUiEventEnum.PICTURE_IN_PICTURE_RESIZE);
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

    void setOhmOffset(int offset) {
        mOhmOffset = offset;
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

    @Override
    public void onPipTransitionStateChanged(@PipTransitionState.TransitionState int oldState,
            @PipTransitionState.TransitionState int newState, @Nullable Bundle extra) {
        switch (newState) {
            case PipTransitionState.SCHEDULED_BOUNDS_CHANGE:
                if (!extra.getBoolean(RESIZE_BOUNDS_CHANGE)) break;

                if (mPipBoundsState.getBounds().equals(mLastResizeBounds)) {
                    // If the bounds are invariant move the destination bounds by a single pixel
                    // to top/bottom to avoid a no-op transition. This trick helps keep the
                    // animation a part of the transition.
                    float snapFraction = mPipBoundsAlgorithm.getSnapFraction(
                            mPipBoundsState.getBounds());

                    // Move to the top if closer to the bottom edge and vice versa.
                    boolean inTopHalf = snapFraction < 1.5 || snapFraction > 3.5;
                    int offsetY = inTopHalf ? 1 : -1;
                    mLastResizeBounds.offset(0 /* dx */, offsetY);
                }
                mWaitingForBoundsChangeTransition = true;

                // Schedule PiP resize transition, but delay any config updates until very end.
                mPipScheduler.scheduleAnimateResizePip(mLastResizeBounds,
                        true /* configAtEnd */, PINCH_RESIZE_SNAP_DURATION);
                break;
            case PipTransitionState.CHANGING_PIP_BOUNDS:
                if (!mWaitingForBoundsChangeTransition) break;
                // If resize transition was scheduled from this component, handle leash updates.
                mWaitingForBoundsChangeTransition = false;

                SurfaceControl pipLeash = mPipTransitionState.getPinnedTaskLeash();
                Preconditions.checkState(pipLeash != null,
                        "No leash cached by mPipTransitionState=" + mPipTransitionState);

                final SurfaceControl.Transaction startTx = extra.getParcelable(
                        PipTransition.PIP_START_TX, SurfaceControl.Transaction.class);
                final SurfaceControl.Transaction finishTx = extra.getParcelable(
                        PipTransition.PIP_FINISH_TX, SurfaceControl.Transaction.class);
                final Rect destinationBounds = extra.getParcelable(
                        PipTransition.PIP_DESTINATION_BOUNDS, Rect.class);
                final int duration = extra.getInt(ANIMATING_BOUNDS_CHANGE_DURATION,
                        PipTransition.BOUNDS_CHANGE_JUMPCUT_DURATION);

                PipResizeAnimator animator = new PipResizeAnimator(mContext,
                        mSurfaceTransactionHelper, pipLeash,
                        startTx, finishTx, destinationBounds, mStartBoundsAfterRelease,
                        destinationBounds, duration, mAngle);
                animator.setAnimationEndCallback(() -> {
                    // All motion operations have actually finished, so make bounds cache updates.
                    mUserResizeBounds.set(destinationBounds);
                    resetState();
                    cleanUpHighPerfSessionMaybe();

                    // Signal that we are done with bounds change transition
                    mPipScheduler.scheduleFinishPipBoundsChange(destinationBounds);
                });
                animator.start();
                break;
        }
    }

    /**
     * Dumps the {@link PipResizeGestureHandler} state.
     */
    public void dump(PrintWriter pw, String prefix) {
        final String innerPrefix = prefix + "  ";
        pw.println(prefix + TAG);
        pw.println(innerPrefix + "mAllowGesture=" + mAllowGesture);
        pw.println(innerPrefix + "mIsAttached=" + mIsAttached);
        pw.println(innerPrefix + "mIsEnabled=" + mIsEnabled);
        pw.println(innerPrefix + "mEnablePinchResize=" + mEnablePinchResize);
        pw.println(innerPrefix + "mEnableDragCornerResize=" + mEnableDragCornerResize);
        pw.println(innerPrefix + "mThresholdCrossed=" + mThresholdCrossed);
        pw.println(innerPrefix + "mOhmOffset=" + mOhmOffset);
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

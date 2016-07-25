package com.android.launcher3.shortcuts;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CheckLongPressHelper;
import com.android.launcher3.Launcher;
import com.android.launcher3.Utilities;
import com.android.launcher3.dragndrop.DragLayer;

/**
 * A {@link android.view.View.OnTouchListener} that creates a {@link DeepShortcutsContainer} and
 * forwards touch events to it. This listener should be put on any icon that supports shortcuts.
 */
public class ShortcutsContainerListener implements View.OnTouchListener,
        View.OnAttachStateChangeListener {

    /** Scaled touch slop, used for detecting movement outside bounds. */
    private final float mScaledTouchSlop;

    /** Timeout before disallowing intercept on the source's parent. */
    private final int mTapTimeout;

    /** Timeout before accepting a long-press to start forwarding. */
    private final int mLongPressTimeout;

    /** Source view from which events are forwarded. */
    private final BubbleTextView mSrcIcon;

    /** Runnable used to prevent conflicts with scrolling parents. */
    private Runnable mDisallowIntercept;

    /** Runnable used to trigger forwarding on long-press. */
    private Runnable mTriggerLongPress;

    /** Whether this listener is currently forwarding touch events. */
    private boolean mForwarding;

    /** The id of the first pointer down in the current event stream. */
    private int mActivePointerId;

    private Launcher mLauncher;
    private DragLayer mDragLayer;
    /** The coordinates of the touch down, relative to the shortcuts container. */
    private final int[] mTouchDown;
    private boolean mHasMappedTouchDownToContainerCoord;

    /** If true, the gesture is not handled. The value is reset when next gesture starts. */
    private boolean mIgnoreCurrentGesture;
    private DeepShortcutsContainer mShortcutsContainer;

    public ShortcutsContainerListener(BubbleTextView icon) {
        mSrcIcon = icon;
        mScaledTouchSlop = ViewConfiguration.get(icon.getContext()).getScaledTouchSlop();
        mTapTimeout = ViewConfiguration.getTapTimeout();

        mLongPressTimeout = CheckLongPressHelper.DEFAULT_LONG_PRESS_TIMEOUT;

        icon.addOnAttachStateChangeListener(this);

        mLauncher = Launcher.getLauncher(mSrcIcon.getContext());
        mDragLayer = mLauncher.getDragLayer();
        mTouchDown = new int[2];
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // There are no shortcuts associated with this item,
            // so return to normal touch handling.
            mIgnoreCurrentGesture = !mSrcIcon.hasDeepShortcuts();

            mTouchDown[0] = (int) event.getX();
            mTouchDown[1] = (int) event.getY();
            mDragLayer.getDescendantCoordRelativeToSelf(mSrcIcon, mTouchDown);
            mHasMappedTouchDownToContainerCoord = false;
        }

        if (mIgnoreCurrentGesture) {
            return false;
        }

        final boolean wasForwarding = mForwarding;
        final boolean forwarding;
        if (wasForwarding) {
            forwarding = onTouchForwarded(event) || !onForwardingStopped();
        } else {
            forwarding = onTouchObserved(event) && onForwardingStarted();

            if (forwarding) {
                // Make sure we cancel any ongoing source event stream.
                final long now = SystemClock.uptimeMillis();
                final MotionEvent e = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL,
                        0.0f, 0.0f, 0);
                mSrcIcon.onTouchEvent(e);
                e.recycle();
            }
        }

        mForwarding = forwarding;
        return forwarding || wasForwarding;
    }

    @Override
    public void onViewAttachedToWindow(View v) {
    }

    @Override
    public void onViewDetachedFromWindow(View v) {
        mForwarding = false;
        mActivePointerId = MotionEvent.INVALID_POINTER_ID;

        if (mDisallowIntercept != null) {
            mSrcIcon.removeCallbacks(mDisallowIntercept);
        }
    }

    /**
     * Called when forwarding would like to start.
     * <p>
     * This is when we populate the shortcuts container and add it to the DragLayer.
     *
     * @return true to start forwarding, false otherwise
     */
    protected boolean onForwardingStarted() {
        mShortcutsContainer = DeepShortcutsContainer.showForIcon(mSrcIcon);
        return mShortcutsContainer != null;
    }

    /**
     * Called when forwarding would like to stop.
     *
     * @return true to stop forwarding, false otherwise
     */
    protected boolean onForwardingStopped() {
        mShortcutsContainer = null;
        return true;
    }

    /**
     * Observes motion events and determines when to start forwarding.
     *
     * @param srcEvent motion event in source view coordinates
     * @return true to start forwarding motion events, false otherwise
     */
    private boolean onTouchObserved(MotionEvent srcEvent) {
        final View src = mSrcIcon;
        if (!src.isEnabled()) {
            return false;
        }

        final int actionMasked = srcEvent.getActionMasked();
        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = srcEvent.getPointerId(0);

                if (mDisallowIntercept == null) {
                    mDisallowIntercept = new DisallowIntercept();
                }
                src.postDelayed(mDisallowIntercept, mTapTimeout);

                if (mTriggerLongPress == null) {
                    mTriggerLongPress = new TriggerLongPress();
                }
                src.postDelayed(mTriggerLongPress, mLongPressTimeout);
                break;
            case MotionEvent.ACTION_MOVE:
                final int activePointerIndex = srcEvent.findPointerIndex(mActivePointerId);
                if (activePointerIndex >= 0) {
                    final float x = srcEvent.getX(activePointerIndex);
                    final float y = srcEvent.getY(activePointerIndex);

                    // Has the pointer moved outside of the view?
                    if (!Utilities.pointInView(src, x, y, mScaledTouchSlop)) {
                        clearCallbacks();

                        return false;
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                clearCallbacks();
                break;
        }

        return false;
    }

    private void clearCallbacks() {
        if (mTriggerLongPress != null) {
            mSrcIcon.removeCallbacks(mTriggerLongPress);
        }

        if (mDisallowIntercept != null) {
            mSrcIcon.removeCallbacks(mDisallowIntercept);
        }
    }

    private void onLongPress() {
        clearCallbacks();

        final BubbleTextView src = mSrcIcon;
        if (!src.isEnabled() || !src.hasDeepShortcuts()) {
            // Ignore long-press if the view is disabled or doesn't have shortcuts.
            return;
        }

        if (!onForwardingStarted()) {
            return;
        }

        // Don't let the parent intercept our events.
        src.getParent().requestDisallowInterceptTouchEvent(true);

        // Make sure we cancel any ongoing source event stream.
        final long now = SystemClock.uptimeMillis();
        final MotionEvent e = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0, 0, 0);
        src.onTouchEvent(e);
        e.recycle();

        mForwarding = true;
    }

    /**
     * Handles forwarded motion events and determines when to stop
     * forwarding.
     *
     * @param srcEvent motion event in source view coordinates
     * @return true to continue forwarding motion events, false to cancel
     */
    private boolean onTouchForwarded(MotionEvent srcEvent) {
        final View src = mSrcIcon;
        final DeepShortcutsContainer dst = mShortcutsContainer;
        if (dst == null) {
            return false;
        }
        if (!dst.isLaidOut()) {
            return true;
        }

        // Convert event to destination-local coordinates.
        final MotionEvent dstEvent = MotionEvent.obtainNoHistory(srcEvent);
        Utilities.translateEventCoordinates(src, dst, dstEvent);

        // Convert touch down event to destination-local coordinates.
        if (!mHasMappedTouchDownToContainerCoord) {
            mDragLayer.mapCoordInSelfToDescendent(dst, mTouchDown);
            mHasMappedTouchDownToContainerCoord = true;
        }

        // Forward converted event to destination view, then recycle it.
        final boolean handled = dst.onForwardedEvent(dstEvent, mActivePointerId, mTouchDown);
        dstEvent.recycle();

        // Always cancel forwarding when the touch stream ends.
        final int action = srcEvent.getActionMasked();
        final boolean keepForwarding = action != MotionEvent.ACTION_UP
                && action != MotionEvent.ACTION_CANCEL;

        return handled && keepForwarding;
    }

    private class DisallowIntercept implements Runnable {
        @Override
        public void run() {
            final ViewParent parent = mSrcIcon.getParent();
            parent.requestDisallowInterceptTouchEvent(true);
        }
    }

    private class TriggerLongPress implements Runnable {
        @Override
        public void run() {
            onLongPress();
        }
    }
}

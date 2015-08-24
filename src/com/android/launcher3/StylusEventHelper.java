package com.android.launcher3;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Helper for identifying when a stylus touches a view while the primary stylus button is pressed.
 * This can occur in {@value MotionEvent#ACTION_DOWN} or {@value MotionEvent#ACTION_MOVE}.
 */
public class StylusEventHelper {

    /**
     * Implement this interface to receive callbacks for a stylus button press and release.
     */
    public interface StylusButtonListener {
        /**
         * Called when the stylus button is pressed.
         *
         * @param event The MotionEvent that the button press occurred for.
         * @return Whether the event was handled.
         */
        public boolean onPressed(MotionEvent event);

        /**
         * Called when the stylus button is released after a button press. This is also called if
         * the event is canceled or the stylus is lifted off the screen.
         *
         * @param event The MotionEvent the button release occurred for.
         * @return Whether the event was handled.
         */
        public boolean onReleased(MotionEvent event);
    }

    private boolean mIsButtonPressed;
    private View mView;
    private StylusButtonListener mListener;
    private final float mSlop;

    /**
     * Constructs a helper for listening to stylus button presses and releases. Ensure that {
     * {@link #onMotionEvent(MotionEvent)} and {@link #onGenericMotionEvent(MotionEvent)} are called on
     * the helper to correctly identify stylus events.
     *
     * @param listener The listener to call for stylus events.
     * @param view Optional view associated with the touch events.
     */
    public StylusEventHelper(StylusButtonListener listener, View view) {
        mListener = listener;
        mView = view;
        if (mView != null) {
            mSlop = ViewConfiguration.get(mView.getContext()).getScaledTouchSlop();
        } else {
            mSlop = ViewConfiguration.getTouchSlop();
        }
    }

    public boolean onMotionEvent(MotionEvent event) {
        final boolean stylusButtonPressed = isStylusButtonPressed(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mIsButtonPressed = stylusButtonPressed;
                if (mIsButtonPressed) {
                    return mListener.onPressed(event);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!Utilities.pointInView(mView, event.getX(), event.getY(), mSlop)) {
                    return false;
                }
                if (!mIsButtonPressed && stylusButtonPressed) {
                    mIsButtonPressed = true;
                    return mListener.onPressed(event);
                } else if (mIsButtonPressed && !stylusButtonPressed) {
                    mIsButtonPressed = false;
                    return mListener.onReleased(event);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mIsButtonPressed) {
                    mIsButtonPressed = false;
                    return mListener.onReleased(event);
                }
                break;
        }
        return false;
    }

    /**
     * Whether a stylus button press is occurring.
     */
    public boolean inStylusButtonPressed() {
        return mIsButtonPressed;
    }

    /**
     * Identifies if the provided {@link MotionEvent} is a stylus with the primary stylus button
     * pressed.
     *
     * @param event The event to check.
     * @return Whether a stylus button press occurred.
     */
    private static boolean isStylusButtonPressed(MotionEvent event) {
        return event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
                && ((event.getButtonState() & MotionEvent.BUTTON_SECONDARY)
                        == MotionEvent.BUTTON_SECONDARY);
    }
}
package ch.deletescape.lawnchair;

import android.support.annotation.NonNull;
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
         * @return Whether the event was handled.
         */
        boolean onPressed();

        /**
         * Called when the stylus button is released after a button press. This is also called if
         * the event is canceled or the stylus is lifted off the screen.
         *
         * @return Whether the event was handled.
         */
        boolean onReleased();
    }

    private boolean mIsButtonPressed;
    private View mView;
    private StylusButtonListener mListener;
    private final float mSlop;

    /**
     * Constructs a helper for listening to stylus button presses and releases. Ensure that {
     * {@link #onMotionEvent(MotionEvent)} and  are called on
     * the helper to correctly identify stylus events.
     *
     * @param listener The listener to call for stylus events.
     * @param view     Optional view associated with the touch events.
     */
    public StylusEventHelper(StylusButtonListener listener, @NonNull View view) {
        mListener = listener;
        mView = view;
        mSlop = ViewConfiguration.get(mView.getContext()).getScaledTouchSlop();
    }

    public boolean onMotionEvent(MotionEvent event) {
        final boolean stylusButtonPressed = isStylusButtonPressed(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mIsButtonPressed = stylusButtonPressed;
                if (mIsButtonPressed) {
                    return mListener.onPressed();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!Utilities.pointInView(mView, event.getX(), event.getY(), mSlop)) {
                    return false;
                }
                if (!mIsButtonPressed && stylusButtonPressed) {
                    mIsButtonPressed = true;
                    return mListener.onPressed();
                } else if (mIsButtonPressed && !stylusButtonPressed) {
                    mIsButtonPressed = false;
                    return mListener.onReleased();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mIsButtonPressed) {
                    mIsButtonPressed = false;
                    return mListener.onReleased();
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
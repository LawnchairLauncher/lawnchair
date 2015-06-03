package com.android.launcher3;

import com.android.launcher3.Utilities;

import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Helper for identifying when a stylus touches a view while the primary stylus button is pressed.
 * This can occur in {@value MotionEvent#ACTION_DOWN} or {@value MotionEvent#ACTION_MOVE}. On a
 * stylus button press this performs the view's {@link View#performLongClick()} method, if the view
 * is long clickable.
 */
public class StylusEventHelper {
    private boolean mIsButtonPressed;
    private View mView;

    public StylusEventHelper(View view) {
        mView = view;
    }

    /**
     * Call this in onTouchEvent method of a view to identify a stylus button press and perform a
     * long click (if the view is long clickable).
     *
     * @param event The event to check for a stylus button press.
     * @return Whether a stylus event occurred and was handled.
     */
    public boolean checkAndPerformStylusEvent(MotionEvent event) {
        final float slop = ViewConfiguration.get(mView.getContext()).getScaledTouchSlop();

        if (!mView.isLongClickable()) {
            // We don't do anything unless the view is long clickable.
            return false;
        }

        final boolean stylusButtonPressed = isStylusButtonPressed(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mIsButtonPressed = false;
                if (stylusButtonPressed && mView.performLongClick()) {
                    mIsButtonPressed = true;
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (Utilities.pointInView(mView, event.getX(), event.getY(), slop)) {
                    if (!mIsButtonPressed && stylusButtonPressed && mView.performLongClick()) {
                        mIsButtonPressed = true;
                        return true;
                    } else if (mIsButtonPressed && !stylusButtonPressed) {
                        mIsButtonPressed = false;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsButtonPressed = false;
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
    public static boolean isStylusButtonPressed(MotionEvent event) {
        return event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
                && event.isButtonPressed(MotionEvent.BUTTON_SECONDARY);
    }
}
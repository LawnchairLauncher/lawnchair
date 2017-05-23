package ch.deletescape.lawnchair;

import android.view.View;

import com.google.firebase.analytics.FirebaseAnalytics;

import ch.deletescape.lawnchair.StylusEventHelper.StylusButtonListener;

/**
 * Simple listener that performs a long click on the view after a stylus button press.
 */
public class SimpleOnStylusPressListener implements StylusButtonListener {
    private View mView;
    private FirebaseAnalytics mFirebaseAnalytics;

    public SimpleOnStylusPressListener(View view) {
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(view.getContext());
        mView = view;
    }

    public boolean onPressed() {
        mFirebaseAnalytics.logEvent("stylusbutton_pressed", null);
        return mView.isLongClickable() && mView.performLongClick();
    }

    public boolean onReleased() {
        return false;
    }
}
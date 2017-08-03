package ch.deletescape.lawnchair.anim;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.util.Property;
import android.view.View;

public class PropertyResetListener extends AnimatorListenerAdapter {
    private Property<View, Float> mPropertyToReset;
    private float mResetToValue;

    public PropertyResetListener(Property<View, Float> property, float f) {
        mPropertyToReset = property;
        mResetToValue = f;
    }

    @Override
    public void onAnimationEnd(Animator animator) {
        mPropertyToReset.set((View) ((ObjectAnimator) animator).getTarget(), mResetToValue);
    }
}
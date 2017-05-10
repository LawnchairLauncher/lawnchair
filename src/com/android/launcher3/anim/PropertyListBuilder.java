package com.android.launcher3.anim;

import android.animation.PropertyValuesHolder;
import android.view.View;

import java.util.ArrayList;

/**
 * Helper class to build a list of {@link PropertyValuesHolder} for view properties
 */
public class PropertyListBuilder {

    private final ArrayList<PropertyValuesHolder> mProperties = new ArrayList<>();

    public PropertyListBuilder translationX(float value) {
        mProperties.add(PropertyValuesHolder.ofFloat(View.TRANSLATION_X, value));
        return this;
    }

    public PropertyListBuilder translationY(float value) {
        mProperties.add(PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, value));
        return this;
    }

    public PropertyListBuilder scaleX(float value) {
        mProperties.add(PropertyValuesHolder.ofFloat(View.SCALE_X, value));
        return this;
    }

    public PropertyListBuilder scaleY(float value) {
        mProperties.add(PropertyValuesHolder.ofFloat(View.SCALE_Y, value));
        return this;
    }

    /**
     * Helper method to set both scaleX and scaleY
     */
    public PropertyListBuilder scale(float value) {
        return scaleX(value).scaleY(value);
    }

    public PropertyListBuilder alpha(float value) {
        mProperties.add(PropertyValuesHolder.ofFloat(View.ALPHA, value));
        return this;
    }

    public PropertyValuesHolder[] build() {
        return mProperties.toArray(new PropertyValuesHolder[mProperties.size()]);
    }
}

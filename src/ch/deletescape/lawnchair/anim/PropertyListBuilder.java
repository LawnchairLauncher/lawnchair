package ch.deletescape.lawnchair.anim;

import android.animation.PropertyValuesHolder;
import android.view.View;

import java.util.ArrayList;

public class PropertyListBuilder {
    private final ArrayList<PropertyValuesHolder> mProperties = new ArrayList<>();

    public PropertyListBuilder translationX(float f) {
        mProperties.add(PropertyValuesHolder.ofFloat(View.TRANSLATION_X, f));
        return this;
    }

    public PropertyListBuilder translationY(float f) {
        mProperties.add(PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, f));
        return this;
    }

    public PropertyListBuilder scaleX(float f) {
        mProperties.add(PropertyValuesHolder.ofFloat(View.SCALE_X, f));
        return this;
    }

    public PropertyListBuilder scaleY(float f) {
        mProperties.add(PropertyValuesHolder.ofFloat(View.SCALE_Y, f));
        return this;
    }

    public PropertyListBuilder scale(float f) {
        return scaleX(f).scaleY(f);
    }

    public PropertyValuesHolder[] build() {
        return mProperties.toArray(new PropertyValuesHolder[mProperties.size()]);
    }
}
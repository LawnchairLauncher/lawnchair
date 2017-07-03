package ch.deletescape.lawnchair.anim;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;

import java.util.HashMap;
import java.util.Map.Entry;

public class AnimationLayerSet extends AnimatorListenerAdapter {
    private final HashMap<View, Integer> mViewsToLayerTypeMap;

    public AnimationLayerSet() {
        mViewsToLayerTypeMap = new HashMap<>();
    }

    public AnimationLayerSet(View view) {
        mViewsToLayerTypeMap = new HashMap<>(1);
        addView(view);
    }

    public void addView(View view) {
        mViewsToLayerTypeMap.put(view, view.getLayerType());
    }

    @Override
    public void onAnimationStart(Animator animator) {
        for (Entry<View, Integer> entry : mViewsToLayerTypeMap.entrySet()) {
            View view = entry.getKey();
            entry.setValue(view.getLayerType());
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            if (view.isAttachedToWindow() && view.getVisibility() == View.VISIBLE) {
                view.buildLayer();
            }
        }
    }

    @Override
    public void onAnimationEnd(Animator animator) {
        for (Entry<View, Integer> entry : mViewsToLayerTypeMap.entrySet()) {
            (entry.getKey()).setLayerType(entry.getValue(), null);
        }
    }
}
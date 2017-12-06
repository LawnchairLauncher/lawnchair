/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.anim;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.util.ArrayMap;
import android.view.View;
import java.util.Iterator;
import java.util.Map;

/**
 * Helper class to automatically build view hardware layers for the duration of an animation.
 */
public class AnimationLayerSet extends AnimatorListenerAdapter {

    private final ArrayMap<View, Integer> mViewsToLayerTypeMap;

    public AnimationLayerSet() {
        mViewsToLayerTypeMap = new ArrayMap<>();
    }

    public AnimationLayerSet(View v) {
        mViewsToLayerTypeMap = new ArrayMap<>(1);
        addView(v);
    }

    public void addView(View v) {
        mViewsToLayerTypeMap.put(v, v.getLayerType());
    }

    @Override
    public void onAnimationStart(Animator animation) {
        // Enable all necessary layers
        Iterator<Map.Entry<View, Integer>> itr = mViewsToLayerTypeMap.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<View, Integer> entry = itr.next();
            View v = entry.getKey();
            entry.setValue(v.getLayerType());
            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            if (v.isAttachedToWindow() && v.getVisibility() == View.VISIBLE) {
                v.buildLayer();
            }
        }
    }

    @Override
    public void onAnimationEnd(Animator animation) {
        Iterator<Map.Entry<View, Integer>> itr = mViewsToLayerTypeMap.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<View, Integer> entry = itr.next();
            entry.getKey().setLayerType(entry.getValue(), null);
        }
    }
}

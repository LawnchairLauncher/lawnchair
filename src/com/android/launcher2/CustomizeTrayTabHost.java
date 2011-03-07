/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.launcher2;

import android.animation.Animator;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.TabHost;

public class CustomizeTrayTabHost extends TabHost implements LauncherTransitionable {
    private boolean mFirstLayout = true;

    public CustomizeTrayTabHost(Context context) {
        super(context);
    }

    public CustomizeTrayTabHost(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onLauncherTransitionStart(Animator animation) {
        if (animation != null) {
            setLayerType(LAYER_TYPE_HARDWARE, null);
            // just a sanity check that we don't build a layer before a call to onLayout
            if (!mFirstLayout) {
                // force building the layer at the beginning of the animation, so you don't get a
                // blip early in the animation
                buildLayer();
            }
        }
    }

    @Override
    public void onLauncherTransitionEnd(Animator animation) {
        if (animation != null) {
            setLayerType(LAYER_TYPE_NONE, null);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        mFirstLayout = false;
        super.onLayout(changed, l, t, r, b);
    }
}

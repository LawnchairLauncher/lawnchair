/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep.hints;

import android.content.Context;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.View;
import android.widget.FrameLayout;

public class ChipsContainer extends FrameLayout {

    private static final String TAG = "ChipsContainer";

    public static final FloatProperty<ChipsContainer> HINT_VISIBILITY =
            new FloatProperty<ChipsContainer>("hint_visibility") {
                @Override
                public void setValue(ChipsContainer chipsContainer, float v) {
                    chipsContainer.setHintVisibility(v);
                }

                @Override
                public Float get(ChipsContainer chipsContainer) {
                    return chipsContainer.mHintVisibility;
                }
            };

    private float mHintVisibility;

    public ChipsContainer(Context context) {
        super(context);
    }

    public ChipsContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ChipsContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ChipsContainer(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setView(View v) {
        removeAllViews();
        addView(v);
    }

    public void setHintVisibility(float v) {
        if (v == 1) {
            setVisibility(VISIBLE);
        } else {
            setVisibility(GONE);
        }
        mHintVisibility = v;
    }
}

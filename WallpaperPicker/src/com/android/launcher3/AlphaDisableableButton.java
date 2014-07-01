/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.launcher3;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;

/**
 * A Button which becomes translucent when it is disabled
 */
public class AlphaDisableableButton extends Button {
    public static float DISABLED_ALPHA_VALUE = 0.4f;
    public AlphaDisableableButton(Context context) {
        this(context, null);
    }

    public AlphaDisableableButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AlphaDisableableButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if(enabled) {
            setAlpha(1.0f);
        } else {
            setAlpha(DISABLED_ALPHA_VALUE);
        }
    }
}

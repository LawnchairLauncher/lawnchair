/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.launcher3.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RenderNode;
import android.widget.EdgeEffect;

/**
 * Extension of {@link EdgeEffect} which translates the content instead of the default
 * platform implementation
 */
@SuppressWarnings("NewApi")
public class TranslateEdgeEffect extends EdgeEffectCompat {

    private final RenderNode mNode;

    public TranslateEdgeEffect(Context context) {
        super(context);
        mNode = new RenderNode("TranslateEdgeEffect");
    }

    @Override
    public boolean draw(Canvas canvas) {
        return false;
    }

    public boolean getTranslationShift(float[] out) {
        Canvas c = mNode.beginRecording(1, 1);
        boolean result = super.draw(c);
        mNode.endRecording();

        out[0] = getDistance();
        return result;
    }
}

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

package com.android.launcher3.graphics;

import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;

import android.graphics.Canvas;
import android.util.FloatProperty;
import android.view.View;

import com.android.launcher3.R;

/**
 * Contains general scrim properties such as wallpaper-extracted color that subclasses can use.
 */
public class Scrim {

    public static final FloatProperty<Scrim> SCRIM_PROGRESS =
            new FloatProperty<Scrim>("scrimProgress") {
                @Override
                public Float get(Scrim scrim) {
                    return scrim.mScrimProgress;
                }

                @Override
                public void setValue(Scrim scrim, float v) {
                    scrim.setScrimProgress(v);
                }
            };

    protected final View mRoot;

    protected float mScrimProgress;
    protected int mScrimColor;
    protected int mScrimAlpha = 0;

    public Scrim(View view) {
        mRoot = view;
        mScrimColor = mRoot.getContext().getColor(R.color.wallpaper_popup_scrim);
    }

    public void draw(Canvas canvas) {
        canvas.drawColor(setColorAlphaBound(mScrimColor, mScrimAlpha));
    }

    private void setScrimProgress(float progress) {
        if (mScrimProgress != progress) {
            mScrimProgress = progress;
            mScrimAlpha = Math.round(255 * mScrimProgress);
            mRoot.invalidate();
        }
    }
}

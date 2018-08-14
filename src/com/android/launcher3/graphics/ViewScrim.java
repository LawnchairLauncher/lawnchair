/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.graphics.Canvas;
import android.util.Property;
import android.view.View;
import android.view.ViewParent;

import com.android.launcher3.R;

/**
 * A utility class that can be used to draw a scrim behind a view
 */
public abstract class ViewScrim<T extends View> {

    public static Property<ViewScrim, Float> PROGRESS =
            new Property<ViewScrim, Float>(Float.TYPE, "progress") {
                @Override
                public Float get(ViewScrim viewScrim) {
                    return viewScrim.mProgress;
                }

                @Override
                public void set(ViewScrim object, Float value) {
                    object.setProgress(value);
                }
            };

    protected final T mView;
    protected float mProgress = 0;

    public ViewScrim(T view) {
        mView = view;
    }

    public void attach() {
        mView.setTag(R.id.view_scrim, this);
    }

    public void setProgress(float progress) {
        if (mProgress != progress) {
            mProgress = progress;
            onProgressChanged();
            invalidate();
        }
    }

    public abstract void draw(Canvas canvas, int width, int height);

    protected void onProgressChanged() { }

    public void invalidate() {
        ViewParent parent = mView.getParent();
        if (parent != null) {
            ((View) parent).invalidate();
        }
    }

    public static ViewScrim get(View view) {
        return (ViewScrim) view.getTag(R.id.view_scrim);
    }
}

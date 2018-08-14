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
package com.android.quickstep.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;

import com.android.launcher3.FastBitmapDrawable;

import java.util.ArrayList;

import androidx.annotation.NonNull;

/**
 * A view which draws a drawable stretched to fit its size. Unlike ImageView, it avoids relayout
 * when the drawable changes.
 */
public class IconView extends View {

    public interface OnScaleUpdateListener {
        public void onScaleUpdate(float scale);
    }

    private Drawable mDrawable;

    private ArrayList<OnScaleUpdateListener> mScaleListeners;

    public IconView(Context context) {
        super(context);
    }

    public IconView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public IconView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setDrawable(Drawable d) {
        if (mDrawable != null) {
            mDrawable.setCallback(null);
        }
        mDrawable = d;
        if (mDrawable != null) {
            mDrawable.setCallback(this);
            mDrawable.setBounds(0, 0, getWidth(), getHeight());
        }
        invalidate();
    }

    public Drawable getDrawable() {
        return mDrawable;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mDrawable != null) {
            mDrawable.setBounds(0, 0, w, h);
        }
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who) || who == mDrawable;
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();

        final Drawable drawable = mDrawable;
        if (drawable != null && drawable.isStateful()
                && drawable.setState(getDrawableState())) {
            invalidateDrawable(drawable);
        }
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable drawable) {
        super.invalidateDrawable(drawable);
        if (drawable instanceof FastBitmapDrawable && mScaleListeners != null) {
            for (OnScaleUpdateListener listener : mScaleListeners) {
                listener.onScaleUpdate(((FastBitmapDrawable) drawable).getScale());
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mDrawable != null) {
            mDrawable.draw(canvas);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public void addUpdateScaleListener(OnScaleUpdateListener listener) {
        if (mScaleListeners == null) {
            mScaleListeners = new ArrayList<>();
        }
        mScaleListeners.add(listener);
        if (mDrawable instanceof FastBitmapDrawable) {
            listener.onScaleUpdate(((FastBitmapDrawable) mDrawable).getScale());
        }
    }

    public void removeUpdateScaleListener(OnScaleUpdateListener listener) {
        if (mScaleListeners != null) {
            mScaleListeners.remove(listener);
        }
    }
}

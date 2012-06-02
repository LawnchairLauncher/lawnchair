/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

import com.android.launcher.R;

public class DrawableStateProxyView extends LinearLayout {

    private View mView;
    private int mViewId;

    public DrawableStateProxyView(Context context) {
        this(context, null);
    }

    public DrawableStateProxyView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }


    public DrawableStateProxyView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.DrawableStateProxyView,
                defStyle, 0);
        mViewId = a.getResourceId(R.styleable.DrawableStateProxyView_sourceViewId, -1);
        a.recycle();

        setFocusable(false);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();

        if (mView == null) {
            View parent = (View) getParent();
            mView = parent.findViewById(mViewId);
        }
        mView.setPressed(isPressed());
        mView.setHovered(isHovered());
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        return false;
    }
}

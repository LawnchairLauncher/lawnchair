/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.widget.ImageView;
import com.android.launcher.R;

public class ClippedImageView extends ImageView {
    private final int mZone;

    public ClippedImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ClippedImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ClippedImageView, defStyle, 0);

        mZone = a.getDimensionPixelSize(R.styleable.ClippedImageView_ignoreZone, 0);

        a.recycle();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int zone = mZone;
        return !(zone != 0 && (zone > 0 && event.getX() >= zone) ||
                (zone < 0 && event.getX() < getWidth() + zone)) && super.onTouchEvent(event);

    }
}

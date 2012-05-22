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

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.ImageView;

class PagedViewWidgetImageView extends ImageView {
    public boolean mAllowRequestLayout = true;

    public PagedViewWidgetImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void requestLayout() {
        if (mAllowRequestLayout) {
            super.requestLayout();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.clipRect(getScrollX() + getPaddingLeft(),
                getScrollY() + getPaddingTop(),
                getScrollX() + getRight() - getLeft() - getPaddingRight(),
                getScrollY() + getBottom() - getTop() - getPaddingBottom());

        super.onDraw(canvas);
        canvas.restore();

    }
}

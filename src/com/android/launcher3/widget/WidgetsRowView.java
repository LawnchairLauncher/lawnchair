/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.launcher3.widget;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import com.android.launcher3.R;

/**
 * Layout used for widget tray rows for each app. For performance, this view can be replaced with
 * a {@link RecyclerView} in the future if we settle on scrollable single row for the widgets.
 * If we decide on collapsable grid, then HorizontalScrollView can be replaced with a
 * {@link GridLayout}.
 */
public class WidgetsRowView extends HorizontalScrollView {
    static final String TAG = "WidgetsRow";

    private Runnable mOnLayoutListener;
    private String mAppName;

    public WidgetsRowView(Context context, String appName) {
        super(context, null, 0);
        mAppName = appName;
    }

    /**
     * Clears all the key listeners for the individual widgets.
     */
    public void resetChildrenOnKeyListeners() {
        int childCount = getChildCount();
        for (int j = 0; j < childCount; ++j) {
            getChildAt(j).setOnKeyListener(null);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        TextView tv = (TextView) findViewById(R.id.widget_name);
        tv.setText(mAppName);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mOnLayoutListener = null;
    }

    public void setOnLayoutListener(Runnable r) {
        mOnLayoutListener = r;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mOnLayoutListener != null) {
            mOnLayoutListener.run();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean result = super.onTouchEvent(event);
        return result;
    }

    public static class LayoutParams extends FrameLayout.LayoutParams {
        public LayoutParams(int width, int height) {
            super(width, height);
        }
    }
}

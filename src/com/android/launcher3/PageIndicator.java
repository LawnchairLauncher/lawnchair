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

package com.android.launcher3;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.android.launcher3.R;

import java.util.ArrayList;

public class PageIndicator extends LinearLayout {
    @SuppressWarnings("unused")
    private static final String TAG = "PageIndicator";

    private LayoutInflater mLayoutInflater;

    public PageIndicator(Context context) {
        this(context, null);
    }

    public PageIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PageIndicator(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mLayoutInflater = LayoutInflater.from(context);

        LayoutTransition transition = getLayoutTransition();
        transition.setDuration(250);
    }

    void addMarker(int index) {
        index = Math.max(0, Math.min(index, getChildCount()));
        View marker = mLayoutInflater.inflate(R.layout.page_indicator_marker, this, false);
        addView(marker, index);
    }
    void addMarkers(int count) {
        for (int i = 0; i < count; ++i) {
            addMarker(Integer.MAX_VALUE);
        }
    }

    void removeMarker(int index) {
        if (getChildCount() > 0) {
            index = Math.max(0, Math.min(index, getChildCount() - 1));
            removeViewAt(index);
        }
    }
    void removeAllMarkers() {
        while (getChildCount() > 0) {
            removeMarker(Integer.MAX_VALUE);
        }
    }

    void setActiveMarker(int index) {
        for (int i = 0; i < getChildCount(); ++i) {
            PageIndicatorMarker marker = (PageIndicatorMarker) getChildAt(i);
            if (index == i) {
                marker.activate();
            } else {
                marker.inactivate();
            }
        }
    }
}

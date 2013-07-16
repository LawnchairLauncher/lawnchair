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

import android.animation.LayoutTransition;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.android.launcher3.R;

public class PageIndicatorMarker extends FrameLayout {
    @SuppressWarnings("unused")
    private static final String TAG = "PageIndicator";

    private static final int MARKER_FADE_DURATION = 150;

    private View mActiveMarker;
    private View mInactiveMarker;

    public PageIndicatorMarker(Context context) {
        this(context, null);
    }

    public PageIndicatorMarker(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PageIndicatorMarker(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    protected void onFinishInflate() {
        mActiveMarker = findViewById(R.id.active);
        mInactiveMarker = findViewById(R.id.inactive);
    }

    public void activate() {
        mActiveMarker.animate().alpha(1f)
                .setDuration(MARKER_FADE_DURATION).start();
        mInactiveMarker.animate().alpha(0f)
                .setDuration(MARKER_FADE_DURATION).start();
    }
    public void inactivate() {
        mInactiveMarker.animate().alpha(1f)
                .setDuration(MARKER_FADE_DURATION).start();
        mActiveMarker.animate().alpha(0f)
                .setDuration(MARKER_FADE_DURATION).start();
    }
}

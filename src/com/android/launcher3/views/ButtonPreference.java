/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.views;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * Extension of {@link Preference} which makes the widget layout clickable.
 *
 * @see #setWidgetLayoutResource(int)
 */
public class ButtonPreference extends Preference {

    private boolean mWidgetFrameVisible = false;

    public ButtonPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ButtonPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ButtonPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ButtonPreference(Context context) {
        super(context);
    }

    public void setWidgetFrameVisible(boolean isVisible) {
        if (mWidgetFrameVisible != isVisible) {
            mWidgetFrameVisible = isVisible;
            notifyChanged();
        }
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);

        ViewGroup widgetFrame = view.findViewById(android.R.id.widget_frame);
        if (widgetFrame != null) {
            widgetFrame.setVisibility(mWidgetFrameVisible ? View.VISIBLE : View.GONE);
        }
    }
}

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
import android.content.res.Resources;
import android.util.AttributeSet;
import android.widget.LinearLayout;


import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DynamicGrid;
import com.android.launcher3.LauncherAppState;

/**
 * Represents the individual cell of the widget inside the widget tray.
 */
public class WidgetRowView extends LinearLayout {

    private static final int PRESET_INDENT_SIZE_TABLET = 56;

    /** Widget row width is calculated by multiplying this factor to grid cell width. */
    private static final float HEIGHT_SCALE = 2.8f;

    static int sIndent = 0;
    static int sHeight = 0;

    public WidgetRowView(Context context) {
        this(context, null);
    }

    public WidgetRowView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WidgetRowView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setContainerHeight();
        setWillNotDraw(false);
        setClipToPadding(false);
        setAccessibilityDelegate(LauncherAppState.getInstance().getAccessibilityDelegate());
    }

    /**
     * Sets the widget cell container size based on the physical dimension of the device.
     */
    private void setContainerHeight() {
        // Do nothing if already set
        if (sHeight > 0) {
            return;
        }

        Resources r = getResources();
        DeviceProfile profile = LauncherAppState.getInstance().getDynamicGrid().getDeviceProfile();
        if (profile.isLargeTablet || profile.isTablet) {
            sIndent = DynamicGrid.pxFromDp(PRESET_INDENT_SIZE_TABLET, r.getDisplayMetrics());
        }
        sHeight = (int) (profile.cellWidthPx * HEIGHT_SCALE);
    }
}

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
package com.android.launcher3.allapps;

import android.graphics.Rect;
import android.view.animation.Interpolator;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.anim.PropertySetter;

/**
 * A abstract representation of a row in all-apps view
 */
public interface FloatingHeaderRow {

    FloatingHeaderRow[] NO_ROWS = new FloatingHeaderRow[0];

    void setup(FloatingHeaderView parent, FloatingHeaderRow[] allRows, boolean tabsHidden);

    void setInsets(Rect insets, DeviceProfile grid);

    int getExpectedHeight();

    /**
     * Returns true if the row should draw based on its current position and layout.
     */
    boolean shouldDraw();

    /**
     * Returns true if the view has anything worth drawing. This is different than
     * {@link #shouldDraw()} as this is called earlier in the layout to determine the view
     * position.
     */
    boolean hasVisibleContent();

    void setContentVisibility(boolean hasHeaderExtra, boolean hasAllAppsContent,
            PropertySetter setter, Interpolator headerFade, Interpolator allAppsFade);

    /**
     * Scrolls the content vertically.
     */
    void setVerticalScroll(int scroll, boolean isScrolledOut);

    Class<? extends FloatingHeaderRow> getTypeClass();
}

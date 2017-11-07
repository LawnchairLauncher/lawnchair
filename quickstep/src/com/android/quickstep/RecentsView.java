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

package com.android.quickstep;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.HorizontalScrollView;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;

/**
 * A placeholder view for recents
 */
public class RecentsView extends HorizontalScrollView implements Insettable {
    public RecentsView(Context context) {
        this(context, null);
    }

    public RecentsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RecentsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setAlpha(0);
        setVisibility(INVISIBLE);
    }

    public void setViewVisible(boolean isVisible) { }

    @Override
    public void setInsets(Rect insets) {
        MarginLayoutParams lp = (MarginLayoutParams) getLayoutParams();
        lp.topMargin = insets.top;
        lp.bottomMargin = insets.bottom;
        lp.leftMargin = insets.left;
        lp.rightMargin = insets.right;

        DeviceProfile dp = Launcher.getLauncher(getContext()).getDeviceProfile();
        if (!dp.isVerticalBarLayout()) {
             lp.bottomMargin += dp.hotseatBarSizePx + getResources().getDimensionPixelSize(
                     R.dimen.dynamic_grid_min_page_indicator_size);
        }
    }
}

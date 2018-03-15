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
package com.android.quickstep;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.quickstep.views.RecentsView;

public class FallbackRecentsView extends RecentsView<RecentsActivity> implements Insettable {

    public FallbackRecentsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FallbackRecentsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOverviewStateEnabled(true);
    }

    @Override
    protected void onAllTasksRemoved() {
        mActivity.finish();
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        DeviceProfile dp = mActivity.getDeviceProfile();
        Rect padding = getPadding(dp, getContext());
        verticalCenter(padding, dp);
        setPadding(padding.left, padding.top, padding.right, padding.bottom);
    }

    public static void verticalCenter(Rect padding, DeviceProfile dp) {
        Rect insets = dp.getInsets();
        int totalSpace = (padding.top + padding.bottom - insets.top - insets.bottom) / 2;
        padding.top = insets.top + totalSpace;
        padding.bottom = insets.bottom + totalSpace;
    }
}

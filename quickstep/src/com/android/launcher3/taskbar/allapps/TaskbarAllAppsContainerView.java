/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.taskbar.allapps;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowInsets;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.R;
import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext;

/** All apps container accessible from taskbar. */
public class TaskbarAllAppsContainerView extends
        ActivityAllAppsContainerView<TaskbarOverlayContext> {

    public TaskbarAllAppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskbarAllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        setInsets(insets.getInsets(WindowInsets.Type.systemBars()).toRect());
        return super.onApplyWindowInsets(insets);
    }

    @Override
    protected View inflateSearchBox() {
        // Remove top padding of header, since we do not have any search
        mHeader.setPadding(mHeader.getPaddingLeft(), 0,
                mHeader.getPaddingRight(), mHeader.getPaddingBottom());

        TaskbarAllAppsFallbackSearchContainer searchView =
                new TaskbarAllAppsFallbackSearchContainer(getContext(), null);
        searchView.setId(R.id.search_container_all_apps);
        searchView.setVisibility(GONE);
        return searchView;
    }

    @Override
    protected boolean isSearchSupported() {
        return false;
    }

    @Override
    protected void updateBackground(DeviceProfile deviceProfile) {
        super.updateBackground(deviceProfile);
        // TODO(b/240670050): Remove this and add header protection for the taskbar entrypoint.
        mBottomSheetBackground.setBackgroundResource(R.drawable.bg_rounded_corner_bottom_sheet);
    }

    @Override
    public boolean isInAllApps() {
        // All apps is always open
        return true;
    }
}

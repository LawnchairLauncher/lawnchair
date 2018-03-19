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

import android.app.ActivityOptions;
import android.os.Bundle;
import android.view.View;

import com.android.launcher3.BaseDraggingActivity;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.badge.BadgeInfo;
import com.android.launcher3.uioverrides.UiFactory;
import com.android.launcher3.views.BaseDragLayer;

/**
 * A simple activity to show the recently launched tasks
 */
public class RecentsActivity extends BaseDraggingActivity {

    private RecentsRootView mRecentsRootView;
    private FallbackRecentsView mFallbackRecentsView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // In case we are reusing IDP, create a copy so that we dont conflict with Launcher
        // activity.
        LauncherAppState appState = LauncherAppState.getInstanceNoCreate();
        setDeviceProfile(appState != null
                ? appState.getInvariantDeviceProfile().getDeviceProfile(this).copy(this)
                : new InvariantDeviceProfile(this).getDeviceProfile(this));

        setContentView(R.layout.fallback_recents_activity);
        mRecentsRootView = findViewById(R.id.drag_layer);
        mFallbackRecentsView = findViewById(R.id.overview_panel);

        RecentsActivityTracker.onRecentsActivityCreate(this);
    }

    @Override
    public BaseDragLayer getDragLayer() {
        return mRecentsRootView;
    }

    @Override
    public View getRootView() {
        return mRecentsRootView;
    }

    @Override
    public <T extends View> T getOverviewPanel() {
        return (T) mFallbackRecentsView;
    }

    @Override
    public BadgeInfo getBadgeInfoForItem(ItemInfo info) {
        return null;
    }

    @Override
    public ActivityOptions getActivityLaunchOptions(View v, boolean useDefaultLaunchOptions) {
        return null;
    }

    @Override
    public void invalidateParent(ItemInfo info) { }

    @Override
    protected void onStart() {
        super.onStart();
        UiFactory.onStart(this);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        UiFactory.onTrimMemory(this, level);
    }
}

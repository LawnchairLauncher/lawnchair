/*
 * Copyright (C) 2019 The Android Open Source Project
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
import android.view.View;

import com.android.launcher3.R;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.fallback.GoRecentsActivityRootView;
import com.android.quickstep.views.IconRecentsView;

/**
 * A recents activity that displays recent tasks with an icon and small snapshot.
 */
public final class RecentsActivity extends BaseRecentsActivity {

    private GoRecentsActivityRootView mRecentsRootView;
    private IconRecentsView mIconRecentsView;

    @Override
    protected void initViews() {
        setContentView(R.layout.fallback_recents_activity);
        mRecentsRootView = findViewById(R.id.drag_layer);
        mIconRecentsView = findViewById(R.id.overview_panel);
        mIconRecentsView.setRecentsToActivityHelper(new FallbackRecentsToActivityHelper(this));
    }

    @Override
    protected void reapplyUi() {
        //TODO: Implement this depending on how insets will affect the view.
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
        return (T) mIconRecentsView;
    }

    @Override
    public ActivityOptions getActivityLaunchOptions(View v) {
        // Stubbed. Recents launch animation will come from the recents view itself.
        return null;
    }

    @Override
    protected void onStart() {
        // Set the alpha to 1 before calling super, as it may get set back to 0 due to
        // onActivityStart callback.
        mIconRecentsView.setAlpha(0);
        super.onStart();
    }
}

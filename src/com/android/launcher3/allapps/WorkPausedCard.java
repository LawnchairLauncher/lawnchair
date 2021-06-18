/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_TURN_ON_WORK_APPS_TAP;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.content.Context;
import android.content.res.Configuration;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;

/**
 * Work profile toggle switch shown at the bottom of AllApps work tab
 */
public class WorkPausedCard extends LinearLayout implements View.OnClickListener {

    private final Launcher mLauncher;
    private Button mBtn;

    public WorkPausedCard(Context context) {
        this(context, null, 0);
    }

    public WorkPausedCard(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WorkPausedCard(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(getContext());
    }


    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBtn = findViewById(R.id.enable_work_apps);
        mBtn.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (Utilities.ATLEAST_P) {
            setEnabled(false);
            mLauncher.getStatsLogManager().logger().log(LAUNCHER_TURN_ON_WORK_APPS_TAP);
            UI_HELPER_EXECUTOR.post(() -> WorkModeSwitch.setWorkProfileEnabled(getContext(), true));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int orientation = getResources().getConfiguration().orientation;
        getLayoutParams().height = orientation == Configuration.ORIENTATION_PORTRAIT
                ? LayoutParams.MATCH_PARENT : LayoutParams.WRAP_CONTENT;
        super.onLayout(changed, l, t, r, b);
    }
}

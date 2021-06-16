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

package com.android.launcher3.hybridhotseat;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.Launcher;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.ActivityTracker;

/**
 * Proxy activity to return user to home screen and show halfsheet education
 */
public class HotseatEduActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(getPackageName())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        Launcher.ACTIVITY_TRACKER.registerCallback(new HotseatActivityTracker());
        startActivity(homeIntent);
        finish();
    }

    static class HotseatActivityTracker<T extends QuickstepLauncher> implements
            ActivityTracker.SchedulerCallback {

        @Override
        public boolean init(BaseActivity activity, boolean alreadyOnHome) {
            QuickstepLauncher launcher = (QuickstepLauncher) activity;
            if (launcher != null) {
                launcher.getHotseatPredictionController().showEdu();
            }
            return false;
        }

    }
}

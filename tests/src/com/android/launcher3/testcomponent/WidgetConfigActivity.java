/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.launcher3.testcomponent;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

/**
 * Simple activity for widget configuration
 */
public class WidgetConfigActivity extends Activity {

    public static final String SUFFIX_FINISH = "-finish";
    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_INTENT = "intent";

    private final BroadcastReceiver mFinishReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            WidgetConfigActivity.this.setResult(
                    intent.getIntExtra(EXTRA_CODE, RESULT_CANCELED),
                    (Intent) intent.getParcelableExtra(EXTRA_INTENT));
            WidgetConfigActivity.this.finish();
        }
    };

    private final String mAction = this.getClass().getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerReceiver(mFinishReceiver, new IntentFilter(mAction + SUFFIX_FINISH));
    }

    @Override
    protected void onResume() {
        super.onResume();
        sendBroadcast(new Intent(mAction).putExtra(Intent.EXTRA_INTENT, getIntent()));
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mFinishReceiver);
        super.onDestroy();
    }
}

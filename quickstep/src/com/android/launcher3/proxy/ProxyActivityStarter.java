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

package com.android.launcher3.proxy;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.util.Log;

import com.android.launcher3.util.StartActivityParams;

public class ProxyActivityStarter extends Activity {

    private static final String TAG = "ProxyActivityStarter";

    public static final String EXTRA_PARAMS = "start-activity-params";

    private StartActivityParams mParams;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setVisible(false);

        mParams = getIntent().getParcelableExtra(EXTRA_PARAMS);
        if (mParams == null) {
            Log.d(TAG, "Proxy activity started without params");
            finishAndRemoveTask();
            return;
        }

        if (savedInstanceState != null) {
            // Already started the activity. Just wait for the result.
            return;
        }

        try {
            if (mParams.intent != null) {
                startActivityForResult(mParams.intent, mParams.requestCode, mParams.options);
                return;
            } else if (mParams.intentSender != null) {
                startIntentSenderForResult(mParams.intentSender, mParams.requestCode,
                        mParams.fillInIntent, mParams.flagsMask, mParams.flagsValues,
                        mParams.extraFlags,
                        mParams.options);
                return;
            }
        } catch (NullPointerException | ActivityNotFoundException | SecurityException
                | SendIntentException e) {
            mParams.deliverResult(this, RESULT_CANCELED, null);
        }
        finishAndRemoveTask();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == mParams.requestCode) {
            mParams.deliverResult(this, resultCode, data);
        }
        finishAndRemoveTask();
    }

    public static Intent getLaunchIntent(Context context, StartActivityParams params) {
        return new Intent(context, ProxyActivityStarter.class)
                .putExtra(EXTRA_PARAMS, params)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }
}

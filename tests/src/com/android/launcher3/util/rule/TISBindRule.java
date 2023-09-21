/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.util.rule;

import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class TISBindRule implements TestRule {
    public static String TAG = "TISBindRule";
    public static String INTENT_FILTER = "android.intent.action.QUICKSTEP_SERVICE";
    public static String TIS_PERMISSIONS = "android.permission.STATUS_BAR_SERVICE";

    private String getLauncherPackageName(Context context) {
        return ComponentName.unflattenFromString(context.getString(
                com.android.internal.R.string.config_recentsComponentName)).getPackageName();
    }

    private ServiceConnection createConnection() {
        return new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                Log.d(TAG, "Connected to TouchInteractionService");
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                Log.d(TAG, "Disconnected from TouchInteractionService");
            }
        };
    }

    @NonNull
    @Override
    public Statement apply(@NonNull Statement base, @NonNull Description description) {
        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
                final ServiceConnection connection = createConnection();
                UiAutomation uiAutomation =
                        InstrumentationRegistry.getInstrumentation().getUiAutomation();
                uiAutomation.adoptShellPermissionIdentity(TIS_PERMISSIONS);
                Intent launchIntent = new Intent(INTENT_FILTER);
                launchIntent.setPackage(getLauncherPackageName(context));
                context.bindService(launchIntent, connection, Context.BIND_AUTO_CREATE);
                uiAutomation.dropShellPermissionIdentity();
                try {
                    base.evaluate();
                } finally {
                    context.unbindService(connection);
                }
            }
        };
    }
}

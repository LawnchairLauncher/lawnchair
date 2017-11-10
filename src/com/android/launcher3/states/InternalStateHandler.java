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
package com.android.launcher3.states;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import com.android.launcher3.Launcher;
import com.android.launcher3.Launcher.OnResumeCallback;

/**
 * Utility class to sending state handling logic to Launcher from within the same process
 */
public abstract class InternalStateHandler extends Binder implements OnResumeCallback {

    public static final String EXTRA_STATE_HANDLER = "launcher.state_handler";

    public abstract void onNewIntent(Launcher launcher);

    public static void handleIntent(Launcher launcher, Intent intent) {
        IBinder stateBinder = intent.getExtras().getBinder(EXTRA_STATE_HANDLER);
        if (stateBinder instanceof InternalStateHandler) {
            InternalStateHandler handler = (InternalStateHandler) stateBinder;
            launcher.setOnResumeCallback(handler);
            handler.onNewIntent(launcher);
        }
        intent.getExtras().remove(EXTRA_STATE_HANDLER);
    }
}

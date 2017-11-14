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
import android.os.Bundle;
import android.os.IBinder;

import com.android.launcher3.Launcher;
import com.android.launcher3.Launcher.OnResumeCallback;

/**
 * Utility class to sending state handling logic to Launcher from within the same process.
 *
 * Extending {@link Binder} ensures that the platform maintains a single instance of each object
 * which allows this object to safely navigate the system process.
 */
public abstract class InternalStateHandler extends Binder implements OnResumeCallback {

    public static final String EXTRA_STATE_HANDLER = "launcher.state_handler";

    protected abstract void init(Launcher launcher, boolean alreadyOnHome);

    public final Intent addToIntent(Intent intent) {
        Bundle extras = new Bundle();
        extras.putBinder(EXTRA_STATE_HANDLER, this);
        intent.putExtras(extras);
        return intent;
    }

    public static boolean handleCreate(Launcher launcher, Intent intent) {
        return handleIntent(launcher, intent, false);
    }

    public static boolean handleNewIntent(Launcher launcher, Intent intent, boolean alreadyOnHome) {
        return handleIntent(launcher, intent, alreadyOnHome);
    }

    private static boolean handleIntent(
            Launcher launcher, Intent intent, boolean alreadyOnHome) {
        boolean result = false;
        if (intent != null && intent.getExtras() != null) {
            IBinder stateBinder = intent.getExtras().getBinder(EXTRA_STATE_HANDLER);
            if (stateBinder instanceof InternalStateHandler) {
                InternalStateHandler handler = (InternalStateHandler) stateBinder;
                launcher.setOnResumeCallback(handler);
                handler.init(launcher, alreadyOnHome);
                result = true;
            }
            intent.getExtras().remove(EXTRA_STATE_HANDLER);
        }
        return result;
    }
}

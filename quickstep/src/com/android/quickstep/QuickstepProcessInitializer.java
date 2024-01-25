/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.os.Trace;
import android.os.UserManager;
import android.util.Log;
import android.view.ThreadedRenderer;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.MainProcessInitializer;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;

@SuppressWarnings("unused")
public class QuickstepProcessInitializer extends MainProcessInitializer {

    private static final String TAG = "QuickstepProcessInitializer";
    private static final int SETUP_DELAY_MILLIS = 5000;

    public QuickstepProcessInitializer(Context context) {
        // Fake call to create an instance of InteractionJankMonitor to avoid binder calls during
        // its initialization during transitions.
        InteractionJankMonitorWrapper.cancel(-1);
    }

    @Override
    protected void init(Context context) {
        // Workaround for b/120550382, an external app can cause the launcher process to start for
        // a work profile user which we do not support. Disable the application immediately when we
        // detect this to be the case.
        UserManager um = (UserManager) context.getSystemService(Context.USER_SERVICE);
        if (um.isManagedProfile()) {
            PackageManager pm = context.getPackageManager();
            pm.setApplicationEnabledSetting(context.getPackageName(),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0 /* flags */);
            Log.w(TAG, "Disabling " + BuildConfig.APPLICATION_ID
                    + ", unable to run in a managed profile");
            return;
        }

        super.init(context);

        // Elevate GPU priority for Quickstep and Remote animations.
        ThreadedRenderer.setContextPriority(
                ThreadedRenderer.EGL_CONTEXT_PRIORITY_HIGH_IMG);

        // Enable Looper trace points.
        // This allows us to see Handler callbacks on traces.
        Looper.getMainLooper().setTraceTag(Trace.TRACE_TAG_APP);

        if (BuildConfig.IS_STUDIO_BUILD) {
            BinderTracker.startTracking(call ->  Log.e("BinderCall",
                    call.descriptor + " called on mainthread under " + call.activeTrace));
        }
    }
}

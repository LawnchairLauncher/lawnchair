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

package com.android.wm.shell.sysui;

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_INIT;

import android.os.Build;
import android.os.SystemClock;
import android.util.Pair;
import android.view.SurfaceControl;

import androidx.annotation.VisibleForTesting;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

import java.util.ArrayList;

/**
 * The entry point implementation into the shell for initializing shell internal state.  Classes
 * which need to initialize on start of the host SysUI should inject an instance of this class and
 * add an init callback.
 */
public class ShellInit {
    private static final String TAG = ShellInit.class.getSimpleName();

    private final ShellExecutor mMainExecutor;

    // An ordered list of init callbacks to be made once shell is first started
    private final ArrayList<Pair<String, Runnable>> mInitCallbacks = new ArrayList<>();
    private boolean mHasInitialized;


    public ShellInit(ShellExecutor mainExecutor) {
        mMainExecutor = mainExecutor;
        ProtoLog.init(ShellProtoLogGroup.values());
    }

    /**
     * Adds a callback to the ordered list of callbacks be made when Shell is first started.  This
     * can be used in class constructors when dagger is used to ensure that the initialization order
     * matches the dependency order.
     *
     * @param r the callback to be made when Shell is initialized
     * @param instance used for debugging only
     */
    public <T extends Object> void addInitCallback(Runnable r, T instance) {
        if (mHasInitialized) {
            if (Build.isDebuggable()) {
                // All callbacks must be added prior to the Shell being initialized
                throw new IllegalArgumentException("Can not add callback after init");
            }
            return;
        }
        final String className = instance.getClass().getSimpleName();
        mInitCallbacks.add(new Pair<>(className, r));
        ProtoLog.v(WM_SHELL_INIT, "Adding init callback for %s", className);
    }

    /**
     * Calls all the init callbacks when the Shell is first starting.
     */
    @VisibleForTesting
    public void init() {
        ProtoLog.v(WM_SHELL_INIT, "Initializing Shell Components: %d", mInitCallbacks.size());
        SurfaceControl.setDebugUsageAfterRelease(true);
        // Init in order of registration
        for (int i = 0; i < mInitCallbacks.size(); i++) {
            final Pair<String, Runnable> info = mInitCallbacks.get(i);
            final long t1 = SystemClock.uptimeMillis();
            info.second.run();
            final long t2 = SystemClock.uptimeMillis();
            ProtoLog.v(WM_SHELL_INIT, "\t%s init took %dms", info.first, (t2 - t1));
        }
        mInitCallbacks.clear();
        mHasInitialized = true;
    }
}

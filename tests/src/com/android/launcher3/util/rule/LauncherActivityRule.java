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
package com.android.launcher3.util.rule;

import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.os.Bundle;

import androidx.test.InstrumentationRegistry;

import com.android.launcher3.Launcher;
import com.android.launcher3.Workspace.ItemOperator;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.concurrent.Callable;

/**
 * Test rule to get the current Launcher activity.
 */
public class LauncherActivityRule implements TestRule {

    private Launcher mActivity;

    @Override
    public Statement apply(Statement base, Description description) {
        return new MyStatement(base);
    }

    public Launcher getActivity() {
        return mActivity;
    }

    public Callable<Boolean> itemExists(final ItemOperator op) {
        return () -> {
            Launcher launcher = getActivity();
            if (launcher == null) {
                return false;
            }
            return launcher.getWorkspace().getFirstMatch(op) != null;
        };
    }

    private class MyStatement extends Statement implements ActivityLifecycleCallbacks {

        private final Statement mBase;

        public MyStatement(Statement base) {
            mBase = base;
        }

        @Override
        public void evaluate() throws Throwable {
            Application app = (Application)
                    InstrumentationRegistry.getTargetContext().getApplicationContext();
            app.registerActivityLifecycleCallbacks(this);
            try {
                mBase.evaluate();
            } finally {
                app.unregisterActivityLifecycleCallbacks(this);
            }
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle bundle) {
            if (activity instanceof Launcher) {
                mActivity = (Launcher) activity;
            }
        }

        @Override
        public void onActivityStarted(Activity activity) {
            if (activity instanceof Launcher) {
                mActivity.getRotationHelper().forceAllowRotationForTesting(true);
            }
        }

        @Override
        public void onActivityResumed(Activity activity) {
        }

        @Override
        public void onActivityPaused(Activity activity) {
        }

        @Override
        public void onActivityStopped(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            if (activity == mActivity) {
                mActivity = null;
            }
        }
    }
}

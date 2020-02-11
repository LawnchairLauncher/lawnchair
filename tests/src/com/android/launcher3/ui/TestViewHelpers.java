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
package com.android.launcher3.ui;

import static androidx.test.InstrumentationRegistry.getInstrumentation;
import static androidx.test.InstrumentationRegistry.getTargetContext;

import android.content.ComponentName;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.uiautomator.UiDevice;

import com.android.launcher3.LauncherAppWidgetProviderInfo;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.testcomponent.AppWidgetNoConfig;
import com.android.launcher3.testcomponent.AppWidgetWithConfig;

import java.util.concurrent.Callable;
import java.util.function.Function;

public class TestViewHelpers {
    private static final String TAG = "TestViewHelpers";

    /**
     * Finds a widget provider which can fit on the home screen.
     *
     * @param test               test suite.
     * @param hasConfigureScreen if true, a provider with a config screen is returned.
     */
    public static LauncherAppWidgetProviderInfo findWidgetProvider(AbstractLauncherUiTest test,
            final boolean hasConfigureScreen) {
        LauncherAppWidgetProviderInfo info =
                test.getOnUiThread(new Callable<LauncherAppWidgetProviderInfo>() {
                    @Override
                    public LauncherAppWidgetProviderInfo call() throws Exception {
                        ComponentName cn = new ComponentName(getInstrumentation().getContext(),
                                hasConfigureScreen ? AppWidgetWithConfig.class
                                        : AppWidgetNoConfig.class);
                        Log.d(TAG, "findWidgetProvider componentName=" + cn.flattenToString());
                        return AppWidgetManagerCompat.getInstance(getTargetContext())
                                .findProvider(cn, Process.myUserHandle());
                    }
                });
        if (info == null) {
            throw new IllegalArgumentException("No valid widget provider");
        }
        return info;
    }

    public static View findChildView(ViewGroup parent, Function<View, Boolean> condition) {
        for (int i = 0; i < parent.getChildCount(); ++i) {
            final View child = parent.getChildAt(i);
            if (condition.apply(child)) return child;
        }
        return null;
    }
}

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
package com.android.launcher3.util;

import static org.mockito.Mockito.mock;

import com.android.launcher3.shadows.LShadowAppWidgetManager;
import com.android.launcher3.shadows.LShadowBitmap;
import com.android.launcher3.shadows.LShadowLauncherApps;
import com.android.launcher3.shadows.LShadowUserManager;
import com.android.launcher3.shadows.ShadowLooperExecutor;
import com.android.launcher3.shadows.ShadowMainThreadInitializedObject;
import com.android.launcher3.shadows.ShadowDeviceFlag;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;

import org.junit.runners.model.InitializationError;
import org.robolectric.DefaultTestLifecycle;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.TestLifecycle;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.lang.reflect.Method;

import javax.annotation.Nonnull;

/**
 * Test runner with Launcher specific configurations
 */
public class LauncherRoboTestRunner extends RobolectricTestRunner {

    private static final Class<?>[] SHADOWS = new Class<?>[] {
            LShadowAppWidgetManager.class,
            LShadowUserManager.class,
            LShadowLauncherApps.class,
            LShadowBitmap.class,

            ShadowLooperExecutor.class,
            ShadowMainThreadInitializedObject.class,
            ShadowDeviceFlag.class,
    };

    public LauncherRoboTestRunner(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    @Override
    protected Config buildGlobalConfig() {
        return new Config.Builder().setShadows(SHADOWS).build();
    }

    @Nonnull
    @Override
    protected Class<? extends TestLifecycle> getTestLifecycleClass() {
        return LauncherTestLifecycle.class;
    }

    public static class LauncherTestLifecycle extends DefaultTestLifecycle {

        @Override
        public void beforeTest(Method method) {
            super.beforeTest(method);
            ShadowLog.stream = System.out;

            // Disable plugins
            PluginManagerWrapper.INSTANCE.initializeForTesting(mock(PluginManagerWrapper.class));
        }

        @Override
        public void afterTest(Method method) {
            super.afterTest(method);

            ShadowLog.stream = null;
            ShadowMainThreadInitializedObject.resetInitializedObjects();
        }
    }
}

/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.android.launcher3.Flags.FLAG_ENABLE_SUPPORT_FOR_ARCHIVING;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

/** Unit tests for {@link PackageManagerHelper}. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public final class PackageManagerHelperTest {
    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TEST_PACKAGE = "com.android.test.package";
    private static final int TEST_USER = 2;

    private Context mContext;
    private LauncherApps mLauncherApps;
    private PackageManagerHelper mPackageManagerHelper;

    @Before
    public void setup() {
        mContext = mock(Context.class);
        mLauncherApps = mock(LauncherApps.class);
        when(mContext.getSystemService(eq(LauncherApps.class))).thenReturn(mLauncherApps);
        when(mContext.getResources()).thenReturn(
                InstrumentationRegistry.getInstrumentation().getTargetContext().getResources());
        mPackageManagerHelper = new PackageManagerHelper(mContext);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ENABLE_SUPPORT_FOR_ARCHIVING)
    public void getApplicationInfo_archivedApp_appInfoIsNotNull()
            throws PackageManager.NameNotFoundException {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.isArchived = true;
        when(mLauncherApps.getApplicationInfo(TEST_PACKAGE, 0 /* flags */,
                UserHandle.of(TEST_USER)))
                .thenReturn(applicationInfo);

        assertThat(mPackageManagerHelper.getApplicationInfo(TEST_PACKAGE, UserHandle.of(TEST_USER),
                0 /* flags */))
                .isNotNull();
    }
}

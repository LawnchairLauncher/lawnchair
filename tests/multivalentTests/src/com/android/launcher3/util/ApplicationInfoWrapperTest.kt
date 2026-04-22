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

package com.android.launcher3.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.ApplicationInfo.FLAG_EXTERNAL_STORAGE
import android.content.pm.ApplicationInfo.FLAG_INSTALLED
import android.content.pm.ApplicationInfo.FLAG_SUSPENDED
import android.content.pm.ApplicationInfo.FLAG_SYSTEM
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags.FLAG_ENABLE_SUPPORT_FOR_ARCHIVING
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

/** Unit tests for {@link ApplicationInfoWrapper}. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class ApplicationInfoWrapperTest {

    @get:Rule val setFlagsRule = SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT)

    private lateinit var context: Context
    private lateinit var launcherApps: LauncherApps

    @Before
    fun setup() {
        context = Mockito.mock(Context::class.java)
        launcherApps = Mockito.mock(LauncherApps::class.java)
        whenever(context.getSystemService(eq(LauncherApps::class.java))).thenReturn(launcherApps)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SUPPORT_FOR_ARCHIVING)
    fun archivedApp_appInfoIsNotNull() {
        val applicationInfo = ApplicationInfo()
        applicationInfo.isArchived = true
        whenever(launcherApps.getApplicationInfo(eq(TEST_PACKAGE), any(), eq(TEST_USER)))
            .thenReturn(applicationInfo)

        val wrapper = ApplicationInfoWrapper(context, TEST_PACKAGE, TEST_USER)
        assertNotNull(wrapper.getInfo())
        assertTrue(wrapper.isArchived())
        assertFalse(wrapper.isInstalled())
    }

    @Test
    fun notInstalledApp_nullAppInfo() {
        val applicationInfo = ApplicationInfo()
        whenever(launcherApps.getApplicationInfo(eq(TEST_PACKAGE), any(), eq(TEST_USER)))
            .thenReturn(applicationInfo)

        val wrapper = ApplicationInfoWrapper(context, TEST_PACKAGE, TEST_USER)
        assertNull(wrapper.getInfo())
        assertFalse(wrapper.isInstalled())
    }

    @Test
    fun appInfo_suspended() {
        val wrapper =
            ApplicationInfoWrapper(
                ApplicationInfo().apply { flags = FLAG_INSTALLED.or(FLAG_SUSPENDED) }
            )
        assertTrue(wrapper.isSuspended())
    }

    @Test
    fun appInfo_notSuspended() {
        val wrapper = ApplicationInfoWrapper(ApplicationInfo())
        assertFalse(wrapper.isSuspended())
    }

    @Test
    fun appInfo_system() {
        val wrapper = ApplicationInfoWrapper(ApplicationInfo().apply { flags = FLAG_SYSTEM })
        assertTrue(wrapper.isSystem())
    }

    @Test
    fun appInfo_notSystem() {
        val wrapper = ApplicationInfoWrapper(ApplicationInfo())
        assertFalse(wrapper.isSystem())
    }

    @Test
    fun appInfo_onSDCard() {
        val wrapper =
            ApplicationInfoWrapper(ApplicationInfo().apply { flags = FLAG_EXTERNAL_STORAGE })
        assertTrue(wrapper.isOnSdCard())
    }

    @Test
    fun appInfo_notOnSDCard() {
        val wrapper = ApplicationInfoWrapper(ApplicationInfo())
        assertFalse(wrapper.isOnSdCard())
    }

    companion object {
        const val TEST_PACKAGE = "com.android.test.package"
        private val TEST_USER = UserHandle.of(3)
    }
}

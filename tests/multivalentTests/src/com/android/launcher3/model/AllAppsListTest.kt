/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.model

import android.content.ComponentName
import android.content.pm.LauncherApps
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AllAppsListTest {

    @get:Rule val context = SandboxApplication().withModelDependency()

    private val user = Process.myUserHandle()
    private val appsList
        get() = context.appComponent.testableModelState.appsList

    @Test
    fun updatePackage_updates_the_list() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            assertFalse(appsList.getAndResetChangeFlag())
            assertTrue(appsList.data.isEmpty())

            appsList.updatePackage(context, TEST_PACKAGE, user, HashSet())
            assertTrue(appsList.getAndResetChangeFlag())
            assertFalse(appsList.data.isEmpty())
        }
    }

    @Test
    fun removePackage_removed_data() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            appsList.updatePackage(context, TEST_PACKAGE, user, HashSet())
            appsList.getAndResetChangeFlag()
            assertFalse(appsList.data.isEmpty())

            appsList.removePackage(TEST_PACKAGE, user)
            assertTrue(appsList.getAndResetChangeFlag())
            assertTrue(appsList.data.isEmpty())
        }
    }

    @Test
    fun updatePackage_performs_diff() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            val launcherApps = context.spyService(LauncherApps::class.java)
            val allApps = launcherApps.getActivityList(TEST_PACKAGE, user)

            appsList.updatePackage(context, TEST_PACKAGE, user, HashSet())
            appsList.getAndResetChangeFlag()
            assertFalse(appsList.data.isEmpty())

            doReturn(listOf(allApps[0], allApps[1]))
                .whenever(launcherApps)
                .getActivityList(TEST_PACKAGE, user)
            val outRemovedComponents = HashSet<ComponentName>()
            appsList.updatePackage(context, TEST_PACKAGE, user, outRemovedComponents)
            assertTrue(appsList.getAndResetChangeFlag())
            assertEquals(2, appsList.data.size)
            assertEquals(allApps.size - 2, outRemovedComponents.size)
        }
    }
}

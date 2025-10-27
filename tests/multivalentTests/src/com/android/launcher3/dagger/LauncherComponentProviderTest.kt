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

package com.android.launcher3.dagger

import android.content.Context
import android.content.ContextWrapper
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.R
import com.android.launcher3.util.LauncherModelHelper.SandboxModelContext
import com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherComponentProviderTest {

    val app: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `returns same component as Launcher application`() {
        val c = SandboxModelContext()
        assertSame(c.appComponent, LauncherComponentProvider.get(c))
        assertNotSame(LauncherComponentProvider.get(c), LauncherComponentProvider.get(app))
    }

    @Test
    fun `returns same component for isolated context`() {
        val c = IsolatedContext()

        // Same component is returned for multiple calls, irrespective of the wrappers
        assertNotNull(LauncherComponentProvider.get(c))
        assertSame(
            LauncherComponentProvider.get(c),
            LauncherComponentProvider.get(ContextThemeWrapper(c, R.style.LauncherTheme)),
        )

        // Different than main application
        assertNotSame(LauncherComponentProvider.get(c), LauncherComponentProvider.get(app))
    }

    @Test
    fun `different components for different isolated context`() {
        val c1 = IsolatedContext()
        val c2 = IsolatedContext()

        assertNotNull(LauncherComponentProvider.get(c1))
        assertNotNull(LauncherComponentProvider.get(c2))
        assertNotSame(LauncherComponentProvider.get(c1), LauncherComponentProvider.get(c2))
    }

    inner class IsolatedContext : ContextWrapper(app.createPackageContext(TEST_PACKAGE, 0)) {

        override fun getApplicationContext(): Context = this
    }
}

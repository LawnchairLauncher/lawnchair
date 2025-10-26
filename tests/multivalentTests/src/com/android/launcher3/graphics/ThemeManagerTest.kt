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

package com.android.launcher3.graphics

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.FakeLauncherPrefs
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.icons.mono.MonoIconThemeController
import com.android.launcher3.util.AllModulesForTest
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.FakePrefsModule
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import dagger.Component
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ThemeManagerTest {

    @get:Rule val context = SandboxApplication()

    lateinit var themeManager: ThemeManager

    @Before
    fun setUp() {
        context.initDaggerComponent(DaggerThemeManagerComponent.builder())
        themeManager = ThemeManager.INSTANCE[context]
    }

    @Test
    fun `isMonoThemeEnabled get and set`() {
        themeManager.isMonoThemeEnabled = true
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        assertTrue(themeManager.isMonoThemeEnabled)
        assertThat(themeManager.iconState.themeController)
            .isInstanceOf(MonoIconThemeController::class.java)

        themeManager.isMonoThemeEnabled = false
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        assertFalse(themeManager.isMonoThemeEnabled)
        assertThat(themeManager.iconState.themeController).isNull()
    }

    @Test
    fun `callback called on theme change`() {
        themeManager.isMonoThemeEnabled = false

        var callbackCalled = false
        themeManager.addChangeListener { callbackCalled = true }
        themeManager.isMonoThemeEnabled = true
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}

        assertTrue(callbackCalled)
    }

    @Test
    fun `iconState changes with theme`() {
        themeManager.isMonoThemeEnabled = false
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        val disabledIconState = themeManager.iconState

        themeManager.isMonoThemeEnabled = true
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        assertNotEquals(disabledIconState, themeManager.iconState)

        themeManager.isMonoThemeEnabled = false
        TestUtil.runOnExecutorSync(MAIN_EXECUTOR) {}
        assertEquals(disabledIconState, themeManager.iconState)
    }
}

@LauncherAppSingleton
@Component(modules = [AllModulesForTest::class, FakePrefsModule::class])
interface ThemeManagerComponent : LauncherAppComponent {

    override fun getLauncherPrefs(): FakeLauncherPrefs

    @Component.Builder
    interface Builder : LauncherAppComponent.Builder {

        override fun build(): ThemeManagerComponent
    }
}

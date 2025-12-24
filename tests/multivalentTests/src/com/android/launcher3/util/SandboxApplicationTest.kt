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
import android.hardware.display.DisplayManager
import android.os.UserHandle
import android.os.UserManager
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
import com.android.launcher3.LauncherFiles.SHARED_PREFERENCES_KEY
import com.android.launcher3.LauncherPrefs.Companion.BOOT_AWARE_PREFS_KEY
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.stub

@RunWith(LauncherMultivalentJUnit::class)
class SandboxApplicationTest {
    @get:Rule val app = SandboxApplication()

    private val display: Display
        get() {
            return checkNotNull(app.getSystemService(DisplayManager::class.java))
                .getDisplay(DEFAULT_DISPLAY)
        }

    @Test
    fun testCreateDisplayContext_isSandboxed() {
        val displayContext = app.createDisplayContext(display)
        assertThat(displayContext.applicationContext).isEqualTo(app)
    }

    @Test
    fun testCreateWindowContext_fromSandboxedDisplayContext_isSandboxed() {
        val displayContext = app.createDisplayContext(display)
        val nestedContext = displayContext.createWindowContext(TYPE_APPLICATION_OVERLAY, null)
        assertThat(nestedContext.applicationContext).isEqualTo(app)
    }

    @Test
    fun testGetApplicationContext_beforeManualInit_throwsException() {
        val manualApp = SandboxApplication()

        assertThrows(IllegalStateException::class.java) {
            assertThat(manualApp.applicationContext).isEqualTo(manualApp)
        }
    }

    @Test
    fun testGetApplicationContext_afterManualInit_isApplication() {
        SandboxApplication().run {
            init()
            assertThat(applicationContext).isEqualTo(this)
            onDestroy()
        }
    }

    @Test
    fun testGetSharedPreferences_userLocked_throwsException() {
        app.spyService(UserManager::class.java).stub {
            on { isUserUnlockingOrUnlocked(UserHandle.myUserId()) } doReturn false
        }
        assertThrows(IllegalStateException::class.java) {
            app.createCredentialProtectedStorageContext()
                .getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        }
    }

    @Test
    fun testGetSharedPreferences_windowContextAndUserLocked_throwsException() {
        app.spyService(UserManager::class.java).stub {
            on { isUserUnlockingOrUnlocked(UserHandle.myUserId()) } doReturn false
        }
        val windowContext =
            app.createCredentialProtectedStorageContext()
                .createDisplayContext(display)
                .createWindowContext(TYPE_APPLICATION_OVERLAY, null)

        assertThrows(IllegalStateException::class.java) {
            windowContext.getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        }
    }

    @Test
    fun testGetSharedPreferences_deviceProtectedStorageContextAndUserLocked_returnsPreferences() {
        app.spyService(UserManager::class.java).stub {
            on { isUserUnlockingOrUnlocked(UserHandle.myUserId()) } doReturn false
        }
        val deviceProtectedStorageContext = app.createDeviceProtectedStorageContext()
        val sharedPreferences =
            deviceProtectedStorageContext.getSharedPreferences(
                BOOT_AWARE_PREFS_KEY,
                Context.MODE_PRIVATE,
            )
        assertThat(sharedPreferences).isNotNull()
    }

    @Test
    fun testGetSharedPreferences_userUnlocked_returnsPreferences() {
        app.spyService(UserManager::class.java).stub {
            on { isUserUnlockingOrUnlocked(UserHandle.myUserId()) } doReturn true
        }
        val sharedPreferences =
            app.createCredentialProtectedStorageContext()
                .getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        assertThat(sharedPreferences).isNotNull()
    }
}

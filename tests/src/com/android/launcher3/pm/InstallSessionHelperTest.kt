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

package com.android.launcher3.pm

import android.content.pm.ApplicationInfo
import android.content.pm.ApplicationInfo.FLAG_INSTALLED
import android.content.pm.ApplicationInfo.FLAG_SYSTEM
import android.content.pm.LauncherApps
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Process.myUserHandle
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.PROMISE_ICON_IDS
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.IntArray
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class InstallSessionHelperTest {

    @get:Rule val sandboxContext = SandboxApplication()

    private val packageManager = sandboxContext.packageManager
    private val expectedAppPackage = "expectedAppPackage"
    private val expectedInstallerPackage = sandboxContext.packageName
    private val mockPackageInstaller: PackageInstaller = mock()

    private lateinit var installSessionHelper: InstallSessionHelper
    private lateinit var launcherApps: LauncherApps

    @Before
    fun setup() {
        whenever(packageManager.packageInstaller).thenReturn(mockPackageInstaller)
        launcherApps = sandboxContext.spyService(LauncherApps::class.java)
        installSessionHelper = InstallSessionHelper(sandboxContext)
    }

    @Test
    fun `getActiveSessions fetches verified install sessions from LauncherApps`() {
        // Given
        val expectedVerifiedSession1 =
            PackageInstaller.SessionInfo().apply {
                sessionId = 0
                installerPackageName = expectedInstallerPackage
                appPackageName = expectedAppPackage
                userId = myUserHandle().identifier
            }
        val expectedVerifiedSession2 =
            PackageInstaller.SessionInfo().apply {
                sessionId = 1
                installerPackageName = expectedInstallerPackage
                appPackageName = "app2"
                userId = myUserHandle().identifier
            }
        val expectedSessions = listOf(expectedVerifiedSession1, expectedVerifiedSession2)
        whenever(launcherApps.allPackageInstallerSessions).thenReturn(expectedSessions)
        // When
        val actualSessions = installSessionHelper.getActiveSessions()
        // Then
        assertThat(actualSessions.values.toList()).isEqualTo(expectedSessions)
    }

    @Test
    fun `getActiveSessionInfo fetches verified install sessions for given user and pkg`() {
        // Given
        val expectedVerifiedSession =
            PackageInstaller.SessionInfo().apply {
                installerPackageName = expectedInstallerPackage
                appPackageName = expectedAppPackage
                userId = myUserHandle().identifier
            }
        whenever(launcherApps.allPackageInstallerSessions)
            .thenReturn(listOf(expectedVerifiedSession))
        // When
        val actualSession =
            installSessionHelper.getActiveSessionInfo(myUserHandle(), expectedAppPackage)
        // Then
        assertThat(actualSession).isEqualTo(expectedVerifiedSession)
    }

    @Test
    fun `getVerifiedSessionInfo verifies and returns session for given id`() {
        // Given
        val expectedSession =
            PackageInstaller.SessionInfo().apply {
                sessionId = 1
                installerPackageName = expectedInstallerPackage
                appPackageName = expectedAppPackage
                userId = myUserHandle().identifier
            }
        whenever(mockPackageInstaller.getSessionInfo(1)).thenReturn(expectedSession)
        // When
        val actualSession = installSessionHelper.getVerifiedSessionInfo(1)
        // Then
        assertThat(actualSession).isEqualTo(expectedSession)
    }

    @Test
    fun `isTrustedPackage returns true if LauncherApps finds ApplicationInfo`() {
        // Given
        val expectedApplicationInfo =
            ApplicationInfo().apply { flags = FLAG_SYSTEM or FLAG_INSTALLED }
        doReturn(expectedApplicationInfo)
            .whenever(launcherApps)
            .getApplicationInfo(eq(expectedAppPackage), any(), eq(UserHandle(0)))
        // When
        val actualResult = installSessionHelper.isTrustedPackage(expectedAppPackage, UserHandle(0))
        // Then
        assertThat(actualResult).isTrue()
    }

    @Test
    fun `getAllVerifiedSessions verifies and returns all active install sessions`() {
        // Given
        val expectedVerifiedSession1 =
            PackageInstaller.SessionInfo().apply {
                sessionId = 0
                installerPackageName = expectedInstallerPackage
                appPackageName = expectedAppPackage
                userId = myUserHandle().identifier
            }
        val expectedVerifiedSession2 =
            PackageInstaller.SessionInfo().apply {
                sessionId = 1
                installerPackageName = expectedInstallerPackage
                appPackageName = "app2"
                userId = myUserHandle().identifier
            }
        val expectedSessions = listOf(expectedVerifiedSession1, expectedVerifiedSession2)
        whenever(launcherApps.allPackageInstallerSessions).thenReturn(expectedSessions)
        // When
        val actualSessions = installSessionHelper.allVerifiedSessions
        // Then
        assertThat(actualSessions).isEqualTo(expectedSessions)
    }

    @Test
    fun `promiseIconAddedForId returns true if there is a promiseIcon with the session id`() {
        // Given
        val expectedIdString = IntArray().apply { add(1) }.toConcatString()
        LauncherPrefs.get(sandboxContext).putSync(Pair(PROMISE_ICON_IDS, expectedIdString))
        val expectedSession =
            PackageInstaller.SessionInfo().apply {
                sessionId = 1
                installerPackageName = expectedInstallerPackage
                appPackageName = "app2"
                userId = myUserHandle().identifier
            }
        whenever(launcherApps.allPackageInstallerSessions).thenReturn(listOf(expectedSession))
        // When
        var actualResult = false
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            actualResult = installSessionHelper.promiseIconAddedForId(1)
        }
        // Then
        assertThat(actualResult).isTrue()
    }

    @Test
    fun `removePromiseIconId removes promiseIconId for given Session id`() {
        // Given
        val expectedIdString = IntArray().apply { add(1) }.toConcatString()
        LauncherPrefs.get(sandboxContext).putSync(Pair(PROMISE_ICON_IDS, expectedIdString))
        val expectedSession =
            PackageInstaller.SessionInfo().apply {
                sessionId = 1
                installerPackageName = expectedInstallerPackage
                appPackageName = "app2"
                userId = myUserHandle().identifier
            }
        whenever(launcherApps.allPackageInstallerSessions).thenReturn(listOf(expectedSession))
        // When
        var actualResult = true
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            installSessionHelper.removePromiseIconId(1)
            actualResult = installSessionHelper.promiseIconAddedForId(1)
        }
        // Then
        assertThat(actualResult).isFalse()
    }

    @Test
    fun `tryQueuePromiseAppIcon will update promise icon ids from eligible sessions`() {
        // Given
        val expectedIdString = IntArray().apply { add(1) }.toConcatString()
        LauncherPrefs.get(sandboxContext).putSync(Pair(PROMISE_ICON_IDS, expectedIdString))
        val expectedSession =
            PackageInstaller.SessionInfo().apply {
                sessionId = 1
                installerPackageName = expectedInstallerPackage
                appPackageName = "appPackage"
                userId = myUserHandle().identifier
                appIcon = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8)
                appLabel = "appLabel"
                installReason = PackageManager.INSTALL_REASON_USER
            }
        whenever(launcherApps.allPackageInstallerSessions).thenReturn(listOf(expectedSession))
        // When
        var wasPromiseIconAdded = false
        var actualPromiseIconIds = ""
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            installSessionHelper.removePromiseIconId(1)
            installSessionHelper.tryQueuePromiseAppIcon(expectedSession)
            wasPromiseIconAdded = installSessionHelper.promiseIconAddedForId(1)
            actualPromiseIconIds = LauncherPrefs.get(sandboxContext).get(PROMISE_ICON_IDS)
        }
        // Then
        assertThat(wasPromiseIconAdded).isTrue()
        assertThat(actualPromiseIconIds).isEqualTo(expectedIdString)
    }

    @Test
    fun `verifySessionInfo is true if can verify given SessionInfo`() {
        // Given
        val expectedIdString = IntArray().apply { add(1) }.toConcatString()
        LauncherPrefs.get(sandboxContext).putSync(Pair(PROMISE_ICON_IDS, expectedIdString))
        val expectedSession =
            PackageInstaller.SessionInfo().apply {
                sessionId = 1
                installerPackageName = expectedInstallerPackage
                appPackageName = "appPackage"
                userId = myUserHandle().identifier
                appIcon = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8)
                appLabel = "appLabel"
                installReason = PackageManager.INSTALL_REASON_USER
            }
        whenever(launcherApps.allPackageInstallerSessions).thenReturn(listOf(expectedSession))
        // When
        var actualResult = false
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            actualResult = installSessionHelper.verifySessionInfo(expectedSession)
        }
        // Then
        assertThat(actualResult).isTrue()
    }
}

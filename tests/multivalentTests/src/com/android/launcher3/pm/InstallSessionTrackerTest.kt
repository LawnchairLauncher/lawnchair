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

import android.content.pm.LauncherApps
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags.FLAG_ENABLE_SUPPORT_FOR_ARCHIVING
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.LauncherModelHelper
import com.android.launcher3.util.PackageUserKey
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class InstallSessionTrackerTest {
    @get:Rule val setFlagsRule = SetFlagsRule()

    private val mockInstallSessionHelper: InstallSessionHelper = mock()
    private val mockCallback: InstallSessionTracker.Callback = mock()
    private val mockPackageInstaller: PackageInstaller = mock()

    private val launcherModelHelper = LauncherModelHelper()
    private val sandboxContext = launcherModelHelper.sandboxContext

    lateinit var launcherApps: LauncherApps
    lateinit var installSessionTracker: InstallSessionTracker

    @Before
    fun setup() {
        launcherApps = sandboxContext.spyService(LauncherApps::class.java)
        installSessionTracker =
            InstallSessionTracker(
                mockInstallSessionHelper,
                mockCallback,
                mockPackageInstaller,
                launcherApps,
            )
    }

    @After
    fun teardown() {
        launcherModelHelper.destroy()
    }

    @Test
    fun `onCreated triggers callbacks for setting up new install session`() {
        // Given
        val expectedSessionId = 1
        val expectedSession =
            PackageInstaller.SessionInfo().apply {
                sessionId = expectedSessionId
                appPackageName = "appPackageName"
                userId = 0
            }
        val expectedPackageKey = PackageUserKey("appPackageName", UserHandle(0))
        whenever(mockInstallSessionHelper.getVerifiedSessionInfo(expectedSessionId))
            .thenReturn(expectedSession)
        // When
        installSessionTracker.onCreated(expectedSessionId)
        // Then
        verify(mockCallback).onInstallSessionCreated(any())
        verify(mockCallback).onUpdateSessionDisplay(expectedPackageKey, expectedSession)
        verify(mockInstallSessionHelper).tryQueuePromiseAppIcon(expectedSession)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_SUPPORT_FOR_ARCHIVING)
    fun `onCreated for unarchival triggers onPackageStateChanged`() {
        // Given
        val expectedSessionId = 1
        val expectedSession =
            spy(PackageInstaller.SessionInfo()).apply {
                sessionId = expectedSessionId
                appPackageName = "appPackageName"
                userId = 0
                whenever(isUnarchival).thenReturn(true)
            }
        whenever(mockInstallSessionHelper.getVerifiedSessionInfo(expectedSessionId))
            .thenReturn(expectedSession)
        // When
        installSessionTracker.onCreated(expectedSessionId)
        // Then
        verify(mockCallback).onPackageStateChanged(any())
    }

    @Test
    fun `onFinished triggers onPackageStateChanged if session found in cache`() {
        // Given
        val expectedSessionId = 1
        val expectedSession =
            PackageInstaller.SessionInfo().apply {
                sessionId = expectedSessionId
                appPackageName = "appPackageName"
                userId = 0
            }
        val expectedPackageKey = PackageUserKey("appPackageName", UserHandle(0))
        whenever(mockInstallSessionHelper.getVerifiedSessionInfo(expectedSessionId))
            .thenReturn(expectedSession)
        whenever(mockInstallSessionHelper.activeSessions)
            .thenReturn(hashMapOf(expectedPackageKey to expectedSession))
        // When
        installSessionTracker.onFinished(expectedSessionId, /* success */ true)
        // Then
        verify(mockCallback).onPackageStateChanged(any())
    }

    @Test
    fun `onFinished failure calls onSessionFailure and promise icon removal for existing icon`() {
        // Given
        val expectedSessionId = 1
        val expectedPackage = "appPackageName"
        val expectedSession =
            PackageInstaller.SessionInfo().apply {
                sessionId = expectedSessionId
                appPackageName = expectedPackage
                userId = 0
            }
        val expectedPackageKey = PackageUserKey(expectedPackage, UserHandle(0))
        whenever(mockInstallSessionHelper.getVerifiedSessionInfo(expectedSessionId))
            .thenReturn(expectedSession)
        whenever(mockInstallSessionHelper.activeSessions)
            .thenReturn(hashMapOf(expectedPackageKey to expectedSession))
        whenever(mockInstallSessionHelper.promiseIconAddedForId(expectedSessionId)).thenReturn(true)
        // When
        installSessionTracker.onFinished(expectedSessionId, /* success */ false)
        // Then
        verify(mockCallback).onSessionFailure(expectedPackage, expectedPackageKey.mUser)
        verify(mockInstallSessionHelper).removePromiseIconId(expectedSessionId)
    }

    @Test
    fun `onProgressChanged triggers onPackageStateChanged if verified session found`() {
        // Given
        val expectedSessionId = 1
        val expectedSession =
            PackageInstaller.SessionInfo().apply {
                sessionId = expectedSessionId
                appPackageName = "appPackageName"
                userId = 0
            }
        whenever(mockInstallSessionHelper.getVerifiedSessionInfo(expectedSessionId))
            .thenReturn(expectedSession)
        // When
        installSessionTracker.onProgressChanged(expectedSessionId, /* progress */ 50f)
        // Then
        verify(mockCallback).onPackageStateChanged(any())
    }

    @Test
    fun `onBadgingChanged triggers session display update and queues promise icon if verified`() {
        // Given
        val expectedSessionId = 1
        val expectedSession =
            PackageInstaller.SessionInfo().apply {
                sessionId = expectedSessionId
                appPackageName = "appPackageName"
                userId = 0
            }
        val expectedPackageKey = PackageUserKey("appPackageName", UserHandle(0))
        whenever(mockInstallSessionHelper.getVerifiedSessionInfo(expectedSessionId))
            .thenReturn(expectedSession)
        // When
        installSessionTracker.onBadgingChanged(expectedSessionId)
        // Then
        verify(mockCallback).onUpdateSessionDisplay(expectedPackageKey, expectedSession)
        verify(mockInstallSessionHelper).tryQueuePromiseAppIcon(expectedSession)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun `register triggers registerPackageInstallerSessionCallback for versions from Q`() {
        // Given
        doNothing()
            .whenever(launcherApps)
            .registerPackageInstallerSessionCallback(MODEL_EXECUTOR, installSessionTracker)
        // When
        installSessionTracker.register()
        // Then
        verify(launcherApps)
            .registerPackageInstallerSessionCallback(MODEL_EXECUTOR, installSessionTracker)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun `unregister triggers unregisterPackageInstallerSessionCallback for versions from Q`() {
        // Given
        doNothing()
            .whenever(launcherApps)
            .unregisterPackageInstallerSessionCallback(installSessionTracker)
        // When
        installSessionTracker.close()
        // Then
        verify(launcherApps).unregisterPackageInstallerSessionCallback(installSessionTracker)
    }
}

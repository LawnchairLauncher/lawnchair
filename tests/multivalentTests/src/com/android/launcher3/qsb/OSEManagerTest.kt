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

package com.android.launcher3.qsb

import android.app.SearchManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.R
import com.android.launcher3.pm.InstallSessionHelper
import com.android.launcher3.pm.InstallSessionTracker
import com.android.launcher3.pm.PackageInstallInfo
import com.android.launcher3.pm.PackageInstallInfo.Companion.STATUS_INSTALLED
import com.android.launcher3.qsb.OSEManager.Companion.OVERLAY_ACTION
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.android.launcher3.util.SafeCloseable
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.SecureStringObserver
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/** Unit tests for OSEManager. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class OSEManagerTest {

    @get:Rule val mockitoRule = MockitoJUnit.rule()

    val context = spy(SandboxApplication())
    private val settingsObserver: SecureStringObserver = mock()
    private val installSessionHelper: InstallSessionHelper = mock()
    private lateinit var launcherApps: LauncherApps
    private lateinit var searchManager: SearchManager
    private val componentName: ComponentName = mock()
    private val installSessionTracker: InstallSessionTracker = mock()
    private val mockInstallSessionInfo: PackageInstaller.SessionInfo = mock()
    private val appInfoInstalled = ApplicationInfo()
    private val oseManager: OSEManager by lazy {
        OSEManager(context, settingsObserver, installSessionHelper, UI_HELPER_EXECUTOR)
    }
    val sessionTrackerCaptor = argumentCaptor<OSEManager.SessionTrackerCallback>()
    val res = spy(context.resources)
    lateinit var listenableRefClosable: SafeCloseable
    val mockCallback: (OSEManager.OSEInfo) -> Unit = mock()

    @After
    fun tearDown() {
        oseManager.close()
        UI_HELPER_EXECUTOR.submit {}.get()
        listenableRefClosable.close()
    }

    @Before
    fun setUp() {
        doReturn(res).whenever(context).resources
        searchManager = context.spyService(SearchManager::class.java)
        doReturn(BING_PKG).whenever(componentName).packageName
        doReturn(componentName).whenever(searchManager).globalSearchActivity
        doReturn(emptyArray<String>())
            .whenever(res)
            .getStringArray(eq(R.array.supported_overlay_apps))
        listenableRefClosable = oseManager.oseInfo.forEach(UI_HELPER_EXECUTOR, mockCallback)
        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) {}
        reset(mockCallback)
        appInfoInstalled.isArchived = false
        appInfoInstalled.flags = ApplicationInfo.FLAG_INSTALLED
        launcherApps = context.spyService(LauncherApps::class.java)
        doReturn(appInfoInstalled)
            .whenever(launcherApps)
            .getApplicationInfo(eq(GOOGLE_PACKAGE), anyInt(), eq(Process.myUserHandle()))
        doReturn(appInfoInstalled)
            .whenever(launcherApps)
            .getApplicationInfo(eq(DUCK_PKG), anyInt(), eq(Process.myUserHandle()))
        doReturn(appInfoInstalled)
            .whenever(launcherApps)
            .getApplicationInfo(eq(BING_PKG), anyInt(), eq(Process.myUserHandle()))
        doReturn(installSessionTracker).whenever(installSessionHelper).registerInstallTracker(any())
    }

    @Test
    fun testOsePkgIsNull() {
        doReturn(null).whenever(settingsObserver).getValue()

        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }

        assertThat(oseManager.oseInfo.value.pkg).isEqualTo(BING_PKG)
        verifyNoInteractions(installSessionHelper)
    }

    @Test
    fun `overlay null when supported_overlay_apps is empty`() {
        doReturn(BING_PKG).whenever(settingsObserver).getValue()

        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }

        assertThat(oseManager.oseInfo.value.pkg).isEqualTo(BING_PKG)
        assertThat(oseManager.oseInfo.value.overlayTarget).isNull()
    }

    @Test
    fun `overlay fallback to first entry in supported_overlay_apps for unsupported overlay`() {
        doReturn(BING_PKG).whenever(settingsObserver).getValue()
        context.packageManager.mockOverlayResolution(DUCK_PKG, mockResolverInfo(DUCK_PKG))
        doReturn(arrayOf(DUCK_PKG, GOOGLE_PACKAGE))
            .whenever(res)
            .getStringArray(eq(R.array.supported_overlay_apps))

        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }

        assertThat(oseManager.oseInfo.value.pkg).isEqualTo(BING_PKG)
        assertThat(oseManager.oseInfo.value.overlayTarget).isNotNull()
        assertThat(oseManager.oseInfo.value.overlayTarget?.packageName).isEqualTo(DUCK_PKG)
    }

    @Test
    fun `overlay matches the valid supported overlay`() {
        doReturn(DUCK_PKG).whenever(settingsObserver).getValue()
        context.packageManager.mockOverlayResolution(DUCK_PKG, mockResolverInfo(DUCK_PKG))
        doReturn(arrayOf(GOOGLE_PACKAGE, DUCK_PKG, BING_PKG))
            .whenever(res)
            .getStringArray(eq(R.array.supported_overlay_apps))

        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }

        assertThat(oseManager.oseInfo.value.pkg).isEqualTo(DUCK_PKG)
        assertThat(oseManager.oseInfo.value.overlayTarget).isNotNull()
        assertThat(oseManager.oseInfo.value.overlayTarget?.packageName).isEqualTo(DUCK_PKG)
    }

    @Test
    fun `callback invoked when OseInfo changes`() {
        doReturn(BING_PKG).whenever(settingsObserver).getValue()

        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }
        assertThat(oseManager.oseInfo.value.pkg).isEqualTo(BING_PKG)
        Mockito.verify(mockCallback, times(1)).invoke(any())

        // OSE Package unchanged.
        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }
        Mockito.verify(mockCallback, times(1)).invoke(any())

        // Change the OSE package
        doReturn(DUCK_PKG).whenever(settingsObserver).getValue()
        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }
        Mockito.verify(mockCallback, times(2)).invoke(any())
        assertThat(oseManager.oseInfo.value.pkg).isEqualTo(DUCK_PKG)
    }

    @Test
    fun `OseInfo defaults to globalSearchPackage when ose package is not installed`() {
        doReturn(GOOGLE_PACKAGE).whenever(settingsObserver).getValue()
        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }
        assertThat(oseManager.oseInfo.value.pkg).isEqualTo(GOOGLE_PACKAGE)
        Mockito.verify(mockCallback, times(1)).invoke(any())

        val appInfoNotInstalled = ApplicationInfo()
        doReturn(appInfoNotInstalled)
            .whenever(launcherApps)
            .getApplicationInfo(eq(DUCK_PKG), anyInt(), eq(Process.myUserHandle()))
        // Change the OSE package to not installed package
        doReturn(DUCK_PKG).whenever(settingsObserver).getValue()

        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }

        // OseInfo defaults to global search package
        assertThat(oseManager.oseInfo.value.pkg).isEqualTo(BING_PKG)
        Mockito.verify(mockCallback, times(2)).invoke(any())
    }

    @Test
    fun `OseInfo defaults to globalSearchPackage when ose package with no active install session`() {
        doReturn(GOOGLE_PACKAGE).whenever(settingsObserver).getValue()
        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }
        assertThat(oseManager.oseInfo.value.pkg).isEqualTo(GOOGLE_PACKAGE)
        Mockito.verify(mockCallback, times(1)).invoke(any())

        val appInfoNotInstalled = ApplicationInfo()
        doReturn(appInfoNotInstalled)
            .whenever(launcherApps)
            .getApplicationInfo(eq(DUCK_PKG), anyInt(), eq(Process.myUserHandle()))
        // No active install session
        doReturn(null)
            .whenever(installSessionHelper)
            .getActiveSessionInfo(eq(Process.myUserHandle()), eq(DUCK_PKG))
        // Change the OSE package to not installed package and no active install session
        doReturn(DUCK_PKG).whenever(settingsObserver).getValue()

        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }

        // OseInfo defaults to global search package
        assertThat(oseManager.oseInfo.value.pkg).isEqualTo(BING_PKG)
        Mockito.verify(mockCallback, times(2)).invoke(any())
    }

    @Test
    fun `register to installSessionTracker when ose package has active install session`() {
        val appInfoNotInstalled = ApplicationInfo()
        doReturn(appInfoNotInstalled)
            .whenever(launcherApps)
            .getApplicationInfo(eq(DUCK_PKG), anyInt(), eq(Process.myUserHandle()))
        // Active install session
        doReturn(mockInstallSessionInfo)
            .whenever(installSessionHelper)
            .getActiveSessionInfo(eq(Process.myUserHandle()), eq(DUCK_PKG))
        doReturn(true).whenever(installSessionHelper).verifySessionInfo(eq(mockInstallSessionInfo))
        // Change the OSE package to not installed package and no active install session
        doReturn(DUCK_PKG).whenever(settingsObserver).getValue()

        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }

        verify(installSessionHelper).registerInstallTracker(any())
        // OseInfo set to OseSettingsValue since there is active session
        assertThat(oseManager.oseInfo.value.pkg).isEqualTo(DUCK_PKG)
        assertThat(oseManager.oseInfo.value.installPending).isTrue()
    }

    @Test
    fun `callback invoked when ose package install session succeeds`() {
        val appInfoNotInstalled = ApplicationInfo()
        doReturn(appInfoNotInstalled)
            .whenever(launcherApps)
            .getApplicationInfo(eq(DUCK_PKG), anyInt(), eq(Process.myUserHandle()))
        // Active install session
        doReturn(mockInstallSessionInfo)
            .whenever(installSessionHelper)
            .getActiveSessionInfo(eq(Process.myUserHandle()), eq(DUCK_PKG))
        doReturn(true).whenever(installSessionHelper).verifySessionInfo(eq(mockInstallSessionInfo))
        // Change the OSE package and it has active install session
        doReturn(DUCK_PKG).whenever(settingsObserver).getValue()

        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }

        verify(installSessionHelper).registerInstallTracker(sessionTrackerCaptor.capture())

        doReturn(appInfoInstalled)
            .whenever(launcherApps)
            .getApplicationInfo(eq(DUCK_PKG), anyInt(), eq(Process.myUserHandle()))
        val packageInstalledInfo =
            PackageInstallInfo(DUCK_PKG, STATUS_INSTALLED, 100, Process.myUserHandle())
        sessionTrackerCaptor.firstValue.onPackageStateChanged(packageInstalledInfo)

        UI_HELPER_EXECUTOR.submit {}.get()
        verify(installSessionTracker).close()
        // OseInfo changes after ose package is installed
        assertThat(oseManager.oseInfo.value.pkg).isEqualTo(DUCK_PKG)
        assertThat(oseManager.oseInfo.value.installPending).isFalse()
        Mockito.verify(mockCallback, times(2)).invoke(any())
    }

    @Test
    fun `callback invoked when ose package install session fails`() {
        val appInfoNotInstalled = ApplicationInfo()
        doReturn(appInfoNotInstalled)
            .whenever(launcherApps)
            .getApplicationInfo(eq(DUCK_PKG), anyInt(), eq(Process.myUserHandle()))
        // Active install session
        doReturn(mockInstallSessionInfo)
            .whenever(installSessionHelper)
            .getActiveSessionInfo(eq(Process.myUserHandle()), eq(DUCK_PKG))
        doReturn(true).whenever(installSessionHelper).verifySessionInfo(eq(mockInstallSessionInfo))
        // Change the OSE package and it has active install session
        doReturn(DUCK_PKG).whenever(settingsObserver).getValue()

        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }

        // OseInfo set to oseSettingsValue since there is active install session
        assertThat(oseManager.oseInfo.value.pkg).isEqualTo(DUCK_PKG)
        verify(installSessionHelper).registerInstallTracker(sessionTrackerCaptor.capture())

        // Session failed, so there is no active session.
        doReturn(null)
            .whenever(installSessionHelper)
            .getActiveSessionInfo(eq(Process.myUserHandle()), eq(DUCK_PKG))

        sessionTrackerCaptor.firstValue.onSessionFailure(DUCK_PKG, Process.myUserHandle())
        UI_HELPER_EXECUTOR.submit {}.get()

        verify(installSessionTracker, times(1)).close()
        verify(installSessionHelper, times(2)).registerInstallTracker(any())
        // ReloadOse is called and OseInfo fallback to defaultSearchPackage since OsePackage
        // installation failed
        assertThat(oseManager.oseInfo.value.pkg).isEqualTo(BING_PKG)
        assertThat(oseManager.oseInfo.value.installPending).isFalse()
        // callback invoked
        Mockito.verify(mockCallback, times(2)).invoke(any())
    }

    @Test
    fun `Multiple package install sessions and ose changes multiple times `() {
        val appInfoNotInstalled = ApplicationInfo()
        doReturn(appInfoNotInstalled)
            .whenever(launcherApps)
            .getApplicationInfo(eq(DUCK_PKG), anyInt(), eq(Process.myUserHandle()))
        doReturn(appInfoNotInstalled)
            .whenever(launcherApps)
            .getApplicationInfo(eq(GOOGLE_PACKAGE), anyInt(), eq(Process.myUserHandle()))
        // Active install sessions
        doReturn(mockInstallSessionInfo)
            .whenever(installSessionHelper)
            .getActiveSessionInfo(eq(Process.myUserHandle()), eq(DUCK_PKG))
        doReturn(mockInstallSessionInfo)
            .whenever(installSessionHelper)
            .getActiveSessionInfo(eq(Process.myUserHandle()), eq(GOOGLE_PACKAGE))
        doReturn(true).whenever(installSessionHelper).verifySessionInfo(eq(mockInstallSessionInfo))

        val tracker1: InstallSessionTracker = mock()
        val tracker2: InstallSessionTracker = mock()
        val tracker3: InstallSessionTracker = mock()
        doReturn(tracker1).whenever(installSessionHelper).registerInstallTracker(any())
        // Change the OSE package and it has active install session
        doReturn(DUCK_PKG).whenever(settingsObserver).getValue()
        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }
        UI_HELPER_EXECUTOR.submit {}.get()
        verify(installSessionHelper).registerInstallTracker(sessionTrackerCaptor.capture())
        assertThat(sessionTrackerCaptor.firstValue.osePackage)
            .isEqualTo(oseManager.oseInfo.value.pkg)
        assertThat(oseManager.oseInfo.value.installPending).isTrue()
        assertThat(oseManager.tracker).isEqualTo(tracker1)

        // Change the OSE package and it has active install session
        doReturn(GOOGLE_PACKAGE).whenever(settingsObserver).getValue()
        doReturn(tracker2).whenever(installSessionHelper).registerInstallTracker(any())
        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }
        UI_HELPER_EXECUTOR.submit {}.get()
        verify(tracker1).close()
        verify(installSessionHelper, times(2))
            .registerInstallTracker(sessionTrackerCaptor.capture())
        assertThat(sessionTrackerCaptor.lastValue.osePackage)
            .isEqualTo(oseManager.oseInfo.value.pkg)
        assertThat(oseManager.oseInfo.value.installPending).isTrue()
        assertThat(oseManager.tracker).isEqualTo(tracker2)

        doReturn(tracker3).whenever(installSessionHelper).registerInstallTracker(any())
        // Change the OSE package and it has active install session
        doReturn(DUCK_PKG).whenever(settingsObserver).getValue()
        assertThat(oseManager.tracker).isEqualTo(tracker2)
        TestUtil.runOnExecutorSync(UI_HELPER_EXECUTOR) { oseManager.reloadOse() }
        UI_HELPER_EXECUTOR.submit {}.get()
        verify(tracker2).close()
        verify(installSessionHelper, times(3))
            .registerInstallTracker(sessionTrackerCaptor.capture())
        assertThat(sessionTrackerCaptor.lastValue.osePackage)
            .isEqualTo(oseManager.oseInfo.value.pkg)
        assertThat(oseManager.oseInfo.value.installPending).isTrue()
        assertThat(oseManager.tracker).isEqualTo(tracker3)
    }

    private fun mockResolverInfo(pkg: String) =
        ResolveInfo().apply {
            activityInfo =
                ActivityInfo().apply {
                    packageName = pkg
                    name = "test"
                }
        }

    private fun PackageManager.mockOverlayResolution(pkg: String, info: ResolveInfo?) {
        doReturn(info)
            .whenever(this)
            .resolveActivity(
                argThat<Intent> { OVERLAY_ACTION == action && pkg == getPackage() },
                eq(0),
            )
    }

    companion object {
        private const val DUCK_PKG = "com.duckduckgo"
        private const val BING_PKG = "com.bing"
        private const val GOOGLE_PACKAGE = "com.google"
    }
}

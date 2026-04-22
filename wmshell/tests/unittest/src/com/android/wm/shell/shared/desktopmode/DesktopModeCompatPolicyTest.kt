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

package com.android.wm.shell.shared.desktopmode

import android.Manifest.permission.SYSTEM_ALERT_WINDOW
import android.app.TaskInfo
import android.compat.testing.PlatformCompatChangeRule
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Process
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.internal.R
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFullscreenTask
import com.android.wm.shell.windowdecor.DesktopModeWindowDecorViewModelTestsBase.Companion.HOME_LAUNCHER_PACKAGE_NAME
import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [@link DesktopModeCompatPolicy].
 *
 * Build/Install/Run: atest WMShellUnitTests:DesktopModeCompatPolicyTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class DesktopModeCompatPolicyTest : ShellTestCase() {
    @get:Rule val compatRule = PlatformCompatChangeRule()
    private lateinit var desktopModeCompatPolicy: DesktopModeCompatPolicy
    private val packageManager: PackageManager = mock()
    private val homeActivities = ComponentName(HOME_LAUNCHER_PACKAGE_NAME, /* class */ "")
    private val baseActivityTest = ComponentName("com.test.dummypackage", "TestClass")

    @Before
    fun setUp() {
        desktopModeCompatPolicy = DesktopModeCompatPolicy(mContext)
        whenever(packageManager.getHomeActivities(any())).thenReturn(homeActivities)
        mContext.setMockPackageManager(packageManager)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_MODALS_FULLSCREEN_WITH_PERMISSION,
        Flags.FLAG_ENABLE_MODALS_FULLSCREEN_WITH_PLATFORM_SIGNATURE)
    fun testIsTopActivityExemptFromDesktopWindowing_onlyTransparentActivitiesInStack() {
        assertTrue(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                    .apply {
                        isActivityStackTransparent = true
                        isTopActivityNoDisplay = false
                        numActivities = 1
                        baseActivity = baseActivityTest
                    }))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MODALS_FULLSCREEN_WITH_PLATFORM_SIGNATURE)
    @DisableFlags(Flags.FLAG_ENABLE_MODALS_FULLSCREEN_WITH_PERMISSION)
    fun testIsTopActivityExemptWithPlatformSignature_onlyTransparentActivitiesInStack() {
        assertTrue(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                .apply {
                    isActivityStackTransparent = true
                    isTopActivityNoDisplay = false
                    numActivities = 1
                    topActivityInfo = ActivityInfo().apply {
                        applicationInfo = ApplicationInfo().apply {
                            privateFlags = ApplicationInfo.PRIVATE_FLAG_SIGNED_WITH_PLATFORM_KEY
                        }
                    }
                    baseActivity = baseActivityTest
                }))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MODALS_FULLSCREEN_WITH_PLATFORM_SIGNATURE)
    @DisableFlags(Flags.FLAG_ENABLE_MODALS_FULLSCREEN_WITH_PERMISSION)
    fun testIsTopActivityExemptWithoutPlatformSignature_onlyTransparentActivitiesInStack() {
        assertFalse(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                .apply {
                    isActivityStackTransparent = true
                    isTopActivityNoDisplay = false
                    numActivities = 1
                    topActivityInfo = ActivityInfo().apply {
                        applicationInfo = ApplicationInfo().apply {
                            privateFlags = 0
                        }
                    }
                    baseActivity = baseActivityTest
                }))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MODALS_FULLSCREEN_WITH_PERMISSION)
    fun testIsTopActivityExemptWithPermission_onlyTransparentActivitiesInStack() {
        allowOverlayPermissionForAllUsers(arrayOf(SYSTEM_ALERT_WINDOW))
        assertTrue(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                .apply {
                    isActivityStackTransparent = true
                    isTopActivityNoDisplay = false
                    numActivities = 1
                    baseActivity = baseActivityTest
                }))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MODALS_FULLSCREEN_WITH_PERMISSION)
    fun testIsTopActivityExemptWithNoPermission_onlyTransparentActivitiesInStack() {
        allowOverlayPermissionForAllUsers(arrayOf())
        assertFalse(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                .apply {
                    isActivityStackTransparent = true
                    isTopActivityNoDisplay = false
                    numActivities = 1
                    baseActivity = baseActivityTest
                }))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MODALS_FULLSCREEN_WITH_PERMISSION)
    fun testIsTopActivityExemptCachedPermissionCheckIsUsed() {
        allowOverlayPermissionForAllUsers(arrayOf())
        assertFalse(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                .apply {
                    isActivityStackTransparent = true
                    isTopActivityNoDisplay = false
                    numActivities = 1
                    baseActivity = baseActivityTest
                    userId = 10
                }))
        assertFalse(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                .apply {
                    isActivityStackTransparent = true
                    isTopActivityNoDisplay = false
                    numActivities = 1
                    baseActivity = baseActivityTest
                    userId = 10
                }))
        assertFalse(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                .apply {
                    isActivityStackTransparent = true
                    isTopActivityNoDisplay = false
                    numActivities = 1
                    baseActivity = baseActivityTest
                    userId = 0
                }))
        verify(packageManager, times(1)).getPackageInfoAsUser(
            eq("com.test.dummypackage"),
            eq(PackageManager.GET_PERMISSIONS),
            eq(10)
        )
        verify(packageManager, times(1)).getPackageInfoAsUser(
            eq("com.test.dummypackage"),
            eq(PackageManager.GET_PERMISSIONS),
            eq(0)
        )
    }

    @Test
    fun testIsTopActivityExemptFromDesktopWindowing_noActivitiesInStack() {
        assertFalse(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                .apply {
                    isActivityStackTransparent = true
                    isTopActivityNoDisplay = false
                    numActivities = 0
                }))
    }

    @Test
    fun testIsTopActivityExemptFromDesktopWindowing_nonTransparentActivitiesInStack() {
        assertFalse(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                .apply {
                    isActivityStackTransparent = false
                    isTopActivityNoDisplay = false
                    numActivities = 1
                }))
    }

    @Test
    fun testIsTopActivityExemptFromDesktopWindowing_transparentActivityStack_notDisplayed() {
        assertFalse(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                .apply {
                    isActivityStackTransparent = true
                    isTopActivityNoDisplay = true
                    numActivities = 1
                }))
    }

    @Test
    fun testIsTopActivityExemptFromDesktopWindowing_systemUiTask() {
        val systemUIPackageName = context.resources.getString(R.string.config_systemUi)
        val baseComponent = ComponentName(systemUIPackageName, /* class */ "")
        assertTrue(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                    .apply {
                        baseActivity = baseComponent
                        isTopActivityNoDisplay = false
                    }))
    }

    @Test
    fun testIsTopActivityExemptFromDesktopWindowing_systemUiTask_notDisplayed() {
        val systemUIPackageName = context.resources.getString(R.string.config_systemUi)
        val baseComponent = ComponentName(systemUIPackageName, /* class */ "")
        assertFalse(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                .apply {
                    baseActivity = baseComponent
                    isTopActivityNoDisplay = true
                }))
    }

    @Test
    fun testIsTopActivityExemptFromDesktopWindowing_defaultHomePackage() {
        assertTrue(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                .apply {
                    baseActivity = homeActivities
                    isTopActivityNoDisplay = false
                }))
    }

    @Test
    fun testIsTopActivityExemptFromDesktopWindowing_defaultHomePackage_notDisplayed() {
        assertFalse(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                .apply {
                    baseActivity = homeActivities
                    isTopActivityNoDisplay = true
                }))
    }

    @Test
    fun testIsTopActivityExemptFromDesktopWindowing_defaultHomePackage_notYetAvailable() {
        val emptyHomeActivities: ComponentName = mock()
        mContext.setMockPackageManager(packageManager)

        whenever(emptyHomeActivities.packageName).thenReturn(null)
        whenever(packageManager.getHomeActivities(any())).thenReturn(emptyHomeActivities)

        assertTrue(desktopModeCompatPolicy.isTopActivityExemptFromDesktopWindowing(
            createFreeformTask(/* displayId */ 0)
                .apply {
                    baseActivity = baseActivityTest
                    isTopActivityNoDisplay = false
                }))
    }

    @Test
    fun testShouldDisableDesktopEntryPoints_noDisplayActivity() {
        assertTrue(desktopModeCompatPolicy.shouldDisableDesktopEntryPoints(
            createFullscreenTask(/* displayId */ 0)
                .apply {
                    isTopActivityNoDisplay = true
                }))
    }

    @Test
    fun testShouldDisableDesktopEntryPoints_transparentTask() {
        assertTrue(desktopModeCompatPolicy.shouldDisableDesktopEntryPoints(
            createFullscreenTask(/* displayId */ 0)
                .apply {
                    isActivityStackTransparent = true
                    numActivities = 1
                }))
    }

    @Test
    fun testShouldDisableDesktopEntryPoints_defaultHomePackage() {
        assertTrue(desktopModeCompatPolicy.shouldDisableDesktopEntryPoints(
            createFullscreenTask(/* displayId */ 0)
                .apply {
                    baseActivity = homeActivities
                }))
    }

    @Test
    fun testShouldDisableDesktopEntryPoints_defaultHomePackage_notYetAvailable() {
        val emptyHomeActivities: ComponentName = mock()
        mContext.setMockPackageManager(packageManager)

        whenever(emptyHomeActivities.packageName).thenReturn(null)
        whenever(packageManager.getHomeActivities(any())).thenReturn(emptyHomeActivities)

        assertTrue(desktopModeCompatPolicy.shouldDisableDesktopEntryPoints(
            createFullscreenTask(/* displayId */ 0)))
    }

    @Test
    fun testShouldDisableDesktopEntryPoints_systemUiTask() {
        val systemUIPackageName = context.resources.getString(R.string.config_systemUi)
        val baseComponent = ComponentName(systemUIPackageName, /* class */ "")
        assertTrue(desktopModeCompatPolicy.shouldDisableDesktopEntryPoints(
            createFreeformTask(/* displayId */ 0)
                .apply {
                    baseActivity = baseComponent
                }))
    }

    @Test
    @EnableFlags(Flags.FLAG_EXCLUDE_CAPTION_FROM_APP_BOUNDS)
    @DisableCompatChanges(ActivityInfo.INSETS_DECOUPLED_CONFIGURATION_ENFORCED)
    fun testShouldExcludeCaptionFromAppBounds_resizeable_false() {
        assertFalse(desktopModeCompatPolicy.shouldExcludeCaptionFromAppBounds(
            setUpFreeformTask().apply { isResizeable = true })
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_EXCLUDE_CAPTION_FROM_APP_BOUNDS)
    @DisableCompatChanges(ActivityInfo.INSETS_DECOUPLED_CONFIGURATION_ENFORCED)
    fun testShouldExcludeCaptionFromAppBounds_nonResizeable_true() {
        assertTrue(desktopModeCompatPolicy.shouldExcludeCaptionFromAppBounds(
            setUpFreeformTask().apply { isResizeable = false })
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_EXCLUDE_CAPTION_FROM_APP_BOUNDS)
    @EnableCompatChanges(ActivityInfo.INSETS_DECOUPLED_CONFIGURATION_ENFORCED)
    fun testShouldExcludeCaptionFromAppBounds_nonResizeable_sdk35_false() {
        assertFalse(desktopModeCompatPolicy.shouldExcludeCaptionFromAppBounds(
            setUpFreeformTask().apply { isResizeable = false })
        )
    }


    @Test
    @EnableFlags(Flags.FLAG_EXCLUDE_CAPTION_FROM_APP_BOUNDS)
    @DisableCompatChanges(ActivityInfo.INSETS_DECOUPLED_CONFIGURATION_ENFORCED)
    @EnableCompatChanges(ActivityInfo.OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS)
    fun testShouldExcludeCaptionFromAppBounds_resizeable_overridden_true() {
        assertTrue(desktopModeCompatPolicy.shouldExcludeCaptionFromAppBounds(
            setUpFreeformTask().apply { isResizeable = true })
        )
    }

    fun setUpFreeformTask(): TaskInfo =
        createFreeformTask().apply {
            val componentName =
                ComponentName.createRelative(
                    mContext,
                    DesktopModeCompatPolicyTest::class.java.simpleName
                )
            baseActivity = componentName
            topActivityInfo = ActivityInfo().apply {
                applicationInfo = ApplicationInfo().apply {
                    packageName = componentName.packageName
                    uid = Process.myUid()
                }
            }
        }

    fun allowOverlayPermissionForAllUsers(permissions: Array<String>) {
        val packageInfo = mock<PackageInfo>()
        packageInfo.requestedPermissions = permissions
        whenever(
            packageManager.getPackageInfoAsUser(
                anyString(),
                eq(PackageManager.GET_PERMISSIONS),
                anyInt(),
            )
        ).thenReturn(packageInfo)
    }
}

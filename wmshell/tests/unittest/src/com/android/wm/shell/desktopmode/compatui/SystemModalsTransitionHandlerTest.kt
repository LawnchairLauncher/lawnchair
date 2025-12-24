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

package com.android.wm.shell.desktopmode.compatui

import android.Manifest.permission.SYSTEM_ALERT_WINDOW
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Binder
import android.testing.AndroidTestingRunner
import android.testing.TestableContext
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_OPEN
import androidx.test.filters.SmallTest
import com.android.internal.policy.DesktopModeCompatPolicy
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFullscreenTask
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFullscreenTaskBuilder
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createSystemModalTask
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createSystemModalTaskBuilder
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createSystemModalTaskWithBaseActivity
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.DesktopWallpaperActivity
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.TransitionInfoBuilder
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.DesktopModeWindowDecorViewModelTestsBase.Companion.HOME_LAUNCHER_PACKAGE_NAME
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for {@link SystemModalsTransitionHandler} Usage: atest
 * WMShellUnitTests:SystemModalsTransitionHandlerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class SystemModalsTransitionHandlerTest : ShellTestCase() {
    private val mainExecutor = mock<ShellExecutor>()
    private val animExecutor = mock<ShellExecutor>()
    private val shellInit = mock<ShellInit>()
    private val transitions = mock<Transitions>()
    private val desktopUserRepositories = mock<DesktopUserRepositories>()
    private val desktopRepository = mock<DesktopRepository>()
    private val startT = mock<SurfaceControl.Transaction>()
    private val finishT = mock<SurfaceControl.Transaction>()
    private val packageManager = mock<PackageManager>()
    private val componentName = mock<ComponentName>()

    private lateinit var spyContext: TestableContext
    private lateinit var transitionHandler: SystemModalsTransitionHandler
    private lateinit var desktopModeCompatPolicy: DesktopModeCompatPolicy

    @Before
    fun setUp() {
        spyContext = spy(mContext)
        // Simulate having one Desktop task so that we see Desktop Mode as active
        whenever(desktopUserRepositories.current).thenReturn(desktopRepository)
        whenever(desktopRepository.isAnyDeskActive(anyInt())).thenReturn(true)
        whenever(spyContext.packageManager).thenReturn(packageManager)
        whenever(componentName.packageName).thenReturn(HOME_LAUNCHER_PACKAGE_NAME)
        whenever(packageManager.getHomeActivities(ArrayList())).thenReturn(componentName)
        desktopModeCompatPolicy = DesktopModeCompatPolicy(spyContext)
        transitionHandler = createTransitionHandler()
        allowOverlayPermissionForAllUsers(arrayOf(SYSTEM_ALERT_WINDOW))
    }

    private fun createTransitionHandler() =
        SystemModalsTransitionHandler(
            context,
            mainExecutor,
            animExecutor,
            shellInit,
            transitions,
            desktopUserRepositories,
            desktopModeCompatPolicy,
        )

    @Test
    fun instantiate_addsInitCallback() {
        verify(shellInit).addInitCallback(any(), any<SystemModalsTransitionHandler>())
    }

    @Test
    fun startAnimation_desktopNotActive_doesNotAnimate() {
        whenever(desktopUserRepositories.current.isAnyDeskActive(anyInt())).thenReturn(true)
        val info =
            TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN, createSystemModalTaskWithBaseActivity())
                .build()

        assertThat(transitionHandler.startAnimation(Binder(), info, startT, finishT) {}).isTrue()
    }

    @Test
    fun startAnimation_launchingSystemModal_animates() {
        val info =
            TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN, createSystemModalTaskWithBaseActivity())
                .build()

        assertThat(transitionHandler.startAnimation(Binder(), info, startT, finishT) {}).isTrue()
    }

    @Test
    fun startAnimation_nonLaunchingSystemModal_doesNotAnimate() {
        val info =
            TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_CHANGE, createSystemModalTask())
                .build()

        assertThat(transitionHandler.startAnimation(Binder(), info, startT, finishT) {}).isFalse()
    }

    @Test
    fun startAnimation_launchingWallpaperTask_doesNotAnimate() {
        val wallpaperTask =
            createSystemModalTaskBuilder().setBaseIntent(createWallpaperIntent()).build()
        val info =
            TransitionInfoBuilder(TRANSIT_OPEN).addChange(TRANSIT_OPEN, wallpaperTask).build()

        assertThat(transitionHandler.startAnimation(Binder(), info, startT, finishT) {}).isFalse()
    }

    private fun createWallpaperIntent() =
        Intent().apply { setComponent(DesktopWallpaperActivity.wallpaperActivityComponent) }

    @Test
    fun startAnimation_launchingFullscreenTask_doesNotAnimate() {
        val info =
            TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN, createFullscreenTask())
                .build()

        assertThat(transitionHandler.startAnimation(Binder(), info, startT, finishT) {}).isFalse()
    }

    @Test
    fun startAnimation_closingSystemModal_animates() {
        val info =
            TransitionInfoBuilder(TRANSIT_CLOSE)
                .addChange(TRANSIT_CLOSE, createSystemModalTaskWithBaseActivity())
                .build()

        assertThat(transitionHandler.startAnimation(Binder(), info, startT, finishT) {}).isTrue()
    }

    @Test
    fun startAnimation_closingFullscreenTask_doesNotAnimate() {
        val info =
            TransitionInfoBuilder(TRANSIT_CLOSE)
                .addChange(TRANSIT_CLOSE, createFullscreenTask())
                .build()

        assertThat(transitionHandler.startAnimation(Binder(), info, startT, finishT) {}).isFalse()
    }

    @Test
    fun startAnimation_closingPreviouslyLaunchedSystemModal_animates() {
        val systemModalTask = createSystemModalTaskWithBaseActivity()
        val nonModalSystemModalTask =
            createFullscreenTaskBuilder().setTaskId(systemModalTask.taskId).build()
        val launchInfo =
            TransitionInfoBuilder(TRANSIT_OPEN).addChange(TRANSIT_OPEN, systemModalTask).build()
        transitionHandler.startAnimation(Binder(), launchInfo, startT, finishT) {}
        val closeInfo =
            TransitionInfoBuilder(TRANSIT_CLOSE)
                .addChange(TRANSIT_CLOSE, nonModalSystemModalTask)
                .build()

        assertThat(transitionHandler.startAnimation(Binder(), closeInfo, startT, finishT) {})
            .isTrue()
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
            )
            .thenReturn(packageInfo)
    }
}

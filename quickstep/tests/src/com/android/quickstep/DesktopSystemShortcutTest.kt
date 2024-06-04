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

package com.android.quickstep

import android.content.ComponentName
import android.content.Intent
import android.platform.test.flag.junit.SetFlagsRule
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession
import com.android.dx.mockito.inline.extended.StaticMockitoSession
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AbstractFloatingViewHelper
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.TransformingTouchDelegate
import com.android.quickstep.TaskOverlayFactory.TaskOverlay
import com.android.quickstep.views.LauncherRecentsView
import com.android.quickstep.views.TaskThumbnailViewDeprecated
import com.android.quickstep.views.TaskView
import com.android.quickstep.views.TaskViewIcon
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.android.window.flags.Flags
import com.android.wm.shell.common.desktopmode.DesktopModeTransitionSource
import com.android.wm.shell.shared.DesktopModeStatus
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/** Test for DesktopSystemShortcut */
class DesktopSystemShortcutTest {

    @get:Rule val setFlagsRule = SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT)

    private val launcher: QuickstepLauncher = mock()
    private val statsLogManager: StatsLogManager = mock()
    private val statsLogger: StatsLogManager.StatsLogger = mock()
    private val recentsView: LauncherRecentsView = mock()
    private val taskView: TaskView = mock()
    private val workspaceItemInfo: WorkspaceItemInfo = mock()
    private val abstractFloatingViewHelper: AbstractFloatingViewHelper = mock()
    private val thumbnailViewDeprecated: TaskThumbnailViewDeprecated = mock()
    private val iconView: TaskViewIcon = mock()
    private val transformingTouchDelegate: TransformingTouchDelegate = mock()
    private val factory: TaskShortcutFactory =
        DesktopSystemShortcut.createFactory(abstractFloatingViewHelper)
    private val overlayFactory: TaskOverlayFactory = mock()
    private val overlay: TaskOverlay<*> = mock()

    private lateinit var mockitoSession: StaticMockitoSession

    @Before
    fun setUp() {
        mockitoSession =
            mockitoSession()
                .strictness(Strictness.LENIENT)
                .spyStatic(DesktopModeStatus::class.java)
                .startMocking()
        ExtendedMockito.doReturn(true).`when` { DesktopModeStatus.enforceDeviceRestrictions() }
        ExtendedMockito.doReturn(true).`when` { DesktopModeStatus.isDesktopModeSupported(any()) }
        whenever(overlayFactory.createOverlay(any())).thenReturn(overlay)
    }

    @After
    fun tearDown() {
        mockitoSession.finishMocking()
    }

    @Test
    fun createDesktopTaskShortcutFactory_desktopModeDisabled() {
        setFlagsRule.disableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)

        val task =
            Task(TaskKey(1, 0, Intent(), ComponentName("", ""), 0, 2000)).apply {
                isDockable = true
            }
        val taskContainer = createTaskContainer(task)

        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).isNull()
    }

    @Test
    fun createDesktopTaskShortcutFactory_desktopModeEnabled_DeviceNotSupported() {
        setFlagsRule.enableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
        ExtendedMockito.doReturn(false).`when` { DesktopModeStatus.isDesktopModeSupported(any()) }

        val taskContainer = createTaskContainer(createTask())

        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).isNull()
    }

    @Test
    fun createDesktopTaskShortcutFactory_desktopModeEnabled_DeviceNotSupported_OverrideEnabled() {
        setFlagsRule.enableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
        ExtendedMockito.doReturn(false).`when` { DesktopModeStatus.isDesktopModeSupported(any()) }
        ExtendedMockito.doReturn(false).`when` { DesktopModeStatus.enforceDeviceRestrictions() }

        val taskContainer = spy(createTaskContainer(createTask()))
        doReturn(workspaceItemInfo).whenever(taskContainer).itemInfo

        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).isNotNull()
    }

    @Test
    fun createDesktopTaskShortcutFactory_undockable() {
        setFlagsRule.enableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)

        val unDockableTask = createTask().apply { isDockable = false }
        val taskContainer = createTaskContainer(unDockableTask)

        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).isNull()
    }

    @Test
    fun desktopSystemShortcutClicked() {
        setFlagsRule.enableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)

        val task = createTask()
        val taskContainer = spy(createTaskContainer(task))

        whenever(launcher.getOverviewPanel<LauncherRecentsView>()).thenReturn(recentsView)
        whenever(launcher.statsLogManager).thenReturn(statsLogManager)
        whenever(statsLogManager.logger()).thenReturn(statsLogger)
        whenever(statsLogger.withItemInfo(any())).thenReturn(statsLogger)
        whenever(recentsView.moveTaskToDesktop(any(), any(), any())).thenAnswer {
            val successCallback = it.getArgument<Runnable>(2)
            successCallback.run()
        }
        doReturn(workspaceItemInfo).whenever(taskContainer).itemInfo

        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).hasSize(1)
        assertThat(shortcuts!!.first()).isInstanceOf(DesktopSystemShortcut::class.java)

        val desktopShortcut = shortcuts.first() as DesktopSystemShortcut

        desktopShortcut.onClick(taskView)

        val allTypesExceptRebindSafe =
            AbstractFloatingView.TYPE_ALL and AbstractFloatingView.TYPE_REBIND_SAFE.inv()
        verify(abstractFloatingViewHelper).closeOpenViews(launcher, true, allTypesExceptRebindSafe)
        verify(recentsView)
            .moveTaskToDesktop(
                eq(taskContainer),
                eq(DesktopModeTransitionSource.APP_FROM_OVERVIEW),
                any()
            )
        verify(statsLogger).withItemInfo(workspaceItemInfo)
        verify(statsLogger).log(LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_DESKTOP_TAP)
    }

    private fun createTask(): Task {
        return Task(TaskKey(1, 0, Intent(), ComponentName("", ""), 0, 2000)).apply {
            isDockable = true
        }
    }

    private fun createTaskContainer(task: Task): TaskView.TaskContainer {
        return taskView.TaskContainer(
            task,
            thumbnailView = null,
            thumbnailViewDeprecated,
            iconView,
            transformingTouchDelegate,
            SplitConfigurationOptions.STAGE_POSITION_UNDEFINED,
            digitalWellBeingToast = null,
            showWindowsView = null,
            overlayFactory
        )
    }
}

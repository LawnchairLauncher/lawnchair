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

import android.Manifest.permission.SYSTEM_ALERT_WINDOW
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.platform.app.InstrumentationRegistry
import com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession
import com.android.dx.mockito.inline.extended.StaticMockitoSession
import com.android.internal.R
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AbstractFloatingViewHelper
import com.android.launcher3.Flags.enableRefactorTaskThumbnail
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.logging.StatsLogManager.LauncherEvent
import com.android.launcher3.model.data.TaskViewItemInfo
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.TransformingTouchDelegate
import com.android.quickstep.TaskOverlayFactory.TaskOverlay
import com.android.quickstep.task.thumbnail.TaskThumbnailView
import com.android.quickstep.views.LauncherRecentsView
import com.android.quickstep.views.RecentsViewContainer
import com.android.quickstep.views.TaskContainer
import com.android.quickstep.views.TaskThumbnailViewDeprecated
import com.android.quickstep.views.TaskView
import com.android.quickstep.views.TaskViewIcon
import com.android.quickstep.views.TaskViewType
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.android.window.flags.Flags
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/** Test for [DesktopSystemShortcut] */
// TODO(b/403558856): Improve test coverage for DesktopModeCompatPolicy integration.
class DesktopSystemShortcutTest {

    @get:Rule val setFlagsRule = SetFlagsRule(SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT)

    private val launcher: RecentsViewContainer = mock()
    private val statsLogManager: StatsLogManager = mock()
    private val statsLogger: StatsLogManager.StatsLogger = mock()
    private val recentsView: LauncherRecentsView = mock()
    private val abstractFloatingViewHelper: AbstractFloatingViewHelper = mock()
    private val overlayFactory: TaskOverlayFactory = mock()
    private val factory: TaskShortcutFactory =
        DesktopSystemShortcut.createFactory(abstractFloatingViewHelper)
    private val context: Context = spy(InstrumentationRegistry.getInstrumentation().targetContext)
    private val taskView: TaskView = createTaskViewMock()

    private lateinit var mockitoSession: StaticMockitoSession

    @Before
    fun setUp() {
        mockitoSession =
            mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(DesktopModeStatus::class.java)
                .startMocking()
        whenever(DesktopModeStatus.canEnterDesktopMode(any())).thenReturn(true)
        whenever(overlayFactory.createOverlay(any())).thenReturn(mock<TaskOverlay<*>>())
        whenever(launcher.asContext()).thenReturn(context)
    }

    @After
    fun tearDown() {
        mockitoSession.finishMocking()
    }

    @Test
    fun createDesktopTaskShortcutFactory_desktopModeDisabled() {
        `when`(DesktopModeStatus.canEnterDesktopMode(any())).thenReturn(false)

        val taskContainer = createTaskContainer(createTask())

        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY)
    @DisableFlags(Flags.FLAG_ENABLE_MODALS_FULLSCREEN_WITH_PERMISSION)
    fun createDesktopTaskShortcutFactory_transparentTask() {
        val baseComponent = ComponentName("", /* class */ "")
        val taskKey =
            TaskKey(
                /* id */ 1,
                /* windowingMode */ 0,
                Intent(),
                baseComponent,
                /* userId */ 0,
                /* lastActiveTime */ 2000,
                DEFAULT_DISPLAY,
                baseComponent,
                /* numActivities */ 1,
                /* isTopActivityNoDisplay */ false,
                /* isActivityStackTransparent */ true,
            )
        val taskContainer = createTaskContainer(Task(taskKey))
        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).isNull()
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY,
        Flags.FLAG_ENABLE_MODALS_FULLSCREEN_WITH_PERMISSION,
    )
    fun createDesktopTaskShortcutFactoryPermissionEnabledAllowed_transparentTask() {
        val packageManager: PackageManager = mock()
        setUpTransparentPermission(packageManager, isAllowed = true)
        val baseComponent = ComponentName("", /* class */ "")
        val taskKey =
            TaskKey(
                /* id */ 1,
                /* windowingMode */ 0,
                Intent(),
                baseComponent,
                /* userId */ 0,
                /* lastActiveTime */ 2000,
                DEFAULT_DISPLAY,
                baseComponent,
                /* numActivities */ 1,
                /* isTopActivityNoDisplay */ false,
                /* isActivityStackTransparent */ true,
            )
        val taskContainer = createTaskContainer(Task(taskKey))
        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).isNull()
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY,
        Flags.FLAG_ENABLE_MODALS_FULLSCREEN_WITH_PERMISSION,
    )
    fun createDesktopTaskShortcutFactoryPermissionEnabledNotAllowed_transparentTask() {
        val packageManager: PackageManager = mock()
        setUpTransparentPermission(packageManager, isAllowed = false)
        val baseComponent = ComponentName("", /* class */ "")
        val homeActivities = ComponentName("defaultHomePackage", /* class */ "")
        whenever(packageManager.getHomeActivities(any())).thenReturn(homeActivities)
        val taskKey =
            TaskKey(
                /* id */ 1,
                /* windowingMode */ 0,
                Intent(),
                baseComponent,
                /* userId */ 0,
                /* lastActiveTime */ 2000,
                DEFAULT_DISPLAY,
                baseComponent,
                /* numActivities */ 1,
                /* isTopActivityNoDisplay */ false,
                /* isActivityStackTransparent */ true,
            )
        val taskContainer = createTaskContainer(Task(taskKey).apply { isDockable = true })
        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).isNotEmpty()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY)
    fun createDesktopTaskShortcutFactory_systemUiTask() {
        val sysUiPackageName: String = context.resources.getString(R.string.config_systemUi)
        val baseComponent = ComponentName(sysUiPackageName, /* class */ "")
        val taskKey =
            TaskKey(
                /* id */ 1,
                /* windowingMode */ 0,
                Intent(),
                baseComponent,
                /* userId */ 0,
                /* lastActiveTime */ 2000,
                DEFAULT_DISPLAY,
                baseComponent,
                /* numActivities */ 1,
                /* isTopActivityNoDisplay */ false,
                /* isActivityStackTransparent */ false,
            )
        val taskContainer = createTaskContainer(Task(taskKey))
        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).isNull()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY)
    fun createDesktopTaskShortcutFactory_defaultHomeTask() {
        val packageManager: PackageManager = mock()
        whenever(context.packageManager).thenReturn(packageManager)
        val homeActivities = ComponentName("defaultHomePackage", /* class */ "")
        whenever(packageManager.getHomeActivities(any())).thenReturn(homeActivities)
        val taskKey =
            TaskKey(
                /* id */ 1,
                /* windowingMode */ 0,
                Intent(),
                homeActivities,
                /* userId */ 0,
                /* lastActiveTime */ 2000,
                DEFAULT_DISPLAY,
                homeActivities,
                /* numActivities */ 1,
                /* isTopActivityNoDisplay */ false,
                /* isActivityStackTransparent */ false,
            )
        val taskContainer = createTaskContainer(Task(taskKey).apply { isDockable = true })
        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).isNull()
    }

    @Test
    fun createDesktopTaskShortcutFactory_undockable() {
        val unDockableTask = createTask().apply { isDockable = false }
        val taskContainer = createTaskContainer(unDockableTask)

        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).isNull()
    }

    @Test
    fun desktopSystemShortcutClicked() {
        val task = createTask()
        val taskContainer = spy(createTaskContainer(task))

        whenever(launcher.getOverviewPanel<LauncherRecentsView>()).thenReturn(recentsView)
        whenever(launcher.statsLogManager).thenReturn(statsLogManager)
        whenever(statsLogManager.logger()).thenReturn(statsLogger)
        whenever(statsLogger.withItemInfo(any())).thenReturn(statsLogger)
        whenever(taskView.context).thenReturn(context)
        whenever(recentsView.moveTaskToDesktop(any(), any(), any())).thenAnswer {
            val successCallback = it.getArgument<Runnable>(2)
            successCallback.run()
        }
        val taskViewItemInfo = mock<TaskViewItemInfo>()
        doReturn(taskViewItemInfo).whenever(taskContainer).itemInfo

        val shortcuts = factory.getShortcuts(launcher, taskContainer)
        assertThat(shortcuts).isNotNull()
        assertThat(shortcuts!!.single()).isInstanceOf(DesktopSystemShortcut::class.java)

        val desktopShortcut = shortcuts.single() as DesktopSystemShortcut

        desktopShortcut.onClick(taskView)

        val allTypesExceptRebindSafe =
            AbstractFloatingView.TYPE_ALL and AbstractFloatingView.TYPE_REBIND_SAFE.inv()
        verify(abstractFloatingViewHelper).closeOpenViews(launcher, true, allTypesExceptRebindSafe)
        verify(recentsView)
            .moveTaskToDesktop(
                eq(taskContainer),
                eq(DesktopModeTransitionSource.APP_FROM_OVERVIEW),
                any(),
            )
        verify(statsLogger).withItemInfo(taskViewItemInfo)
        verify(statsLogger).log(LauncherEvent.LAUNCHER_SYSTEM_SHORTCUT_DESKTOP_TAP)
    }

    private fun createTask() =
        Task(
                TaskKey(
                    /* id */ 1,
                    /* windowingMode */ 0,
                    Intent(),
                    ComponentName("", ""),
                    /* userId */ 0,
                    /* lastActiveTime */ 2000,
                    DEFAULT_DISPLAY,
                    ComponentName("", ""),
                    /* numActivities */ 1,
                    /* isTopActivityNoDisplay */ false,
                    /* isActivityStackTransparent */ false,
                )
            )
            .apply { isDockable = true }

    private fun createTaskContainer(task: Task) =
        TaskContainer(
            taskView,
            task,
            if (enableRefactorTaskThumbnail()) mock<TaskThumbnailView>()
            else mock<TaskThumbnailViewDeprecated>(),
            mock<TaskViewIcon>(),
            mock<TransformingTouchDelegate>(),
            SplitConfigurationOptions.STAGE_POSITION_UNDEFINED,
            digitalWellBeingToast = null,
            showWindowsView = null,
            overlayFactory,
        )

    private fun setUpTransparentPermission(packageManager: PackageManager, isAllowed: Boolean) {
        val packageInfo: PackageInfo = mock()
        if (isAllowed) {
            packageInfo.requestedPermissions = arrayOf(SYSTEM_ALERT_WINDOW)
        }
        whenever(context.packageManager).thenReturn(packageManager)
        whenever(
                packageManager.getPackageInfoAsUser(
                    anyString(),
                    eq(PackageManager.GET_PERMISSIONS),
                    anyInt(),
                )
            )
            .thenReturn(packageInfo)
    }

    private fun createTaskViewMock(): TaskView {
        val taskView: TaskView = mock()
        whenever(taskView.type).thenReturn(TaskViewType.SINGLE)
        whenever(taskView.context).thenReturn(context)
        return taskView
    }
}

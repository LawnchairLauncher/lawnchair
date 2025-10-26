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

package com.android.launcher3.model.data

import android.content.ComponentName
import android.content.Intent
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.Flags.enableRefactorTaskThumbnail
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_NOT_PINNABLE
import com.android.launcher3.model.data.TaskViewItemInfo.Companion.createTaskViewAtom
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.AllModulesForTest
import com.android.launcher3.util.SandboxContext
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.TransformingTouchDelegate
import com.android.launcher3.util.UserIconInfo
import com.android.quickstep.TaskOverlayFactory
import com.android.quickstep.TaskOverlayFactory.TaskOverlay
import com.android.quickstep.recents.di.RecentsDependencies
import com.android.quickstep.task.thumbnail.TaskThumbnailView
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.TaskContainer
import com.android.quickstep.views.TaskThumbnailViewDeprecated
import com.android.quickstep.views.TaskView
import com.android.quickstep.views.TaskViewIcon
import com.android.quickstep.views.TaskViewType
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.google.common.truth.Truth.assertThat
import dagger.BindsInstance
import dagger.Component
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Test for [TaskViewItemInfo] */
@RunWith(AndroidJUnit4::class)
class TaskViewItemInfoTest {
    private val context = SandboxContext(InstrumentationRegistry.getInstrumentation().targetContext)
    private val taskView = mock<TaskView>()
    private val recentsView = mock<RecentsView<*, *>>()
    private val overlayFactory = mock<TaskOverlayFactory>()
    private val userCache = mock<UserCache>()
    private val userInfo = mock<UserIconInfo>()

    @Before
    fun setUp() {
        whenever(overlayFactory.createOverlay(any())).thenReturn(mock<TaskOverlay<*>>())
        whenever(taskView.context).thenReturn(context)
        whenever(taskView.recentsView).thenReturn(recentsView)
        whenever(recentsView.indexOfChild(taskView)).thenReturn(TASK_VIEW_INDEX)
        whenever(userInfo.isPrivate).thenReturn(false)
        whenever(userCache.getUserInfo(any())).thenReturn(userInfo)
        context.initDaggerComponent(
            DaggerTaskViewItemInfoTest_TestComponent.builder().bindUserCache(userCache)
        )
        RecentsDependencies.maybeInitialize(context)
    }

    @After
    fun tearDown() {
        RecentsDependencies.destroy(context)
    }

    @Test
    fun singleTask() {
        val taskContainers = listOf(createTaskContainer(createTask(1)))
        whenever(taskView.type).thenReturn(TaskViewType.SINGLE)
        whenever(taskView.taskContainers).thenReturn(taskContainers)

        val taskViewItemInfo = TaskViewItemInfo(taskContainers[0].taskView, taskContainers[0])

        assertThat(taskViewItemInfo.taskViewAtom)
            .isEqualTo(
                createTaskViewAtom(
                    type = 0,
                    index = TASK_VIEW_INDEX,
                    componentName = "${PACKAGE}/${CLASS}",
                    cardinality = 1,
                )
            )
        assertThat(taskViewItemInfo.runtimeStatusFlags and FLAG_NOT_PINNABLE).isEqualTo(0)
    }

    @Test
    fun splitTask() {
        val taskContainers =
            listOf(createTaskContainer(createTask(1)), createTaskContainer(createTask(2)))
        whenever(taskView.type).thenReturn(TaskViewType.GROUPED)
        whenever(taskView.taskContainers).thenReturn(taskContainers)

        val taskViewItemInfo = TaskViewItemInfo(taskContainers[0].taskView, taskContainers[0])

        assertThat(taskViewItemInfo.taskViewAtom)
            .isEqualTo(
                createTaskViewAtom(
                    type = 1,
                    index = TASK_VIEW_INDEX,
                    componentName = "${PACKAGE}/${CLASS}",
                    cardinality = 2,
                )
            )
        assertThat(taskViewItemInfo.runtimeStatusFlags and FLAG_NOT_PINNABLE).isEqualTo(0)
    }

    @Test
    fun desktopTask() {
        val taskContainers =
            listOf(
                createTaskContainer(createTask(1)),
                createTaskContainer(createTask(2)),
                createTaskContainer(createTask(3)),
            )
        whenever(taskView.type).thenReturn(TaskViewType.DESKTOP)
        whenever(taskView.taskContainers).thenReturn(taskContainers)

        val taskViewItemInfo = TaskViewItemInfo(taskContainers[0].taskView, taskContainers[0])

        assertThat(taskViewItemInfo.taskViewAtom)
            .isEqualTo(
                createTaskViewAtom(
                    type = 2,
                    index = TASK_VIEW_INDEX,
                    componentName = "${PACKAGE}/${CLASS}",
                    cardinality = 3,
                )
            )
        assertThat(taskViewItemInfo.runtimeStatusFlags and FLAG_NOT_PINNABLE).isEqualTo(0)
    }

    @Test
    fun privateTask() {
        val taskContainers = listOf(createTaskContainer(createTask(1)))
        whenever(taskView.type).thenReturn(TaskViewType.SINGLE)
        whenever(taskView.taskContainers).thenReturn(taskContainers)
        whenever(userInfo.isPrivate).thenReturn(true)

        val taskViewItemInfo = TaskViewItemInfo(taskContainers[0].taskView, taskContainers[0])

        assertThat(taskViewItemInfo.taskViewAtom)
            .isEqualTo(
                createTaskViewAtom(
                    type = 0,
                    index = TASK_VIEW_INDEX,
                    componentName = "${PACKAGE}/${CLASS}",
                    cardinality = 1,
                )
            )
        assertThat(taskViewItemInfo.runtimeStatusFlags and FLAG_NOT_PINNABLE)
            .isEqualTo(FLAG_NOT_PINNABLE)
    }

    @Test
    fun emptyDesktopTask() {
        whenever(taskView.type).thenReturn(TaskViewType.DESKTOP)

        val taskViewItemInfo = TaskViewItemInfo(taskView = taskView, taskContainer = null)

        assertThat(taskViewItemInfo.taskViewAtom)
            .isEqualTo(
                createTaskViewAtom(
                    type = 2,
                    index = TASK_VIEW_INDEX,
                    componentName = "",
                    cardinality = 0,
                )
            )
        assertThat(taskViewItemInfo.user).isEqualTo(Process.myUserHandle())
        assertThat(taskViewItemInfo.intent).isNotNull()
    }

    private fun createTask(id: Int) =
        Task(TaskKey(id, 0, Intent(), ComponentName(PACKAGE, CLASS), 0, 2000))

    private fun createTaskContainer(task: Task): TaskContainer {
        return TaskContainer(
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
    }

    @LauncherAppSingleton
    @Component(modules = [AllModulesForTest::class])
    interface TestComponent : LauncherAppComponent {
        @Component.Builder
        interface Builder : LauncherAppComponent.Builder {
            @BindsInstance fun bindUserCache(userCache: UserCache): Builder

            override fun build(): TestComponent
        }
    }

    companion object {
        const val PACKAGE = "package"
        const val CLASS = "class"
        const val TASK_VIEW_INDEX = 4
    }
}

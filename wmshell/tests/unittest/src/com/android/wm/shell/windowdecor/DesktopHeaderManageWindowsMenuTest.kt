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
package com.android.wm.shell.windowdecor

import android.app.ActivityManager
import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.pm.UserInfo
import android.os.UserManager
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.view.SurfaceControl
import android.view.Display.DEFAULT_DISPLAY
import android.window.TaskSnapshot
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn
import com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession
import com.android.dx.mockito.inline.extended.StaticMockitoSession
import com.android.window.flags.Flags
import com.android.wm.shell.MockToken
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.shared.desktopmode.FakeDesktopConfig
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.windowdecor.additionalviewcontainer.AdditionalSystemViewContainer
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertNotNull
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Tests for [DesktopHeaderManageWindowsMenu].
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:DesktopHeaderManageWindowsMenuTest
 */
@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner::class)
class DesktopHeaderManageWindowsMenuTest : ShellTestCase() {

    private val mockUserManager = mock<UserManager>()

    private lateinit var mockitoSession: StaticMockitoSession
    private lateinit var userRepositories: DesktopUserRepositories
    private lateinit var desktopState: FakeDesktopState
    private lateinit var desktopConfig: FakeDesktopConfig

    private var menu: DesktopHeaderManageWindowsMenu? = null

    @Before
    fun setUp() {
        mockitoSession =
            mockitoSession()
                .strictness(Strictness.LENIENT)
                .spyStatic(ActivityManager::class.java)
                .startMocking()
        doReturn(DEFAULT_USER_ID).`when` { ActivityManager.getCurrentUser() }
        desktopState = FakeDesktopState()
        desktopState.canEnterDesktopMode = true
        desktopConfig = FakeDesktopConfig()

        whenever(mockUserManager.getProfiles(DEFAULT_USER_ID)).thenReturn(
            listOf(UserInfo(DEFAULT_USER_ID, "User 10", /* flags= */ 0)))
        userRepositories = DesktopUserRepositories(
            shellInit = ShellInit(TestShellExecutor()),
            shellController = mock(),
            persistentRepository = mock(),
            repositoryInitializer = mock(),
            mainCoroutineScope = mock(),
            userManager = mockUserManager,
            desktopState = desktopState,
            desktopConfig = desktopConfig,
        )
        userRepositories.getProfile(DEFAULT_USER_ID).apply {
            addDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
            setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 0)
        }
    }

    @After
    fun tearDown() {
        menu?.removeFromContainer()
        mockitoSession.finishMocking()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun testShow_forImmersiveTask_usesSystemViewContainer() {
        val task = createFreeformTask()
        assertThat(userRepositories.getProfile(DEFAULT_USER_ID).userId).isEqualTo(DEFAULT_USER_ID)
        userRepositories.getProfile(DEFAULT_USER_ID).setTaskInFullImmersiveState(
            displayId = task.displayId,
            taskId = task.taskId,
            immersive = true
        )

        val menu = createMenu(task)

        assertNotNull(menu)
        assertThat(menu.menuViewContainer).isInstanceOf(AdditionalSystemViewContainer::class.java)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun testShow_nullSnapshotDoesNotCauseNPE() {
        val task = createFreeformTask()
        val snapshotList = listOf(Pair(/* index = */ 1, /* snapshot = */ null))
        // Set as immersive so that menu is created as system view container (simpler of the
        // options)
        userRepositories.getProfile(DEFAULT_USER_ID).setTaskInFullImmersiveState(
            displayId = task.displayId,
            taskId = task.taskId,
            immersive = true
        )
        try {
            menu = createMenu(task, snapshotList)
        } catch (e: NullPointerException) {
            fail("Null snapshot should not have thrown null pointer exception")
        }
    }

    private fun createMenu(
        task: RunningTaskInfo,
        snapshotList: List<Pair<Int, TaskSnapshot?>> = emptyList()
    ): DesktopHeaderManageWindowsMenu? {
        val menu = DesktopHeaderManageWindowsMenu(
            callerTaskInfo = task,
            x = 0,
            y = 0,
            displayController = mock(),
            rootTdaOrganizer = mock(),
            context = context,
            desktopUserRepositories = userRepositories,
            surfaceControlBuilderSupplier = { SurfaceControl.Builder() },
            surfaceControlTransactionSupplier = { SurfaceControl.Transaction() },
            snapshotList = snapshotList,
            onIconClickListener = {},
            onOutsideClickListener = {},
        )
        this.menu = menu
        return menu
    }

    private fun createFreeformTask(): RunningTaskInfo = TestRunningTaskInfoBuilder()
        .setToken(MockToken().token())
        .setActivityType(ACTIVITY_TYPE_STANDARD)
        .setWindowingMode(WINDOWING_MODE_FREEFORM)
        .setUserId(DEFAULT_USER_ID)
        .build()

    private companion object {
        const val DEFAULT_USER_ID = 10
    }
}

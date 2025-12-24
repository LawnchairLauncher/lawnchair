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

package com.android.launcher3.model.tasks

import android.os.Process.myUserHandle
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.Flags
import com.android.launcher3.Flags.FLAG_ENABLE_PRIVATE_SPACE
import com.android.launcher3.LauncherModel.ModelUpdateTask
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.TestableModelState
import com.android.launcher3.model.data.AppsListData.Companion.FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED
import com.android.launcher3.model.data.AppsListData.Companion.FLAG_WORK_PROFILE_QUIET_MODE_ENABLED
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.UserIconInfo
import com.android.launcher3.util.rule.MockUsersRule
import com.android.launcher3.util.rule.MockUsersRule.MockUser
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class UserAvailabilityChangedTaskTest {

    @get:Rule val setFlagsRule = SetFlagsRule()
    @get:Rule val context = spy(SandboxApplication().withModelDependency())
    @get:Rule val mockito = MockitoJUnit.rule()
    @get:Rule val mockUsers = MockUsersRule(context)

    @Mock lateinit var mockTaskController: ModelTaskController

    private val modelState: TestableModelState
        get() = context.appComponent.testableModelState

    @Before
    fun setup() {
        doReturn(context).whenever(mockTaskController).context
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PRIVATE_SPACE, Flags.FLAG_MODEL_REPOSITORY)
    @MockUser(userType = UserIconInfo.TYPE_MAIN)
    fun update_triggers_no_callbacks_if_current_user_not_work_or_private() {
        UserAvailabilityChangedTask(myUserHandle()).executeSync()
        modelState.appsList.getAndResetChangeFlag()

        assertThat(modelState.appsRepo.appsListStateRef.value.flags).isEqualTo(0)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PRIVATE_SPACE, Flags.FLAG_MODEL_REPOSITORY)
    @MockUser(userType = UserIconInfo.TYPE_PRIVATE, isQuietModeEnabled = true)
    fun update_flag_when_private_user_is_quiet() {
        UserAvailabilityChangedTask(myUserHandle()).executeSync()
        modelState.appsList.getAndResetChangeFlag()

        assertThat(modelState.appsRepo.appsListStateRef.value.flags)
            .isEqualTo(FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_PRIVATE_SPACE, Flags.FLAG_MODEL_REPOSITORY)
    @MockUser(userType = UserIconInfo.TYPE_WORK, isQuietModeEnabled = true)
    fun update_flag_when_work_user_is_quiet() {
        UserAvailabilityChangedTask(myUserHandle()).executeSync()
        modelState.appsList.getAndResetChangeFlag()

        assertThat(modelState.appsRepo.appsListStateRef.value.flags)
            .isEqualTo(FLAG_WORK_PROFILE_QUIET_MODE_ENABLED)
    }

    private fun ModelUpdateTask.executeSync() =
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            execute(mockTaskController, modelState.dataModel, modelState.appsList)
        }
}

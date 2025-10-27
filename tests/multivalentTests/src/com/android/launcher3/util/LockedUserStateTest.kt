/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.launcher3.util

import android.content.Context
import android.content.Intent
import android.os.Process
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

/** Unit tests for {@link LockedUserState} */
@SmallTest
@RunWith(AndroidJUnit4::class)
class LockedUserStateTest {

    private val userManager: UserManager = mock()
    private val context: Context = mock()
    private val lifeCycle: DaggerSingletonTracker = mock()

    @Before
    fun setup() {
        whenever(context.getSystemService(UserManager::class.java)).thenReturn(userManager)
    }

    @After
    fun tearDown() {
        UI_HELPER_EXECUTOR.submit {}.get()
        MAIN_EXECUTOR.submit {}.get()
    }

    @Test
    fun runOnUserUnlocked_runs_action_immediately_if_already_unlocked() {
        whenever(userManager.isUserUnlocked(Process.myUserHandle())).thenReturn(true)
        val action: Runnable = mock()
        LockedUserState(context, lifeCycle).runOnUserUnlocked(action)
        verify(action).run()
    }

    @Test
    fun runOnUserUnlocked_waits_to_run_action_until_user_is_unlocked() {
        whenever(userManager.isUserUnlocked(Process.myUserHandle())).thenReturn(false)
        val action: Runnable = mock()
        val state = LockedUserState(context, lifeCycle)
        state.runOnUserUnlocked(action)
        // b/343530737
        verifyNoMoreInteractions(action)
        state.userUnlockedReceiver.onReceive(context, Intent(Intent.ACTION_USER_UNLOCKED))
        verify(action).run()
    }

    @Test
    fun isUserUnlocked_returns_true_when_user_is_unlocked() {
        whenever(userManager.isUserUnlocked(Process.myUserHandle())).thenReturn(true)
        assertThat(LockedUserState(context, lifeCycle).isUserUnlocked).isTrue()
    }

    @Test
    fun isUserUnlocked_returns_false_when_user_is_locked() {
        whenever(userManager.isUserUnlocked(Process.myUserHandle())).thenReturn(false)
        assertThat(LockedUserState(context, lifeCycle).isUserUnlocked).isFalse()
    }
}

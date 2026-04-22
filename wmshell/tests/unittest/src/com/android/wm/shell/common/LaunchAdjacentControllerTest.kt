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
package com.android.wm.shell.common

import android.os.IBinder
import android.testing.AndroidTestingRunner
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.HierarchyOp
import androidx.test.filters.SmallTest
import com.android.wm.shell.MockToken
import com.android.wm.shell.ShellTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

@SmallTest
@RunWith(AndroidTestingRunner::class)
class LaunchAdjacentControllerTest : ShellTestCase() {

    private lateinit var controller: LaunchAdjacentController

    @Mock private lateinit var syncQueue: SyncTransactionQueue

    @Before
    fun setUp() {
        controller = LaunchAdjacentController(syncQueue)
    }

    @Test
    fun newInstance_enabledByDefault() {
        assertThat(controller.launchAdjacentEnabled).isTrue()
    }

    @Test
    fun setLaunchAdjacentRoot_launchAdjacentEnabled_setsFlagRoot() {
        val token = MockToken().token()
        controller.setLaunchAdjacentRoot(token)
        val wct = getLatestTransactionOrFail()
        assertThat(wct.getSetLaunchAdjacentFlagRootContainer()).isEqualTo(token.asBinder())
    }

    @Test
    fun setLaunchAdjacentRoot_launchAdjacentDisabled_doesNotUpdateFlagRoot() {
        val token = MockToken().token()
        controller.launchAdjacentEnabled = false
        controller.setLaunchAdjacentRoot(token)
        verify(syncQueue, never()).queue(any())
    }

    @Test
    fun clearLaunchAdjacentRoot_launchAdjacentEnabled_clearsFlagRoot() {
        val token = MockToken().token()
        controller.setLaunchAdjacentRoot(token)
        controller.clearLaunchAdjacentRoot()
        val wct = getLatestTransactionOrFail()
        assertThat(wct.getClearLaunchAdjacentFlagRootContainer()).isEqualTo(token.asBinder())
    }

    @Test
    fun clearLaunchAdjacentRoot_launchAdjacentDisabled_clearsFlagRoot() {
        val token = MockToken().token()
        controller.setLaunchAdjacentRoot(token)
        controller.launchAdjacentEnabled = false
        clearInvocations(syncQueue)

        controller.clearLaunchAdjacentRoot()
        val wct = getLatestTransactionOrFail()
        assertThat(wct.getClearLaunchAdjacentFlagRootContainer()).isEqualTo(token.asBinder())
    }

    @Test
    fun setLaunchAdjacentEnabled_wasDisabledWithContainerSet_setsFlagRoot() {
        val token = MockToken().token()
        controller.setLaunchAdjacentRoot(token)
        controller.launchAdjacentEnabled = false
        clearInvocations(syncQueue)

        controller.launchAdjacentEnabled = true
        val wct = getLatestTransactionOrFail()
        assertThat(wct.getSetLaunchAdjacentFlagRootContainer()).isEqualTo(token.asBinder())
    }

    @Test
    fun setLaunchAdjacentEnabled_containerNotSet_doesNotUpdateFlagRoot() {
        controller.launchAdjacentEnabled = false
        controller.launchAdjacentEnabled = true
        verify(syncQueue, never()).queue(any())
    }

    @Test
    fun setLaunchAdjacentEnabled_multipleTimes_setsFlagRootOnce() {
        val token = MockToken().token()
        controller.setLaunchAdjacentRoot(token)
        controller.launchAdjacentEnabled = true
        controller.launchAdjacentEnabled = true
        // Only execute once
        verify(syncQueue).queue(any())
    }

    @Test
    fun setLaunchAdjacentDisabled_containerSet_clearsFlagRoot() {
        val token = MockToken().token()
        controller.setLaunchAdjacentRoot(token)
        controller.launchAdjacentEnabled = false
        val wct = getLatestTransactionOrFail()
        assertThat(wct.getClearLaunchAdjacentFlagRootContainer()).isEqualTo(token.asBinder())
    }

    @Test
    fun setLaunchAdjacentDisabled_containerNotSet_doesNotUpdateFlagRoot() {
        controller.launchAdjacentEnabled = false
        verify(syncQueue, never()).queue(any())
    }

    @Test
    fun setLaunchAdjacentDisabled_multipleTimes_setsFlagRootOnce() {
        val token = MockToken().token()
        controller.setLaunchAdjacentRoot(token)
        clearInvocations(syncQueue)
        controller.launchAdjacentEnabled = false
        controller.launchAdjacentEnabled = false
        // Only execute once
        verify(syncQueue).queue(any())
    }

    private fun getLatestTransactionOrFail(): WindowContainerTransaction {
        val arg = ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
        verify(syncQueue, atLeastOnce()).queue(arg.capture())
        return arg.allValues.last().also { assertThat(it).isNotNull() }
    }
}

private fun WindowContainerTransaction.getSetLaunchAdjacentFlagRootContainer(): IBinder {
    return hierarchyOps
        // Find the operation with the correct type
        .filter { op -> op.type == HierarchyOp.HIERARCHY_OP_TYPE_SET_LAUNCH_ADJACENT_FLAG_ROOT }
        // For set flag root operation, toTop is false
        .filter { op -> !op.toTop }
        .map { it.container }
        .first()
}

private fun WindowContainerTransaction.getClearLaunchAdjacentFlagRootContainer(): IBinder {
    return hierarchyOps
        // Find the operation with the correct type
        .filter { op -> op.type == HierarchyOp.HIERARCHY_OP_TYPE_SET_LAUNCH_ADJACENT_FLAG_ROOT }
        // For clear flag root operation, toTop is true
        .filter { op -> op.toTop }
        .map { it.container }
        .first()
}

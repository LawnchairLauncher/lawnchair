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

package com.android.wm.shell.compatui.impl

import android.app.ActivityManager
import android.graphics.Point
import android.testing.AndroidTestingRunner
import android.view.View
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.compatui.api.CompatUIComponent
import com.android.wm.shell.compatui.api.CompatUIComponentState
import com.android.wm.shell.compatui.api.CompatUIInfo
import com.android.wm.shell.compatui.api.CompatUIState
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/**
 * Tests for {@link CompatUIComponent}.
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:CompatUIComponentTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class CompatUIComponentTest : ShellTestCase() {

    private lateinit var component: CompatUIComponent
    private lateinit var layout: FakeCompatUILayout
    private lateinit var spec: FakeCompatUISpec
    private lateinit var state: CompatUIState
    private lateinit var info: CompatUIInfo
    private lateinit var syncQueue: SyncTransactionQueue
    private lateinit var displayLayout: DisplayLayout
    private lateinit var view: View
    private lateinit var position: Point
    private lateinit var componentState: CompatUIComponentState

    @JvmField
    @Rule
    val compatUIHandlerRule: CompatUIHandlerRule = CompatUIHandlerRule()

    @Before
    fun setUp() {
        state = CompatUIState()
        view = View(mContext)
        position = Point(123, 456)
        layout = FakeCompatUILayout(viewBuilderReturn = view, positionBuilderReturn = position)
        spec = FakeCompatUISpec("comp", layout = layout)
        info = testCompatUIInfo()
        syncQueue = mock<SyncTransactionQueue>()
        displayLayout = mock<DisplayLayout>()
        component =
            CompatUIComponent(spec.getSpec(),
                "compId",
                mContext,
                state,
                info,
                syncQueue,
                displayLayout)
        componentState = object : CompatUIComponentState {}
        state.registerUIComponent("compId", component, componentState)
    }

    @Test
    fun `when initLayout is invoked spec fields are used`() {
        compatUIHandlerRule.postBlocking {
            component.initLayout(info)
        }
        with(layout) {
            assertViewBuilderInvocation(1)
            assertEquals(info, lastViewBuilderCompatUIInfo)
            assertEquals(componentState, lastViewBuilderCompState)
            assertViewBinderInvocation(0)
            assertPositionFactoryInvocation(1)
            assertEquals(info, lastPositionFactoryCompatUIInfo)
            assertEquals(view, lastPositionFactoryView)
            assertEquals(componentState, lastPositionFactoryCompState)
            assertEquals(state.sharedState, lastPositionFactorySharedState)
        }
    }

    @Test
    fun `when update is invoked only position and binder spec fields are used`() {
        compatUIHandlerRule.postBlocking {
            component.initLayout(info)
            layout.resetState()
            component.update(info)
        }
        with(layout) {
            assertViewBuilderInvocation(0)
            assertViewBinderInvocation(1)
            assertPositionFactoryInvocation(1)
        }
    }

    private fun testCompatUIInfo(): CompatUIInfo {
        val taskInfo = ActivityManager.RunningTaskInfo()
        taskInfo.taskId = 1
        return CompatUIInfo(taskInfo, null)
    }
}

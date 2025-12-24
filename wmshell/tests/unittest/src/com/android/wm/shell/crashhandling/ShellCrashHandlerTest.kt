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

package com.android.wm.shell.crashhandling

import android.app.ActivityManager.RunningTaskInfo
import android.app.PendingIntent
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_PENDING_INTENT
import com.android.modules.utils.testing.ExtendedMockitoRule
import com.android.window.flags2.Flags
import com.android.wm.shell.MockToken
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.bubbles.BubbleController
import com.android.wm.shell.bubbles.util.BubbleTestUtils.verifyExitBubbleTransaction
import com.android.wm.shell.common.HomeIntentProvider
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.NoOpTransitionHandler
import com.android.wm.shell.transition.Transitions
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import kotlin.test.Test
import org.junit.Before
import org.junit.Rule
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ShellCrashHandlerTest : ShellTestCase() {
    @JvmField
    @Rule
    val extendedMockitoRule =
        ExtendedMockitoRule.Builder(this).mockStatic(PendingIntent::class.java).build()!!

    private val testExecutor = mock<ShellExecutor>()
    private val context = mock<Context>()
    private val shellTaskOrganizer = mock<ShellTaskOrganizer>()
    private val desktopState = FakeDesktopState()
    private val transitions = mock<Transitions>()
    private val bubbleController = mock<BubbleController>()

    private lateinit var homeIntentProvider: HomeIntentProvider
    private lateinit var crashHandler: ShellCrashHandler
    private lateinit var shellInit: ShellInit

    @Before
    fun setup() {
        desktopState.canEnterDesktopMode = true
        whenever(PendingIntent.getActivity(any(), any(), any(), any(), any())).thenReturn(mock())

        shellInit = spy(ShellInit(testExecutor))

        homeIntentProvider = HomeIntentProvider(context)
        crashHandler =
            ShellCrashHandler(
                shellTaskOrganizer,
                transitions,
                homeIntentProvider,
                desktopState,
                Optional.of(bubbleController),
                shellInit,
            )
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    fun init_freeformTaskExists_sendsHomeIntent() {
        val wctCaptor = ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
        val task = createTaskInfo(1)
        whenever(shellTaskOrganizer.getRunningTasks()).thenReturn(arrayListOf(task))

        shellInit.init()

        verify(shellTaskOrganizer).applyTransaction(wctCaptor.capture())
        wctCaptor.value.assertPendingIntentAt(0, launchHomeIntent(DEFAULT_DISPLAY))
    }

    @Test
    @EnableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_RESTART_BUBBLE_CLEANUP)
    fun init_bubbleTaskExists_convertsToUndefined() {
        val bubbleTask = createTaskInfo(1, windowingMode = WINDOWING_MODE_MULTI_WINDOW)
        whenever(shellTaskOrganizer.getRunningTasks()).thenReturn(arrayListOf(bubbleTask))
        whenever(bubbleController.shouldBeAppBubble(bubbleTask)).thenReturn(true)

        shellInit.init()

        val wctCaptor = ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
        verify(transitions)
            .startTransition(eq(TRANSIT_CHANGE), wctCaptor.capture(), isA<NoOpTransitionHandler>())
        val wct = wctCaptor.value

        verifyExitBubbleTransaction(wct, bubbleTask.token.asBinder())
        wct.assertPendingIntentAt(wct.hierarchyOps.lastIndex, launchHomeIntent(DEFAULT_DISPLAY))
    }

    private fun launchHomeIntent(displayId: Int): Intent {
        return Intent(Intent.ACTION_MAIN).apply {
            if (displayId != DEFAULT_DISPLAY) {
                addCategory(Intent.CATEGORY_SECONDARY_HOME)
            } else {
                addCategory(Intent.CATEGORY_HOME)
            }
        }
    }

    private fun createTaskInfo(id: Int, windowingMode: Int = WINDOWING_MODE_FREEFORM) =
        RunningTaskInfo().apply {
            taskId = id
            displayId = DEFAULT_DISPLAY
            configuration.windowConfiguration.windowingMode = windowingMode
            token = MockToken.token()
            baseIntent = Intent().apply { component = ComponentName("package", "component.name") }
        }

    private fun WindowContainerTransaction.assertPendingIntentAt(index: Int, intent: Intent) {
        val op = hierarchyOps[index]
        assertThat(op.type).isEqualTo(HIERARCHY_OP_TYPE_PENDING_INTENT)
        assertThat(op.activityIntent?.component).isEqualTo(intent.component)
        assertThat(op.activityIntent?.categories).isEqualTo(intent.categories)
    }
}

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

package com.android.wm.shell.bubbles

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import android.os.UserHandle
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.StatusBarNotification
import android.view.View
import android.widget.FrameLayout
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.internal.protolog.ProtoLog
import com.android.window.flags.Flags.FLAG_EXCLUDE_TASK_FROM_RECENTS
import com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_ANYTHING
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.android.wm.shell.MockToken
import com.android.wm.shell.R
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.bubbles.Bubbles.BubbleMetadataFlagListener
import com.android.wm.shell.bubbles.util.BubbleTestUtils.verifyEnterBubbleTransaction
import com.android.wm.shell.common.TestShellExecutor
import com.android.wm.shell.taskview.TaskView
import com.android.wm.shell.taskview.TaskViewController
import com.android.wm.shell.taskview.TaskViewTaskController
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.reset
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [BubbleTaskViewListener].
 *
 * Build/Install/Run:
 *  atest WMShellRobolectricTests:BubbleTaskViewListenerTest (on host)
 *  atest WMShellMultivalentTestsOnDevice:BubbleTaskViewListenerTest (on device)
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleTaskViewListenerTest {

    @get:Rule
    val setFlagsRule = SetFlagsRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val taskOrganizer = mock<ShellTaskOrganizer>()
    private val taskViewTaskToken: WindowContainerToken = MockToken.token()
    private var taskViewController = mock<TaskViewController>()
    private val taskInfo = mock<ActivityManager.RunningTaskInfo>()
    private val taskViewTaskController = mock<TaskViewTaskController> {
        on { taskOrganizer } doReturn taskOrganizer
        on { taskToken } doReturn taskViewTaskToken
        on { taskInfo } doReturn taskInfo
    }
    private var listenerCallback = mock<BubbleTaskViewListener.Callback>()
    private var expandedViewManager = mock<BubbleExpandedViewManager>()

    private lateinit var bubbleTaskViewListener: BubbleTaskViewListener
    private lateinit var taskView: TaskView
    private lateinit var bubbleTaskView: BubbleTaskView
    private lateinit var parentView: ViewPoster
    private lateinit var mainExecutor: TestShellExecutor
    private lateinit var bgExecutor: TestShellExecutor

    @Before
    fun setUp() {
        ProtoLog.REQUIRE_PROTOLOGTOOL = false
        ProtoLog.init()

        parentView = ViewPoster(context)
        mainExecutor = TestShellExecutor()
        bgExecutor = TestShellExecutor()

        taskView = TaskView(context, taskViewController, taskViewTaskController)
        bubbleTaskView = BubbleTaskView(taskView, mainExecutor)

        bubbleTaskViewListener =
            BubbleTaskViewListener(
                context,
                bubbleTaskView,
                parentView,
                expandedViewManager,
                listenerCallback
            )
    }

    @Test
    fun createBubbleTaskViewListener_withCreatedTaskView() {
        // Make the bubbleTaskView look like it's been created
        val taskId = 123
        bubbleTaskView.listener.onTaskCreated(taskId, mock<ComponentName>())
        reset(listenerCallback)

        bubbleTaskViewListener =
            BubbleTaskViewListener(
                context,
                bubbleTaskView,
                parentView,
                expandedViewManager,
                listenerCallback
            )

        assertThat(bubbleTaskView.delegateListener).isEqualTo(bubbleTaskViewListener)
        assertThat(bubbleTaskViewListener.taskView).isEqualTo(bubbleTaskView.taskView)

        verify(listenerCallback).onTaskCreated()
        assertThat(bubbleTaskViewListener.taskId).isEqualTo(taskId)
    }

    @Test
    fun createBubbleTaskViewListener() {
        bubbleTaskViewListener =
            BubbleTaskViewListener(
                context,
                bubbleTaskView,
                parentView,
                expandedViewManager,
                listenerCallback
            )

        assertThat(bubbleTaskView.delegateListener).isEqualTo(bubbleTaskViewListener)
        assertThat(bubbleTaskViewListener.taskView).isEqualTo(bubbleTaskView.taskView)
        verify(listenerCallback, never()).onTaskCreated()
    }

    @Test
    fun onInitialized_pendingIntentChatBubble() {
        val target = Intent(context, TestActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, target,
            PendingIntent.FLAG_MUTABLE)

        val b = createChatBubble("key", pendingIntent)
        bubbleTaskViewListener.setBubble(b)

        assertThat(b.isChat).isTrue()
        // Has shortcut info
        assertThat(b.shortcutInfo).isNotNull()
        // But it didn't use that on bubble metadata
        assertThat(b.metadataShortcutId).isNull()

        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onInitialized()
        }
        getInstrumentation().waitForIdleSync()

        // ..so it's pending intent-based, so the pending intent should be active
        assertThat(b.isPendingIntentActive).isTrue()

        val intentCaptor = argumentCaptor<Intent>()
        val optionsCaptor = argumentCaptor<ActivityOptions>()

        verify(taskViewController).startActivity(any(),
            eq(pendingIntent),
            intentCaptor.capture(),
            optionsCaptor.capture(),
            any())
        val intentFlags = intentCaptor.lastValue.flags
        assertThat((intentFlags and Intent.FLAG_ACTIVITY_NEW_DOCUMENT) != 0).isTrue()
        assertThat((intentFlags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK) != 0).isTrue()
        assertThat(optionsCaptor.lastValue.launchedFromBubble).isTrue()
        assertThat(optionsCaptor.lastValue.taskAlwaysOnTop).isTrue()
    }

    @Test
    fun onInitialized_shortcutChatBubble() {
        val shortcutInfo = ShortcutInfo.Builder(context)
            .setId("mockShortcutId")
            .build()
        val b = createChatBubble("key", shortcutInfo)
        bubbleTaskViewListener.setBubble(b)

        assertThat(b.isChat).isTrue()
        assertThat(b.shortcutInfo).isNotNull()
        // Chat bubble using a shortcut
        assertThat(b.metadataShortcutId).isNotNull()

        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onInitialized()
        }
        getInstrumentation().waitForIdleSync()

        val optionsCaptor = argumentCaptor<ActivityOptions>()

        assertThat(b.isPendingIntentActive).isFalse() // not triggered for shortcut chats
        verify(taskViewController).startShortcutActivity(any(),
            eq(shortcutInfo),
            optionsCaptor.capture(),
            any())
        assertThat(optionsCaptor.lastValue.launchedFromBubble).isTrue()
        assertThat(optionsCaptor.lastValue.isApplyActivityFlagsForBubbles).isTrue()
        assertThat(optionsCaptor.lastValue.taskAlwaysOnTop).isTrue()
    }

    @EnableFlags(FLAG_ENABLE_BUBBLE_ANYTHING)
    @Test
    fun onInitialized_shortcutBubble() {
        val shortcutInfo = ShortcutInfo.Builder(context)
            .setId("mockShortcutId")
            .build()

        val b = createShortcutBubble(shortcutInfo)
        bubbleTaskViewListener.setBubble(b)

        assertThat(b.isChat).isFalse()
        assertThat(b.isShortcut).isTrue()
        assertThat(b.shortcutInfo).isNotNull()

        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onInitialized()
        }
        getInstrumentation().waitForIdleSync()

        val optionsCaptor = argumentCaptor<ActivityOptions>()

        assertThat(b.isPendingIntentActive).isFalse() // chat only triggers setting it active
        verify(taskViewController).startShortcutActivity(any(),
            eq(shortcutInfo),
            optionsCaptor.capture(),
            any())
        assertThat(optionsCaptor.lastValue.launchedFromBubble).isFalse() // chat only
        assertThat(optionsCaptor.lastValue.isApplyActivityFlagsForBubbles).isFalse() // chat only
        assertThat(optionsCaptor.lastValue.isApplyMultipleTaskFlagForShortcut).isTrue()
        assertThat(optionsCaptor.lastValue.taskAlwaysOnTop).isTrue()
    }

    @Test
    fun onInitialized_appBubble_intent() {
        val b = createAppBubble()
        bubbleTaskViewListener.setBubble(b)

        assertThat(b.isApp).isTrue()

        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onInitialized()
        }
        getInstrumentation().waitForIdleSync()

        val intentCaptor = argumentCaptor<Intent>()
        val optionsCaptor = argumentCaptor<ActivityOptions>()

        assertThat(b.isPendingIntentActive).isFalse() // chat only triggers setting it active
        verify(taskViewController).startActivity(any(),
            any(),
            intentCaptor.capture(),
            optionsCaptor.capture(),
            any())

        assertThat(optionsCaptor.lastValue.launchedFromBubble).isFalse() // chat only
        assertThat(optionsCaptor.lastValue.isApplyActivityFlagsForBubbles).isFalse() // chat only
        assertThat(optionsCaptor.lastValue.taskAlwaysOnTop).isTrue()
    }

    @Test
    fun onInitialized_appBubble_pendingIntent() {
        val b = createAppBubble(usePendingIntent = true)
        bubbleTaskViewListener.setBubble(b)

        assertThat(b.isApp).isTrue()

        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onInitialized()
        }
        getInstrumentation().waitForIdleSync()

        val intentCaptor = argumentCaptor<Intent>()
        val optionsCaptor = argumentCaptor<ActivityOptions>()

        assertThat(b.isPendingIntentActive).isFalse() // chat only triggers setting it active
        verify(taskViewController).startActivity(any(),
            any(),
            intentCaptor.capture(),
            optionsCaptor.capture(),
            any())

        assertThat(optionsCaptor.lastValue.launchedFromBubble).isFalse() // chat only
        assertThat(optionsCaptor.lastValue.isApplyActivityFlagsForBubbles).isFalse() // chat only
        assertThat(optionsCaptor.lastValue.taskAlwaysOnTop).isTrue()
    }

    @Test
    fun onInitialized_noteBubble() {
        val b = createNoteBubble()
        bubbleTaskViewListener.setBubble(b)

        assertThat(b.isNote).isTrue()

        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onInitialized()
        }
        getInstrumentation().waitForIdleSync()

        val intentCaptor = argumentCaptor<Intent>()
        val optionsCaptor = argumentCaptor<ActivityOptions>()

        assertThat(b.isPendingIntentActive).isFalse() // chat only triggers setting it active
        verify(taskViewController).startActivity(any(),
            any(),
            intentCaptor.capture(),
            optionsCaptor.capture(),
            any())

        assertThat(optionsCaptor.lastValue.launchedFromBubble).isFalse() // chat only
        assertThat(optionsCaptor.lastValue.isApplyActivityFlagsForBubbles).isFalse() // chat only
        assertThat(optionsCaptor.lastValue.taskAlwaysOnTop).isTrue()
    }

    @Test
    fun onInitialized_preparingTransition() {
        val b = createAppBubble()
        bubbleTaskViewListener.setBubble(b)
        taskView = Mockito.spy(taskView)
        val preparingTransition = mock<BubbleTransitions.BubbleTransition>()
        b.preparingTransition = preparingTransition

        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onInitialized()
        }
        getInstrumentation().waitForIdleSync()

        verify(preparingTransition).surfaceCreated()
    }

    @Test
    fun onInitialized_destroyed() {
        val b = createAppBubble()
        bubbleTaskViewListener.setBubble(b)

        assertThat(b.isApp).isTrue()

        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onReleased()
            bubbleTaskViewListener.onInitialized()
        }
        getInstrumentation().waitForIdleSync()

        verify(taskViewController, never()).startActivity(any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun onInitialized_initialized() {
        val b = createAppBubble()
        bubbleTaskViewListener.setBubble(b)

        assertThat(b.isApp).isTrue()

        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onInitialized()
        }
        getInstrumentation().waitForIdleSync()

        reset(taskViewController)

        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onInitialized()
        }
        // Already initialized, so no activity should be started.
        verify(taskViewController, never()).startActivity(any(), any(), anyOrNull(), any(), any())
    }

    @Test
    fun onTaskCreated() {
        val b = createAppBubble()
        bubbleTaskViewListener.setBubble(b)

        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onInitialized()
        }
        getInstrumentation().waitForIdleSync()
        verify(taskViewController).startActivity(any(), any(), anyOrNull(), any(), any())

        val taskId = 123
        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onTaskCreated(taskId, mock<ComponentName>())
        }
        getInstrumentation().waitForIdleSync()

        verify(listenerCallback).onTaskCreated()
        verify(expandedViewManager, never()).setNoteBubbleTaskId(any(), any())
        assertThat(bubbleTaskViewListener.taskId).isEqualTo(taskId)
    }

    @Test
    @EnableFlags(
        FLAG_ENABLE_BUBBLE_ANYTHING,
        FLAG_EXCLUDE_TASK_FROM_RECENTS,
    )
    fun onTaskCreated_appliesWctToEnterBubble() {
        val b = createAppBubble()
        bubbleTaskViewListener.setBubble(b)
        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onInitialized()
        }
        getInstrumentation().waitForIdleSync()

        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onTaskCreated(123 /* taskId */, mock<ComponentName>())
        }
        getInstrumentation().waitForIdleSync()

        val wctCaptor = argumentCaptor<WindowContainerTransaction>()
        verify(taskOrganizer).applyTransaction(wctCaptor.capture())
        val wct = wctCaptor.lastValue
        verifyEnterBubbleTransaction(
            wct,
            taskViewTaskToken.asBinder(),
            b.isApp || b.isShortcut,
        )
    }

    @Test
    fun onTaskCreated_noteBubble() {
        val b = createNoteBubble()
        bubbleTaskViewListener.setBubble(b)
        assertThat(b.isNote).isTrue()

        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onInitialized()
        }
        getInstrumentation().waitForIdleSync()
        verify(taskViewController).startActivity(any(), any(), anyOrNull(), any(), any())

        val taskId = 123
        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onTaskCreated(taskId, mock<ComponentName>())
        }
        getInstrumentation().waitForIdleSync()

        verify(listenerCallback).onTaskCreated()
        verify(expandedViewManager).setNoteBubbleTaskId(eq(b.key), eq(taskId))
        assertThat(bubbleTaskViewListener.taskId).isEqualTo(taskId)
    }

    @Test
    fun onTaskVisibilityChanged_true() {
        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onTaskVisibilityChanged(1, true)
        }
        verify(listenerCallback).onContentVisibilityChanged(eq(true))
    }

    @Test
    fun onTaskVisibilityChanged_false() {
        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onTaskVisibilityChanged(1, false)
        }
        verify(listenerCallback).onContentVisibilityChanged(eq(false))
    }

    @Test
    fun onTaskRemovalStarted() {
        val mockTaskView = mock<TaskView>() {
            on { getController() } doReturn taskViewTaskController
        }
        bubbleTaskView = BubbleTaskView(mockTaskView, mainExecutor)

        bubbleTaskViewListener =
            BubbleTaskViewListener(
                context,
                bubbleTaskView,
                parentView,
                expandedViewManager,
                listenerCallback
            )

        val b = createAppBubble()
        bubbleTaskViewListener.setBubble(b)

        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onInitialized()
        }
        getInstrumentation().waitForIdleSync()
        verify(mockTaskView).startActivity(any(), anyOrNull(), any(), any())

        taskInfo.isRunning = true
        taskInfo.token = taskViewTaskToken
        whenever(expandedViewManager.shouldBeAppBubble(eq(taskInfo))).doReturn(true)
        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onTaskRemovalStarted(1)
        }

        verify(expandedViewManager).removeBubble(eq(b.key), eq(Bubbles.DISMISS_TASK_FINISHED))
        verify(mockTaskView).release()

        // Capture the WCT used to clean up the task
        val wct = argumentCaptor<WindowContainerTransaction>().let { wctCaptor ->
            verify(taskOrganizer).applyTransaction(wctCaptor.capture())
            wctCaptor.lastValue
        }
        val change = wct.changes[taskViewTaskToken.asBinder()]!!
        assertThat(change.interceptBackPressed).isFalse()
        assertThat(parentView.lastRemovedView).isEqualTo(mockTaskView)
        assertThat(bubbleTaskViewListener.taskView).isNull()
        verify(listenerCallback).onTaskRemovalStarted()
    }

    @EnableFlags(FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @Test
    fun onTaskInfoChanged() {
        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onTaskInfoChanged(taskInfo)
        }
        verify(listenerCallback).onTaskInfoChanged(taskInfo)
    }

    @Test
    fun onBackPressedOnTaskRoot_expanded() {
        val taskId = 123
        whenever(expandedViewManager.isStackExpanded()).doReturn(true)

        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onTaskCreated(taskId, mock<ComponentName>())
            bubbleTaskViewListener.onBackPressedOnTaskRoot(taskId)
        }
        verify(listenerCallback).onBackPressed()
    }

    @Test
    fun onBackPressedOnTaskRoot_notExpanded() {
        val taskId = 123
        whenever(expandedViewManager.isStackExpanded()).doReturn(false)

        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onTaskCreated(taskId, mock<ComponentName>())
            bubbleTaskViewListener.onBackPressedOnTaskRoot(taskId)
        }
        verify(listenerCallback, never()).onBackPressed()
    }

    @Test
    fun onBackPressedOnTaskRoot_taskIdMissMatch() {
        val taskId = 123
        whenever(expandedViewManager.isStackExpanded()).doReturn(true)

        getInstrumentation().runOnMainSync {
            bubbleTaskViewListener.onTaskCreated(taskId, mock<ComponentName>())
            bubbleTaskViewListener.onBackPressedOnTaskRoot(42)
        }
        verify(listenerCallback, never()).onBackPressed()
    }

    @Test
    fun setBubble_isNew() {
        val b = createAppBubble()
        val isNew = bubbleTaskViewListener.setBubble(b)
        assertThat(isNew).isTrue()
    }

    @Test
    fun setBubble_launchContentChanged() {
        val target = Intent(context, TestActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, target,
            PendingIntent.FLAG_MUTABLE
        )

        val b = createChatBubble("key", pendingIntent)
        var isNew = bubbleTaskViewListener.setBubble(b)
        // First time bubble is set, so it is "new"
        assertThat(isNew).isTrue()

        val b2 = createChatBubble("key", pendingIntent)
        isNew = bubbleTaskViewListener.setBubble(b2)
        // Second time bubble is set & it uses same type of launch content, not "new"
        assertThat(isNew).isFalse()

        val shortcutInfo = ShortcutInfo.Builder(context)
            .setId("mockShortcutId")
            .build()
        val b3 = createChatBubble("key", shortcutInfo)
        // bubble is using different content, so it is "new"
        isNew = bubbleTaskViewListener.setBubble(b3)
        assertThat(isNew).isTrue()
    }

    private fun createAppBubble(usePendingIntent: Boolean = false): Bubble {
        val target = Intent(context, TestActivity::class.java)
        val component = ComponentName(context, TestActivity::class.java)
        target.setPackage(context.packageName)
        target.setComponent(component)
        if (usePendingIntent) {
            // Robolectric doesn't seem to play nice with PendingIntents, have to mock it.
            val pendingIntent = mock<PendingIntent>()
            whenever(pendingIntent.intent).thenReturn(target)
            return Bubble.createAppBubble(pendingIntent, mock<UserHandle>(),
                mainExecutor, bgExecutor)
        }
        return Bubble.createAppBubble(target, mock<UserHandle>(), mock<Icon>(),
            mainExecutor, bgExecutor)
    }

    private fun createShortcutBubble(shortcutInfo: ShortcutInfo): Bubble {
        return Bubble.createShortcutBubble(shortcutInfo, mainExecutor, bgExecutor)
    }

    private fun createNoteBubble(): Bubble {
        val target = Intent(context, TestActivity::class.java)
        target.setPackage(context.packageName)
        return Bubble.createNotesBubble(target, mock<UserHandle>(), mock<Icon>(),
            mainExecutor, bgExecutor)
    }

    private fun createChatBubble(key: String, shortcutInfo: ShortcutInfo): Bubble {
        return Bubble(
            key,
            shortcutInfo,
            0 /* desiredHeight */,
            0 /* desiredHeightResId */,
            "title",
            -1 /*taskId */,
            null /* locusId */, true /* isdismissabel */,
            mainExecutor, bgExecutor, mock<BubbleMetadataFlagListener>()
        )
    }

    private fun createChatBubble(key: String, pendingIntent: PendingIntent): Bubble {
        val metadata = Notification.BubbleMetadata.Builder(
            pendingIntent,
            Icon.createWithResource(context, R.drawable.bubble_ic_create_bubble)
        ).build()
        val shortcutInfo = ShortcutInfo.Builder(context)
            .setId("shortcutId")
            .build()
        val notification: Notification =
            Notification.Builder(context, key)
                .setSmallIcon(mock<Icon>())
                .setWhen(System.currentTimeMillis())
                .setContentTitle("title")
                .setContentText("content")
                .setBubbleMetadata(metadata)
                .build()
        val sbn = mock<StatusBarNotification>()
        val ranking = mock<Ranking>()
        whenever(sbn.getNotification()).thenReturn(notification)
        whenever(sbn.getKey()).thenReturn(key)
        whenever(ranking.getConversationShortcutInfo()).thenReturn(shortcutInfo)
        val entry = BubbleEntry(sbn, ranking, true, false, false, false)
        return Bubble(
            entry, mock<BubbleMetadataFlagListener>(), null, mainExecutor,
            bgExecutor
        )
    }

    /**
     * FrameLayout that immediately runs any runnables posted to it and tracks view removals.
     */
    class ViewPoster(context: Context) : FrameLayout(context) {

        lateinit var lastRemovedView: View

        override fun post(r: Runnable): Boolean {
            r.run()
            return true
        }

        override fun removeView(v: View) {
            super.removeView(v)
            lastRemovedView = v
        }
    }
}
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

package com.android.wm.shell.bubbles

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Insets
import android.graphics.Rect
import android.os.Handler
import android.os.UserManager
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.IWindowManager
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.internal.protolog.ProtoLog
import com.android.internal.statusbar.IStatusBarService
import com.android.wm.shell.Flags
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.bubbles.Bubbles.SysuiProxy
import com.android.wm.shell.bubbles.storage.BubblePersistentRepository
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayImeController
import com.android.wm.shell.common.DisplayInsetsController
import com.android.wm.shell.common.FloatingContentCoordinator
import com.android.wm.shell.common.HomeIntentProvider
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.common.TaskStackListenerImpl
import com.android.wm.shell.common.TestShellExecutor
import com.android.wm.shell.draganddrop.DragAndDropController
import com.android.wm.shell.shared.TransactionPool
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import com.android.wm.shell.shared.bubbles.BubbleBarUpdate
import com.android.wm.shell.shared.bubbles.DeviceConfig
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.taskview.TaskViewTransitions
import com.android.wm.shell.transition.Transitions
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional

/** Tests for [BubbleController] when using bubble bar */
@SmallTest
@EnableFlags(Flags.FLAG_ENABLE_BUBBLE_BAR)
@RunWith(AndroidJUnit4::class)
class BubbleControllerBubbleBarTest {

    companion object {
        private const val SCREEN_WIDTH = 2000
        private const val SCREEN_HEIGHT = 1000
    }

    @get:Rule val setFlagsRule = SetFlagsRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var bubbleController: BubbleController
    private lateinit var uiEventLoggerFake: UiEventLoggerFake
    private lateinit var bubblePositioner: BubblePositioner
    private lateinit var bubbleData: BubbleData
    private lateinit var mainExecutor: TestShellExecutor
    private lateinit var bgExecutor: TestShellExecutor

    @Before
    fun setUp() {
        ProtoLog.REQUIRE_PROTOLOGTOOL = false
        ProtoLog.init()

        mainExecutor = TestShellExecutor()
        bgExecutor = TestShellExecutor()

        uiEventLoggerFake = UiEventLoggerFake()
        val bubbleLogger = BubbleLogger(uiEventLoggerFake)

        val deviceConfig =
            DeviceConfig(
                windowBounds = Rect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT),
                isLargeScreen = true,
                isSmallTablet = false,
                isLandscape = true,
                isRtl = false,
                insets = Insets.of(10, 20, 30, 40),
            )

        bubblePositioner = BubblePositioner(context, deviceConfig)
        bubblePositioner.isShowingInBubbleBar = true

        bubbleData =
            BubbleData(
                context,
                bubbleLogger,
                bubblePositioner,
                BubbleEducationController(context),
                mainExecutor,
                bgExecutor,
            )

        val shellInit = ShellInit(mainExecutor)

        bubbleController =
            createBubbleController(
                shellInit,
                bubbleData,
                bubbleLogger,
                bubblePositioner,
                mainExecutor,
                bgExecutor,
            )
        bubbleController.asBubbles().setSysuiProxy(mock<SysuiProxy>())

        shellInit.init()

        mainExecutor.flushAll()
        bgExecutor.flushAll()

        bubbleController.setLauncherHasBubbleBar(true)
        bubbleController.registerBubbleStateListener(FakeBubblesStateListener())
    }

    @After
    fun tearDown() {
        mainExecutor.flushAll()
        bgExecutor.flushAll()
        getInstrumentation().waitForIdleSync()
    }

    @Test
    fun testEventLogging_bubbleBar_dragBarLeft() {
        addBubble()

        bubblePositioner.bubbleBarLocation = BubbleBarLocation.RIGHT

        bubbleController.setBubbleBarLocation(
            BubbleBarLocation.LEFT,
            BubbleBarLocation.UpdateSource.DRAG_BAR,
        )

        // 2 events: add bubble + drag event
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(2)
        assertThat(uiEventLoggerFake.eventId(1))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_MOVED_LEFT_DRAG_BAR.id)
    }

    @Test
    fun testEventLogging_bubbleBar_dragBarRight() {
        addBubble()

        bubblePositioner.bubbleBarLocation = BubbleBarLocation.LEFT

        bubbleController.setBubbleBarLocation(
            BubbleBarLocation.RIGHT,
            BubbleBarLocation.UpdateSource.DRAG_BAR,
        )

        // 2 events: add bubble + drag event
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(2)
        assertThat(uiEventLoggerFake.eventId(1))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_MOVED_RIGHT_DRAG_BAR.id)
    }

    @Test
    fun testEventLogging_bubbleBar_dragBubbleLeft() {
        addBubble()

        bubblePositioner.bubbleBarLocation = BubbleBarLocation.RIGHT

        bubbleController.setBubbleBarLocation(
            BubbleBarLocation.LEFT,
            BubbleBarLocation.UpdateSource.DRAG_BUBBLE,
        )

        // 2 events: add bubble + drag event
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(2)
        assertThat(uiEventLoggerFake.eventId(1))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_MOVED_LEFT_DRAG_BUBBLE.id)
    }

    @Test
    fun testEventLogging_bubbleBar_dragBubbleRight() {
        addBubble()

        bubblePositioner.bubbleBarLocation = BubbleBarLocation.LEFT

        bubbleController.setBubbleBarLocation(
            BubbleBarLocation.RIGHT,
            BubbleBarLocation.UpdateSource.DRAG_BUBBLE,
        )

        // 2 events: add bubble + drag event
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(2)
        assertThat(uiEventLoggerFake.eventId(1))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_MOVED_RIGHT_DRAG_BUBBLE.id)
    }

    @Test
    fun testEventLogging_bubbleBar_addBubble() {
        addBubble()

        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        assertThat(uiEventLoggerFake.eventId(0))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_BUBBLE_POSTED.id)
    }

    @Test
    fun testEventLogging_bubbleBar_updateBubble() {
        val bubble = addBubble()
        uiEventLoggerFake.logs.clear()

        bubble.setTextChangedForTest(true)
        bubbleController.inflateAndAdd(bubble, false, true)

        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        assertThat(uiEventLoggerFake.eventId(0))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_BUBBLE_UPDATED.id)
    }

    @Test
    fun testEventLogging_bubbleBar_dragSelectedBubbleToDismiss() {
        addBubble("key1")
        addBubble("key2")
        expandAndSelectBubble("key2")
        uiEventLoggerFake.logs.clear()

        // Dismiss selected bubble
        assertThat(bubbleData.selectedBubbleKey).isEqualTo("key2")
        getInstrumentation().runOnMainSync {
            bubbleController.startBubbleDrag("key2")
            bubbleController.dragBubbleToDismiss("key2", System.currentTimeMillis())
        }
        // Log bubble dismissed via drag and there's a switch event
        assertThat(bubbleData.selectedBubbleKey).isEqualTo("key1")
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(2)
        assertThat(uiEventLoggerFake.eventId(0))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_BUBBLE_DISMISSED_DRAG_BUBBLE.id)
        assertThat(uiEventLoggerFake.eventId(1))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_BUBBLE_SWITCHED.id)
    }

    @Test
    fun testEventLogging_bubbleBar_dragOtherBubbleToDismiss() {
        addBubble("key1")
        addBubble("key2")
        expandAndSelectBubble("key2")

        uiEventLoggerFake.logs.clear()

        // Dismiss the non selected bubble
        assertThat(bubbleData.selectedBubbleKey).isEqualTo("key2")
        getInstrumentation().runOnMainSync {
            bubbleController.startBubbleDrag("key1")
            bubbleController.dragBubbleToDismiss("key1", System.currentTimeMillis())
        }

        // Log bubble dismissed via drag, but no switch event
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        assertThat(uiEventLoggerFake.eventId(0))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_BUBBLE_DISMISSED_DRAG_BUBBLE.id)
    }

    @Test
    fun testEventLogging_bubbleBar_dragBarToDismiss() {
        addBubble()
        uiEventLoggerFake.logs.clear()

        bubbleController.removeAllBubbles(Bubbles.DISMISS_USER_GESTURE)
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        assertThat(uiEventLoggerFake.eventId(0))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_DISMISSED_DRAG_BAR.id)
    }

    @Test
    fun testEventLogging_bubbleBar_blocked() {
        val bubble = addBubble()
        uiEventLoggerFake.logs.clear()

        bubbleController.removeBubble(bubble.key, Bubbles.DISMISS_NO_LONGER_BUBBLE)
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        assertThat(uiEventLoggerFake.eventId(0))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_BUBBLE_REMOVED_BLOCKED.id)
    }

    @Test
    fun testEventLogging_bubbleBar_notifCanceled() {
        val bubble = addBubble()
        uiEventLoggerFake.logs.clear()

        bubbleController.removeBubble(bubble.key, Bubbles.DISMISS_NOTIF_CANCEL)
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        assertThat(uiEventLoggerFake.eventId(0))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_BUBBLE_REMOVED_CANCELED.id)
    }

    @Test
    fun testEventLogging_bubbleBar_taskFinished() {
        val bubble = addBubble()
        uiEventLoggerFake.logs.clear()

        bubbleController.removeBubble(bubble.key, Bubbles.DISMISS_TASK_FINISHED)
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        assertThat(uiEventLoggerFake.eventId(0))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_BUBBLE_ACTIVITY_FINISH.id)
    }

    @Test
    fun testEventLogging_bubbleBar_expandAndCollapse() {
        addBubble("key")
        uiEventLoggerFake.logs.clear()

        expandAndSelectBubble("key")

        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        assertThat(uiEventLoggerFake.eventId(0))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_EXPANDED.id)
        uiEventLoggerFake.logs.clear()

        getInstrumentation().runOnMainSync {
            bubbleController.collapseStack()
        }

        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        assertThat(uiEventLoggerFake.eventId(0))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_COLLAPSED.id)
    }

    @Test
    fun testEventLogging_bubbleBar_autoExpandingBubble() {
        addBubble("key", autoExpand = true)

        // 2 events: add bubble + expand
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(2)
        assertThat(uiEventLoggerFake.eventId(1))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_EXPANDED.id)
    }

    @Test
    fun testEventLogging_bubbleBar_switchBubble() {
        addBubble("key1")
        addBubble("key2")
        expandAndSelectBubble("key2")
        assertThat(bubbleData.selectedBubbleKey).isEqualTo("key2")
        uiEventLoggerFake.logs.clear()

        // Select the next bubble
        expandAndSelectBubble("key1")

        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        assertThat(uiEventLoggerFake.eventId(0))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_BUBBLE_SWITCHED.id)
    }

    @Test
    fun testEventLogging_bubbleBar_openOverflow() {
        addBubble("key")
        expandAndSelectBubble("key")
        uiEventLoggerFake.logs.clear()

        expandAndSelectBubble(BubbleOverflow.KEY)

        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        assertThat(uiEventLoggerFake.eventId(0))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_OVERFLOW_SELECTED.id)
    }

    @Test
    fun testEventLogging_bubbleBar_fromOverflowToBar() {
        val bubble = addBubble()

        // Dismiss the bubble so it's in the overflow
        bubbleController.removeBubble(bubble.key, Bubbles.DISMISS_USER_GESTURE)
        val overflowBubble = bubbleData.getOverflowBubbleWithKey(bubble.key)
        assertThat(overflowBubble).isNotNull()

        // Promote overflow bubble and check that it is logged
        bubbleController.promoteBubbleFromOverflow(overflowBubble)

        // 2 events: add + remove
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(2)
        assertThat(uiEventLoggerFake.eventId(1))
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_OVERFLOW_REMOVE_BACK_TO_BAR.id)
    }

    private fun expandAndSelectBubble(key: String) {
        getInstrumentation().runOnMainSync {
            bubbleController.expandStackAndSelectBubbleFromLauncher(key, 0)
        }
    }

    private fun addBubble(key: String = "key", autoExpand: Boolean = false): Bubble {
        val bubble = FakeBubbleFactory.createChatBubble(context, key)
        bubble.setInflateSynchronously(true)
        bubble.setShouldAutoExpand(autoExpand)
        bubbleController.inflateAndAdd(bubble,
            /* suppressFlyout= */ true,
            /* showInShade= */ true,
            /* bubbleBarLocation = */ null,
        )
        return bubble
    }

    private fun createBubbleController(
        shellInit: ShellInit,
        bubbleData: BubbleData,
        bubbleLogger: BubbleLogger,
        bubblePositioner: BubblePositioner,
        mainExecutor: TestShellExecutor,
        bgExecutor: TestShellExecutor,
    ): BubbleController {
        val shellCommandHandler = ShellCommandHandler()
        val shellController =
            ShellController(
                context,
                shellInit,
                shellCommandHandler,
                mock<DisplayInsetsController>(),
                mock<UserManager>(),
                mainExecutor,
            )
        val surfaceSynchronizer = { obj: Runnable -> obj.run() }

        val bubbleDataRepository =
            BubbleDataRepository(
                mock<LauncherApps>(),
                mainExecutor,
                bgExecutor,
                BubblePersistentRepository(context),
            )

        val shellTaskOrganizer = mock<ShellTaskOrganizer>()
        whenever(shellTaskOrganizer.executor).thenReturn(directExecutor())

        return BubbleController(
            context,
            shellInit,
            shellCommandHandler,
            shellController,
            bubbleData,
            surfaceSynchronizer,
            FloatingContentCoordinator(),
            bubbleDataRepository,
            mock<BubbleTransitions>(),
            mock<IStatusBarService>(),
            mock<WindowManager>(),
            mock<DisplayInsetsController>(),
            mock<DisplayImeController>(),
            mock<UserManager>(),
            mock<LauncherApps>(),
            bubbleLogger,
            mock<TaskStackListenerImpl>(),
            shellTaskOrganizer,
            bubblePositioner,
            mock<DisplayController>(),
            /* oneHandedOptional= */ Optional.empty(),
            mock<DragAndDropController>(),
            mainExecutor,
            mock<Handler>(),
            bgExecutor,
            mock<TaskViewTransitions>(),
            mock<Transitions>(),
            SyncTransactionQueue(TransactionPool(), mainExecutor),
            mock<IWindowManager>(),
            BubbleResizabilityChecker(),
            HomeIntentProvider(context),
            FakeBubbleAppInfoProvider(),
            { Optional.empty() },
        )
    }

    private class FakeBubblesStateListener : Bubbles.BubbleStateListener {
        override fun onBubbleStateChange(update: BubbleBarUpdate?) {}

        override fun animateBubbleBarLocation(location: BubbleBarLocation?) {}

        override fun showBubbleBarPillowAt(location: BubbleBarLocation?) {}
    }
}

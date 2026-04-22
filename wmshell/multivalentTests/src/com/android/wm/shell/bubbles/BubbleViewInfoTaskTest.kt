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
import android.content.pm.ShortcutInfo
import android.content.res.Resources
import android.graphics.Color
import android.os.Handler
import android.os.UserManager
import android.view.IWindowManager
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.R
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.internal.protolog.ProtoLog
import com.android.internal.statusbar.IStatusBarService
import com.android.launcher3.icons.BubbleIconFactory
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.bubbles.storage.BubblePersistentRepository
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayImeController
import com.android.wm.shell.common.DisplayInsetsController
import com.android.wm.shell.common.FloatingContentCoordinator
import com.android.wm.shell.common.HomeIntentProvider
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.common.TaskStackListenerImpl
import com.android.wm.shell.common.TestShellExecutor
import com.android.wm.shell.shared.TransactionPool
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.taskview.TaskView
import com.android.wm.shell.taskview.TaskViewTransitions
import com.android.wm.shell.transition.Transitions
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import java.util.Optional
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Test inflating bubbles with [BubbleViewInfoTask]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleViewInfoTaskTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var metadataFlagListener: Bubbles.BubbleMetadataFlagListener
    private lateinit var iconFactory: BubbleIconFactory
    private lateinit var bubbleController: BubbleController
    private lateinit var mainExecutor: TestShellExecutor
    private lateinit var bgExecutor: TestShellExecutor
    private lateinit var bubbleStackView: BubbleStackView
    private lateinit var bubblePositioner: BubblePositioner
    private lateinit var bubbleLogger: BubbleLogger
    private lateinit var expandedViewManager: BubbleExpandedViewManager
    private lateinit var appInfoProvider: FakeBubbleAppInfoProvider

    private val bubbleTaskViewFactory = BubbleTaskViewFactory {
        BubbleTaskView(mock<TaskView>(), directExecutor())
    }

    @Before
    fun setUp() {
        ProtoLog.REQUIRE_PROTOLOGTOOL = false
        metadataFlagListener = Bubbles.BubbleMetadataFlagListener {}
        iconFactory =
            BubbleIconFactory(
                context,
                60,
                30,
                Color.RED,
                context.resources.getDimensionPixelSize(R.dimen.importance_ring_stroke_width)
            )

        mainExecutor = TestShellExecutor()
        bgExecutor = TestShellExecutor()
        val windowManager = context.getSystemService(WindowManager::class.java)
        val shellInit = ShellInit(mainExecutor)
        val shellCommandHandler = ShellCommandHandler()
        val shellController =
            ShellController(
                context,
                shellInit,
                shellCommandHandler,
                mock<DisplayInsetsController>(),
                mock<UserManager>(),
                mainExecutor
            )
        bubblePositioner = BubblePositioner(context, windowManager)
        bubbleLogger = BubbleLogger(UiEventLoggerFake())
        val bubbleData =
            BubbleData(
                context,
                bubbleLogger,
                bubblePositioner,
                BubbleEducationController(context),
                mainExecutor,
                bgExecutor
            )

        val surfaceSynchronizer = { obj: Runnable -> obj.run() }

        val bubbleDataRepository =
            BubbleDataRepository(
                mock<LauncherApps>(),
                mainExecutor,
                bgExecutor,
                BubblePersistentRepository(context)
            )

        appInfoProvider = FakeBubbleAppInfoProvider()

        bubbleController =
            BubbleController(
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
                windowManager,
                mock<DisplayInsetsController>(),
                mock<DisplayImeController>(),
                mock<UserManager>(),
                mock<LauncherApps>(),
                bubbleLogger,
                mock<TaskStackListenerImpl>(),
                mock<ShellTaskOrganizer>(),
                bubblePositioner,
                mock<DisplayController>(),
                null,
                null,
                mainExecutor,
                mock<Handler>(),
                bgExecutor,
                mock<TaskViewTransitions>(),
                mock<Transitions>(),
                SyncTransactionQueue(TransactionPool(), mainExecutor),
                mock<IWindowManager>(),
                BubbleResizabilityChecker(),
                HomeIntentProvider(context),
                appInfoProvider,
                { Optional.empty() },
            )

        // TODO: (b/371829099) - when optional overflow is no longer flagged we can enable this
        //  again, something about the overflow being added uncovers an issue with Robolectric and
        //  bitmaps; this is switched to a mock to work around that (b/375513387).
//        val bubbleStackViewManager = BubbleStackViewManager.fromBubbleController(bubbleController)
//        bubbleStackView = BubbleStackView(
//                context,
//                bubbleStackViewManager,
//                bubblePositioner,
//                bubbleData,
//                surfaceSynchronizer,
//                FloatingContentCoordinator(),
//                bubbleController,
//                mainExecutor
//            )
        bubbleStackView = mock<BubbleStackView>()
        whenever(bubbleStackView.generateLayoutParams(any()))
            .thenReturn(FrameLayout.LayoutParams(1000, 1000))
        whenever(bubbleStackView.generateLayoutParams(any(), any()))
            .thenReturn(FrameLayout.LayoutParams(1000, 1000))
        expandedViewManager = BubbleExpandedViewManager.fromBubbleController(bubbleController)
    }

    @Test
    fun start_runsOnExecutors() {
        val bubble = createBubbleWithShortcut()
        val task = createBubbleViewInfoTask(bubble)

        task.start()

        assertThat(bubble.isInflated).isFalse()
        assertThat(bubble.expandedView).isNull()
        assertThat(task.isFinished).isFalse()

        bgExecutor.flushAll()
        assertThat(bubble.isInflated).isFalse()
        assertThat(bubble.expandedView).isNull()
        assertThat(task.isFinished).isFalse()

        mainExecutor.flushAll()
        assertThat(bubble.isInflated).isTrue()
        assertThat(bubble.expandedView).isNotNull()
        assertThat(task.isFinished).isTrue()
    }

    @Test
    fun startSync_runsImmediately() {
        val bubble = createBubbleWithShortcut()
        val task = createBubbleViewInfoTask(bubble)

        task.startSync()
        assertThat(bubble.isInflated).isTrue()
        assertThat(bubble.expandedView).isNotNull()
        assertThat(task.isFinished).isTrue()
    }

    @Test
    fun start_calledTwice_throwsIllegalStateException() {
        val bubble = createBubbleWithShortcut()
        val task = createBubbleViewInfoTask(bubble)
        task.start()
        Assert.assertThrows(IllegalStateException::class.java) { task.start() }
    }

    @Test
    fun startSync_calledTwice_throwsIllegalStateException() {
        val bubble = createBubbleWithShortcut()
        val task = createBubbleViewInfoTask(bubble)
        task.startSync()
        Assert.assertThrows(IllegalStateException::class.java) { task.startSync() }
    }

    @Test
    fun start_callbackNotified() {
        val bubble = createBubbleWithShortcut()
        var bubbleFromCallback: Bubble? = null
        val callback = BubbleViewInfoTask.Callback { b: Bubble? -> bubbleFromCallback = b }
        val task = createBubbleViewInfoTask(bubble, callback)
        task.start()
        bgExecutor.flushAll()
        mainExecutor.flushAll()
        assertThat(bubbleFromCallback).isSameInstanceAs(bubble)
    }

    @Test
    fun startSync_callbackNotified() {
        val bubble = createBubbleWithShortcut()
        var bubbleFromCallback: Bubble? = null
        val callback = BubbleViewInfoTask.Callback { b: Bubble? -> bubbleFromCallback = b }
        val task = createBubbleViewInfoTask(bubble, callback)
        task.startSync()
        assertThat(bubbleFromCallback).isSameInstanceAs(bubble)
    }

    @Test
    fun cancel_beforeBackgroundWorkStarts_bubbleNotInflated() {
        val bubble = createBubbleWithShortcut()
        val task = createBubbleViewInfoTask(bubble)
        task.start()

        // Cancel before allowing background or main executor to run
        task.cancel()
        bgExecutor.flushAll()
        mainExecutor.flushAll()

        assertThat(bubble.isInflated).isFalse()
        assertThat(bubble.expandedView).isNull()
        assertThat(task.isFinished).isTrue()
    }

    @Test
    fun cancel_afterBackgroundWorkBeforeMainThreadWork_bubbleNotInflated() {
        val bubble = createBubbleWithShortcut()
        val task = createBubbleViewInfoTask(bubble)
        task.start()

        // Cancel after background executor runs, but before main executor runs
        bgExecutor.flushAll()
        task.cancel()
        mainExecutor.flushAll()

        assertThat(bubble.isInflated).isFalse()
        assertThat(bubble.expandedView).isNull()
        assertThat(task.isFinished).isTrue()
    }

    @Test
    fun cancel_beforeStart_bubbleNotInflated() {
        val bubble = createBubbleWithShortcut()
        val task = createBubbleViewInfoTask(bubble)
        task.cancel()
        task.start()
        bgExecutor.flushAll()
        mainExecutor.flushAll()

        assertThat(task.isFinished).isTrue()
        assertThat(bubble.isInflated).isFalse()
        assertThat(bubble.expandedView).isNull()
    }

    private fun createBubbleWithShortcut(): Bubble {
        val shortcutInfo = ShortcutInfo.Builder(context, "mockShortcutId").build()
        return Bubble(
            "mockKey",
            shortcutInfo,
            1000,
            Resources.ID_NULL,
            "mockTitle",
            0 /* taskId */,
            "mockLocus",
            true /* isDismissible */,
            mainExecutor,
            bgExecutor,
            metadataFlagListener
        )
    }

    private fun createBubbleViewInfoTask(
        bubble: Bubble,
        callback: BubbleViewInfoTask.Callback? = null
    ): BubbleViewInfoTask {
        return BubbleViewInfoTask(
            bubble,
            context,
            expandedViewManager,
            bubbleTaskViewFactory,
            bubblePositioner,
            bubbleStackView,
            null /* layerView */,
            iconFactory,
            appInfoProvider,
            false /* skipInflation */,
            callback,
            mainExecutor,
            bgExecutor
        )
    }
}

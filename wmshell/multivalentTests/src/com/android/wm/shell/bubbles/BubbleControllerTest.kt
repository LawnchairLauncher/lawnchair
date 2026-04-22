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
import android.app.TaskInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.content.res.Resources
import android.graphics.Insets
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.UserHandle
import android.os.UserManager
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import android.platform.test.flag.junit.SetFlagsRule
import android.view.IWindowManager
import android.view.InsetsSource
import android.view.InsetsState
import android.view.SurfaceControl
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.TransitionInfo
import android.window.WindowContainerToken
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.internal.protolog.ProtoLog
import com.android.internal.statusbar.IStatusBarService
import com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_BAR
import com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
import com.android.wm.shell.R
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.bubbles.Bubbles.BubbleExpandListener
import com.android.wm.shell.bubbles.Bubbles.SysuiProxy
import com.android.wm.shell.bubbles.storage.BubblePersistentRepository
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayImeController
import com.android.wm.shell.common.DisplayInsetsController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.FloatingContentCoordinator
import com.android.wm.shell.common.HomeIntentProvider
import com.android.wm.shell.common.ImeListener
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.common.TaskStackListenerImpl
import com.android.wm.shell.common.TestShellExecutor
import com.android.wm.shell.common.TestSyncExecutor
import com.android.wm.shell.draganddrop.DragAndDropController
import com.android.wm.shell.shared.TransactionPool
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper
import com.android.wm.shell.shared.bubbles.DeviceConfig
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.taskview.TaskViewRepository
import com.android.wm.shell.taskview.TaskViewTaskController
import com.android.wm.shell.taskview.TaskViewTransitions
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TRANSIT_CONVERT_TO_BUBBLE
import com.android.wm.shell.transition.Transitions.TransitionHandler
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import java.util.Optional
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.isA
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

/** Tests for [BubbleController].
 *
 * Build/Install/Run:
 *  atest WMShellRobolectricTests:BubbleControllerTest (on host)
 *  atest WMShellMultivalentTestsOnDevice:BubbleControllerTest (on device)
 */
@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class BubbleControllerTest(flags: FlagsParameterization) {

    @get:Rule
    val setFlagsRule = SetFlagsRule(flags)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val uiEventLoggerFake = UiEventLoggerFake()
    private val displayImeController = mock<DisplayImeController>()
    private val displayInsetsController = mock<DisplayInsetsController>()
    private val userManager = mock<UserManager>()
    private val taskStackListener = mock<TaskStackListenerImpl>()
    private val transitions = mock<Transitions>()
    private val taskViewTransitions = mock<TaskViewTransitions>()
    private val bubbleAppInfoProvider = FakeBubbleAppInfoProvider()

    private lateinit var bubbleController: BubbleController
    private lateinit var bubblePositioner: BubblePositioner
    private lateinit var bubbleLogger: BubbleLogger
    private lateinit var mainExecutor: TestShellExecutor
    private lateinit var bgExecutor: TestShellExecutor
    private lateinit var bubbleData: BubbleData
    private lateinit var eduController: BubbleEducationController
    private lateinit var displayController: DisplayController
    private lateinit var imeListener: ImeListener
    private lateinit var bubbleTransitions: BubbleTransitions
    private lateinit var shellTaskOrganizer: ShellTaskOrganizer

    private val deviceConfigFolded =
        DeviceConfig(
            windowBounds = Rect(0, 0, 700, 1000),
            isLargeScreen = false,
            isSmallTablet = false,
            isLandscape = false,
            isRtl = false,
            insets = Insets.of(10, 20, 30, 40),
        )
    private val deviceConfigUnfolded = deviceConfigFolded.copy(
        windowBounds = Rect(0, 0, 1400, 2000),
        isLargeScreen = true,
        isSmallTablet = true,
    )

    @Before
    fun setUp() {
        ProtoLog.REQUIRE_PROTOLOGTOOL = false
        ProtoLog.init()

        bubbleLogger = BubbleLogger(uiEventLoggerFake)
        eduController = BubbleEducationController(context)

        mainExecutor = TestShellExecutor()
        bgExecutor = TestShellExecutor()

        val realWindowManager = context.getSystemService<WindowManager>()!!
        val realDefaultDisplay = realWindowManager.defaultDisplay
        // Tests don't have permission to add our window to windowManager, so we mock it :(
        val windowManager = mock<WindowManager> {
            // But we do want the metrics from the real one
            on { currentWindowMetrics } doReturn realWindowManager.currentWindowMetrics
            on { defaultDisplay } doReturn realDefaultDisplay
        }
        displayController = mock<DisplayController> {
            on { getDisplayLayout(anyInt()) } doReturn DisplayLayout(context, realDefaultDisplay)
        }

        bubblePositioner = BubblePositioner(context, deviceConfigFolded)

        bubbleData =
            BubbleData(
                context,
                bubbleLogger,
                bubblePositioner,
                eduController,
                mainExecutor,
                bgExecutor,
            )

        shellTaskOrganizer =
            ShellTaskOrganizer(
                mock<ShellInit>(),
                ShellCommandHandler(),
                null,
                Optional.empty(),
                Optional.empty(),
                TestSyncExecutor(),
            )

        bubbleTransitions =
            BubbleTransitions(
                context,
                transitions,
                shellTaskOrganizer,
                mock<TaskViewRepository>(),
                bubbleData,
                taskViewTransitions,
                bubbleAppInfoProvider
            )

        bubbleController =
            createBubbleController(
                bubbleData,
                windowManager,
                bubbleLogger,
                bubblePositioner,
                mainExecutor,
                bgExecutor,
            )
        bubbleController.asBubbles().setSysuiProxy(mock<SysuiProxy>())
        // Flush so that proxy gets set
        mainExecutor.flushAll()

        val insetsChangedListenerCaptor = argumentCaptor<ImeListener>()
        verify(displayInsetsController)
            .addInsetsChangedListener(anyInt(), insetsChangedListenerCaptor.capture())
        imeListener = insetsChangedListenerCaptor.lastValue
    }

    @After
    fun tearDown() {
        getInstrumentation().waitForIdleSync()
    }

    @Test
    fun onInit_addsBubbleTaskStackListener() {
        verify(taskStackListener).addListener(isA<BubbleTaskStackListener>())
    }

    @Test
    fun showOrHideNotesBubble_createsNoteBubble() {
        val intent = Intent(context, TestActivity::class.java)
        intent.setPackage(context.packageName)
        val user = UserHandle.of(0)
        val expectedKey = Bubble.getNoteBubbleKeyForApp(intent.getPackage(), user)

        getInstrumentation().runOnMainSync {
            bubbleController.showOrHideNotesBubble(intent, user, mock<Icon>())
        }
        getInstrumentation().waitForIdleSync()

        assertThat(bubbleController.hasBubbles()).isTrue()
        assertThat(bubbleData.getAnyBubbleWithKey(expectedKey)).isNotNull()
        assertThat(bubbleData.getAnyBubbleWithKey(expectedKey)!!.isNote).isTrue()
    }

    @Test
    fun onDeviceLocked_expanded_imeHidden_shouldCollapseImmediately() {
        val bubble = createBubble("key")
        bubblePositioner.setImeVisible(false, 0)
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true
            )
        }
        assertThat(bubbleData.hasBubbles()).isTrue()

        // expand and lock the device
        getInstrumentation().runOnMainSync {
            bubbleController.expandStackAndSelectBubble(bubble)
            assertThat(bubbleData.isExpanded).isTrue()
            bubbleController.onStatusBarStateChanged(/* isShade= */ false)
        }
        // verify that we collapsed immediately, since the IME is hidden
        assertThat(bubbleData.isExpanded).isFalse()
    }

    @Test
    fun onDeviceLocked_expanded_imeVisible_shouldHideImeBeforeCollapsing() {
        val bubble = createBubble("key")
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true
            )
        }
        assertThat(bubbleData.hasBubbles()).isTrue()

        // expand and show the IME. then lock the device
        val imeVisibleInsetsState = createFakeInsetsState(imeVisible = true)
        getInstrumentation().runOnMainSync {
            bubbleController.expandStackAndSelectBubble(bubble)
            assertThat(bubbleData.isExpanded).isTrue()
            imeListener.insetsChanged(imeVisibleInsetsState)
            assertThat(bubblePositioner.isImeVisible).isTrue()
            bubbleController.onStatusBarStateChanged(/* isShade= */ false)
        }
        // check that we haven't actually started collapsing because we weren't notified yet that
        // the IME is hidden
        assertThat(bubbleData.isExpanded).isTrue()
        // collapsing while the device is locked goes through display ime controller
        verify(displayImeController).hideImeForBubblesWhenLocked(anyInt())

        // notify that the IME was hidden
        val imeHiddenInsetsState = createFakeInsetsState(imeVisible = false)
        getInstrumentation().runOnMainSync { imeListener.insetsChanged(imeHiddenInsetsState) }
        assertThat(bubblePositioner.isImeVisible).isFalse()
        // bubbles should be collapsed now
        assertThat(bubbleData.isExpanded).isFalse()
    }

    @Test
    fun onDeviceLocked_whileHidingImeDuringCollapse() {
        val bubble = createBubble("key")
        val expandListener = FakeBubbleExpandListener()
        bubbleController.setExpandListener(expandListener)

        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true
            )
        }
        assertThat(bubbleData.hasBubbles()).isTrue()

        // expand
        getInstrumentation().runOnMainSync {
            bubbleController.expandStackAndSelectBubble(bubble)
            assertThat(bubbleData.isExpanded).isTrue()
            mainExecutor.flushAll()
        }

        assertThat(expandListener.bubblesExpandedState).isEqualTo(mapOf("key" to true))

        // show the IME
        val imeVisibleInsetsState = createFakeInsetsState(imeVisible = true)
        getInstrumentation().runOnMainSync { imeListener.insetsChanged(imeVisibleInsetsState) }

        assertThat(bubblePositioner.isImeVisible).isTrue()

        // collapse the stack
        getInstrumentation().runOnMainSync { bubbleController.collapseStack() }
        assertThat(bubbleData.isExpanded).isFalse()
        // since we started to collapse while the IME was visible, we will wait to be notified that
        // the IME is hidden before completing the collapse. check that the expand listener was not
        // yet called
        assertThat(expandListener.bubblesExpandedState).isEqualTo(mapOf("key" to true))

        // lock the device during this state
        getInstrumentation().runOnMainSync {
            bubbleController.onStatusBarStateChanged(/* isShade= */ false)
        }
        verify(displayImeController).hideImeForBubblesWhenLocked(anyInt())

        // notify that the IME is hidden
        val imeHiddenInsetsState = createFakeInsetsState(imeVisible = false)
        getInstrumentation().runOnMainSync { imeListener.insetsChanged(imeHiddenInsetsState) }
        assertThat(bubblePositioner.isImeVisible).isFalse()
        // verify the collapse action completed
        assertThat(expandListener.bubblesExpandedState).isEqualTo(mapOf("key" to false))
    }

    @Test
    fun setTaskViewVisible_callsBaseTransitionsWithReorder() {
        val baseTransitions = mock<TaskViewTransitions>()
        val taskView = mock<TaskViewTaskController>()
        val bubbleTaskViewController = bubbleController.BubbleTaskViewController(baseTransitions)

        bubbleTaskViewController.setTaskViewVisible(taskView, true /* visible */)

        if (BubbleAnythingFlagHelper.enableCreateAnyBubbleWithForceExcludedFromRecents()) {
            verify(baseTransitions).setTaskViewVisible(
                taskView,
                true, /* visible */
                true, /* reorder */
                false, /* syncHiddenWithVisibilityOnReorder */
            )
        } else {
            verify(baseTransitions).setTaskViewVisible(taskView, true /* visible */)
        }
    }

    @Test
    fun setTaskViewVisible_lastBubbleRemoval_skipsTaskViewHiding() {
        assumeTrue(BubbleAnythingFlagHelper.enableCreateAnyBubbleWithForceExcludedFromRecents())

        val baseTransitions = mock<TaskViewTransitions>()
        val taskView = mock<TaskViewTaskController>()
        val bubbleTaskViewController = bubbleController.BubbleTaskViewController(baseTransitions)

        bubbleTaskViewController.setTaskViewVisible(taskView, false /* visible */)

        verify(baseTransitions, never()).setTaskViewVisible(
            any(), /* taskView */
            any(), /* visible */
            any(), /* reorder */
            any(), /* syncHiddenWithVisibilityOnReorder */
        )
    }

    @Test
    fun hasStableBubbleForTask_whenBubbleIsCollapsed_returnsTrue() {
        val taskId = 777
        val bubble = createBubble("key", taskId)
        getInstrumentation().runOnMainSync {
            bubbleData.notificationEntryUpdated(
                bubble,
                true /* suppressFlyout */,
                true /* showInShade= */,
            )
        }

        assertThat(bubbleController.hasStableBubbleForTask(taskId)).isTrue()
    }

    @Test
    fun hasStableBubbleForTask_whenBubbleInTransition_returnsFalse() {
        val taskId = 777
        val bubble = createBubble("key", taskId).apply { preparingTransition = mock() }
        getInstrumentation().runOnMainSync {
            bubbleData.notificationEntryUpdated(
                bubble,
                true /* suppressFlyout */,
                true /* showInShade= */,
            )
        }

        assertThat(bubbleController.hasStableBubbleForTask(taskId)).isFalse()
    }

    @Test
    fun hasStableBubbleForTask_noBubble_returnsFalse() {
        val bubble = createBubble("key", taskId = 123)
        getInstrumentation().runOnMainSync {
            bubbleData.notificationEntryUpdated(
                bubble,
                true /* suppressFlyout */,
                true /* showInShade= */,
            )
        }

        assertThat(bubbleController.hasStableBubbleForTask(777)).isFalse()
    }

    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun expandStackAndSelectBubbleForExistingTransition_reusesExistingBubble() {
        assumeTrue(BubbleAnythingFlagHelper.enableCreateAnyBubble())

        // switch to bubble bar mode because the transition currently requires bubble layer view
        bubblePositioner.update(deviceConfigUnfolded)
        bubblePositioner.isShowingInBubbleBar = true
        getInstrumentation().runOnMainSync {
            bubbleController.setLauncherHasBubbleBar(true)
            bubbleController.registerBubbleStateListener(FakeBubblesStateListener())
        }

        val taskInfo = ActivityManager.RunningTaskInfo().apply {
            taskId = 123
            baseActivity = COMPONENT
        }
        val bubble = createAppBubble(taskInfo)
        getInstrumentation().runOnMainSync {
            bubbleData.notificationEntryUpdated(
                bubble,
                true /* suppressFlyout */,
                true /* showInShade= */,
            )
        }

        getInstrumentation().runOnMainSync {
            bubbleController.expandStackAndSelectBubbleForExistingTransition(
                taskInfo,
                mock(), /* transition */
            ) {}
        }

        assertThat(bubbleData.selectedBubble).isEqualTo(bubble)
        assertThat(bubbleController.isStackExpanded).isTrue()
    }

    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun expandStackAndSelectBubbleForExistingTransition_newBubble() {
        assumeTrue(BubbleAnythingFlagHelper.enableCreateAnyBubble())

        // switch to bubble bar mode because the transition currently requires bubble layer view
        bubblePositioner.update(deviceConfigUnfolded)
        bubblePositioner.isShowingInBubbleBar = true
        getInstrumentation().runOnMainSync {
            bubbleController.setLauncherHasBubbleBar(true)
            bubbleController.registerBubbleStateListener(FakeBubblesStateListener())
        }

        val taskInfo = ActivityManager.RunningTaskInfo().apply {
            baseActivity = COMPONENT
            taskId = 123
            token = mock<WindowContainerToken>()
        }

        var transitionHandler: TransitionHandler? = null
        getInstrumentation().runOnMainSync {
            transitionHandler = bubbleController.expandStackAndSelectBubbleForExistingTransition(
                taskInfo,
                mock(), /* transition */
            ) {}
        }

        assertThat(transitionHandler).isNotNull()

        val leash = SurfaceControl.Builder().setName("taskLeash").build()
        val transitionInfo = TransitionInfo(TRANSIT_CONVERT_TO_BUBBLE, 0)
        val change = TransitionInfo.Change(taskInfo.token, leash).apply {
            setTaskInfo(taskInfo)
            mode = TRANSIT_CHANGE
        }
        transitionInfo.addChange(change)
        transitionInfo.addRoot(TransitionInfo.Root(0, mock(), 0, 0))
        getInstrumentation().runOnMainSync {
            transitionHandler!!.startAnimation(mock(), transitionInfo, mock(), mock(), mock())
        }

        assertThat(bubbleData.getBubbleInStackWithKey("key_app_bubble:123")).isNotNull()
    }

    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun convertExpandedBubbleToBar_startsConvertingToBar() {
        val bubble = createBubble("key")
        bubble.setShouldAutoExpand(true)
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true
            )
        }
        assertThat(bubbleData.hasBubbles()).isTrue()
        assertThat(bubbleData.isExpanded).isTrue()
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble)
        assertThat(bubble.taskView).isNotNull()

        bubblePositioner.update(deviceConfigUnfolded)
        bubblePositioner.isShowingInBubbleBar = true
        getInstrumentation().runOnMainSync {
            bubbleController.setLauncherHasBubbleBar(true)
            bubbleController.registerBubbleStateListener(FakeBubblesStateListener())
        }

        assertThat(bubble.isConvertingToBar).isTrue()
    }

    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun convertExpandedBubbleToBar_updatesTaskViewParent() {
        val bubble = createBubble("key")
        bubble.setShouldAutoExpand(true)
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true
            )
        }
        assertThat(bubbleData.hasBubbles()).isTrue()
        assertThat(bubbleData.isExpanded).isTrue()
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble)
        assertThat(bubble.taskView).isNotNull()

        assertThat(bubble.taskView.parent.parent).isEqualTo(bubble.expandedView)

        bubblePositioner.update(deviceConfigUnfolded)
        bubblePositioner.isShowingInBubbleBar = true
        getInstrumentation().runOnMainSync {
            bubbleController.setLauncherHasBubbleBar(true)
            bubbleController.registerBubbleStateListener(FakeBubblesStateListener())
        }

        assertThat(bubble.taskView.parent).isEqualTo(bubble.bubbleBarExpandedView)
    }

    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    @Test
    fun convertExpandedBubbleToBar_screenOff_doesNotCollapse() {
        val bubble = createBubble("key")
        bubble.setShouldAutoExpand(true)
        getInstrumentation().runOnMainSync {
            bubbleController.inflateAndAdd(
                bubble,
                /* suppressFlyout= */ true,
                /* showInShade= */ true
            )
        }
        assertThat(bubbleData.hasBubbles()).isTrue()
        assertThat(bubbleData.isExpanded).isTrue()
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble)
        assertThat(bubble.taskView).isNotNull()

        assertThat(bubble.taskView.parent.parent).isEqualTo(bubble.expandedView)

        bubblePositioner.update(deviceConfigUnfolded)
        bubblePositioner.isShowingInBubbleBar = true
        getInstrumentation().runOnMainSync {
            bubbleController.setLauncherHasBubbleBar(true)
            bubbleController.registerBubbleStateListener(FakeBubblesStateListener())
        }

        bubbleController.broadcastReceiver.onReceive(context, Intent(Intent.ACTION_SCREEN_OFF))
        assertThat(bubbleData.isExpanded).isTrue()
    }

    private fun createBubble(key: String, taskId: Int = 0): Bubble {
        val icon = Icon.createWithResource(context.resources, R.drawable.bubble_ic_overflow_button)
        val shortcutInfo = ShortcutInfo.Builder(context, "fakeId").setIcon(icon).build()
        val bubble =
            Bubble(
                key,
                shortcutInfo,
                /* desiredHeight= */ 0,
                Resources.ID_NULL,
                "title",
                taskId,
                "locus",
                /* isDismissable= */ true,
                directExecutor(),
                directExecutor(),
            ) {}
        return bubble
    }

    private fun createAppBubble(taskInfo: TaskInfo): Bubble {
        return Bubble.createTaskBubble(taskInfo, UserHandle.of(0), null, mainExecutor, bgExecutor)
    }

    private fun createBubbleController(
        bubbleData: BubbleData,
        windowManager: WindowManager,
        bubbleLogger: BubbleLogger,
        bubblePositioner: BubblePositioner,
        mainExecutor: TestShellExecutor,
        bgExecutor: TestShellExecutor,
    ): BubbleController {
        val shellInit = ShellInit(mainExecutor)
        val shellCommandHandler = ShellCommandHandler()
        val shellController =
            ShellController(
                context,
                shellInit,
                shellCommandHandler,
                displayInsetsController,
                userManager,
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

        val resizeChecker = ResizabilityChecker { _, _, _ -> true }

        val bubbleController =
            BubbleController(
                context,
                shellInit,
                shellCommandHandler,
                shellController,
                bubbleData,
                surfaceSynchronizer,
                FloatingContentCoordinator(),
                bubbleDataRepository,
                bubbleTransitions,
                mock<IStatusBarService>(),
                windowManager,
                displayInsetsController,
                displayImeController,
                userManager,
                mock<LauncherApps>(),
                bubbleLogger,
                taskStackListener,
                shellTaskOrganizer,
                bubblePositioner,
                displayController,
                Optional.empty(),
                mock<DragAndDropController>(),
                mainExecutor,
                mock<Handler>(),
                bgExecutor,
                taskViewTransitions,
                transitions,
                SyncTransactionQueue(TransactionPool(), mainExecutor),
                mock<IWindowManager>(),
                resizeChecker,
                HomeIntentProvider(context),
                bubbleAppInfoProvider,
                { Optional.empty() },
            )
        bubbleController.setInflateSynchronously(true)
        bubbleController.onInit()

        return bubbleController
    }

    private class FakeBubbleExpandListener : BubbleExpandListener {
        val bubblesExpandedState = mutableMapOf<String, Boolean>()

        override fun onBubbleExpandChanged(isExpanding: Boolean, key: String) {
            bubblesExpandedState[key] = isExpanding
        }
    }

    companion object {
        private val COMPONENT = ComponentName("com.example.app", "com.example.app.MainActivity")

        private fun createFakeInsetsState(imeVisible: Boolean): InsetsState {
            val insetsState = InsetsState()
            if (imeVisible) {
                insetsState
                    .getOrCreateSource(InsetsSource.ID_IME, WindowInsets.Type.ime())
                    .setFrame(Rect(0, 100, 100, 200))
                    .setVisible(true)
            }
            return insetsState
        }

        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams() = FlagsParameterization.allCombinationsOf(
            FLAG_ENABLE_CREATE_ANY_BUBBLE,
        )
    }
}

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

package com.android.wm.shell.bubbles.bar

import android.animation.AnimatorTestRule
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Insets
import android.graphics.PointF
import android.graphics.Rect
import android.os.Handler
import android.os.UserManager
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.IWindowManager
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.view.children
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.internal.protolog.ProtoLog
import com.android.internal.statusbar.IStatusBarService
import com.android.wm.shell.Flags
import com.android.wm.shell.R
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.bubbles.Bubble
import com.android.wm.shell.bubbles.BubbleController
import com.android.wm.shell.bubbles.BubbleData
import com.android.wm.shell.bubbles.BubbleDataRepository
import com.android.wm.shell.bubbles.BubbleExpandedViewManager
import com.android.wm.shell.bubbles.BubbleLogger
import com.android.wm.shell.bubbles.BubblePositioner
import com.android.wm.shell.bubbles.BubbleResizabilityChecker
import com.android.wm.shell.bubbles.BubbleTransitions
import com.android.wm.shell.bubbles.Bubbles.SysuiProxy
import com.android.wm.shell.bubbles.FakeBubbleAppInfoProvider
import com.android.wm.shell.bubbles.FakeBubbleExpandedViewManager
import com.android.wm.shell.bubbles.FakeBubbleFactory
import com.android.wm.shell.bubbles.FakeBubbleTaskViewFactory
import com.android.wm.shell.bubbles.UiEventSubject.Companion.assertThat
import com.android.wm.shell.bubbles.animation.AnimatableScaleMatrix
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
import com.android.wm.shell.shared.animation.PhysicsAnimatorTestUtils
import com.android.wm.shell.shared.bubbles.BubbleBarLocation
import com.android.wm.shell.shared.bubbles.DeviceConfig
import com.android.wm.shell.shared.bubbles.DragZone
import com.android.wm.shell.shared.bubbles.DragZoneFactory
import com.android.wm.shell.shared.bubbles.DragZoneFactory.SplitScreenModeChecker.SplitScreenMode
import com.android.wm.shell.shared.bubbles.DraggedObject
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.taskview.TaskViewTransitions
import com.android.wm.shell.transition.Transitions
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Tests for [BubbleBarLayerView] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleBarLayerViewTest {
    companion object {
        const val SCREEN_WIDTH = 2000
        const val SCREEN_HEIGHT = 1000
    }

    @get:Rule val setFlagsRule = SetFlagsRule()

    @get:Rule val animatorTestRule: AnimatorTestRule = AnimatorTestRule(this)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var bubbleBarLayerView: BubbleBarLayerView
    private lateinit var uiEventLoggerFake: UiEventLoggerFake
    private lateinit var bubbleController: BubbleController
    private lateinit var bubblePositioner: BubblePositioner
    private lateinit var expandedViewManager: BubbleExpandedViewManager
    private lateinit var mainExecutor: TestShellExecutor
    private lateinit var bgExecutor: TestShellExecutor
    private lateinit var bubbleLogger: BubbleLogger
    private lateinit var testBubblesList: MutableList<Bubble>
    private lateinit var dragZoneFactory: DragZoneFactory

    @Before
    fun setUp() {
        ProtoLog.REQUIRE_PROTOLOGTOOL = false
        ProtoLog.init()
        PhysicsAnimatorTestUtils.prepareForTest()

        uiEventLoggerFake = UiEventLoggerFake()
        bubbleLogger = BubbleLogger(uiEventLoggerFake)

        mainExecutor = TestShellExecutor()
        bgExecutor = TestShellExecutor()

        val windowManager = context.getSystemService(WindowManager::class.java)

        bubblePositioner = BubblePositioner(context, windowManager)
        bubblePositioner.setShowingInBubbleBar(true)
        val deviceConfig =
            DeviceConfig(
                windowBounds = Rect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT),
                isLargeScreen = true,
                isSmallTablet = false,
                isLandscape = true,
                isRtl = false,
                insets = Insets.of(10, 20, 30, 40)
            )
        bubblePositioner.update(deviceConfig)

        testBubblesList = mutableListOf()
        val bubbleData = mock<BubbleData>()
        whenever(bubbleData.bubbles).thenReturn(testBubblesList)
        whenever(bubbleData.hasBubbles()).thenReturn(testBubblesList.isNotEmpty())

        val bubbleBarPropertiesProvider = object : DragZoneFactory.BubbleBarPropertiesProvider {
            override fun getHeight() = 60
            override fun getWidth() = 90
            override fun getBottomPadding() = 20
        }
        dragZoneFactory =
            DragZoneFactory(
                context,
                deviceConfig,
                { SplitScreenMode.UNSUPPORTED },
                { false },
                bubbleBarPropertiesProvider
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

        bubbleBarLayerView = BubbleBarLayerView(context, bubbleController, bubbleData, bubbleLogger)

        expandedViewManager = FakeBubbleExpandedViewManager(bubbleBar = true, expanded = true)
    }

    @After
    fun tearDown() {
        PhysicsAnimatorTestUtils.tearDown()
        getInstrumentation().waitForIdleSync()
    }

    private fun createBubbleController(
        bubbleData: BubbleData,
        windowManager: WindowManager?,
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
            FakeBubbleAppInfoProvider(),
            { Optional.empty() },
        )
    }

    @Test
    fun showExpandedView() {
        val bubble = createBubble("first")

        getInstrumentation().runOnMainSync { bubbleBarLayerView.showExpandedView(bubble) }
        waitForExpandedViewAnimation()

        // Scrim, dismiss view and expanded view
        assertThat(bubbleBarLayerView.childCount).isEqualTo(3)
        assertThat(bubbleBarLayerView.getChildAt(2)).isEqualTo(bubble.bubbleBarExpandedView)
    }

    @Test
    fun twoBubbles_dismissActiveBubble_newBubbleShown() {
        val firstBubble = createBubble("first")
        val secondBubble = createBubble("second")

        getInstrumentation().runOnMainSync { bubbleBarLayerView.showExpandedView(firstBubble) }
        waitForExpandedViewAnimation()

        getInstrumentation().runOnMainSync { bubbleBarLayerView.removeBubble(firstBubble) {} }
        // Expanded view is removed when bubble is removed
        assertThat(firstBubble.bubbleBarExpandedView).isNull()

        getInstrumentation().runOnMainSync { bubbleBarLayerView.showExpandedView(secondBubble) }
        waitForExpandedViewAnimation()

        assertThat(bubbleBarLayerView.children.count { it is BubbleBarExpandedView }).isEqualTo(1)
        assertThat(bubbleBarLayerView.children.last()).isEqualTo(secondBubble.bubbleBarExpandedView)
    }

    @Test
    fun twoBubbles_switchBubbles_newBubbleShown() {
        val firstBubble = createBubble("first")
        val secondBubble = createBubble("second")

        getInstrumentation().runOnMainSync { bubbleBarLayerView.showExpandedView(firstBubble) }
        waitForExpandedViewAnimation()

        getInstrumentation().runOnMainSync { bubbleBarLayerView.showExpandedView(secondBubble) }
        waitForExpandedViewAnimation()

        assertThat(bubbleBarLayerView.children.count { it is BubbleBarExpandedView }).isEqualTo(1)
        assertThat(bubbleBarLayerView.children.last()).isEqualTo(secondBubble.bubbleBarExpandedView)
    }

    @Test
    fun twoBubbles_removeBubbleInTransition_skipCollapse() {
        val firstBubble = createBubble("first")
        val secondBubble = createBubble("second")


        getInstrumentation().runOnMainSync { bubbleBarLayerView.showExpandedView(firstBubble) }
        waitForExpandedViewAnimation()

        getInstrumentation().runOnMainSync { bubbleBarLayerView.showExpandedView(secondBubble) }
        waitForExpandedViewAnimation()

        firstBubble.preparingTransition = object : BubbleTransitions.BubbleTransition {}

        getInstrumentation().runOnMainSync { bubbleBarLayerView.removeBubble(firstBubble) {} }

        assertThat(bubbleBarLayerView.isExpanded).isTrue()
    }

    @Test
    fun testEventLogging_dismissExpandedViewViaDrag() {
        val bubble = createBubble("first")
        getInstrumentation().runOnMainSync { bubbleBarLayerView.showExpandedView(bubble) }
        assertThat(bubbleBarLayerView.findViewById<View>(R.id.bubble_bar_handle_view)).isNotNull()

        bubbleBarLayerView.dragController?.dragListener?.onReleased(true)

        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        assertThat(uiEventLoggerFake.logs[0].eventId)
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_BUBBLE_DISMISSED_DRAG_EXP_VIEW.id)
        assertThat(uiEventLoggerFake.logs[0]).hasBubbleInfo(bubble)
    }

    @DisableFlags(Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN, Flags.FLAG_ENABLE_BUBBLE_ANYTHING)
    @Test
    fun testEventLogging_dragExpandedViewLeft() {
        val bubble = createBubble("first")
        bubblePositioner.bubbleBarLocation = BubbleBarLocation.RIGHT

        getInstrumentation().runOnMainSync {
            bubbleBarLayerView.showExpandedView(bubble)
            bubble.bubbleBarExpandedView!!.onContentVisibilityChanged(true /* visible */)
        }
        waitForExpandedViewAnimation()

        val handleView = bubbleBarLayerView.findViewById<View>(R.id.bubble_bar_handle_view)
        assertThat(handleView).isNotNull()

        // Drag from right to left
        handleView.dispatchTouchEvent(0L, MotionEvent.ACTION_DOWN, rightEdge())
        handleView.dispatchTouchEvent(10L, MotionEvent.ACTION_MOVE, leftEdge())
        handleView.dispatchTouchEvent(20L, MotionEvent.ACTION_UP, leftEdge())

        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        assertThat(uiEventLoggerFake.logs[0].eventId)
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_MOVED_LEFT_DRAG_EXP_VIEW.id)
        assertThat(uiEventLoggerFake.logs[0]).hasBubbleInfo(bubble)
    }

    @DisableFlags(Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN, Flags.FLAG_ENABLE_BUBBLE_ANYTHING)
    @Test
    fun testEventLogging_dragExpandedViewRight() {
        val bubble = createBubble("first")
        bubblePositioner.bubbleBarLocation = BubbleBarLocation.LEFT

        getInstrumentation().runOnMainSync {
            bubbleBarLayerView.showExpandedView(bubble)
            bubble.bubbleBarExpandedView!!.onContentVisibilityChanged(true /* visible */)
        }
        waitForExpandedViewAnimation()

        val handleView = bubbleBarLayerView.findViewById<View>(R.id.bubble_bar_handle_view)
        assertThat(handleView).isNotNull()

        // Drag from left to right
        handleView.dispatchTouchEvent(0L, MotionEvent.ACTION_DOWN, leftEdge())
        handleView.dispatchTouchEvent(10L, MotionEvent.ACTION_MOVE, rightEdge())
        handleView.dispatchTouchEvent(20L, MotionEvent.ACTION_UP, rightEdge())

        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        assertThat(uiEventLoggerFake.logs[0].eventId)
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_MOVED_RIGHT_DRAG_EXP_VIEW.id)
        assertThat(uiEventLoggerFake.logs[0]).hasBubbleInfo(bubble)
    }

    @EnableFlags(Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN, Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @Test
    fun testEventLogging_dragExpandedViewLeft_bubbleAnything() {
        val bubble = createBubble("first")
        bubblePositioner.bubbleBarLocation = BubbleBarLocation.RIGHT

        getInstrumentation().runOnMainSync {
            bubbleBarLayerView.showExpandedView(bubble)
            bubble.bubbleBarExpandedView!!.onContentVisibilityChanged(true /* visible */)
        }
        waitForExpandedViewAnimation()

        val handleView = bubbleBarLayerView.findViewById<View>(R.id.bubble_bar_handle_view)
        assertThat(handleView).isNotNull()

        val dragZones = dragZoneFactory.createSortedDragZones(
            DraggedObject.ExpandedView(BubbleBarLocation.RIGHT))
        val rightDragZone = dragZones.filterIsInstance<DragZone.Bubble.Right>().first()
        val rightPoint = PointF(rightDragZone.bounds.rect.centerX().toFloat(),
            rightDragZone.bounds.rect.centerY().toFloat())
        val leftDragZone = dragZones.filterIsInstance<DragZone.Bubble.Left>().first()
        val leftPoint = PointF(leftDragZone.bounds.rect.centerX().toFloat(),
            leftDragZone.bounds.rect.centerY().toFloat())

        // Drag from right to left
        handleView.dispatchTouchEvent(0L, MotionEvent.ACTION_DOWN, rightPoint)
        handleView.dispatchTouchEvent(10L, MotionEvent.ACTION_MOVE, leftPoint)
        handleView.dispatchTouchEvent(20L, MotionEvent.ACTION_UP, leftPoint)

        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        assertThat(uiEventLoggerFake.logs[0].eventId)
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_MOVED_LEFT_DRAG_EXP_VIEW.id)
        assertThat(uiEventLoggerFake.logs[0]).hasBubbleInfo(bubble)
    }

    @EnableFlags(Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN, Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE)
    @Test
    fun testEventLogging_dragExpandedViewRight_bubbleAnything() {
        val bubble = createBubble("first")
        bubblePositioner.bubbleBarLocation = BubbleBarLocation.LEFT

        getInstrumentation().runOnMainSync {
            bubbleBarLayerView.showExpandedView(bubble)
            bubble.bubbleBarExpandedView!!.onContentVisibilityChanged(true /* visible */)
        }
        waitForExpandedViewAnimation()

        val handleView = bubbleBarLayerView.findViewById<View>(R.id.bubble_bar_handle_view)
        assertThat(handleView).isNotNull()

        val dragZones = dragZoneFactory.createSortedDragZones(
            DraggedObject.ExpandedView(BubbleBarLocation.LEFT))
        val rightDragZone = dragZones.filterIsInstance<DragZone.Bubble.Right>().first()
        val rightPoint = PointF(rightDragZone.bounds.rect.centerX().toFloat(),
            rightDragZone.bounds.rect.centerY().toFloat())
        val leftDragZone = dragZones.filterIsInstance<DragZone.Bubble.Left>().first()
        val leftPoint = PointF(leftDragZone.bounds.rect.centerX().toFloat(),
            leftDragZone.bounds.rect.centerY().toFloat())

        // Drag from left to right
        handleView.dispatchTouchEvent(0L, MotionEvent.ACTION_DOWN, leftPoint)
        handleView.dispatchTouchEvent(10L, MotionEvent.ACTION_MOVE, rightPoint)
        handleView.dispatchTouchEvent(20L, MotionEvent.ACTION_UP, rightPoint)

        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        assertThat(uiEventLoggerFake.logs[0].eventId)
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_MOVED_RIGHT_DRAG_EXP_VIEW.id)
        assertThat(uiEventLoggerFake.logs[0]).hasBubbleInfo(bubble)
    }

    @Test
    fun testUpdateExpandedView_updateLocation() {
        bubblePositioner.bubbleBarLocation = BubbleBarLocation.RIGHT
        val bubble = createBubble("first")

        getInstrumentation().runOnMainSync {
            bubbleBarLayerView.showExpandedView(bubble)
        }
        waitForExpandedViewAnimation()

        val previousX = bubble.bubbleBarExpandedView!!.x

        bubblePositioner.bubbleBarLocation = BubbleBarLocation.LEFT
        getInstrumentation().runOnMainSync {
            bubbleBarLayerView.updateExpandedView()
        }

        assertThat(bubble.bubbleBarExpandedView!!.x).isNotEqualTo(previousX)
    }

    @Test
    fun testUpdatedExpandedView_updateLocation_skipWhileAnimating() {
        bubblePositioner.bubbleBarLocation = BubbleBarLocation.RIGHT
        val bubble = createBubble("first")

        getInstrumentation().runOnMainSync {
            bubbleBarLayerView.showExpandedView(bubble)
        }
        waitForExpandedViewAnimation()

        val previousX = bubble.bubbleBarExpandedView!!.x
        bubble.bubbleBarExpandedView!!.isAnimating = true

        bubblePositioner.bubbleBarLocation = BubbleBarLocation.LEFT
        getInstrumentation().runOnMainSync {
            bubbleBarLayerView.updateExpandedView()
        }

        // Expanded view is not updated while animating
        assertThat(bubble.bubbleBarExpandedView!!.x).isEqualTo(previousX)
    }

    private fun createBubble(key: String): Bubble {
        val bubble = FakeBubbleFactory.createChatBubble(context, key).also {
            testBubblesList.add(it)
        }
        val bubbleTaskView = FakeBubbleTaskViewFactory(context, mainExecutor).create()
        bubbleTaskView.listener.onTaskCreated(/* taskId= */ 1, ComponentName("package", "class"))
        val bubbleBarExpandedView =
            FakeBubbleFactory.createExpandedView(
                context,
                bubblePositioner,
                expandedViewManager,
                bubble,
                bubbleTaskView,
                mainExecutor,
                bgExecutor,
                bubbleLogger,
            )
        // Mark visible so we don't wait for task view before animations can start
        bubbleBarExpandedView.onContentVisibilityChanged(true /* visible */)

        return bubble
    }

    private fun leftEdge(): PointF {
        val screenSize = bubblePositioner.availableRect
        return PointF(screenSize.left.toFloat(), screenSize.height() / 2f)
    }

    private fun rightEdge(): PointF {
        val screenSize = bubblePositioner.availableRect
        return PointF(screenSize.right.toFloat(), screenSize.height() / 2f)
    }

    private fun waitForExpandedViewAnimation() {
        // wait for idle to allow the animation to start
        getInstrumentation().runOnMainSync { animatorTestRule.advanceTimeBy(1000) }
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(
            AnimatableScaleMatrix.SCALE_X,
            AnimatableScaleMatrix.SCALE_Y,
        )
        getInstrumentation().waitForIdleSync()
    }

    private fun View.dispatchTouchEvent(eventTime: Long, action: Int, point: PointF) {
        val event = MotionEvent.obtain(0L, eventTime, action, point.x, point.y, 0)
        getInstrumentation().runOnMainSync { dispatchTouchEvent(event) }
    }
}

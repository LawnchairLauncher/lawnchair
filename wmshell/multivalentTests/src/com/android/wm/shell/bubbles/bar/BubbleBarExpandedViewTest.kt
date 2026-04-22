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

import android.content.ComponentName
import android.content.Context
import android.graphics.Insets
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.R
import com.android.wm.shell.bubbles.Bubble
import com.android.wm.shell.bubbles.BubbleExpandedViewManager
import com.android.wm.shell.bubbles.BubbleLogger
import com.android.wm.shell.bubbles.BubblePositioner
import com.android.wm.shell.bubbles.BubbleTaskView
import com.android.wm.shell.bubbles.FakeBubbleExpandedViewManager
import com.android.wm.shell.bubbles.FakeBubbleFactory
import com.android.wm.shell.bubbles.FakeBubbleTaskViewFactory
import com.android.wm.shell.bubbles.RegionSamplingProvider
import com.android.wm.shell.bubbles.UiEventSubject.Companion.assertThat
import com.android.wm.shell.common.TestShellExecutor
import com.android.wm.shell.shared.bubbles.DeviceConfig
import com.android.wm.shell.shared.handles.RegionSamplingHelper
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executor

/** Tests for [BubbleBarExpandedViewTest] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleBarExpandedViewTest {
    companion object {
        const val SCREEN_WIDTH = 2000
        const val SCREEN_HEIGHT = 1000
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val windowManager = context.getSystemService(WindowManager::class.java)

    private lateinit var mainExecutor: TestShellExecutor
    private lateinit var bgExecutor: TestShellExecutor

    private lateinit var expandedViewManager: BubbleExpandedViewManager
    private lateinit var positioner: BubblePositioner
    private lateinit var bubbleTaskView: BubbleTaskView
    private lateinit var bubble: Bubble
    private lateinit var bubbleTaskViewFactory: FakeBubbleTaskViewFactory

    private lateinit var bubbleExpandedView: BubbleBarExpandedView
    private var testableRegionSamplingHelper: TestableRegionSamplingHelper? = null
    private var regionSamplingProvider: TestRegionSamplingProvider? = null

    private val uiEventLoggerFake = UiEventLoggerFake()

    @Before
    fun setUp() {
        ProtoLog.REQUIRE_PROTOLOGTOOL = false
        ProtoLog.init()
        mainExecutor = TestShellExecutor()
        bgExecutor = TestShellExecutor()
        positioner = BubblePositioner(context, windowManager)
        positioner.setShowingInBubbleBar(true)
        val deviceConfig =
            DeviceConfig(
                windowBounds = Rect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT),
                isLargeScreen = true,
                isSmallTablet = false,
                isLandscape = true,
                isRtl = false,
                insets = Insets.of(10, 20, 30, 40)
            )
        positioner.update(deviceConfig)

        expandedViewManager = FakeBubbleExpandedViewManager(bubbleBar = true, expanded = true)
        bubbleTaskViewFactory = FakeBubbleTaskViewFactory(context, mainExecutor)

        bubble = FakeBubbleFactory.createChatBubble(context)
        bubbleTaskView = bubbleTaskViewFactory.create()

        regionSamplingProvider = TestRegionSamplingProvider()

        bubbleExpandedView = LayoutInflater.from(context).inflate(
            R.layout.bubble_bar_expanded_view, null, false /* attachToRoot */
        ) as BubbleBarExpandedView
        bubbleExpandedView.bubbleLogger = BubbleLogger(uiEventLoggerFake)
        bubbleExpandedView.initialize(
            expandedViewManager,
            positioner,
            false /* isOverflow */,
            bubble,
            bubbleTaskView,
            mainExecutor,
            bgExecutor,
            regionSamplingProvider,
        )

        getInstrumentation().runOnMainSync {
            bubbleExpandedView.onAttachedToWindow()
            // Helper should be created once attached to window
            testableRegionSamplingHelper = regionSamplingProvider!!.helper
        }

        bubbleExpandedView.update(bubble)
    }

    @After
    fun tearDown() {
        testableRegionSamplingHelper?.stopAndDestroy()
        getInstrumentation().waitForIdleSync()
    }

    @Test
    fun testCreateSamplingHelper_onAttach() {
        assertThat(testableRegionSamplingHelper).isNotNull()
    }

    @Test
    fun testDestroySamplingHelper_onDetach() {
        bubbleExpandedView.onDetachedFromWindow()
        assertThat(testableRegionSamplingHelper!!.isDestroyed).isTrue()
    }

    @Test
    fun testStopSampling_onDragStart() {
        bubbleExpandedView.setContentVisibility(true)
        assertThat(testableRegionSamplingHelper!!.isStarted).isTrue()

        bubbleExpandedView.setDragging(true)
        assertThat(testableRegionSamplingHelper!!.isStopped).isTrue()
    }

    @Test
    fun testStartSampling_onDragEnd() {
        bubbleExpandedView.setDragging(true)
        bubbleExpandedView.setContentVisibility(true)
        assertThat(testableRegionSamplingHelper!!.isStopped).isTrue()

        bubbleExpandedView.setDragging(false)
        assertThat(testableRegionSamplingHelper!!.isStarted).isTrue()
    }

    @Test
    fun testStartSampling_onContentVisible() {
        bubbleExpandedView.setContentVisibility(true)
        assertThat(testableRegionSamplingHelper!!.setWindowVisible).isTrue()
        assertThat(testableRegionSamplingHelper!!.isStarted).isTrue()
    }

    @Test
    fun testStopSampling_onContentInvisible() {
        bubbleExpandedView.setContentVisibility(false)

        assertThat(testableRegionSamplingHelper!!.setWindowInvisible).isTrue()
        assertThat(testableRegionSamplingHelper!!.isStopped).isTrue()
    }

    @Test
    fun testSampling_startStopAnimating_visible() {
        bubbleExpandedView.isAnimating = true
        bubbleExpandedView.setContentVisibility(true)
        assertThat(testableRegionSamplingHelper!!.isStopped).isTrue()

        bubbleExpandedView.isAnimating = false
        assertThat(testableRegionSamplingHelper!!.isStarted).isTrue()
    }

    @Test
    fun testSampling_startStopAnimating_invisible() {
        bubbleExpandedView.isAnimating = true
        bubbleExpandedView.setContentVisibility(false)
        assertThat(testableRegionSamplingHelper!!.isStopped).isTrue()
        testableRegionSamplingHelper!!.reset()

        bubbleExpandedView.isAnimating = false
        assertThat(testableRegionSamplingHelper!!.isStopped).isTrue()
    }

    @Test
    fun testEventLogging_dismissBubbleViaAppMenu() {
        getInstrumentation().runOnMainSync { bubbleExpandedView.handleView.performClick() }
        val dismissMenuItem = bubbleExpandedView.menuView()
            .actionViewWithText(context.getString(R.string.bubble_dismiss_text))
        assertThat(dismissMenuItem).isNotNull()
        getInstrumentation().runOnMainSync { dismissMenuItem.performClick() }
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        assertThat(uiEventLoggerFake.logs[0].eventId)
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_BUBBLE_DISMISSED_APP_MENU.id)
        assertThat(uiEventLoggerFake.logs[0]).hasBubbleInfo(bubble)
    }

    @Test
    fun testEventLogging_openAppSettings() {
        getInstrumentation().runOnMainSync { bubbleExpandedView.handleView.performClick() }
        val appMenuItem = bubbleExpandedView.menuView()
            .actionViewWithText(context.getString(R.string.bubbles_app_settings, bubble.appName))
        getInstrumentation().runOnMainSync { appMenuItem.performClick() }
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        assertThat(uiEventLoggerFake.logs[0].eventId)
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_APP_MENU_GO_TO_SETTINGS.id)
        assertThat(uiEventLoggerFake.logs[0]).hasBubbleInfo(bubble)
    }

    @Test
    fun testEventLogging_unBubbleConversation() {
        getInstrumentation().runOnMainSync { bubbleExpandedView.handleView.performClick() }
        val menuItem = bubbleExpandedView.menuView()
            .actionViewWithText(context.getString(R.string.bubbles_dont_bubble_conversation))
        getInstrumentation().runOnMainSync { menuItem.performClick() }
        assertThat(uiEventLoggerFake.numLogs()).isEqualTo(1)
        assertThat(uiEventLoggerFake.logs[0].eventId)
            .isEqualTo(BubbleLogger.Event.BUBBLE_BAR_APP_MENU_OPT_OUT.id)
        assertThat(uiEventLoggerFake.logs[0]).hasBubbleInfo(bubble)
    }

    @Test
    fun animateExpansion_waitsUntilTaskCreated() {
        var animated = false
        bubbleExpandedView.animateExpansionWhenTaskViewVisible { animated = true }
        assertThat(animated).isFalse()
        bubbleExpandedView.onTaskCreated()
        assertThat(animated).isTrue()
    }

    @Test
    fun animateExpansion_taskViewAttachedAndVisible() {
        val inflater = LayoutInflater.from(context)
        val expandedView = inflater.inflate(
            R.layout.bubble_bar_expanded_view, null, false /* attachToRoot */
        ) as BubbleBarExpandedView
        val taskView = bubbleTaskViewFactory.create()
        val taskViewParent = FrameLayout(context)
        taskViewParent.addView(taskView.taskView)
        taskView.listener.onTaskCreated(666, ComponentName(context, "BubbleBarExpandedViewTest"))
        assertThat(taskView.isVisible).isTrue()

        expandedView.initialize(
            expandedViewManager,
            positioner,
            false /* isOverflow */,
            bubble,
            taskView,
            mainExecutor,
            bgExecutor,
            regionSamplingProvider,
        )

        // the task view should be added to the expanded view
        assertThat(taskView.taskView.parent).isEqualTo(expandedView)

        var animated = false
        expandedView.animateExpansionWhenTaskViewVisible { animated = true }
        assertThat(animated).isTrue()
    }

    @Test
    fun animateExpansion_taskViewAttachedAndInvisible() {
        val inflater = LayoutInflater.from(context)
        val expandedView = inflater.inflate(
            R.layout.bubble_bar_expanded_view, null, false /* attachToRoot */
        ) as BubbleBarExpandedView
        val taskView = bubbleTaskViewFactory.create()
        val taskViewParent = FrameLayout(context)
        taskViewParent.addView(taskView.taskView)
        taskView.listener.onTaskCreated(666, ComponentName(context, "BubbleBarExpandedViewTest"))
        assertThat(taskView.isVisible).isTrue()
        taskView.listener.onTaskVisibilityChanged(666, false)
        assertThat(taskView.isVisible).isFalse()

        expandedView.initialize(
            expandedViewManager,
            positioner,
            false /* isOverflow */,
            bubble,
            taskView,
            mainExecutor,
            bgExecutor,
            regionSamplingProvider,
        )

        // the task view should be added to the expanded view
        assertThat(taskView.taskView.parent).isEqualTo(expandedView)

        var animated = false
        expandedView.animateExpansionWhenTaskViewVisible { animated = true }
        assertThat(animated).isFalse()

        // send a visible signal to simulate a new surface getting created
        expandedView.onContentVisibilityChanged(true)

        assertThat(animated).isTrue()
    }

    private fun BubbleBarExpandedView.menuView(): BubbleBarMenuView {
        return findViewByPredicate { it is BubbleBarMenuView }
    }

    private fun BubbleBarMenuView.actionViewWithText(text: CharSequence): View {
        val views = ArrayList<View>()
        findViewsWithText(views, text, View.FIND_VIEWS_WITH_TEXT)
        assertWithMessage("Expecting a single action with text '$text'").that(views).hasSize(1)
        // findViewsWithText returns the TextView, but the click listener is on the parent container
        return views.first().parent as View
    }

    private inner class TestRegionSamplingProvider : RegionSamplingProvider {

        lateinit var helper: TestableRegionSamplingHelper

        override fun createHelper(
            sampledView: View?,
            callback: RegionSamplingHelper.SamplingCallback?,
            backgroundExecutor: Executor?,
            mainExecutor: Executor?
        ): RegionSamplingHelper {
            helper = TestableRegionSamplingHelper(sampledView, callback, backgroundExecutor,
                mainExecutor)
            return helper
        }
    }

    private inner class TestableRegionSamplingHelper(
        sampledView: View?,
        samplingCallback: SamplingCallback?,
        backgroundExecutor: Executor?,
        mainExecutor: Executor?
    ) : RegionSamplingHelper(sampledView, samplingCallback, backgroundExecutor, mainExecutor) {

        var isStarted = false
        var isStopped = false
        var isDestroyed = false
        var setWindowVisible = false
        var setWindowInvisible = false

        override fun start(initialSamplingBounds: Rect) {
            super.start(initialSamplingBounds)
            isStarted = true
        }

        override fun stop() {
            super.stop()
            isStopped = true
        }

        override fun stopAndDestroy() {
            super.stopAndDestroy()
            isDestroyed = true
        }

        override fun setWindowVisible(visible: Boolean) {
            super.setWindowVisible(visible)
            if (visible) {
                setWindowVisible = true
            } else {
                setWindowInvisible = true
            }
        }

        fun reset() {
            isStarted = false
            isStopped = false
            isDestroyed = false
            setWindowVisible = false
            setWindowInvisible = false
        }
    }
}

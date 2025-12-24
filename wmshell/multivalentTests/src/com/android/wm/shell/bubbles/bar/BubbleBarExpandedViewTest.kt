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
import com.android.wm.shell.bubbles.UiEventSubject.Companion.assertThat
import com.android.wm.shell.common.TestShellExecutor
import com.android.wm.shell.shared.bubbles.DeviceConfig
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
        )

        bubbleExpandedView.update(bubble)
    }

    @After
    fun tearDown() {
        getInstrumentation().waitForIdleSync()
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
        var endRunnableRun = false
        bubbleExpandedView.animateExpansionWhenTaskViewVisible(
            { animated = true },
            { endRunnableRun = true }
        )
        assertThat(animated).isFalse()
        assertThat(endRunnableRun).isFalse()
        bubbleExpandedView.onTaskCreated()
        assertThat(animated).isTrue()
        // The end runnable should not be run unless the animation is canceled by
        // cancelPendingAnimation.
        assertThat(endRunnableRun).isFalse()
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
        )

        // the task view should be added to the expanded view
        assertThat(taskView.taskView.parent).isEqualTo(expandedView)

        var animated = false
        var endRunnableRun = false
        expandedView.animateExpansionWhenTaskViewVisible(
            { animated = true },
            { endRunnableRun = true }
        )
        assertThat(animated).isTrue()
        // The end runnable should not be run unless the animation is canceled by
        // cancelPendingAnimation.
        assertThat(endRunnableRun).isFalse()
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
        )

        // the task view should be added to the expanded view
        assertThat(taskView.taskView.parent).isEqualTo(expandedView)

        var animated = false
        var endRunnableRun = false
        expandedView.animateExpansionWhenTaskViewVisible(
            { animated = true },
            { endRunnableRun = true }
        )
        assertThat(animated).isFalse()
        assertThat(endRunnableRun).isFalse()

        // send a visible signal to simulate a new surface getting created
        expandedView.onContentVisibilityChanged(true)

        assertThat(animated).isTrue()
        assertThat(endRunnableRun).isFalse()
    }


    @Test
    fun animateExpansion_taskViewAttachedAndInvisibleThenAnimationCanceled() {
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
        )

        // the task view should be added to the expanded view
        assertThat(taskView.taskView.parent).isEqualTo(expandedView)

        var animated = false
        var endRunnableRun = false
        expandedView.animateExpansionWhenTaskViewVisible(
            { animated = true },
            { endRunnableRun = true }
        )
        assertThat(animated).isFalse()
        assertThat(endRunnableRun).isFalse()

        // Cancel the pending animation.
        expandedView.cancelPendingAnimation()

        assertThat(animated).isFalse()
        assertThat(endRunnableRun).isTrue()
    }

    @Test
    fun initialize_forOverflow_hidesCaptionAndHandle() {
        val overflowExpandedView = LayoutInflater.from(context).inflate(
                R.layout.bubble_bar_expanded_view, null, false /* attachToRoot */
        ) as BubbleBarExpandedView
        overflowExpandedView.bubbleLogger = BubbleLogger(uiEventLoggerFake)

        overflowExpandedView.initialize(
                expandedViewManager,
                positioner,
                true /* isOverflow */,
                null, /* bubble */
                null /* bubbleTaskView */
        )

        val captionView = overflowExpandedView.findViewById<View>(R.id.bubble_bar_caption_view)
        assertThat(captionView.visibility).isEqualTo(View.GONE)
        assertThat(overflowExpandedView.handleView.visibility).isEqualTo(View.GONE)
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
}

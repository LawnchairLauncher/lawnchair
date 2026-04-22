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
import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Insets
import android.graphics.Outline
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.bubbles.Bubble
import com.android.wm.shell.bubbles.BubbleExpandedViewManager
import com.android.wm.shell.bubbles.BubbleLogger
import com.android.wm.shell.bubbles.BubbleOverflow
import com.android.wm.shell.bubbles.BubblePositioner
import com.android.wm.shell.bubbles.BubbleTaskView
import com.android.wm.shell.bubbles.FakeBubbleExpandedViewManager
import com.android.wm.shell.bubbles.FakeBubbleFactory
import com.android.wm.shell.common.TestShellExecutor
import com.android.wm.shell.shared.bubbles.DeviceConfig
import com.android.wm.shell.taskview.TaskView
import com.android.wm.shell.taskview.TaskViewController
import com.android.wm.shell.taskview.TaskViewTaskController
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests for [BubbleBarAnimationHelper] */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleBarAnimationHelperTest {

    @get:Rule val animatorTestRule: AnimatorTestRule = AnimatorTestRule(this)
    private lateinit var activityScenario: ActivityScenario<TestActivity>

    companion object {
        const val SCREEN_WIDTH = 2000
        const val SCREEN_HEIGHT = 1000
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var animationHelper: BubbleBarAnimationHelper
    private lateinit var bubblePositioner: BubblePositioner
    private lateinit var expandedViewManager: BubbleExpandedViewManager
    private lateinit var bubbleLogger: BubbleLogger
    private lateinit var mainExecutor: TestShellExecutor
    private lateinit var bgExecutor: TestShellExecutor
    private lateinit var container: FrameLayout

    @Before
    fun setUp() {
        ProtoLog.REQUIRE_PROTOLOGTOOL = false
        ProtoLog.init()
        activityScenario = ActivityScenario.launch(TestActivity::class.java)
        activityScenario.onActivity { activity -> container = activity.container }
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
                insets = Insets.of(10, 20, 30, 40),
            )
        bubblePositioner.update(deviceConfig)
        expandedViewManager = FakeBubbleExpandedViewManager()
        bubbleLogger = BubbleLogger(UiEventLoggerFake())

        mainExecutor = TestShellExecutor()
        bgExecutor = TestShellExecutor()

        animationHelper = BubbleBarAnimationHelper(context, bubblePositioner)
    }

    @After
    fun tearDown() {
        bgExecutor.flushAll()
        mainExecutor.flushAll()
    }

    @Test
    fun animateSwitch_bubbleToBubble_oldHiddenNewShown() {
        val fromBubble = createBubble(key = "from").initialize(container)
        val toBubble = createBubble(key = "to").initialize(container)

        val semaphore = Semaphore(0)
        val after = Runnable { semaphore.release() }

        activityScenario.onActivity {
            animationHelper.animateSwitch(fromBubble, toBubble, after)
            animatorTestRule.advanceTimeBy(1000)
        }
        getInstrumentation().waitForIdleSync()

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(fromBubble.bubbleBarExpandedView?.visibility).isEqualTo(View.INVISIBLE)
        assertThat(fromBubble.bubbleBarExpandedView?.alpha).isEqualTo(0f)
        assertThat(fromBubble.bubbleBarExpandedView?.isSurfaceZOrderedOnTop).isFalse()

        assertThat(toBubble.bubbleBarExpandedView?.visibility).isEqualTo(View.VISIBLE)
        assertThat(toBubble.bubbleBarExpandedView?.alpha).isEqualTo(1f)
        assertThat(toBubble.bubbleBarExpandedView?.isSurfaceZOrderedOnTop).isFalse()
    }

    @Test
    fun animateSwitch_bubbleToBubble_handleColorTransferred() {
        val fromBubble = createBubble(key = "from").initialize(container)
        fromBubble.bubbleBarExpandedView!!
            .handleView
            .updateHandleColor(/* isRegionDark= */ true, /* animated= */ false)
        val toBubble = createBubble(key = "to").initialize(container)

        activityScenario.onActivity {
            animationHelper.animateSwitch(fromBubble, toBubble, /* afterAnimation= */ null)
            animatorTestRule.advanceTimeBy(1000)
        }
        getInstrumentation().waitForIdleSync()

        assertThat(toBubble.bubbleBarExpandedView!!.handleView.handleColor)
            .isEqualTo(fromBubble.bubbleBarExpandedView!!.handleView.handleColor)
    }

    @Test
    fun animateSwitch_bubbleToBubble_updateTaskBounds() {
        val fromBubble = createBubble("from").initialize(container)
        val toBubbleTaskController = mock<TaskViewTaskController>()
        val taskController = mock<TaskViewController>()
        val toBubble = createBubble("to", taskController, toBubbleTaskController).initialize(
            container)

        activityScenario.onActivity {
            animationHelper.animateSwitch(fromBubble, toBubble) {}
            // Start the animation, but don't finish
            animatorTestRule.advanceTimeBy(100)
        }
        getInstrumentation().waitForIdleSync()
        // Clear invocations to ensure that bounds update happens after animation ends
        clearInvocations(taskController)
        getInstrumentation().runOnMainSync { animatorTestRule.advanceTimeBy(900) }
        getInstrumentation().waitForIdleSync()

        verify(taskController).setTaskBounds(eq(toBubbleTaskController), any())
    }

    @Test
    fun animateSwitch_bubbleToOverflow_oldHiddenNewShown() {
        val fromBubble = createBubble(key = "from").initialize(container)
        val overflow = createOverflow().initialize(container)

        val semaphore = Semaphore(0)
        val after = Runnable { semaphore.release() }

        activityScenario.onActivity {
            animationHelper.animateSwitch(fromBubble, overflow, after)
            animatorTestRule.advanceTimeBy(1000)
        }
        getInstrumentation().waitForIdleSync()

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(fromBubble.bubbleBarExpandedView?.visibility).isEqualTo(View.INVISIBLE)
        assertThat(fromBubble.bubbleBarExpandedView?.alpha).isEqualTo(0f)
        assertThat(fromBubble.bubbleBarExpandedView?.isSurfaceZOrderedOnTop).isFalse()

        assertThat(overflow.bubbleBarExpandedView?.visibility).isEqualTo(View.VISIBLE)
        assertThat(overflow.bubbleBarExpandedView?.alpha).isEqualTo(1f)
    }

    @Test
    fun animateSwitch_overflowToBubble_oldHiddenNewShown() {
        val overflow = createOverflow().initialize(container)
        val toBubble = createBubble(key = "to").initialize(container)

        val semaphore = Semaphore(0)
        val after = Runnable { semaphore.release() }

        activityScenario.onActivity {
            animationHelper.animateSwitch(overflow, toBubble, after)
            animatorTestRule.advanceTimeBy(1000)
        }
        getInstrumentation().waitForIdleSync()

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(overflow.bubbleBarExpandedView?.visibility).isEqualTo(View.INVISIBLE)
        assertThat(overflow.bubbleBarExpandedView?.alpha).isEqualTo(0f)

        assertThat(toBubble.bubbleBarExpandedView?.visibility).isEqualTo(View.VISIBLE)
        assertThat(toBubble.bubbleBarExpandedView?.alpha).isEqualTo(1f)
        assertThat(toBubble.bubbleBarExpandedView?.isSurfaceZOrderedOnTop).isFalse()
    }

    @Test
    fun animateToRestPosition_updateTaskBounds() {
        val taskView = mock<TaskViewTaskController>()
        val controller = mock<TaskViewController>()
        val bubble = createBubble("key", controller, taskView).initialize(container)

        val semaphore = Semaphore(0)
        val after = Runnable { semaphore.release() }

        activityScenario.onActivity {
            animationHelper.animateExpansion(bubble, after)
            animatorTestRule.advanceTimeBy(1000)
        }
        getInstrumentation().waitForIdleSync()
        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()

        getInstrumentation().runOnMainSync {
            animationHelper.animateToRestPosition()
            animatorTestRule.advanceTimeBy(100)
        }
        // Clear invocations to ensure that bounds update happens after animation ends
        clearInvocations(controller)
        getInstrumentation().runOnMainSync { animatorTestRule.advanceTimeBy(900) }
        getInstrumentation().waitForIdleSync()

        verify(controller).setTaskBounds(eq(taskView), any())
    }

    @Test
    fun animateExpansion() {
        val bubble = createBubble(key = "b1").initialize(container)
        val bbev = bubble.bubbleBarExpandedView!!

        val semaphore = Semaphore(0)
        val after = Runnable { semaphore.release() }

        activityScenario.onActivity {
            bbev.onTaskCreated()
            animationHelper.animateExpansion(bubble, after)
            animatorTestRule.advanceTimeBy(1000)
        }
        getInstrumentation().waitForIdleSync()

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(bbev.alpha).isEqualTo(1)
    }

    @Test
    fun animateExpansion_cancelCallsEndCallback() {
        val bubble = createBubble(key = "b1").initialize(container)
        val bbev = bubble.bubbleBarExpandedView!!

        val semaphore = Semaphore(0)
        var afterCalled = false
        val after = Runnable {
            afterCalled = true
            semaphore.release()
        }

        activityScenario.onActivity {
            bbev.onTaskCreated()
            animationHelper.animateExpansion(bubble, after)
            animationHelper.cancelAnimations()
        }
        getInstrumentation().waitForIdleSync()

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(afterCalled).isTrue()
    }

    @Test
    fun onImeTopChanged_noOverlap() {
        val bubble = createBubble(key = "b1").initialize(container)
        val bbev = bubble.bubbleBarExpandedView!!

        val semaphore = Semaphore(0)
        val after = Runnable { semaphore.release() }

        activityScenario.onActivity {
            bbev.onTaskCreated()
            animationHelper.animateExpansion(bubble, after)
            animatorTestRule.advanceTimeBy(1000)
        }
        getInstrumentation().waitForIdleSync()

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()

        activityScenario.onActivity {
            // notify that the IME top coordinate is greater than the bottom of the expanded view.
            // there's no overlap so it should not be clipped.
            animationHelper.onImeTopChanged(bbev.contentBottomOnScreen * 2)
        }
        val outline = Outline()
        bbev.outlineProvider.getOutline(bbev, outline)
        assertThat(outline.mRect.bottom).isEqualTo(bbev.height)
    }

    @Test
    fun onImeTopChanged_overlapsWithExpandedView() {
        val bubble = createBubble(key = "b1").initialize(container)
        val bbev = bubble.bubbleBarExpandedView!!

        val semaphore = Semaphore(0)
        val after = Runnable { semaphore.release() }

        activityScenario.onActivity {
            bbev.onTaskCreated()
            animationHelper.animateExpansion(bubble, after)
            animatorTestRule.advanceTimeBy(1000)
        }
        getInstrumentation().waitForIdleSync()

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()

        activityScenario.onActivity {
            // notify that the IME top coordinate is less than the bottom of the expanded view,
            // meaning it overlaps with it so we should be clipping the expanded view.
            animationHelper.onImeTopChanged(bbev.contentBottomOnScreen - 10)
        }
        val outline = Outline()
        bbev.outlineProvider.getOutline(bbev, outline)
        assertThat(outline.mRect.bottom).isEqualTo(bbev.height - 10)
    }

    private fun createBubble(
        key: String,
        taskViewController: TaskViewController = mock<TaskViewController>(),
        taskViewTaskController: TaskViewTaskController = mock<TaskViewTaskController>(),
    ): Bubble {
        val taskView = TaskView(context, taskViewController, taskViewTaskController)
        val taskInfo = mock<ActivityManager.RunningTaskInfo>()
        whenever(taskViewTaskController.taskInfo).thenReturn(taskInfo)
        val bubble = FakeBubbleFactory.createChatBubble(context, key)
        val bubbleTaskView = BubbleTaskView(taskView, mainExecutor)
        bubbleTaskView.listener.onTaskCreated(/* taskId= */ 1, ComponentName("package", "class"))

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
        return bubble
    }

    private fun createOverflow(): BubbleOverflow {
        val overflow = BubbleOverflow(context, bubblePositioner)
        overflow.initializeForBubbleBar(expandedViewManager, bubblePositioner)
        return overflow
    }

    private fun Bubble.initialize(container: ViewGroup): Bubble {
        activityScenario.onActivity { container.addView(bubbleBarExpandedView) }
        // Mark taskView's visible
        bubbleBarExpandedView!!.onContentVisibilityChanged(true)
        return this
    }

    private fun BubbleOverflow.initialize(container: ViewGroup): BubbleOverflow {
        activityScenario.onActivity { container.addView(bubbleBarExpandedView) }
        return this
    }

    class TestActivity : Activity() {
        lateinit var container: FrameLayout
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            container = FrameLayout(applicationContext)
            container.layoutParams = LayoutParams(50, 50)
            setContentView(container)
        }
    }
}

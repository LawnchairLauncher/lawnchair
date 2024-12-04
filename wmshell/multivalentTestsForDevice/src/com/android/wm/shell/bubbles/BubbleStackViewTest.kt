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
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.UserHandle
import android.platform.test.flag.junit.SetFlagsRule
import android.view.IWindowManager
import android.view.WindowManager
import android.view.WindowManagerGlobal
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.internal.protolog.common.ProtoLog
import com.android.launcher3.icons.BubbleIconFactory
import com.android.wm.shell.Flags
import com.android.wm.shell.R
import com.android.wm.shell.bubbles.Bubbles.SysuiProxy
import com.android.wm.shell.bubbles.animation.AnimatableScaleMatrix
import com.android.wm.shell.common.FloatingContentCoordinator
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.shared.animation.PhysicsAnimatorTestUtils
import com.android.wm.shell.taskview.TaskView
import com.android.wm.shell.taskview.TaskViewTaskController
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

/** Unit tests for [BubbleStackView]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleStackViewTest {

    @get:Rule val setFlagsRule = SetFlagsRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var positioner: BubblePositioner
    private lateinit var iconFactory: BubbleIconFactory
    private lateinit var expandedViewManager: FakeBubbleExpandedViewManager
    private lateinit var bubbleStackView: BubbleStackView
    private lateinit var shellExecutor: ShellExecutor
    private lateinit var windowManager: IWindowManager
    private lateinit var bubbleTaskViewFactory: BubbleTaskViewFactory
    private lateinit var bubbleData: BubbleData
    private lateinit var bubbleStackViewManager: FakeBubbleStackViewManager
    private var sysuiProxy = mock<SysuiProxy>()

    @Before
    fun setUp() {
        PhysicsAnimatorTestUtils.prepareForTest()
        // Disable protolog tool when running the tests from studio
        ProtoLog.REQUIRE_PROTOLOGTOOL = false
        windowManager = WindowManagerGlobal.getWindowManagerService()!!
        shellExecutor = TestShellExecutor()
        val windowManager = context.getSystemService(WindowManager::class.java)
        iconFactory =
            BubbleIconFactory(
                context,
                context.resources.getDimensionPixelSize(R.dimen.bubble_size),
                context.resources.getDimensionPixelSize(R.dimen.bubble_badge_size),
                Color.BLACK,
                context.resources.getDimensionPixelSize(
                    com.android.internal.R.dimen.importance_ring_stroke_width
                )
            )
        positioner = BubblePositioner(context, windowManager)
        bubbleData =
            BubbleData(
                context,
                BubbleLogger(UiEventLoggerFake()),
                positioner,
                BubbleEducationController(context),
                shellExecutor
            )
        bubbleStackViewManager = FakeBubbleStackViewManager()
        expandedViewManager = FakeBubbleExpandedViewManager()
        bubbleTaskViewFactory = FakeBubbleTaskViewFactory()
        bubbleStackView =
            BubbleStackView(
                context,
                bubbleStackViewManager,
                positioner,
                bubbleData,
                null,
                FloatingContentCoordinator(),
                { sysuiProxy },
                shellExecutor
            )

        context
            .getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(StackEducationView.PREF_STACK_EDUCATION, true)
            .apply()
    }

    @After
    fun tearDown() {
        PhysicsAnimatorTestUtils.tearDown()
    }

    @Test
    fun addBubble() {
        val bubble = createAndInflateBubble()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)
    }

    @Test
    fun tapBubbleToExpand() {
        val bubble = createAndInflateBubble()

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble)
        }

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(bubbleStackView.bubbleCount).isEqualTo(1)
        var lastUpdate: BubbleData.Update? = null
        val semaphore = Semaphore(0)
        val listener =
            BubbleData.Listener { update ->
                lastUpdate = update
                semaphore.release()
            }
        bubbleData.setListener(listener)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubble.iconView!!.performClick()
            // we're checking the expanded state in BubbleData because that's the source of truth.
            // This will eventually propagate an update back to the stack view, but setting the
            // entire pipeline is outside the scope of a unit test.
            assertThat(bubbleData.isExpanded).isTrue()
        }

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(lastUpdate).isNotNull()
        assertThat(lastUpdate!!.expandedChanged).isTrue()
        assertThat(lastUpdate!!.expanded).isTrue()
    }

    @Test
    fun tapDifferentBubble_shouldReorder() {
        val bubble1 = createAndInflateChatBubble(key = "bubble1")
        val bubble2 = createAndInflateChatBubble(key = "bubble2")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.addBubble(bubble1)
            bubbleStackView.addBubble(bubble2)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertThat(bubbleStackView.bubbleCount).isEqualTo(2)
        assertThat(bubbleData.bubbles).hasSize(2)
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble2)
        assertThat(bubble2.iconView).isNotNull()

        var lastUpdate: BubbleData.Update? = null
        val semaphore = Semaphore(0)
        val listener =
            BubbleData.Listener { update ->
                lastUpdate = update
                semaphore.release()
            }
        bubbleData.setListener(listener)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubble2.iconView!!.performClick()
            assertThat(bubbleData.isExpanded).isTrue()

            bubbleStackView.setSelectedBubble(bubble2)
            bubbleStackView.isExpanded = true
        }

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(lastUpdate!!.expanded).isTrue()
        assertThat(lastUpdate!!.bubbles.map { it.key })
            .containsExactly("bubble2", "bubble1")
            .inOrder()

        // wait for idle to allow the animation to start
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // wait for the expansion animation to complete before interacting with the bubbles
        PhysicsAnimatorTestUtils.blockUntilAnimationsEnd(
                AnimatableScaleMatrix.SCALE_X, AnimatableScaleMatrix.SCALE_Y)

        // tap on bubble1 to select it
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubble1.iconView!!.performClick()
        }
        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble1)

        // tap on bubble1 again to collapse the stack
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            // we have to set the selected bubble in the stack view manually because we don't have a
            // listener wired up.
            bubbleStackView.setSelectedBubble(bubble1)
            bubble1.iconView!!.performClick()
        }

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(bubbleData.selectedBubble).isEqualTo(bubble1)
        assertThat(bubbleData.isExpanded).isFalse()
        assertThat(lastUpdate!!.orderChanged).isTrue()
        assertThat(lastUpdate!!.bubbles.map { it.key })
            .containsExactly("bubble1", "bubble2")
            .inOrder()
    }

    @EnableFlags(Flags.FLAG_ENABLE_OPTIONAL_BUBBLE_OVERFLOW)
    @Test
    fun testCreateStackView_noOverflowContents_noOverflow() {
        bubbleStackView =
                BubbleStackView(
                        context,
                        bubbleStackViewManager,
                        positioner,
                        bubbleData,
                        null,
                        FloatingContentCoordinator(),
                        { sysuiProxy },
                        shellExecutor
                )

        assertThat(bubbleData.overflowBubbles).isEmpty()
        val bubbleOverflow = bubbleData.overflow
        // Overflow shouldn't be attached
        assertThat(bubbleStackView.getBubbleIndex(bubbleOverflow)).isEqualTo(-1)
    }

    @EnableFlags(Flags.FLAG_ENABLE_OPTIONAL_BUBBLE_OVERFLOW)
    @Test
    fun testCreateStackView_hasOverflowContents_hasOverflow() {
        // Add a bubble to the overflow
        val bubble1 = createAndInflateChatBubble(key = "bubble1")
        bubbleData.notificationEntryUpdated(bubble1, false, false)
        bubbleData.dismissBubbleWithKey(bubble1.key, Bubbles.DISMISS_USER_GESTURE)
        assertThat(bubbleData.overflowBubbles).isNotEmpty()

        bubbleStackView =
                BubbleStackView(
                        context,
                        bubbleStackViewManager,
                        positioner,
                        bubbleData,
                        null,
                        FloatingContentCoordinator(),
                        { sysuiProxy },
                        shellExecutor
                )
        val bubbleOverflow = bubbleData.overflow
        assertThat(bubbleStackView.getBubbleIndex(bubbleOverflow)).isGreaterThan(-1)
    }

    @DisableFlags(Flags.FLAG_ENABLE_OPTIONAL_BUBBLE_OVERFLOW)
    @Test
    fun testCreateStackView_noOverflowContents_hasOverflow() {
        bubbleStackView =
                BubbleStackView(
                        context,
                        bubbleStackViewManager,
                        positioner,
                        bubbleData,
                        null,
                        FloatingContentCoordinator(),
                        { sysuiProxy },
                        shellExecutor
                )

        assertThat(bubbleData.overflowBubbles).isEmpty()
        val bubbleOverflow = bubbleData.overflow
        assertThat(bubbleStackView.getBubbleIndex(bubbleOverflow)).isGreaterThan(-1)
    }

    @EnableFlags(Flags.FLAG_ENABLE_OPTIONAL_BUBBLE_OVERFLOW)
    @Test
    fun showOverflow_true() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.showOverflow(true)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        val bubbleOverflow = bubbleData.overflow
        assertThat(bubbleStackView.getBubbleIndex(bubbleOverflow)).isGreaterThan(-1)
    }

    @EnableFlags(Flags.FLAG_ENABLE_OPTIONAL_BUBBLE_OVERFLOW)
    @Test
    fun showOverflow_false() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.showOverflow(true)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val bubbleOverflow = bubbleData.overflow
        assertThat(bubbleStackView.getBubbleIndex(bubbleOverflow)).isGreaterThan(-1)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.showOverflow(false)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // The overflow should've been removed
        assertThat(bubbleStackView.getBubbleIndex(bubbleOverflow)).isEqualTo(-1)
    }

    @DisableFlags(Flags.FLAG_ENABLE_OPTIONAL_BUBBLE_OVERFLOW)
    @Test
    fun showOverflow_ignored() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            bubbleStackView.showOverflow(false)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // showOverflow should've been ignored, so the overflow would be attached
        val bubbleOverflow = bubbleData.overflow
        assertThat(bubbleStackView.getBubbleIndex(bubbleOverflow)).isGreaterThan(-1)
    }

    private fun createAndInflateChatBubble(key: String): Bubble {
        val icon = Icon.createWithResource(context.resources, R.drawable.bubble_ic_overflow_button)
        val shortcutInfo = ShortcutInfo.Builder(context, "fakeId").setIcon(icon).build()
        val bubble =
            Bubble(
                key,
                shortcutInfo,
                /* desiredHeight= */ 6,
                Resources.ID_NULL,
                "title",
                /* taskId= */ 0,
                "locus",
                /* isDismissable= */ true,
                directExecutor()
            ) {}
        inflateBubble(bubble)
        return bubble
    }

    private fun createAndInflateBubble(): Bubble {
        val intent = Intent(Intent.ACTION_VIEW).setPackage(context.packageName)
        val icon = Icon.createWithResource(context.resources, R.drawable.bubble_ic_overflow_button)
        val bubble = Bubble.createAppBubble(intent, UserHandle(1), icon, directExecutor())
        inflateBubble(bubble)
        return bubble
    }

    private fun inflateBubble(bubble: Bubble) {
        bubble.setInflateSynchronously(true)
        bubbleData.notificationEntryUpdated(bubble, true, false)

        val semaphore = Semaphore(0)
        val callback: BubbleViewInfoTask.Callback =
            BubbleViewInfoTask.Callback { semaphore.release() }
        bubble.inflate(
            callback,
            context,
            expandedViewManager,
            bubbleTaskViewFactory,
            positioner,
            bubbleStackView,
            null,
            iconFactory,
            false
        )

        assertThat(semaphore.tryAcquire(5, TimeUnit.SECONDS)).isTrue()
        assertThat(bubble.isInflated).isTrue()
    }

    private class FakeBubbleStackViewManager : BubbleStackViewManager {

        override fun onAllBubblesAnimatedOut() {}

        override fun updateWindowFlagsForBackpress(interceptBack: Boolean) {}

        override fun checkNotificationPanelExpandedState(callback: Consumer<Boolean>) {}

        override fun hideCurrentInputMethod() {}
    }

    private class TestShellExecutor : ShellExecutor {

        override fun execute(runnable: Runnable) {
            runnable.run()
        }

        override fun executeDelayed(r: Runnable, delayMillis: Long) {
            r.run()
        }

        override fun removeCallbacks(r: Runnable?) {}

        override fun hasCallback(r: Runnable): Boolean = false
    }

    private inner class FakeBubbleTaskViewFactory : BubbleTaskViewFactory {
        override fun create(): BubbleTaskView {
            val taskViewTaskController = mock<TaskViewTaskController>()
            val taskView = TaskView(context, taskViewTaskController)
            return BubbleTaskView(taskView, shellExecutor)
        }
    }

    private inner class FakeBubbleExpandedViewManager : BubbleExpandedViewManager {

        override val overflowBubbles: List<Bubble>
            get() = emptyList()

        override fun setOverflowListener(listener: BubbleData.Listener) {}

        override fun collapseStack() {}

        override fun updateWindowFlagsForBackpress(intercept: Boolean) {}

        override fun promoteBubbleFromOverflow(bubble: Bubble) {}

        override fun removeBubble(key: String, reason: Int) {}

        override fun dismissBubble(bubble: Bubble, reason: Int) {}

        override fun setAppBubbleTaskId(key: String, taskId: Int) {}

        override fun isStackExpanded(): Boolean = false

        override fun isShowingAsBubbleBar(): Boolean = false

        override fun hideCurrentInputMethod() {}
    }
}

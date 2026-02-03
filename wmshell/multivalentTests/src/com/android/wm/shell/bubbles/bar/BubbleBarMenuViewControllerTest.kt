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

package com.android.wm.shell.bubbles.bar

import android.animation.AnimatorTestRule
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.wm.shell.bubbles.Bubble
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [BubbleBarMenuViewController]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleBarMenuViewControllerTest {

    @get:Rule
    val animatorTestRule: AnimatorTestRule = AnimatorTestRule(this)
    private lateinit var activityScenario: ActivityScenario<TestActivity>
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var menuViewController: BubbleBarMenuViewController
    private val listener = TestListener()
    private lateinit var container: FrameLayout

    @Before
    fun setUp() {
        activityScenario = ActivityScenario.launch(TestActivity::class.java)
        activityScenario.onActivity { activity -> container = activity.container }
        val handleView = BubbleBarHandleView(context)
        menuViewController = BubbleBarMenuViewController(context, handleView, container)
        menuViewController.setListener(listener)
    }

    @Test
    fun showMenu_immediatelyUpdatesVisibility() {
        activityScenario.onActivity {
            menuViewController.showMenu(/* animated= */ true)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(listener.visible).isTrue()

        // advance the animator timer since the actual visibility of the menu is updated in the
        // middle of the animation
        activityScenario.onActivity {
            animatorTestRule.advanceTimeBy(600)
        }
        assertThat(menuViewController.isMenuVisible).isTrue()
    }

    @Test
    fun hideMenu_updatesVisibilityAfterAnimationEnds() {
        activityScenario.onActivity {
            menuViewController.showMenu(/* animated= */ true)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertThat(listener.visible).isTrue()

        activityScenario.onActivity {
            animatorTestRule.advanceTimeBy(600)
        }
        assertThat(menuViewController.isMenuVisible).isTrue()

        activityScenario.onActivity {
            menuViewController.hideMenu(/* animated= */ true)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        // check that the listener hasn't been notified yet
        assertThat(listener.visible).isTrue()

        activityScenario.onActivity {
            animatorTestRule.advanceTimeBy(600)
        }
        assertThat(listener.visible).isFalse()
        assertThat(menuViewController.isMenuVisible).isFalse()
    }

    private class TestListener : BubbleBarMenuViewController.Listener {

        var visible = false

        override fun onMenuVisibilityChanged(visible: Boolean) {
            this.visible = visible
        }

        override fun onUnBubbleConversation(bubble: Bubble?) {}

        override fun onOpenAppSettings(bubble: Bubble?) {}

        override fun onDismissBubble(bubble: Bubble?) {}

        override fun onMoveToFullscreen(bubble: Bubble?) {}
    }

    class TestActivity : Activity() {
        lateinit var container: FrameLayout
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            container = FrameLayout(applicationContext)
            setContentView(container)
        }
    }
}

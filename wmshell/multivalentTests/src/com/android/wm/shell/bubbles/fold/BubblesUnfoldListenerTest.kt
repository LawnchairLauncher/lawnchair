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

package com.android.wm.shell.bubbles.fold

import android.content.Context
import android.graphics.Insets
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.wm.shell.bubbles.BubbleData
import com.android.wm.shell.bubbles.BubbleEducationController
import com.android.wm.shell.bubbles.BubbleLogger
import com.android.wm.shell.bubbles.BubblePositioner
import com.android.wm.shell.bubbles.FakeBubbleFactory
import com.android.wm.shell.common.TestShellExecutor
import com.android.wm.shell.shared.bubbles.DeviceConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Unit tests for [BubblesUnfoldListener]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubblesUnfoldListenerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var bubblePositioner: BubblePositioner
    private lateinit var bubbleLogger: BubbleLogger
    private lateinit var bubbleData: BubbleData
    private lateinit var foldLockSettingsObserver: BubblesFoldLockSettingsObserver
    private lateinit var unfoldListener: BubblesUnfoldListener

    private var isStayAwakeOnFold = false
    private var barToFloatingTransitionStarted = false
    private var barToFullscreenTransitionStarted = false

    @Before
    fun setUp() {
        val deviceConfig =
            DeviceConfig(
                windowBounds = Rect(0, 0, 700, 1000),
                isLargeScreen = false,
                isSmallTablet = false,
                isLandscape = false,
                isRtl = false,
                insets = Insets.of(10, 20, 30, 40),
            )
        bubblePositioner = BubblePositioner(context, deviceConfig)
        bubbleLogger = BubbleLogger(UiEventLoggerFake())
        val mainExecutor = TestShellExecutor()
        val backgroundExecutor = TestShellExecutor()
        bubbleData =
            BubbleData(
                context,
                bubbleLogger,
                bubblePositioner,
                BubbleEducationController(context),
                mainExecutor,
                backgroundExecutor
            )
        foldLockSettingsObserver = BubblesFoldLockSettingsObserver { isStayAwakeOnFold }
        unfoldListener = BubblesUnfoldListener(
            bubbleData, foldLockSettingsObserver) { bubble, moveToFullscreen ->
            if (moveToFullscreen) {
                barToFullscreenTransitionStarted = true
            } else {
                barToFloatingTransitionStarted = true
            }
        }
    }

    @Test
    fun fold_noBubbles_shouldNotStartTransition() {
        unfoldListener.onFoldStateChanged(isFolded = true)
        assertThat(barToFloatingTransitionStarted).isFalse()
    }

    @Test
    fun fold_collapsed_shouldNotStartTransition() {
        val bubble = FakeBubbleFactory.createChatBubble(context)
        bubbleData.notificationEntryUpdated(bubble, true, false)
        assertThat(bubbleData.hasBubbles()).isTrue()
        bubbleData.selectedBubble = bubble

        unfoldListener.onFoldStateChanged(isFolded = true)
        assertThat(barToFloatingTransitionStarted).isFalse()
    }

    @Test
    fun fold_expandedBubble_doesNotStayAwakeOnFold_shouldNotStartTransition() {
        val bubble = FakeBubbleFactory.createChatBubble(context)
        bubbleData.notificationEntryUpdated(bubble, true, false)
        assertThat(bubbleData.hasBubbles()).isTrue()
        bubbleData.setSelectedBubbleAndExpandStack(bubble)
        assertThat(bubbleData.isExpanded).isTrue()

        unfoldListener.onFoldStateChanged(isFolded = true)
        assertThat(barToFloatingTransitionStarted).isFalse()
    }

    @Test
    fun fold_expandedBubble_staysAwakeOnFold_shouldStartTransition() {
        isStayAwakeOnFold = true
        val bubble = FakeBubbleFactory.createChatBubble(context)
        bubbleData.notificationEntryUpdated(bubble, true, false)
        assertThat(bubbleData.hasBubbles()).isTrue()
        bubbleData.setSelectedBubbleAndExpandStack(bubble)
        assertThat(bubbleData.isExpanded).isTrue()

        unfoldListener.onFoldStateChanged(isFolded = true)
        assertThat(barToFloatingTransitionStarted).isTrue()
    }

    @Test
    fun fold_overflowExpanded_staysAwakeOnFold_shouldNotStartTransition() {
        isStayAwakeOnFold = true
        val bubble = FakeBubbleFactory.createChatBubble(context)
        bubbleData.notificationEntryUpdated(bubble, true, false)
        assertThat(bubbleData.hasBubbles()).isTrue()
        bubbleData.setSelectedBubbleAndExpandStack(bubble)
        assertThat(bubbleData.isExpanded).isTrue()
        bubbleData.selectedBubble = bubbleData.overflow

        unfoldListener.onFoldStateChanged(isFolded = true)
        assertThat(barToFloatingTransitionStarted).isFalse()
    }

    @Test
    fun fold_expandedBubble_staysAwakeOnFold_unfolding_shouldNotStartTransition() {
        isStayAwakeOnFold = true
        val bubble = FakeBubbleFactory.createChatBubble(context)
        bubbleData.notificationEntryUpdated(bubble, true, false)
        assertThat(bubbleData.hasBubbles()).isTrue()
        bubbleData.setSelectedBubbleAndExpandStack(bubble)
        assertThat(bubbleData.isExpanded).isTrue()

        unfoldListener.onFoldStateChanged(isFolded = false)
        assertThat(barToFloatingTransitionStarted).isFalse()
    }

    @Test
    fun fold_expandedFixedLandscapeBubble_staysAwakeOnFold_shouldStartFullscreenTransition() {
        isStayAwakeOnFold = true
        val bubble = FakeBubbleFactory.createChatBubble(context)
        bubble.setIsTopActivityFixedOrientationLandscape(true)
        bubbleData.notificationEntryUpdated(bubble, true, false)
        assertThat(bubbleData.hasBubbles()).isTrue()
        bubbleData.setSelectedBubbleAndExpandStack(bubble)
        assertThat(bubbleData.isExpanded).isTrue()

        unfoldListener.onFoldStateChanged(isFolded = true)
        assertThat(barToFloatingTransitionStarted).isFalse()
        assertThat(barToFullscreenTransitionStarted).isTrue()
    }
}

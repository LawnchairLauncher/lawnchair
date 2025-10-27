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

package com.android.launcher3.taskbar.bubbles

import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController
import com.android.quickstep.inputconsumers.BubbleBarInputConsumer
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/**
 * Tests for bubble bar input consumer, namely the static method that indicates whether the input
 * consumer should handle the event.
 */
@RunWith(AndroidJUnit4::class)
class BubbleBarInputConsumerTest {

    private lateinit var bubbleControllers: BubbleControllers

    @Mock private lateinit var taskbarActivityContext: TaskbarActivityContext
    @Mock private lateinit var bubbleBarController: BubbleBarController
    @Mock private lateinit var bubbleBarViewController: BubbleBarViewController
    @Mock private lateinit var bubbleStashController: BubbleStashController
    @Mock private lateinit var bubbleStashedHandleViewController: BubbleStashedHandleViewController
    @Mock private lateinit var bubbleDragController: BubbleDragController
    @Mock private lateinit var bubbleDismissController: BubbleDismissController
    @Mock private lateinit var bubbleBarPinController: BubbleBarPinController
    @Mock private lateinit var bubblePinController: BubblePinController
    @Mock private lateinit var bubbleBarSwipeController: BubbleBarSwipeController
    @Mock private lateinit var bubbleCreator: BubbleCreator

    @Mock private lateinit var motionEvent: MotionEvent

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        bubbleControllers =
            BubbleControllers(
                bubbleBarController,
                bubbleBarViewController,
                bubbleStashController,
                Optional.of(bubbleStashedHandleViewController),
                bubbleDragController,
                bubbleDismissController,
                bubbleBarPinController,
                bubblePinController,
                Optional.of(bubbleBarSwipeController),
                bubbleCreator,
            )
    }

    @Test
    fun testIsEventOnBubbles_noTaskbarActivityContext() {
        assertThat(BubbleBarInputConsumer.isEventOnBubbles(null, motionEvent)).isFalse()
    }

    @Test
    fun testIsEventOnBubbles_bubblesNotEnabled() {
        whenever(taskbarActivityContext.isBubbleBarEnabled).thenReturn(false)
        assertThat(BubbleBarInputConsumer.isEventOnBubbles(taskbarActivityContext, motionEvent))
            .isFalse()
    }

    @Test
    fun testIsEventOnBubbles_noBubbleControllers() {
        whenever(taskbarActivityContext.isBubbleBarEnabled).thenReturn(true)
        whenever(taskbarActivityContext.bubbleControllers).thenReturn(null)
        assertThat(BubbleBarInputConsumer.isEventOnBubbles(taskbarActivityContext, motionEvent))
            .isFalse()
    }

    @Test
    fun testIsEventOnBubbles_noBubbles() {
        whenever(taskbarActivityContext.isBubbleBarEnabled).thenReturn(true)
        whenever(taskbarActivityContext.bubbleControllers).thenReturn(bubbleControllers)
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(false)
        assertThat(BubbleBarInputConsumer.isEventOnBubbles(taskbarActivityContext, motionEvent))
            .isFalse()
    }

    @Test
    fun testIsEventOnBubbles_eventOnStashedHandle() {
        whenever(taskbarActivityContext.isBubbleBarEnabled).thenReturn(true)
        whenever(taskbarActivityContext.bubbleControllers).thenReturn(bubbleControllers)
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(true)

        whenever(bubbleStashController.isStashed).thenReturn(true)
        whenever(bubbleStashedHandleViewController.isEventOverHandle(any())).thenReturn(true)

        assertThat(BubbleBarInputConsumer.isEventOnBubbles(taskbarActivityContext, motionEvent))
            .isTrue()
    }

    @Test
    fun testIsEventOnBubbles_eventNotOnStashedHandle() {
        whenever(taskbarActivityContext.isBubbleBarEnabled).thenReturn(true)
        whenever(taskbarActivityContext.bubbleControllers).thenReturn(bubbleControllers)
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(true)

        whenever(bubbleStashController.isStashed).thenReturn(true)
        whenever(bubbleStashedHandleViewController.isEventOverHandle(any())).thenReturn(false)

        assertThat(BubbleBarInputConsumer.isEventOnBubbles(taskbarActivityContext, motionEvent))
            .isFalse()
    }

    @Test
    fun testIsEventOnBubbles_eventOnVisibleBubbleView() {
        whenever(taskbarActivityContext.isBubbleBarEnabled).thenReturn(true)
        whenever(taskbarActivityContext.bubbleControllers).thenReturn(bubbleControllers)
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(true)

        whenever(bubbleStashController.isStashed).thenReturn(false)
        whenever(bubbleBarViewController.isBubbleBarVisible).thenReturn(true)
        whenever(bubbleBarViewController.isEventOverBubbleBar(any())).thenReturn(true)

        assertThat(BubbleBarInputConsumer.isEventOnBubbles(taskbarActivityContext, motionEvent))
            .isTrue()
    }

    @Test
    fun testIsEventOnBubbles_eventNotOnVisibleBubbleView() {
        whenever(taskbarActivityContext.isBubbleBarEnabled).thenReturn(true)
        whenever(taskbarActivityContext.bubbleControllers).thenReturn(bubbleControllers)
        whenever(bubbleBarViewController.hasBubbles()).thenReturn(true)

        whenever(bubbleStashController.isStashed).thenReturn(false)
        whenever(bubbleBarViewController.isBubbleBarVisible).thenReturn(true)
        whenever(bubbleBarViewController.isEventOverBubbleBar(any())).thenReturn(false)

        assertThat(BubbleBarInputConsumer.isEventOnBubbles(taskbarActivityContext, motionEvent))
            .isFalse()
    }
}

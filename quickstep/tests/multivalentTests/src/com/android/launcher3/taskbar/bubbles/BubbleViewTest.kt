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

import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import androidx.core.graphics.drawable.toBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.R
import com.android.wm.shell.shared.bubbles.BubbleInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleViewTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var bubbleView: BubbleView
    private lateinit var overflowView: BubbleView
    private lateinit var bubble: BubbleBarBubble

    @Test
    fun hasUnseenContent_bubble() {
        setupBubbleViews()
        assertThat(bubbleView.hasUnseenContent()).isTrue()

        bubbleView.markSeen()
        assertThat(bubbleView.hasUnseenContent()).isFalse()
    }

    @Test
    fun hasUnseenContent_overflow() {
        setupBubbleViews()
        assertThat(overflowView.hasUnseenContent()).isFalse()
    }

    private fun setupBubbleViews() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val inflater = LayoutInflater.from(context)

            val bitmap = ColorDrawable(Color.WHITE).toBitmap(width = 20, height = 20)
            overflowView = inflater.inflate(R.layout.bubblebar_item_view, null, false) as BubbleView
            overflowView.setOverflow(BubbleBarOverflow(overflowView), bitmap)

            val bubbleInfo =
                BubbleInfo(
                    "key",
                    0,
                    null,
                    null,
                    0,
                    context.packageName,
                    null,
                    null,
                    false,
                    true,
                    null,
                )
            bubbleView = inflater.inflate(R.layout.bubblebar_item_view, null, false) as BubbleView
            bubble =
                BubbleBarBubble(
                    bubbleInfo,
                    bubbleView,
                    bitmap,
                    bitmap,
                    Color.WHITE,
                    Path(),
                    "",
                    null,
                )
            bubbleView.setBubble(bubble)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
}

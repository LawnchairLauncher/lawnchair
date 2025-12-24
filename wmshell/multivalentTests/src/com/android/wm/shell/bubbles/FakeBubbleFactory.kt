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
import android.content.pm.ShortcutInfo
import android.content.res.Resources
import android.view.LayoutInflater
import com.android.internal.logging.testing.UiEventLoggerFake
import com.android.wm.shell.R
import com.android.wm.shell.bubbles.BubbleViewInfoTask.BubbleViewInfo
import com.android.wm.shell.bubbles.bar.BubbleBarExpandedView
import com.android.wm.shell.common.TestShellExecutor
import com.google.common.util.concurrent.MoreExecutors.directExecutor

/** Helper to create a [Bubble] instance */
class FakeBubbleFactory {

    companion object {
        fun createExpandedView(
            context: Context,
            bubblePositioner: BubblePositioner,
            expandedViewManager: BubbleExpandedViewManager,
            bubble: Bubble,
            bubbleTaskView: BubbleTaskView,
            mainExecutor: TestShellExecutor,
            bgExecutor: TestShellExecutor,
            bubbleLogger: BubbleLogger = BubbleLogger(UiEventLoggerFake()),
        ): BubbleBarExpandedView {
            val bubbleBarExpandedView =
                (LayoutInflater.from(context)
                        .inflate(R.layout.bubble_bar_expanded_view, null, false /* attachToRoot */)
                        as BubbleBarExpandedView)
                    .apply {
                        this.bubbleLogger = bubbleLogger
                        initialize(
                            expandedViewManager,
                            bubblePositioner,
                            false, /* isOverflow */
                            bubble,
                            bubbleTaskView,
                        )
                    }
            bubble.setViewInfo(createViewInfo(bubbleBarExpandedView))
            return bubbleBarExpandedView
        }

        private fun createViewInfo(bubbleExpandedView: BubbleBarExpandedView): BubbleViewInfo {
            return BubbleViewInfo().apply { bubbleBarExpandedView = bubbleExpandedView }
        }

        fun createChatBubble(
            context: Context,
            key: String = "key",
        ): Bubble {
            val bubble =
                Bubble(
                    key,
                    ShortcutInfo.Builder(context, "id").build(),
                    100, /* desiredHeight */
                    Resources.ID_NULL, /* desiredHeightResId */
                    "title",
                    0, /* taskId */
                    null, /* locus */
                    true, /* isDismissable */
                    directExecutor(),
                    directExecutor(),
                ) {}
            return bubble
        }
    }
}

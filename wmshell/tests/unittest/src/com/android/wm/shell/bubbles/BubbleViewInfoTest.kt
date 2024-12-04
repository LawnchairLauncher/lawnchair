/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.content.res.Resources
import android.graphics.Color
import android.os.Handler
import android.os.UserManager
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.IWindowManager
import android.view.WindowManager
import androidx.test.filters.SmallTest
import com.android.internal.R
import com.android.internal.statusbar.IStatusBarService
import com.android.launcher3.icons.BubbleIconFactory
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.WindowManagerShellWrapper
import com.android.wm.shell.bubbles.bar.BubbleBarLayerView
import com.android.wm.shell.bubbles.properties.BubbleProperties
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayInsetsController
import com.android.wm.shell.common.FloatingContentCoordinator
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.common.TaskStackListenerImpl
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.taskview.TaskView
import com.android.wm.shell.taskview.TaskViewTransitions
import com.android.wm.shell.transition.Transitions
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.Executor

/** Tests for loading / inflating views & icons for a bubble. */
@SmallTest
@RunWith(AndroidTestingRunner::class)
@RunWithLooper(setAsMainLooper = true)
class BubbleViewInfoTest : ShellTestCase() {

    private lateinit var metadataFlagListener: Bubbles.BubbleMetadataFlagListener
    private lateinit var iconFactory: BubbleIconFactory
    private lateinit var bubble: Bubble
    private lateinit var bubbleController: BubbleController
    private lateinit var mainExecutor: ShellExecutor
    private lateinit var bubbleStackView: BubbleStackView
    private lateinit var bubbleBarLayerView: BubbleBarLayerView
    private lateinit var bubblePositioner: BubblePositioner
    private lateinit var expandedViewManager: BubbleExpandedViewManager

    private val bubbleTaskViewFactory = BubbleTaskViewFactory {
        BubbleTaskView(mock<TaskView>(), mock<Executor>())
    }

    @Before
    fun setup() {
        metadataFlagListener = Bubbles.BubbleMetadataFlagListener {}
        iconFactory =
            BubbleIconFactory(
                context,
                60,
                30,
                Color.RED,
                mContext.resources.getDimensionPixelSize(R.dimen.importance_ring_stroke_width)
            )

        mainExecutor = TestShellExecutor()
        val windowManager = context.getSystemService(WindowManager::class.java)
        val shellInit = ShellInit(mainExecutor)
        val shellCommandHandler = ShellCommandHandler()
        val shellController = ShellController(context, shellInit, shellCommandHandler,
					      mock<DisplayInsetsController>(), mainExecutor)
        bubblePositioner = BubblePositioner(context, windowManager)
        val bubbleData =
            BubbleData(
                context,
                mock<BubbleLogger>(),
                bubblePositioner,
                BubbleEducationController(context),
                mainExecutor
            )
        val surfaceSynchronizer = { obj: Runnable -> obj.run() }

        bubbleController =
            BubbleController(
                context,
                shellInit,
                shellCommandHandler,
                shellController,
                bubbleData,
                surfaceSynchronizer,
                FloatingContentCoordinator(),
                mock<BubbleDataRepository>(),
                mock<IStatusBarService>(),
                windowManager,
                WindowManagerShellWrapper(mainExecutor),
                mock<UserManager>(),
                mock<LauncherApps>(),
                mock<BubbleLogger>(),
                mock<TaskStackListenerImpl>(),
                ShellTaskOrganizer(mainExecutor),
                bubblePositioner,
                mock<DisplayController>(),
                null,
                null,
                mainExecutor,
                mock<Handler>(),
                mock<ShellExecutor>(),
                mock<TaskViewTransitions>(),
                mock<Transitions>(),
                mock<SyncTransactionQueue>(),
                mock<IWindowManager>(),
                mock<BubbleProperties>()
            )

        val bubbleStackViewManager = BubbleStackViewManager.fromBubbleController(bubbleController)
        bubbleStackView =
            BubbleStackView(
                context,
                bubbleStackViewManager,
                bubblePositioner,
                bubbleData,
                surfaceSynchronizer,
                FloatingContentCoordinator(),
                bubbleController,
                mainExecutor
            )
        expandedViewManager = BubbleExpandedViewManager.fromBubbleController(bubbleController)
        bubbleBarLayerView = BubbleBarLayerView(context, bubbleController, bubbleData)
    }

    @Test
    fun testPopulate() {
        bubble = createBubbleWithShortcut()
        val info =
            BubbleViewInfoTask.BubbleViewInfo.populate(
                context,
                expandedViewManager,
                bubbleTaskViewFactory,
                bubblePositioner,
                bubbleStackView,
                iconFactory,
                bubble,
                false /* skipInflation */
            )
        assertThat(info!!).isNotNull()

        assertThat(info.imageView).isNotNull()
        assertThat(info.expandedView).isNotNull()
        assertThat(info.bubbleBarExpandedView).isNull()

        assertThat(info.shortcutInfo).isNotNull()
        assertThat(info.appName).isNotEmpty()
        assertThat(info.rawBadgeBitmap).isNotNull()
        assertThat(info.dotPath).isNotNull()
        assertThat(info.bubbleBitmap).isNotNull()
        assertThat(info.badgeBitmap).isNotNull()
    }

    @Test
    fun testPopulateForBubbleBar() {
        bubble = createBubbleWithShortcut()
        val info =
            BubbleViewInfoTask.BubbleViewInfo.populateForBubbleBar(
                context,
                expandedViewManager,
                bubbleTaskViewFactory,
                bubblePositioner,
                bubbleBarLayerView,
                iconFactory,
                bubble,
                false /* skipInflation */
            )
        assertThat(info!!).isNotNull()

        assertThat(info.imageView).isNull()
        assertThat(info.expandedView).isNull()
        assertThat(info.bubbleBarExpandedView).isNotNull()

        assertThat(info.shortcutInfo).isNotNull()
        assertThat(info.appName).isNotEmpty()
        assertThat(info.rawBadgeBitmap).isNotNull()
        assertThat(info.dotPath).isNotNull()
        assertThat(info.bubbleBitmap).isNotNull()
        assertThat(info.badgeBitmap).isNotNull()
    }

    @Test
    fun testPopulate_invalidShortcutIcon() {
        bubble = createBubbleWithShortcut()

        // This eventually calls down to load the shortcut icon from the app, simulate an
        // exception here if the app has an issue loading the shortcut icon; we default to
        // the app icon in that case / none of the icons will be null.
        val mockIconFactory = mock<BubbleIconFactory>()
        whenever(mockIconFactory.getBubbleDrawable(eq(context), eq(bubble.shortcutInfo), any()))
            .doThrow(RuntimeException())

        val info =
            BubbleViewInfoTask.BubbleViewInfo.populateForBubbleBar(
                context,
                expandedViewManager,
                bubbleTaskViewFactory,
                bubblePositioner,
                bubbleBarLayerView,
                iconFactory,
                bubble,
                true /* skipInflation */
            )
        assertThat(info).isNotNull()

        assertThat(info?.shortcutInfo).isNotNull()
        assertThat(info?.appName).isNotEmpty()
        assertThat(info?.rawBadgeBitmap).isNotNull()
        assertThat(info?.dotPath).isNotNull()
        assertThat(info?.bubbleBitmap).isNotNull()
        assertThat(info?.badgeBitmap).isNotNull()
    }

    private fun createBubbleWithShortcut(): Bubble {
        val shortcutInfo = ShortcutInfo.Builder(mContext, "mockShortcutId").build()
        return Bubble(
            "mockKey",
            shortcutInfo,
            1000,
            Resources.ID_NULL,
            "mockTitle",
            0 /* taskId */,
            "mockLocus",
            true /* isDismissible */,
            mainExecutor,
            metadataFlagListener
        )
    }
}

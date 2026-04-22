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

package com.android.wm.shell.windowdecor.viewholder

import android.app.ActivityManager.RunningTaskInfo
import android.graphics.Point
import android.os.Handler
import android.testing.AndroidTestingRunner
import android.view.View
import android.widget.ImageButton
import androidx.test.filters.SmallTest
import com.android.internal.policy.SystemBarUtils
import com.android.wm.shell.R
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.windowdecor.WindowManagerWrapper
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test

/**
 * Tests for [AppHandleViewHolder].
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:AppHandleViewHolderTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class AppHandleViewHolderTest : ShellTestCase() {

    companion object {
        private const val CAPTION_WIDTH = 500
        private const val CAPTION_HEIGHT = 100
    }

    private val mockView = mock<View>()
    private val mockImageButton = mock<ImageButton>()
    private val mockOnTouchListener = mock<View.OnTouchListener>()
    private val mockOnClickListener = mock<View.OnClickListener>()
    private val mockWindowManagerWrapper = mock<WindowManagerWrapper>()
    private val mockHandler = mock<Handler>()
    private val mockTaskInfo = mock<RunningTaskInfo>()
    private val mockDesktopModeUiEventLogger = mock<DesktopModeUiEventLogger>()

    @Before
    fun setup() {
        whenever(mockView.context).thenReturn(mContext)
        whenever(mockView.requireViewById<View>(R.id.desktop_mode_caption))
            .thenReturn(mockView)
        whenever(mockView.requireViewById<ImageButton>(R.id.caption_handle))
            .thenReturn(mockImageButton)
    }

    @Test
    fun statusBarInputLayer_disposedWhenCaptionBelowStatusBar() {
        val appHandleViewHolder: AppHandleViewHolder = spy(createAppHandleViewHolder())
        val captionPosition = Point(0, SystemBarUtils.getStatusBarHeight(mContext) + 10)

        appHandleViewHolder.bindData(
            AppHandleViewHolder.HandleData(
                taskInfo = mockTaskInfo,
                position = captionPosition,
                width = CAPTION_WIDTH,
                height = CAPTION_HEIGHT,
                showInputLayer = false,
                isCaptionVisible = true
            )
        )

        verify(appHandleViewHolder).disposeStatusBarInputLayer()
    }

    private fun createAppHandleViewHolder(): AppHandleViewHolder {
        return AppHandleViewHolder(
            mockView,
            mContext,
            mockOnTouchListener,
            mockOnClickListener,
            mockWindowManagerWrapper,
            mockHandler,
            mockDesktopModeUiEventLogger,
        )
    }
}

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

package com.android.wm.shell.desktopmode

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED
import android.graphics.Rect
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.apptoweb.AppToWebRepositoryImpl
import com.android.wm.shell.windowdecor.viewholder.AppHandleIdentifier
import com.android.wm.shell.windowdecor.viewholder.AppHandleIdentifier.AppHandleWindowingMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidTestingRunner::class)
class WindowDecorCaptionRepositoryTest : ShellTestCase() {
    @Mock private lateinit var appToWebRepository: AppToWebRepositoryImpl

    private lateinit var mockInit: AutoCloseable
    private lateinit var captionRepository: WindowDecorCaptionRepository

    @Before
    fun setUp() {
        mockInit = MockitoAnnotations.openMocks(this)
        captionRepository = WindowDecorCaptionRepository()
    }

    @After
    fun tearDown() {
        mockInit.close()
    }

    @Test
    fun notifyCaptionChange_toAppHandleVisible_updatesStateWithCorrectData() = runTest {
        val taskInfo = createTaskInfo(WINDOWING_MODE_FULLSCREEN, GMAIL_PACKAGE_NAME)
        val appHandleCaptionState =
            CaptionState.AppHandle(
                runningTaskInfo = taskInfo,
                isHandleMenuExpanded = false,
                globalAppHandleBounds =
                    Rect(/* left= */ 0, /* top= */ 1, /* right= */ 2, /* bottom= */ 3),
                appHandleIdentifier = createHandleIdentifier(taskInfo.taskId),
                isFocused = true,
            )

        captionRepository.notifyCaptionChanged(appHandleCaptionState)

        assertThat(captionRepository.captionStateFlow.first()).isEqualTo(appHandleCaptionState)
    }

    @Test
    fun notifyCaptionChange_toAppChipVisible_updatesStateWithCorrectData() = runTest {
        val taskInfo = createTaskInfo(WINDOWING_MODE_FREEFORM, GMAIL_PACKAGE_NAME)
        val appHeaderCaptionState =
            CaptionState.AppHeader(
                runningTaskInfo = taskInfo,
                isHeaderMenuExpanded = true,
                globalAppChipBounds =
                    Rect(/* left= */ 0, /* top= */ 1, /* right= */ 2, /* bottom= */ 3),
                isFocused = true,
            )

        captionRepository.notifyCaptionChanged(appHeaderCaptionState)

        assertThat(captionRepository.captionStateFlow.first()).isEqualTo(appHeaderCaptionState)
    }

    @Test
    fun notifyCaptionChange_toNoCaption_updatesState() = runTest {
        captionRepository.notifyCaptionChanged(CaptionState.NoCaption())

        assertThat(captionRepository.captionStateFlow.first()).isEqualTo(CaptionState.NoCaption())
    }

    private fun createTaskInfo(
        deviceWindowingMode: Int = WINDOWING_MODE_UNDEFINED,
        runningTaskPackageName: String = LAUNCHER_PACKAGE_NAME,
    ): RunningTaskInfo =
        RunningTaskInfo().apply {
            configuration.windowConfiguration.apply { windowingMode = deviceWindowingMode }
            topActivityInfo?.apply { packageName = runningTaskPackageName }
        }

    private fun createHandleIdentifier(
        taskId: Int,
        handleBounds: Rect = Rect(),
        displayId: Int = DEFAULT_DISPLAY,
        windowingMode: AppHandleWindowingMode =
            AppHandleWindowingMode.APP_HANDLE_WINDOWING_MODE_FULLSCREEN,
    ): AppHandleIdentifier = AppHandleIdentifier(handleBounds, displayId, taskId, windowingMode)

    private companion object {
        const val GMAIL_PACKAGE_NAME = "com.google.android.gm"
        const val LAUNCHER_PACKAGE_NAME = "com.google.android.apps.nexuslauncher"
    }
}

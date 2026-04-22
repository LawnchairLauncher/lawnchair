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

package com.android.wm.shell.desktopmode.education

import android.os.SystemProperties
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableContext
import androidx.test.filters.SmallTest
import com.android.modules.utils.testing.ExtendedMockitoRule
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.CaptionState
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger
import com.android.wm.shell.desktopmode.DesktopModeUiEventLogger.DesktopUiEventEnum
import com.android.wm.shell.desktopmode.WindowDecorCaptionRepository
import com.android.wm.shell.desktopmode.education.AppHandleEducationController.Companion.APP_HANDLE_EDUCATION_DELAY_MILLIS
import com.android.wm.shell.desktopmode.education.AppHandleEducationController.Companion.TOOLTIP_VISIBLE_DURATION_MILLIS
import com.android.wm.shell.desktopmode.education.data.AppHandleEducationDatastoreRepository
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.util.createAppHandleState
import com.android.wm.shell.util.createAppHeaderState
import com.android.wm.shell.util.createWindowingEducationProto
import com.android.wm.shell.windowdecor.education.DesktopWindowingEducationTooltipController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests of [AppHandleEducationController] Usage: atest AppHandleEducationControllerTest */
@SmallTest
@RunWith(AndroidTestingRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AppHandleEducationControllerTest : ShellTestCase() {
    @JvmField
    @Rule
    val extendedMockitoRule =
        ExtendedMockitoRule.Builder(this).mockStatic(SystemProperties::class.java).build()!!

    private lateinit var educationController: AppHandleEducationController
    private lateinit var testableContext: TestableContext
    private val testScope = TestScope()
    private val testDataStoreFlow = MutableStateFlow(createWindowingEducationProto())
    private val testCaptionStateFlow = MutableStateFlow<CaptionState>(CaptionState.NoCaption())
    private val educationConfigCaptor =
        argumentCaptor<DesktopWindowingEducationTooltipController.TooltipEducationViewConfig>()
    @Mock private lateinit var mockEducationFilter: AppHandleEducationFilter
    @Mock private lateinit var mockDataStoreRepository: AppHandleEducationDatastoreRepository
    @Mock private lateinit var mockCaptionHandleRepository: WindowDecorCaptionRepository
    @Mock private lateinit var mockTooltipController: DesktopWindowingEducationTooltipController
    @Mock private lateinit var mockDesktopModeUiEventLogger: DesktopModeUiEventLogger
    private lateinit var desktopState: FakeDesktopState

    @Before
    fun setUp() {
        desktopState = FakeDesktopState()
        desktopState.canEnterDesktopMode = true
        MockitoAnnotations.initMocks(this)
        Dispatchers.setMain(StandardTestDispatcher(testScope.testScheduler))
        testableContext = TestableContext(mContext)
        whenever(mockDataStoreRepository.dataStoreFlow).thenReturn(testDataStoreFlow)
        whenever(mockCaptionHandleRepository.captionStateFlow).thenReturn(testCaptionStateFlow)

        educationController =
            AppHandleEducationController(
                testableContext,
                mockEducationFilter,
                mockDataStoreRepository,
                mockCaptionHandleRepository,
                mockTooltipController,
                testScope.backgroundScope,
                Dispatchers.Main,
                mockDesktopModeUiEventLogger,
                desktopState,
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun init_appHandleVisible_shouldCallShowEducationTooltipAndMarkAsViewed() =
        testScope.runTest {
            // App handle is visible. Should show education tooltip.
            setShouldShowDesktopModeEducation(true)

            // Simulate app handle visible.
            testCaptionStateFlow.value = createAppHandleState()
            // Wait for first tooltip to showup.
            waitForBufferDelay()

            verify(mockTooltipController, times(1)).showEducationTooltip(any(), any())
            verify(mockDataStoreRepository, times(1))
                .updateAppHandleHintViewedTimestampMillis(eq(true))
            verify(mockDesktopModeUiEventLogger, times(1))
                .log(any(), eq(DesktopUiEventEnum.APP_HANDLE_EDUCATION_TOOLTIP_SHOWN))
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun init_appHandleEducationVisible_afterDelayTooltipShouldBeDismissed() =
        testScope.runTest {
            // App handle is visible. Should show education tooltip.
            setShouldShowDesktopModeEducation(true)
            // Simulate app handle visible.
            testCaptionStateFlow.value = createAppHandleState()
            // Wait for first tooltip to showup.
            waitForBufferDelay()

            // Wait until tooltip gets dismissed
            waitForBufferDelay(TOOLTIP_VISIBLE_DURATION_MILLIS + 1000L)

            verify(mockTooltipController, times(1)).hideEducationTooltip()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun init_appHandleVisibleAndMenuExpanded_shouldCallShowEducationTooltipAndMarkAsViewed() =
        testScope.runTest {
            setShouldShowDesktopModeEducation(true)

            // Simulate app handle visible and handle menu is expanded.
            testCaptionStateFlow.value = createAppHandleState(isHandleMenuExpanded = true)
            waitForBufferDelay()

            verify(mockTooltipController, times(1)).showEducationTooltip(any(), any())
            verify(mockDataStoreRepository, times(1))
                .updateEnterDesktopModeHintViewedTimestampMillis(eq(true))
            verify(mockDesktopModeUiEventLogger, times(1))
                .log(any(), eq(DesktopUiEventEnum.ENTER_DESKTOP_MODE_EDUCATION_TOOLTIP_SHOWN))
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun init_appHeaderVisible_shouldCallShowEducationTooltipAndMarkAsViewed() =
        testScope.runTest {
            setShouldShowDesktopModeEducation(true)

            // Simulate app header visible.
            testCaptionStateFlow.value = createAppHeaderState()
            waitForBufferDelay()

            verify(mockTooltipController, times(1)).showEducationTooltip(any(), any())
            verify(mockDataStoreRepository, times(1))
                .updateExitDesktopModeHintViewedTimestampMillis(eq(true))
            verify(mockDesktopModeUiEventLogger, times(1))
                .log(any(), eq(DesktopUiEventEnum.EXIT_DESKTOP_MODE_EDUCATION_TOOLTIP_SHOWN))
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun init_noCaptionStateNotified_shouldHideAllTooltips() =
        testScope.runTest {
            setShouldShowDesktopModeEducation(true)

            // Simulate no caption state notification
            testCaptionStateFlow.value = CaptionState.NoCaption()
            waitForBufferDelay()

            verify(mockTooltipController, times(1)).hideEducationTooltip()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun init_appHandleHintViewed_shouldNotListenToNoCaptionNotification() =
        testScope.runTest {
            testDataStoreFlow.value =
                createWindowingEducationProto(appHandleHintViewedTimestampMillis = 123L)
            setShouldShowDesktopModeEducation(true)

            // Simulate no caption state notification
            testCaptionStateFlow.value = CaptionState.NoCaption()
            waitForBufferDelay()

            verify(mockTooltipController, never()).hideEducationTooltip()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun init_enterDesktopModeHintViewed_shouldNotListenToNoCaptionNotification() =
        testScope.runTest {
            testDataStoreFlow.value =
                createWindowingEducationProto(enterDesktopModeHintViewedTimestampMillis = 123L)
            setShouldShowDesktopModeEducation(true)

            // Simulate no caption state notification
            testCaptionStateFlow.value = CaptionState.NoCaption()
            waitForBufferDelay()

            verify(mockTooltipController, never()).hideEducationTooltip()
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun init_exitDesktopModeHintViewed_shouldNotListenToNoCaptionNotification() =
        testScope.runTest {
            testDataStoreFlow.value =
                createWindowingEducationProto(exitDesktopModeHintViewedTimestampMillis = 123L)
            setShouldShowDesktopModeEducation(true)

            // Simulate no caption state notification
            testCaptionStateFlow.value = CaptionState.NoCaption()
            waitForBufferDelay()

            verify(mockTooltipController, never()).hideEducationTooltip()
        }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun init_flagDisabled_shouldNotCallShowEducationTooltip() =
        testScope.runTest {
            // App handle visible but education aconfig flag disabled, should not show education
            // tooltip.
            desktopState.canEnterDesktopMode = false
            setShouldShowDesktopModeEducation(true)

            // Simulate app handle visible.
            testCaptionStateFlow.value = createAppHandleState()
            // Wait for first tooltip to showup.
            waitForBufferDelay()

            verify(mockTooltipController, never()).showEducationTooltip(any(), any())
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun init_shouldShowDesktopModeEducationReturnsFalse_shouldNotCallShowEducationTooltip() =
        testScope.runTest {
            // App handle is visible but [shouldShowDesktopModeEducation] api returns false, should
            // not show education tooltip.
            setShouldShowDesktopModeEducation(false)

            // Simulate app handle visible.
            testCaptionStateFlow.value = createAppHandleState()
            // Wait for first tooltip to showup.
            waitForBufferDelay()

            verify(mockTooltipController, never()).showEducationTooltip(any(), any())
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun init_appHandleNotVisible_shouldNotCallShowEducationTooltip() =
        testScope.runTest {
            // App handle is not visible, should not show education tooltip.
            setShouldShowDesktopModeEducation(true)

            // Simulate app handle is not visible.
            testCaptionStateFlow.value = CaptionState.NoCaption()
            // Wait for first tooltip to showup.
            waitForBufferDelay()

            verify(mockTooltipController, never()).showEducationTooltip(any(), any())
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun init_appHandleHintViewedAlready_shouldNotCallShowEducationTooltip() =
        testScope.runTest {
            // App handle is visible but app handle hint has been viewed before,
            // should not show education tooltip.
            // Mark app handle hint viewed.
            testDataStoreFlow.value =
                createWindowingEducationProto(appHandleHintViewedTimestampMillis = 123L)
            setShouldShowDesktopModeEducation(true)

            // Simulate app handle visible.
            testCaptionStateFlow.value = createAppHandleState(isHandleMenuExpanded = false)
            // Wait for first tooltip to showup.
            waitForBufferDelay()

            verify(mockTooltipController, never()).showEducationTooltip(any(), any())
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun init_enterDesktopModeHintViewedAlready_shouldNotCallShowEducationTooltip() =
        testScope.runTest {
            // App handle is visible but app handle hint has been viewed before,
            // should not show education tooltip.
            // Mark app handle hint viewed.
            testDataStoreFlow.value =
                createWindowingEducationProto(enterDesktopModeHintViewedTimestampMillis = 123L)
            setShouldShowDesktopModeEducation(true)

            // Simulate app handle visible.
            testCaptionStateFlow.value = createAppHandleState(isHandleMenuExpanded = true)
            // Wait for first tooltip to showup.
            waitForBufferDelay()

            verify(mockTooltipController, never()).showEducationTooltip(any(), any())
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun init_exitDesktopModeHintViewedAlready_shouldNotCallShowEducationTooltip() =
        testScope.runTest {
            // App handle is visible but app handle hint has been viewed before,
            // should not show education tooltip.
            // Mark app handle hint viewed.
            testDataStoreFlow.value =
                createWindowingEducationProto(exitDesktopModeHintViewedTimestampMillis = 123L)
            setShouldShowDesktopModeEducation(true)

            // Simulate app handle visible.
            testCaptionStateFlow.value = createAppHeaderState()
            // Wait for first tooltip to showup.
            waitForBufferDelay()

            verify(mockTooltipController, never()).showEducationTooltip(any(), any())
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun overridePrerequisite_appHandleHintViewedAlready_shouldCallShowEducationTooltip() =
        testScope.runTest {
            // App handle is visible but app handle hint has been viewed before.
            // But as we are overriding prerequisite conditions, we should show app
            // handle tooltip.
            // Mark app handle hint viewed.
            testDataStoreFlow.value =
                createWindowingEducationProto(appHandleHintViewedTimestampMillis = 123L)
            whenever(SystemProperties.getBoolean(eq(FORCE_SHOW_EDUCATION_SYSPROP), anyBoolean()))
                .thenReturn(true)
            setShouldShowDesktopModeEducation(true)

            // Simulate app handle visible.
            testCaptionStateFlow.value = createAppHandleState(isHandleMenuExpanded = false)
            // Wait for first tooltip to showup.
            waitForBufferDelay()

            verify(mockTooltipController, times(1)).showEducationTooltip(any(), any())
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun clickAppHandleHint_openHandleMenuCallbackInvoked() =
        testScope.runTest {
            // App handle is visible. Should show education tooltip.
            setShouldShowDesktopModeEducation(true)
            val mockOpenHandleMenuCallback: (Int) -> Unit = mock()
            val mockToDesktopModeCallback: (Int, DesktopModeTransitionSource) -> Unit = mock()
            educationController.setAppHandleEducationTooltipCallbacks(
                mockOpenHandleMenuCallback,
                mockToDesktopModeCallback,
            )
            // Simulate app handle visible.
            testCaptionStateFlow.value = createAppHandleState()
            // Wait for first tooltip to showup.
            waitForBufferDelay()

            verify(mockTooltipController, atLeastOnce())
                .showEducationTooltip(educationConfigCaptor.capture(), any())
            educationConfigCaptor.lastValue.onEducationClickAction.invoke()

            verify(mockOpenHandleMenuCallback, times(1)).invoke(any())
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun clickEnterDesktopModeHint_toDesktopModeCallbackInvoked() =
        testScope.runTest {
            // App handle is visible. Should show education tooltip.
            setShouldShowDesktopModeEducation(true)
            val mockOpenHandleMenuCallback: (Int) -> Unit = mock()
            val mockToDesktopModeCallback: (Int, DesktopModeTransitionSource) -> Unit = mock()
            educationController.setAppHandleEducationTooltipCallbacks(
                mockOpenHandleMenuCallback,
                mockToDesktopModeCallback,
            )
            // Simulate app handle visible.
            testCaptionStateFlow.value = createAppHandleState(isHandleMenuExpanded = true)
            // Wait for first tooltip to showup.
            waitForBufferDelay()

            verify(mockTooltipController, atLeastOnce())
                .showEducationTooltip(educationConfigCaptor.capture(), any())
            educationConfigCaptor.lastValue.onEducationClickAction.invoke()

            verify(mockToDesktopModeCallback, times(1))
                .invoke(any(), eq(DesktopModeTransitionSource.APP_HANDLE_MENU_BUTTON))
        }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun clickExitDesktopModeHint_openHandleMenuCallbackInvoked() =
        testScope.runTest {
            // App handle is visible. Should show education tooltip.
            setShouldShowDesktopModeEducation(true)
            val mockOpenHandleMenuCallback: (Int) -> Unit = mock()
            val mockToDesktopModeCallback: (Int, DesktopModeTransitionSource) -> Unit = mock()
            educationController.setAppHandleEducationTooltipCallbacks(
                mockOpenHandleMenuCallback,
                mockToDesktopModeCallback,
            )
            // Simulate app handle visible.
            testCaptionStateFlow.value = createAppHeaderState()
            // Wait for first tooltip to showup.
            waitForBufferDelay()

            verify(mockTooltipController, atLeastOnce())
                .showEducationTooltip(educationConfigCaptor.capture(), any())
            educationConfigCaptor.lastValue.onEducationClickAction.invoke()

            verify(mockOpenHandleMenuCallback, times(1)).invoke(any())
        }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION,
        Flags.FLAG_ENABLE_APP_HANDLE_POSITION_REPORTING,
    )
    fun init_taskNotFocused_shouldNotCallShowEducationTooltip() =
        testScope.runTest {
            setShouldShowDesktopModeEducation(true)

            // Simulate app handle not focused.
            testCaptionStateFlow.value = createAppHandleState(isFocused = false)
            // Wait for first tooltip to showup.
            waitForBufferDelay()

            verify(mockTooltipController, never()).showEducationTooltip(any(), any())
        }

    private suspend fun setShouldShowDesktopModeEducation(shouldShowDesktopModeEducation: Boolean) {
        whenever(mockEducationFilter.shouldShowDesktopModeEducation(any<CaptionState.AppHandle>()))
            .thenReturn(shouldShowDesktopModeEducation)
        whenever(mockEducationFilter.shouldShowDesktopModeEducation(any<CaptionState.AppHeader>()))
            .thenReturn(shouldShowDesktopModeEducation)
    }

    /**
     * Class under test waits for some time before showing education, simulate advance time before
     * verifying or moving forward
     */
    private fun TestScope.waitForBufferDelay(
        delay: Long = APP_HANDLE_EDUCATION_DELAY_BUFFER_MILLIS
    ) {
        advanceTimeBy(delay)
        runCurrent()
    }

    private companion object {
        val APP_HANDLE_EDUCATION_DELAY_BUFFER_MILLIS: Long =
            APP_HANDLE_EDUCATION_DELAY_MILLIS + 1000L

        val FORCE_SHOW_EDUCATION_SYSPROP = "persist.windowing_force_show_desktop_mode_education"
    }
}

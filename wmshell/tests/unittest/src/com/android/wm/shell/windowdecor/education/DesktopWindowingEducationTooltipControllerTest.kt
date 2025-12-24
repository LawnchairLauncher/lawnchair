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

package com.android.wm.shell.windowdecor.education

import android.annotation.LayoutRes
import android.content.Context
import android.graphics.Point
import android.testing.AndroidTestingRunner
import android.testing.TestableContext
import android.testing.TestableLooper
import android.testing.TestableResources
import android.view.MotionEvent
import android.view.Surface.ROTATION_180
import android.view.Surface.ROTATION_90
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.window.WindowContainerTransaction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.filters.SmallTest
import com.android.wm.shell.R
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.windowdecor.additionalviewcontainer.AdditionalSystemViewContainer
import com.android.wm.shell.windowdecor.education.DesktopWindowingEducationTooltipController.TooltipArrowDirection
import com.android.wm.shell.windowdecor.education.DesktopWindowingEducationTooltipController.TooltipColorScheme
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@RunWith(AndroidTestingRunner::class)
class DesktopWindowingEducationTooltipControllerTest : ShellTestCase() {
    @Mock private lateinit var mockWindowManager: WindowManager
    @Mock private lateinit var mockViewContainerFactory: AdditionalSystemViewContainer.Factory
    @Mock private lateinit var mockDisplayController: DisplayController
    @Mock private lateinit var mockPopupWindow: AdditionalSystemViewContainer
    private lateinit var testableResources: TestableResources
    private lateinit var testableContext: TestableContext
    private lateinit var tooltipController: DesktopWindowingEducationTooltipController
    private val tooltipViewArgumentCaptor = argumentCaptor<View>()

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        testableContext = TestableContext(mContext)
        testableResources =
            testableContext.orCreateTestableResources.apply {
                addOverride(R.dimen.desktop_windowing_education_tooltip_padding, 10)
            }
        testableContext.addMockSystemService(
            Context.LAYOUT_INFLATER_SERVICE,
            context.getSystemService(Context.LAYOUT_INFLATER_SERVICE),
        )
        testableContext.addMockSystemService(WindowManager::class.java, mockWindowManager)
        tooltipController =
            DesktopWindowingEducationTooltipController(
                testableContext,
                mockViewContainerFactory,
                mockDisplayController,
            )
    }

    @Test
    fun showEducationTooltip_createsTooltipWithCorrectText() {
        val tooltipText = "This is a tooltip"
        val tooltipViewConfig = createTooltipConfig(tooltipText = tooltipText)

        tooltipController.showEducationTooltip(tooltipViewConfig = tooltipViewConfig, taskId = 123)

        verify(mockViewContainerFactory, times(1))
            .create(
                windowManagerWrapper = any(),
                taskId = anyInt(),
                x = anyInt(),
                y = anyInt(),
                width = anyInt(),
                height = anyInt(),
                flags = anyInt(),
                view = tooltipViewArgumentCaptor.capture(),
            )
        val tooltipTextView =
            tooltipViewArgumentCaptor.lastValue.findViewById<TextView>(R.id.tooltip_text)
        assertThat(tooltipTextView.text).isEqualTo(tooltipText)
    }

    @Test
    fun showEducationTooltip_usesCorrectTaskIdForWindow() {
        val tooltipViewConfig = createTooltipConfig()
        val taskIdArgumentCaptor = argumentCaptor<Int>()

        tooltipController.showEducationTooltip(tooltipViewConfig = tooltipViewConfig, taskId = 123)

        verify(mockViewContainerFactory, times(1))
            .create(
                windowManagerWrapper = any(),
                taskId = taskIdArgumentCaptor.capture(),
                x = anyInt(),
                y = anyInt(),
                width = anyInt(),
                height = anyInt(),
                flags = anyInt(),
                view = anyOrNull(),
            )
        assertThat(taskIdArgumentCaptor.lastValue).isEqualTo(123)
    }

    @Test
    fun showEducationTooltip_tooltipPointsUpwards_horizontallyPositionTooltip() {
        val initialTooltipX = 0
        val initialTooltipY = 0
        val tooltipViewConfig =
            createTooltipConfig(
                arrowDirection = TooltipArrowDirection.UP,
                tooltipViewGlobalCoordinates = Point(initialTooltipX, initialTooltipY),
            )
        val tooltipXArgumentCaptor = argumentCaptor<Int>()
        val tooltipWidthArgumentCaptor = argumentCaptor<Int>()

        tooltipController.showEducationTooltip(tooltipViewConfig = tooltipViewConfig, taskId = 123)

        verify(mockViewContainerFactory, times(1))
            .create(
                windowManagerWrapper = any(),
                taskId = anyInt(),
                x = tooltipXArgumentCaptor.capture(),
                y = anyInt(),
                width = tooltipWidthArgumentCaptor.capture(),
                height = anyInt(),
                flags = anyInt(),
                view = tooltipViewArgumentCaptor.capture(),
            )
        val expectedTooltipX = initialTooltipX - tooltipWidthArgumentCaptor.lastValue / 2
        assertThat(tooltipXArgumentCaptor.lastValue).isEqualTo(expectedTooltipX)
    }

    @Test
    fun showEducationTooltip_tooltipPointsHorizontally_verticallyPositionTooltip() {
        val initialTooltipX = 0
        val initialTooltipY = 0
        val tooltipViewConfig =
            createTooltipConfig(
                arrowDirection = TooltipArrowDirection.HORIZONTAL,
                tooltipViewGlobalCoordinates = Point(initialTooltipX, initialTooltipY),
            )
        val tooltipYArgumentCaptor = argumentCaptor<Int>()
        val tooltipHeightArgumentCaptor = argumentCaptor<Int>()

        tooltipController.showEducationTooltip(tooltipViewConfig = tooltipViewConfig, taskId = 123)

        verify(mockViewContainerFactory, times(1))
            .create(
                windowManagerWrapper = any(),
                taskId = anyInt(),
                x = anyInt(),
                y = tooltipYArgumentCaptor.capture(),
                width = anyInt(),
                height = tooltipHeightArgumentCaptor.capture(),
                flags = anyInt(),
                view = tooltipViewArgumentCaptor.capture(),
            )
        val expectedTooltipY = initialTooltipY - tooltipHeightArgumentCaptor.lastValue / 2
        assertThat(tooltipYArgumentCaptor.lastValue).isEqualTo(expectedTooltipY)
    }

    @Test
    fun showEducationTooltip_touchEventActionOutside_dismissActionPerformed() {
        val mockLambda: () -> Unit = mock()
        val tooltipViewConfig = createTooltipConfig(onDismissAction = mockLambda)

        tooltipController.showEducationTooltip(tooltipViewConfig = tooltipViewConfig, taskId = 123)
        verify(mockViewContainerFactory, times(1))
            .create(
                windowManagerWrapper = any(),
                taskId = anyInt(),
                x = anyInt(),
                y = anyInt(),
                width = anyInt(),
                height = anyInt(),
                flags = anyInt(),
                view = tooltipViewArgumentCaptor.capture(),
            )
        val motionEvent =
            MotionEvent.obtain(
                /* downTime= */ 0L,
                /* eventTime= */ 0L,
                MotionEvent.ACTION_OUTSIDE,
                /* x= */ 0f,
                /* y= */ 0f,
                /* metaState= */ 0,
            )
        tooltipViewArgumentCaptor.lastValue.dispatchTouchEvent(motionEvent)

        verify(mockLambda).invoke()
    }

    @Test
    fun showEducationTooltip_tooltipClicked_onClickActionPerformed() {
        val mockLambda: () -> Unit = mock()
        val tooltipViewConfig = createTooltipConfig(onEducationClickAction = mockLambda)

        tooltipController.showEducationTooltip(tooltipViewConfig = tooltipViewConfig, taskId = 123)
        verify(mockViewContainerFactory, times(1))
            .create(
                windowManagerWrapper = any(),
                taskId = anyInt(),
                x = anyInt(),
                y = anyInt(),
                width = anyInt(),
                height = anyInt(),
                flags = anyInt(),
                view = tooltipViewArgumentCaptor.capture(),
            )
        tooltipViewArgumentCaptor.lastValue.performClick()

        verify(mockLambda).invoke()
    }

    @Test
    fun showEducationTooltip_displayRotationChanged_hidesTooltip() {
        whenever(
                mockViewContainerFactory.create(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            )
            .thenReturn(mockPopupWindow)
        val tooltipViewConfig = createTooltipConfig()

        tooltipController.showEducationTooltip(tooltipViewConfig = tooltipViewConfig, taskId = 123)
        tooltipController.onDisplayChange(
            /* displayId= */ 123,
            /* fromRotation= */ ROTATION_90,
            /* toRotation= */ ROTATION_180,
            /* newDisplayAreaInfo= */ null,
            WindowContainerTransaction(),
        )

        verify(mockPopupWindow, times(1)).releaseView()
        verify(mockDisplayController, atLeastOnce()).removeDisplayChangingController(any())
    }

    @Test
    fun showEducationTooltip_setTooltipColorScheme_correctColorsAreSet() {
        val tooltipColorScheme =
            TooltipColorScheme(
                container = Color.Red.toArgb(),
                text = Color.Blue.toArgb(),
                icon = Color.Green.toArgb(),
            )
        val tooltipViewConfig = createTooltipConfig(tooltipColorScheme = tooltipColorScheme)

        tooltipController.showEducationTooltip(tooltipViewConfig = tooltipViewConfig, taskId = 123)

        verify(mockViewContainerFactory, times(1))
            .create(
                windowManagerWrapper = any(),
                taskId = anyInt(),
                x = anyInt(),
                y = anyInt(),
                width = anyInt(),
                height = anyInt(),
                flags = anyInt(),
                view = tooltipViewArgumentCaptor.capture(),
            )
        val tooltipTextView =
            tooltipViewArgumentCaptor.lastValue.findViewById<TextView>(R.id.tooltip_text)
        assertThat(tooltipTextView.textColors.defaultColor).isEqualTo(Color.Blue.toArgb())
    }

    private fun createTooltipConfig(
        @LayoutRes tooltipViewLayout: Int = R.layout.desktop_windowing_education_top_arrow_tooltip,
        tooltipColorScheme: TooltipColorScheme =
            TooltipColorScheme(
                container = Color.Red.toArgb(),
                text = Color.Red.toArgb(),
                icon = Color.Red.toArgb(),
            ),
        tooltipViewGlobalCoordinates: Point = Point(0, 0),
        tooltipText: String = "This is a tooltip",
        arrowDirection: TooltipArrowDirection = TooltipArrowDirection.UP,
        onEducationClickAction: () -> Unit = {},
        onDismissAction: () -> Unit = {},
    ) =
        DesktopWindowingEducationTooltipController.TooltipEducationViewConfig(
            tooltipViewLayout = tooltipViewLayout,
            tooltipColorScheme = tooltipColorScheme,
            tooltipViewGlobalCoordinates = tooltipViewGlobalCoordinates,
            tooltipText = tooltipText,
            arrowDirection = arrowDirection,
            onEducationClickAction = onEducationClickAction,
            onDismissAction = onDismissAction,
        )
}
